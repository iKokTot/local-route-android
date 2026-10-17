package org.localroute.android.tunnel

import io.nekohasekai.libbox.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory warning/error log. Never uploads or records a complete configuration. */
object VpnDiagnostics {
    private val mutable = MutableStateFlow<List<String>>(emptyList())
    val lines = mutable.asStateFlow()
    fun redact(text: String): String = text
        .replace(Regex("vless://[^\\s]+", RegexOption.IGNORE_CASE), "[ссылка скрыта]")
        .replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"), "[ключ скрыт]")
        .replace(Regex("socks5://[^@\\s]+@"), "socks5://[ключ скрыт]@")
    fun add(text: String) { mutable.update { (it + redact(text).take(1200)).takeLast(100) } }
    fun handler(): CommandClientHandler = object : CommandClientHandler {
        override fun clearLogs() = Unit
        override fun connected() = Unit
        override fun disconnected(message: String?) = Unit
        override fun initializeClashMode(modes: StringIterator?, currentMode: String?) = Unit
        override fun setDefaultLogLevel(level: Int) = Unit
        override fun updateClashMode(mode: String?) = Unit
        override fun writeConnectionEvents(events: ConnectionEvents?) = Unit
        override fun writeGroups(groups: OutboundGroupIterator?) = Unit
        override fun writeOutbounds(outbounds: OutboundGroupItemIterator?) = Unit
        override fun writeStatus(status: StatusMessage?) = Unit
        override fun writeLogs(logs: LogIterator?) {
            while (logs?.hasNext() == true) {
                val line = logs.next().message
                // Debug builds also expose connection destinations: keep only failures.
                if (Regex("error|warn|failed|timeout|unreachable|refused", RegexOption.IGNORE_CASE).containsMatchIn(line)) add(line)
            }
        }
    }
}
