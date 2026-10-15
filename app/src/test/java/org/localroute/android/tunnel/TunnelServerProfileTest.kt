package org.localroute.android.tunnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TunnelServerProfileTest {
    @Test fun `saving a profile replaces its metadata without duplicates`() {
        val original = TunnelServerProfile("home", "Дом")
        val renamed = TunnelServerProfile("home", "Личный сервер")

        val result = TunnelServerProfiles.replace(listOf(original), renamed)

        assertEquals(listOf(renamed), result)
    }

    @Test fun `active profile falls back to the first local profile`() {
        val first = TunnelServerProfile("home", "Дом")
        val second = TunnelServerProfile("backup", "Резерв")

        assertSame(first, TunnelServerProfiles.active(listOf(first, second), "unknown"))
        assertSame(second, TunnelServerProfiles.active(listOf(first, second), "backup"))
    }
}
