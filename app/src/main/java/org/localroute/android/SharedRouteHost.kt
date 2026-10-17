package org.localroute.android

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.localroute.android.core.*
import org.localroute.android.data.ProfileStore
import org.localroute.android.localdpi.*
import org.localroute.android.shortcuts.ShortcutMode
import org.localroute.android.telegram.*
import org.localroute.android.tunnel.*
import java.security.MessageDigest

/** Общая страница десктопа; этот класс отвечает только за возможности Android. */
@SuppressLint("SetJavaScriptEnabled")
class SharedRouteHost(
    private val activity: ComponentActivity,
    private val startDpi: (Set<ServiceTarget>, DpiStrategy) -> Unit,
    private val stopDpi: () -> Unit,
    private val startVpn: () -> Unit,
    private val stopVpn: () -> Unit,
    private val startTelegram: () -> Unit,
    private val stopTelegram: () -> Unit,
    private val addTile: (ShortcutMode) -> Unit
) {
    companion object { const val ORIGIN = "https://localroute.invalid/" }
    private val profile = ProfileStore(activity)
    private val servers = VlessStore(activity)
    private val telegram = TelegramProxySettings(activity)
    private var operation: Job? = null
    private var operationName: String? = null
    private var pendingMode: Int? = null
    val webView = WebView(activity)
    private val dpiCatalog: JSONObject by lazy { JSONObject(activity.assets.open("ui/catalog.json").bufferedReader().use { it.readText() }) }
    private fun id(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
    private fun dpiActive() = LocalDpiRuntime.state.value.phase in setOf(EnginePhase.ACTIVE, EnginePhase.STARTING)
    private fun vpnActive() = VlessRuntime.state.value.state in setOf(TunnelState.Connected, TunnelState.Connecting)
    private fun tgActive() = TelegramProxyRuntime.state.value.phase in setOf(TelegramProxyPhase.LISTENING, TelegramProxyPhase.STARTING)
    private fun array(values: Iterable<String>) = JSONArray(values.toList())
    private fun strings(value: JSONArray?): List<String> = value?.let { (0 until it.length()).map(it::getString) }.orEmpty()
    private fun response(message: String = "") = JSONObject().put("ok", true).put("message", message)

    init {
        webView.setBackgroundColor(android.graphics.Color.rgb(16, 20, 24))
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) { pendingMode?.let { showMode(it) } }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                val url = request.url
                val path = url.path.orEmpty().removePrefix("/")
                if (url.scheme != "https" || url.host != "localroute.invalid" || path.contains("..")) return blocked()
                val mime = when (path.substringAfterLast('.')) { "html" -> "text/html"; "css" -> "text/css"; "js" -> "application/javascript"; "svg" -> "image/svg+xml"; "json" -> "application/json"; else -> return blocked() }
                return runCatching { WebResourceResponse(mime, "utf-8", activity.assets.open("ui/$path")) }.getOrElse { blocked() }
            }
        }
        webView.addJavascriptInterface(this, "LocalRouteNative")
        webView.loadUrl(ORIGIN + "index.html")
    }
    private fun blocked() = WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), java.io.ByteArrayInputStream(byteArrayOf()))
    fun back() { webView.evaluateJavascript("Boolean(window.localRouteBack && window.localRouteBack())") { handled -> if (handled != "true") activity.moveTaskToBack(true) } }
    fun destroy() { operation?.cancel(); webView.removeJavascriptInterface("LocalRouteNative"); webView.destroy() }
    fun showMode(mode: Int) {
        pendingMode = mode.coerceIn(0, 2)
        webView.evaluateJavascript("if(typeof navigate==='function')navigate(${JSONObject.quote(listOf("lab", "telegram", "routes")[pendingMode!!])})", null)
    }

    @JavascriptInterface fun postMessage(raw: String) {
        if (raw.length > 540_000) return
        val request = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val requestId = request.optInt("id", -1)
        val method = request.optString("method")
        activity.lifecycleScope.launch {
            if (webView.url?.startsWith(ORIGIN) != true) return@launch
            val readonly = method == "readState"
            val stopping = method in setOf("stopAll", "stopEngine", "stopTelegram")
            if (!readonly && !stopping && operation?.isActive == true) { reply(requestId, JSONObject().put("ok",false).put("message","Дождитесь завершения операции или остановите её.")); return@launch }
            if (stopping) { operation?.cancelAndJoin(); operation = null; operationName = null }
            val current = coroutineContext[Job]
            if (!readonly) { operation = current; operationName = method }
            try {
                val result = execute(method, request.opt("payload"))
                if (!readonly) { operation = null; operationName = null }
                val output = if (method in setOf("readState", "saveProfile", "stopEngine", "stopAll")) snapshot() else result.put("state", snapshot())
                reply(requestId, output)
            } catch (cancelled: CancellationException) {
                reply(requestId, JSONObject().put("ok",false).put("message","Операция остановлена."))
                throw cancelled
            } catch (_: Exception) {
                reply(requestId, JSONObject().put("ok",false).put("message","Действие не выполнено. Проверьте настройки и подключение. Рабочие данные сохранены."))
            } finally { if (operation === current) { operation = null; operationName = null } }
        }
    }
    private fun reply(requestId: Int, value: JSONObject) {
        if (!activity.isFinishing) webView.evaluateJavascript("window.localRouteReply($requestId,$value)",null)
    }

    private fun library(): JSONArray = servers.connectionGroups()
    private fun links(group: JSONObject): List<String> = strings(group.optJSONArray("links"))
    private fun profiles(): List<VlessProfile> = (0 until library().length()).flatMap { links(library().getJSONObject(it)) }.distinct().map { VlessProfile(it,VlessUriParser.parse(it)) }
    private fun selectedServices(): JSONArray = array(profile.selectedServices().map { if(it == ServiceTarget.YOUTUBE) "youtube" else "discord" })
    private fun dpiDomains(): Set<String> = profile.selectedServices().flatMap { target ->
        strings(dpiCatalog.getJSONObject("dpi").getJSONObject(if(target == ServiceTarget.YOUTUBE) "youtube" else "discord").getJSONArray("domains"))
    }.toSet() + profile.customDomains()
    private fun routeServices(): Pair<List<String>, Set<String>> {
        val domains = servers.routes().domains
        val catalog = dpiCatalog.getJSONObject("vpn")
        val selected = catalog.keys().asSequence().filter { key -> domains.containsAll(strings(catalog.getJSONObject(key).getJSONArray("domains"))) }.toList()
        val builtins = selected.flatMap { strings(catalog.getJSONObject(it).getJSONArray("domains")) }.toSet()
        return selected to (domains - builtins)
    }
    private fun snapshot(): JSONObject {
        val candidates=candidateStrategies()
        val trial=profile.dpiTrial()
        val routes = servers.routes()
        val (services,custom) = routeServices()
        val catalog = dpiCatalog.getJSONObject("vpn")
        val vpnLabels = JSONObject().also { labels -> catalog.keys().forEach { labels.put(it,catalog.getJSONObject(it).getString("label")) } }
        val current = servers.uri()
        val serverList = JSONArray()
        val groups = JSONArray()
        val dedup = mutableSetOf<String>()
        val stored = library()
        for (i in 0 until stored.length()) {
            val group = stored.getJSONObject(i)
            val groupIds = mutableListOf<String>()
            for(link in links(group)) {
                val parsed = VlessProfile(link,VlessUriParser.parse(link))
                val key = id(link); groupIds += key
                if(dedup.add(key)) serverList.put(JSONObject().put("id",key).put("name",parsed.name).put("server",parsed.endpoint.server).put("port",parsed.endpoint.port).put("selected",link == current))
            }
            groups.put(JSONObject().put("id",group.getString("id")).put("name",group.getString("name")).put("canRefresh",group.optString("source").isNotBlank()).put("serverIds",array(groupIds)))
        }
        val dpi = LocalDpiRuntime.state.value
        val tg = TelegramProxyRuntime.state.value
        val logs = dpi.log + tg.logs + VpnDiagnostics.lines.value
        val diagnostic = JSONArray().put(JSONObject().put("label","DPI").put("value",dpi.message).put("level",if(dpiActive())"ok" else "neutral"))
            .put(JSONObject().put("label","VPN").put("value",VlessRuntime.state.value.message).put("level",if(vpnActive())"ok" else "neutral"))
            .put(JSONObject().put("label","Telegram").put("value",tg.message).put("level",if(tgActive())"ok" else "neutral"))
        return JSONObject().put("appVersion",BuildConfig.VERSION_NAME).put("platform","android")
            .put("catalog",JSONObject().put("dpiServices",JSONObject().put("youtube","YouTube").put("discord","Discord")).put("vlessServices",vpnLabels)
                .put("strategies",JSONArray().also { list -> candidates.forEach { list.put(JSONObject().put("id",it.id).put("label",it.title)) } }).put("autoStrategyIds",array(candidates.map { it.id })))
            .put("profile",JSONObject().put("services",selectedServices()).put("customDomains",array(profile.customDomains())).put("domains",array(dpiDomains())).put("strategy",dpi.strategyId ?: profile.lastSuccessfulStrategy() ?: candidates.first().id))
            .put("vless",JSONObject().put("mode",if(routes.allTraffic)"all" else "selective").put("services",array(services)).put("customDomains",array(custom)).put("domains",array(routes.domains)).put("apps",array(routes.packages)))
            .put("verifiedStrategy",profile.lastSuccessfulStrategy() ?: JSONObject.NULL)
            .put("dpiTrial",JSONObject().put("candidate",trial.candidate ?: JSONObject.NULL).put("attempt",trial.tried.size).put("total",candidates.size)
                .put("hasNext",candidates.any { it.id !in trial.tried }).put("pendingConfirmation",dpi.phase==EnginePhase.ACTIVE && trial.candidate!=null && trial.candidate==dpi.strategyId)
                .put("name",candidates.firstOrNull { it.id==trial.candidate }?.title ?: ""))
            .put("dpiCommand",profile.customCommand().ifBlank { AndroidStrategies.BYEDPI_ANDROID_TCP_UDP_COMMAND })
            .put("active",dpiActive()).put("vlessActive",vpnActive()).put("vlessConfigured",!current.isNullOrBlank())
            .put("telegramActive",tgActive()).put("telegramBusy",tg.phase==TelegramProxyPhase.STARTING).put("telegramError",if(tg.phase==TelegramProxyPhase.ERROR)tg.message else "")
            .put("telegram",JSONObject().put("port",telegram.port()).put("enabled",telegram.jointStart()))
            .put("vlessServers",serverList).put("vlessSubscriptions",groups).put("vlessSubscriptionSaved",(0 until stored.length()).any { stored.getJSONObject(it).optString("source").isNotBlank() })
            .put("autoStart",false).put("operation",if(operation?.isActive==true)JSONObject().put("kind",operationName) else JSONObject.NULL)
            .put("diagnostics",diagnostic).put("logs",array(logs.takeLast(100))).put("appLibrary",appLibrary())
            .put("bypassApps", JSONArray().also { list ->
                val selected=org.localroute.android.data.VpnExclusions(activity).packages()
                RecommendedBypassApps.apps.forEach { (key,name) -> list.put(JSONObject().put("id",key).put("name",name).put("selected",key in selected)) }
            })
    }
    private fun appLibrary(): JSONArray {
        val entries = linkedMapOf("com.google.android.youtube" to "YouTube", "com.discord" to "Discord", "org.telegram.messenger" to "Telegram", "com.android.chrome" to "Google Chrome", "com.instagram.android" to "Instagram", "com.openai.chatgpt" to "ChatGPT", "com.twitter.android" to "X / Twitter", "com.zhiliaoapp.musically" to "TikTok")
        return JSONArray().also { list -> entries.forEach { (key,name) -> list.put(JSONObject().put("name",name).put("process",key)) } }
    }

    private suspend fun execute(method: String, payload: Any?): JSONObject {
        val raw = payload as? JSONObject ?: JSONObject()
        when(method) {
            "readState" -> Unit
            "saveProfile" -> {
                check(!dpiActive())
                val services = strings(raw.optJSONArray("services")).mapNotNull { if(it=="youtube")ServiceTarget.YOUTUBE else if(it=="discord")ServiceTarget.DISCORD else null }.toSet()
                val custom = strings(raw.optJSONArray("customDomains")).toSet(); require(custom.all(RouteDomains::valid))
                val changed = services != profile.selectedServices() || custom != profile.customDomains()
                profile.saveSelectedServices(services); check(profile.saveCustomDomains(custom))
                if(changed) { profile.clearLastSuccessfulStrategy(); profile.saveDpiTrial(DpiTrialPlan()) }
            }
            "saveDpiCommand" -> { check(!dpiActive()); val command=payload as? String ?: ""; require(AndroidStrategies.custom(command)!=null); profile.saveCustomCommand(command); profile.clearLastSuccessfulStrategy(); profile.saveDpiTrial(DpiTrialPlan()) }
            "saveBypassApps" -> { check(!vpnActive()); val selected=strings(payload as? JSONArray).toSet(); require(RecommendedBypassApps.packages.containsAll(selected)); org.localroute.android.data.VpnExclusions(activity).save(selected) }
            "confirmDpi" -> {
                val trial=profile.dpiTrial(); val candidate=requireNotNull(trial.candidate)
                check(LocalDpiRuntime.state.value.phase==EnginePhase.ACTIVE && LocalDpiRuntime.state.value.strategyId==candidate)
                profile.saveLastSuccessfulStrategy(candidate); profile.saveDpiTrial(trial.confirmed())
                return response("Этот профиль сохранён после вашей проверки.")
            }
            "startVerified" -> { require(profile.lastSuccessfulStrategy()!=null); startLocal() }
            "autoSelect" -> return runTrial(restart=true)
            "testStrategy" -> return runTrial(restart=true, requested=payload as? String)
            "rejectDpi" -> return runTrial(restart=false)
            "toggleEngine" -> { if(dpiActive())stopDpi() else return runTrial(restart=true) }
            "stopEngine" -> { stopDpi(); awaitStopped(dpi=true) }
            "saveVless" -> {
                check(!vpnActive()); val uri=raw.optString("uri").ifBlank { servers.uri().orEmpty() }; VlessUriParser.parse(uri)
                val selected = strings(raw.optJSONArray("services"))
                val catalog=dpiCatalog.getJSONObject("vpn")
                val domains=selected.flatMap { strings(catalog.getJSONObject(it).getJSONArray("domains")) }.toSet() + strings(raw.optJSONArray("customDomains"))
                val routes=ServerRoutes(raw.optString("mode")=="all",domains,strings(raw.optJSONArray("apps")).toSet())
                servers.saveRoutes(routes); servers.save(uri)
                if(raw.optString("uri").isNotBlank()) addLinks(listOf(uri),"")
            }
            "importVless" -> return importSubscription(raw)
            "selectVless" -> { check(!vpnActive()); val selected=profiles().firstOrNull { id(it.link)==payload } ?: error("Сервер не найден"); servers.save(selected.link) }
            "removeSubscription" -> {
                check(!vpnActive()); val groups=library(); val remaining=JSONArray()
                for(i in 0 until groups.length()) if(groups.getJSONObject(i).getString("id")!=payload) remaining.put(groups.getJSONObject(i))
                check(remaining.length()!=groups.length()); servers.saveConnectionGroups(remaining)
                val removed=(0 until groups.length()).map { groups.getJSONObject(it) }.first { it.getString("id")==payload }
                if(links(removed).contains(servers.uri()) && profiles().none { it.link==servers.uri() }) servers.clearSelection()
            }
            "deleteVless" -> { check(!vpnActive()); servers.clear() }
            "startVless" -> startServer()
            "toggleVless" -> { if(vpnActive()) { stopVpn(); awaitStopped(vpn=true) } else startServer() }
            "testVless" -> {
                val started=!vpnActive()
                try {
                    if(started) startServer()
                    val ok=withContext(Dispatchers.IO) { runCatching { ServerConnectionProbe.check() }.getOrDefault(false) }
                    return response(if(ok)"HTTPS через сервер работает; проверьте нужные приложения." else "Нет ответа HTTPS через сервер.").put("ok",ok)
                        .put("results",JSONArray().put(JSONObject().put("label","HTTPS через сервер").put("success",ok).put("detail",if(ok)"HTTP 200" else "Нет ответа").put("durationMs",0)))
                } finally { if(started) { stopVpn(); withContext(NonCancellable) { awaitStopped(vpn=true) } } }
            }
            "autoSelectServer" -> return autoSelectServer()
            "saveTelegram" -> { check(!tgActive()); telegram.savePort(raw.optInt("port",1443)); telegram.setJointStart(raw.optBoolean("enabled")) }
            "startTelegram" -> startTelegram()
            "stopTelegram" -> stopTelegram()
            "copyTelegram" -> copy(telegram.link())
            "connectTelegram" -> activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(telegram.link())))
            "startAll" -> {
                val failures=mutableListOf<String>()
                suspend fun component(name: String, action: suspend () -> Unit) { try { action() } catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { failures += name } }
                if(!dpiActive() && profile.lastSuccessfulStrategy()!=null) component("DPI") { startLocal() }
                if(!vpnActive() && !servers.uri().isNullOrBlank()) component("VPN") { startServer() }
                if(!tgActive() && telegram.jointStart()) component("Telegram") { startTelegram() }
                return response(if(failures.isEmpty())"Настроенные компоненты запущены." else "Не удалось запустить: ${failures.joinToString()}. Другие компоненты обработаны.").put("ok",failures.isEmpty())
            }
            "stopAll" -> { stopDpi(); stopVpn(); stopTelegram(); awaitStopped(dpi=true,vpn=true) }
            "addTile" -> addTile(ShortcutMode.valueOf(payload as String))
            "open" -> {
                val url=payload as? String ?: ""; require(url in setOf("https://t.me/TheLocalRoute","https://max.ru/channel_LocalRoute","https://github.com/iKokTot/local-route"))
                activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))
            }
            "exportDiagnostics" -> { copy(JSONObject().put("version",BuildConfig.VERSION_NAME).put("platform","android").put("dpi",dpiActive()).put("vpn",vpnActive()).put("telegram",tgActive()).put("dpiDomainCount",dpiDomains().size).put("vpnDomainCount",servers.routes().domains.size).toString(2)); return response("Диагностика без секретов скопирована.") }
            else -> error("Команда не поддерживается")
        }
        return response("Настройки применены.")
    }
    private fun copy(value: String) { activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Local Route",value)) }
    private fun candidateStrategies(): List<DpiStrategy> {
        return AndroidStrategies.candidates(profile.lastSuccessfulStrategy(),profile.customCommand())
    }
    private suspend fun runTrial(restart: Boolean,requested: String?=null): JSONObject {
        if(dpiActive()) { stopDpi();awaitStopped(dpi=true);check(!dpiActive()) }
        val candidates=candidateStrategies()
        val ids=candidates.map { it.id }
        val previous=if(restart) DpiTrialPlan() else profile.dpiTrial()
        val plan=if(requested!=null) { require(requested in ids);DpiTrialPlan(setOf(requested),requested) } else previous.next(ids)
        profile.saveDpiTrial(plan)
        val candidate=candidates.firstOrNull { it.id==plan.candidate }
            ?: return response("Все варианты (${candidates.size}) проверены. DPI остановлен. Можно начать заново, задать свою строку или отдельно использовать VPN.").put("ok",false)
        try { startLocal(candidate) }
        catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { return response("Вариант «${candidate.title}» не запустился. Нажмите «Не работает — следующий».").put("ok",false) }
        return response("Вариант ${plan.tried.size} из ${candidates.size}: ${candidate.title}. Проверьте YouTube и Discord. Если не работает — нажмите «Не работает — следующий».").put("pendingConfirmation",true)
    }
    private suspend fun startLocal(requested: DpiStrategy?=null) {
        if(dpiActive()) return
        require(profile.selectedServices().isNotEmpty() || profile.customDomains().isNotEmpty())
        val strategy=requested ?: AndroidStrategies.resolve(profile.lastSuccessfulStrategy(),profile.customCommand()) ?: AndroidStrategies.custom(profile.customCommand()) ?: AndroidStrategies.all.first()
        LocalDpiRuntime.update(EnginePhase.STARTING,"Ожидаем разрешение Android и запуск DPI…",strategy.id)
        startDpi(profile.selectedServices(),strategy)
        check(withTimeoutOrNull(90_000) { while(LocalDpiRuntime.state.value.phase==EnginePhase.STARTING) delay(100); LocalDpiRuntime.state.value.phase==EnginePhase.ACTIVE }==true)
    }
    private suspend fun startServer() {
        if(vpnActive()) return
        require(!servers.uri().isNullOrBlank())
        VlessRuntime.update(TunnelState.Connecting,"Ожидаем разрешение Android и запуск VPN…")
        startVpn()
        check(withTimeoutOrNull(90_000) { while(VlessRuntime.state.value.state==TunnelState.Connecting) delay(100); VlessRuntime.state.value.state==TunnelState.Connected }==true)
    }
    private suspend fun awaitStopped(dpi: Boolean=false,vpn: Boolean=false) {
        withTimeoutOrNull(10_000) { while((dpi && dpiActive()) || (vpn && vpnActive()) || CombinedRuntime.state.value.busy) delay(100) }
    }
    private suspend fun importSubscription(raw: JSONObject): JSONObject {
        check(!vpnActive())
        val before=library()
        val updates=mutableListOf<Pair<String,List<String>>>()
        if(raw.optBoolean("refresh")) {
            for(i in 0 until before.length()) {
                val group=before.getJSONObject(i)
                if(raw.optString("groupId").isNotBlank() && group.getString("id")!=raw.getString("groupId")) continue
                val source=group.optString("source"); if(source.isBlank())continue
                val profiles=withContext(Dispatchers.IO) { SubscriptionLoader.load(source) }
                updates += source to profiles.map { it.link }
            }
            require(updates.isNotEmpty())
        } else {
            val input=raw.optString("input").trim(); val source=if(input.startsWith("https://")) input else ""
            val profiles=withContext(Dispatchers.IO) { if(source.isNotBlank())SubscriptionLoader.load(source) else VlessSubscriptionParser.parse(input) }
            require(profiles.isNotEmpty()); updates += source to profiles.map { it.link }
        }
        currentCoroutineContext().ensureActive()
        var result=before
        updates.forEach { (source,links) -> result=mergeGroup(result,links,source) }
        servers.saveConnectionGroups(result)
        return response("Список обновлён. Поддерживаются VLESS-серверы; другие протоколы пропущены.")
    }
    private fun mergeGroup(before: JSONArray, links: List<String>, source: String): JSONArray {
        val key=id("group:"+(source.ifBlank { links.joinToString("\n") }))
        val group=JSONObject().put("id",key).put("name",if(source.isBlank())"Мои серверы" else Uri.parse(source).host).put("source",source).put("links",array(links))
        var replaced=false; val result=JSONArray()
        for(i in 0 until before.length()) { val old=before.getJSONObject(i); if(old.getString("id")==key || (source.isNotBlank() && old.optString("source")==source)) { result.put(group); replaced=true } else result.put(old) }
        if(!replaced) { require(result.length()<16); result.put(group) }; return result
    }
    private fun addLinks(links: List<String>,source: String) { servers.saveConnectionGroups(mergeGroup(library(),links,source)) }
    private suspend fun autoSelectServer(): JSONObject {
        check(!vpnActive())
        val previous=servers.uri().orEmpty(); var found=false
        try {
            withTimeoutOrNull(120_000) {
                for(candidate in profiles().sortedBy { if(it.link==previous)0 else 1 }) {
                    servers.save(candidate.link)
                    try { startServer() } catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { }
                    if(vpnActive() && withContext(Dispatchers.IO) { runCatching { ServerConnectionProbe.check() }.getOrDefault(false) }) { found=true; break }
                    stopVpn(); awaitStopped(vpn=true)
                }
            }
            return response(if(found)"Сервер выбран по ответу HTTPS; VPN включён." else "Сервер с ответом HTTPS не найден.").put("ok",found)
        } finally {
            if(!found) { stopVpn(); withContext(NonCancellable) { awaitStopped(vpn=true) }; if(previous.isBlank())servers.clearSelection() else servers.save(previous) }
        }
    }
}
