package org.localroute.android.localdpi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EnginePhase { STOPPED, STARTING, ACTIVE, ERROR }

data class EngineState(
    val phase: EnginePhase = EnginePhase.STOPPED,
    val message: String = "Готово к запуску",
    val strategyId: String? = null,
    val log: List<String> = emptyList()
)

object LocalDpiRuntime {
    private val mutable = MutableStateFlow(EngineState())
    val state = mutable.asStateFlow()

    fun update(phase: EnginePhase, message: String, strategyId: String? = mutable.value.strategyId) {
        val line = "${System.currentTimeMillis()}: $message"
        mutable.value = EngineState(phase, message, strategyId, (mutable.value.log + line).takeLast(60))
    }
}
