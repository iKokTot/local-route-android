package org.localroute.android

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.localroute.android.tunnel.*

class SubscriptionStoreTest {
    @Test fun encryptedListSurvivesRecreationAndFailureKeepsSelectedProfile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = VlessStore(context)
        val old = store.profiles(); val source = store.source(); val selected = store.uri().orEmpty()
        val link = "vless://00000000-0000-4000-8000-000000000000@example.com:443?security=tls#Test"
        try {
            val profiles = VlessSubscriptionParser.parse(link)
            store.importProfiles(profiles, "https://example.com/list")
            store.save(link)
            assertEquals(link, VlessStore(context).profiles().single().link)
            assertEquals(link, VlessStore(context).uri())
            assertTrue(runCatching { store.importProfiles(emptyList()) }.isFailure)
            assertEquals(link, VlessStore(context).uri())
            assertEquals("https://example.com/list", VlessStore(context).source())
        } finally {
            if (old.isNotEmpty()) store.importProfiles(old, source)
            store.save(selected)
        }
    }
}
