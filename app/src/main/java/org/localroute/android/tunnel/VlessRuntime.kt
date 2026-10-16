package org.localroute.android.tunnel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class VlessStatus(val state: TunnelState, val message: String)

object VlessRuntime {
    private val mutable = MutableStateFlow(VlessStatus(TunnelState.Disconnected, "VLESS не настроен"))
    val state: StateFlow<VlessStatus> = mutable
    fun update(state: TunnelState, message: String) { mutable.value = VlessStatus(state, message) }
}
