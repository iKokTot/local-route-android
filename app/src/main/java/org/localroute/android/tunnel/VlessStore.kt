package org.localroute.android.tunnel

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** VLESS credentials never leave this encrypted, device-local preference file. */
class VlessStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        "local_route_vless",
        MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun uri(): String? = prefs.getString(KEY_URI, null)
    fun profiles(): List<VlessProfile> = VlessSubscriptionParser.parse(prefs.getString("profiles_v1", "").orEmpty()).ifEmpty {
        uri()?.let { runCatching { listOf(VlessProfile(it, VlessUriParser.parse(it))) }.getOrNull() }.orEmpty()
    }
    fun source(): String = prefs.getString("source_v1", "").orEmpty()
    fun importProfiles(profiles: List<VlessProfile>, source: String = "") {
        require(profiles.isNotEmpty())
        prefs.edit().putString("profiles_v1", profiles.joinToString("\n") { it.link })
            .putString("source_v1", source).commit()
    }
    fun remove(link: String) {
        val remaining = profiles().filterNot { it.link == link }
        prefs.edit().putString("profiles_v1", remaining.joinToString("\n") { it.link }).apply()
    }
    fun save(uri: String) {
        val valid = runCatching { VlessUriParser.parse(uri) }.isSuccess
        val links = profiles().map { it.link }.toMutableList()
        if (valid && uri.trim() !in links) links.add(uri.trim())
        prefs.edit()
            .putString(KEY_URI, uri.trim())
            .putString("profiles_v1", links.takeLast(64).joinToString("\n"))
            .remove(KEY_PACKAGES)
            .remove(KEY_ALL)
            .apply()
    }

    private companion object {
        const val KEY_URI = "vless_uri_v1"
        const val KEY_PACKAGES = "vless_packages_v1"
        const val KEY_ALL = "vless_route_all_v1"
    }
}
