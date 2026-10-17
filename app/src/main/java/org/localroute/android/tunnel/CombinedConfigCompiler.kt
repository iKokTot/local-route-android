package org.localroute.android.tunnel

import org.localroute.android.core.CombinedRouting

/** Единая точка сборки маршрутов для Android. */
object CombinedConfigCompiler {
    fun compile(plan: CombinedRouting, endpoint: VlessEndpoint? = null, bootstrapDns: String = "1.1.1.1", gatewayPassword: String? = null, additionalLocalPackages: Set<String> = emptySet()): String =
        CommunityConfigCompiler.compile(plan, endpoint, bootstrapDns, gatewayPassword, additionalLocalPackages)
}
