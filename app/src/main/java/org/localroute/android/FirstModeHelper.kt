package org.localroute.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun FirstModeHelper(tab: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("local_route_help", android.content.Context.MODE_PRIVATE) }
    var visible by remember(tab) { mutableStateOf(!prefs.getBoolean("mode_help_v1_$tab", false)) }
    if (visible) OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("С чего начать", style = MaterialTheme.typography.titleMedium)
            Text(when (tab) {
                0 -> "Нажмите «Включить DPI» внизу, подтвердите запрос Android и откройте YouTube или Discord. Свой сервер не нужен. Профиль и выбор сервисов — в «Настроить» сверху."
                1 -> "Сначала включите прокси кнопкой внизу. Затем нажмите «Подключить Telegram» и подтвердите прокси в Telegram. Local Route должен оставаться запущенным."
                else -> "Есть своя ссылка vless://? Нажмите «Добавить ссылку» или «Настроить», вставьте и сохраните её. Вернитесь на эту вкладку и включите VLESS кнопкой внизу. Без ссылки используйте DPI."
            }, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { prefs.edit().putBoolean("mode_help_v1_$tab", true).apply(); visible = false }) { Text("Понятно") }
        }
    }
}
