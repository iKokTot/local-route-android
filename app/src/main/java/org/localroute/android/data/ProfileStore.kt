package org.localroute.android.data

import android.content.Context

class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("local_route", Context.MODE_PRIVATE)
    fun lastSuccessfulStrategy(): String? = prefs.getString("last_successful_strategy", null)
    fun saveLastSuccessfulStrategy(id: String) = prefs.edit().putString("last_successful_strategy", id).apply()
    fun autostartRequested(): Boolean = prefs.getBoolean("autostart_requested", false)
    fun setAutostartRequested(value: Boolean) = prefs.edit().putBoolean("autostart_requested", value).apply()
}
