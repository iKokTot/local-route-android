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
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val Night = Color(0xFF0E1720)
private val Panel = Color(0xFF182630)
private val PanelRaised = Color(0xFF1D2D38)
private val Line = Color(0xFF344957)
private val Mint = Color(0xFF82F0B7)
private val MintDark = Color(0xFF173F35)
private val TextMain = Color(0xFFF1F7F9)
private val TextMuted = Color(0xFFA9BECA)
private val Coral = Color(0xFFFF7185)

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_START_FROM_TILE = "start_from_tile" }

    private var strategyToStart: DpiStrategy? = null
    private var vlessStartRequested = false
    private lateinit var vlessStore: VlessStore
    private var requestedTab by mutableStateOf(0)
    private val telegramNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        startTelegramProxy()
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        continueVpnRequest()
    }
    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) startLocalService()
        else if (vlessStartRequested) {
            vlessStartRequested = false
            VlessRuntime.update(TunnelState.Error("Разрешение Android VPN не выдано"), "Разрешение Android VPN не выдано")
        } else LocalDpiRuntime.update(EnginePhase.ERROR, "Разрешение Android VPN не выдано")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ProfileStore(this)
        vlessStore = VlessStore(this)
        setContent {
            LocalRouteTheme {
                LocalRouteApp(
                    store = store,
                    isInstalled = ::isInstalled,
                    onStart = ::requestVpn,
                    onStop = ::stopVpn,
                    onAddTile = ::requestQuickTile,
                    onOpenService = ::openService,
                    vlessUri = vlessStore.uri().orEmpty(),
                    onSaveVless = ::saveVless,
                    onStartVless = ::requestVless,
                    onStopVless = { startService(Intent(this, CombinedVpnService::class.java).setAction(CombinedVpnService.STOP_SERVER)) },
                    initialTab = requestedTab,
                    onTelegramStart = ::requestTelegramProxy,
                    onTelegramStop = { startService(Intent(this, TelegramProxyService::class.java).setAction(TelegramProxyService.STOP)) },
                    onTelegramCheck = { startService(Intent(this, TelegramProxyService::class.java).setAction(TelegramProxyService.CHECK)) },
                    onTelegramConnect = {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TelegramProxySettings(this).link()))) }
                            .onFailure { TelegramProxyRuntime.checked("Установите Telegram и повторите подключение") }
                    }
                )
            }
        }
        handleShortcut(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcut(intent)
    }

    private fun handleShortcut(intent: Intent) {
        requestedTab = if (intent.getBooleanExtra("open_telegram", false)) 1 else intent.getIntExtra("open_tab", 0).coerceIn(0, 2)
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
        val consent = VpnService.prepare(this)
        if (consent == null) startLocalService() else vpnConsent.launch(consent)
    }

    private fun startLocalService() {
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

@Composable
private fun LocalRouteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mint, onPrimary = Color(0xFF062317), background = Night,
            onBackground = TextMain, surface = Panel, onSurface = TextMain,
            surfaceVariant = PanelRaised, onSurfaceVariant = TextMuted,
            outline = Line, error = Coral
        ),
        content = content
    )
}

@Composable
private fun LocalRouteApp(
    store: ProfileStore,
    isInstalled: (ServiceTarget) -> Boolean,
    onStart: (Set<ServiceTarget>, DpiStrategy) -> Unit,
    onStop: () -> Unit,
    onAddTile: (ShortcutMode) -> Unit,
    onOpenService: (ServiceTarget) -> Unit,
    vlessUri: String,
    onSaveVless: (String) -> Boolean,
    onStartVless: () -> Unit,
    onStopVless: () -> Unit,
    initialTab: Int,
    onTelegramStart: () -> Unit,
    onTelegramStop: () -> Unit,
    onTelegramCheck: () -> Unit,
    onTelegramConnect: () -> Unit
) {
    var tab by remember { mutableStateOf(initialTab) }
    var showGuide by remember { mutableStateOf(!store.guideCompleted()) }
    var guideStep by remember { mutableStateOf(0) }
    val guideTabs = listOf(2, 0, 1)
    LaunchedEffect(showGuide, guideStep) { if (showGuide) tab = guideTabs[guideStep] }
    var dpiChecks by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var settingsSection by remember { mutableStateOf("") }
    fun openModeSettings() {
        dpiChecks = false
        settingsSection = listOf("Настройки DPI", "Настройки TG", "Настройки VLESS")[tab]
        settingsOpen = true
    }
    BackHandler(enabled = settingsOpen || dpiChecks || showGuide) {
        when { dpiChecks -> dpiChecks = false; settingsOpen && settingsSection.isNotBlank() -> settingsSection = ""; settingsOpen -> settingsOpen = false; else -> { store.completeGuide(); showGuide = false } }
    }
    LaunchedEffect(initialTab) { if (!showGuide) tab = initialTab }
    var selected by remember { mutableStateOf(store.selectedServices()) }
    var lastSuccessful by remember { mutableStateOf(store.lastSuccessfulStrategy()) }
    var customCommand by remember { mutableStateOf(store.customCommand()) }
    var serverDraft by remember { mutableStateOf(vlessUri) }
    val engine by LocalDpiRuntime.state.collectAsState()
    val serverOverview by VlessRuntime.state.collectAsState()
    val combinedOverview by org.localroute.android.tunnel.CombinedRuntime.state.collectAsState()
    val guardedStart = onStart
    val telegramOverview by TelegramProxyRuntime.state.collectAsState()
    val dpiActive = engine.phase == EnginePhase.ACTIVE || engine.phase == EnginePhase.STARTING
    val serverActive = serverOverview.state == TunnelState.Connected || serverOverview.state == TunnelState.Connecting
    val tgActive = telegramOverview.phase == TelegramProxyPhase.LISTENING || telegramOverview.phase == TelegramProxyPhase.STARTING
    val currentActive = when (tab) { 0 -> dpiActive; 1 -> tgActive; else -> serverActive }
    val applying = combinedOverview.busy || (tab == 1 && telegramOverview.phase == TelegramProxyPhase.STARTING)
    fun toggleCurrent() {
        when (tab) {
            0 -> if (dpiActive) onStop() else guardedStart(selected, AndroidStrategies.resolve(store.lastSuccessfulStrategy(), store.customCommand()) ?: AndroidStrategies.all.first())
            1 -> if (tgActive) onTelegramStop() else onTelegramStart()
            else -> if (serverActive) onStopVless() else if (onSaveVless(serverDraft)) onStartVless()
        }
    }
    Scaffold(
        containerColor = Night,
        topBar = { Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (settingsOpen || dpiChecks) TextButton(onClick = { if (dpiChecks) dpiChecks = false else if (settingsSection.isNotBlank()) settingsSection = "" else settingsOpen = false }) { Text("Назад") }
                else Image(painterResource(R.drawable.local_route_mark), contentDescription = null, modifier = Modifier.size(32.dp))
                Text(if (settingsOpen) "Настройки" else "Local Route", modifier = Modifier.weight(1f).padding(start = 8.dp), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                if (!showGuide && !settingsOpen) TextButton(onClick = { openModeSettings() }) { Text("Настроить") }
            }
        } },
        bottomBar = {
            Column {
            if (!showGuide && !settingsOpen && !dpiChecks) {
                Surface(color = Night) {
                    Button(onClick = { toggleCurrent() }, enabled = !applying && (currentActive || when (tab) { 0 -> selected.isNotEmpty(); 1 -> true; else -> serverDraft.isNotBlank() }),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (currentActive) PanelRaised else Mint, contentColor = if (currentActive) Coral else Night), shape = RoundedCornerShape(18.dp)) {
                        if (applying) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
                        else { Icon(if (currentActive) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(10.dp)) }
                        Text(if (applying) "Применяем…" else when (tab) { 0 -> if (dpiActive) "Выключить DPI" else "Включить DPI"; 1 -> if (tgActive) "Выключить прокси" else "Включить прокси"; else -> if (serverActive) "Выключить VPN" else "Включить VLESS" }, maxLines = 1, fontWeight = FontWeight.Bold)
                    }
                }
            }
            NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
                val destinations = listOf("DPI" to Icons.Rounded.Bolt, "Telegram" to Icons.AutoMirrored.Rounded.Send, "Сервер" to Icons.Rounded.Security)
                destinations.forEachIndexed { index, item ->
                    NavigationBarItem(selected = (if (showGuide) guideTabs[guideStep] else tab) == index, onClick = { tab = index; settingsOpen = false; dpiChecks = false; if (showGuide) { store.completeGuide(); showGuide = false } },
                        icon = { Icon(item.second, null) }, label = { Text(item.first) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Mint, selectedTextColor = TextMain,
                            indicatorColor = MintDark, unselectedIconColor = TextMuted, unselectedTextColor = TextMuted))
                }
            }
            }
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            if (showGuide) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("Шаг ${guideStep + 1} из 3", color = Mint)
                    Text(listOf("VLESS · ваш сервер", "DPI · без сервера", "TG · только Telegram")[guideStep], fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(listOf(
                        "Здесь — интернет через свой сервер. Нажмите «Добавить ссылку», вставьте свою vless://-ссылку в настройках и сохраните. Затем вернитесь назад: кнопка запуска всегда внизу. Готовых серверов в приложении нет. Нет ссылки? Попробуйте DPI на следующем шаге.",
                        "Эта вкладка — для YouTube и Discord. Выберите сервисы и включите обход. Сервер не нужен, IP не скрывается. DPI и сервер можно включить вместе: выбранные сервисы идут напрямую через DPI, остальные — через сервер.",
                        "Включите Telegram-прокси, затем нажмите «Подключить Telegram» и подтвердите в Telegram. Local Route должен оставаться запущенным. При резервном подключении используются сторонние ретрансляторы. После подключения проверьте сообщения.")[guideStep], color = TextMuted, lineHeight = 26.sp, fontSize = 18.sp)
                    Text("Нужная вкладка выделена внизу. Бесплатно, без рекламы.", color = Mint)
                    Button(onClick = { if (guideStep < 2) guideStep++ else { store.completeGuide(); showGuide = false } }, modifier = Modifier.fillMaxWidth()) { Text(if (guideStep < 2) "Дальше" else "Начать пользоваться") }
                    TextButton(onClick = { store.completeGuide(); showGuide = false }) { Text("Закрыть подсказки") }
                }
            } else if (settingsOpen && !dpiChecks) {
                RouteSettingsScreen(serverDraft, { serverDraft = it }, onSaveVless, serverActive,
                    selected, { service ->
                        selected = if (service in selected) selected - service else selected + service
                        store.saveSelectedServices(selected)
                        if (engine.phase == EnginePhase.ACTIVE) {
                            if (selected.isEmpty()) onStop() else onStart(selected, AndroidStrategies.resolve(store.lastSuccessfulStrategy(), store.customCommand()) ?: AndroidStrategies.all.first())
                        }
                    }, onAddTile, { tab = 0; dpiChecks = true }, { guideStep = 0; settingsOpen = false; showGuide = true }, settingsSection, { settingsSection = it }, onStartVless, onStopVless)
            } else if (!dpiChecks) {
                ModeHomeScreen(tab, currentActive, applying, dpiActive, serverActive, engine.message,
                    serverOverview, telegramOverview, selected, serverDraft.isNotBlank(), { openModeSettings() }, onTelegramConnect, onTelegramCheck)
            } else
            when (tab) {
                0 -> if (dpiChecks) {
                    Column {
                        OutlinedButton(onClick = {
                            dpiChecks = false
                            lastSuccessful = store.lastSuccessfulStrategy()
                            customCommand = store.customCommand()
                        }, modifier = Modifier.padding(horizontal = 20.dp)) { Text("Назад к DPI") }
                        Box(Modifier.weight(1f)) { DiagnosticsScreen(selected, guardedStart, onStop, onOpenService) }
                    }
                } else ControlScreen(engine, selected, isInstalled, lastSuccessful, customCommand,
                    onToggleService = { service ->
                        selected = if (service in selected) selected - service else selected + service
                        store.saveSelectedServices(selected)
                        if (engine.phase == EnginePhase.ACTIVE) {
                            if (selected.isEmpty()) onStop()
                            else onStart(selected, AndroidStrategies.resolve(lastSuccessful, customCommand) ?: AndroidStrategies.all.first())
                        }
                    },
                    onStart = { strategy -> guardedStart(selected, strategy) }, onStop = onStop,
                    onOpenDiagnostics = { dpiChecks = true }, onAddTile = { onAddTile(ShortcutMode.DPI) })
                1 -> TelegramScreen(onTelegramStart, onTelegramStop, onTelegramCheck, onTelegramConnect, { onAddTile(ShortcutMode.TG) })
                else -> VlessScreen(serverDraft, { serverDraft = it }, onSaveVless, { draft ->
                    val startServer: () -> Unit = { if (onSaveVless(draft)) onStartVless() }
                    startServer()
                }, onStopVless, { onAddTile(ShortcutMode.VLESS) })
            }
        }
    }
}

@Composable
private fun VlessScreen(
    uri: String, onUriChange: (String) -> Unit, onSave: (String) -> Boolean, onStart: (String) -> Unit, onStop: () -> Unit, onAddTile: () -> Unit
) {
    var showLink by remember { mutableStateOf(false) }
    val status by VlessRuntime.state.collectAsState()
    val active = status.state == TunnelState.Connected || status.state == TunnelState.Connecting
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenHeader("VLESS · СВОЯ ССЫЛКА", "Свой сервер", when (status.state) {
            TunnelState.Connecting -> "Запускается…"
            TunnelState.Connected -> "VPN включён · проверьте интернет"
            is TunnelState.Error, is TunnelState.InvalidConfig -> "Нужна проверка настроек"
            else -> if (uri.isBlank()) "Сначала добавьте ссылку" else "Выключен"
        }, status.state == TunnelState.Connected)
        Card(colors = CardDefaults.cardColors(containerColor = if (active) MintDark else Panel),
            border = BorderStroke(1.dp, Line), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Серверный маршрут", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(if (status.state is TunnelState.Error) "Не удалось включить VLESS. Проверьте ссылку и попробуйте снова. Подробности — в журнале ниже." else status.message,
                    color = if (status.state is TunnelState.Error || status.state is TunnelState.InvalidConfig) Coral else Mint)
                Text("При включённом DPI выбранные сервисы останутся на телефоне, остальной интернет пойдёт через ваш сервер. Можно выключать каждый режим отдельно.", color = TextMuted, lineHeight = 21.sp)
            }
        }
        if (uri.isBlank()) Text("Нужна своя ссылка vless:// от владельца сервера. Готовых серверов в приложении нет.", color = TextMuted, lineHeight = 21.sp)
        OutlinedTextField(value = uri, onValueChange = onUriChange, modifier = Modifier.fillMaxWidth().height(160.dp),
            label = { Text("Ваша ссылка vless://") }, placeholder = { Text("vless://…") }, minLines = 4, enabled = !active,
            visualTransformation = if (showLink) VisualTransformation.None else PasswordVisualTransformation())
        TextButton(onClick = { showLink = !showLink }) { Text(if (showLink) "Скрыть ссылку" else "Показать ссылку") }
        Text("Ссылка хранится в зашифрованном хранилище телефона.", color = TextMuted, fontSize = 12.sp)
        OutlinedButton(onClick = { onSave(uri) }, enabled = uri.isNotBlank() && !active, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Сохранить ссылку", maxLines = 1)
        }
        Button(onClick = { onStart(uri) }, enabled = uri.isNotBlank() && !active,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(14.dp)) { Text("Включить VLESS", maxLines = 1) }
        OutlinedButton(onClick = onStop, enabled = active, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral)) { Text("Выключить VPN", maxLines = 1) }
        OutlinedButton(onClick = onAddTile, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Добавить плитку VLESS в шторку", maxLines = 1) }
        VpnExclusionsCard()
        VpnDiagnosticsCard()
        Text("Включённый движок ещё не доказывает доступность сервера. После подключения откройте нужный сайт или приложение.", color = TextMuted, fontSize = 12.sp)
        if (status.state is TunnelState.Error) {
            var details by remember { mutableStateOf(false) }
            TextButton(onClick = { details = !details }) { Text(if (details) "Скрыть журнал" else "Показать журнал") }
            if (details) Text(status.message.replace(Regex("vless://[^\\s]+"), "[ссылка скрыта]")
                .replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}"), "[ключ скрыт]"), color = TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ScreenHeader(kicker: String, title: String, state: String, active: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.local_route_mark),
            contentDescription = "Local Route",
            modifier = Modifier.size(42.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(kicker.uppercase(), color = Mint, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.1.sp)
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
        Surface(shape = RoundedCornerShape(12.dp), color = Panel, border = BorderStroke(1.dp, Line)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (active) Mint else TextMuted, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(state, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TelegramScreen(onStart: () -> Unit, onStop: () -> Unit, onCheck: () -> Unit, onConnect: () -> Unit, onAddTile: () -> Unit) {
    val status by TelegramProxyRuntime.state.collectAsState()
    val active = status.phase == TelegramProxyPhase.LISTENING
    val starting = status.phase == TelegramProxyPhase.STARTING
    var showLogs by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenHeader("ПРОКСИ · ТОЛЬКО ДЛЯ TELEGRAM", "Telegram", when {
            status.checking -> "Проверяется"
            status.verified == true -> "Узел ответил · проверьте сообщения"
            status.verified == false -> "Нет связи"
            active -> "Прокси запущен · связь не проверена"
            starting -> "Запускается"
            else -> "Выключен"
        }, status.verified == true)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), border = BorderStroke(1.dp, Line), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Подключение Telegram", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Сначала включите прокси здесь. Затем откройте Telegram кнопкой ниже и подтвердите подключение. Остальные приложения этот прокси не подключает.", color = TextMuted, lineHeight = 21.sp)
                TextButton(onClick = { showPrivacy = !showPrivacy }) { Text(if (showPrivacy) "Скрыть подробности" else "Через кого идёт соединение?") }
                if (showPrivacy) Text("Используются сторонние CF-ретрансляторы и резервное TCP-подключение. Список узлов загружается из Flowseal на GitHub; узлы не принадлежат Local Route. Сообщения Telegram зашифрованы, но операторы могут видеть сетевые метаданные. При VLESS используется ваш сервер. Бесплатно, без рекламы.", color = TextMuted, lineHeight = 21.sp)
                Text(status.message, color = if (status.phase == TelegramProxyPhase.ERROR || status.verified == false) Coral else Mint, lineHeight = 21.sp)
            }
        }
        Button(onClick = onStart, enabled = !active && !starting, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(14.dp)) {
            Text("1. Включить прокси", maxLines = 1)
        }
        Button(onClick = onConnect, enabled = active, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(14.dp)) {
            Text("2. Подключить в Telegram", maxLines = 1)
        }
        OutlinedButton(onClick = onCheck, enabled = active && !status.checking, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
            if (status.checking) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
            Text(if (status.checking) "Проверяем Telegram…" else "Проверить соединение", maxLines = 1)
        }
        OutlinedButton(onClick = onStop, enabled = active || starting, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral)) {
            Text("Выключить прокси", maxLines = 1)
        }
        OutlinedButton(onClick = onAddTile, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Добавить плитку TG Proxy в шторку", maxLines = 1) }
        Text("Один раз подтвердите добавление прокси в Telegram. Для работы Local Route должен оставаться запущенным; остановка прокси прервёт это подключение Telegram.", color = TextMuted, lineHeight = 20.sp)
        Text("Если прямые адреса Telegram недоступны, включите VLESS. Открытый порт не означает доступ к Telegram: проверка требует ответа дата-центра и не подтверждает все медиа и звонки.", color = TextMuted, lineHeight = 20.sp)
        OutlinedButton(onClick = { showLogs = !showLogs }, modifier = Modifier.fillMaxWidth()) { Text(if (showLogs) "Скрыть журнал" else "Технический журнал") }
        if (showLogs) Text(status.logs.joinToString("\n").ifBlank { "Журнал пока пуст" }, fontSize = 12.sp, color = TextMuted)
    }
}

@Composable
private fun ControlScreen(
    engine: org.localroute.android.localdpi.EngineState,
    selected: Set<ServiceTarget>,
    installed: (ServiceTarget) -> Boolean,
    lastSuccessful: String?,
    customCommand: String,
    onToggleService: (ServiceTarget) -> Unit,
    onStart: (DpiStrategy) -> Unit,
    onStop: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onAddTile: () -> Unit
) {
    val active = engine.phase == EnginePhase.ACTIVE || engine.phase == EnginePhase.STARTING
    // Stage one has exactly one supported profile.  The primary action must
    // start it directly; a user should never need to run an auto-picker just
    // to turn on the only available engine.
    val last = AndroidStrategies.resolve(lastSuccessful, customCommand) ?: AndroidStrategies.all.first()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader("DPI · БЕЗ ВНЕШНЕГО СЕРВЕРА", "Локальный обход", when (engine.phase) {
            EnginePhase.STARTING -> "Запускается…"
            EnginePhase.ACTIVE -> "Включён · проверьте приложения"
            EnginePhase.ERROR -> "Не удалось запустить"
            else -> "Выключен"
        }, engine.phase == EnginePhase.ACTIVE)

        Card(
            colors = CardDefaults.cardColors(containerColor = if (active) MintDark else Panel),
            border = BorderStroke(1.dp, if (active) Mint.copy(alpha = .45f) else Line),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Security, if (active) Mint else TextMuted)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (active) "Обход включён на телефоне" else "Для YouTube и Discord", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(if (engine.phase == EnginePhase.ERROR) "Откройте проверку профиля и журнал: там сохранены подробности ошибки." else engine.message, color = TextMuted, fontSize = 13.sp)
                }
            }
        }

        SectionTitle("СЕРВИСЫ", "Направлять через DPI")
        Text(
            "Без сервера используется общий локальный DPI-режим, кроме приложений в исключениях. Когда включены DPI и сервер, выбранные сервисы остаются локальными, остальные идут через сервер. IP локального режима не скрывается.",
            color = TextMuted,
            fontSize = 13.sp
        )
        ServiceTarget.entries.forEach { service ->
            val available = installed(service)
            SelectCard(
                title = service.title,
                subtitle = if (available) "Приложение и известные домены" else "Известные домены в браузере",
                selected = service in selected,
                enabled = true,
                onClick = { onToggleService(service) }
            )
        }
        if (selected.isEmpty()) Text("Выберите хотя бы один сервис для DPI. Серверный режим можно использовать отдельно.", color = TextMuted)
        Text("Изменение маршрутов может кратко прервать соединения. В браузере DPI применяется к известным адресам сервиса.", color = TextMuted, fontSize = 12.sp)

        Button(
            onClick = { onStart(last) }, enabled = selected.isNotEmpty() && !active,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("Включить DPI", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth().height(50.dp),
            border = BorderStroke(1.dp, Line), shape = RoundedCornerShape(14.dp)
        ) { Icon(Icons.Rounded.Science, null); Spacer(Modifier.width(8.dp)); Text("Проверить профиль") }
        OutlinedButton(
            onClick = onStop, enabled = active, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            border = BorderStroke(1.dp, if (active) Coral else Line), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (active) Coral else TextMuted)
        ) { Icon(Icons.Rounded.Stop, null); Spacer(Modifier.width(8.dp)); Text("Выключить DPI", fontWeight = FontWeight.Bold) }

        QuickTileCard(onAddTile)
        VpnDiagnosticsCard()
        ProjectLinks()
    }
}

private enum class RunState { IDLE, RUNNING, CANDIDATE, SUCCESS, FAILED }

@Composable
private fun DiagnosticsScreen(
    selected: Set<ServiceTarget>,
    onStart: (Set<ServiceTarget>, DpiStrategy) -> Unit,
    onStop: () -> Unit,
    onOpenService: (ServiceTarget) -> Unit
) {
    val engine by LocalDpiRuntime.state.collectAsState()
    val active = engine.phase == EnginePhase.ACTIVE || engine.phase == EnginePhase.STARTING
    var logsOpen by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader("ПРОВЕРКА", "Проверка работы", if (active) "Защита включена" else "Нужен запуск", active)
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            border = BorderStroke(1.dp, Line), shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Mint, modifier = Modifier.size(26.dp))
                Text(if (active) "Проверьте YouTube" else "Сначала включите защиту", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(
                    if (active) "Откройте YouTube и включите видео. Только реальное воспроизведение показывает, что защита работает в вашей сети."
                    else "Автоматический сетевой тест отключён: он не может честно подтвердить работу YouTube и мог выдавать неверный результат.",
                    color = TextMuted, fontSize = 14.sp, lineHeight = 20.sp
                )
                if (active) {
                    selected.forEach { service ->
                        OutlinedButton(
                            onClick = { onOpenService(service) }, modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Mint.copy(alpha = .65f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Mint)
                        ) {
                            Icon(Icons.Rounded.OpenInNew, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Открыть ${service.title}", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Button(
                        onClick = { onStart(selected, AndroidStrategies.all.first()) },
                        enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Включить защиту", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().clickable { logsOpen = !logsOpen },
            colors = CardDefaults.cardColors(containerColor = Panel), border = BorderStroke(1.dp, Line),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Terminal, null, tint = Mint)
                    Spacer(Modifier.width(10.dp))
                    Text("Технический журнал", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(if (logsOpen) "Скрыть" else "Показать", color = Mint, fontSize = 12.sp)
                }
                if (logsOpen) {
                    HorizontalDivider(color = Line)
                    val lines = engine.log
                    Text(if (lines.isEmpty()) "Событий пока нет" else lines.joinToString("\n"), color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    Text("Журнал остаётся только на устройстве.", color = Mint, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun LegacyDiagnosticsScreen(
    selected: Set<ServiceTarget>,
    lastSuccessful: String?,
    customCommand: String,
    onCustomCommandChange: (String) -> Unit,
    onStart: (Set<ServiceTarget>, DpiStrategy) -> Unit,
    onStop: () -> Unit,
    onOpenService: (ServiceTarget) -> Unit,
    onConfirmed: (DpiStrategy) -> Unit,
    onRejected: (DpiStrategy) -> Unit
) {
    val scope = rememberCoroutineScope()
    val statuses = remember { mutableStateMapOf<String, RunState>() }
    var running by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Запустите подбор. Результат сохранится только после вашей проверки в приложении.") }
    var details by remember { mutableStateOf<List<String>>(emptyList()) }
    var logsOpen by remember { mutableStateOf(false) }
    var pendingCandidate by remember { mutableStateOf<DpiStrategy?>(null) }
    var continueAfterRejection by remember { mutableStateOf(false) }

    suspend fun waitUntilActive(id: String): Boolean = withTimeoutOrNull<Boolean>(12_000) {
        while (true) {
            val state = LocalDpiRuntime.state.value
            if (state.phase == EnginePhase.ACTIVE && state.strategyId == id) return@withTimeoutOrNull true
            if (state.phase == EnginePhase.ERROR) return@withTimeoutOrNull false
            delay(120)
        }
        @Suppress("UNREACHABLE_CODE") false
    } ?: false

    suspend fun check(strategy: DpiStrategy, quick: Boolean, startEngine: Boolean = true): Boolean {
        if (startEngine) {
            statuses[strategy.id] = RunState.RUNNING
            statusText = "Запускаем ${strategy.title}…"
            onStart(selected, strategy)
            if (!waitUntilActive(strategy.id)) {
                statuses[strategy.id] = RunState.FAILED
                details = listOf(LocalDpiRuntime.state.value.message)
                return false
            }
            delay(500)
        }
        statusText = if (quick) "Быстрая проверка ${strategy.title}" else "Подробная проверка ${strategy.title}"
        val result = ConnectivityProbe.throughLocalSocks(
            selected,
            timeoutMs = if (quick) 4500 else 9000,
            thorough = !quick
        )
        details = result.lines
        result.lines.forEach { LocalDpiRuntime.log("${strategy.title}: $it") }
        statuses[strategy.id] = if (result.successful) RunState.RUNNING else RunState.FAILED
        return result.successful
    }

    fun beginSearch(candidates: List<DpiStrategy>, automatic: Boolean, clear: Boolean) {
        if (running || pendingCandidate != null || selected.isEmpty()) return
        scope.launch {
            running = true
            if (clear) statuses.clear()
            details = emptyList()
            var candidate: DpiStrategy? = null
            for (strategy in candidates) {
                if (!check(strategy, quick = true)) continue
                // Keep the same native VPN/SOCKS/TUN session alive. Unlike a
                // JVM service, hev-socks5-tunnel owns global native state and
                // cannot be safely stopped and immediately started merely to
                // move from the quick probe to the detailed probe.
                if (check(strategy, quick = false, startEngine = false)) {
                    candidate = strategy
                    break
                }
            }
            if (candidate != null) {
                statuses[candidate.id] = RunState.CANDIDATE
                pendingCandidate = candidate
                continueAfterRejection = automatic
                statusText = "Кандидат запущен: ${candidate.title}"
                details = details + "Автопроверка проверила несколько узлов, но не может доказать работу приложения. Откройте YouTube/Discord и подтвердите результат сами."
            } else {
                onStop()
                statusText = if (automatic) {
                    "Локальные Android-профили не прошли предварительную проверку"
                } else {
                    "Стратегия не прошла предварительную проверку"
                }
                if (automatic) details = details + "В этой сети может потребоваться будущий SECURE_TUNNEL."
            }
            running = false
        }
    }

    fun orderedCandidates(): List<DpiStrategy> = buildList {
        AndroidStrategies.custom(customCommand)?.let { add(it) }
        addAll(AndroidStrategies.ordered(lastSuccessful).filterNot { strategy -> any { it.id == strategy.id } })
    }

    fun runAutoTune(afterId: String? = null) {
        val ordered = orderedCandidates()
        val candidates = if (afterId == null) ordered else {
            val index = ordered.indexOfFirst { it.id == afterId }
            if (index >= 0) ordered.drop(index + 1) else ordered
        }
        beginSearch(candidates, automatic = true, clear = afterId == null)
    }

    fun runSingle(strategy: DpiStrategy) {
        beginSearch(listOf(strategy), automatic = false, clear = false)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader(
            "ДИАГНОСТИКА",
            "Проверка стратегий",
            when { running -> "Идёт проверка"; pendingCandidate != null -> "Нужна проверка"; else -> "Готово" },
            running || pendingCandidate != null
        )
        Text(
            "Проверка запускает единственный профиль ByeDPIAndroid. Он сохраняется как рабочий только после вашей проверки YouTube/Discord.",
            color = TextMuted,
            fontSize = 14.sp
        )
        Button(
            onClick = { runSingle(AndroidStrategies.all.first()) }, enabled = !running && pendingCandidate == null && selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(14.dp)
        ) {
            if (running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Night)
            else Icon(Icons.Rounded.Bolt, null)
            Spacer(Modifier.width(9.dp))
            Text(if (running) "Проверяем…" else "Проверить профиль", fontWeight = FontWeight.Bold)
        }

        StatusPanel(statusText, details, running)

        pendingCandidate?.let { candidate ->
            CandidateConfirmationCard(
                strategy = candidate,
                services = selected,
                onOpenService = onOpenService,
                onWorks = {
                    statuses[candidate.id] = RunState.SUCCESS
                    onConfirmed(candidate)
                    pendingCandidate = null
                    continueAfterRejection = false
                    statusText = "Подтверждено вами и сохранено: ${candidate.title}"
                    details = listOf("Быстрый запуск и плитка теперь используют эту стратегию первой.")
                },
                onFails = {
                    val resume = continueAfterRejection
                    statuses[candidate.id] = RunState.FAILED
                    onRejected(candidate)
                    pendingCandidate = null
                    continueAfterRejection = false
                    onStop()
                    statusText = "Вы отметили стратегию как нерабочую"
                    if (resume) {
                        scope.launch {
                            delay(650)
                            runAutoTune(candidate.id)
                        }
                    }
                }
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            border = BorderStroke(1.dp, Line),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Своя строка ByeDPI", fontWeight = FontWeight.Bold)
                Text(
                    "Можно вставить параметры из рабочего ByeDPIAndroid. Локальный адрес, порт и внешние прокси изменять нельзя.",
                    color = TextMuted,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = customCommand,
                    onValueChange = onCustomCommandChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    enabled = !running && pendingCandidate == null,
                    placeholder = { Text(AndroidStrategies.BYEDPI_ANDROID_TCP_UDP_COMMAND, fontSize = 10.sp) }
                )
                val custom = AndroidStrategies.custom(customCommand)
                OutlinedButton(
                    onClick = { custom?.let(::runSingle) },
                    enabled = custom != null && !running && pendingCandidate == null && selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Mint.copy(alpha = .65f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Mint)
                ) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Проверить свою строку")
                }
                if (customCommand.isNotBlank() && custom == null) {
                    Text("Строка пуста либо содержит запрещённую настройку адреса/внешнего прокси.", color = Coral, fontSize = 11.sp)
                }
            }
        }

        SectionTitle("ANDROID-ПРОФИЛЬ", "1 проверяемый профиль")
        AndroidStrategies.all.forEachIndexed { index, strategy ->
            StrategyCard(index + 1, strategy, statuses[strategy.id] ?: RunState.IDLE, lastSuccessful == strategy.id, running || pendingCandidate != null) {
                runSingle(strategy)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().clickable { logsOpen = !logsOpen },
            colors = CardDefaults.cardColors(containerColor = Panel), border = BorderStroke(1.dp, Line),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Terminal, null, tint = Mint)
                    Spacer(Modifier.width(10.dp))
                    Text("Технический журнал", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(if (logsOpen) "Скрыть" else "Показать", color = Mint, fontSize = 12.sp)
                }
                if (logsOpen) {
                    HorizontalDivider(color = Line)
                    val lines = LocalDpiRuntime.state.value.log
                    Text(if (lines.isEmpty()) "Событий пока нет" else lines.joinToString("\n"), color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    Text("Журнал остаётся только на устройстве и никуда не отправляется.", color = Mint, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CandidateConfirmationCard(
    strategy: DpiStrategy,
    services: Set<ServiceTarget>,
    onOpenService: (ServiceTarget) -> Unit,
    onWorks: () -> Unit,
    onFails: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MintDark),
        border = BorderStroke(1.dp, Mint.copy(alpha = .7f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Проверьте реальное приложение", color = Mint, fontWeight = FontWeight.ExtraBold)
            Text(strategy.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "VPN оставлен включённым. Откройте выбранные сервисы, запустите видео или соединение, затем вернитесь сюда.",
                color = TextMuted,
                fontSize = 13.sp
            )
            services.forEach { service ->
                OutlinedButton(
                    onClick = { onOpenService(service) },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Line)
                ) {
                    Icon(Icons.Rounded.OpenInNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Открыть ${service.title}")
                }
            }
            Button(onClick = onWorks, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.CheckCircle, null)
                Spacer(Modifier.width(8.dp))
                Text("Работает — сохранить", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onFails,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Coral),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral)
            ) {
                Icon(Icons.Rounded.ErrorOutline, null)
                Spacer(Modifier.width(8.dp))
                Text("Не работает — остановить")
            }
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, tint: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = .13f)) {
        Icon(icon, null, tint = tint, modifier = Modifier.padding(10.dp).size(22.dp))
    }
}

@Composable
private fun SectionTitle(kicker: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(kicker, color = Mint, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SelectCard(title: String, subtitle: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected && enabled) MintDark else Panel),
        border = BorderStroke(1.dp, if (selected && enabled) Mint.copy(alpha = .7f) else Line),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Apps, if (enabled) Mint else TextMuted)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = TextMuted, fontSize = 12.sp)
            }
            Switch(
                checked = selected && enabled, onCheckedChange = { if (enabled) onClick() }, enabled = enabled,
                colors = SwitchDefaults.colors(checkedThumbColor = Night, checkedTrackColor = Mint, uncheckedTrackColor = Line)
            )
        }
    }
}

@Composable
private fun ProjectLinks() {
    val uri = LocalUriHandler.current
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            "Telegram",
            color = Mint,
            fontSize = 12.sp,
            modifier = Modifier.clickable { uri.openUri("https://t.me/TheLocalRoute") }.padding(8.dp)
        )
        Text("·", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        Text(
            "GitHub",
            color = Mint,
            fontSize = 12.sp,
            modifier = Modifier.clickable { uri.openUri("https://github.com/iKokTot/local-route") }.padding(8.dp)
        )
    }
}

@Composable
private fun QuickTileCard(onAddTile: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), border = BorderStroke(1.dp, Line), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Bolt, null, tint = Mint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Быстрый запуск DPI", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Плитка DPI включает сохранённую рабочую стратегию. TG Proxy и VLESS запускаются своими плитками — добавьте их на соответствующих вкладках.", color = TextMuted, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
            OutlinedButton(
                onClick = onAddTile, modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Mint.copy(alpha = .65f)), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Mint)
            ) { Text("Добавить плитку DPI в шторку", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun StatusPanel(title: String, details: List<String>, running: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelRaised), border = BorderStroke(1.dp, Line),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            if (running) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Mint)
            else Icon(Icons.Rounded.Tune, null, tint = Mint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                details.forEach { Text(it, color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp) }
            }
        }
    }
}

@Composable
private fun StrategyCard(index: Int, strategy: DpiStrategy, state: RunState, saved: Boolean, blocked: Boolean, onClick: () -> Unit) {
    val tint = when (state) {
        RunState.SUCCESS, RunState.CANDIDATE -> Mint
        RunState.FAILED -> Coral
        else -> TextMuted
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !blocked, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (saved) MintDark else Panel),
        border = BorderStroke(1.dp, if (saved) Mint.copy(alpha = .6f) else Line), shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = PanelRaised, shape = CircleShape) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { Text(index.toString(), color = Mint, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strategy.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (saved) Text("СОХРАНЕНА", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text(strategy.description, color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Spacer(Modifier.width(8.dp))
            when (state) {
                RunState.RUNNING -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Mint)
                RunState.CANDIDATE -> Icon(Icons.Rounded.Science, "Нужна проверка в приложении", tint = tint)
                RunState.SUCCESS -> Icon(Icons.Rounded.CheckCircle, null, tint = tint)
                RunState.FAILED -> Icon(Icons.Rounded.ErrorOutline, null, tint = tint)
                RunState.IDLE -> Icon(Icons.Rounded.PlayArrow, "Проверить", tint = tint)
            }
        }
    }
}
