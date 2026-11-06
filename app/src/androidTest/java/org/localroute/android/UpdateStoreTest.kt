package org.localroute.android

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.localroute.android.updates.UpdateStore
import java.security.MessageDigest

class UpdateStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("app_updates",Context.MODE_PRIVATE)
    @Suppress("DEPRECATION")
    private fun certificate() = MessageDigest.getInstance("SHA-256").digest(context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNATURES).signatures!![0].toByteArray()).joinToString("") { "%02x".format(it) }
    private fun manifest() = JSONObject().put("schemaVersion",1).put("versionCode",BuildConfig.VERSION_CODE+1).put("versionName","0.24.17")
        .put("packageName",context.packageName).put("minSdk",26).put("certificateSha256",certificate()).put("sha256","a".repeat(64))
        .put("releaseUrl","https://github.com/iKokTot/local-route-android/releases/tag/android-v0.24.17")
        .put("apkAsset","Local-Route-Android-0.24.17.apk").put("size",123).put("notes","Исправления")
    private fun release() = JSONObject().put("tag_name","android-v0.24.17").put("draft",false).put("prerelease",false)
        .put("html_url",manifest().getString("releaseUrl")).put("assets",JSONArray().put(asset("local-route-update.json")).put(asset("Local-Route-Android-0.24.17.apk")))
    private fun asset(name: String) = JSONObject().put("name",name).put("state","uploaded").put("size",123).put("digest","sha256:${"a".repeat(64)}")
        .put("browser_download_url","https://github.com/iKokTot/local-route-android/releases/download/android-v0.24.17/$name")
    private fun isolated(test: () -> Unit) {
        val backup=prefs.all
        prefs.edit().clear().commit()
        try {test()} finally {
            val editor=prefs.edit().clear()
            backup.forEach { (key,value) -> when(value) {is String->editor.putString(key,value);is Long->editor.putLong(key,value);is Boolean->editor.putBoolean(key,value);is Int->editor.putInt(key,value)} }
            editor.commit()
        }
    }
    @Test fun dailyChecksManualOverrideAndSkipPersist() = isolated {
        var requests=0
        val store=UpdateStore(context) { url,_ -> requests++; if(url.contains("api.github.com"))release().toString() else manifest().toString() }
        runBlocking { store.check(false);store.check(false) }
        assertEquals(2,requests)
        assertTrue(store.state().getBoolean("showCard"))
        store.dismiss(true)
        assertFalse(UpdateStore(context).state().getBoolean("showCard"))
        assertTrue(store.state().getBoolean("available"))
        runBlocking {store.check(true)}
        assertEquals(4,requests);assertFalse(store.state().getBoolean("showCard"))
    }
    @Test fun snoozeAndOfflineChecksRetainKnownReleaseWithoutRepeatedRequests() = isolated {
        val store=UpdateStore(context) { url,_ -> if(url.contains("api.github.com"))release().toString() else manifest().toString() }
        runBlocking {store.check(true)}
        store.dismiss(false)
        assertFalse(store.state().getBoolean("showCard"))
        prefs.edit().putLong("snoozed_until",System.currentTimeMillis()-1).commit()
        assertTrue(store.state().getBoolean("showCard"))
        var requests=0
        val offline=UpdateStore(context) { _,_ -> requests++;error("Offline") }
        runBlocking {offline.check(true);offline.check(false)}
        assertEquals(1,requests);assertTrue(offline.state().getBoolean("available"))
        assertTrue(offline.state().getString("message").contains("Не удалось"))
    }
    @Test fun wrongPackageCertificateAndMissingApkNeverOfferDownload() = isolated {
        for(field in listOf("packageName","certificateSha256")) {
            val invalid=manifest().put(field,"wrong")
            val store=UpdateStore(context) { url,_ -> if(url.contains("api.github.com"))release().toString() else invalid.toString() }
            runBlocking {store.check(true)}
            assertFalse(store.state().getBoolean("available"));assertNull(store.releaseToOpen())
        }
        val release=release().put("assets",JSONArray().put(asset("local-route-update.json")))
        val store=UpdateStore(context) { url,_ -> if(url.contains("api.github.com"))release.toString() else manifest().toString() }
        runBlocking {store.check(true)};assertFalse(store.state().getBoolean("available"))
    }
    @Test fun prereleaseAndLegacyReleaseDoNotOfferUpdate() = isolated {
        for(release in listOf(release().put("prerelease",true),release().put("assets",JSONArray()))) {
            var requests=0
            val store=UpdateStore(context) { _,_ ->requests++;release.toString()}
            runBlocking {store.check(true)}
            assertEquals(1,requests);assertFalse(store.state().getBoolean("available"))
        }
    }
    @Test fun mismatchedDigestAndUntrustedReleaseUrlFailClosed() = isolated {
        for(invalid in listOf(manifest().put("sha256","b".repeat(64)),manifest().put("releaseUrl","https://evil.org/"))) {
            val store=UpdateStore(context) {url,_ -> if(url.contains("api.github.com"))release().toString() else invalid.toString()}
            runBlocking {store.check(true)};assertFalse(store.state().getBoolean("available"));assertNull(store.releaseToOpen())
        }
    }
}
