package org.localroute.android.tunnel

import java.security.SecureRandom

/** Device-local authenticated SOCKS entry for TGWs; credentials never enter logs or storage. */
object VlessGateway {
    const val PORT = 1081
    @Volatile var password: String? = null
        private set
    fun prepare(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it.toInt() and 255) }.also { password = it }
    fun close() { password = null }
    fun proxyUrl(): String? = password?.takeIf { VlessRuntime.state.value.state == TunnelState.Connected }
        ?.let { "socks5://localroute:$it@127.0.0.1:$PORT" }
}
