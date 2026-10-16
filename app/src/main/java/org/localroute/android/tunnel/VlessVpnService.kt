package org.localroute.android.tunnel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
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

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "VLESS-маршрут", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = when (intent?.action) {
        ACTION_START -> { start(); START_STICKY }
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
        val route = store.routeProfile()
        if (!route.routeAllApplications && route.selectedPackages.isEmpty()) {
            VlessRuntime.update(TunnelState.InvalidConfig("Не выбраны приложения"), "Выберите хотя бы одно приложение")
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
                VlessConfigCompiler.compile(endpoint),
                OverrideOptions().apply {
                    autoRedirect = false
                    if (!route.routeAllApplications) includePackage = StringArray(route.selectedPackages.iterator())
                }
            )
            commandServer = server
            started = true
            VlessRuntime.update(TunnelState.Connected, if (route.routeAllApplications) "VLESS активен для всего интернета" else "VLESS активен для выбранных приложений")
            startForegroundCompat(notification("Выбранные приложения через VLESS"))
        } catch (error: Exception) {
            runCatching { provisionalServer?.closeService() }
            runCatching { provisionalServer?.close() }
            VlessRuntime.update(TunnelState.Error(error.message ?: "Не удалось запустить VLESS"), "VLESS: ${error.message ?: "ошибка запуска"}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stop(message: String?) {
        if (!started && commandServer == null && tun == null) return
        val server = commandServer
        commandServer = null
        runCatching { server?.closeService() }
        runCatching { server?.close() }
        runCatching { tun?.close() }
        tun = null
        started = false
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
        val open = PendingIntent.getActivity(this, 0, Intent(this, org.localroute.android.MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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
            .addAddress("198.18.0.1", 30).addRoute("0.0.0.0", 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        val include = options.includePackage
        if (include.hasNext()) {
            while (include.hasNext()) addAllowed(builder, include.next())
        } else {
            // The engine's own sockets must never re-enter its TUN in whole-device mode.
            addDisallowed(builder, packageName)
        }
        return (builder.establish() ?: error("Android не создал VPN-интерфейс")).also { tun = it }.fd
    }

    private fun addAllowed(builder: Builder, packageName: String) = try { builder.addAllowedApplication(packageName) } catch (_: NameNotFoundException) { }
    private fun addDisallowed(builder: Builder, packageName: String) = try { builder.addDisallowedApplication(packageName) } catch (_: NameNotFoundException) { }

    override fun autoDetectInterfaceControl(fd: Int) { protect(fd) }
    override fun cancelNotification(identifier: String, typeID: Int) = Unit
    override fun checkPlatformShell() = Unit
    override fun clearDNSCache() = Unit
    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) = Unit
    override fun closeNeighborMonitor(listener: NeighborUpdateListener) = Unit
    override fun createBridge(options: BridgeOptions): BridgeSession = throw UnsupportedOperationException("Bridge не используется")
    override fun findConnectionOwner(ipProtocol: Int, sourceAddress: String, sourcePort: Int, destinationAddress: String, destinationPort: Int): ConnectionOwner = throw UnsupportedOperationException("Поиск владельца не требуется")
    override fun getInterfaces(): NetworkInterfaceIterator = EmptyNetworkInterfaces
    override fun includeAllNetworks(): Boolean = false
    override fun localDNSTransport(): LocalDNSTransport? = null
    override fun lookupSFTPServer(): String = ""
    override fun lookupUser(username: String): PlatformUser = throw UnsupportedOperationException("Пользователи ОС не используются")
    override fun openShellSession(user: PlatformUser, command: String, arguments: StringIterator, directory: String, rows: Int, cols: Int): ShellSession = throw UnsupportedOperationException("Shell не используется")
    override fun readSystemSSHHostKey(): String = ""
    override fun readWIFIState(): WIFIState = WIFIState("", "")
    override fun registerMyInterface(name: String) = Unit
    override fun sendNotification(notification: BoxNotification) = Unit
    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) = Unit
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

    private class StringArray(private val iterator: Iterator<String>) : StringIterator {
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun len(): Int = 0
        override fun next(): String = iterator.next()
    }
    private object EmptyNetworkInterfaces : NetworkInterfaceIterator {
        override fun hasNext(): Boolean = false
        override fun next(): io.nekohasekai.libbox.NetworkInterface = throw NoSuchElementException()
    }
}
