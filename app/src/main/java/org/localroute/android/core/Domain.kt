package org.localroute.android.core

enum class TrafficMode { LOCAL_DPI, SECURE_TUNNEL }

enum class ServiceTarget(val title: String, val packageName: String) {
    YOUTUBE("YouTube", "com.google.android.youtube"),
    DISCORD("Discord", "com.discord");
}

data class RouteProfile(
    val mode: TrafficMode = TrafficMode.LOCAL_DPI,
    val services: Set<ServiceTarget> = setOf(ServiceTarget.YOUTUBE, ServiceTarget.DISCORD),
    val strategyId: String? = null
)

data class DpiStrategy(
    val id: String,
    val title: String,
    val description: String,
    val nativeArguments: List<String>
)

object AndroidStrategies {
    /** Profile copied from the user's working ByeDPIAndroid 1.2 command line. */
    const val BYEDPI_ANDROID_TCP_UDP_COMMAND =
        "-Kt,h -d1 -s0+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s " +
            "-d35+s -An -Ku -a5 -s443+s -d80+s -d443+s -s80+s -s443+s -d53+s -s53+s -d443+s"

    // Это кандидаты для проверки в своей сети, а не заранее подтверждённые стратегии.
    // Дополнительные варианты используют методы из README закреплённого ByeDPI.
    val all = listOf(
        DpiStrategy(
            "byedpi-android-tcp-udp",
            "ByeDPIAndroid · TCP + UDP",
            "Многошаговый Android-профиль из ByeDPIAndroid: отдельные цепочки для TLS/HTTP и UDP/QUIC.",
            commandArguments(BYEDPI_ANDROID_TCP_UDP_COMMAND)
        ),
        DpiStrategy("byedpi-tls-record", "TLS · разделение записи", "Проверьте видео и голос после запуска.", commandArguments("--tlsrec 1+s")),
        DpiStrategy("byedpi-tls-split", "TLS · разделение пакета", "Проверьте видео и голос после запуска.", commandArguments("--split 1 --tlsrec 1+s")),
        DpiStrategy("byedpi-tls-disorder", "TLS · обратный порядок", "Вариант из примера README ByeDPI; требует проверки в вашей сети.", commandArguments("--disorder 1 --auto=torst --tlsrec 1+s"))
    )

    fun ordered(lastSuccessfulId: String?): List<DpiStrategy> =
        all.sortedBy { if (it.id == lastSuccessfulId) 0 else 1 }

    fun candidates(lastSuccessfulId: String?, customCommand: String): List<DpiStrategy> =
        (listOfNotNull(custom(customCommand)) + ordered(lastSuccessfulId))
            // Одинаковые команды не должны повторяться под разными названиями.
            .distinctBy { it.nativeArguments }

    fun custom(command: String): DpiStrategy? {
        val args = commandArguments(command)
        if (args.isEmpty()) return null
        // LOCAL_DPI must remain a loopback-only proxy controlled by the app.
        // Do not allow a pasted profile to replace the listener or redirect to
        // another proxy/server.
        val forbidden = setOf("-i", "--ip", "-p", "--port", "-C", "--connect-to", "-P", "--protect-path", "-D", "--daemon", "-w", "--pidfile")
        if (args.any { arg -> forbidden.any { option -> arg == option || (option.length == 2 && arg.startsWith(option) && arg.length > 2) || arg.startsWith("$option=") } }) {
            return null
        }
        return DpiStrategy(
            id = "custom-command",
            title = "Своя строка ByeDPI",
            description = "Пользовательский Android-профиль; локальный адрес и порт контролирует Local Route.",
            nativeArguments = args
        )
    }

    fun resolve(id: String?, customCommand: String = ""): DpiStrategy? =
        if (id == "custom-command") custom(customCommand) else all.firstOrNull { it.id == id }

    private fun commandArguments(command: String): List<String> {
        val tokens = command.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val firstOption = tokens.indexOfFirst { it.startsWith("-") }
        return if (firstOption >= 0) tokens.drop(firstOption) else emptyList()
    }
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

class AutoTuner(private val probe: StrategyProbe) {
    /** Sequential by design: never opens an unbounded group of phone connections. */
    suspend fun select(lastSuccessfulId: String?, services: Set<ServiceTarget>): AutoTuneResult {
        val log = mutableListOf<String>()
        for (strategy in AndroidStrategies.ordered(lastSuccessfulId)) {
            log += "Быстрая проверка: ${strategy.title}"
            if (probe.quick(strategy, services) !is DiagnosticResult.Success) continue
            log += "Подробная проверка: ${strategy.title}"
            if (probe.detailed(strategy, services) is DiagnosticResult.Success) {
                log += "Найден кандидат для проверки в реальном приложении: ${strategy.title}"
                return AutoTuneResult(strategy, log)
            }
        }
        log += "Локальные стратегии не подтвердились: ограниченная сеть. Для такого случая потребуется будущий SECURE_TUNNEL."
        return AutoTuneResult(null, log)
    }

    /** A network probe is not proof that YouTube/Discord works end-to-end. */
    fun confirm(strategy: DpiStrategy, save: (String) -> Unit) {
        save(strategy.id)
    }
}
