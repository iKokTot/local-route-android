package org.localroute.android.tunnel

import org.localroute.android.core.RouteProfile

/**
 * A server tunnel is deliberately separate from LOCAL_DPI. Its configuration
 * belongs to the user, stays on the device and is never silently derived from
 * a public endpoint or sent to Local Route infrastructure.
 */
interface SecureTunnelController {
    suspend fun connect(profile: RouteProfile): TunnelState
    suspend fun disconnect()
}

sealed interface TunnelState {
    data object Disconnected : TunnelState
    data object ConfigRequired : TunnelState
    data object Connecting : TunnelState
    data object Connected : TunnelState
    data class InvalidConfig(val message: String) : TunnelState
    data class Error(val message: String) : TunnelState
}
