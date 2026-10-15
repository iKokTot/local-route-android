package org.localroute.android.localdpi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import io.github.dovecoteescapee.byedpi.core.ByeDpiProxy
import io.github.dovecoteescapee.byedpi.core.TProxyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.localroute.android.MainActivity
import org.localroute.android.R
import org.localroute.android.core.AndroidStrategies
import org.localroute.android.core.DpiStrategy
import java.io.File

/**
 * Lifecycle adapted from ByeDPIAndroid: one ByeDPI event-loop and one
 * tun2socks worker live for the whole VPN session.  The UI never restarts
 * either native worker while it is checking the selected profile.
 */
class LocalDpiVpnService : LifecycleVpnService() {
    companion object {
        const val ACTION_START = "org.localroute.START"
        const val ACTION_STOP = "org.localroute.STOP"
        const val EXTRA_STRATEGY = "strategy"
        const val EXTRA_STRATEGY_TITLE = "strategy_title"
        const val EXTRA_STRATEGY_ARGUMENTS = "strategy_arguments"
        private const val CHANNEL = "local_route_vpn"
        private const val NOTIFICATION_ID = 7
    }

    private val proxy = ByeDpiProxy()
    private val mutex = Mutex()
    private var proxyJob: Job? = null
    private var tun: ParcelFileDescriptor? = null
    private var config: File? = null
    private var stopping = false
    private var active = false

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Локальный режим", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = when (intent?.action) {
        ACTION_START -> {
            lifecycleScope.launch { start(strategyFrom(intent)) }
            START_STICKY
        }
        ACTION_STOP -> {
            lifecycleScope.launch { stop(reportStopped = true) }
            START_NOT_STICKY
        }
        else -> START_NOT_STICKY
    }

    override fun onRevoke() {
        lifecycleScope.launch { stop(reportStopped = true) }
    }

    private suspend fun start(strategy: DpiStrategy) {
        if (active) return
        LocalDpiRuntime.update(EnginePhase.STARTING, "Запуск ${strategy.title}…", strategy.id)
        try {
            mutex.withLock {
                startProxy(strategy)
                startTun2Socks()
            }
            active = true
            startForegroundCompat(notification(strategy.title))
            LocalDpiRuntime.update(EnginePhase.ACTIVE, "Активна: ${strategy.title}", strategy.id)
        } catch (error: Exception) {
            LocalDpiRuntime.update(EnginePhase.ERROR, error.message ?: "Не удалось запустить локальный движок")
            stop(reportStopped = false)
        }
    }

    private fun startProxy(strategy: DpiStrategy) {
        check(proxyJob == null) { "ByeDPI уже запущен" }
        proxyJob = lifecycleScope.launch(Dispatchers.IO) {
            val code = proxy.startProxy(strategy.nativeArguments.toTypedArray())
            withContext(Dispatchers.Main) {
                if (code != 0 && !stopping) {
                    LocalDpiRuntime.update(EnginePhase.ERROR, "ByeDPI завершился с кодом $code")
                    stop(reportStopped = false)
                }
            }
        }
    }

    private fun startTun2Socks() {
        config = File.createTempFile("config", "tmp", cacheDir).apply {
            writeText("""
                | misc:
                |   task-stack-size: 81920
                | socks5:
                |   mtu: 8500
                |   address: 127.0.0.1
                |   port: 1080
                |   udp: udp
            """.trimMargin("| "))
        }
        val fd = Builder()
            .setSession("Local Route · ByeDPI")
            .setConfigureIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .addAddress("10.10.10.10", 32)
            .addRoute("0.0.0.0", 0)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setMetered(false)
                addDisallowedApplication(applicationContext.packageName)
            }
            .establish() ?: error("Android не создал VPN-интерфейс")
        tun = fd
        TProxyService.TProxyStartService(config!!.absolutePath, fd.fd)
    }

    private suspend fun stop(reportStopped: Boolean) {
        mutex.withLock {
            stopping = true
            try {
                TProxyService.TProxyStopService()
                tun?.close()
                tun = null
                config?.delete()
                config = null
                proxy.stopProxy()
                proxyJob?.join()
                proxyJob = null
            } finally {
                stopping = false
                active = false
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (reportStopped) LocalDpiRuntime.update(EnginePhase.STOPPED, "Защита остановлена", null)
        stopSelf()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun notification(strategy: String): Notification {
        val stop = PendingIntent.getService(this, 1, Intent(this, LocalDpiVpnService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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

    private fun strategyFrom(intent: Intent): DpiStrategy {
        val id = intent.getStringExtra(EXTRA_STRATEGY)
        val args = intent.getStringArrayExtra(EXTRA_STRATEGY_ARGUMENTS)
        return if (id != null && args != null) {
            DpiStrategy(id, intent.getStringExtra(EXTRA_STRATEGY_TITLE) ?: id, "Проверенный Android-профиль", args.toList())
        } else AndroidStrategies.all.first()
    }
}
