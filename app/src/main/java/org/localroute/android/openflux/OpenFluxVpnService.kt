package org.localroute.android.openflux

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import io.github.dovecoteescapee.byedpi.core.TProxyService
import kotlinx.coroutines.*
import org.json.JSONObject
import org.localroute.android.MainActivity
import org.localroute.android.R
import org.localroute.android.data.VpnExclusions
import org.localroute.android.localdpi.LifecycleVpnService
import java.io.File

object OpenFluxRuntime {
    @Volatile var phase = "stopped"
    @Volatile var message = "Нужно добавить подключение"
    @Synchronized fun update(value: String, text: String) {
        if(phase!=value) org.localroute.android.core.DiagnosticJournal.record("OpenFlux",text)
        message=text; phase=value
    }
    fun active() = phase in setOf("starting","connecting","connected","stopping")
}

/** Standalone experimental IPv4 VPN. Never competes with the combined DPI/VLESS TUN. */
class OpenFluxVpnService : LifecycleVpnService() {
    companion object { const val START="openflux.start"; const val STOP="openflux.stop" }
    private var worker: Job?=null
    private var process: Process?=null
    private var tun: ParcelFileDescriptor?=null
    private var config: File?=null
    private var forwarding=false
    private var stopping=false
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("openflux","OpenFlux · эксперимент",NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action==START) {
            val stop=PendingIntent.getService(this,92,Intent(this,javaClass).setAction(STOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val open=PendingIntent.getActivity(this,93,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
            val notification=NotificationCompat.Builder(this,"openflux").setSmallIcon(R.drawable.ic_local_route)
                .setContentTitle("Local Route · OpenFlux").setContentText("Экспериментальный туннель").setOngoing(true)
                .setContentIntent(open).addAction(0,"Остановить",stop).build()
            if(Build.VERSION.SDK_INT>=34) startForeground(92,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(92,notification)
            if(worker?.isActive!=true && !stopping) worker=lifecycleScope.launch { runClient() }
        } else finish()
        return START_NOT_STICKY
    }
    private suspend fun runClient() {
        OpenFluxRuntime.update("starting","Запускаем OpenFlux…")
        try {
            val link=OpenFluxStore(this).link() ?: error("Нужна ссылка от выходного узла")
            val p=withContext(Dispatchers.IO) {
                ProcessBuilder(File(applicationInfo.nativeLibraryDir,"libopenflux.so").absolutePath)
                    .redirectError(File("/dev/null")).apply {
                        val manager=getSystemService(android.net.ConnectivityManager::class.java)
                        val dns=manager.getLinkProperties(manager.activeNetwork)?.dnsServers?.firstOrNull { it is java.net.Inet4Address }?.hostAddress ?: "1.1.1.1"
                        environment()["OPENFLUX_DNS"]=dns
                    }.start().also {
                        process=it
                        it.outputStream.bufferedWriter().use { output -> output.write(link); output.newLine() }
                    }
            }
            val reader=p.inputStream.bufferedReader()
            while(currentCoroutineContext().isActive) {
                val line=withContext(Dispatchers.IO) { reader.readLine() } ?: error("OpenFlux завершил работу")
                when(runCatching { JSONObject(line).optString("event") }.getOrNull()) {
                    "listening" -> { startTun(); OpenFluxRuntime.update("connecting","Клиент запущен. Ожидаем выходной узел…") }
                    "connected" -> OpenFluxRuntime.update("connected","Связь с выходным узлом установлена")
                    "connecting" -> OpenFluxRuntime.update("connecting","Ожидаем связь с выходным узлом…")
                    "invalid","unsupported","failed" -> error("Не удалось подключить OpenFlux. Проверьте ссылку и выходной узел")
                }
            }
        } catch(c: CancellationException) { throw c }
        catch(error: Exception) {
            if(!stopping) {
                android.util.Log.e("LocalRouteOpenFlux", "Client failed: ${error.javaClass.simpleName}; at ${error.stackTrace.firstOrNull()}")
                OpenFluxRuntime.update("error","OpenFlux не подключён. Проверьте выходной узел и ссылку")
            }
        }
        finally {
            cleanup()
            if(stopping) OpenFluxRuntime.update("stopped","OpenFlux выключен")
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
    }
    private fun startTun() {
        check(tun==null)
        config=File.createTempFile("openflux-", ".yaml",cacheDir).apply { writeText("misc:\n  task-stack-size: 81920\nsocks5:\n  mtu: 1400\n  address: 127.0.0.1\n  port: 1082\n  udp: udp\n") }
        val builder=Builder().setSession("Local Route · OpenFlux").setMtu(1400)
            .addAddress("10.43.203.1",32).addRoute("0.0.0.0",0).addDnsServer("1.1.1.1")
            .addDisallowedApplication(packageName)
        VpnExclusions(this).packages().filter { it!=packageName }.forEach { runCatching { builder.addDisallowedApplication(it) } }
        if(Build.VERSION.SDK_INT>=29) builder.setMetered(false)
        tun=builder.establish() ?: error("Нет разрешения Android VPN")
        org.localroute.android.tunnel.NativeTunBridge.start(this,config!!.absolutePath,tun!!.fd)
        forwarding=true
    }
    private fun cleanup() {
        process?.destroy(); process?.destroyForcibly(); process=null
        if(forwarding) { org.localroute.android.tunnel.NativeTunBridge.stop(this); forwarding=false }
        runCatching { tun?.close() }; tun=null
        config?.delete(); config=null
    }
    private fun finish() {
        stopping=true
        OpenFluxRuntime.update("stopping","Останавливаем OpenFlux…")
        worker?.cancel(); cleanup()
        if(worker==null || worker?.isCompleted==true) {
            OpenFluxRuntime.update("stopped","OpenFlux выключен")
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
    }
    override fun onRevoke() { finish() }
    override fun onDestroy() {
        stopping=true; worker?.cancel(); cleanup()
        if(OpenFluxRuntime.active()) OpenFluxRuntime.update("stopped","OpenFlux выключен")
        super.onDestroy()
    }
}
