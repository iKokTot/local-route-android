package org.localroute.android.tunnel

import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** HTTPS response through the active authenticated gateway, not a TCP-port check. */
object ServerConnectionProbe {
    data class Result(val host: String, val success: Boolean, val detail: String, val durationMs: Long? = null, val httpStatus: Int? = null)
    internal fun acceptsStatus(line: String?): Boolean = line?.let { Regex("HTTP/1\\.[01] ([0-9]{3})(?: .*|)$").matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() in 200..399 } ?: false
    fun check(): Boolean = probe().any { it.success }
    fun probe(): List<Result> {
        val hosts = listOf("example.com", "www.cloudflare.com", "www.wikipedia.org")
        val password = VlessGateway.password ?: return hosts.map { Result(it, false, "VPN не запущен") }
        val sockets = java.util.concurrent.ConcurrentHashMap.newKeySet<Socket>()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(hosts.size) { task -> Thread(task, "vpn-probe").apply { isDaemon = true } }
        try {
            val tasks = hosts.map { host -> java.util.concurrent.Callable {
                val start=System.nanoTime()
                val result=try { endpoint(host, password, sockets) }
                catch (_: javax.net.ssl.SSLException) { Result(host, false, "Нет защищённого HTTPS-ответа") }
                catch (_: Exception) { Result(host, false, "Ответ не получен") }
                result.copy(durationMs=((System.nanoTime()-start)/1_000_000).coerceAtLeast(1))
            } }
            return pool.invokeAll(tasks, 12, java.util.concurrent.TimeUnit.SECONDS).mapIndexed { index, future ->
                if(future.isCancelled) Result(hosts[index], false, "Время ожидания истекло") else future.get()
            }
        } finally { pool.shutdownNow(); sockets.forEach { runCatching { it.close() } } }
    }
    private fun endpoint(hostName: String, password: String, sockets: MutableSet<Socket>): Result {
        Socket().use { socket ->
            sockets.add(socket)
            socket.connect(java.net.InetSocketAddress("127.0.0.1", VlessGateway.PORT), 3000)
            socket.soTimeout = 4000
            val input = java.io.DataInputStream(socket.getInputStream())
            val output = socket.getOutputStream()
            output.write(byteArrayOf(5, 1, 2)); output.flush()
            require(input.readUnsignedByte() == 5 && input.readUnsignedByte() == 2)
            val user = "localroute".toByteArray(); val pass = password.toByteArray()
            output.write(byteArrayOf(1, user.size.toByte()) + user + byteArrayOf(pass.size.toByte()) + pass); output.flush()
            require(input.readUnsignedByte() == 1 && input.readUnsignedByte() == 0)
            val host = hostName.toByteArray()
            output.write(byteArrayOf(5, 1, 0, 3, host.size.toByte()) + host + byteArrayOf(1, -69)); output.flush()
            require(input.readUnsignedByte() == 5 && input.readUnsignedByte() == 0)
            input.readUnsignedByte()
            val count = when (input.readUnsignedByte()) { 1 -> 4; 4 -> 16; 3 -> input.readUnsignedByte(); else -> error("SOCKS") }
            input.readFully(ByteArray(count + 2))
            ((SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(socket, hostName, 443, true) as SSLSocket).use { tls ->
                tls.soTimeout = 4000
                tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                tls.startHandshake()
                tls.outputStream.write("HEAD / HTTP/1.1\r\nHost: $hostName\r\nConnection: close\r\n\r\n".toByteArray())
                tls.outputStream.flush()
                val line = tls.inputStream.bufferedReader().readLine()?.take(128)
                val success = acceptsStatus(line)
                val status=line?.split(' ')?.getOrNull(1)?.toIntOrNull()
                return Result(hostName, success, if(success) "HTTPS-ответ получен" else if(status!=null) "Сайт ответил HTTP $status; это не обрыв VPN" else "HTTP-ответ не получен",httpStatus=status)
            }
        }
    }
}
