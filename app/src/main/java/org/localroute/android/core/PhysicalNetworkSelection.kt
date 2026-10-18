package org.localroute.android.core

/** Never let a secondary network's callback steal an existing default route. */
object PhysicalNetworkSelection {
    data class Candidate<T>(val value: T, val validated: Boolean)
    fun <T> choose(candidates: List<Candidate<T>>, default: T?, previous: T?): T? =
        candidates.firstOrNull { it.value == default }?.value
            ?: candidates.firstOrNull { it.value == previous && it.validated }?.value
            ?: candidates.firstOrNull { it.validated }?.value
            ?: candidates.firstOrNull { it.value == previous }?.value
            ?: candidates.firstOrNull()?.value
}
