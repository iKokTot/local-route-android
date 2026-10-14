package org.localroute.android.data

import android.content.Context
import org.localroute.android.core.ServiceTarget

class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("local_route", Context.MODE_PRIVATE)
    fun lastSuccessfulStrategy(): String? = prefs.getString("last_successful_strategy", null)
    fun saveLastSuccessfulStrategy(id: String) = prefs.edit().putString("last_successful_strategy", id).apply()
    fun selectedServices(): Set<ServiceTarget> {
        val saved = prefs.getStringSet("selected_services", null) ?: return ServiceTarget.entries.toSet()
        return saved.mapNotNull { value -> ServiceTarget.entries.firstOrNull { it.name == value } }.toSet()
            .ifEmpty { ServiceTarget.entries.toSet() }
    }
    fun saveSelectedServices(services: Set<ServiceTarget>) =
        prefs.edit().putStringSet("selected_services", services.map { it.name }.toSet()).apply()
    fun autostartRequested(): Boolean = prefs.getBoolean("autostart_requested", false)
    fun setAutostartRequested(value: Boolean) = prefs.edit().putBoolean("autostart_requested", value).apply()
}
