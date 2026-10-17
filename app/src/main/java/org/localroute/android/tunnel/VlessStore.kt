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

    fun routes(): org.localroute.android.core.ServerRoutes {
        val defaults = org.localroute.android.core.ServerRoutes()
        return org.localroute.android.core.ServerRoutes(prefs.getBoolean("route_all_v2", false),
            prefs.getStringSet("route_domains_v2", defaults.domains).orEmpty().toSet(),
            prefs.getStringSet("route_packages_v2", emptySet()).orEmpty().toSet())
    }
    fun saveRoutes(routes: org.localroute.android.core.ServerRoutes) {
        check(prefs.edit().putBoolean("route_all_v2", routes.allTraffic).putStringSet("route_domains_v2", routes.domains)
            .putStringSet("route_packages_v2", routes.packages).commit()) { "Не удалось сохранить маршруты" }
    }
    fun uri(): String? = prefs.getString(KEY_URI, null)
    fun connectionGroups(): org.json.JSONArray {
        val stored = prefs.getString("connection_groups_v2", null)
        if (stored != null) return org.json.JSONArray(stored)
        val profiles = profiles()
        if (profiles.isEmpty()) return org.json.JSONArray()
        return org.json.JSONArray().put(org.json.JSONObject().put("id", "legacy").put("name", "Мои подключения")
            .put("source", source()).put("links", org.json.JSONArray(profiles.map { it.link })))
    }
    fun saveConnectionGroups(groups: org.json.JSONArray) {
        require(groups.length() <= 16)
        val links = (0 until groups.length()).flatMap { index ->
            val values = groups.getJSONObject(index).getJSONArray("links")
            (0 until values.length()).map(values::getString)
        }.toSet()
        val edit = prefs.edit().putString("connection_groups_v2", groups.toString()).remove("profiles_v1").remove("source_v1")
        if (uri() != null && uri() !in links) edit.remove(KEY_URI)
        check(edit.commit()) { "Не удалось сохранить подписки" }
    }
    fun clearSelection() { check(prefs.edit().remove(KEY_URI).commit()) { "Не удалось удалить выбор сервера" } }
    fun profiles(): List<VlessProfile> = VlessSubscriptionParser.parse(prefs.getString("profiles_v1", "").orEmpty()).ifEmpty {
        uri()?.let { runCatching { listOf(VlessProfile(it, VlessUriParser.parse(it))) }.getOrNull() }.orEmpty()
    }
    fun source(): String = prefs.getString("source_v1", "").orEmpty()
    fun importProfiles(profiles: List<VlessProfile>, source: String = "") {
        require(profiles.isNotEmpty())
        check(prefs.edit().putString("profiles_v1", profiles.joinToString("\n") { it.link })
            .putString("source_v1", source).commit()) { "Не удалось сохранить подписку" }
    }
    fun clear() { check(prefs.edit().clear().commit()) { "Не удалось удалить подключения" } }
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
