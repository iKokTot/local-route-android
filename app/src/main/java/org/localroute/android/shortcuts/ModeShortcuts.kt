package org.localroute.android.shortcuts

import org.localroute.android.tunnel.CombinedVpnService
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import org.localroute.android.core.AndroidStrategies
import org.localroute.android.data.ProfileStore
import org.localroute.android.localdpi.LocalDpiVpnService
import org.localroute.android.telegram.TelegramProxyService
import org.localroute.android.tunnel.VlessVpnService

enum class ShortcutMode(val label: String, val tab: Int, val icon: Int) {
    DPI("DPI", 0, org.localroute.android.R.drawable.ic_tile_dpi),
    TG("TG Proxy", 1, org.localroute.android.R.drawable.ic_tile_tg),
    VLESS("VLESS", 2, org.localroute.android.R.drawable.ic_tile_vless)
}

object ModeShortcuts {
    fun start(context: Context, mode: ShortcutMode) {
        when (mode) {
            ShortcutMode.DPI -> {
                val store = ProfileStore(context)
                val strategy = AndroidStrategies.resolve(store.lastSuccessfulStrategy(), store.customCommand()) ?: AndroidStrategies.all.first()
                ContextCompat.startForegroundService(context, Intent(context, CombinedVpnService::class.java)
                    .setAction(CombinedVpnService.START_DPI)
                    .putExtra(LocalDpiVpnService.EXTRA_STRATEGY, strategy.id)
                    .putExtra(LocalDpiVpnService.EXTRA_STRATEGY_TITLE, strategy.title)
                    .putExtra(LocalDpiVpnService.EXTRA_STRATEGY_ARGUMENTS, strategy.nativeArguments.toTypedArray()))
            }
            ShortcutMode.TG -> ContextCompat.startForegroundService(context, Intent(context, TelegramProxyService::class.java).setAction(TelegramProxyService.START))
            ShortcutMode.VLESS -> {
                ContextCompat.startForegroundService(context, Intent(context, CombinedVpnService::class.java).setAction(CombinedVpnService.START_SERVER))
            }
        }
    }
    fun stop(context: Context, mode: ShortcutMode) {
        val running = when (mode) {
            ShortcutMode.DPI -> org.localroute.android.localdpi.LocalDpiRuntime.state.value.phase in setOf(org.localroute.android.localdpi.EnginePhase.ACTIVE, org.localroute.android.localdpi.EnginePhase.STARTING)
            ShortcutMode.TG -> org.localroute.android.telegram.TelegramProxyRuntime.state.value.phase in setOf(org.localroute.android.telegram.TelegramProxyPhase.LISTENING, org.localroute.android.telegram.TelegramProxyPhase.STARTING)
            ShortcutMode.VLESS -> org.localroute.android.tunnel.VlessRuntime.state.value.state in setOf(org.localroute.android.tunnel.TunnelState.Connected, org.localroute.android.tunnel.TunnelState.Connecting)
        }
        if (!running) return
        val (service, action) = when (mode) {
            ShortcutMode.DPI -> CombinedVpnService::class.java to CombinedVpnService.STOP_DPI
            ShortcutMode.TG -> TelegramProxyService::class.java to TelegramProxyService.STOP
            ShortcutMode.VLESS -> CombinedVpnService::class.java to CombinedVpnService.STOP_SERVER
        }
        context.startService(Intent(context, service).setAction(action))
    }
}
