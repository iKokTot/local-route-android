package org.localroute.android

import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.valnesfjord.tgwsproxyrs.NativeProxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.localroute.android.telegram.*
import org.localroute.android.tunnel.*

@RunWith(AndroidJUnit4::class)
class NetworkIntegrationTest {
    @Test fun excludedAppBypassesTunInBothModesAndReturnsAfterRemoval() {
        foreground()
        val store = VlessStore(context)
        val previous = store.uri().orEmpty()
        val exclusions = org.localroute.android.data.VpnExclusions(context)
        val oldExclusions = exclusions.packages()
        fun command(action: String) {
            val before = CombinedRuntime.state.value.completed
            ContextCompat.startForegroundService(context, Intent(context, CombinedVpnService::class.java).setAction(action))
            await { CombinedRuntime.state.value.completed > before && !CombinedRuntime.state.value.busy }
        }
        fun probeVpn(): String = context.contentResolver.query(Uri.parse("content://org.localroute.networkprobe/check?vpnstatus=1"), null, null, null, null)!!.use { check(it.moveToFirst()); it.getString(0) }
        try {
            exclusions.save(oldExclusions - "org.localroute.networkprobe")
            store.save("vless://00000000-0000-4000-8000-000000000000@10.0.2.2:18443?security=none&type=ws&path=%2Ftest")
            command(CombinedVpnService.START_SERVER)
            assertEquals("local-route-vless-fixture", probe())
            exclusions.save(oldExclusions + "org.localroute.networkprobe")
            command(CombinedVpnService.RELOAD)
            await { probeVpn() == "DIRECT" }
            assertEquals(TunnelState.Connected, VlessRuntime.state.value.state)
            command(CombinedVpnService.START_DPI)
            await { probeVpn() == "DIRECT" }
            assertTrue(CombinedRuntime.state.value.plan.dpiEnabled)
            command(CombinedVpnService.STOP_SERVER)
            await { probeVpn() == "VPN" }
            command(CombinedVpnService.START_SERVER)
            exclusions.save(oldExclusions - "org.localroute.networkprobe")
            command(CombinedVpnService.RELOAD)
            assertEquals("local-route-vless-fixture", probe())
            exclusions.save(emptySet())
            assertTrue(org.localroute.android.data.VpnExclusions(context).packages().isEmpty())
        } finally {
            command(CombinedVpnService.STOP_ALL)
            exclusions.save(oldExclusions); store.save(previous)
        }
    }
    @Test fun uiStopButtonsAffectOnlyTheirOwnMode() {
        val store = VlessStore(context)
        val previous = store.uri().orEmpty()
        val prefs = context.getSharedPreferences("local_route", android.content.Context.MODE_PRIVATE)
        val guideCompleted = prefs.getBoolean("guide_completed_v2", false)
        fun command(action: String) = ContextCompat.startForegroundService(context, Intent(context, CombinedVpnService::class.java).setAction(action))
        try {
            store.save("vless://00000000-0000-4000-8000-000000000000@10.0.2.2:18443?security=none&type=ws&path=%2Ftest")
            foreground()
            command(CombinedVpnService.START_DPI)
            await { org.localroute.android.localdpi.LocalDpiRuntime.state.value.phase == org.localroute.android.localdpi.EnginePhase.ACTIVE }
            command(CombinedVpnService.START_SERVER)
            await { VlessRuntime.state.value.state == TunnelState.Connected }
            val ui = SharedInterfaceTest().apply { attach() }
            ui.stopComponent("dpi")
            await { org.localroute.android.localdpi.LocalDpiRuntime.state.value.phase == org.localroute.android.localdpi.EnginePhase.STOPPED && !CombinedRuntime.state.value.busy }
            assertEquals(TunnelState.Connected, VlessRuntime.state.value.state)
            assertEquals("local-route-vless-fixture", probe())
            command(CombinedVpnService.START_DPI)
            await { org.localroute.android.localdpi.LocalDpiRuntime.state.value.phase == org.localroute.android.localdpi.EnginePhase.ACTIVE }
            ui.stopComponent("vless")
            await { VlessRuntime.state.value.state == TunnelState.Disconnected && !CombinedRuntime.state.value.busy }
            assertEquals(org.localroute.android.localdpi.EnginePhase.ACTIVE, org.localroute.android.localdpi.LocalDpiRuntime.state.value.phase)
            ui.stopComponent("dpi")
            await { !CombinedRuntime.state.value.plan.requiresVpn && !CombinedRuntime.state.value.busy }
        } finally {
            context.startService(Intent(context, CombinedVpnService::class.java).setAction(CombinedVpnService.STOP_ALL))
            store.save(previous)
            prefs.edit().putBoolean("guide_completed_v2", guideCompleted).commit()
        }
    }
    @Test fun emptySelectionRemainsEmpty() {
        val profile = org.localroute.android.data.ProfileStore(context)
        val previous = profile.selectedServices()
        try {
            profile.saveSelectedServices(emptySet())
            assertTrue(profile.selectedServices().isEmpty())
        } finally { profile.saveSelectedServices(previous) }
    }
    @Test fun combinedRejectsInvalidServerWithoutStoppingDpiAndHandlesRapidCommands() {
        foreground()
        val store = VlessStore(context)
        val previous = store.uri().orEmpty()
        fun command(action: String) = ContextCompat.startForegroundService(context, Intent(context, CombinedVpnService::class.java).setAction(action))
        try {
            command(CombinedVpnService.START_DPI)
            await { org.localroute.android.localdpi.LocalDpiRuntime.state.value.phase == org.localroute.android.localdpi.EnginePhase.ACTIVE && !CombinedRuntime.state.value.busy }
            store.save("invalid-link")
            command(CombinedVpnService.START_SERVER)
            await { VlessRuntime.state.value.state is TunnelState.InvalidConfig }
            assertEquals(org.localroute.android.localdpi.EnginePhase.ACTIVE, org.localroute.android.localdpi.LocalDpiRuntime.state.value.phase)
            val baseline = CombinedRuntime.state.value.completed
            command(CombinedVpnService.STOP_DPI)
            command(CombinedVpnService.START_DPI)
            command(CombinedVpnService.STOP_DPI)
            command(CombinedVpnService.START_DPI)
            await(45000) { CombinedRuntime.state.value.completed >= baseline + 4 && !CombinedRuntime.state.value.busy }
            assertEquals(org.localroute.android.localdpi.EnginePhase.ACTIVE, org.localroute.android.localdpi.LocalDpiRuntime.state.value.phase)
            assertTrue(CombinedRuntime.state.value.plan.dpiEnabled)
        } finally {
            context.startService(Intent(context, CombinedVpnService::class.java).setAction(CombinedVpnService.STOP_ALL))
            store.save(previous)
        }
    }
    @Test fun combinedSelectedPackageTcpAndUdpUseLocalSocks() {
        foreground()
        val store = VlessStore(context)
        val previous = store.uri().orEmpty()
        val messages = java.util.concurrent.CopyOnWriteArrayList<String>()
        var client: io.nekohasekai.libbox.CommandClient? = null
        try {
            store.save("vless://00000000-0000-4000-8000-000000000000@10.0.2.2:18443?security=none&type=ws&path=%2Ftest")
            ContextCompat.startForegroundService(context, Intent(context, CombinedVpnService::class.java).setAction(CombinedVpnService.START_SERVER))
            await { VlessRuntime.state.value.state == TunnelState.Connected }
            ContextCompat.startForegroundService(context, Intent(context, CombinedVpnService::class.java).setAction(CombinedVpnService.START_DPI).putExtra("test_package", "org.localroute.networkprobe"))
            await { org.localroute.android.localdpi.LocalDpiRuntime.state.value.phase == org.localroute.android.localdpi.EnginePhase.ACTIVE }
            val handler = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(io.nekohasekai.libbox.CommandClientHandler::class.java)) { _, method, args ->
                when (method.name) {
                    "hashCode" -> 1
                    "equals" -> false
                    "toString" -> "CombinedTestLog"
                    "writeLogs" -> { val logs = args!![0] as io.nekohasekai.libbox.LogIterator
                        while (logs.hasNext()) messages.add(logs.next().message)
                        null }
                    else -> null
                }
            } as io.nekohasekai.libbox.CommandClientHandler
            client = io.nekohasekai.libbox.CommandClient(handler, io.nekohasekai.libbox.CommandClientOptions().apply { addCommand(io.nekohasekai.libbox.Libbox.CommandLog) })
            client.connect()
            fun selectedProbe(query: String): String {
                repeat(3) { attempt ->
                    val result = context.contentResolver.query(Uri.parse("content://org.localroute.networkprobe/check?$query=1&waitvpn=1"), null, null, null, null)!!.use { check(it.moveToFirst()); it.getString(0) }
                    if (result != "DIRECT_UNREACHABLE" || attempt == 2) return result
                    Thread.sleep(500)
                }
                error("Unreachable")
            }
            val localResult = selectedProbe("localtcp")
            android.util.Log.i("CombinedRoutes", messages.joinToString("\n"))
            assertEquals(messages.joinToString("\n") + "\n" + org.localroute.android.localdpi.LocalDpiRuntime.state.value + "\n" + VlessRuntime.state.value, "local-route-dpi-fixture", localResult)
            assertEquals(messages.joinToString("\n"), "local-route-udp", selectedProbe("localudp"))
            await { messages.any { it.contains("outbound/socks[local-dpi]") && it.contains("18445") } && messages.any { it.contains("outbound/socks[local-dpi]") && it.contains("18446") } }
        } finally {
            client?.disconnect()
            context.startService(Intent(context, CombinedVpnService::class.java).setAction(CombinedVpnService.STOP_ALL))
            store.save(previous)
        }
    }
    @Test fun combinedLifecyclePreservesRemainingMode() {
        foreground()
        val store = VlessStore(context)
        val previous = store.uri().orEmpty()
        fun command(action: String) = ContextCompat.startForegroundService(context, Intent(context, CombinedVpnService::class.java).setAction(action))
        fun waitState(dpi: Boolean, server: Boolean) = await(30000) {
            (org.localroute.android.localdpi.LocalDpiRuntime.state.value.phase == org.localroute.android.localdpi.EnginePhase.ACTIVE) == dpi &&
                (VlessRuntime.state.value.state == TunnelState.Connected) == server
        }
        try {
            store.save("vless://00000000-0000-4000-8000-000000000000@10.0.2.2:18443?security=none&type=ws&path=%2Ftest")
            command(CombinedVpnService.START_DPI)
            waitState(true, false)
            command(CombinedVpnService.START_SERVER)
            waitState(true, true)
            val messages = java.util.concurrent.CopyOnWriteArrayList<String>()
            val handler = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(io.nekohasekai.libbox.CommandClientHandler::class.java)) { _, method, args ->
                when (method.name) {
                    "hashCode" -> 1
                    "equals" -> false
                    "toString" -> "LifecycleLog"
                    "writeLogs" -> { val logs = args!![0] as io.nekohasekai.libbox.LogIterator
                        while (logs.hasNext()) messages.add(logs.next().message)
                        null }
                    else -> null
                }
            } as io.nekohasekai.libbox.CommandClientHandler
            val logger = io.nekohasekai.libbox.CommandClient(handler, io.nekohasekai.libbox.CommandClientOptions().apply { addCommand(io.nekohasekai.libbox.Libbox.CommandLog) })
            logger.connect()
            val first = probe()
            logger.disconnect()
            assertEquals(CombinedRuntime.state.value.toString() + "\n" + messages.joinToString("\n"), "local-route-vless-fixture", first)
            command(CombinedVpnService.STOP_DPI)
            waitState(false, true)
            assertEquals("local-route-vless-fixture", probe())
            command(CombinedVpnService.START_DPI)
            waitState(true, true)
            command(CombinedVpnService.STOP_SERVER)
            waitState(true, false)
            assertNull(VlessGateway.proxyUrl())
            java.net.Socket("127.0.0.1", 1080).use {
                it.soTimeout = 2000
                it.getOutputStream().write(byteArrayOf(5, 1, 0))
                assertEquals(5, it.getInputStream().read()); assertEquals(0, it.getInputStream().read())
            }
            command(CombinedVpnService.STOP_DPI)
            waitState(false, false)
        } finally {
            context.startService(Intent(context, CombinedVpnService::class.java).setAction(CombinedVpnService.STOP_ALL))
            store.save(previous)
        }
    }
    @Test fun combinedConfigurationsAreAcceptedByBundledNativeEngine() {
        val endpoint = VlessUriParser.parse("vless://00000000-0000-4000-8000-000000000000@10.0.2.2:18443?security=none&type=ws&path=%2Ftest")
        for ((dpi, server) in listOf(true to false, false to true, true to true)) {
            val config = CombinedConfigCompiler.compile(org.localroute.android.core.CombinedRouting(dpi, server), if (server) endpoint else null)
            io.nekohasekai.libbox.Libbox.checkConfig(config)
            val parsed = org.json.JSONObject(config)
            assertEquals(1, parsed.getJSONArray("inbounds").length())
            assertEquals("tun", parsed.getJSONArray("inbounds").getJSONObject(0).getString("type"))
        }
    }
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun await(timeout: Long = 20000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out. VLESS=${VlessRuntime.state.value}; TG=${TelegramProxyRuntime.state.value}" }
            Thread.sleep(100)
        }
    }
    private fun foreground() {
        // Keep independent test cases isolated from Android's process-global
        // HTTP/DNS network caches. Within a lifecycle test the same UID/process
        // deliberately survives mode transitions and must recover normally.
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("am force-stop org.localroute.networkprobe")).use { it.readBytes() }
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val completed = CombinedRuntime.state.value.completed
        ContextCompat.startForegroundService(context, Intent(context, CombinedVpnService::class.java).setAction(CombinedVpnService.STOP_ALL))
        await { CombinedRuntime.state.value.completed > completed && !CombinedRuntime.state.value.busy && !CombinedRuntime.state.value.plan.requiresVpn }
        Thread.sleep(500)
    }
    private fun probe(dns: Boolean = false, waitVpn: Boolean = true, normal: Boolean = false): String {
        // Reload replaces the TUN. Android may still report the former VPN while
        // its first connection is being torn down. Require a genuine fixture
        // response after bounded recovery, not uninterrupted existing sockets.
        repeat(if (waitVpn) 3 else 1) { attempt ->
            val result = context.contentResolver.query(Uri.parse("content://org.localroute.networkprobe/check?dns=${if (dns) 1 else 0}&waitvpn=${if (waitVpn) 1 else 0}&normal=${if (normal) 1 else 0}"), null, null, null, null)!!.use {
                check(it.moveToFirst()); it.getString(0)
            }
            if (result != "DIRECT_UNREACHABLE" || !waitVpn || attempt == 2) return result
            Thread.sleep(500)
        }
        error("Unreachable")
    }
    private fun vless(transport: String, tls: Boolean = false, dns: Boolean = false) {
        foreground()
        val store = VlessStore(context)
        val oldUri = store.uri()
        val oldRoutes = store.routes()
        var logClient: io.nekohasekai.libbox.CommandClient? = null
        val messages = java.util.concurrent.CopyOnWriteArrayList<String>()
        try {
            val port = if (tls) 18444 else 18443
            store.saveRoutes(org.localroute.android.core.ServerRoutes(allTraffic = true))
            val security = if (tls) "tls&allowInsecure=1&sni=localhost" else "none"
            store.save("vless://00000000-0000-4000-8000-000000000000@10.0.2.2:$port?security=$security&type=$transport&path=%2Ftest")
            ContextCompat.startForegroundService(context, Intent(context, CombinedVpnService::class.java).setAction(CombinedVpnService.START_SERVER))
            await { VlessRuntime.state.value.state is TunnelState.Connected || VlessRuntime.state.value.state is TunnelState.Error }
            assertEquals(VlessRuntime.state.value.message, TunnelState.Connected, VlessRuntime.state.value.state)
            val handler = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(io.nekohasekai.libbox.CommandClientHandler::class.java)) { _, method, args ->
                when (method.name) {
                    "hashCode" -> 1
                    "equals" -> false
                    "toString" -> "TestLogHandler"
                    "writeLogs" -> {
                        val logs = args!![0] as io.nekohasekai.libbox.LogIterator
                        while (logs.hasNext()) messages.add(logs.next().message)
                        null
                    }
                    else -> null
                }
            } as io.nekohasekai.libbox.CommandClientHandler
            logClient = io.nekohasekai.libbox.CommandClient(handler, io.nekohasekai.libbox.CommandClientOptions().apply { addCommand(io.nekohasekai.libbox.Libbox.CommandLog) })
            logClient.connect()
            Thread.sleep(500)
            val result = probe(dns)
            assertEquals(messages.joinToString("\n"), "local-route-vless-fixture", result)
        } finally {
            runCatching { logClient?.disconnect() }
            context.startService(Intent(context, CombinedVpnService::class.java).setAction(CombinedVpnService.STOP_SERVER))
            await { VlessRuntime.state.value.state == TunnelState.Disconnected }
            store.save(oldUri.orEmpty())
            store.saveRoutes(oldRoutes)
        }
        assertEquals("DIRECT_UNREACHABLE", probe(waitVpn = false))
    }
    @Test fun vlessTcpWholeDeviceThroughActualTun() = vless("tcp")
    @Test fun vlessWebSocketWholeDeviceThroughActualTun() = vless("ws")
    @Test fun vlessTlsWebSocketWithDnsThroughActualTun() = vless("ws", tls = true, dns = true)

    @Test fun telegramStartsChecksRealDcStopsAndRestarts() {
        foreground()
        repeat(2) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, TelegramProxyService::class.java).setAction(TelegramProxyService.START))
                await { TelegramProxyRuntime.state.value.phase == TelegramProxyPhase.LISTENING || TelegramProxyRuntime.state.value.phase == TelegramProxyPhase.ERROR }
                assertEquals(TelegramProxyRuntime.state.value.message, TelegramProxyPhase.LISTENING, TelegramProxyRuntime.state.value.phase)
                assertTrue(NativeProxy.nativeIsRunning())
                TelegramMtprotoProbe.check(TelegramProxySettings(context).secret())
            } finally {
                context.startService(Intent(context, TelegramProxyService::class.java).setAction(TelegramProxyService.STOP))
                await { TelegramProxyRuntime.state.value.phase in setOf(TelegramProxyPhase.STOPPED, TelegramProxyPhase.ERROR) }
                assertFalse(NativeProxy.nativeIsRunning())
                Thread.sleep(500)
            }
        }
    }

    @Test fun telegramLinkUsesPaddedSecretAndCfFallbackReallyAnswers() {
        foreground()
        val settings = TelegramProxySettings(context)
        assertEquals("dd" + settings.secret(), Uri.parse(settings.link()).getQueryParameter("secret"))
        assertEquals("127.0.0.1", Uri.parse(settings.link()).getQueryParameter("server"))
        try {
            // Force this transport in the test so a working direct WS cannot mask a broken CF path.
            assertNull(NativeProxy.nativeStart(settings.arguments() + " --pinned-upstream cfproxy"))
            await(60000) { TelegramProxyRuntime.state.value.phase in setOf(TelegramProxyPhase.LISTENING, TelegramProxyPhase.ERROR) }
            assertEquals(TelegramProxyPhase.LISTENING, TelegramProxyRuntime.state.value.phase)
            TelegramMtprotoProbe.check(settings.secret(), timeout = 45000)
        } finally {
            NativeProxy.nativeStop()
            Thread.sleep(1000)
        }
    }

    @Test fun localDpiStillStartsAndPassesHttps() {
        foreground()
        val strategy = org.localroute.android.core.AndroidStrategies.all.first()
        val service = CombinedVpnService::class.java
        try {
            ContextCompat.startForegroundService(context, Intent(context, service)
                .setAction(CombinedVpnService.START_DPI)
                .putExtra(org.localroute.android.localdpi.LocalDpiVpnService.EXTRA_STRATEGY, strategy.id)
                .putExtra(org.localroute.android.localdpi.LocalDpiVpnService.EXTRA_STRATEGY_TITLE, strategy.title)
                .putExtra(org.localroute.android.localdpi.LocalDpiVpnService.EXTRA_STRATEGY_ARGUMENTS, strategy.nativeArguments.toTypedArray()))
            await { org.localroute.android.localdpi.LocalDpiRuntime.state.value.phase == org.localroute.android.localdpi.EnginePhase.ACTIVE }
            val result = probe(normal = true)
            assertTrue("Direct HTTPS alongside local DPI failed: $result", result.contains("Example Domain"))
            assertTrue(TelegramProxySettings(context).arguments().contains("socks5://127.0.0.1:1080"))
            ContextCompat.startForegroundService(context, Intent(context, TelegramProxyService::class.java).setAction(TelegramProxyService.START))
            await(40000) { TelegramProxyRuntime.state.value.verified != null || TelegramProxyRuntime.state.value.phase == TelegramProxyPhase.ERROR }
            assertEquals(TelegramProxyRuntime.state.value.message, true, TelegramProxyRuntime.state.value.verified)
        } finally {
            context.startService(Intent(context, TelegramProxyService::class.java).setAction(TelegramProxyService.STOP))
            await { !NativeProxy.nativeIsRunning() }
            context.startService(Intent(context, service).setAction(CombinedVpnService.STOP_DPI))
            await { org.localroute.android.localdpi.LocalDpiRuntime.state.value.phase == org.localroute.android.localdpi.EnginePhase.STOPPED }
        }
    }

    @Test fun telegramCanUseAuthenticatedVlessGateway() {
        foreground()
        val store = VlessStore(context)
        val previous = store.uri()
        try {
            store.save("vless://00000000-0000-4000-8000-000000000000@10.0.2.2:18443?type=tcp&security=none")
            org.localroute.android.shortcuts.ModeShortcuts.start(context, org.localroute.android.shortcuts.ShortcutMode.VLESS)
            await { VlessRuntime.state.value.state == TunnelState.Connected || VlessRuntime.state.value.state is TunnelState.Error }
            assertEquals(VlessRuntime.state.value.message, TunnelState.Connected, VlessRuntime.state.value.state)
            assertTrue(TelegramProxySettings(context).arguments().contains("--outbound-proxy socks5://"))
            java.net.Socket("127.0.0.1", VlessGateway.PORT).use { socket ->
                socket.soTimeout = 2000
                socket.getOutputStream().write(byteArrayOf(5, 1, 0))
                assertEquals(5, socket.getInputStream().read())
                assertEquals(255, socket.getInputStream().read())
            }
            org.localroute.android.shortcuts.ModeShortcuts.start(context, org.localroute.android.shortcuts.ShortcutMode.TG)
            await(40000) { TelegramProxyRuntime.state.value.verified != null || TelegramProxyRuntime.state.value.phase == TelegramProxyPhase.ERROR }
            assertEquals(TelegramProxyRuntime.state.value.message, true, TelegramProxyRuntime.state.value.verified)
            val gatewaySecret = VlessGateway.password!!
            assertFalse(TelegramProxyRuntime.state.value.logs.any { it.contains(gatewaySecret) })
        } finally {
            org.localroute.android.shortcuts.ModeShortcuts.stop(context, org.localroute.android.shortcuts.ShortcutMode.TG)
            await { !NativeProxy.nativeIsRunning() }
            org.localroute.android.shortcuts.ModeShortcuts.stop(context, org.localroute.android.shortcuts.ShortcutMode.VLESS)
            await { VlessRuntime.state.value.state == TunnelState.Disconnected }
            store.save(previous.orEmpty())
        }
    }

    @Test fun threeModeTilesAreDeclaredWithBrandIcon() {
        val names = listOf("org.localroute.android.localdpi.LocalRouteTileService", "org.localroute.android.shortcuts.TelegramTileService", "org.localroute.android.shortcuts.VlessTileService")
        names.forEachIndexed { index, name ->
            @Suppress("DEPRECATION")
            val service = context.packageManager.getServiceInfo(android.content.ComponentName(context, name), 0)
            assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", service.permission)
            assertEquals(org.localroute.android.shortcuts.ShortcutMode.entries[index].icon, service.icon)
        }
    }

    @Test fun quickModesSwitchVpnAndReconnectTelegram() {
        foreground()
        val modes = org.localroute.android.shortcuts.ModeShortcuts
        val store = VlessStore(context)
        val previous = store.uri()
        try {
            store.save("vless://00000000-0000-4000-8000-000000000000@10.0.2.2:18443?type=tcp&security=none")
            modes.start(context, org.localroute.android.shortcuts.ShortcutMode.TG)
            await(40000) { TelegramProxyRuntime.state.value.verified == true }
            modes.start(context, org.localroute.android.shortcuts.ShortcutMode.VLESS)
            await(40000) { TelegramProxyRuntime.state.value.verified == true && TelegramProxyRuntime.state.value.message.contains("через VLESS") }
            assertEquals(TunnelState.Connected, VlessRuntime.state.value.state)
            modes.start(context, org.localroute.android.shortcuts.ShortcutMode.DPI)
            await { org.localroute.android.localdpi.LocalDpiRuntime.state.value.phase == org.localroute.android.localdpi.EnginePhase.ACTIVE }
            assertEquals(TunnelState.Connected, VlessRuntime.state.value.state)
            modes.stop(context, org.localroute.android.shortcuts.ShortcutMode.VLESS)
            await(40000) { TelegramProxyRuntime.state.value.verified == true && TelegramProxyRuntime.state.value.message.contains("через DPI") }
            assertEquals(TunnelState.Disconnected, VlessRuntime.state.value.state)
            assertEquals(org.localroute.android.localdpi.EnginePhase.ACTIVE, org.localroute.android.localdpi.LocalDpiRuntime.state.value.phase)
        } finally {
            modes.stop(context, org.localroute.android.shortcuts.ShortcutMode.TG)
            await { !NativeProxy.nativeIsRunning() }
            modes.stop(context, org.localroute.android.shortcuts.ShortcutMode.VLESS)
            modes.stop(context, org.localroute.android.shortcuts.ShortcutMode.DPI)
            await { org.localroute.android.localdpi.LocalDpiRuntime.state.value.phase == org.localroute.android.localdpi.EnginePhase.STOPPED }
            store.save(previous.orEmpty())
        }
    }
}
