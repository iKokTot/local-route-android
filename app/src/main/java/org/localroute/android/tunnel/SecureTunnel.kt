package org.localroute.android.tunnel

import org.localroute.android.core.RouteProfile

/** Contract only. No endpoint, credentials or server connection exists in this MVP. */
interface SecureTunnelController {
    suspend fun connect(profile: RouteProfile): TunnelState
    suspend fun disconnect()
}

sealed interface TunnelState { data object NotImplemented : TunnelState; data object Connected : TunnelState }
