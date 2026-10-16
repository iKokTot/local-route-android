package org.localroute.android.tunnel

/** Builds the small, audited sing-box configuration used by VlessVpnService. */
object VlessConfigCompiler {
    fun compile(endpoint: VlessEndpoint): String {
        val transport = when (endpoint.transport) {
            VlessTransport.TCP -> ""
            VlessTransport.WEBSOCKET -> ",\"transport\":{\"type\":\"ws\",\"path\":${quote(endpoint.path ?: "/")}" +
                (endpoint.host?.let { ",\"headers\":{\"Host\":${quote(it)}}" } ?: "") + "}"
            VlessTransport.GRPC -> ",\"transport\":{\"type\":\"grpc\",\"service_name\":${quote(endpoint.serviceName.orEmpty())}}"
            VlessTransport.HTTP_UPGRADE -> ",\"transport\":{\"type\":\"httpupgrade\",\"host\":${quote(endpoint.host.orEmpty())},\"path\":${quote(endpoint.path ?: "/")}}"
        }
        val tls = if (endpoint.security == VlessSecurity.NONE) "" else {
            val reality = if (endpoint.security == VlessSecurity.REALITY) {
                ",\"reality\":{\"enabled\":true,\"public_key\":${quote(endpoint.realityPublicKey.orEmpty())},\"short_id\":${quote(endpoint.realityShortId.orEmpty())}}"
            } else ""
            ",\"tls\":{\"enabled\":true,\"server_name\":${quote(endpoint.sni ?: endpoint.server)},\"insecure\":${endpoint.allowInsecure}$reality}"
        }
        val flow = endpoint.flow?.let { ",\"flow\":${quote(it)}" } ?: ""
        return """{"log":{"level":"warn","timestamp":true},"inbounds":[{"type":"tun","tag":"local-route-vless","address":["198.18.0.1/30"],"auto_route":true,"strict_route":true,"stack":"mixed","mtu":1400}],"outbounds":[{"type":"vless","tag":"vless-out","server":${quote(endpoint.server)},"server_port":${endpoint.port},"uuid":${quote(endpoint.uuid)}$flow$tls$transport},{"type":"direct","tag":"direct"}],"route":{"auto_detect_interface":true,"rules":[{"protocol":"dns","action":"hijack-dns"},{"ip_is_private":true,"outbound":"direct"}],"final":"vless-out"}}"""
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }
}
