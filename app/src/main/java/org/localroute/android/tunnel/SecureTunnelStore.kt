package org.localroute.android.tunnel

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

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

    /** Migrates the original single WireGuard slot into a named local profile. */
    private fun migrateLegacyConfiguration() {
        if (prefs.contains(KEY_PROFILES)) return
        val legacy = prefs.getString(KEY_LEGACY_CONFIGURATION, null)?.trim().orEmpty()
        if (legacy.isBlank()) return
        val profile = TunnelServerProfiles.default()
        prefs.edit()
            .putString(KEY_PROFILES, encodeProfiles(listOf(profile)))
            .putString(KEY_ACTIVE_PROFILE, profile.id)
            .putString(configurationKey(profile.id), legacy)
            .remove(KEY_LEGACY_CONFIGURATION)
            .apply()
    }

    fun profiles(): List<TunnelServerProfile> {
        migrateLegacyConfiguration()
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val id = item.optString("id").trim()
                    val name = item.optString("name").trim()
                    if (id.isNotBlank() && name.isNotBlank()) add(TunnelServerProfile(id, name))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun activeProfile(): TunnelServerProfile? =
        TunnelServerProfiles.active(profiles(), prefs.getString(KEY_ACTIVE_PROFILE, null))

    fun configuration(profileId: String = activeProfile()?.id ?: TunnelServerProfiles.DEFAULT_ID): String? {
        migrateLegacyConfiguration()
        return prefs.getString(configurationKey(profileId), null)
    }

    fun saveProfile(profile: TunnelServerProfile, configuration: String) {
        val updated = TunnelServerProfiles.replace(profiles(), profile)
        prefs.edit()
            .putString(KEY_PROFILES, encodeProfiles(updated))
            .putString(KEY_ACTIVE_PROFILE, profile.id)
            .putString(configurationKey(profile.id), configuration.trim())
            .apply()
    }

    fun saveConfiguration(configuration: String) {
        saveProfile(activeProfile() ?: TunnelServerProfiles.default(), configuration)
    }

    fun selectProfile(id: String) {
        if (profiles().any { it.id == id }) prefs.edit().putString(KEY_ACTIVE_PROFILE, id).apply()
    }

    private fun encodeProfiles(profiles: List<TunnelServerProfile>): String = JSONArray().apply {
        profiles.forEach { profile ->
            put(JSONObject().put("id", profile.id).put("name", profile.name).put("protocol", profile.protocol.name))
        }
    }.toString()

    private fun configurationKey(id: String) = "wireguard_configuration_$id"

    private companion object {
        const val KEY_LEGACY_CONFIGURATION = "wireguard_configuration_v1"
        const val KEY_PROFILES = "tunnel_profiles_v1"
        const val KEY_ACTIVE_PROFILE = "active_tunnel_profile_v1"
    }
}
