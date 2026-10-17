package org.localroute.android.core

/** Последовательная проверка пользователем: отказ не повторяет уже испытанный профиль. */
data class DpiTrialPlan(val tried: Set<String> = emptySet(), val candidate: String? = null) {
    fun next(orderedIds: List<String>): DpiTrialPlan {
        val next = orderedIds.firstOrNull { it !in tried }
        return DpiTrialPlan(if(next == null) tried else tried + next, next)
    }
    fun confirmed(): DpiTrialPlan = copy(candidate = null)
}
