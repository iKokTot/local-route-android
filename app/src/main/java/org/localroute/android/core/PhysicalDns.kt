package org.localroute.android.core

/** Numeric unscoped DNS only: IPv6 link-local addresses cannot be used without an interface. */
object PhysicalDns {
    fun choose(addresses: List<String>): String = addresses.firstOrNull { it.matches(Regex("[0-9]+(\\.[0-9]+){3}")) }
        ?: addresses.firstOrNull { it.contains(':') && it.matches(Regex("[0-9a-fA-F:]+")) && !it.startsWith("fe80:", true) }
        ?: "1.1.1.1"
}
