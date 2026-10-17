package org.localroute.android.tunnel

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VlessSubscriptionParserTest {
    private val first = "vless://00000000-0000-4000-8000-000000000000@one.example:443?security=tls&type=ws&path=%2Fa#Первый"
    private val second = "vless://00000000-0000-4000-8000-000000000000@two.example:443?security=tls&type=grpc&serviceName=route#Второй"

    @Test fun parsesLinesAndUsesDisplayNames() {
        val profiles = VlessSubscriptionParser.parse("$first\n$second\n$first")
        assertEquals(2, profiles.size)
        assertEquals("Первый", profiles.first().name)
        assertEquals("two.example", profiles[1].endpoint.server)
    }

    @Test fun parsesBase64AndSkipsInvalidEntries() {
        val encoded = Base64.getEncoder().encodeToString("$first\nnot-a-link\n$second".toByteArray())
        val profiles = VlessSubscriptionParser.parse(encoded)
        assertEquals(2, profiles.size)
        assertTrue(profiles.all { it.link.startsWith("vless://") })
    }
    @Test fun respectsLimitAndDecodesNames() {
        assertEquals(1, VlessSubscriptionParser.parse("$first\n$second", 1).size)
        assertEquals("Hello world", VlessSubscriptionParser.parse(first.substringBefore('#') + "#Hello%20world").single().name)
        assertTrue(VlessSubscriptionParser.parse("<html>not a subscription</html>").isEmpty())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsOversizedInput() {
        VlessSubscriptionParser.parse("x".repeat(512_001))
    }
}
