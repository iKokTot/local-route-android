package org.localroute.android.tunnel

import org.junit.Assert.*
import org.junit.Test
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class SubscriptionLoaderTest {
    private class Response(val body: String, val status: Int = 200) : HttpsURLConnection(URL("https://example.com/list")) {
        var closed = false
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getInputStream() = body.byteInputStream()
        override fun getCipherSuite() = "TEST"
        override fun getLocalCertificates(): Array<java.security.cert.Certificate>? = null
        override fun getServerCertificates(): Array<java.security.cert.Certificate> = emptyArray()
    }
    @Test fun loadsOnlyDataAndDisconnects() {
        val response = Response("vless://id@example.com:443?security=tls")
        assertEquals(1, SubscriptionLoader.load("https://example.com/list") { response }.size)
        assertTrue(response.closed)
        assertFalse(response.instanceFollowRedirects)
    }
    @Test fun rejectsErrorsEmptyAndOversizedResponseWithoutKeepingSocket() {
        for (response in listOf(Response("", 302), Response("<html>error</html>"), Response("x".repeat(512_001)))) {
            assertTrue(runCatching { SubscriptionLoader.load("https://example.com/list") { response } }.isFailure)
            assertTrue(response.closed)
        }
    }
    @Test fun refusesPlainHttpBeforeOpeningConnection() {
        assertTrue(runCatching { SubscriptionLoader.load("http://example.com/list") { error("must not open") } }.isFailure)
    }
}
