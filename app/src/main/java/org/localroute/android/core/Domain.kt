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
        DpiStrategy(
            "byedpi-17-default",
            "ByeDPI 17 · Default",
            "Актуальный профиль OOB + UDP fake + TLS Record.",
            listOf("-o1", "-a1", "-r-5+se")
        ),
        DpiStrategy(
            "android-multipoint",
            "Android Multipoint",
            "Disorder и серия точек разбиения для современных блокировок.",
            listOf("--disorder", "1", "--split", "1+s", "--split", "3+s", "--split", "6+s", "--split", "9+s", "--split", "12+s", "--split", "15+s", "--split", "20+s", "--split", "30+s", "--udp-fake", "1")
        ),
        DpiStrategy(
            "youtube-discord-chain",
            "YouTube + Discord Chain",
            "Многоступенчатый профиль Split/OOB/Fake с автоматическим переключением.",
            listOf("-s1", "-q1", "-Y", "-Ar", "-s5", "-o1+s", "-At", "-f-1", "-r1+s", "-As", "-s1", "-o1+s", "-s-1", "-An", "-b+500")
        ),
        DpiStrategy(
            "adaptive-oob-fake",
            "Adaptive OOB + Fake",
            "Цепочка OOB/Fake для сетей с меняющейся фильтрацией.",
            listOf("-o1", "-a1", "-At,r,s", "-f-1", "-a1", "-Ar,s", "-o1", "-a1", "-At", "-r1+s", "-f-1", "-t6", "-a1")
        ),
        DpiStrategy(
            "oob-tls-fallback",
            "OOB + TLS Fallback",
            "Две OOB-точки и резервный TLS Record по тайм-ауту.",
            listOf("--oob", "1", "--oob", "25+s", "--timeout", "3", "--auto=torst", "--tlsrec", "1+s")
        ),
        DpiStrategy("android-disorder-1", "Android Disorder · 1", "Базовый Linux/Android-профиль ByeDPI.", listOf("--disorder", "1")),
        DpiStrategy("byedpi-balanced", "ByeDPI Balanced", "Disorder с резервным TLS Record после блокировки.", listOf("--disorder", "1", "--auto=torst", "--timeout", "3", "--tlsrec", "1+s")),
        DpiStrategy("tls-record-sni", "TLS Record / SNI", "Деление ClientHello внутри SNI.", listOf("--tlsrec", "3+s")),
        DpiStrategy("split-sni-1", "Split SNI · 1", "Мягкое разбиение TLS-запроса.", listOf("--split", "1+s")),
        DpiStrategy("split-sni-3", "Split SNI · 3", "Более поздняя граница разбиения SNI.", listOf("--split", "3+s")),
        DpiStrategy("disorder-sni", "Disorder SNI", "Перестановка возле имени сервера.", listOf("--disorder", "1+s")),
        DpiStrategy("split-disorder", "Split + Disorder", "Комбинация разбиения и перестановки.", listOf("--split", "1+s", "--disorder", "3+s")),
        DpiStrategy("auto-tls", "Auto TLS", "Включает TLS-изменение после признака блокировки.", listOf("--auto=torst", "--timeout", "3", "--tlsrec", "3+s")),
        DpiStrategy("oob-sni", "OOB SNI", "Внеполосный байт внутри SNI.", listOf("--oob", "3+s")),
        DpiStrategy("disoob-sni", "Disorder + OOB", "Перестановка с внеполосным байтом.", listOf("--disoob", "3+s")),
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
