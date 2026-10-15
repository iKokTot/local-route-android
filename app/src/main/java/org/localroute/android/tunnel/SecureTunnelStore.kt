package org.localroute.android.tunnel

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Stores the private WireGuard configuration with a key held by Android Keystore. */
class SecureTunnelStore(context: Context) {
    private val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        "local_route_secure_tunnel",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun configuration(): String? = prefs.getString(KEY_CONFIGURATION, null)

    fun saveConfiguration(configuration: String) {
        prefs.edit().putString(KEY_CONFIGURATION, configuration.trim()).apply()
    }

    fun clearConfiguration() {
        prefs.edit().remove(KEY_CONFIGURATION).apply()
    }

    private companion object { const val KEY_CONFIGURATION = "wireguard_configuration_v1" }
}
