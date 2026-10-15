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
import org.localroute.android.core.DpiStrategy
import java.util.concurrent.Executors

class LocalDpiVpnService : VpnService() {
    companion object {
        const val ACTION_START = "org.localroute.START"
        const val ACTION_STOP = "org.localroute.STOP"
        const val EXTRA_STRATEGY = "strategy"
        const val EXTRA_STRATEGY_TITLE = "strategy_title"
        const val EXTRA_STRATEGY_ARGUMENTS = "strategy_arguments"
        const val EXTRA_PACKAGES = "packages"
        private const val CHANNEL = "local_route_vpn"
    }

    private lateinit var engine: NativeTunnelBridge
    private var tun: ParcelFileDescriptor? = null
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        engine = AndroidNativeTunnelBridge(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            worker.execute(::stopLocal)
            return START_NOT_STICKY
        }
        val strategyId = intent?.getStringExtra(EXTRA_STRATEGY)
        val suppliedArguments = intent?.getStringArrayExtra(EXTRA_STRATEGY_ARGUMENTS)
        val strategy = if (strategyId != null && suppliedArguments != null) {
            DpiStrategy(
                strategyId,
                intent.getStringExtra(EXTRA_STRATEGY_TITLE) ?: strategyId,
                "Пользовательский Android-профиль",
                suppliedArguments.toList()
            )
        } else {
            AndroidStrategies.all.firstOrNull { it.id == strategyId } ?: AndroidStrategies.all.first()
        }
        val packages = intent?.getStringArrayExtra(EXTRA_PACKAGES) ?: emptyArray()

        LocalDpiRuntime.update(EnginePhase.STARTING, "Запуск ${strategy.title}…", strategy.id)
        startForeground(7, notification(strategy.title))
        worker.execute { startLocal(strategy, packages) }
        return START_STICKY
    }

    /**
     * Establishing the native proxy and probing its local SOCKS socket must not
     * run in Android's main thread. Besides keeping the service responsive,
     * this avoids NetworkOnMainThreadException during the readiness handshake.
     */
    private fun startLocal(strategy: DpiStrategy, packages: Array<String>) {
        resetEngine()

        if (packages.isEmpty()) {
            fail("Не выбран ни один сервис")
            return
        }

        val builder = Builder()
            .setSession("Local Route · ${strategy.title}")
            .addAddress("10.10.10.10", 32)
            .addRoute("0.0.0.0", 0)

        // Match ByeDPIAndroid's default: do not force a public resolver.
        // Android keeps using the network-provided DNS when no VPN DNS is set.
        // This avoids breaking networks where 1.1.1.1 is blocked or intercepted.

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        var allowed = 0
        packages.forEach { packageName ->
            try {
                builder.addAllowedApplication(packageName)
                allowed++
            } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                // A selected app may simply not be installed. Other selected apps still work.
            }
        }
        if (allowed == 0) {
            fail("YouTube или Discord не установлены на устройстве")
            return
        }

        val descriptor = builder.establish()
        if (descriptor == null) {
            fail("Android не создал VPN-интерфейс")
            return
        }
        tun = descriptor
        val result = engine.start(descriptor, strategy)
        if (result.isSuccess) {
            LocalDpiRuntime.update(EnginePhase.ACTIVE, "Активна: ${strategy.title}", strategy.id)
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

    private fun fail(error: String) {
        LocalDpiRuntime.update(EnginePhase.ERROR, error)
        shutdown(reportStopped = false)
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
        worker.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)
}
