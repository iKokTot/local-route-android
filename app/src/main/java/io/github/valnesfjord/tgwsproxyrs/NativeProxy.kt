package io.github.valnesfjord.tgwsproxyrs

import org.localroute.android.telegram.TelegramProxyRuntime
import androidx.annotation.Keep

/** Upstream MIT JNI ABI, reviewed against tg-ws-proxy-rs v2.5.4 android.rs. */
@Keep
object NativeProxy {
    init { System.loadLibrary("tg_ws_proxy_jni") }
    @JvmStatic external fun nativeStart(args: String): String?
    @JvmStatic external fun nativeStop()
    @JvmStatic external fun nativeIsRunning(): Boolean
    @JvmStatic fun onNativeListening(link: String) = TelegramProxyRuntime.listening()
    @JvmStatic fun onNativeLog(line: String) = TelegramProxyRuntime.log(line)
    @JvmStatic fun onNativeError(message: String) = TelegramProxyRuntime.failed(message)
    @JvmStatic fun onNativeStopped() = TelegramProxyRuntime.stopped()
}
