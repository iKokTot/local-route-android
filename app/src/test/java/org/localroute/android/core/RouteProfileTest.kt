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
}
