package org.localroute.android.tunnel

import org.localroute.android.core.CombinedRouting

/** Configuration for one Android TUN with independently controlled local and server routes. */
object CombinedConfigCompiler {
    fun compile(plan: CombinedRouting, endpoint: VlessEndpoint? = null, bootstrapDns: String = "1.1.1.1", gatewayPassword: String? = null, additionalLocalPackages: Set<String> = emptySet()): String {
        require(plan.requiresVpn) { "Оба режима выключены: VPN не нужен" }
        require(!plan.serverEnabled || endpoint != null) { "Для серверного режима нужна рабочая конфигурация" }
        require(Regex("[0-9a-fA-F:.]+").matches(bootstrapDns)) { "Нужен IP DNS-сервера" }
        // Standalone VLESS keeps the known pre-combined configuration, without
        // sniff/package rules that it does not need.
        if (!plan.dpiEnabled) return VlessConfigCompiler.compile(requireNotNull(endpoint), bootstrapDns, gatewayPassword)
        val outbounds = mutableListOf("""{"type":"direct","tag":"direct"}""")
        if (plan.serverEnabled) outbounds += VlessConfigCompiler.compileOutbound(requireNotNull(endpoint))
        if (plan.dpiEnabled) outbounds += """{"type":"socks","tag":"local-dpi","server":"127.0.0.1","server_port":1080,"version":"5","udp_over_tcp":false}"""
        val rules = mutableListOf("""{"port":53,"action":"hijack-dns"}""")
        if (plan.serverEnabled) rules += """{"action":"sniff"}"""
        val gateway = if (plan.serverEnabled && gatewayPassword != null) {
            require(Regex("[0-9a-f]{32}").matches(gatewayPassword))
            rules += """{"inbound":"tgws-gateway","outbound":"vless-out"}"""
            """,{"type":"socks","tag":"tgws-gateway","listen":"127.0.0.1","listen_port":1081,"users":[{"username":"localroute","password":"$gatewayPassword"}]}"""
        } else ""
        if (plan.dpiEnabled && plan.serverEnabled && plan.services.isNotEmpty()) {
            val packages = plan.services.map { it.packageName }.toSet() + additionalLocalPackages
            require(packages.all { Regex("[a-zA-Z0-9_.]+").matches(it) })
            rules += """{"package_name":[${packages.joinToString(",") { "\"$it\"" }}],"outbound":"local-dpi"}"""
            rules += """{"domain_suffix":[${plan.domains.joinToString(",") { "\"$it\"" }}],"outbound":"local-dpi"}"""
        }
        rules += """{"ip_is_private":true,"outbound":"direct"}"""
        // Standalone DPI must not depend on Android UID attribution or TLS SNI
        // sniffing (ECH / OEM differences). It remains entirely device-local.
        val final = if (plan.serverEnabled) "vless-out" else "local-dpi"
        val dnsServers = mutableListOf("""{"type":"udp","tag":"bootstrap-dns","server":"$bootstrapDns"}""")
        if (plan.serverEnabled) dnsServers += """{"type":"https","tag":"server-dns","server":"1.1.1.1","server_port":443,"path":"/dns-query","detour":"vless-out"}"""
        // Selected service DNS never silently traverses the user's VLESS server.
        val dnsRules = if (plan.dpiEnabled && plan.domains.isNotEmpty()) """{"domain_suffix":[${plan.domains.joinToString(",") { "\"$it\"" }}],"server":"bootstrap-dns"}""" else ""
        val dnsFinal = if (plan.serverEnabled) "server-dns" else "bootstrap-dns"
        return """{"log":{"level":"warn"},"dns":{"servers":[${dnsServers.joinToString(",")}],"rules":[$dnsRules],"final":"$dnsFinal"},"inbounds":[{"type":"tun","tag":"local-route-combined","address":["198.18.0.1/30","fdfe:dcba:9876::1/126"],"auto_route":true,"strict_route":true,"stack":"gvisor","mtu":1400}$gateway],"outbounds":[${outbounds.joinToString(",")}],"route":{"auto_detect_interface":true,"default_domain_resolver":"bootstrap-dns","rules":[${rules.joinToString(",")}],"final":"$final"}}"""
    }
}
