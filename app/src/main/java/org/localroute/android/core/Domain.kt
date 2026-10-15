package org.localroute.android.core

enum class TrafficMode { LOCAL_DPI, SECURE_TUNNEL }

enum class ServiceTarget(
    val title: String,
    val packageName: String,
    val supportingPackages: Set<String> = emptySet()
) {
    // The Android YouTube client delegates parts of discovery, account and
    // Cronet setup to Google Play services. Routing only the visible YouTube
    // package can leave the real app spinning even though a direct HTTPS
    // probe succeeds.
    YOUTUBE(
        "YouTube",
        "com.google.android.youtube",
        setOf("com.google.android.gms", "com.google.android.gsf")
    ),
    DISCORD("Discord", "com.discord");

    val vpnPackages: Set<String> get() = setOf(packageName) + supportingPackages
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
    /** Profile copied from the user's working ByeDPIAndroid 1.2 command line. */
    const val BYEDPI_ANDROID_TCP_UDP_COMMAND =
        "-Kt,h -d1 -s0+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s " +
            "-d35+s -An -Ku -a5 -s443+s -d80+s -d443+s -s80+s -s443+s -d53+s -s53+s -d443+s"

    // Parameters are intentionally Android/local-SOCKS oriented, not WinDivert presets.
    val all = listOf(
        DpiStrategy(
            "byedpi-android-tcp-udp",
            "ByeDPIAndroid · TCP + UDP",
            "Многошаговый Android-профиль из ByeDPIAndroid: отдельные цепочки для TLS/HTTP и UDP/QUIC.",
            commandArguments(BYEDPI_ANDROID_TCP_UDP_COMMAND)
        ),
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
