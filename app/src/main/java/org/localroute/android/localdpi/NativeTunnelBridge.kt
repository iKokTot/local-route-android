package org.localroute.android.localdpi

import android.content.Context
import android.os.ParcelFileDescriptor
import io.github.dovecoteescapee.byedpi.core.ByeDpiProxy
import io.github.dovecoteescapee.byedpi.core.TProxyService
import org.localroute.android.core.DpiStrategy
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future

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
        // ByeDPIAndroid passes the user's command line verbatim (apart from
        // argv[0]). ByeDPI already defaults to 127.0.0.1:1080. Injecting
        // --no-ipv6 here changed the strategy and broke mobile networks where
        // the same pasted line works in the reference application.
        val fd = localProxy.create(strategy.nativeArguments.toTypedArray())
        check(fd >= 0) { "ByeDPI не принял параметры стратегии" }
        proxy = localProxy
        proxyFd = fd
        proxyTask = executor.submit { localProxy.run(fd) }
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
        // The reference client joins the native event loop before allowing a
        // new strategy to start. A timed join could leave the old global
        // ByeDPI state alive while parse_args configured the next strategy.
        runCatching { proxyTask?.get() }
        proxyTask = null
        proxy = null
        proxyFd = -1
        config?.delete(); config = null
    }
}
