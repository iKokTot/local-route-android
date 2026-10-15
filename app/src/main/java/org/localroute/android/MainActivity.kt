package org.localroute.android

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon as AndroidIcon
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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
import org.localroute.android.tunnel.SecureTunnelRuntime
import org.localroute.android.tunnel.SecureTunnelStore
import org.localroute.android.tunnel.TunnelServerProfile
import org.localroute.android.tunnel.TunnelServerProfiles
import org.localroute.android.tunnel.TunnelState
import org.localroute.android.tunnel.WireGuardSecureTunnelController

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
    private var secureTunnelStartRequested = false
    private lateinit var secureTunnelStore: SecureTunnelStore
    private lateinit var secureTunnelController: WireGuardSecureTunnelController
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        continueVpnRequest()
    }
    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) startLocalService()
        else LocalDpiRuntime.update(EnginePhase.ERROR, "Разрешение Android VPN не выдано")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ProfileStore(this)
        secureTunnelStore = SecureTunnelStore(this)
        secureTunnelController = WireGuardSecureTunnelController(this, secureTunnelStore)
        setContent {
            LocalRouteTheme {
                LocalRouteApp(
                    store = store,
                    isInstalled = ::isInstalled,
                    onStart = ::requestVpn,
                    onStop = ::stopVpn,
                    onAddTile = ::requestQuickTile,
                    onOpenService = ::openService,
                    secureTunnelProfileName = secureTunnelStore.activeProfile()?.name.orEmpty(),
                    secureTunnelConfiguration = secureTunnelStore.configuration().orEmpty(),
                    onSaveSecureTunnelProfile = ::saveSecureTunnelProfile,
                    onStartSecureTunnel = ::requestSecureTunnel
                )
            }
        }
        if (intent.getBooleanExtra(EXTRA_START_FROM_TILE, false)) {
            val strategy = AndroidStrategies.resolve(store.lastSuccessfulStrategy(), store.customCommand())
                ?: AndroidStrategies.all.first()
            requestVpn(store.selectedServices(), strategy)
        }
    }

    private fun requestVpn(services: Set<ServiceTarget>, strategy: DpiStrategy) {
        secureTunnelStartRequested = false
        strategyToStart = strategy
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            continueVpnRequest()
        }
    }

    private fun continueVpnRequest() {
        val consent = VpnService.prepare(this)
        if (consent == null) startLocalService() else vpnConsent.launch(consent)
    }

    private fun startLocalService() {
        if (secureTunnelStartRequested) {
            secureTunnelStartRequested = false
            startSecureTunnel()
            return
        }
        val strategy = strategyToStart ?: AndroidStrategies.all.first()
        ContextCompat.startForegroundService(this, Intent(this, LocalDpiVpnService::class.java)
            .setAction(LocalDpiVpnService.ACTION_START)
            .putExtra(LocalDpiVpnService.EXTRA_STRATEGY, strategy.id)
            .putExtra(LocalDpiVpnService.EXTRA_STRATEGY_TITLE, strategy.title)
            .putExtra(LocalDpiVpnService.EXTRA_STRATEGY_ARGUMENTS, strategy.nativeArguments.toTypedArray()))
    }

    private fun saveSecureTunnelProfile(name: String, configuration: String) {
        val current = secureTunnelStore.activeProfile() ?: TunnelServerProfiles.default()
        secureTunnelStore.saveProfile(
            TunnelServerProfile(current.id, name.trim().ifBlank { current.name }),
            configuration
        )
        SecureTunnelRuntime.update(TunnelState.Disconnected, "Профиль сервера сохранён на устройстве")
    }

    private fun requestSecureTunnel() {
        if (secureTunnelStore.configuration().isNullOrBlank()) {
            SecureTunnelRuntime.update(TunnelState.ConfigRequired, "Сначала вставьте конфигурацию WireGuard")
            return
        }
        secureTunnelStartRequested = true
        startService(Intent(this, LocalDpiVpnService::class.java).setAction(LocalDpiVpnService.ACTION_STOP))
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            continueVpnRequest()
        }
    }

    private fun startSecureTunnel() {
        lifecycleScope.launch {
            SecureTunnelRuntime.update(TunnelState.Connecting, "Подключение к вашему серверу…")
            when (val result = secureTunnelController.connect(org.localroute.android.core.RouteProfile(mode = org.localroute.android.core.TrafficMode.SECURE_TUNNEL))) {
                TunnelState.Connected -> SecureTunnelRuntime.update(result, "Защищённый туннель активен")
                is TunnelState.InvalidConfig -> SecureTunnelRuntime.update(result, result.message)
                is TunnelState.Error -> SecureTunnelRuntime.update(result, result.message)
                TunnelState.ConfigRequired -> SecureTunnelRuntime.update(result, "Сначала вставьте конфигурацию WireGuard")
                else -> SecureTunnelRuntime.update(result, "Состояние туннеля изменено")
            }
        }
    }

    private fun openService(target: ServiceTarget) {
        val launch = packageManager.getLaunchIntentForPackage(target.packageName)
        if (launch != null) startActivity(launch)
        else LocalDpiRuntime.update(EnginePhase.ERROR, "${target.title} не установлен на устройстве")
    }

    private fun stopVpn() {
        startService(Intent(this, LocalDpiVpnService::class.java).setAction(LocalDpiVpnService.ACTION_STOP))
        lifecycleScope.launch {
            secureTunnelController.disconnect()
            SecureTunnelRuntime.update(TunnelState.Disconnected, "Защита остановлена")
        }
    }

    private fun requestQuickTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, LocalRouteTileService::class.java),
                "Local Route",
                AndroidIcon.createWithResource(this, R.drawable.ic_local_route),
                mainExecutor
            ) { result ->
                val message = if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                    "Плитка Local Route добавлена в шторку"
                } else {
                    "Плитка уже добавлена или запрос закрыт"
                }
                LocalDpiRuntime.update(LocalDpiRuntime.state.value.phase, message)
            }
        } else {
            startActivity(Intent("android.settings.QUICK_SETTINGS_SETTINGS"))
        }
    }

    private fun isInstalled(target: ServiceTarget): Boolean = runCatching {
        @Suppress("DEPRECATION") packageManager.getApplicationInfo(target.packageName, 0)
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
    onAddTile: () -> Unit,
    onOpenService: (ServiceTarget) -> Unit,
    secureTunnelProfileName: String,
    secureTunnelConfiguration: String,
    onSaveSecureTunnelProfile: (String, String) -> Unit,
    onStartSecureTunnel: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    var selected by remember {
        val stored = store.selectedServices()
        val available = stored.filter(isInstalled).toSet()
        if (available != stored) store.saveSelectedServices(available)
        mutableStateOf(available)
    }
    var lastSuccessful by remember { mutableStateOf(store.lastSuccessfulStrategy()) }
    var customCommand by remember { mutableStateOf(store.customCommand()) }
    val engine by LocalDpiRuntime.state.collectAsState()

    Scaffold(
        containerColor = Night,
        bottomBar = {
            NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
                val destinations = listOf("Управление" to Icons.Rounded.Dashboard, "Проверка" to Icons.Rounded.Science, "Туннель" to Icons.Rounded.Security)
                destinations.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.second, null) },
                        label = { Text(item.first) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Mint, selectedTextColor = TextMain,
                            indicatorColor = MintDark, unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                }
            }
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            if (tab == 0) {
                ControlScreen(
                    engine = engine,
                    selected = selected,
                    installed = isInstalled,
                    lastSuccessful = lastSuccessful,
                    customCommand = customCommand,
                    onToggleService = { service ->
                        selected = if (service in selected) selected - service else selected + service
                        store.saveSelectedServices(selected)
                    },
                    onStart = { strategy -> onStart(selected, strategy) },
                    onStop = onStop,
                    onOpenDiagnostics = { tab = 1 },
                    onAddTile = onAddTile
                )
            } else if (tab == 1) {
                DiagnosticsScreen(
                    selected = selected,
                    onStart = onStart,
                    onStop = onStop,
                    onOpenService = onOpenService
                )
            } else {
                SecureTunnelScreen(
                    initialProfileName = secureTunnelProfileName,
                    initialConfiguration = secureTunnelConfiguration,
                    onSaveProfile = onSaveSecureTunnelProfile,
                    onStart = onStartSecureTunnel,
                    onStop = onStop
                )
            }
        }
    }
}

@Composable
private fun SecureTunnelScreen(
    initialProfileName: String,
    initialConfiguration: String,
    onSaveProfile: (String, String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    var profileName by remember { mutableStateOf(initialProfileName.ifBlank { "Мой сервер" }) }
    var configuration by remember { mutableStateOf(initialConfiguration) }
    val status by SecureTunnelRuntime.state.collectAsState()
    val active = status.state == TunnelState.Connected || status.state == TunnelState.Connecting
    val configPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.onSuccess { imported ->
                if (imported.isNotBlank()) {
                    configuration = imported
                    onSaveProfile(profileName, imported)
                } else {
                    SecureTunnelRuntime.update(TunnelState.InvalidConfig("Файл конфигурации пуст"), "Файл конфигурации пуст")
                }
            }.onFailure {
                SecureTunnelRuntime.update(TunnelState.InvalidConfig("Не удалось прочитать выбранный файл"), "Не удалось прочитать выбранный файл")
            }
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader("МОЙ СЕРВЕР", "Защищённый туннель", if (active) "Подключён" else "Не подключён", active)
        Card(colors = CardDefaults.cardColors(containerColor = if (active) MintDark else Panel), border = BorderStroke(1.dp, if (active) Mint.copy(alpha = .45f) else Line), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBadge(Icons.Rounded.Security, if (active) Mint else TextMuted)
                Text("Личный сервер WireGuard", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(status.message, color = TextMuted, fontSize = 13.sp)
                Text("Это полноценный зашифрованный туннель. Он не гарантирует доступ из сети с белым списком: сначала нужно проверить, доступен ли адрес вашего сервера.", color = TextMuted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
        Text("ПРОФИЛЬ СЕРВЕРА", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        Text("Имя нужно только на этом телефоне. Завтра сюда можно будет добавить другие личные серверы, не смешивая их ключи и конфигурации.", color = TextMuted, fontSize = 13.sp, lineHeight = 19.sp)
        OutlinedTextField(
            value = profileName,
            onValueChange = { profileName = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Название подключения") },
            placeholder = { Text("Например, Мой сервер") }
        )
        Text("КОНФИГУРАЦИЯ WG-QUICK", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        Text("Вставьте личный файл WireGuard: [Interface] и [Peer]. Он шифруется Android Keystore и остаётся только на этом телефоне.", color = TextMuted, fontSize = 13.sp, lineHeight = 19.sp)
        OutlinedButton(
            onClick = { configPicker.launch(arrayOf("text/plain", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth().height(50.dp), border = BorderStroke(1.dp, Line),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Mint), shape = RoundedCornerShape(14.dp)
        ) { Icon(Icons.Rounded.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Выбрать файл .conf", fontWeight = FontWeight.Bold) }
        OutlinedTextField(
            value = configuration,
            onValueChange = { configuration = it },
            modifier = Modifier.fillMaxWidth().height(210.dp),
            label = { Text("Конфигурация WireGuard") },
            placeholder = { Text("[Interface]\nPrivateKey = …\n\n[Peer]\nPublicKey = …\nEndpoint = …") },
            minLines = 8
        )
        OutlinedButton(
            onClick = { onSaveProfile(profileName, configuration) },
            enabled = configuration.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp),
            border = BorderStroke(1.dp, Mint.copy(alpha = .7f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Mint), shape = RoundedCornerShape(14.dp)
        ) { Icon(Icons.Rounded.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("Сохранить на устройстве", fontWeight = FontWeight.Bold) }
        Button(
            onClick = { onSaveProfile(profileName, configuration); onStart() },
            enabled = configuration.isNotBlank() && !active, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)
        ) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Подключить мой сервер", fontWeight = FontWeight.Bold) }
        OutlinedButton(
            onClick = onStop, enabled = active, modifier = Modifier.fillMaxWidth().height(54.dp),
            border = BorderStroke(1.dp, if (active) Coral else Line), colors = ButtonDefaults.outlinedButtonColors(contentColor = if (active) Coral else TextMuted), shape = RoundedCornerShape(14.dp)
        ) { Icon(Icons.Rounded.Stop, null); Spacer(Modifier.width(8.dp)); Text("Отключить туннель", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ScreenHeader(kicker: String, title: String, state: String, active: Boolean) {
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
        ScreenHeader("LOCAL ROUTE", "Управление", if (active) "Защита активна" else "Готово к запуску", active)

        Card(
            colors = CardDefaults.cardColors(containerColor = if (active) MintDark else Panel),
            border = BorderStroke(1.dp, if (active) Mint.copy(alpha = .45f) else Line),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Security, if (active) Mint else TextMuted)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (active) "Локальная защита работает" else "Локальная защита", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(engine.message, color = TextMuted, fontSize = 13.sp)
                }
            }
        }

        SectionTitle("СЕРВИСЫ", "Проверять работу")
        Text(
            "Как в ByeDPIAndroid, локальный VPN обрабатывает трафик всего устройства. Выбор ниже определяет приложения и узлы для проверки.",
            color = TextMuted,
            fontSize = 13.sp
        )
        ServiceTarget.entries.forEach { service ->
            val available = installed(service)
            SelectCard(
                title = service.title,
                subtitle = if (available) "Приложение найдено" else "Не установлено на этом устройстве",
                selected = service in selected,
                enabled = available,
                onClick = { if (available) onToggleService(service) }
            )
        }

        Button(
            onClick = { onStart(last) }, enabled = selected.isNotEmpty() && !active,
            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("Включить защиту", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth().height(50.dp),
            border = BorderStroke(1.dp, Line), shape = RoundedCornerShape(14.dp)
        ) { Icon(Icons.Rounded.Science, null); Spacer(Modifier.width(8.dp)); Text("Проверить профиль") }
        OutlinedButton(
            onClick = onStop, enabled = active, modifier = Modifier.fillMaxWidth().height(54.dp),
            border = BorderStroke(1.dp, if (active) Coral else Line), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (active) Coral else TextMuted)
        ) { Icon(Icons.Rounded.Stop, null); Spacer(Modifier.width(8.dp)); Text("Остановить защиту", fontWeight = FontWeight.Bold) }

        QuickTileCard(onAddTile)
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
                        enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(54.dp),
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
                    Text("Быстрый запуск", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Плитка в шторке запускает сохранённую рабочую стратегию одним нажатием.", color = TextMuted, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
            OutlinedButton(
                onClick = onAddTile, modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Mint.copy(alpha = .65f)), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Mint)
            ) { Text("Добавить плитку в шторку", fontWeight = FontWeight.Bold) }
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
