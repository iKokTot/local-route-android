package org.localroute.android.tunnel

/**
 * Public metadata for a user-owned tunnel endpoint. The actual private
 * configuration is deliberately kept separately in [SecureTunnelStore].
 *
 * Keeping this model protocol-neutral lets the UI manage several personal
 * servers later without treating a server address as application telemetry.
 */
data class TunnelServerProfile(
    val id: String,
    val name: String,
    val protocol: TunnelProtocol = TunnelProtocol.WIREGUARD
)

enum class TunnelProtocol { WIREGUARD }

object TunnelServerProfiles {
    const val DEFAULT_ID = "personal-server"

    fun default() = TunnelServerProfile(DEFAULT_ID, "Мой сервер")

    fun replace(profiles: List<TunnelServerProfile>, profile: TunnelServerProfile): List<TunnelServerProfile> {
        val withoutCurrent = profiles.filterNot { it.id == profile.id }
        return withoutCurrent + profile
    }

    fun active(profiles: List<TunnelServerProfile>, activeId: String?): TunnelServerProfile? =
        profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
}
