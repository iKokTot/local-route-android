package org.localroute.android

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.localroute.android.core.AndroidStrategies
import org.localroute.android.core.ServiceTarget
import org.localroute.android.localdpi.LocalDpiVpnService

class MainActivity : ComponentActivity() {
    private var servicesToStart: Set<ServiceTarget> = emptySet()
    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) startLocalService()
    }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { LocalRouteApp(::requestVpn, ::stopVpn) } }
    private fun requestVpn(services: Set<ServiceTarget>) {
        servicesToStart = services
        val consent = VpnService.prepare(this)
        if (consent == null) startLocalService() else vpnConsent.launch(consent)
    }
    private fun startLocalService() = startService(Intent(this, LocalDpiVpnService::class.java).setAction(LocalDpiVpnService.ACTION_START).putExtra("packages", servicesToStart.map { it.packageName }.toTypedArray()))
    private fun stopVpn() = startService(Intent(this, LocalDpiVpnService::class.java).setAction(LocalDpiVpnService.ACTION_STOP))
}

@Composable
private fun LocalRouteApp(onStart: (Set<ServiceTarget>) -> Unit, onStop: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    MaterialTheme {
        Scaffold(bottomBar = { NavigationBar { listOf("Управление", "Проверка").forEachIndexed { i, title -> NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(if (i == 0) "⌁" else "✓") }, label = { Text(title) }) } } }) { padding ->
            Column(Modifier.padding(padding).padding(horizontal = 20.dp, vertical = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (tab == 0) ControlScreen(onStart, onStop) else CheckScreen()
            }
        }
    }
}

@Composable private fun ControlScreen(onStart: (Set<ServiceTarget>) -> Unit, onStop: () -> Unit) {
    Text("Local Route", style = MaterialTheme.typography.headlineMedium)
    Card { Column(Modifier.padding(16.dp)) { Text("Локальный DPI", style = MaterialTheme.typography.titleLarge); Text("Без сервера · IP не скрывается", style = MaterialTheme.typography.bodyMedium) } }
    Text("Сервисы", style = MaterialTheme.typography.titleMedium)
    var selected by remember { mutableStateOf(ServiceTarget.entries.toSet()) }
    ServiceTarget.entries.forEach { target -> Row { Checkbox(checked = target in selected, onCheckedChange = { checked -> selected = if (checked) selected + target else selected - target }); Text(target.title, Modifier.padding(top = 12.dp)) } }
    AssistChip(onClick = {}, label = { Text("Стратегия: ещё не подтверждена") })
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Button(onClick = { onStart(selected) }, enabled = selected.isNotEmpty()) { Text("Включить") }; OutlinedButton(onClick = onStop) { Text("Остановить") } }
    Card { Column(Modifier.padding(16.dp)) { Text("Важно", style = MaterialTheme.typography.titleMedium); Text("Локальный DPI не заменяет туннель в сети с разрешённым списком сайтов."); Text("Telegram — скоро.", style = MaterialTheme.typography.bodySmall) } }
}

@Composable private fun CheckScreen() {
    Text("Проверка стратегий", style = MaterialTheme.typography.headlineMedium)
    Text("Быстрая проверка идёт первой; подробная — только для прошедшего кандидата. Одновременно запускается не больше одной проверки.")
    AndroidStrategies.all.forEach { strategy -> Card { Column(Modifier.padding(16.dp)) { Text(strategy.title, style = MaterialTheme.typography.titleMedium); Text(strategy.description) } } }
    Text("Автоподбор будет доступен после проверки соединения на устройстве. Логи остаются только на телефоне.", style = MaterialTheme.typography.bodySmall)
}
