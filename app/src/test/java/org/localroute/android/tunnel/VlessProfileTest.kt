package org.localroute.android.tunnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VlessProfileTest {
    @Test fun `ChatGPT compatibility is scoped to server mode and leaves IPv6 tunnel intact`() {
        val endpoint=VlessUriParser.parse("vless://id@example.com:443?security=none")
        val vpn=CombinedConfigCompiler.compile(org.localroute.android.core.CombinedRouting(serverEnabled=true),endpoint)
        assertTrue(vpn.contains("\"query_type\":[\"AAAA\",\"HTTPS\"]"))
        assertTrue(vpn.contains("\"action\":\"predefined\",\"rcode\":\"NOERROR\""))
        assertTrue(vpn.contains("fdfe:dcba:9876::1/126"))
        assertFalse(vpn.contains("\"strategy\":\"ipv4_only\""))
        val dpi=CombinedConfigCompiler.compile(org.localroute.android.core.CombinedRouting(dpiEnabled=true))
        assertFalse(dpi.contains("\"action\":\"predefined\""))
    }
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

    @Test fun `gateway requires authentication and uses the Android userspace stack`() {
        val endpoint = VlessUriParser.parse("vless://id@example.com:443?security=none")
        val config = VlessConfigCompiler.compile(endpoint, socksPassword = "test-password")
        assertTrue(config.contains("\"stack\":\"gvisor\""))
        assertTrue(config.contains("\"users\":[{\"username\":\"localroute\",\"password\":\"test-password\"}]"))
        assertFalse(config.contains("include_package"))
    }

    @Test fun `compiled profile keeps a VLESS outbound and private traffic direct`() {
        val endpoint = VlessUriParser.parse("vless://00000000-0000-4000-8000-000000000000@example.com:443?type=grpc&security=tls&sni=cdn.example.com&serviceName=grpc")
        val config = VlessConfigCompiler.compile(endpoint)

        assertTrue(config.contains("\"type\":\"vless\""))
        assertTrue(config.contains("\"service_name\":\"grpc\""))
        assertTrue(config.contains("\"ip_is_private\":true"))
        assertTrue(config.contains("\"auto_detect_interface\":true"))
        assertTrue(config.contains("\"detour\":\"vless-out\""))
    }
}
