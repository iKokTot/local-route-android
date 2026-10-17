package org.localroute.android.shortcuts

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import org.localroute.android.MainActivity
import org.localroute.android.R
import org.localroute.android.localdpi.EnginePhase
import org.localroute.android.localdpi.LocalDpiRuntime
import org.localroute.android.telegram.TelegramProxyPhase
import org.localroute.android.telegram.TelegramProxyRuntime
import org.localroute.android.tunnel.TunnelState
import org.localroute.android.tunnel.VlessRuntime
import org.localroute.android.tunnel.VlessStore

abstract class ModeTileService : TileService() {
    protected abstract val mode: ShortcutMode
    private var observing: Job? = null
    override fun onStartListening() {
        super.onStartListening()
        observing?.cancel()
        observing = CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            combine(LocalDpiRuntime.state, TelegramProxyRuntime.state, VlessRuntime.state) { _, _, _ -> Unit }.collect { update() }
        }
        update()
    }
    override fun onStopListening() { observing?.cancel(); observing = null; super.onStopListening() }
    override fun onDestroy() { observing?.cancel(); super.onDestroy() }
    private fun active() = when (mode) {
        ShortcutMode.DPI -> LocalDpiRuntime.state.value.phase in setOf(EnginePhase.ACTIVE, EnginePhase.STARTING)
        ShortcutMode.TG -> TelegramProxyRuntime.state.value.phase in setOf(TelegramProxyPhase.LISTENING, TelegramProxyPhase.STARTING)
        ShortcutMode.VLESS -> VlessRuntime.state.value.state in setOf(TunnelState.Connected, TunnelState.Connecting)
    }
    private fun update() {
        qsTile?.apply {
            label = mode.label
            icon = Icon.createWithResource(this@ModeTileService, mode.icon)
            state = if (active()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 29) subtitle = when {
                mode == ShortcutMode.TG && TelegramProxyRuntime.state.value.verified == false -> "Нет связи с TG"
                active() -> "Включён · нажмите остановить"
                else -> "Нажмите включить"
            }
            updateTile()
        }
    }
    override fun onClick() {
        super.onClick()
        unlockAndRun {
            if (active()) ModeShortcuts.stop(this, mode)
            else {
                val consent = mode != ShortcutMode.TG && VpnService.prepare(this) != null
                val notifications = Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                val missingServices = mode == ShortcutMode.DPI && org.localroute.android.data.ProfileStore(this).selectedServices().isEmpty()
                val configuration = (mode == ShortcutMode.VLESS && VlessStore(this).uri().isNullOrBlank()) || missingServices
                if (consent || notifications || configuration) {
                    val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra("open_tab", mode.tab)
                    if (!missingServices) intent.putExtra("start_mode", mode.name)
                    if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 40 + mode.ordinal, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                    else { @Suppress("DEPRECATION") startActivityAndCollapse(intent) }
                } else ModeShortcuts.start(this, mode)
            }
            update()
        }
    }
}
class TelegramTileService : ModeTileService() { override val mode = ShortcutMode.TG }
class VlessTileService : ModeTileService() { override val mode = ShortcutMode.VLESS }
