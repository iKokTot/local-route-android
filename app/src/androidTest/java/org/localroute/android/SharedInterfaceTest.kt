package org.localroute.android

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Проверка интерфейса на установленном APK без смены профилей и запуска сетевых движков. */
class SharedInterfaceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity
    private lateinit var web: WebView
    internal fun attach() {
        val context=instrumentation.targetContext
        activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        instrumentation.runOnMainSync { web=requireNotNull(findWeb(activity.window.decorView)) }
        await("Boolean(window.localRoute && document.querySelector('#engine-status') && document.querySelector('#engine-status').textContent!=='Загрузка состояния…')")
    }
    internal fun stopComponent(component: String) {
        require(component in listOf("dpi","vless"))
        js("navigate('control')")
        // Этот помощник вызывается сетевым тестом только после запуска компонента.
        await("!document.querySelector('#start-$component').disabled && state.${if(component=="dpi") "active" else "vlessActive"}")
        js("document.querySelector('#start-$component').click()")
    }
    private fun findWeb(view: View): WebView? {
        if(view is WebView)return view
        if(view is ViewGroup)for(index in 0 until view.childCount) findWeb(view.getChildAt(index))?.let { return it }
        return null
    }
    private fun js(script: String): Any? {
        val done=CountDownLatch(1);var result: String?=null
        instrumentation.runOnMainSync { web.evaluateJavascript(script) { result=it;done.countDown() } }
        check(done.await(10,TimeUnit.SECONDS)) { "WebView не ответил" }
        return result?.let { JSONTokener(it).nextValue() }
    }
    private fun await(script: String) {
        val deadline=SystemClock.uptimeMillis()+15000
        while(SystemClock.uptimeMillis()<deadline) { if(js(script)==true)return;SystemClock.sleep(100) }
        error("Условие интерфейса не выполнено: $script")
    }
    @Test fun sharedPageFitsAndSettingsBackKeepsOriginWithoutTouchingConnections() {
        attach()
        assertEquals("Обзор",js("document.querySelector('#page-title').textContent"))
        assertEquals("https://localroute.invalid/index.html",js("location.href"))
        js("window.localRoute.readState().then(value=>window.nativeSnapshot=value)")
        await("Boolean(window.nativeSnapshot)")
        assertEquals("android",js("window.nativeSnapshot.platform"))
        assertEquals(BuildConfig.VERSION_NAME,js("window.nativeSnapshot.appVersion"))
        assertEquals(false,js("Boolean(window.nativeSnapshot.vlessSecret || window.nativeSnapshot.vlessLibrarySecret)"))
        assertEquals(true,js("(()=>{const nav=document.querySelector('.sidebar').getBoundingClientRect();return [...document.querySelectorAll('.component-card')].every(card=>card.getBoundingClientRect().bottom<nav.top)})()"))
        val before=js("JSON.stringify([nativeSnapshot.active,nativeSnapshot.vlessActive,nativeSnapshot.telegramActive,nativeSnapshot.profile,nativeSnapshot.vless,nativeSnapshot.vlessServers])")
        js("navigate('lab');document.querySelector('.topbar [data-page=\"settings\"]').click()")
        assertEquals("Настройки",js("document.querySelector('#page-title').textContent"))
        instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
        await("document.querySelector('#page-title').textContent==='DPI'")
        js("navigate('routes');document.querySelector('#open-subscription').click()")
        assertEquals(true,js("document.querySelector('#subscription-dialog').open"))
        instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
        await("!document.querySelector('#subscription-dialog').open")
        assertEquals("VPN",js("document.querySelector('#page-title').textContent"))
        js("window.localRoute.readState().then(value=>window.afterSnapshot=value)")
        await("Boolean(window.afterSnapshot)")
        assertEquals(before,js("JSON.stringify([afterSnapshot.active,afterSnapshot.vlessActive,afterSnapshot.telegramActive,afterSnapshot.profile,afterSnapshot.vless,afterSnapshot.vlessServers])"))
        js("navigate('control')")
    }
}
