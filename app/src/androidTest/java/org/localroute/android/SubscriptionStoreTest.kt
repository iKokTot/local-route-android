package org.localroute.android

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.localroute.android.tunnel.*

class SubscriptionStoreTest {
    @Test fun blankLegacySelectionDoesNotBlockImport() {
        val store=VlessStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val before=store.connectionGroups(); val selected=store.uri()
        try {
            store.save("")
            assertNull(store.uri())
            store.saveConnectionGroups(org.json.JSONArray(),preserveSelection=true)
            assertNull(store.uri())
        } finally {
            store.saveConnectionGroups(before)
            if(selected==null)store.clearSelection() else store.save(selected)
        }
    }
    @Test fun refreshRetainsSelectedServerSeparately() {
        val store = VlessStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val before = store.connectionGroups(); val selected = store.uri()
        val link = "vless://00000000-0000-4000-8000-000000000000@old.example:443?security=tls"
        try {
            store.save(link)
            store.saveConnectionGroups(org.json.JSONArray(), preserveSelection=true)
            assertEquals(link, VlessStore(InstrumentationRegistry.getInstrumentation().targetContext).uri())
            assertEquals("saved-selection", store.connectionGroups().getJSONObject(0).getString("id"))
        } finally {
            store.saveConnectionGroups(before)
            if (selected == null) store.clearSelection() else store.save(selected)
        }
    }
    @Test fun refreshedGroupCannotKeepRemovedServerSelected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = VlessStore(context)
        val oldGroups = store.connectionGroups()
        val oldUri = store.uri()
        val first = "vless://00000000-0000-4000-8000-000000000000@one.example:443?security=tls"
        val second = first.replace("one.example", "two.example")
        fun group(link: String) = org.json.JSONArray().put(org.json.JSONObject().put("id", "audit")
            .put("name", "Audit").put("source", "https://example.com/list")
            .put("links", org.json.JSONArray().put(link)))
        try {
            store.saveConnectionGroups(group(first)); store.save(first)
            store.saveConnectionGroups(group(second))
            assertNull(store.uri())
            assertEquals(second, store.connectionGroups().getJSONObject(0).getJSONArray("links").getString(0))
        } finally {
            store.saveConnectionGroups(oldGroups)
            if (oldUri != null) store.save(oldUri) else store.clearSelection()
        }
    }
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
