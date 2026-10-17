package org.localroute.android.tunnel

/** Lifecycle exposed by a local, user-configured tunnel implementation. */
sealed interface TunnelState {
    data object Disconnected : TunnelState
    data object ConfigRequired : TunnelState
    data object Connecting : TunnelState
    data object Connected : TunnelState
    data class InvalidConfig(val message: String) : TunnelState
    data class Error(val message: String) : TunnelState
}
