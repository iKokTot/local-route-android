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
    @Test fun noFalseDpiSuccessAndUnsavedSettingsAskBeforeLeaving() {
        attach()
        js("navigate('lab')")
        assertEquals(false, js("Boolean(document.querySelector('#android-dpi-feedback').offsetParent)"))
        assertEquals(false, js("Boolean(document.querySelector('#test-results .empty-state').offsetParent)"))
        js("navigate('telegram');navigate('settings');document.querySelector('#telegram-port').value='3443';document.querySelector('#telegram-port').dispatchEvent(new Event('input',{bubbles:true}));document.querySelector('#back-settings').click()")
        await("Boolean(document.querySelector('#confirmation-dialog')?.open)")
        assertEquals("settings", js("currentPage"))
        instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
        await("!document.querySelector('#confirmation-dialog')")
        assertEquals(true, js("dirty('telegram')"))
        js("document.querySelector('#back-settings').click()")
        await("Boolean(document.querySelector('#confirmation-dialog')?.open)")
        js("document.querySelector('#confirmation-dialog .primary').click()")
        await("currentPage==='telegram' && !dirty('telegram')")
        js("navigate('control')")
    }
    @Test fun oneConnectionFormImportsAndSelectsWithoutHiddenSave() {
        attach()
        val store = org.localroute.android.tunnel.VlessStore(instrumentation.targetContext)
        val routes = store.routes()
        val groups = store.connectionGroups()
        val selected = store.uri()
        try {
            js("navigate('routes')")
            assertEquals(false, js("Boolean(document.querySelector('#single-server-editor').offsetParent)"))
            assertEquals(false, js("Boolean(document.querySelector('[data-mode-settings=routes]').offsetParent)"))
            assertEquals(true, js("Boolean(document.querySelector('.vpn-exceptions').offsetParent)"))
            js("navigate('routes');document.querySelector('#open-subscription').click()")
            assertEquals(true, js("document.querySelector('#subscription-dialog').open"))
            assertEquals(false, js("document.querySelector('#subscription-input').disabled"))
            assertEquals(false, js("document.querySelector('#import-subscription').disabled"))
            js("document.querySelector('#subscription-input').value='https://example.org/subscription';document.querySelector('#subscription-input').dispatchEvent(new Event('input',{bubbles:true}))")
            assertEquals("https://example.org/subscription", js("document.querySelector('#subscription-input').value"))
            js("document.querySelector('#subscription-input').value='vless://00000000-0000-4000-8000-000000000000@example.org:443?security=tls&type=ws#InterfaceTest';document.querySelector('#import-subscription').click()")
            await("!pending && !document.querySelector('#subscription-dialog').open && state.vlessServers.length>0")
            js("document.querySelector('.select-server:not(:disabled)').click()")
            await("!pending && state.vlessConfigured")
            assertEquals(false, js("dirty('vless')"))
            assertEquals(false, js("document.querySelector('#toggle-vless').disabled"))
            assertEquals(true, store.routes().allTraffic)
            assertEquals(true, store.routes().packages.isEmpty())
            store.saveRoutes(routes.copy(allTraffic=false, packages=setOf("com.example.old")))
            assertEquals(true, store.routes().allTraffic)
            assertEquals(true, store.routes().packages.isEmpty())
            js("document.querySelector('#delete-vless').click()")
            await("Boolean(document.querySelector('#confirmation-dialog')?.open)")
            js("document.querySelector('#confirmation-dialog .outlined').click()")
            await("!document.querySelector('#confirmation-dialog')")
            assertNotNull(store.uri())
            assertEquals(true, js("document.documentElement.scrollWidth <= window.innerWidth"))
        } finally {
            store.saveRoutes(routes)
            store.saveConnectionGroups(groups)
            if(selected!=null) store.save(selected) else store.clearSelection()
            js("navigate('control')")
        }
    }
    @Test fun compactAndroidSettingsAndEditablePortPersist() {
        attach()
        assertEquals(true, js("document.querySelector('#diagnostics').closest('[data-page-panel]').dataset.pagePanel==='settings'"))
        assertEquals(false, js("Boolean(document.querySelector('[data-page-panel=control] #technical-log'))"))
        js("navigate('lab')")
        assertEquals(false, js("Boolean(document.querySelector('[data-page-panel=lab] #service-list'))"))
        js("navigate('settings')")
        assertEquals(true, js("document.querySelector('[data-mode-settings=lab]').open"))
        assertEquals(false, js("document.querySelector('[data-mode-settings=routes]').open"))
        js("navigate('telegram');navigate('settings')")
        assertEquals(false, js("document.querySelector('#telegram-port').disabled"))
        assertEquals(false, js("Boolean(document.querySelector('.telegram-autostart').offsetParent)"))
        val preferences = org.localroute.android.telegram.TelegramProxySettings(instrumentation.targetContext)
        val old = preferences.port()
        try {
            js("document.querySelector('#telegram-port').value='2443';document.querySelector('#telegram-port').dispatchEvent(new Event('input',{bubbles:true}));document.querySelector('#telegram-save').click()")
            await("!pending && state.telegram.port===2443")
            assertEquals(2443, org.localroute.android.telegram.TelegramProxySettings(instrumentation.targetContext).port())
            assertTrue(preferences.link().contains("port=2443&"))
            assertTrue(preferences.arguments().contains("--port 2443 "))
            assertTrue(runCatching { preferences.savePort(1080) }.isFailure)
            assertTrue(runCatching { preferences.savePort(65536) }.isFailure)
            assertEquals(true, js("document.documentElement.scrollWidth <= window.innerWidth"))
        } finally { preferences.savePort(old) }
    }
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
