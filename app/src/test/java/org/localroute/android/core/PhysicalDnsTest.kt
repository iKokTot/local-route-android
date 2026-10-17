package org.localroute.android.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PhysicalDnsTest {
    @Test fun scopedIpv6DoesNotBreakEitherVpnMode() = assertEquals("192.168.1.1", PhysicalDns.choose(listOf("fe80::1%wlan0", "192.168.1.1")))
    @Test fun globalIpv6IsAccepted() = assertEquals("2001:4860:4860::8888", PhysicalDns.choose(listOf("2001:4860:4860::8888")))
    @Test fun unusableDnsHasNumericFallback() = assertEquals("1.1.1.1", PhysicalDns.choose(listOf("fe80::1", "not-an-ip")))
}
