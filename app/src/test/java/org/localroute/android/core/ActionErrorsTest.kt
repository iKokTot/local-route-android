package org.localroute.android.core

import org.junit.Assert.*
import org.junit.Test

class ActionErrorsTest {
    @Test fun errorsAreUsefulWithoutLeakingCredentialOrUrl() {
        val secret="vless://private-key@example.test:443"
        for(method in listOf("importVless","saveBypassApps","startVless")) {
            assertFalse(ActionErrors.message(method,IllegalArgumentException(secret)).contains(secret))
            assertFalse(ActionErrors.message(method,java.io.IOException(secret)).contains(secret))
        }
        assertTrue(ActionErrors.message("importVless",IllegalArgumentException(secret)).contains("VLESS"))
        assertTrue(ActionErrors.message("importVless",java.net.SocketTimeoutException(secret)).contains("вовремя"))
        assertTrue(ActionErrors.message("saveBypassApps",IllegalStateException(secret)).contains("исключения"))
    }
}
