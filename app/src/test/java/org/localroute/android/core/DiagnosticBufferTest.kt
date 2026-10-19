package org.localroute.android.core

import org.junit.Assert.*
import org.junit.Test

class DiagnosticBufferTest {
    @Test fun captureIncludesSuccessfulAndIdleConnectionsAndExpires() {
        var time=100L
        val context=FailureLogContext { time }
        context.startCapture()
        assertEquals(1,context.accept("INFO [123 0ms] outbound/vless: connection to chatgpt.com:443").size)
        assertEquals(1,context.accept("DEBUG [123 20s] flow closed: idle timeout").size)
        time += 90_000
        assertFalse(context.capturing())
        assertTrue(context.accept("INFO [124 0ms] outbound connection").isEmpty())
        context.startCapture();context.stopCapture()
        assertFalse(context.capturing())
    }
    @Test fun removesCredentialsAndSubscriptionUrlsBeforeStorage() {
        val secret="00000000-0000-4000-8000-000000000000"
        val text=DiagnosticRedactor.clean("\u001b[31mERROR\u001b[0m vless://$secret@host:443 https://sub.example/privateToken socks5://user:pass@127.0.0.1 password=letmein token=abc123 secret=telegram-secret Bearer eyTest.signature uuid=$secret")
        for(value in listOf(secret,"privateToken","letmein","abc123","telegram-secret","eyTest.signature","user:pass","\u001b")) assertFalse(value,text.contains(value))
        assertTrue(text.contains("ERROR"))
    }
    @Test fun jsonCredentialsAndNewlinesAreSanitized() {
        val text=DiagnosticRedactor.clean("{\"password\":\"private\",\"secret\":\"hidden\",\"public_key\":\"sensitive\"}\nnext")
        assertFalse(text.contains("private"));assertFalse(text.contains("sensitive"));assertFalse(text.contains("\n"))
    }
    @Test fun bufferRotatesAndCanBeRestoredSafely() {
        val buffer=DiagnosticBuffer(3,100)
        repeat(10) { buffer.add("event-$it") }
        assertEquals(listOf("event-7","event-8","event-9"),buffer.snapshot())
        val restored=DiagnosticBuffer(3,100)
        buffer.snapshot().joinToString("\n").lineSequence().forEach(restored::add)
        assertEquals(buffer.snapshot(),restored.snapshot())
        buffer.clear();assertTrue(buffer.snapshot().isEmpty())
    }
    @Test fun characterLimitBoundsMemory() {
        val buffer=DiagnosticBuffer(500,100)
        repeat(100) { buffer.add("a".repeat(40)) }
        assertTrue(buffer.snapshot().sumOf { it.length+1 }<=100)
    }
    @Test fun failuresIncludeOnlyMatchingConnectionContext() {
        val context=FailureLogContext()
        assertTrue(context.accept("INFO[0001] [123 0ms] inbound/tun: inbound connection to chatgpt.com:443").isEmpty())
        context.accept("INFO[0001] [123 0ms] outbound/vless[vless-out]: outbound connection to chatgpt.com:443")
        context.accept("INFO[0001] [999 0ms] outbound/direct: outbound connection to unrelated.example:443")
        val report=context.accept("ERROR[0001] [123 315ms] connection: report handshake success: connection refused")
        assertEquals(3,report.size)
        assertTrue(report.any { it.contains("chatgpt.com") && it.contains("vless-out") })
        assertFalse(report.any { it.contains("unrelated.example") })
    }
    @Test fun contextClearDoesNotResurrectOldDestinations() {
        val context=FailureLogContext()
        context.accept("INFO [123 0ms] inbound connection to old.example:443")
        context.clear()
        assertEquals(1,context.accept("ERROR [123 3ms] connection refused").size)
    }
}
