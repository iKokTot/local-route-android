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


import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.localroute.android.core.*
import org.localroute.android.data.ProfileStore
import org.localroute.android.localdpi.*
import io.github.dovecoteescapee.byedpi.core.ByeDpiProxy

/** One Android TUN, independent local DPI and user-server outputs. */
class CombinedVpnService : VpnService(), PlatformInterface, CommandServerHandler {
    companion object {
        const val START_DPI = "org.localroute.COMBINED_DPI_START"
        const val STOP_DPI = "org.localroute.COMBINED_DPI_STOP"
        const val START_SERVER = "org.localroute.COMBINED_SERVER_START"
        const val STOP_SERVER = "org.localroute.COMBINED_SERVER_STOP"
        const val STOP_ALL = "org.localroute.COMBINED_STOP"
        const val RELOAD = "org.localroute.COMBINED_RELOAD"
        private const val CHANNEL = "local_route_combined"
        private val sessionMutex = Mutex()
        private var libboxReady = false
        private var owner: CombinedVpnService? = null
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var plan = CombinedRouting()
    private var commandServer: CommandServer? = null
    private var tun: ParcelFileDescriptor? = null
    private var defaultInterfaceListener: InterfaceUpdateListener? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val connectivityManager by lazy { getSystemService(ConnectivityManager::class.java) }
    private val proxy = ByeDpiProxy()
    private var proxyJob: Job? = null
    private var proxyGeneration = 0
    private var strategy: DpiStrategy? = null
    private var activeEndpoint: VlessEndpoint? = null
    private var shuttingDown = false
    private var testPackage: String? = null
    private var openingServerEnabled = false
    private var diagnosticClient: io.nekohasekai.libbox.CommandClient? = null
    private var physicalNetwork: Network? = null
    private var physicalInterface: String? = null
    private var networkRevision = 0L

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "DPI и сервер", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CombinedRuntime.queued()
        foreground("Настройка маршрутов…")
        scope.launch {
            sessionMutex.withLock {
                try { withContext(Dispatchers.IO) { applyCommand(intent, startId) } }
                catch (error: IllegalArgumentException) {
                    if (intent?.action == START_DPI) LocalDpiRuntime.update(if (plan.dpiEnabled) EnginePhase.ACTIVE else EnginePhase.ERROR,
                        "DPI не изменён: ${error.message}")
                    else VlessRuntime.update(if (plan.serverEnabled) TunnelState.Connected else TunnelState.InvalidConfig(error.message.orEmpty()),
                        "Настройки не применены: ${error.message}. Предыдущие маршруты сохранены.")
                    if (!plan.requiresVpn) { VlessGateway.close(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelfResult(startId) }
                    else foreground("Предыдущий маршрут сохранён")
                }
                catch (error: Exception) {
                    withContext(Dispatchers.IO) { runCatching { shutdown() } }
                    LocalDpiRuntime.update(EnginePhase.ERROR, "Общий VPN остановлен: ${error.message}")
                    VlessRuntime.update(TunnelState.Error(error.message.orEmpty()), "Не удалось применить маршруты: ${error.message}")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    notifyFailure()
                    stopSelfResult(startId)
                }
                finally { CombinedRuntime.completed(plan) }
            }
        }
        return START_NOT_STICKY
    }
    private suspend fun applyCommand(intent: Intent?, startId: Int) {
        if (owner !== this) {
            owner?.shutdown()
            owner = this
        }
        val profile = ProfileStore(this)
        val requested = when (intent?.action) {
            START_DPI -> plan.copy(dpiEnabled = true, services = profile.selectedServices())
            STOP_DPI -> plan.copy(dpiEnabled = false)
            START_SERVER -> plan.copy(serverEnabled = true)
            STOP_SERVER -> plan.copy(serverEnabled = false)
            STOP_ALL -> plan.copy(dpiEnabled = false, serverEnabled = false)
            else -> plan
        }
        val next = requested.copy(services = profile.selectedServices(), customDpiDomains = profile.customDomains(), serverRoutes = VlessStore(this).routes())
        if (!next.requiresVpn) { shutdown(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelfResult(startId); return }
        require(Build.VERSION.SDK_INT >= 29 || next.serverRoutes.packages.isEmpty()) {
            "Правила VPN по приложениям требуют Android 10 или новее. Используйте доменные правила."
        }
        if (intent?.action == START_DPI) require(next.services.isNotEmpty() || next.customDpiDomains.isNotEmpty()) { "Выберите сервис для DPI" }
        // Validate before mutating workers or replacing a healthy routing session.
        val endpoint = if (next.serverEnabled) {
            if (intent?.action == START_SERVER || !plan.serverEnabled) VlessUriParser.parse(VlessStore(this).uri().orEmpty())
            else activeEndpoint ?: VlessUriParser.parse(VlessStore(this).uri().orEmpty())
        } else null
        val requestedStrategy = if (next.dpiEnabled) {
            val args = intent?.getStringArrayExtra(LocalDpiVpnService.EXTRA_STRATEGY_ARGUMENTS)
            if (intent?.action == START_DPI && args != null) DpiStrategy(
                intent.getStringExtra(LocalDpiVpnService.EXTRA_STRATEGY) ?: "custom-command",
                intent.getStringExtra(LocalDpiVpnService.EXTRA_STRATEGY_TITLE) ?: "DPI", "", args.toList())
            else strategy ?: AndroidStrategies.resolve(profile.lastSuccessfulStrategy(), profile.customCommand()) ?: AndroidStrategies.all.first()
        } else null
        setupBox()
        val networks = connectivityManager.allNetworks.filter { network ->
            connectivityManager.getNetworkCapabilities(network)?.let {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } == true
        }
        val physical = PhysicalNetworkSelection.choose(networks.map {
            PhysicalNetworkSelection.Candidate(it, connectivityManager.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
        }, connectivityManager.activeNetwork, physicalNetwork)
        val dns = PhysicalDns.choose(physical?.let { connectivityManager.getLinkProperties(it)?.dnsServers?.mapNotNull { address -> address.hostAddress } }.orEmpty())
        val password = if (next.serverEnabled) VlessGateway.password ?: VlessGateway.prepare() else null
        if (org.localroute.android.BuildConfig.DEBUG && intent?.hasExtra("test_package") == true) testPackage = intent.getStringExtra("test_package")
        val config = CombinedConfigCompiler.compile(next, endpoint, dns, password, testPackage?.let { setOf(it) }.orEmpty())
            .let { if (org.localroute.android.BuildConfig.DEBUG) it.replace("\"level\":\"warn\"", "\"level\":\"debug\"") else it }
        Libbox.checkConfig(config)
        VpnDiagnostics.add("Маршрут: DPI=${next.dpiEnabled}, VLESS=${next.serverEnabled}; Android ${Build.VERSION.SDK_INT}; DNS=$dns; исключений=${org.localroute.android.data.VpnExclusions(this).packages().size}")
        if (next.dpiEnabled && (proxyJob == null || strategy != requestedStrategy)) {
            stopProxy()
            LocalDpiRuntime.update(EnginePhase.STARTING, "Запуск локального DPI…", requestedStrategy?.id)
            val generation = ++proxyGeneration
            proxyJob = scope.launch(Dispatchers.IO) {
                val code = runCatching { proxy.startProxy(requireNotNull(requestedStrategy).nativeArguments.toTypedArray()) }.getOrElse { -1 }
                scope.launch {
                    if (generation == proxyGeneration && !shuttingDown) sessionMutex.withLock {
                        // A local worker failure must not unnecessarily stop a healthy server mode.
                        val retainedServer = plan.serverEnabled && runCatching {
                            withContext(Dispatchers.IO) { applyCommand(Intent().setAction(STOP_DPI), 0) }
                        }.isSuccess
                        if (!retainedServer) {
                            withContext(Dispatchers.IO) { runCatching { shutdown() } }
                            stopForeground(STOP_FOREGROUND_REMOVE); notifyFailure(); stopSelf()
                        }
                        LocalDpiRuntime.update(EnginePhase.ERROR, if (retainedServer)
                            "DPI завершился: $code. Серверный VPN продолжает работать." else "Локальный движок завершился: $code. VPN остановлен.")
                        CombinedRuntime.completed(plan)
                    }
                }
            }
            withContext(Dispatchers.IO) {
                val deadline = System.nanoTime() + 4_000_000_000L
                var ready = false
                while (!ready && System.nanoTime() < deadline) {
                    ready = runCatching { java.net.Socket().use { socket ->
                        socket.connect(java.net.InetSocketAddress("127.0.0.1", 1080), 200)
                        socket.soTimeout = 300
                        socket.getOutputStream().write(byteArrayOf(5, 1, 0))
                        val input = socket.getInputStream()
                        input.read() == 5 && input.read() == 0
                    } }.getOrDefault(false)
                    if (!ready) delay(50)
                }
                check(ready) { "ByeDPI не открыл SOCKS5" }
            }
            strategy = requestedStrategy
        }
        if (next.serverEnabled && !plan.serverEnabled) VlessRuntime.update(TunnelState.Connecting, "Применение серверного маршрута…")
        val server = commandServer ?: CommandServer(this, this).also { commandServer = it; it.start() }
        openingServerEnabled = next.serverEnabled
        server.startOrReloadService(config, OverrideOptions().apply { autoRedirect = false })
        runCatching {
            diagnosticClient?.disconnect()
            diagnosticClient = io.nekohasekai.libbox.CommandClient(VpnDiagnostics.handler(), io.nekohasekai.libbox.CommandClientOptions().apply { addCommand(Libbox.CommandLog) }).also { it.connect() }
        }.onFailure { VpnDiagnostics.add("Не удалось подключить журнал движка: ${it.message}") }
        if (!next.dpiEnabled) stopProxy()
        if (!next.serverEnabled) VlessGateway.close()
        plan = next
        activeEndpoint = endpoint
        LocalDpiRuntime.update(if (plan.dpiEnabled) EnginePhase.ACTIVE else EnginePhase.STOPPED,
            if (plan.dpiEnabled && plan.serverEnabled) "Выбранные сервисы → DPI на телефоне"
            else if (plan.dpiEnabled) "Локальный DPI · без сервера" else "DPI выключен", strategy?.id)
        VlessRuntime.update(if (plan.serverEnabled) TunnelState.Connected else TunnelState.Disconnected,
            if (plan.serverEnabled && plan.dpiEnabled) "DPI → локально; VPN → по сохранённым правилам"
            else if (plan.serverEnabled) "VPN → по сохранённым правилам" else "Серверный режим выключен")
        foreground(if (plan.dpiEnabled && plan.serverEnabled) "DPI + VLESS" else if (plan.dpiEnabled) "Только DPI" else "Только VLESS")
    }
    private fun setupBox() {
        if (libboxReady) return
        val work = File(filesDir, "libbox").apply { mkdirs() }
        val temp = File(cacheDir, "libbox").apply { mkdirs() }
        Libbox.setup(SetupOptions().apply {
            basePath = filesDir.absolutePath; workingPath = work.absolutePath; tempPath = temp.absolutePath
            fixAndroidStack = true; logMaxLines = 200; appVersion = "Local Route"; appMarketingVersion = "Local Route"
        })
        libboxReady = true
    }
    private suspend fun stopProxy() {
        proxyGeneration++
        withContext(Dispatchers.IO) {
            check(proxy.stopProxy() == 0) { "Не удалось остановить ByeDPI" }
            withTimeout(3000) { proxyJob?.join() }
        }
        proxyJob = null
        strategy = null
    }
    private suspend fun shutdown() {
        shuttingDown = true
        runCatching { diagnosticClient?.disconnect() }; diagnosticClient = null
        runCatching { tun?.close() }; tun = null
        runCatching { commandServer?.closeService() }
        runCatching { commandServer?.close() }; commandServer = null
        defaultInterfaceListener?.let(::closeDefaultInterfaceMonitor)
        runCatching { tun?.close() }; tun = null
        try { stopProxy() } finally {
            plan = CombinedRouting(); testPackage = null; activeEndpoint = null
            if (owner === this) {
                VlessGateway.close()
                CombinedRuntime.stopped()
                LocalDpiRuntime.update(EnginePhase.STOPPED, "DPI выключен", null)
                VlessRuntime.update(TunnelState.Disconnected, "Серверный режим выключен")
                owner = null
            }
            shuttingDown = false
        }
    }
    private fun stop(message: String?) {
        scope.launch { sessionMutex.withLock { withContext(Dispatchers.IO) { runCatching { shutdown() } }; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() } }
    }
    override fun onRevoke() { stop("VPN отозван") }
    override fun onDestroy() {
        scope.launch { sessionMutex.withLock { withContext(Dispatchers.IO) { runCatching { shutdown() } } }; scope.cancel() }
        super.onDestroy()
    }
    private fun foreground(text: String) {
        getSystemService(NotificationManager::class.java).cancel(28)
        fun action(command: String, request: Int) = PendingIntent.getService(this, request,
            Intent(this, CombinedVpnService::class.java).setAction(command), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 70, Intent(this, org.localroute.android.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(org.localroute.android.R.drawable.ic_local_route)
            .setContentTitle("Local Route").setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
        if (plan.dpiEnabled) builder.addAction(0, "DPI выкл.", action(STOP_DPI, 71))
        if (plan.serverEnabled) builder.addAction(0, "VLESS выкл.", action(STOP_SERVER, 72))
        builder.addAction(0, "Выключить всё", action(STOP_ALL, 73))
        if (Build.VERSION.SDK_INT >= 34) startForeground(27, builder.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(27, builder.build())
    }
    private fun notifyFailure() {
        val open = PendingIntent.getActivity(this, 74, Intent(this, org.localroute.android.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        getSystemService(NotificationManager::class.java).notify(28, NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(org.localroute.android.R.drawable.ic_local_route).setContentTitle("Local Route остановлен")
            .setContentText("Ошибка маршрутов. Откройте приложение перед продолжением работы.")
            .setContentIntent(open).setAutoCancel(true).build())
    }

    override fun openTun(options: TunOptions): Int {
        check(prepare(this) == null) { "Не выдано разрешение Android VPN" }
        val builder = Builder().setSession("Local Route · DPI / VLESS").setMtu(options.mtu)
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
        if (RecommendedBypassApps.applies(openingServerEnabled))
            org.localroute.android.data.VpnExclusions(this).packages().filter { it != packageName }.forEach { addDisallowed(builder, it) }
        tun?.close()
        return (builder.establish() ?: error("Android не создал VPN-интерфейс")).also {
            tun = it
        }.fd
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
        physicalNetwork = null
        physicalInterface = null
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
            override fun onLost(network: Network) = updateDefaultInterface(network, lost = true)
            override fun onLinkPropertiesChanged(network: Network, properties: android.net.LinkProperties) = updateDefaultInterface(network)
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = updateDefaultInterface(network)
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
    // libbox calls this during startOrReloadService: close the old core, not the Android service.
    // Mirrors SFA BoxService.serviceStop, and must not queue a full shutdown after reload.
    override fun serviceStop() {
        runCatching { tun?.close() }; tun = null
        runCatching { commandServer?.closeService() }
    }
    override fun setSystemProxyEnabled(isEnabled: Boolean) = Unit
    override fun triggerNativeCrash() = Unit
    override fun writeDebugMessage(message: String) = Unit

    private fun updateDefaultInterface(network: Network?, lost: Boolean = false) {
        val listener = defaultInterfaceListener ?: return
        val candidates = connectivityManager.allNetworks.mapNotNull { candidate ->
            if (lost && candidate == network) return@mapNotNull null
            val caps = connectivityManager.getNetworkCapabilities(candidate) ?: return@mapNotNull null
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) ||
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@mapNotNull null
            PhysicalNetworkSelection.Candidate(candidate, caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        }
        val physical = PhysicalNetworkSelection.choose(candidates, connectivityManager.activeNetwork, physicalNetwork)
        CombinedRuntime.networkAvailable(physical != null)
        val interfaceName = physical?.let { connectivityManager.getLinkProperties(it)?.interfaceName }.orEmpty()
        val index = runCatching { JavaNetworkInterface.getByName(interfaceName)?.index ?: -1 }.getOrDefault(-1)
        if (physical != physicalNetwork || physicalInterface != interfaceName) {
            val initialized = physicalInterface != null
            physicalNetwork = physical
            physicalInterface = interfaceName
            val revision = ++networkRevision
            VlessRuntime.clearCheck()
            VpnDiagnostics.add(if (physical == null) "Нет физической сети. Ожидаем подключения." else "Исходящая сеть изменена: $interfaceName")
            setUnderlyingNetworks(physical?.let { arrayOf(it) })
            // Recompile physical DNS too: a Wi-Fi resolver is often unreachable on cellular.
            // Coalesce bursts; initial monitor callbacks during reload must not trigger a loop.
            if (initialized && physical != null && plan.requiresVpn) scope.launch {
                delay(750)
                if (revision == networkRevision && owner === this@CombinedVpnService && plan.requiresVpn && !shuttingDown)
                    startService(Intent(this@CombinedVpnService, CombinedVpnService::class.java).setAction(RELOAD))
            }
        }
        listener.updateDefaultInterface(interfaceName, index, false, false)
    }

    private class StringArray(private val iterator: Iterator<String>) : StringIterator {
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun len(): Int = 0
        override fun next(): String = iterator.next()
    }
}


