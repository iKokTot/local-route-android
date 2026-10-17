package org.localroute.android.tunnel

import org.junit.Assert.*
import org.junit.Test

class ServerConnectionProbeTest {
    @Test fun acceptsHttpsSuccessAndRedirectButNotErrorOrMalformedStatus() {
        assertTrue(ServerConnectionProbe.acceptsStatus("HTTP/1.1 200 OK"))
        assertTrue(ServerConnectionProbe.acceptsStatus("HTTP/1.0 204 No Content"))
        assertTrue(ServerConnectionProbe.acceptsStatus("HTTP/1.1 301 Moved Permanently"))
        assertFalse(ServerConnectionProbe.acceptsStatus("HTTP/1.1 403 Forbidden"))
        assertFalse(ServerConnectionProbe.acceptsStatus("HTTP/1.1 500 Error"))
        assertFalse(ServerConnectionProbe.acceptsStatus("not an HTTP response"))
        assertFalse(ServerConnectionProbe.acceptsStatus(null))
    }
}
