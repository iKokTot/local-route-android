package org.localroute.android.telegram

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TelegramProxyPhase { STOPPED, STARTING, LISTENING, ERROR }
data class TelegramProxyStatus(
    val phase: TelegramProxyPhase = TelegramProxyPhase.STOPPED,
    val message: String = "Прокси выключен",
    val checking: Boolean = false,
    val verified: Boolean? = null,
    val logs: List<String> = emptyList()
)

object TelegramProxyRuntime {
    private val mutable = MutableStateFlow(TelegramProxyStatus())
    val state = mutable.asStateFlow()
    fun starting() { mutable.update { TelegramProxyStatus(TelegramProxyPhase.STARTING, "Запуск Telegram-прокси…", logs = it.logs) } }
    fun listening() { mutable.update { it.copy(phase = TelegramProxyPhase.LISTENING, verified = null, message = "Локальный порт открыт. Доступ к Telegram ещё не проверен.") } }
    fun failed(message: String) {
        log(message)
        mutable.update { it.copy(phase = TelegramProxyPhase.ERROR, message = "Не удалось запустить Telegram-прокси. Остановите его и включите снова. Подробности — в журнале.", checking = false, verified = false) }
    }
    fun stopped() { mutable.update { if (it.phase == TelegramProxyPhase.ERROR) it else it.copy(phase = TelegramProxyPhase.STOPPED, message = "Прокси выключен", checking = false, verified = null) } }
    fun checking() { mutable.update { it.copy(checking = true, message = "Проверяем ответ Telegram через локальный прокси…") } }
    fun checked(message: String, verified: Boolean? = null) {
        org.localroute.android.core.DiagnosticJournal.record("CHECK/TG",message)
        mutable.update { it.copy(checking = false, message = message, verified = verified) }
    }
    fun log(message: String) {
        org.localroute.android.core.DiagnosticJournal.record("TG",message)
        mutable.update { it.copy(logs = (it.logs + safe(message)).takeLast(60)) }
    }
    private fun safe(value: String) = value.replace(Regex("(?:tg|socks5)://[^\\s]+"), "[ссылка скрыта]")
        .replace(Regex("(?i)[0-9a-f]{32,}"), "[ключ скрыт]").take(500)
}
