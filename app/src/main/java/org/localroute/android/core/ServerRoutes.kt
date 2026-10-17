package org.localroute.android.core

/** Одинаковый порядок на обеих платформах: DPI, правила VPN, прямой выход. */
data class ServerRoutes(
    val allTraffic: Boolean = true,
    val domains: Set<String> = setOf("tiktok.com", "tiktokv.com", "tiktokcdn.com", "ibytedtos.com", "byteoversea.com", "musical.ly", "instagram.com", "cdninstagram.com", "fbcdn.net"),
    val packages: Set<String> = emptySet()
) {
    init {
        require(domains.all(RouteDomains::valid)) { "Укажите домены без адресов страниц и портов" }
        require(packages.all { Regex("[a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+").matches(it) }) { "Нужны имена пакетов Android, например com.openai.chatgpt" }
    }
}

object RouteDomains {
    fun valid(value: String): Boolean = value.length <= 253 &&
        value.split('.').let { parts -> parts.size >= 2 && parts.all { Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?").matches(it) } && parts.last().any(Char::isLetter) }
    fun parse(value: String): Set<String> = value.split(Regex("[\\s,;]+")).filter(String::isNotBlank).map {
        it.lowercase(java.util.Locale.ROOT).removePrefix("*.").trimEnd('.')
    }.toSet().also { require(it.all(::valid)) { "Нужны домены, например example.com, по одному в строке" } }
}
