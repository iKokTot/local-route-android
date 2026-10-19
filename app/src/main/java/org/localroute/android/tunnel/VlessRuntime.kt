package org.localroute.android.tunnel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class VlessStatus(val state: TunnelState, val message: String, val verified: Boolean? = null, val checkedAt: Long? = null)

object VlessRuntime {
    private val mutable = MutableStateFlow(VlessStatus(TunnelState.Disconnected, "VLESS не настроен"))
    val state: StateFlow<VlessStatus> = mutable
    fun update(state: TunnelState, message: String) {
        org.localroute.android.core.DiagnosticJournal.record("VPN/${state.javaClass.simpleName}",message)
        mutable.value = VlessStatus(state, message)
    }
    fun checked(success: Boolean) {
        org.localroute.android.core.DiagnosticJournal.record("CHECK/VPN",if(success)"Получен HTTPS-ответ" else "Проверочные HTTPS-адреса не ответили")
        mutable.value = mutable.value.copy(verified = success, checkedAt = System.currentTimeMillis())
    }
    fun clearCheck() { mutable.value = mutable.value.copy(verified = null, checkedAt = null) }
}
