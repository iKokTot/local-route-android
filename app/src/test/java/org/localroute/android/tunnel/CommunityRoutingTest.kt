package org.localroute.android.tunnel

import org.junit.Assert.*
import org.junit.Test
import org.localroute.android.core.*
import java.io.File

class CommunityRoutingTest {
    private val link = "vless://00000000-0000-4000-8000-000000000000@example.com:443?security=reality&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&fp=firefox&alpn=h2%2Chttp%2F1.1"
    @Test fun realityKeepsFingerprintAndAlpn() {
        val endpoint = VlessUriParser.parse(link)
        val config = VlessConfigCompiler.compile(endpoint)
        assertEquals("firefox", endpoint.fingerprint)
        assertTrue(config.contains("\"utls\":{\"enabled\":true,\"fingerprint\":\"firefox\"}"))
        assertTrue(config.contains("\"alpn\":[\"h2\",\"http/1.1\"]"))
        assertEquals("chrome", VlessUriParser.parse(link.replace("&fp=firefox", "")).fingerprint)
    }
    @Test fun dpiDomainWinsOverVpnDomainAndPackageAndUnlistedTrafficIsDirect() {
        val plan = CombinedRouting(true, true, customDpiDomains = setOf("custom.example"),
            serverRoutes = ServerRoutes(false, setOf("youtube.com", "vpn.example"), setOf("com.android.chrome")))
        assertEquals(TrafficDestination.LOCAL_DPI, plan.destinationForDomain("video.youtube.com"))
        assertEquals(TrafficDestination.LOCAL_DPI, plan.destinationForDomain("custom.example"))
        assertEquals(TrafficDestination.SERVER, plan.destinationForDomain("vpn.example"))
        assertEquals(TrafficDestination.DIRECT, plan.destinationForDomain("other.example"))
        val config = CombinedConfigCompiler.compile(plan, VlessUriParser.parse(link))
        assertTrue(config.indexOf("\"outbound\":\"local-dpi\"") < config.indexOf("\"package_name\""))
        assertTrue(config.contains("\"reverse_mapping\":true"))
    }
    @Test fun allCombinationsProduceConfigurationsForEngineValidation() {
        val output = File("build/community-configs").apply { mkdirs() }
        for (dpi in listOf(false, true)) for (server in listOf(false, true)) for (all in listOf(false, true)) {
            if (!dpi && !server) continue
            val plan = CombinedRouting(dpi, server, customDpiDomains = setOf("custom.example"),
                serverRoutes = ServerRoutes(all, setOf("vpn.example"), setOf("com.android.chrome")))
            val config = CombinedConfigCompiler.compile(plan, VlessUriParser.parse(link), gatewayPassword = if (server) "a".repeat(32) else null)
            File(output, "$dpi-$server-$all.json").writeText(config)
            assertEquals(if (server && all) TrafficDestination.SERVER else TrafficDestination.DIRECT, plan.defaultDestination)
            if (dpi) assertEquals(TrafficDestination.LOCAL_DPI, plan.destinationForDomain("youtube.com"))
        }
    }
    @Test fun rejectsDomainInjectionAndAcceptsSubdomains() {
        assertEquals(setOf("example.com"), RouteDomains.parse("*.Example.com."))
        assertTrue(runCatching { RouteDomains.parse("example.com\"}") }.isFailure)
        assertTrue(runCatching { ServerRoutes(packages = setOf("bad\"package")) }.isFailure)
    }
}
