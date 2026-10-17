package org.localroute.android.tunnel

import org.junit.Assert.*
import org.junit.Test

class VpnDiagnosticsTest {
    @Test fun keysAndConnectionUrisAreRedacted() {
        val uuid = "00000000-0000-4000-8000-000000000000"
        val text = VpnDiagnostics.redact("vless://$uuid@example.com:443?secret=x uuid=$uuid socks5://user:password@127.0.0.1")
        assertFalse(text.contains(uuid)); assertFalse(text.contains("secret=x")); assertFalse(text.contains("password"))
    }
}
