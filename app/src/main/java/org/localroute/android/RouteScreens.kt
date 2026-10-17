package org.localroute.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.localroute.android.core.ServiceTarget
import org.localroute.android.shortcuts.ShortcutMode
import org.localroute.android.telegram.*
import org.localroute.android.tunnel.*

@Composable
internal fun ModeHomeScreen(tab: Int, active: Boolean, applying: Boolean, dpi: Boolean, server: Boolean,
    dpiMessage: String, vless: VlessStatus, tg: TelegramProxyStatus, selected: Set<ServiceTarget>, hasLink: Boolean,
    settings: () -> Unit, connectTelegram: () -> Unit, checkTelegram: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(when (tab) { 0 -> "Локальный обход"; 1 -> "Telegram"; else -> "Свой сервер" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        FirstModeHelper(tab)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(when { applying -> "Подключаем…"; tab == 1 && tg.verified == false -> "Нет связи"; active -> "Включён"; else -> "Выключен" },
                    style = MaterialTheme.typography.headlineSmall, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text(when (tab) {
                    0 -> if (dpi && server) "${selected.joinToString { it.title }} — локально, остальное — через сервер." else "Локальный режим без внешнего сервера."
                    1 -> if (tg.verified == true) "Узел ответил. Проверьте сообщения в Telegram." else if (active) "Подключите прокси в самом Telegram." else "Отдельное подключение только для Telegram."
                    else -> if (!hasLink) "Для подключения нужна ваша VLESS-ссылка." else if (dpi && server) "Остальной интернет — через ваш сервер." else "Интернет через ваш сервер."
                }, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (tab == 0) {
            Text(if (server) "Локально: ${selected.joinToString { it.title }}" else "YouTube и Discord · IP не скрывается", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = settings) { Text("Выбрать сервисы") }
            if (dpiMessage.contains("ошиб", true) || dpiMessage.contains("не удалось", true)) Text("Не удалось применить маршрут. Подробности — в настройках → диагностика.", color = MaterialTheme.colorScheme.error)
        }
        if (tab == 2) {
            OutlinedButton(onClick = settings, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (hasLink) "Настроить подключение" else "Добавить ссылку") }
            if (vless.state is TunnelState.Error || vless.state is TunnelState.InvalidConfig) Text("Проверьте ссылку. Подробности — в настройках → диагностика.", color = MaterialTheme.colorScheme.error)
        }
        if (tab == 1 && active) {
            OutlinedButton(onClick = connectTelegram, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Подключить Telegram") }
            TextButton(onClick = checkTelegram, enabled = !tg.checking) { Text(if (tg.checking) "Проверяем…" else "Проверить соединение") }
            if (tg.verified == false) Text("Telegram пока не ответил. Подробности — в диагностике настроек.", color = MaterialTheme.colorScheme.error)
        }
        if (dpi || server) Text("DPI: ${if (dpi) "вкл." else "выкл."}  ·  Сервер: ${if (server) "вкл." else "выкл."}", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun RouteSettingsScreen(uri: String, changeUri: (String) -> Unit, saveUri: (String) -> Boolean, serverActive: Boolean,
    selected: Set<ServiceTarget>, toggle: (ServiceTarget) -> Unit, addTile: (ShortcutMode) -> Unit, profile: () -> Unit, guide: () -> Unit,
    section: String, changeSection: (String) -> Unit) {
    var showLink by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = section.isNotBlank()) { changeSection("") }
    val tg by TelegramProxyRuntime.state.collectAsState()
    var tgLog by remember { mutableStateOf(false) }
    androidx.compose.runtime.key(section) {
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (section.isBlank()) {
            listOf("Настройки DPI", "Настройки TG", "Настройки VLESS", "О приложении").forEach { title ->
                OutlinedButton(onClick = { changeSection(title) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(title) }
            }
            TextButton(onClick = guide, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Как пользоваться") }
        } else {
        TextButton(onClick = { changeSection("") }) { Text("Все настройки") }
        Text(section, style = MaterialTheme.typography.titleLarge)
        when (section) {
        "Настройки VLESS" -> {
        OutlinedTextField(uri, { changeUri(it); saved = false }, label = { Text("VLESS-ссылка") }, minLines = 2, maxLines = 4,
            enabled = !serverActive, visualTransformation = if (showLink) VisualTransformation.None else PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { showLink = !showLink }) { Text(if (showLink) "Скрыть ссылку" else "Показать ссылку") }
            OutlinedButton(onClick = { saved = saveUri(uri) }, enabled = !serverActive && uri.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (saved) "Сохранено" else "Сохранить ссылку") }
        }
        if (serverActive) Text("Чтобы изменить ссылку, выключите серверный режим.", style = MaterialTheme.typography.bodySmall)
        VpnExclusionsCard()
        OutlinedButton(onClick = { addTile(ShortcutMode.VLESS) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Добавить плитку VLESS") }
        VpnDiagnosticsCard()
        }
        "Настройки DPI" -> {
        Text("При совместном режиме эти сервисы не отправляются на сервер.", style = MaterialTheme.typography.bodySmall)
        ServiceTarget.entries.forEach { service ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(service.title, modifier = Modifier.weight(1f)); Switch(service in selected, { toggle(service) })
            }
        }
        OutlinedButton(onClick = profile, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Профиль DPI и проверка") }
        OutlinedButton(onClick = { addTile(ShortcutMode.DPI) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Добавить плитку DPI") }
        VpnDiagnosticsCard()
        }
        "Настройки TG" -> {
        Text("Включите прокси на вкладке Telegram, затем подтвердите подключение в самом Telegram.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { addTile(ShortcutMode.TG) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Добавить плитку TG") }
        OutlinedButton(onClick = { tgLog = !tgLog }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (tgLog) "Скрыть журнал Telegram" else "Диагностика Telegram") }
        if (tgLog) Text(tg.logs.joinToString("\n").ifBlank { "Журнал пуст" }, style = MaterialTheme.typography.bodySmall)
        }
        "О приложении" -> {
        Text("Бесплатно, без рекламы и телеметрии. DPI работает локально и не скрывает IP; сети с белым списком он не заменяет. VLESS требует вашей ссылки и отправляет трафик на указанный вами сервер. Telegram может использовать сторонние ретрансляторы Flowseal; сообщения зашифрованы, но узлы видят сетевые метаданные. Исключения подключаются напрямую. Смена маршрута может кратко прервать соединение. Запуск движка не гарантирует доступность всех сайтов. Код Android — GPL-3.0-or-later; лицензии зависимостей включены в APK.", style = MaterialTheme.typography.bodyMedium)
        }
        }
        }
        CommunityLinksPanel()
    }
    }
}
