package org.localroute.android.data

import android.content.Context

/** Apps bypass Android TUN only while VLESS is enabled. DPI alone has no exclusions. */
class VpnExclusions(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("local_route_routing", Context.MODE_PRIVATE)
    fun packages(): Set<String> = prefs.getStringSet("excluded_packages", null)?.toSet() ?: defaults
    fun save(packages: Set<String>) {
        require(packages.all { it.matches(Regex("[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+")) })
        check(prefs.edit().putStringSet("excluded_packages", packages.toSet()).commit())
    }
    companion object {
        // Known package IDs, not domain guesses. Missing apps are ignored by Android.
        val defaults = org.localroute.android.core.RecommendedBypassApps.packages
    }
}
