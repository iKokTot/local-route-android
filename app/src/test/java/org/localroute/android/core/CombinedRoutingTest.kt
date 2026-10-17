package org.localroute.android.core

import org.junit.Assert.*
import org.junit.Test

class CombinedRoutingTest {
    @Test fun allFourCombinationsHaveExplicitDefaults() {
        for (dpi in listOf(false, true)) for (server in listOf(false, true)) {
            val state = CombinedRouting(dpi, server)
            assertEquals(dpi || server, state.requiresVpn)
            assertEquals(if (server) TrafficDestination.SERVER else if (dpi) TrafficDestination.LOCAL_DPI else TrafficDestination.DIRECT, state.destinationForDomain("example.com"))
            assertEquals(if (dpi) TrafficDestination.LOCAL_DPI else state.defaultDestination, state.destinationForDomain("rr1.googlevideo.com"))
        }
    }
    @Test fun switchesNeverDisableTheOtherFeature() {
        val both = CombinedRouting(true, true)
        assertEquals(CombinedRouting(false, true), both.toggle(ConnectionFeature.DPI, false))
        assertEquals(CombinedRouting(true, false), both.toggle(ConnectionFeature.SERVER, false))
    }
    @Test fun domainMatchingIsLabelBoundedAndCaseInsensitive() {
        val state = CombinedRouting(true, true)
        assertEquals(TrafficDestination.LOCAL_DPI, state.destinationForDomain("WWW.YouTube.Com."))
        assertEquals(TrafficDestination.SERVER, state.destinationForDomain("notyoutube.com"))
        assertEquals(TrafficDestination.SERVER, state.destinationForDomain("youtube.com.attacker.test"))
    }
    @Test fun selectionAffectsOnlyChosenServicesIncludingNativePackages() {
        val state = CombinedRouting(true, true, setOf(ServiceTarget.DISCORD))
        assertEquals(TrafficDestination.SERVER, state.destinationForDomain("youtube.com"))
        assertEquals(TrafficDestination.LOCAL_DPI, state.destinationForPackage("com.discord"))
        assertEquals(TrafficDestination.SERVER, state.destinationForPackage("com.google.android.youtube"))
    }
    @Test fun localRouteHasNoAutomaticServerFallback() {
        val state = CombinedRouting(true, true)
        assertEquals(TrafficDestination.LOCAL_DPI, state.destinationForDomain("discord.media"))
        assertEquals(TrafficDestination.LOCAL_DPI, state.copy(serverEnabled = false).destinationForDomain("example.com"))
    }
}
