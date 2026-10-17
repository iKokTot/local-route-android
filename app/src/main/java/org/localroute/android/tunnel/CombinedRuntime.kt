package org.localroute.android.tunnel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.localroute.android.core.CombinedRouting

data class CombinedStatus(val plan: CombinedRouting = CombinedRouting(), val pending: Int = 0, val completed: Long = 0) {
    val busy: Boolean get() = pending > 0
}
object CombinedRuntime {
    private val mutable = MutableStateFlow(CombinedStatus())
    val state = mutable.asStateFlow()
    fun queued() = mutable.update { it.copy(pending = it.pending + 1) }
    fun completed(plan: CombinedRouting) = mutable.update { it.copy(plan = plan, pending = (it.pending - 1).coerceAtLeast(0), completed = it.completed + 1) }
    fun stopped() = mutable.update { it.copy(plan = CombinedRouting()) }
}
