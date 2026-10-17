package org.localroute.android.core

enum class ConnectionFeature { DPI, SERVER }
enum class TrafficDestination { DIRECT, LOCAL_DPI, SERVER }

/** Desired state, separate from observed engine health. Never enables fallback implicitly. */
data class CombinedRouting(
    val dpiEnabled: Boolean = false,
    val serverEnabled: Boolean = false,
    val services: Set<ServiceTarget> = ServiceTarget.entries.toSet(),
    val customDpiDomains: Set<String> = emptySet(),
    val serverRoutes: ServerRoutes = ServerRoutes()
) {
    val requiresVpn: Boolean get() = dpiEnabled || serverEnabled
    val defaultDestination: TrafficDestination
        get() = if (serverEnabled && serverRoutes.allTraffic) TrafficDestination.SERVER else TrafficDestination.DIRECT

    fun toggle(feature: ConnectionFeature, enabled: Boolean): CombinedRouting = when (feature) {
        ConnectionFeature.DPI -> copy(dpiEnabled = enabled)
        ConnectionFeature.SERVER -> copy(serverEnabled = enabled)
    }

    fun destinationForPackage(packageName: String): TrafficDestination =
        if (serverEnabled && packageName in serverRoutes.packages) TrafficDestination.SERVER else defaultDestination

    fun destinationForDomain(domain: String): TrafficDestination {
        val host = domain.trimEnd('.').lowercase(java.util.Locale.ROOT)
        return if (dpiEnabled && domains.any { host == it || host.endsWith(".$it") }) TrafficDestination.LOCAL_DPI
        else if (serverEnabled && serverRoutes.domains.any { host == it || host.endsWith(".$it") }) TrafficDestination.SERVER else defaultDestination
    }

    val domains: Set<String> get() = services.flatMap { service -> when (service) {
        ServiceTarget.YOUTUBE -> listOf("youtube.com", "youtu.be", "youtube-nocookie.com", "youtubekids.com", "googlevideo.com", "ytimg.com", "ggpht.com", "youtube.googleapis.com", "youtubei.googleapis.com", "youtubeembeddedplayer.googleapis.com")
        ServiceTarget.DISCORD -> listOf("discord.com", "gateway.discord.gg", "cdn.discordapp.com", "updates.discord.com", "discord.gg", "discord.media", "discordapp.com", "discordapp.net", "discordcdn.com", "discordstatus.com", "discord-activities.com", "discordactivities.com", "discord.gift", "discord.gifts", "discord.new", "discord.store", "discord.dev", "discord.co", "dis.gd")
    } }.toSet() + customDpiDomains
}
