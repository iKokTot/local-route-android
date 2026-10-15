package org.localroute.android.core

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class ProbeSummary(val successful: Boolean, val lines: List<String>)

object ConnectivityProbe {
    private data class Target(
        val service: ServiceTarget,
        val label: String,
        val host: String,
        val path: String,
        val statuses: IntRange = 200..299,
        val contentType: String? = null,
        val bodyContains: String? = null,
        val minBodyBytes: Int = 0
    )

    private val quickTargets = listOf(
        Target(ServiceTarget.YOUTUBE, "основной узел", "www.youtube.com", "/generate_204", statuses = 200..299),
        Target(ServiceTarget.DISCORD, "REST API", "discord.com", "/api/v10/gateway", bodyContains = "gateway.discord.gg")
    )

    private val detailedTargets = listOf(
        Target(
            ServiceTarget.YOUTUBE,
            "изображения видео",
            "i.ytimg.com",
            "/vi/jNQXAC9IVRw/hqdefault.jpg",
            contentType = "image/",
            minBodyBytes = 1024
        ),
        Target(
            ServiceTarget.YOUTUBE,
            "маршрутизация видео",
            "redirector.googlevideo.com",
            "/report_mapping",
            statuses = 200..399,
            minBodyBytes = 1
        ),
        Target(ServiceTarget.DISCORD, "REST API", "discord.com", "/api/v10/gateway", bodyContains = "gateway.discord.gg"),
        Target(
            ServiceTarget.DISCORD,
            "CDN",
            "cdn.discordapp.com",
            "/embed/avatars/0.png",
            contentType = "image/",
            minBodyBytes = 512
        )
    )

    /**
     * Resolves DNS before entering SOCKS, matching tun2socks traffic (which
     * already has a destination IP), but keeps the original host for TLS SNI
     * and certificate verification. This confirms that the chosen ByeDPI
     * strategy actually carried the TLS handshake.
     */
    suspend fun throughLocalSocks(
        services: Set<ServiceTarget>,
        timeoutMs: Int = 4500,
        thorough: Boolean = false
    ): ProbeSummary = withContext(Dispatchers.IO) {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1080))
        val selectedTargets = (if (thorough) detailedTargets else quickTargets)
            .filter { it.service in services }
        val lines = selectedTargets.map { target ->
            runCatching {
                val address = InetAddress.getAllByName(target.host).firstOrNull { it is Inet4Address }
                    ?: error("IPv4-адрес не найден")
                Socket(proxy).use { plain ->
                    plain.connect(InetSocketAddress(address, 443), timeoutMs)
                    plain.soTimeout = timeoutMs
                    val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                    val ssl = factory.createSocket(plain, target.host, 443, true) as SSLSocket
                    ssl.sslParameters = ssl.sslParameters.apply {
                        endpointIdentificationAlgorithm = "HTTPS"
                        // The probe writes an HTTP/1.1 request below. Without an
                        // explicit ALPN choice Android can negotiate h2 and then
                        // wait forever for a response to HTTP/1.1 text.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            applicationProtocols = arrayOf("http/1.1")
                        }
                    }
                    ssl.startHandshake()
                    ssl.use {
                        val request = buildString {
                            append("GET ${target.path} HTTP/1.1\r\n")
                            append("Host: ${target.host}\r\n")
                            append("User-Agent: LocalRoute/0.4\r\n")
                            append("Accept: */*\r\n")
                            append("Connection: close\r\n\r\n")
                        }.toByteArray(Charsets.US_ASCII)
                        ssl.outputStream.write(request)
                        ssl.outputStream.flush()

                        val input = BufferedInputStream(ssl.inputStream)
                        val status = readAsciiLine(input) ?: error("пустой ответ")
                        val code = status.split(' ').getOrNull(1)?.toIntOrNull()
                            ?: error("неверный HTTP-ответ")
                        check(code in target.statuses) { "HTTP $code" }

                        val headers = buildMap {
                            while (true) {
                                val line = readAsciiLine(input) ?: break
                                if (line.isEmpty()) break
                                val separator = line.indexOf(':')
                                if (separator > 0) put(
                                    line.substring(0, separator).lowercase(),
                                    line.substring(separator + 1).trim()
                                )
                            }
                        }
                        target.contentType?.let { prefix ->
                            check(headers["content-type"]?.startsWith(prefix, ignoreCase = true) == true) {
                                "неверный Content-Type"
                            }
                        }

                        val body = ByteArrayOutputStream()
                        val buffer = ByteArray(2048)
                        while (body.size() < 16_384) {
                            val read = input.read(buffer, 0, minOf(buffer.size, 16_384 - body.size()))
                            if (read <= 0) break
                            body.write(buffer, 0, read)
                        }
                        check(body.size() >= target.minBodyBytes) { "ответ слишком короткий" }
                        target.bodyContains?.let { token ->
                            check(body.toString(Charsets.UTF_8.name()).contains(token, ignoreCase = true)) {
                                "ответ не содержит ожидаемые данные"
                            }
                        }
                        "${target.service.title} · ${target.label}: HTTP $code, данные получены"
                    }
                }
            }.getOrElse { "${target.service.title} · ${target.label}: ошибка — ${it.message ?: it.javaClass.simpleName}" }
        }
        ProbeSummary(lines.isNotEmpty() && lines.all { "данные получены" in it }, lines)
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (bytes.size() < 8192) {
            val value = input.read()
            if (value < 0) return if (bytes.size() == 0) null else bytes.toString(Charsets.US_ASCII.name())
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes.write(value)
        }
        return bytes.toString(Charsets.US_ASCII.name())
    }
}
