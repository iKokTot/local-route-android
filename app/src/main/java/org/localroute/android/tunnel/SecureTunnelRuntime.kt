package org.localroute.android.tunnel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SecureTunnelStatus(val state: TunnelState, val message: String)

object SecureTunnelRuntime {
    private val mutable = MutableStateFlow(SecureTunnelStatus(TunnelState.Disconnected, "Конфигурация не подключена"))
    val state: StateFlow<SecureTunnelStatus> = mutable

    fun update(state: TunnelState, message: String) {
        mutable.value = SecureTunnelStatus(state, message)
    }
}
