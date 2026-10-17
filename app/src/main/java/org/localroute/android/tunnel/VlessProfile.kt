package org.localroute.android.tunnel

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

enum class VlessTransport(val wireName: String) { TCP("tcp"), WEBSOCKET("ws"), GRPC("grpc"), HTTP_UPGRADE("httpupgrade") }
enum class VlessSecurity(val wireName: String) { NONE("none"), TLS("tls"), REALITY("reality") }

data class VlessEndpoint(
    val server: String,
    val port: Int,
    val uuid: String,
    val transport: VlessTransport,
    val security: VlessSecurity,
    val sni: String?,
    val host: String?,
    val path: String?,
    val serviceName: String?,
    val flow: String?,
    val realityPublicKey: String?,
    val realityShortId: String?,
    val allowInsecure: Boolean,
    val fingerprint: String? = null,
    val alpn: List<String> = emptyList()
)

/** Parses only the VLESS variants that Local Route deliberately supports. */
object VlessUriParser {
    fun parse(value: String): VlessEndpoint {
        val uri = runCatching { URI(value.trim()) }.getOrElse {
            throw IllegalArgumentException("Не удалось прочитать VLESS-ссылку")
        }
        require(uri.scheme.equals("vless", ignoreCase = true)) { "Нужна ссылка, начинающаяся с vless://" }
        val server = uri.host?.trim().orEmpty()
        require(server.isNotBlank() && uri.port in 1..65535) { "В ссылке нужны адрес и порт сервера" }
        val uuid = decode(uri.userInfo).trim()
        require(uuid.isNotBlank()) { "В ссылке нет UUID пользователя" }
        val query = parseQuery(uri.rawQuery)
        val transport = when (query["type"]?.lowercase() ?: "tcp") {
            "tcp" -> VlessTransport.TCP
            "ws" -> VlessTransport.WEBSOCKET
            "grpc" -> VlessTransport.GRPC
            "httpupgrade" -> VlessTransport.HTTP_UPGRADE
            else -> throw IllegalArgumentException("Этот транспорт VLESS пока не поддерживается")
        }
        val security = when (query["security"]?.lowercase() ?: "none") {
            "none" -> VlessSecurity.NONE
            "tls" -> VlessSecurity.TLS
            "reality" -> VlessSecurity.REALITY
            else -> throw IllegalArgumentException("Этот тип защиты VLESS пока не поддерживается")
        }
        val publicKey = query["pbk"]?.takeIf { it.isNotBlank() }
        if (security == VlessSecurity.REALITY) require(publicKey != null) { "Для Reality требуется параметр pbk" }
        val fingerprint = query["fp"]?.takeIf { it.isNotBlank() }
        require(fingerprint == null || fingerprint in setOf("chrome", "firefox", "edge", "safari", "360", "qq", "ios", "android", "random", "randomized")) { "TLS fingerprint не поддерживается" }
        return VlessEndpoint(
            server = server,
            port = uri.port,
            uuid = uuid,
            transport = transport,
            security = security,
            sni = query["sni"]?.takeIf { it.isNotBlank() } ?: query["host"]?.takeIf { it.isNotBlank() },
            host = query["host"]?.takeIf { it.isNotBlank() },
            path = query["path"]?.ifBlank { "/" },
            serviceName = query["serviceName"]?.takeIf { it.isNotBlank() },
            flow = query["flow"]?.takeIf { it.isNotBlank() },
            realityPublicKey = publicKey,
            realityShortId = query["sid"]?.takeIf { it.isNotBlank() },
            allowInsecure = query["allowInsecure"] == "1",
            fingerprint = fingerprint ?: if (security == VlessSecurity.REALITY) "chrome" else null,
            alpn = query["alpn"].orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
        )
    }

    private fun parseQuery(raw: String?): Map<String, String> = raw.orEmpty().split('&')
        .filter { it.isNotBlank() }
        .associate { token ->
            val divider = token.indexOf('=')
            val key = if (divider >= 0) token.substring(0, divider) else token
            val value = if (divider >= 0) token.substring(divider + 1) else ""
            decode(key) to decode(value)
        }

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}
