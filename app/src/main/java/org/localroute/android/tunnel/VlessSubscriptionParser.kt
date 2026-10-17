package org.localroute.android.tunnel

import java.util.Base64

/** Parses the intentionally small, predictable subscription format supported by Local Route.
 *  The input is either newline-separated vless:// links or a base64 encoded version of it.
 *  No remote fetching is performed here; the caller owns HTTPS, limits and refresh policy.
 */
object VlessSubscriptionParser {
    fun parse(raw: String, maxProfiles: Int = 64): List<VlessProfile> {
        require(raw.length <= 512_000) { "Список серверов слишком большой" }
        val text = decodeBase64IfNeeded(raw.trim())
        return text.lineSequence()
            .map(String::trim)
            .filter { it.startsWith("vless://", ignoreCase = true) }
            .distinct()
            .mapNotNull { link -> runCatching { VlessProfile(link, VlessUriParser.parse(link)) }.getOrNull() }
            .take(maxProfiles)
            .toList()
    }

    private fun decodeBase64IfNeeded(value: String): String {
        if (value.startsWith("vless://", ignoreCase = true) || value.contains("\nvless://", true)) return value
        val compact = value.replace("\\s".toRegex(), "")
        return runCatching { String(Base64.getDecoder().decode(compact.replace('-', '+').replace('_', '/')), Charsets.UTF_8) }
            .getOrDefault(value)
    }
}

data class VlessProfile(val link: String, val endpoint: VlessEndpoint) {
    val name: String = link.substringAfterLast('#', "").takeIf { it.isNotBlank() }?.let {
        runCatching { java.net.URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.getOrDefault(it)
    }?.filterNot { it.isISOControl() }?.take(80) ?: endpoint.server
}
