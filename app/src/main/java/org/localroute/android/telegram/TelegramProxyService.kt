package org.localroute.android.telegram

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.valnesfjord.tgwsproxyrs.NativeProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.localroute.android.MainActivity
import org.localroute.android.R

class TelegramProxyService : LifecycleService() {
    companion object {
        const val START = "org.localroute.TGWS_START"
        const val STOP = "org.localroute.TGWS_STOP"
        const val CHECK = "org.localroute.TGWS_CHECK"
        const val RECONNECT = "org.localroute.TGWS_RECONNECT"
        private const val CHANNEL = "local_route_telegram"
    }
    private var ownsProxy = false
    private var generation = 0
    private var reconnectJob: Job? = null
    private var lastRoute: String? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Telegram-прокси", NotificationManager.IMPORTANCE_LOW))
        lifecycleScope.launch {
            TelegramProxyRuntime.state.collect { state ->
                if (ownsProxy && state.phase in setOf(TelegramProxyPhase.ERROR, TelegramProxyPhase.STOPPED)) stopSelf()
                if (ownsProxy && state.phase == TelegramProxyPhase.LISTENING && state.verified == null && !state.checking) checkConnection()
            }
        }
        lifecycleScope.launch {
            combine(org.localroute.android.localdpi.LocalDpiRuntime.state, org.localroute.android.tunnel.VlessRuntime.state) { _, _ -> TelegramProxySettings.outboundProxy() }
                .distinctUntilChanged().collect { route -> if (ownsProxy && route != lastRoute) reconnect() }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            STOP -> stopSelf()
            CHECK -> checkConnection()
            RECONNECT -> reconnect()
            START -> if (!ownsProxy) {
                launchProxy()
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun reconnect() {
        if (!ownsProxy || reconnectJob?.isActive == true) return
        generation++
        ownsProxy = false
        runCatching { NativeProxy.nativeStop() }
        reconnectJob = lifecycleScope.launch { delay(500); launchProxy() }
    }
    private fun launchProxy() {
        generation++
        TelegramProxyRuntime.starting()
        showNotification()
        ownsProxy = true
        lastRoute = TelegramProxySettings.outboundProxy()
        runCatching { NativeProxy.nativeStart(TelegramProxySettings(this).arguments()) }.fold(
            { error -> if (error != null) { TelegramProxyRuntime.failed(error); stopSelf() } },
            { error -> TelegramProxyRuntime.failed(error.message ?: "Не удалось загрузить ядро Telegram"); stopSelf() })
    }
    private fun checkConnection() {
        if (!ownsProxy || TelegramProxyRuntime.state.value.checking) return
        val currentGeneration = generation
        TelegramProxyRuntime.checking()
        lifecycleScope.launch(Dispatchers.IO) {
            val secret = TelegramProxySettings(this@TelegramProxyService).secret()
            var success = false
            for (dc in listOf(2, 4)) {
                ensureActive()
                if (currentGeneration != generation) return@launch
                val result = runCatching { TelegramMtprotoProbe.check(secret, port = TelegramProxySettings(this@TelegramProxyService).port(), dc = dc, timeout = 22000) }
                TelegramProxyRuntime.log("Проверка DC$dc ${TelegramProxySettings.routeLabel()}: " +
                    result.fold({ "ответ MTProto получен" }, { "${it.javaClass.simpleName}: ${it.message ?: "нет ответа"}" }))
                if (result.isSuccess) {
                    success = true
                    break
                }
            }
            if (currentGeneration == generation && TelegramProxyRuntime.state.value.phase == TelegramProxyPhase.LISTENING) {
                val route = TelegramProxySettings.routeLabel()
                TelegramProxyRuntime.checked(if (success) "Тестовый узел Telegram ответил $route. Теперь подключите прокси в Telegram и проверьте сообщения: тест не проверяет ваш аккаунт и звонки."
                    else "Telegram не ответил $route. Прямые адреса могут быть недоступны. Включите VLESS и повторите проверку; подробности — в журнале.", success)
            }
        }
    }
    private fun showNotification() {
        val open = PendingIntent.getActivity(this, 20, Intent(this, MainActivity::class.java).putExtra("open_tab", 1), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 21, Intent(this, TelegramProxyService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_local_route)
            .setContentTitle("Local Route · TG Proxy").setContentText("Локальный Telegram-прокси включён")
            .setContentIntent(open).setOngoing(true).addAction(0, "Остановить", stop).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(18, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(18, notification)
    }
    override fun onDestroy() {
        generation++
        if (ownsProxy) runCatching { NativeProxy.nativeStop() }
        ownsProxy = false
        TelegramProxyRuntime.stopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
