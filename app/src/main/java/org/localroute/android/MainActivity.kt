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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
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

    private var servicesToStart: Set<ServiceTarget> = emptySet()
    private var strategyToStart: String? = null
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
        setContent {
            LocalRouteTheme {
                LocalRouteApp(
                    store = store,
                    isInstalled = ::isInstalled,
                    onStart = ::requestVpn,
                    onStop = ::stopVpn,
                    onAddTile = ::requestQuickTile
                )
            }
        }
        if (intent.getBooleanExtra(EXTRA_START_FROM_TILE, false)) {
            requestVpn(store.selectedServices(), store.lastSuccessfulStrategy() ?: AndroidStrategies.all.first().id)
        }
    }

    private fun requestVpn(services: Set<ServiceTarget>, strategyId: String) {
        servicesToStart = services
        strategyToStart = strategyId
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
        ContextCompat.startForegroundService(this, Intent(this, LocalDpiVpnService::class.java)
            .setAction(LocalDpiVpnService.ACTION_START)
            .putExtra(LocalDpiVpnService.EXTRA_PACKAGES, servicesToStart.map { it.packageName }.toTypedArray())
            .putExtra(LocalDpiVpnService.EXTRA_STRATEGY, strategyToStart))
    }

    private fun stopVpn() {
        startService(Intent(this, LocalDpiVpnService::class.java).setAction(LocalDpiVpnService.ACTION_STOP))
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
    onStart: (Set<ServiceTarget>, String) -> Unit,
    onStop: () -> Unit,
    onAddTile: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    var selected by remember {
        val stored = store.selectedServices()
        val available = stored.filter(isInstalled).toSet()
        if (available != stored) store.saveSelectedServices(available)
        mutableStateOf(available)
    }
    var lastSuccessful by remember { mutableStateOf(store.lastSuccessfulStrategy()) }
    var autostart by remember { mutableStateOf(store.autostartRequested()) }
    val engine by LocalDpiRuntime.state.collectAsState()

    Scaffold(
        containerColor = Night,
        bottomBar = {
            NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
                val destinations = listOf("Управление" to Icons.Rounded.Dashboard, "Проверка" to Icons.Rounded.Science)
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
                    onToggleService = { service ->
                        selected = if (service in selected) selected - service else selected + service
                        store.saveSelectedServices(selected)
                    },
                    onStart = { id -> onStart(selected, id) },
                    onStop = onStop,
                    onOpenDiagnostics = { tab = 1 },
                    onAddTile = onAddTile,
                    autostart = autostart,
                    onAutostartChange = { value -> autostart = value; store.setAutostartRequested(value) }
                )
            } else {
                DiagnosticsScreen(
                    selected = selected,
                    lastSuccessful = lastSuccessful,
                    onStart = onStart,
                    onStop = onStop,
                    onConfirmed = { strategy ->
                        store.saveLastSuccessfulStrategy(strategy.id)
                        lastSuccessful = strategy.id
                    }
                )
            }
        }
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
    onToggleService: (ServiceTarget) -> Unit,
    onStart: (String) -> Unit,
    onStop: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onAddTile: () -> Unit,
    autostart: Boolean,
    onAutostartChange: (Boolean) -> Unit
) {
    val active = engine.phase == EnginePhase.ACTIVE || engine.phase == EnginePhase.STARTING
    val last = AndroidStrategies.all.firstOrNull { it.id == lastSuccessful }
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

        SectionTitle("СЕРВИСЫ", "Защищать соединения")
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

        if (last != null) {
            Button(
                onClick = { onStart(last.id) }, enabled = selected.isNotEmpty() && !active,
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Запустить сохранённую · ${last.title}", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onOpenDiagnostics, enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Tune, null)
                Spacer(Modifier.width(8.dp))
                Text("Подобрать рабочую стратегию", fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onOpenDiagnostics, modifier = Modifier.weight(1f).height(50.dp),
                border = BorderStroke(1.dp, Line), shape = RoundedCornerShape(14.dp)
            ) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Подобрать") }
            OutlinedButton(
                onClick = onStop, enabled = active, modifier = Modifier.weight(1f).height(50.dp),
                border = BorderStroke(1.dp, if (active) Coral else Line), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (active) Coral else TextMuted)
            ) { Icon(Icons.Rounded.Stop, null); Spacer(Modifier.width(6.dp)); Text("Остановить") }
        }

        QuickTileCard(onAddTile)
        AutostartCard(autostart, onAutostartChange)
        ProjectLinks()
    }
}

private enum class RunState { IDLE, RUNNING, SUCCESS, FAILED }

@Composable
private fun DiagnosticsScreen(
    selected: Set<ServiceTarget>,
    lastSuccessful: String?,
    onStart: (Set<ServiceTarget>, String) -> Unit,
    onStop: () -> Unit,
    onConfirmed: (DpiStrategy) -> Unit
) {
    val scope = rememberCoroutineScope()
    val statuses = remember { mutableStateMapOf<String, RunState>() }
    var running by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Выберите стратегию или запустите автоподбор") }
    var details by remember { mutableStateOf<List<String>>(emptyList()) }
    var logsOpen by remember { mutableStateOf(false) }

    suspend fun waitUntilActive(id: String): Boolean = withTimeoutOrNull<Boolean>(12_000) {
        while (true) {
            val state = LocalDpiRuntime.state.value
            if (state.phase == EnginePhase.ACTIVE && state.strategyId == id) return@withTimeoutOrNull true
            if (state.phase == EnginePhase.ERROR) return@withTimeoutOrNull false
            delay(120)
        }
        @Suppress("UNREACHABLE_CODE") false
    } ?: false

    suspend fun check(strategy: DpiStrategy, quick: Boolean): Boolean {
        statuses[strategy.id] = RunState.RUNNING
        statusText = "Запускаем ${strategy.title}…"
        onStart(selected, strategy.id)
        if (!waitUntilActive(strategy.id)) {
            statuses[strategy.id] = RunState.FAILED
            details = listOf(LocalDpiRuntime.state.value.message)
            return false
        }
        delay(350)
        statusText = if (quick) "Быстрая проверка ${strategy.title}" else "Подробная проверка ${strategy.title}"
        val result = ConnectivityProbe.throughLocalSocks(selected, if (quick) 4000 else 9000)
        details = result.lines
        result.lines.forEach { LocalDpiRuntime.log("${strategy.title}: $it") }
        statuses[strategy.id] = if (result.successful) RunState.SUCCESS else RunState.FAILED
        return result.successful
    }

    fun runSingle(strategy: DpiStrategy) {
        if (running || selected.isEmpty()) return
        scope.launch {
            running = true
            val quickPassed = check(strategy, quick = true)
            val confirmed = quickPassed && check(strategy, quick = false)
            if (confirmed) {
                onConfirmed(strategy)
                statusText = "Подтверждено и сохранено: ${strategy.title}"
            } else {
                statusText = "Стратегия не прошла проверку"
                onStop()
            }
            running = false
        }
    }

    fun runAutoTune() {
        if (running || selected.isEmpty()) return
        scope.launch {
            running = true
            statuses.clear()
            details = emptyList()
            var found: DpiStrategy? = null
            for (strategy in AndroidStrategies.ordered(lastSuccessful)) {
                if (!check(strategy, quick = true)) continue
                if (check(strategy, quick = false)) { found = strategy; break }
            }
            if (found != null) {
                onConfirmed(found)
                statusText = "Рабочая стратегия сохранена: ${found.title}"
            } else {
                onStop()
                statusText = "Локальные стратегии не сработали · ограниченная сеть"
                details = details + "Для такой сети потребуется будущий серверный режим SECURE_TUNNEL."
            }
            running = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader("ДИАГНОСТИКА", "Проверка стратегий", if (running) "Идёт проверка" else "Готово", running)
        Text("Сначала короткая проверка, затем подтверждение. Кандидаты запускаются строго по одному, чтобы не нагружать телефон.", color = TextMuted, fontSize = 14.sp)
        Button(
            onClick = ::runAutoTune, enabled = !running && selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(14.dp)
        ) {
            if (running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Night)
            else Icon(Icons.Rounded.Bolt, null)
            Spacer(Modifier.width(9.dp))
            Text(if (running) "Проверяем…" else "Запустить автоподбор", fontWeight = FontWeight.Bold)
        }

        StatusPanel(statusText, details, running)
        SectionTitle("ANDROID-СТРАТЕГИИ", "${AndroidStrategies.all.size} вариантов")
        AndroidStrategies.all.forEachIndexed { index, strategy ->
            StrategyCard(index + 1, strategy, statuses[strategy.id] ?: RunState.IDLE, lastSuccessful == strategy.id, running) {
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
private fun AutostartCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), border = BorderStroke(1.dp, Line), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Security, null, tint = Mint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("После перезагрузки", fontWeight = FontWeight.Bold)
                Text("Запуск только при сохранённом согласии Android VPN", color = TextMuted, fontSize = 12.sp)
            }
            Switch(
                checked = enabled, onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Night, checkedTrackColor = Mint, uncheckedTrackColor = Line)
            )
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
    val tint = when (state) { RunState.SUCCESS -> Mint; RunState.FAILED -> Coral; else -> TextMuted }
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
                RunState.SUCCESS -> Icon(Icons.Rounded.CheckCircle, null, tint = tint)
                RunState.FAILED -> Icon(Icons.Rounded.ErrorOutline, null, tint = tint)
                RunState.IDLE -> Icon(Icons.Rounded.PlayArrow, "Проверить", tint = tint)
            }
        }
    }
}
