package org.localroute.android.tunnel

import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** HTTPS response through the active authenticated gateway, not a TCP-port check. */
object ServerConnectionProbe {
    fun check(): Boolean {
        val password = VlessGateway.password ?: return false
        Socket().use { socket ->
            socket.connect(java.net.InetSocketAddress("127.0.0.1", VlessGateway.PORT), 5000)
            socket.soTimeout = 8000
            val input = java.io.DataInputStream(socket.getInputStream())
            val output = socket.getOutputStream()
            output.write(byteArrayOf(5, 1, 2)); output.flush()
            require(input.readUnsignedByte() == 5 && input.readUnsignedByte() == 2)
            val user = "localroute".toByteArray(); val pass = password.toByteArray()
            output.write(byteArrayOf(1, user.size.toByte()) + user + byteArrayOf(pass.size.toByte()) + pass); output.flush()
            require(input.readUnsignedByte() == 1 && input.readUnsignedByte() == 0)
            val host = "example.com".toByteArray()
            output.write(byteArrayOf(5, 1, 0, 3, host.size.toByte()) + host + byteArrayOf(1, -69)); output.flush()
            require(input.readUnsignedByte() == 5 && input.readUnsignedByte() == 0)
            input.readUnsignedByte()
            val count = when (input.readUnsignedByte()) { 1 -> 4; 4 -> 16; 3 -> input.readUnsignedByte(); else -> error("SOCKS") }
            input.readFully(ByteArray(count + 2))
            ((SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(socket, "example.com", 443, true) as SSLSocket).use { tls ->
                tls.soTimeout = 8000
                tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                tls.startHandshake()
                tls.outputStream.write("GET / HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n".toByteArray())
                tls.outputStream.flush()
                return tls.inputStream.bufferedReader().readLine()?.startsWith("HTTP/1.1 200") == true
            }
        }
    }
}
