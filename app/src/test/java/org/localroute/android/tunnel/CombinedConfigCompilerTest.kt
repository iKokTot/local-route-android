package org.localroute.android.tunnel

import org.junit.Assert.*
import org.junit.Test
import org.localroute.android.core.CombinedRouting

class CombinedConfigCompilerTest {
    private val endpoint = VlessUriParser.parse("vless://00000000-0000-4000-8000-000000000000@example.com:443?security=tls&type=ws&path=%2Ftest")
    @Test fun bothModesIncludeLocalRulesBeforeDefaultServer() {
        val config = CombinedConfigCompiler.compile(CombinedRouting(true, true), endpoint)
        assertFalse(config.contains("package_name"))
        assertTrue(config.contains("\"domain_suffix\""))
        assertTrue(config.contains("\"final\":\"vless-out\""))
        assertTrue(config.contains("\"udp_over_tcp\":false"))
        assertTrue(config.contains("\"path\":\"/test\""))
    }
    @Test fun dpiAloneContainsNoServerOutboundOrServerDns() {
        val config = CombinedConfigCompiler.compile(CombinedRouting(true, false))
        assertFalse(config.contains("vless-out"))
        assertFalse(config.contains("server-dns"))
        assertTrue(config.contains("\"final\":\"direct\""))
        assertFalse(config.contains("package_name"))
        assertTrue(config.contains("\"sniff\""))
    }
    @Test fun serverAloneContainsNoLocalSocksOrPackageRules() {
        val config = CombinedConfigCompiler.compile(CombinedRouting(false, true), endpoint)
        assertFalse(config.contains("local-dpi"))
        assertFalse(config.contains("package_name"))
        assertTrue(config.contains("vless-out"))
    }
    @Test fun invalidDesiredStatesFailBeforeOpeningTun() {
        assertTrue(runCatching { CombinedConfigCompiler.compile(CombinedRouting()) }.isFailure)
        assertTrue(runCatching { CombinedConfigCompiler.compile(CombinedRouting(false, true)) }.isFailure)
        assertTrue(runCatching { CombinedConfigCompiler.compile(CombinedRouting(true, false), bootstrapDns = "bad\"dns") }.isFailure)
    }
}
