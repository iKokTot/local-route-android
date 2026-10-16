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
    fun routeProfile(): VlessRouteProfile = VlessRouteProfile(
        prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty(),
        prefs.getBoolean(KEY_ALL, false)
    )

    fun save(uri: String, profile: VlessRouteProfile) {
        prefs.edit()
            .putString(KEY_URI, uri.trim())
            .putStringSet(KEY_PACKAGES, profile.selectedPackages)
            .putBoolean(KEY_ALL, profile.routeAllApplications)
            .apply()
    }

    private companion object {
        const val KEY_URI = "vless_uri_v1"
        const val KEY_PACKAGES = "vless_packages_v1"
        const val KEY_ALL = "vless_route_all_v1"
    }
}
