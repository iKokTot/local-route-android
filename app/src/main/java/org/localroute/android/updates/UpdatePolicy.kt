package org.localroute.android.updates

import java.net.URI

data class AppUpdate(
    val versionCode: Long, val versionName: String, val packageName: String,
    val minSdk: Int, val certificate: String, val releaseUrl: String,
    val notes: String, val sha256: String
)

object UpdatePolicy {
    const val REPOSITORY = "https://github.com/iKokTot/local-route-android"
    const val INTERVAL = 24 * 60 * 60 * 1000L
    fun due(now: Long, last: Long) = last <= 0 || now < last || now - last >= INTERVAL
    fun releaseUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 &&
            uri.userInfo == null && uri.query == null && uri.fragment == null &&
            uri.rawPath.matches(Regex("/iKokTot/local-route-android/releases/tag/[A-Za-z0-9._-]+"))
    }.getOrDefault(false)
    fun compatible(update: AppUpdate, installedCode: Long, installedPackage: String, sdk: Int, certificates: Set<String>): Boolean =
        update.versionCode > installedCode && update.packageName == installedPackage &&
            update.minSdk in 26..sdk && certificates.size == 1 && update.certificate in certificates &&
            update.certificate.matches(Regex("[a-f0-9]{64}")) && update.sha256.matches(Regex("[a-f0-9]{64}")) &&
            update.versionName.matches(Regex("[0-9]+(?:\\.[0-9]+){1,3}(?:[-+][A-Za-z0-9.-]+)?")) && releaseUrl(update.releaseUrl)
}
