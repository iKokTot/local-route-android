package org.localroute.android.core

import org.junit.Assert.*
import org.junit.Test
import org.localroute.android.telegram.TelegramProxyRuntime

class TelegramRuntimeTest {
    @Test fun restartPreservesDiagnosticsAndMasksSecrets() {
        TelegramProxyRuntime.log("socks5://user:password@127.0.0.1:1081 0123456789abcdef0123456789abcdef")
        val logs = TelegramProxyRuntime.state.value.logs
        assertFalse(logs.last().contains("password"))
        assertFalse(logs.last().contains("0123456789abcdef"))
        TelegramProxyRuntime.starting()
        assertEquals(logs, TelegramProxyRuntime.state.value.logs)
    }
    @Test fun nativeFailureIsExplainedAndTechnicalDetailsRemainInLog() {
        TelegramProxyRuntime.failed("native test failure")
        assertFalse(TelegramProxyRuntime.state.value.message.contains("native test failure"))
        assertEquals("native test failure", TelegramProxyRuntime.state.value.logs.last())
        assertEquals(false, TelegramProxyRuntime.state.value.verified)
    }
}
