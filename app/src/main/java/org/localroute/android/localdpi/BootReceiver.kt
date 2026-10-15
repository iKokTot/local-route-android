package org.localroute.android.localdpi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import org.localroute.android.core.AndroidStrategies
import org.localroute.android.data.ProfileStore

/** Restarts only after the user enabled the option and Android still retains VPN consent. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = ProfileStore(context)
        if (!store.autostartRequested() || VpnService.prepare(context) != null) return
        val strategy = AndroidStrategies.resolve(store.lastSuccessfulStrategy(), store.customCommand())
            ?: AndroidStrategies.all.first()
        ContextCompat.startForegroundService(context, Intent(context, LocalDpiVpnService::class.java)
            .setAction(LocalDpiVpnService.ACTION_START)
            .putExtra(LocalDpiVpnService.EXTRA_STRATEGY, strategy.id)
            .putExtra(LocalDpiVpnService.EXTRA_STRATEGY_TITLE, strategy.title)
            .putExtra(LocalDpiVpnService.EXTRA_STRATEGY_ARGUMENTS, strategy.nativeArguments.toTypedArray())
            .putExtra(
                LocalDpiVpnService.EXTRA_PACKAGES,
                store.selectedServices().flatMap { it.vpnPackages }.distinct().toTypedArray()
            ))
    }
}
