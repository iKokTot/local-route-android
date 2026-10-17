package org.localroute.android

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon as AndroidIcon
import android.net.VpnService
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.localroute.android.core.AndroidStrategies
import org.localroute.android.core.ConnectivityProbe
import org.localroute.android.core.DpiStrategy
import org.localroute.android.core.ServiceTarget
import org.localroute.android.data.ProfileStore
import org.localroute.android.localdpi.EnginePhase
import org.localroute.android.localdpi.LocalDpiRuntime
import org.localroute.android.localdpi.LocalDpiVpnService
import org.localroute.android.localdpi.LocalRouteTileService
import org.localroute.android.tunnel.TunnelState
import org.localroute.android.tunnel.CombinedVpnService
import org.localroute.android.tunnel.VlessRuntime
import org.localroute.android.tunnel.VlessStore
import org.localroute.android.tunnel.VlessVpnService
import org.localroute.android.telegram.TelegramProxyRuntime
import org.localroute.android.telegram.TelegramProxyPhase
import org.localroute.android.telegram.TelegramProxyService
import org.localroute.android.telegram.TelegramProxySettings
import org.localroute.android.shortcuts.ShortcutMode
import org.localroute.android.shortcuts.TelegramTileService
import org.localroute.android.shortcuts.VlessTileService


class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_START_FROM_TILE = "start_from_tile" }

    private lateinit var sharedHost: SharedRouteHost
    private var pendingVpnRequest = false
    private var strategyToStart: DpiStrategy? = null
    private var vlessStartRequested = false
    private lateinit var vlessStore: VlessStore
    private var requestedTab = 0
    private val telegramNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        startTelegramProxy()
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        continueVpnRequest()
    }
    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) startLocalService()
        else if (vlessStartRequested) {
            pendingVpnRequest = false
            vlessStartRequested = false
            VlessRuntime.update(TunnelState.Error("Разрешение Android VPN не выдано"), "Разрешение Android VPN не выдано")
        } else LocalDpiRuntime.update(EnginePhase.ERROR, "Разрешение Android VPN не выдано")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ProfileStore(this)
        vlessStore = VlessStore(this)
        sharedHost = SharedRouteHost(this, ::requestVpn,
            { cancelPendingVpn(); stopVpn() }, ::requestVless,
            { cancelPendingVpn(); startService(Intent(this, CombinedVpnService::class.java).setAction(CombinedVpnService.STOP_SERVER)) },
            ::requestTelegramProxy,
            { startService(Intent(this, TelegramProxyService::class.java).setAction(TelegramProxyService.STOP)) },
            ::requestQuickTile)
        val sharedContainer = android.widget.FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(17,20,24))
            addView(sharedHost.webView,android.widget.FrameLayout.LayoutParams(-1,-1))
        }
        setContentView(sharedContainer)
        window.statusBarColor = android.graphics.Color.rgb(17,20,24)
        window.navigationBarColor = android.graphics.Color.rgb(25,28,32)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(sharedContainer) { view,insets ->
            val bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val ime=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left,bars.top,bars.right,maxOf(bars.bottom,ime.bottom))
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(sharedContainer)
        onBackPressedDispatcher.addCallback(this,object: androidx.activity.OnBackPressedCallback(true){ override fun handleOnBackPressed(){sharedHost.back()} })
        handleShortcut(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcut(intent)
    }

    override fun onDestroy() { sharedHost.destroy(); super.onDestroy() }

    private fun cancelPendingVpn() { pendingVpnRequest = false; vlessStartRequested = false; strategyToStart = null }

    private fun handleShortcut(intent: Intent) {
        requestedTab = if (intent.getBooleanExtra("open_telegram", false)) 1 else intent.getIntExtra("open_tab", 0).coerceIn(0, 2)
        if(intent.hasExtra("open_tab") || intent.hasExtra("open_telegram") || intent.hasExtra("start_mode")) sharedHost.showMode(requestedTab)
        val mode = intent.getStringExtra("start_mode")
        if (mode == "VLESS") requestVless()
        else if (mode == "TG") requestTelegramProxy()
        else if (mode == "DPI" || intent.getBooleanExtra(EXTRA_START_FROM_TILE, false)) {
            val store = ProfileStore(this)
            val strategy = AndroidStrategies.resolve(store.lastSuccessfulStrategy(), store.customCommand())
                ?: AndroidStrategies.all.first()
            requestVpn(store.selectedServices(), strategy)
        }
    }

    private fun requestVpn(services: Set<ServiceTarget>, strategy: DpiStrategy) {
        pendingVpnRequest = true
        vlessStartRequested = false
        strategyToStart = strategy
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            continueVpnRequest()
        }
    }

    private fun requestTelegramProxy() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            telegramNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startTelegramProxy()
    }

    private fun startTelegramProxy() = ContextCompat.startForegroundService(this, Intent(this, TelegramProxyService::class.java).setAction(TelegramProxyService.START))

    private fun continueVpnRequest() {
        if (!pendingVpnRequest) return
        val consent = VpnService.prepare(this)
        if (consent == null) startLocalService() else vpnConsent.launch(consent)
    }

    private fun startLocalService() {
        if (!pendingVpnRequest) return
        pendingVpnRequest = false
        if (vlessStartRequested) {
            vlessStartRequested = false
            ContextCompat.startForegroundService(this, Intent(this, CombinedVpnService::class.java).setAction(CombinedVpnService.START_SERVER))
            return
        }
        val strategy = strategyToStart ?: AndroidStrategies.all.first()
        ContextCompat.startForegroundService(this, Intent(this, CombinedVpnService::class.java)
            .setAction(CombinedVpnService.START_DPI)
            .putExtra(LocalDpiVpnService.EXTRA_STRATEGY, strategy.id)
            .putExtra(LocalDpiVpnService.EXTRA_STRATEGY_TITLE, strategy.title)
            .putExtra(LocalDpiVpnService.EXTRA_STRATEGY_ARGUMENTS, strategy.nativeArguments.toTypedArray()))
    }

    private fun saveVless(uri: String): Boolean {
        return try {
            org.localroute.android.tunnel.VlessUriParser.parse(uri)
            vlessStore.save(uri)
            VlessRuntime.update(TunnelState.Disconnected, "VLESS-профиль сохранён на устройстве")
            true
        } catch (error: IllegalArgumentException) {
            VlessRuntime.update(TunnelState.InvalidConfig(error.message.orEmpty()), error.message ?: "Некорректная VLESS-ссылка")
            false
        }
    }

    private fun requestVless() {
        val uri = vlessStore.uri().orEmpty()
        try {
            org.localroute.android.tunnel.VlessUriParser.parse(uri)
        } catch (error: Exception) {
            VlessRuntime.update(TunnelState.InvalidConfig(error.message.orEmpty()), error.message ?: "Проверьте VLESS-профиль")
            return
        }
        pendingVpnRequest = true
        vlessStartRequested = true
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else continueVpnRequest()
    }

    private fun openService(target: ServiceTarget) {
        val launch = packageManager.getLaunchIntentForPackage(target.packageName)
        if (launch != null) startActivity(launch)
        else LocalDpiRuntime.update(EnginePhase.ERROR, "${target.title} не установлен на устройстве")
    }

    private fun stopVpn() {
        startService(Intent(this, CombinedVpnService::class.java).setAction(CombinedVpnService.STOP_DPI))
    }

    private fun requestQuickTile(mode: ShortcutMode) {
        val tileClass = when (mode) {
            ShortcutMode.DPI -> LocalRouteTileService::class.java
            ShortcutMode.TG -> TelegramTileService::class.java
            ShortcutMode.VLESS -> VlessTileService::class.java
        }
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, tileClass),
                mode.label,
                AndroidIcon.createWithResource(this, mode.icon),
                mainExecutor
            ) { result ->
                val message = if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                    "Плитка ${mode.label} добавлена в шторку"
                } else {
                    "Плитка уже добавлена или запрос закрыт"
                }
                android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            startActivity(Intent("android.settings.QUICK_SETTINGS_SETTINGS"))
        }
    }

    private fun isInstalled(target: ServiceTarget): Boolean = runCatching {
        @Suppress("DEPRECATION") packageManager.getApplicationInfo(target.packageName, 0)
    }.isSuccess

    private fun isPackageInstalled(packageName: String): Boolean = runCatching {
        @Suppress("DEPRECATION") packageManager.getApplicationInfo(packageName, 0)
    }.isSuccess
}
