package org.localroute.android.core

enum class ConnectionFeature { DPI, SERVER }
enum class TrafficDestination { DIRECT, LOCAL_DPI, SERVER }

/** Desired state, separate from observed engine health. Never enables fallback implicitly. */
data class CombinedRouting(
    val dpiEnabled: Boolean = false,
    val serverEnabled: Boolean = false,
    val services: Set<ServiceTarget> = ServiceTarget.entries.toSet()
) {
    val requiresVpn: Boolean get() = dpiEnabled || serverEnabled
    val defaultDestination: TrafficDestination
        get() = if (serverEnabled) TrafficDestination.SERVER else if (dpiEnabled) TrafficDestination.LOCAL_DPI else TrafficDestination.DIRECT

    fun toggle(feature: ConnectionFeature, enabled: Boolean): CombinedRouting = when (feature) {
        ConnectionFeature.DPI -> copy(dpiEnabled = enabled)
        ConnectionFeature.SERVER -> copy(serverEnabled = enabled)
    }

    fun destinationForPackage(packageName: String): TrafficDestination =
        if (dpiEnabled && services.any { it.packageName == packageName }) TrafficDestination.LOCAL_DPI
        else defaultDestination

    fun destinationForDomain(domain: String): TrafficDestination {
        val host = domain.trimEnd('.').lowercase(java.util.Locale.ROOT)
        return if (dpiEnabled && domains.any { host == it || host.endsWith(".$it") }) TrafficDestination.LOCAL_DPI
        else defaultDestination
    }

    val domains: Set<String> get() = services.flatMap { service -> when (service) {
        ServiceTarget.YOUTUBE -> listOf("youtube.com", "youtu.be", "googlevideo.com", "ytimg.com", "youtubei.googleapis.com", "youtube-nocookie.com")
        ServiceTarget.DISCORD -> listOf("discord.com", "discord.gg", "discordapp.com", "discordapp.net", "discord.media")
    } }.toSet()
}
