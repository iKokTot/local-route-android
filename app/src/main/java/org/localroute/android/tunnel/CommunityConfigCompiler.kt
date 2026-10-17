package org.localroute.android.tunnel

import org.localroute.android.core.CombinedRouting

/** Один TUN распределяет трафик по тем же приоритетам, что и десктоп. */
object CommunityConfigCompiler {
    fun compile(plan: CombinedRouting, endpoint: VlessEndpoint?, bootstrapDns: String, gatewayPassword: String?, additionalLocalPackages: Set<String>): String {
        require(plan.requiresVpn) { "Оба режима выключены: VPN не нужен" }
        require(!plan.serverEnabled || endpoint != null) { "Для серверного режима нужна рабочая конфигурация" }
        require(Regex("[0-9a-fA-F:.]+").matches(bootstrapDns)) { "Нужен IP DNS-сервера" }
        fun array(values: Set<String>) = values.joinToString(",", "[", "]", transform = VlessConfigCompiler::quote)
        val outbounds = mutableListOf("""{"type":"direct","tag":"direct"}""")
        if (plan.serverEnabled) outbounds += VlessConfigCompiler.compileOutbound(requireNotNull(endpoint))
        if (plan.dpiEnabled) outbounds += """{"type":"socks","tag":"local-dpi","server":"127.0.0.1","server_port":1080,"version":"5","udp_over_tcp":false}"""
        val rules = mutableListOf("""{"action":"sniff"}""", """{"protocol":"dns","action":"hijack-dns"}""")
        val gateway = if (plan.serverEnabled && gatewayPassword != null) {
            require(Regex("[0-9a-f]{32}").matches(gatewayPassword))
            rules += """{"inbound":"tgws-gateway","outbound":"vless-out"}"""
            """,{"type":"socks","tag":"tgws-gateway","listen":"127.0.0.1","listen_port":1081,"users":[{"username":"localroute","password":"$gatewayPassword"}]}"""
        } else ""
        rules += """{"ip_is_private":true,"outbound":"direct"}"""
        // Домен DPI имеет приоритет перед VPN-правилом целого приложения.
        if (plan.dpiEnabled && plan.domains.isNotEmpty()) rules += """{"domain_suffix":${array(plan.domains)},"outbound":"local-dpi"}"""
        if (plan.dpiEnabled && additionalLocalPackages.isNotEmpty()) rules += """{"package_name":${array(additionalLocalPackages)},"outbound":"local-dpi"}"""
        if (plan.serverEnabled && !plan.serverRoutes.allTraffic) {
            if (plan.serverRoutes.domains.isNotEmpty()) rules += """{"domain_suffix":${array(plan.serverRoutes.domains)},"outbound":"vless-out"}"""
            if (plan.serverRoutes.packages.isNotEmpty()) rules += """{"package_name":${array(plan.serverRoutes.packages)},"outbound":"vless-out"}"""
        }
        val final = if (plan.serverEnabled && plan.serverRoutes.allTraffic) "vless-out" else "direct"
        val dnsServers = mutableListOf("""{"type":"udp","tag":"bootstrap-dns","server":"$bootstrapDns"}""")
        if (plan.serverEnabled) dnsServers += """{"type":"https","tag":"server-dns","server":"1.1.1.1","server_port":443,"path":"/dns-query","detour":"vless-out"}"""
        val dnsRules = mutableListOf<String>()
        if (plan.dpiEnabled && plan.domains.isNotEmpty()) dnsRules += """{"domain_suffix":${array(plan.domains)},"server":"bootstrap-dns"}"""
        if (plan.serverEnabled && !plan.serverRoutes.allTraffic && plan.serverRoutes.domains.isNotEmpty()) dnsRules += """{"domain_suffix":${array(plan.serverRoutes.domains)},"server":"server-dns"}"""
        val dnsFinal = if (plan.serverEnabled && plan.serverRoutes.allTraffic) "server-dns" else "bootstrap-dns"
        return """{"log":{"level":"warn"},"dns":{"reverse_mapping":true,"servers":[${dnsServers.joinToString(",")}],"rules":[${dnsRules.joinToString(",")}],"final":"$dnsFinal"},"inbounds":[{"type":"tun","tag":"local-route-combined","address":["198.18.0.1/30","fdfe:dcba:9876::1/126"],"auto_route":true,"strict_route":true,"stack":"gvisor","mtu":1400}$gateway],"outbounds":[${outbounds.joinToString(",")}],"route":{"auto_detect_interface":true,"default_domain_resolver":"bootstrap-dns","rules":[${rules.joinToString(",")}],"final":"$final"}}"""
    }
}
