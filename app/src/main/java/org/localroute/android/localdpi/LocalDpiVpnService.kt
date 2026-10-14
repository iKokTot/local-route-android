package org.localroute.android.localdpi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.localroute.android.core.AndroidStrategies

class LocalDpiVpnService : VpnService() {
    companion object { const val ACTION_START = "org.localroute.START"; const val ACTION_STOP = "org.localroute.STOP"; private const val CHANNEL = "local_route_vpn" }
    private lateinit var engine: NativeTunnelBridge

    override fun onCreate() { super.onCreate(); engine = AndroidNativeTunnelBridge(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopLocal(); return Service.START_NOT_STICKY }
        startForeground(7, notification())
        // Only selected app package names should be allowed here; domain filtering is
        // performed by the local proxy strategy, not by the VPN API.
        val builder = Builder().setSession("Local Route: локальный DPI").addAddress("10.7.0.2", 32).addRoute("0.0.0.0", 0)
        val packages = intent?.getStringArrayExtra("packages") ?: emptyArray()
        if (packages.isEmpty()) { stopLocal(); return Service.START_NOT_STICKY }
        var allowed = 0
        packages.forEach { packageName ->
            try { builder.addAllowedApplication(packageName); allowed++ }
            catch (_: android.content.pm.PackageManager.NameNotFoundException) { /* Not installed: keep other chosen apps working. */ }
        }
        if (allowed == 0) { stopLocal(); return Service.START_NOT_STICKY }
        val tun = builder.establish()
        if (tun == null) { stopLocal(); return Service.START_NOT_STICKY }
        val result = engine.start(tun, AndroidStrategies.all.first())
        if (result.isFailure) { tun.close(); stopLocal() }
        return Service.START_NOT_STICKY
    }

    private fun notification() : android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Локальный режим", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, LocalDpiVpnService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Local Route: локальный DPI")
            .setContentText("Трафик не направляется на внешний сервер; IP не скрывается.")
            .addAction(0, "Остановить", stop).build()
    }
    private fun stopLocal() { engine.stop(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)
}
