package org.localroute.android.telegram

import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Asks for a public protocol response; never authenticates or reads user messages. */
object TelegramMtprotoProbe {
    fun check(secretHex: String, port: Int = 1443, dc: Int = 2, timeout: Int = 15000) {
        val secret = secretHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val random = SecureRandom()
        val raw = ByteArray(64).also(random::nextBytes)
        // Reject the reserved HTTP/TLS/MTProto prefixes in obfuscated transport.
        raw[0] = 0x42
        raw[4] = 1
        val enc = cipher(raw.copyOfRange(8, 56), secret)
        val reverse = raw.copyOfRange(8, 56).reversedArray()
        val dec = cipher(reverse, secret)
        val stream = enc.update(ByteArray(64))
        repeat(4) { raw[56 + it] = (0xdd xor (stream[56 + it].toInt() and 255)).toByte() }
        raw[60] = (dc xor (stream[60].toInt() and 255)).toByte()
        raw[61] = stream[61]
        val nonce = ByteArray(16).also(random::nextBytes)
        val request = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(40).putLong(0).putLong((System.currentTimeMillis() / 1000) shl 32)
            .putInt(20).putInt(0xbe7e8ef1.toInt()).put(nonce).array()
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 2000)
            socket.soTimeout = timeout
            socket.getOutputStream().apply { write(raw); write(enc.update(request)); flush() }
            val encrypted = ByteArray(44)
            var offset = 0
            while (offset < encrypted.size) {
                val count = socket.getInputStream().read(encrypted, offset, encrypted.size - offset)
                check(count > 0) { "соединение закрыто" }
                offset += count
            }
            val response = dec.update(encrypted)
            val buffer = ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN)
            check(buffer.getLong(4) == 0L && buffer.getInt(24) == 0x05162463 && response.copyOfRange(28, 44).contentEquals(nonce)) { "не получен корректный resPQ" }
        }
    }
    private fun cipher(prekey: ByteArray, secret: ByteArray): Cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
        val key = MessageDigest.getInstance("SHA-256").digest(prekey.copyOfRange(0, 32) + secret)
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(prekey.copyOfRange(32, 48)))
    }
}
