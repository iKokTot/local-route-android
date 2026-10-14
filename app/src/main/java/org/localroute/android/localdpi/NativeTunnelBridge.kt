package org.localroute.android.localdpi

import android.content.Context
import android.os.ParcelFileDescriptor
import io.github.dovecoteescapee.byedpi.core.ByeDpiProxy
import io.github.dovecoteescapee.byedpi.core.TProxyService
import org.localroute.android.core.DpiStrategy
import java.io.File
import java.util.concurrent.Executors

/** Local-only chain: TUN fd -> hev-socks5-tunnel -> 127.0.0.1 ByeDPI SOCKS5. */
interface NativeTunnelBridge {
    fun start(tun: ParcelFileDescriptor, strategy: DpiStrategy): Result<Unit>
    fun stop()
}

class AndroidNativeTunnelBridge(private val context: Context) : NativeTunnelBridge {
    private val executor = Executors.newSingleThreadExecutor()
    private var proxy: ByeDpiProxy? = null
    private var config: File? = null

    override fun start(tun: ParcelFileDescriptor, strategy: DpiStrategy): Result<Unit> = runCatching {
        check(proxy == null) { "Локальный движок уже запущен" }
        val localProxy = ByeDpiProxy()
        // getopt_long treats element zero as the executable name. Without this
        // placeholder the first real option (--ip) is silently skipped.
        // IPv4 is deliberate: Android emulators and many mobile networks expose
        // unusable IPv6 answers first, which otherwise leaves SOCKS CONNECT hanging.
        val args = arrayOf("ciadpi", "--ip", "127.0.0.1", "--port", "1080", "--no-ipv6", *strategy.nativeArguments.toTypedArray())
        check(localProxy.open(args) >= 0) { "ByeDPI не открыл локальный SOCKS5-порт" }
        proxy = localProxy
        executor.execute { localProxy.run() }
        config = File.createTempFile("local-route-tun2socks-", ".yml", context.cacheDir).apply {
            writeText("""tunnel:
              mtu: 1500
              ipv4: 10.7.0.2
            socks5:
              address: 127.0.0.1
              port: 1080
              udp: udp
            misc:
              log-level: warn
            """.trimIndent())
        }
        TProxyService.TProxyStartService(config!!.absolutePath, tun.fd)
    }.onFailure { stop() }

    override fun stop() {
        runCatching { TProxyService.TProxyStopService() }
        runCatching { proxy?.close() }
        proxy = null
        config?.delete(); config = null
    }
}
