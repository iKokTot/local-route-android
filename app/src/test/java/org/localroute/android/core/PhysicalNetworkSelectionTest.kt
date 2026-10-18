package org.localroute.android.core

import org.junit.Assert.*
import org.junit.Test

class PhysicalNetworkSelectionTest {
    private fun network(name: String, validated: Boolean = true) = PhysicalNetworkSelection.Candidate(name, validated)
    @Test fun secondaryNetworkDoesNotStealRoute() {
        assertEquals("wifi", PhysicalNetworkSelection.choose(listOf(network("cell"), network("wifi")), "vpn", "wifi"))
    }
    @Test fun physicalDefaultHasPriority() {
        assertEquals("cell", PhysicalNetworkSelection.choose(listOf(network("wifi"), network("cell")), "cell", "wifi"))
    }
    @Test fun lostNetworkFallsBackAndReconnects() {
        assertNull(PhysicalNetworkSelection.choose(emptyList<PhysicalNetworkSelection.Candidate<String>>(), "vpn", "wifi"))
        assertEquals("cell", PhysicalNetworkSelection.choose(listOf(network("cell")), "vpn", "wifi"))
    }
    @Test fun validatedNetworkReplacesUnvalidatedPrevious() {
        assertEquals("cell", PhysicalNetworkSelection.choose(listOf(network("wifi", false), network("cell")), "vpn", "wifi"))
    }
}
