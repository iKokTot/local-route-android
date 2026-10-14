package org.localroute.android

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
        Scaffold(bottomBar = { NavigationBar { listOf("Управление", "Проверка").forEachIndexed { i, title -> NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {}, label = { Text(title) }) } } }) { padding ->
            Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (tab == 0) ControlScreen(onStart, onStop) else CheckScreen()
            }
        }
    }
}

@Composable private fun ControlScreen(onStart: (Set<ServiceTarget>) -> Unit, onStop: () -> Unit) {
    Text("Local Route", style = MaterialTheme.typography.headlineMedium)
    Text("Режим: Локальный DPI", style = MaterialTheme.typography.titleMedium)
    Text("Трафик остаётся у провайдера: внешний сервер не используется, IP не скрывается.")
    Text("Сервисы", style = MaterialTheme.typography.titleMedium)
    var selected by remember { mutableStateOf(ServiceTarget.entries.toSet()) }
    ServiceTarget.entries.forEach { target -> Row { Checkbox(checked = target in selected, onCheckedChange = { checked -> selected = if (checked) selected + target else selected - target }); Text(target.title, Modifier.padding(top = 12.dp)) } }
    Text("Последняя подтверждённая стратегия: пока нет")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Button(onClick = { onStart(selected) }, enabled = selected.isNotEmpty()) { Text("Включить") }; OutlinedButton(onClick = onStop) { Text("Остановить") } }
    HorizontalDivider()
    Text("Ограниченная сеть", style = MaterialTheme.typography.titleMedium)
    Text("Если локальные стратегии не проходят, нужен будущий режим «Защищённый туннель» с вашим сервером. Локальный DPI не обходит сеть с разрешённым списком сайтов.")
    Text("Telegram — скоро.", style = MaterialTheme.typography.bodySmall)
}

@Composable private fun CheckScreen() {
    Text("Проверка стратегий", style = MaterialTheme.typography.headlineMedium)
    Text("Быстрая проверка идёт первой; подробная — только для прошедшего кандидата. Одновременно запускается не больше одной проверки.")
    AndroidStrategies.all.forEach { strategy -> Card { Column(Modifier.padding(12.dp)) { Text(strategy.title, style = MaterialTheme.typography.titleMedium); Text(strategy.description); TextButton(onClick = {}) { Text("Запустить проверку") } } } }
    Button(onClick = {}) { Text("Автоподбор") }
    Text("Технический журнал появляется после запуска проверки. Данные и логи никуда не отправляются.", style = MaterialTheme.typography.bodySmall)
}
