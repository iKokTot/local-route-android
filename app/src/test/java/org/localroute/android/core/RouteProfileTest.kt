package org.localroute.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProfileTest {
    @Test fun `default profile is local and selects only supported services`() {
        val profile = RouteProfile()
        assertEquals(TrafficMode.LOCAL_DPI, profile.mode)
        assertEquals(setOf(ServiceTarget.YOUTUBE, ServiceTarget.DISCORD), profile.services)
    }

    @Test fun `strategy catalog contains distinct Android strategy identifiers`() {
        assertEquals(AndroidStrategies.all.size, AndroidStrategies.all.map { it.id }.toSet().size)
        assertTrue(AndroidStrategies.all.all { it.nativeArguments.isNotEmpty() })
    }

    @Test fun `ByeDPIAndroid profile keeps the supplied TCP and UDP command`() {
        val strategy = AndroidStrategies.all.first()
        assertEquals("byedpi-android-tcp-udp", strategy.id)
        assertTrue("-Kt,h" in strategy.nativeArguments)
        assertTrue("-An" in strategy.nativeArguments)
        assertTrue("-Ku" in strategy.nativeArguments)
        assertTrue("-s443+s" in strategy.nativeArguments)
    }

    @Test fun `custom command cannot replace the local listener`() {
        assertEquals(null, AndroidStrategies.custom("--ip 0.0.0.0 --tlsrec 1+s"))
        assertEquals(null, AndroidStrategies.custom("-p 9999 --tlsrec 1+s"))
        assertEquals("custom-command", AndroidStrategies.custom("--tlsrec 1+s")?.id)
    }

}
