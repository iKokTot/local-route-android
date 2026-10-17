package org.localroute.android.telegram

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

class TelegramProxySettings(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(context, "local_route_tgws",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun secret(): String = prefs.getString("secret", null) ?: ByteArray(16).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it.toInt() and 255) }.also { prefs.edit().putString("secret", it).commit() }
    fun link() = "tg://proxy?server=127.0.0.1&port=1443&secret=dd${secret()}"
    fun jointStart(): Boolean = prefs.getBoolean("joint_start", false)
    fun setJointStart(value: Boolean) = prefs.edit().putBoolean("joint_start", value).apply()
    companion object {
        // Как на десктопе, TGWs имеет самостоятельный прямой выход.
        fun outboundProxy(): String? = null
        fun routeLabel(): String = "напрямую"
    }
    fun arguments(): String {
        val gateway = outboundProxy()
        // Explicit upstream CF list: same fallback family observed on desktop, with UI disclosure.
        return "--host 127.0.0.1 --link-ip 127.0.0.1 --port 1443 --secret ${secret()} --default-domains --pool-size 1 --buf-kb 64 --ws-connect-timeout 8 --tcp-fallback-timeout 8 --no-outbound-proxy" +
            (gateway?.let { " --outbound-proxy $it" } ?: "")
    }
}
