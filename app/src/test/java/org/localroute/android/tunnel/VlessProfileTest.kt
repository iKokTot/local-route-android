package org.localroute.android.tunnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VlessProfileTest {
    @Test fun `parses TLS websocket VLESS without retaining display fragment`() {
        val endpoint = VlessUriParser.parse("vless://00000000-0000-4000-8000-000000000000@example.com:443?type=ws&security=tls&path=%2Fvless&host=cdn.example.com&sni=cdn.example.com#Personal")
        assertEquals("example.com", endpoint.server)
        assertEquals(443, endpoint.port)
        assertEquals(VlessTransport.WEBSOCKET, endpoint.transport)
        assertEquals(VlessSecurity.TLS, endpoint.security)
        assertEquals("/vless", endpoint.path)
    }

    @Test fun `reality requires a public key`() {
        val failure = runCatching { VlessUriParser.parse("vless://id@example.com:443?security=reality") }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("pbk"))
    }

    @Test fun `selected package mode never turns on whole device routing`() {
        val profile = VlessRouteProfile(setOf("com.instagram.android"))
        assertFalse(profile.routeAllApplications)
        assertEquals(setOf("com.instagram.android"), profile.selectedPackages)
    }

    @Test fun `compiled profile keeps a VLESS outbound and private traffic direct`() {
        val endpoint = VlessUriParser.parse("vless://00000000-0000-4000-8000-000000000000@example.com:443?type=grpc&security=tls&sni=cdn.example.com&serviceName=grpc")
        val config = VlessConfigCompiler.compile(endpoint)

        assertTrue(config.contains("\"type\":\"vless\""))
        assertTrue(config.contains("\"service_name\":\"grpc\""))
        assertTrue(config.contains("\"ip_is_private\":true"))
    }
}
