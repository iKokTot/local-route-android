package org.localroute.android.localdpi

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import org.localroute.android.MainActivity
import org.localroute.android.core.AndroidStrategies
import org.localroute.android.data.ProfileStore

class LocalRouteTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val active = LocalDpiRuntime.state.value.phase == EnginePhase.ACTIVE
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = if (active) "Local Route включён" else "Local Route"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (LocalDpiRuntime.state.value.phase == EnginePhase.ACTIVE) {
            startService(Intent(this, LocalDpiVpnService::class.java).setAction(LocalDpiVpnService.ACTION_STOP))
            qsTile?.state = Tile.STATE_INACTIVE
            qsTile?.updateTile()
            return
        }
        if (VpnService.prepare(this) != null) {
            val launch = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_START_FROM_TILE, true)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 40, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            } else {
                @Suppress("DEPRECATION") startActivityAndCollapse(launch)
            }
            return
        }
        val store = ProfileStore(this)
        val strategy = AndroidStrategies.resolve(store.lastSuccessfulStrategy(), store.customCommand())
            ?: AndroidStrategies.all.first()
        ContextCompat.startForegroundService(this, Intent(this, LocalDpiVpnService::class.java)
            .setAction(LocalDpiVpnService.ACTION_START)
            .putExtra(LocalDpiVpnService.EXTRA_STRATEGY, strategy.id)
            .putExtra(LocalDpiVpnService.EXTRA_STRATEGY_TITLE, strategy.title)
            .putExtra(LocalDpiVpnService.EXTRA_STRATEGY_ARGUMENTS, strategy.nativeArguments.toTypedArray()))
        qsTile?.state = Tile.STATE_ACTIVE
        qsTile?.updateTile()
    }
}
