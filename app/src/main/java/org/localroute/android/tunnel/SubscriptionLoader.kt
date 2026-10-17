package org.localroute.android.tunnel

import java.net.URI
import javax.net.ssl.HttpsURLConnection

/** Downloads data only: subscriptions cannot install arbitrary engine configuration. */
object SubscriptionLoader {
    fun load(address: String): List<VlessProfile> = load(address) { it.toURL().openConnection() as HttpsURLConnection }

    internal fun load(address: String, open: (URI) -> HttpsURLConnection): List<VlessProfile> {
        val uri = URI(address.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) { "Нужна HTTPS-ссылка на список серверов" }
        val connection = open(uri)
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = false
        try {
            require(connection.responseCode == 200) { "Источник не вернул список серверов" }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 512_000) { "Список серверов слишком большой" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            require(bytes.size <= 512_000) { "Список серверов слишком большой" }
            return VlessSubscriptionParser.parse(bytes.toString(Charsets.UTF_8)).also {
                require(it.isNotEmpty()) { "В списке нет поддерживаемых VLESS-серверов" }
            }
        } finally { connection.disconnect() }
    }
}
