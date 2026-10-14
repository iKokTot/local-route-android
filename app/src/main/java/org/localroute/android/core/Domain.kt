package org.localroute.android.core

enum class TrafficMode { LOCAL_DPI, SECURE_TUNNEL }

enum class ServiceTarget(val title: String, val packageName: String) {
    YOUTUBE("YouTube", "com.google.android.youtube"),
    DISCORD("Discord", "com.discord")
}

data class RouteProfile(
    val mode: TrafficMode = TrafficMode.LOCAL_DPI,
    val services: Set<ServiceTarget> = setOf(ServiceTarget.YOUTUBE, ServiceTarget.DISCORD),
    val strategyId: String? = null,
    val autostartRequested: Boolean = false
)

data class DpiStrategy(
    val id: String,
    val title: String,
    val description: String,
    val nativeArguments: List<String>
)

object AndroidStrategies {
    // Parameters are intentionally Android/local-SOCKS oriented, not WinDivert presets.
    val all = listOf(
        DpiStrategy("tls-record-sni", "TLS record + SNI", "Делит ClientHello возле SNI.", listOf("--tlsrec", "3+s")),
        DpiStrategy("split-sni", "Разбиение SNI", "Делит TLS-запрос рядом с именем сервера.", listOf("--split", "1+s")),
        DpiStrategy("auto-tls", "Осторожный авто-TLS", "Применяет изменение после короткого признака блокировки.", listOf("--auto=torst", "--timeout", "3", "--tlsrec", "3+s"))
    )

    fun ordered(lastSuccessfulId: String?): List<DpiStrategy> =
        all.sortedBy { if (it.id == lastSuccessfulId) 0 else 1 }
}

sealed interface DiagnosticResult {
    data class Success(val details: String) : DiagnosticResult
    data class Failed(val details: String) : DiagnosticResult
}

interface StrategyProbe {
    suspend fun quick(strategy: DpiStrategy, services: Set<ServiceTarget>): DiagnosticResult
    suspend fun detailed(strategy: DpiStrategy, services: Set<ServiceTarget>): DiagnosticResult
}

data class AutoTuneResult(val strategy: DpiStrategy?, val log: List<String>)

class AutoTuner(private val probe: StrategyProbe, private val save: (String) -> Unit) {
    /** Sequential by design: never opens an unbounded group of phone connections. */
    suspend fun select(lastSuccessfulId: String?, services: Set<ServiceTarget>): AutoTuneResult {
        val log = mutableListOf<String>()
        for (strategy in AndroidStrategies.ordered(lastSuccessfulId)) {
            log += "Быстрая проверка: ${strategy.title}"
            if (probe.quick(strategy, services) !is DiagnosticResult.Success) continue
            log += "Подробная проверка: ${strategy.title}"
            if (probe.detailed(strategy, services) is DiagnosticResult.Success) {
                save(strategy.id)
                log += "Подтверждено и сохранено: ${strategy.title}"
                return AutoTuneResult(strategy, log)
            }
        }
        log += "Локальные стратегии не подтвердились: ограниченная сеть. Для такого случая потребуется будущий SECURE_TUNNEL."
        return AutoTuneResult(null, log)
    }
}
