package org.localroute.android.tunnel

import org.junit.Assert.*
import org.junit.Test
import org.localroute.android.core.CombinedRouting

class CombinedRuntimeTest {
    @Test fun busyRemainsUntilEveryQueuedCommandCompletes() {
        val before = CombinedRuntime.state.value.completed
        CombinedRuntime.queued()
        CombinedRuntime.queued()
        CombinedRuntime.completed(CombinedRouting(true, false))
        assertTrue(CombinedRuntime.state.value.busy)
        assertEquals(1, CombinedRuntime.state.value.pending)
        CombinedRuntime.completed(CombinedRouting(true, true))
        assertFalse(CombinedRuntime.state.value.busy)
        assertEquals(before + 2, CombinedRuntime.state.value.completed)
        assertEquals(CombinedRouting(true, true), CombinedRuntime.state.value.plan)
        CombinedRuntime.stopped()
    }
    @Test fun stoppedClearsRoutesButDoesNotLosePendingCommands() {
        CombinedRuntime.queued()
        CombinedRuntime.stopped()
        assertFalse(CombinedRuntime.state.value.plan.requiresVpn)
        assertTrue(CombinedRuntime.state.value.busy)
        CombinedRuntime.completed(CombinedRouting())
        assertFalse(CombinedRuntime.state.value.busy)
    }
}
