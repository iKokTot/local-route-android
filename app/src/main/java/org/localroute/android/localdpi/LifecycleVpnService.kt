package org.localroute.android.localdpi

import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher

/** Minimal GPL-compatible adaptation of ByeDPIAndroid's LifecycleVpnService. */
open class LifecycleVpnService : VpnService(), LifecycleOwner {
    @Suppress("LeakingThis") private val dispatcher = ServiceLifecycleDispatcher(this)
    override fun onCreate() { dispatcher.onServicePreSuperOnCreate(); super.onCreate() }
    override fun onBind(intent: Intent): IBinder? { dispatcher.onServicePreSuperOnBind(); return super.onBind(intent) }
    @Deprecated("Deprecated in Java")
    override fun onStart(intent: Intent?, startId: Int) { dispatcher.onServicePreSuperOnStart(); @Suppress("DEPRECATION") super.onStart(intent, startId) }
    override fun onDestroy() { dispatcher.onServicePreSuperOnDestroy(); super.onDestroy() }
    override val lifecycle: Lifecycle get() = dispatcher.lifecycle
}
