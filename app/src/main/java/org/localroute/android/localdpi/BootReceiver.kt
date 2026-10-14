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
        val strategy = store.lastSuccessfulStrategy() ?: AndroidStrategies.all.first().id
        ContextCompat.startForegroundService(context, Intent(context, LocalDpiVpnService::class.java)
            .setAction(LocalDpiVpnService.ACTION_START)
            .putExtra(LocalDpiVpnService.EXTRA_STRATEGY, strategy)
            .putExtra(LocalDpiVpnService.EXTRA_PACKAGES, store.selectedServices().map { it.packageName }.toTypedArray()))
    }
}
