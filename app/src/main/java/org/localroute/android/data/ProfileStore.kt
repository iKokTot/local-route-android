package org.localroute.android.data

import android.content.Context
import org.localroute.android.core.ServiceTarget

class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("local_route", Context.MODE_PRIVATE)
    fun guideCompleted(): Boolean = prefs.getBoolean("guide_completed_v2", false)
    fun completeGuide() = prefs.edit().putBoolean("guide_completed_v2", true).apply()
    // v2 deliberately invalidates strategies that 0.3 marked successful using
    // only an HTTPS handshake. A profile is now saved only after the user has
    // confirmed that the real target app works.
    fun lastSuccessfulStrategy(): String? = prefs.getString("last_user_confirmed_strategy_v2", null)
    fun saveLastSuccessfulStrategy(id: String) = prefs.edit().putString("last_user_confirmed_strategy_v2", id).apply()
    fun clearLastSuccessfulStrategy() = prefs.edit().remove("last_user_confirmed_strategy_v2").apply()
    fun customCommand(): String = prefs.getString("custom_byedpi_command", "") ?: ""
    fun saveCustomCommand(command: String) = prefs.edit().putString("custom_byedpi_command", command).apply()
    fun selectedServices(): Set<ServiceTarget> {
        val saved = prefs.getStringSet("selected_services", null) ?: return ServiceTarget.entries.toSet()
        return saved.mapNotNull { value -> ServiceTarget.entries.firstOrNull { it.name == value } }.toSet()
    }
    fun saveSelectedServices(services: Set<ServiceTarget>) =
        prefs.edit().putStringSet("selected_services", services.map { it.name }.toSet()).apply()
}
