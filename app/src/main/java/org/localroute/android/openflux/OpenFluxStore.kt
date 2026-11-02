package org.localroute.android.openflux

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class OpenFluxStore(private val context: Context) {
    private val prefs = EncryptedSharedPreferences.create(context, "local_route_openflux",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun link(): String? = prefs.getString("link", null)
    suspend fun save(value: String) = withContext(Dispatchers.IO) {
        val link = value.trim()
        require(link.startsWith("openflux://v1/") && link.length <= 32766 && !link.contains('\n') && !link.contains('\r')) { "Нужна ссылка openflux://v1/ от выходного узла" }
        val process = ProcessBuilder(File(context.applicationInfo.nativeLibraryDir,"libopenflux.so").absolutePath,"--check")
            .redirectError(File("/dev/null")).start()
        try {
            process.outputStream.bufferedWriter().use { it.write(link); it.newLine() }
            check(process.waitFor(15,TimeUnit.SECONDS)) { "Проверка OpenFlux не завершилась" }
            val result = process.inputStream.bufferedReader().readLine().orEmpty()
            require(runCatching { JSONObject(result).optString("event") }.getOrNull()=="valid") { "Ссылка повреждена или этот режим пока не поддерживается" }
            check(prefs.edit().putString("link",link).commit()) { "Не удалось сохранить подключение" }
        } finally { process.destroyForcibly() }
    }
}
