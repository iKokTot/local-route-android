package org.localroute.android

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.localroute.android.data.VpnExclusions
import org.localroute.android.tunnel.CombinedRuntime
import org.localroute.android.tunnel.CombinedVpnService

@Composable
internal fun VpnExclusionsCard() {
    val context = LocalContext.current
    val store = remember { VpnExclusions(context) }
    var saved by remember { mutableStateOf(store.packages()) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(saved) }
    var search by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    val runtime by CombinedRuntime.state.collectAsState()
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .filter { it.activityInfo.packageName != context.packageName }
                .map { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }
                .distinctBy { it.first }.sortedBy { it.second.lowercase() }
        }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Исключения VLESS", style = MaterialTheme.typography.titleMedium)
            Text("Отмеченные приложения идут напрямую при включённом VLESS. На DPI без сервера список не влияет.", style = MaterialTheme.typography.bodyMedium)
            Text("На этом устройстве: ${apps.count { it.first in saved }} приложений в исключениях", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { draft = saved; search = ""; editing = true }, enabled = !runtime.busy) { Text("Настроить исключения") }
            Text("Рекомендовано ${VpnExclusions.defaults.size} приложения: банки, покупки и повседневные сервисы. Это не гарантия несовместимости с VPN.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text("Приложения без VPN") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Галочка — приложение подключается напрямую. При сохранении активный VPN кратко переподключится.")
            TextButton(onClick = { draft = draft + VpnExclusions.defaults }) { Text("Добавить рекомендованные") }
            Text("Если в настройках Android включена блокировка соединений без VPN, исключения не смогут выйти в сеть.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(search, { search = it }, label = { Text("Поиск приложения") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                apps.filter { it.second.contains(search, true) || it.first.contains(search, true) }.forEach { (pkg, label) ->
                    Row(Modifier.fillMaxWidth().clickable { draft = if (pkg in draft) draft - pkg else draft + pkg }.padding(vertical = 4.dp)) {
                        Checkbox(pkg in draft, onCheckedChange = { checked -> draft = if (checked) draft + pkg else draft - pkg })
                        Column(Modifier.weight(1f).padding(top = 8.dp)) { Text(label); Text(pkg, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }, confirmButton = {
        TextButton(enabled = !runtime.busy, onClick = {
            store.save(draft); saved = draft; editing = false
            if (CombinedRuntime.state.value.plan.serverEnabled) context.startService(Intent(context, CombinedVpnService::class.java).setAction(CombinedVpnService.RELOAD))
        }) { Text("Сохранить") }
    }, dismissButton = { TextButton(onClick = { editing = false }) { Text("Отмена") } })
}
