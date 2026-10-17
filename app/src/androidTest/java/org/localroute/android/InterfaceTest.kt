package org.localroute.android

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InterfaceTest {
    @Test fun failedAutoSelectionRestoresPreviousProfile() {
        val store = org.localroute.android.tunnel.VlessStore(context)
        val previous = store.uri().orEmpty()
        val old = store.profiles(); val source = store.source()
        val candidate = "vless://00000000-0000-4000-8000-000000000000@10.0.2.2:18443?security=none#Fixture"
        try {
            store.importProfiles(org.localroute.android.tunnel.VlessSubscriptionParser.parse(candidate))
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            click("Сервер"); click("Настроить")
            clickScrolled("Подобрать и подключить")
            repeat(5) {
                SystemClock.sleep(300)
                for (label in listOf("Allow", "Разрешить", "OK", "ОК")) {
                    if (find(instrumentation.uiAutomation.rootInActiveWindow, label) != null) click(label)
                }
            }
            // The fixture deliberately refuses example.com:443: an open gateway must not pass.
            val deadline = SystemClock.uptimeMillis() + 45000
            while (SystemClock.uptimeMillis() < deadline && (store.uri() != previous || org.localroute.android.tunnel.VlessRuntime.state.value.state == org.localroute.android.tunnel.TunnelState.Connected)) SystemClock.sleep(200)
            repeat(8) { scrollNode(instrumentation.uiAutomation.rootInActiveWindow)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD); SystemClock.sleep(100) }
            awaitText("Сервер с ответом HTTPS не найден. Проверьте подписку или попробуйте другую сеть.")
            assertEquals(previous, store.uri())
        } finally {
            context.startService(Intent(context, org.localroute.android.tunnel.CombinedVpnService::class.java).setAction(org.localroute.android.tunnel.CombinedVpnService.STOP_SERVER))
            if (old.isNotEmpty()) store.importProfiles(old, source)
            store.save(previous)
        }
    }
    @Test fun serverSettingsShowSavedProfilesAndKeepExclusions() {
        val store = org.localroute.android.tunnel.VlessStore(context)
        val link = "vless://id@example.com:443?security=tls#SavedServer"
        val old = store.profiles(); val source = store.source(); val selected = store.uri().orEmpty()
        try {
            store.importProfiles(org.localroute.android.tunnel.VlessSubscriptionParser.parse(link))
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            click("Сервер"); click("Настроить")
            clickScrolled("Мои подключения")
            clickScrolled("SavedServer")
            clickScrolled("Выбрать")
            assertEquals(link, store.uri())
            clickScrolled("Настроить исключения")
            awaitText("Приложения без VPN"); click("Отмена")
        } finally {
            if (old.isNotEmpty()) store.importProfiles(old, source)
            store.save(selected)
        }
    }
    @Test fun settingsFollowCurrentModeAndKeepCommunityPanel() {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        for ((tab, title) in listOf("DPI" to "Настройки DPI", "Telegram" to "Настройки TG", "Сервер" to "Настройки VLESS")) {
            click(tab); click("Настроить"); awaitText(title)
            if (tab != "Сервер") assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "Настроить исключения"))
            click("Все настройки"); awaitText("МАКС"); awaitText("GitHub"); awaitText("ТГ")
            click("Назад")
        }
    }
    @Test fun firstModeHelperCanBeDismissedAndStaysDismissed() {
        val prefs = context.getSharedPreferences("local_route_help", android.content.Context.MODE_PRIVATE)
        val key = "mode_help_v1_0"
        val existed = prefs.contains(key); val previous = prefs.getBoolean(key, false)
        try {
            prefs.edit().remove(key).commit()
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            click("Telegram"); awaitText("Включить прокси"); click("DPI")
            awaitText("С чего начать"); click("Понятно")
            assertTrue(prefs.getBoolean(key, false))
            click("Telegram"); awaitText("Включить прокси"); click("DPI"); awaitText("Локальный обход")
            SystemClock.sleep(300)
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "С чего начать"))
        } finally {
            prefs.edit().apply { if (existed) putBoolean(key, previous) else remove(key) }.commit()
        }
    }
    @Test fun primaryActionIsVisibleAndSettingsAreSeparate() {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        click("DPI")
        assertTrue(awaitText("Включить DPI").isVisibleToUser)
        assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "Диагностика VPN"))
        click("Настроить")
        awaitText("Настройки DPI")
        assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "Включить DPI"))
        assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "Настроить исключения"))
        click("Все настройки")
        click("Настройки VLESS")
        clickScrolled("Настроить исключения")
        awaitText("Приложения без VPN")
        click("Отмена")
        click("Назад")
        click("Назад")
        assertTrue(awaitText("Включить DPI").isVisibleToUser)
    }
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun find(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == text) return node
        for (index in 0 until node.childCount) find(node.getChild(index), text)?.let { return it }
        return null
    }
    internal fun awaitText(text: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            find(instrumentation.uiAutomation.rootInActiveWindow, text)?.let { return it }
            SystemClock.sleep(100)
        }
        error("UI text not found: $text")
    }
    internal fun click(text: String) {
        awaitText(text)
        val deadline = SystemClock.uptimeMillis() + 3000
        while (SystemClock.uptimeMillis() < deadline) {
            var node = find(instrumentation.uiAutomation.rootInActiveWindow, text)
            while (node != null && !node.isClickable) node = node.parent
            if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
            SystemClock.sleep(100)
        }
        // Some Compose/API37 nodes expose clickable but reject ACTION_CLICK during re-layout.
        // Fall back to a real touch at the current accessible node, never invoke app callbacks.
        val target = find(instrumentation.uiAutomation.rootInActiveWindow, text)
        if (target != null && target.isVisibleToUser && target.isEnabled) {
            val bounds = android.graphics.Rect()
            target.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                val now = SystemClock.uptimeMillis()
                val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY(), 0)
                val up = android.view.MotionEvent.obtain(now, now + 60, android.view.MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY(), 0)
                down.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                up.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                try {
                    check(instrumentation.uiAutomation.injectInputEvent(down, true))
                    check(instrumentation.uiAutomation.injectInputEvent(up, true))
                    return
                } finally { down.recycle(); up.recycle() }
            }
        }
        error("Not clickable: $text")
    }
    private fun editable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (index in 0 until node.childCount) editable(node.getChild(index))?.let { return it }
        return null
    }
    @Test fun serverDraftSurvivesTabChangesAndStaysMasked() {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        click("Сервер")
        awaitText("Свой сервер")
        click("Настроить")
        awaitText("Настройки VLESS")
        var field: AccessibilityNodeInfo? = null
        val deadline = SystemClock.uptimeMillis() + 10000
        while (field == null && SystemClock.uptimeMillis() < deadline) {
            field = editable(instrumentation.uiAutomation.rootInActiveWindow)
            if (field == null) SystemClock.sleep(100)
        }
        requireNotNull(field)
        val original = field.text?.toString().orEmpty()
        val draft = "vless://00000000-0000-4000-8000-000000000000@example.com:443?security=tls"
        try {
            assertTrue(field.isPassword)
            assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, draft)
            }))
            click("DPI")
            awaitText("Локальный обход")
            click("Сервер")
            awaitText("Свой сервер")
            click("Настроить")
            awaitText("Настройки VLESS")
            var restoredField: AccessibilityNodeInfo? = null
            val restoreDeadline = SystemClock.uptimeMillis() + 10000
            while (restoredField == null && SystemClock.uptimeMillis() < restoreDeadline) {
                restoredField = editable(instrumentation.uiAutomation.rootInActiveWindow)
                if (restoredField == null) SystemClock.sleep(100)
            }
            val restored = requireNotNull(restoredField)
            assertTrue(restored.isPassword)
            // Accessibility may mask text, so explicitly reveal to validate the draft itself.
            click("Показать ссылку")
            awaitText(draft)
        } finally {
            editable(instrumentation.uiAutomation.rootInActiveWindow)?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, if (original.startsWith("vless://")) original else "")
            })
        }
    }
    private fun scrollNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (index in 0 until node.childCount) scrollNode(node.getChild(index))?.let { return it }
        return null
    }
    internal fun clickScrolled(text: String) {
        repeat(8) {
            if (find(instrumentation.uiAutomation.rootInActiveWindow, text) != null) { click(text); return }
            scrollNode(instrumentation.uiAutomation.rootInActiveWindow)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(200)
        }
        click(text)
    }

    @Test fun guideThenClearThreeTabsAndNoBundledServerPromise() {
        val prefs = context.getSharedPreferences("local_route", android.content.Context.MODE_PRIVATE)
        val completed = prefs.getBoolean("guide_completed_v2", false)
        try {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            click("Настроить")
            click("Все настройки")
            clickScrolled("Как пользоваться")
            awaitText("VLESS · ваш сервер")
            click("Дальше")
            awaitText("DPI · без сервера")
            click("Дальше")
            awaitText("TG · только Telegram")
            click("Начать пользоваться")
            awaitText("Включить прокси")
            click("Сервер")
            awaitText("Свой сервер")
            click("DPI")
            awaitText("Локальный обход")
            assertTrue(prefs.getBoolean("guide_completed_v2", false))
        } finally {
            prefs.edit().putBoolean("guide_completed_v2", completed).commit()
        }
    }
}
