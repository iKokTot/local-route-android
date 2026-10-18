package org.localroute.android.tunnel

import org.junit.Assert.*
import org.junit.Test

class VlessRuntimeTest {
    @Test fun runningIsNotVerifiedAndReloadInvalidatesOldCheck() {
        VlessRuntime.update(TunnelState.Connected, "Движок запущен")
        assertNull(VlessRuntime.state.value.verified)
        VlessRuntime.checked(true)
        assertEquals(true, VlessRuntime.state.value.verified)
        assertNotNull(VlessRuntime.state.value.checkedAt)
        VlessRuntime.clearCheck()
        assertNull(VlessRuntime.state.value.verified)
        assertEquals(TunnelState.Connected, VlessRuntime.state.value.state)
        VlessRuntime.checked(false)
        VlessRuntime.update(TunnelState.Disconnected, "Остановлен")
        assertNull(VlessRuntime.state.value.verified)
    }
}
