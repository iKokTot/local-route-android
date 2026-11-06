package org.localroute.android.updates

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.localroute.android.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Public release metadata only. Never sends profiles, logs or a device identifier. */
class UpdateStore(context: Context, private val reader: ((String, Int) -> String)? = null) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    @Volatile private var checking = false
    @Volatile private var message = ""
    @Suppress("DEPRECATION")
    private val certificates: Set<String> by lazy {
        val signatures = if (Build.VERSION.SDK_INT >= 28)
            app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        else app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNATURES).signatures
        signatures.orEmpty().map { signature -> MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }
    private fun parse(json: JSONObject) = AppUpdate(json.getLong("versionCode"), json.getString("versionName"), json.getString("packageName"),
        json.getInt("minSdk"), json.getString("certificateSha256"), json.getString("releaseUrl"), json.optString("notes").take(2000), json.getString("sha256"))
    private fun compatible(update: AppUpdate) = UpdatePolicy.compatible(update, BuildConfig.VERSION_CODE.toLong(), app.packageName, Build.VERSION.SDK_INT, certificates)
    private fun cached(): AppUpdate? = runCatching { parse(JSONObject(prefs.getString("candidate", "")!!)) }.getOrNull()?.takeIf(::compatible)
    fun state(): JSONObject {
        val update = cached()
        return JSONObject().put("checking", checking).put("message", message).put("checkedAt", prefs.getLong("checked_at", 0))
            .put("available", update != null).put("version", update?.versionName.orEmpty()).put("notes", update?.notes.orEmpty())
            .put("showCard", update != null && update.versionCode != prefs.getLong("skipped", 0) && System.currentTimeMillis() >= prefs.getLong("snoozed_until", 0))
    }
    fun dismiss(skip: Boolean) {
        val update = cached() ?: return
        if (skip) prefs.edit().putLong("skipped", update.versionCode).apply()
        else prefs.edit().putLong("snoozed_until", System.currentTimeMillis() + UpdatePolicy.INTERVAL).apply()
    }
    fun releaseToOpen(): String? = cached()?.releaseUrl

    suspend fun check(force: Boolean): String = mutex.withLock {
        val now = System.currentTimeMillis()
        if (!force && !UpdatePolicy.due(now, prefs.getLong("checked_at", 0))) return@withLock message
        checking = true
        // Record attempts too: offline launches must not repeatedly contact GitHub.
        prefs.edit().putLong("checked_at", now).apply()
        try {
            val candidate = withContext(Dispatchers.IO) { loadRelease() }
            prefs.edit().apply {
                if (candidate == null) remove("candidate") else putString("candidate", candidate.toString())
            }.apply()
            message = when {
                candidate == null -> "Новых совместимых выпусков нет."
                parse(candidate).versionCode <= BuildConfig.VERSION_CODE -> "Установлена актуальная версия."
                else -> "Доступна версия ${parse(candidate).versionName}."
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            message = "Не удалось проверить обновления. Попробуйте позже."
        } finally { checking = false }
        message
    }
    private fun loadRelease(): JSONObject? {
        val release = JSONObject(fetch("https://api.github.com/repos/iKokTot/local-route-android/releases/latest", 512 * 1024))
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
        val tag = release.getString("tag_name")
        require(tag.matches(Regex("[A-Za-z0-9._-]+")))
        val releaseUrl = "${UpdatePolicy.REPOSITORY}/releases/tag/$tag"
        require(release.getString("html_url") == releaseUrl)
        val assets = release.getJSONArray("assets")
        val list = (0 until assets.length()).map(assets::getJSONObject)
        val metadata = list.singleOrNull { it.optString("name") == "local-route-update.json" && it.optString("state") == "uploaded" } ?: return null
        val metadataUrl = "${UpdatePolicy.REPOSITORY}/releases/download/$tag/local-route-update.json"
        require(metadata.getString("browser_download_url") == metadataUrl)
        val manifest = JSONObject(fetch(metadataUrl, 32 * 1024))
        require(manifest.getInt("schemaVersion") == 1 && manifest.getString("releaseUrl") == releaseUrl)
        val apkName = manifest.getString("apkAsset")
        require(apkName.matches(Regex("[A-Za-z0-9._-]+\\.apk")))
        val apk = list.singleOrNull { it.optString("name") == apkName && it.optString("state") == "uploaded" } ?: return null
        require(apk.getString("browser_download_url") == "${UpdatePolicy.REPOSITORY}/releases/download/$tag/$apkName")
        require(apk.getLong("size") == manifest.getLong("size") && apk.getLong("size") > 0)
        apk.optString("digest").takeIf { it.startsWith("sha256:") }?.let { require(it == "sha256:${manifest.getString("sha256")}") }
        val update = parse(manifest)
        return manifest.takeIf { compatible(update) }
    }
    private fun fetch(initial: String, limit: Int): String {
        reader?.let { return it(initial, limit) }
        var url = URL(initial)
        repeat(5) {
            require(url.protocol == "https" && url.userInfo == null && url.port == -1 &&
                url.host in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"))
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 5000; connection.readTimeout = 5000
                connection.setRequestProperty("User-Agent", "Local-Route-Android")
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) { url = URL(url, connection.getHeaderField("Location") ?: error("Missing redirect")) }
                else {
                    require(status == 200)
                    val bytes = connection.inputStream.use { it.readBytesBounded(limit) }
                    return bytes.toString(Charsets.UTF_8)
                }
            } finally { connection.disconnect() }
        }
        error("Too many redirects")
    }
    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) { val count = read(buffer); if (count < 0) break; require(output.size() + count <= limit); output.write(buffer, 0, count) }
        return output.toByteArray()
    }
}
