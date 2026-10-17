package org.localroute.android.tunnel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.system.OsConstants
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import io.nekohasekai.libbox.BridgeOptions
import io.nekohasekai.libbox.BridgeSession
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NeighborUpdateListener
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification as BoxNotification
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.PlatformUser
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import java.io.File
import java.net.NetworkInterface as JavaNetworkInterface

/**
 * A small Android platform bridge for libbox. It deliberately exposes only a
 * VpnService TUN and never accepts remote configuration or analytics.
 */
class VlessVpnService : VpnService(), PlatformInterface, CommandServerHandler {
    companion object {
        const val ACTION_START = "org.localroute.VLESS_START"
        const val ACTION_STOP = "org.localroute.VLESS_STOP"
        private const val CHANNEL = "local_route_vless"
        private const val NOTIFICATION_ID = 17

        @Volatile private var libboxReady = false
        private fun setupLibbox(service: VlessVpnService) = synchronized(VlessVpnService::class.java) {
            if (libboxReady) return@synchronized
            val work = File(service.filesDir, "libbox").apply { mkdirs() }
            val temp = File(service.cacheDir, "libbox").apply { mkdirs() }
            Libbox.setup(SetupOptions().apply {
                basePath = service.filesDir.absolutePath
                workingPath = work.absolutePath
                tempPath = temp.absolutePath
                fixAndroidStack = true
                logMaxLines = 200
                debug = false
                appVersion = "Local Route"
                appMarketingVersion = "Local Route"
            })
            libboxReady = true
        }
    }

    private var commandServer: CommandServer? = null
    private var tun: ParcelFileDescriptor? = null
    private var started = false
    private var defaultInterfaceListener: InterfaceUpdateListener? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val connectivityManager by lazy { getSystemService(ConnectivityManager::class.java) }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "VLESS-маршрут", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = when (intent?.action) {
        ACTION_START -> { start(); START_NOT_STICKY }
        ACTION_STOP -> { stop("VLESS остановлен"); START_NOT_STICKY }
        else -> START_NOT_STICKY
    }

    override fun onRevoke() { stop("Разрешение Android VPN отозвано") }
    override fun onDestroy() { stop(null); super.onDestroy() }

    private fun start() {
        if (started) return
        val store = VlessStore(this)
        val uri = store.uri().orEmpty()
        if (uri.isBlank()) {
            VlessRuntime.update(TunnelState.ConfigRequired, "Сначала сохраните VLESS-ссылку")
            stopSelf()
            return
        }
        VlessRuntime.update(TunnelState.Connecting, "Подключение VLESS…")
        startForegroundCompat(notification("Подключение…"))
        var provisionalServer: CommandServer? = null
        try {
            val endpoint = VlessUriParser.parse(uri)
            setupLibbox(this)
            val server = CommandServer(this, this)
            provisionalServer = server
            server.start()
            server.startOrReloadService(
                VlessConfigCompiler.compile(endpoint, connectivityManager.activeNetwork?.let {
                    connectivityManager.getLinkProperties(it)?.dnsServers?.firstOrNull()?.hostAddress
                } ?: "1.1.1.1", VlessGateway.prepare()),
                OverrideOptions().apply {
                    autoRedirect = false
                }
            )
            commandServer = server
            started = true
            VlessRuntime.update(TunnelState.Connected, "VLESS включён для всего устройства")
            startForegroundCompat(notification("Весь интернет через VLESS"))
        } catch (error: Exception) {
            runCatching { provisionalServer?.closeService() }
            runCatching { provisionalServer?.close() }
            defaultInterfaceListener?.let(::closeDefaultInterfaceMonitor)
            runCatching { tun?.close() }
            tun = null
            VlessGateway.close()
            VlessRuntime.update(TunnelState.Error(error.message ?: "Не удалось запустить VLESS"), "VLESS: ${error.message ?: "ошибка запуска"}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stop(message: String?) {
        val wasStarted = started
        val server = commandServer
        commandServer = null
        runCatching { server?.closeService() }
        runCatching { server?.close() }
        defaultInterfaceListener?.let(::closeDefaultInterfaceMonitor)
        runCatching { tun?.close() }
        tun = null
        started = false
        VlessGateway.close()
        if (wasStarted) {
            VlessRuntime.update(TunnelState.Disconnected, message ?: "VLESS остановлен")
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (message != null) VlessRuntime.update(TunnelState.Disconnected, message)
        stopSelf()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, org.localroute.android.MainActivity::class.java).putExtra("open_tab", 2), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, VlessVpnService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(org.localroute.android.R.drawable.ic_local_route)
            .setContentTitle("Local Route · VLESS")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Остановить", stop)
            .build()
    }

    override fun openTun(options: TunOptions): Int {
        check(prepare(this) == null) { "Не выдано разрешение Android VPN" }
        val builder = Builder().setSession("Local Route · VLESS").setMtu(options.mtu)
        val addresses = options.inet4Address
        while (addresses.hasNext()) {
            val address = addresses.next()
            builder.addAddress(address.address(), address.prefix())
        }
        val ipv6 = options.inet6Address
        while (ipv6.hasNext()) {
            val address = ipv6.next()
            builder.addAddress(address.address(), address.prefix())
        }
        if (options.autoRoute) {
            val dns = options.dnsServerAddress
            while (dns.hasNext()) builder.addDnsServer(dns.next())
            val routes = options.inet4RouteRange
            while (routes.hasNext()) {
                val address = routes.next()
                builder.addRoute(address.address(), address.prefix())
            }
            val routes6 = options.inet6RouteRange
            while (routes6.hasNext()) {
                val address = routes6.next()
                builder.addRoute(address.address(), address.prefix())
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        // Libbox and TGWs use protected/explicit outbound sockets, never their own TUN.
        addDisallowed(builder, packageName)
        return (builder.establish() ?: error("Android не создал VPN-интерфейс")).also { tun = it }.fd
    }

    private fun addAllowed(builder: Builder, packageName: String) = try { builder.addAllowedApplication(packageName) } catch (_: NameNotFoundException) { }
    private fun addDisallowed(builder: Builder, packageName: String) = try { builder.addDisallowedApplication(packageName) } catch (_: NameNotFoundException) { }

    override fun autoDetectInterfaceControl(fd: Int) { check(protect(fd)) { "Не удалось защитить исходящий сокет" } }
    override fun cancelNotification(identifier: String, typeID: Int) = Unit
    override fun checkPlatformShell() = Unit
    override fun clearDNSCache() = Unit
    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        if (defaultInterfaceListener !== listener) return
        defaultInterfaceListener = null
        networkCallback?.let { callback ->
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
        networkCallback = null
    }
    override fun closeNeighborMonitor(listener: NeighborUpdateListener) = Unit
    override fun createBridge(options: BridgeOptions): BridgeSession = throw UnsupportedOperationException("Bridge не используется")
    override fun findConnectionOwner(ipProtocol: Int, sourceAddress: String, sourcePort: Int, destinationAddress: String, destinationPort: Int): ConnectionOwner {
        val uid = if (Build.VERSION.SDK_INT >= 29) connectivityManager.getConnectionOwnerUid(ipProtocol,
            java.net.InetSocketAddress(sourceAddress, sourcePort), java.net.InetSocketAddress(destinationAddress, destinationPort)) else -1
        val packages = if (uid >= 0) packageManager.getPackagesForUid(uid).orEmpty().toList() else emptyList()
        return ConnectionOwner().apply { userId = uid; userName = packages.firstOrNull().orEmpty(); setAndroidPackageNames(StringArray(packages.iterator())) }
    }
    @Suppress("DEPRECATION")
    override fun getInterfaces(): NetworkInterfaceIterator {
        val interfaces = connectivityManager.allNetworks.mapNotNull { network ->
            val properties = connectivityManager.getLinkProperties(network) ?: return@mapNotNull null
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@mapNotNull null
            val native = JavaNetworkInterface.getByName(properties.interfaceName) ?: return@mapNotNull null
            io.nekohasekai.libbox.NetworkInterface().apply {
                name = native.name
                index = native.index
                mtu = native.mtu
                addresses = StringArray(properties.linkAddresses.map { it.toString() }.iterator())
                dnsServer = StringArray(properties.dnsServers.mapNotNull { it.hostAddress }.iterator())
                type = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                    else -> Libbox.InterfaceTypeOther
                }
                flags = OsConstants.IFF_UP or OsConstants.IFF_RUNNING
                metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
        }.iterator()
        return object : NetworkInterfaceIterator {
            override fun hasNext() = interfaces.hasNext()
            override fun next() = interfaces.next()
        }
    }
    override fun includeAllNetworks(): Boolean = false
    override fun localDNSTransport(): LocalDNSTransport? = null
    override fun lookupSFTPServer(): String = ""
    override fun lookupUser(username: String): PlatformUser = throw UnsupportedOperationException("Пользователи ОС не используются")
    override fun openShellSession(user: PlatformUser, command: String, arguments: StringIterator, directory: String, rows: Int, cols: Int): ShellSession = throw UnsupportedOperationException("Shell не используется")
    override fun readSystemSSHHostKey(): String = ""
    override fun readWIFIState(): WIFIState = WIFIState("", "")
    override fun registerMyInterface(name: String) = Unit
    override fun sendNotification(notification: BoxNotification) = Unit
    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        closeDefaultInterfaceMonitor(defaultInterfaceListener ?: listener)
        defaultInterfaceListener = listener
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = updateDefaultInterface(network)
            override fun onLost(network: Network) = updateDefaultInterface(connectivityManager.activeNetwork)
            override fun onLinkPropertiesChanged(network: Network, properties: android.net.LinkProperties) = updateDefaultInterface(network)
        }
        networkCallback = callback
        // The global default can become our own VPN, even though our UID is excluded.
        // Only physical Internet networks may become libbox's outbound interface.
        connectivityManager.registerNetworkCallback(NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), callback)
        updateDefaultInterface(connectivityManager.activeNetwork)
    }
    override fun startNeighborMonitor(listener: NeighborUpdateListener) = Unit
    override fun tailscaleHostname(): String = ""
    override fun underNetworkExtension(): Boolean = false
    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true
    override fun usePlatformBridge(): Boolean = false
    override fun usePlatformShell(): Boolean = false
    override fun useProcFS(): Boolean = false

    override fun connectSSHAgent(): Int = -1
    override fun getSystemProxyStatus(): SystemProxyStatus = SystemProxyStatus().apply { available = false; enabled = false }
    override fun serviceReload() = Unit
    override fun serviceStop() { stop(null) }
    override fun setSystemProxyEnabled(isEnabled: Boolean) = Unit
    override fun triggerNativeCrash() = Unit
    override fun writeDebugMessage(message: String) = Unit

    private fun updateDefaultInterface(network: Network?) {
        val listener = defaultInterfaceListener ?: return
        val physical = network?.takeIf { connectivityManager.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) == true }
            ?: connectivityManager.allNetworks.firstOrNull { connectivityManager.getNetworkCapabilities(it)?.let { caps ->
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } == true }
        val interfaceName = physical?.let { connectivityManager.getLinkProperties(it)?.interfaceName }.orEmpty()
        val index = runCatching { JavaNetworkInterface.getByName(interfaceName)?.index ?: -1 }.getOrDefault(-1)
        listener.updateDefaultInterface(interfaceName, index, false, false)
    }

    private class StringArray(private val iterator: Iterator<String>) : StringIterator {
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun len(): Int = 0
        override fun next(): String = iterator.next()
    }
}
