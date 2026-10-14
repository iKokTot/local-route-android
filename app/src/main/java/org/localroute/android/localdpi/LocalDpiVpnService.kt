package org.localroute.android.localdpi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import org.localroute.android.MainActivity
import org.localroute.android.R
import org.localroute.android.core.AndroidStrategies

class LocalDpiVpnService : VpnService() {
    companion object {
        const val ACTION_START = "org.localroute.START"
        const val ACTION_STOP = "org.localroute.STOP"
        const val EXTRA_STRATEGY = "strategy"
        const val EXTRA_PACKAGES = "packages"
        private const val CHANNEL = "local_route_vpn"
    }

    private lateinit var engine: NativeTunnelBridge
    private var tun: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        engine = AndroidNativeTunnelBridge(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopLocal()
            return START_NOT_STICKY
        }
        val strategy = AndroidStrategies.all.firstOrNull {
            it.id == intent?.getStringExtra(EXTRA_STRATEGY)
        } ?: AndroidStrategies.all.first()

        LocalDpiRuntime.update(EnginePhase.STARTING, "Запуск ${strategy.title}…", strategy.id)
        startForeground(7, notification(strategy.title))
        resetEngine()

        val packages = intent?.getStringArrayExtra(EXTRA_PACKAGES) ?: emptyArray()
        if (packages.isEmpty()) return fail("Не выбран ни один сервис")

        val builder = Builder()
            .setSession("Local Route · ${strategy.title}")
            .setMtu(1500)
            .addAddress("10.7.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")

        var allowed = 0
        packages.forEach { packageName ->
            try {
                builder.addAllowedApplication(packageName)
                allowed++
            } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                // A selected app may simply not be installed. Other selected apps still work.
            }
        }
        if (allowed == 0) return fail("YouTube или Discord не установлены на устройстве")

        val descriptor = builder.establish() ?: return fail("Android не создал VPN-интерфейс")
        tun = descriptor
        val result = engine.start(descriptor, strategy)
        return if (result.isSuccess) {
            LocalDpiRuntime.update(EnginePhase.ACTIVE, "Активна: ${strategy.title}", strategy.id)
            START_STICKY
        } else {
            fail(result.exceptionOrNull()?.message ?: "Не удалось запустить локальный движок")
        }
    }

    private fun notification(strategy: String): android.app.Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Локальный режим", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LocalDpiVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_local_route)
            .setContentTitle("Local Route включён")
            .setContentText(strategy)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Остановить", stop)
            .build()
    }

    private fun fail(error: String): Int {
        LocalDpiRuntime.update(EnginePhase.ERROR, error)
        shutdown(reportStopped = false)
        return START_NOT_STICKY
    }

    private fun stopLocal() = shutdown(reportStopped = true)

    private fun resetEngine() {
        engine.stop()
        runCatching { tun?.close() }
        tun = null
    }

    private fun shutdown(reportStopped: Boolean) {
        resetEngine()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (reportStopped) LocalDpiRuntime.update(EnginePhase.STOPPED, "Защита остановлена", null)
        stopSelf()
    }

    override fun onDestroy() {
        resetEngine()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)
}
