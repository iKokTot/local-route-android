package org.localroute.android.localdpi

import android.content.Context
import android.os.ParcelFileDescriptor
import io.github.dovecoteescapee.byedpi.core.ByeDpiProxy
import io.github.dovecoteescapee.byedpi.core.TProxyService
import org.localroute.android.core.DpiStrategy
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** Local-only chain: TUN fd -> hev-socks5-tunnel -> 127.0.0.1 ByeDPI SOCKS5. */
interface NativeTunnelBridge {
    fun startProxy(strategy: DpiStrategy): Result<Unit>
    fun startTunnel(tun: ParcelFileDescriptor): Result<Unit>
    fun stop()
}

class AndroidNativeTunnelBridge(private val context: Context) : NativeTunnelBridge {
    private val executor = Executors.newSingleThreadExecutor()
    private var proxy: ByeDpiProxy? = null
    private var proxyFd: Int = -1
    private var proxyTask: Future<*>? = null
    private var config: File? = null

    override fun startProxy(strategy: DpiStrategy): Result<Unit> = runCatching {
        check(proxy == null) { "Локальный движок уже запущен" }
        val localProxy = ByeDpiProxy()
        // Keep command construction identical to ByeDPIAndroid v1.2.0. The
        // bundled ByeDPI 13 parser receives argv[0] and owns the local listener.
        val args = arrayOf("--ip", "127.0.0.1", "--port", "1080", "--no-ipv6", *strategy.nativeArguments.toTypedArray())
        val fd = localProxy.create(args)
        check(fd >= 0) { "ByeDPI не принял параметры стратегии" }
        proxy = localProxy
        proxyFd = fd
        proxyTask = executor.submit { localProxy.run(fd) }
        check(awaitSocksReady()) { "ByeDPI не открыл локальный SOCKS5-порт" }
    }.onFailure { stop() }

    override fun startTunnel(tun: ParcelFileDescriptor): Result<Unit> = runCatching {
        check(proxy != null && proxyFd >= 0) { "ByeDPI SOCKS5 ещё не запущен" }
        config = File.createTempFile("local-route-tun2socks-", ".yml", context.cacheDir).apply {
            // Keep this transport block aligned with ByeDPIAndroid. In particular,
            // the larger SOCKS MTU and native task stack are required for TLS
            // ClientHello packets on real devices; the previous 1500-byte override
            // could leave the VPN connected while the TLS stream stalled.
            writeText("""misc:
              task-stack-size: 81920
            socks5:
              mtu: 8500
              address: 127.0.0.1
              port: 1080
              udp: udp
            """.trimIndent())
        }
        TProxyService.TProxyStartService(config!!.absolutePath, tun.fd)
    }.onFailure { stop() }

    override fun stop() {
        runCatching { TProxyService.TProxyStopService() }
        runCatching { proxy?.close(proxyFd) }
        runCatching { proxyTask?.get(2, TimeUnit.SECONDS) }
        proxyTask = null
        proxy = null
        proxyFd = -1
        config?.delete(); config = null
    }

    private fun awaitSocksReady(): Boolean {
        // Native startup is asynchronous. Allow roughly ten seconds on slower
        // phones, but return immediately after a complete SOCKS5 handshake.
        Thread.sleep(100)
        repeat(100) {
            if (proxyTask?.isDone == true) return false
            val probe = runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", 1080), 250)
                    socket.soTimeout = 250
                    socket.getOutputStream().write(byteArrayOf(5, 1, 0))
                    socket.getInputStream().let { it.read() == 5 && it.read() == 0 }
                }
            }
            if (probe.getOrDefault(false)) return true
            Thread.sleep(75)
        }
        return false
    }
}
