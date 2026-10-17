package org.localroute.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.localroute.android.tunnel.VpnDiagnostics

@Composable
internal fun VpnDiagnosticsCard() {
    val lines by VpnDiagnostics.lines.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Скрыть диагностику VPN" else "Диагностика VPN") }
            if (expanded) {
                Text("Запуск движка не подтверждает доступ в сеть. Здесь — ошибки соединений, без вашей VLESS-ссылки. Журнал хранится только в памяти приложения.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(lines.joinToString("\n"))) }, enabled = lines.isNotEmpty()) { Text("Скопировать журнал VPN") }
                Text(lines.joinToString("\n").ifBlank { "Ошибки ещё не зарегистрированы. Откройте нужный сайт или приложение и проверьте журнал снова." }, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
