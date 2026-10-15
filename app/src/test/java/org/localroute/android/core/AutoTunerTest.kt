package org.localroute.android.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTunerTest {
    @Test fun `last successful strategy is checked first`() {
        assertEquals("auto-tls", AndroidStrategies.ordered("auto-tls").first().id)
    }
    @Test fun `first detailed success remains an unsaved candidate`() = runBlocking {
        val calls = mutableListOf<String>(); var saved: String? = null
        val probe = object : StrategyProbe {
            override suspend fun quick(strategy: DpiStrategy, services: Set<ServiceTarget>) = DiagnosticResult.Success("ok").also { calls += "quick:${strategy.id}" }
            override suspend fun detailed(strategy: DpiStrategy, services: Set<ServiceTarget>) = if (strategy.id == "split-sni-1") DiagnosticResult.Success("ok") else DiagnosticResult.Failed("no")
        }
        val tuner = AutoTuner(probe)
        val result = tuner.select(null, setOf(ServiceTarget.YOUTUBE))
        assertEquals("split-sni-1", result.strategy?.id)
        assertEquals(null, saved)
        assertTrue(result.log.last().contains("кандидат"))
        assertTrue(calls.first().startsWith("quick:"))
        tuner.confirm(result.strategy!!) { saved = it }
        assertEquals("split-sni-1", saved)
    }
    @Test fun `failed candidates produce restricted network result`() = runBlocking {
        val probe = object : StrategyProbe {
            override suspend fun quick(strategy: DpiStrategy, services: Set<ServiceTarget>) = DiagnosticResult.Failed("blocked")
            override suspend fun detailed(strategy: DpiStrategy, services: Set<ServiceTarget>) = DiagnosticResult.Failed("unused")
        }
        val result = AutoTuner(probe).select(null, emptySet())
        assertEquals(null, result.strategy); assertTrue(result.log.last().contains("ограниченная сеть"))
    }
}
