package org.localroute.android.tunnel

import io.nekohasekai.libbox.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Capture warnings/errors with their bounded connection context; never upload logs. */
object VpnDiagnostics {
    private val context=org.localroute.android.core.FailureLogContext()
    private val mutable = MutableStateFlow<List<String>>(emptyList())
    val lines = mutable.asStateFlow()
    fun redact(text: String): String = org.localroute.android.core.DiagnosticRedactor.clean(text)
    fun add(text: String) {
        val safe=redact(text).take(1800)
        mutable.update { (it + safe).takeLast(100) }
        org.localroute.android.core.DiagnosticJournal.record("VPN",safe)
    }
    fun reset() { context.clear(); mutable.value=emptyList() }
    fun startCapture() {
        context.startCapture()
        add("Начата подробная запись на 90 секунд: адреса, DNS, TCP/UDP и маршруты; содержимое сообщений не записывается.")
    }
    fun stopCapture() { context.stopCapture(); add("Подробная запись завершена.") }
    fun capturing(): Boolean = context.capturing()
    fun acceptLine(text: String) { context.accept(text).forEach(::add) }
    fun handler(): CommandClientHandler = object : CommandClientHandler {
        override fun clearLogs() { context.clear() }
        override fun connected() = Unit
        override fun disconnected(message: String?) { if(!message.isNullOrBlank()) add("Журнал движка отключён: $message") }
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
                acceptLine(line)
            }
        }
    }
}
