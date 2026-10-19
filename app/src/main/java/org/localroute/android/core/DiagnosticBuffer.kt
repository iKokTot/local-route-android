package org.localroute.android.core

/** One sanitizer for disk, UI and clipboard. Never store complete connection URLs/configs. */
object DiagnosticRedactor {
    fun clean(value: String): String = value
        .replace(Regex("\\u001B\\[[0-?]*[ -/]*[@-~]"), "")
        .replace(Regex("(?i)\\b(?:vless|vmess|trojan|ss|socks5?|tg|https?|hysteria2?|tuic)://[^\\s<>\"']+"), "[ссылка скрыта]")
        .replace(Regex("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"), "[ключ скрыт]")
        .replace(Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+"), "$1[ключ скрыт]")
        .replace(Regex("(?i)([\"']?(?:password|passwd|secret|token|authorization|uuid|pbk|sid|private_key|public_key|short_id)[\"']?\\s*[:=]\\s*)(?:\"[^\"]*\"|'[^']*'|[^\\s,;&}]+)"), "$1[ключ скрыт]")
        .replace(Regex("(?i)\\b[0-9a-f]{32,}\\b"), "[ключ скрыт]")
        .replace('\r', ' ')
        .replace('\n', ' ')
}

class DiagnosticBuffer(private val maxLines: Int = 500, private val maxChars: Int = 120_000) {
    private val entries = ArrayDeque<String>()
    private var size = 0
    @Synchronized fun add(value: String) {
        val line=DiagnosticRedactor.clean(value).take(1800)
        entries.addLast(line); size += line.length + 1
        while(entries.size > maxLines || size > maxChars) size -= entries.removeFirst().length + 1
    }
    @Synchronized fun snapshot(): List<String> = entries.toList()
    @Synchronized fun clear() { entries.clear(); size=0 }
}

/** Successful destinations stay only in bounded RAM; persist context only on a failure. */
class FailureLogContext(private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private var captureUntil = 0L
    @Synchronized fun startCapture() { contexts.clear(); captureUntil = now() + 90_000 }
    @Synchronized fun stopCapture() { captureUntil = 0L }
    @Synchronized fun capturing(): Boolean = now() < captureUntil
    private val contexts = linkedMapOf<String, List<String>>()
    private val id = Regex("\\[(\\d+)\\s+[^]]+]")
    @Synchronized fun accept(raw: String): List<String> {
        val line=DiagnosticRedactor.clean(raw).take(1800)
        if(capturing()) return listOf(line)
        val key=id.find(line)?.groupValues?.get(1)
        val failure=Regex("\\b(error|warn|failed|unreachable|refused)\\b",RegexOption.IGNORE_CASE).containsMatchIn(line)
        if(failure) return (key?.let { contexts.remove(it) }.orEmpty() + line).distinct()
        if(key != null) {
            contexts[key]=(contexts[key].orEmpty() + line).takeLast(4)
            while(contexts.size > 128) contexts.remove(contexts.keys.first())
        }
        return emptyList()
    }
    @Synchronized fun clear() = contexts.clear()
}
