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
        DpiStrategy("tls-record-sni", "TLS Record / SNI", "Деление ClientHello внутри SNI.", listOf("--tlsrec", "3+s")),
        DpiStrategy("split-sni-1", "Split SNI · 1", "Мягкое разбиение TLS-запроса.", listOf("--split", "1+s")),
        DpiStrategy("split-sni-3", "Split SNI · 3", "Более поздняя граница разбиения SNI.", listOf("--split", "3+s")),
        DpiStrategy("disorder-sni", "Disorder SNI", "Изменённый порядок частей ClientHello.", listOf("--disorder", "1+s")),
        DpiStrategy("split-disorder", "Split + Disorder", "Комбинация разбиения и перестановки.", listOf("--split", "1+s", "--disorder", "3+s")),
        DpiStrategy("auto-tls", "Auto TLS", "Включает TLS-изменение после признака блокировки.", listOf("--auto=torst", "--timeout", "3", "--tlsrec", "3+s")),
        DpiStrategy("tls-record-7", "TLS Record · 7", "Альтернативная позиция TLS-record split.", listOf("--tlsrec", "7+s")),
        DpiStrategy("plain-socks", "Контрольный SOCKS", "Без модификации пакетов: отличает DPI-блокировку от общей сетевой ошибки.", listOf("--debug", "0"))
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
