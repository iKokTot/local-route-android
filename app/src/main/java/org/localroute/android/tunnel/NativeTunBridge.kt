package org.localroute.android.tunnel

import io.github.dovecoteescapee.byedpi.core.TProxyService

/** HEV is process-global. A revoked service must never stop its replacement. */
object NativeTunBridge {
    private var owner: Any? = null
    @Synchronized fun start(token: Any, path: String, fd: Int) {
        if(owner!=null) TProxyService.TProxyStopService()
        owner=null
        TProxyService.TProxyStartService(path,fd)
        owner=token
    }
    @Synchronized fun stop(token: Any) {
        if(owner!==token) return
        TProxyService.TProxyStopService()
        owner=null
    }
}
