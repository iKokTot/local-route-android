package org.localroute.android.tunnel

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.BufferedReader
import java.io.StringReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.localroute.android.core.RouteProfile

/**
 * Thin adapter over WireGuard's official Android backend. No protocol or
 * cryptography is reimplemented here; GoBackend owns Android's VpnService.
 */
class WireGuardSecureTunnelController(context: Context, private val store: SecureTunnelStore) : SecureTunnelController {
    private val backend = GoBackend(context.applicationContext)
    private var lastState: TunnelState = TunnelState.Disconnected
    private val tunnel = object : Tunnel {
        override fun getName(): String = "local-route-secure"

        override fun onStateChange(state: Tunnel.State) {
            lastState = if (state == Tunnel.State.UP) TunnelState.Connected else TunnelState.Disconnected
        }
    }

    override suspend fun connect(profile: RouteProfile): TunnelState = withContext(Dispatchers.IO) {
        val raw = store.configuration()?.trim().orEmpty()
        if (raw.isEmpty()) return@withContext TunnelState.ConfigRequired
        val config = try {
            Config.parse(BufferedReader(StringReader(raw)))
        } catch (error: Exception) {
            return@withContext TunnelState.InvalidConfig("Конфигурация WireGuard не распознана: ${error.message ?: "проверьте формат wg-quick"}")
        }
        try {
            lastState = TunnelState.Connecting
            backend.setState(tunnel, Tunnel.State.UP, config)
            lastState
        } catch (error: Exception) {
            TunnelState.Error("Не удалось включить защищённый туннель: ${error.message ?: "ошибка WireGuard"}")
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
        lastState = TunnelState.Disconnected
    }
}
