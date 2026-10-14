package org.localroute.android.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class ProbeSummary(val successful: Boolean, val lines: List<String>)

object ConnectivityProbe {
    private data class Target(val host: String, val path: String)
    private val targets = mapOf(
        ServiceTarget.YOUTUBE to Target("www.youtube.com", "/generate_204"),
        ServiceTarget.DISCORD to Target("discord.com", "/api/v10/gateway")
    )

    /**
     * Resolves DNS before entering SOCKS, matching tun2socks traffic (which
     * already has a destination IP), but keeps the original host for TLS SNI
     * and certificate verification. This confirms that the chosen ByeDPI
     * strategy actually carried the TLS handshake.
     */
    suspend fun throughLocalSocks(
        services: Set<ServiceTarget>,
        timeoutMs: Int = 4500
    ): ProbeSummary = withContext(Dispatchers.IO) {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1080))
        val lines = services.map { service ->
            val target = targets.getValue(service)
            runCatching {
                val address = InetAddress.getAllByName(target.host).firstOrNull { it is Inet4Address }
                    ?: error("IPv4-адрес не найден")
                Socket(proxy).use { plain ->
                    plain.connect(InetSocketAddress(address, 443), timeoutMs)
                    plain.soTimeout = timeoutMs
                    val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                    val ssl = factory.createSocket(plain, target.host, 443, true) as SSLSocket
                    ssl.sslParameters = ssl.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                    ssl.startHandshake()
                    ssl.outputStream.bufferedWriter(Charsets.US_ASCII).use { writer ->
                        writer.write("GET ${target.path} HTTP/1.1\r\n")
                        writer.write("Host: ${target.host}\r\n")
                        writer.write("User-Agent: LocalRoute/0.2\r\n")
                        writer.write("Connection: close\r\n\r\n")
                        writer.flush()
                        val status = BufferedReader(InputStreamReader(ssl.inputStream, Charsets.US_ASCII)).readLine()
                            ?: error("пустой ответ")
                        val code = status.split(' ').getOrNull(1)?.toIntOrNull() ?: error("неверный HTTP-ответ")
                        check(code in 200..399) { "HTTP $code" }
                        "${service.title}: доступ подтверждён ($code)"
                    }
                }
            }.getOrElse { "${service.title}: ошибка — ${it.message ?: it.javaClass.simpleName}" }
        }
        ProbeSummary(lines.isNotEmpty() && lines.all { "доступ подтверждён" in it }, lines)
    }
}
