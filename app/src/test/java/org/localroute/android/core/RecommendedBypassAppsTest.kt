package org.localroute.android.core

import org.junit.Assert.*
import org.junit.Test

class RecommendedBypassAppsTest {
    @Test fun catalogHasVerifiedRealPackageIds() {
        assertEquals(22, RecommendedBypassApps.packages.size)
        assertTrue("ru.ozon.app.android" in RecommendedBypassApps.packages)
        assertTrue("ru.vtb24.mobilebanking.android" in RecommendedBypassApps.packages)
        assertFalse("ru.ozon.app" in RecommendedBypassApps.packages)
        assertTrue(RecommendedBypassApps.packages.all { it.matches(Regex("[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+")) })
    }
    @Test fun noExclusionsForDpiWithoutVless() {
        assertFalse(RecommendedBypassApps.applies(false))
        assertTrue(RecommendedBypassApps.applies(true))
    }
}
