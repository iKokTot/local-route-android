package org.localroute.android.localdpi

import android.os.ParcelFileDescriptor
import org.localroute.android.core.DpiStrategy

/**
 * Boundary for an audited, locally-built ByeDPI + hev-socks5-tunnel integration.
 * No library is loaded until an actual native artifact is packaged, preventing a
 * fake VPN session or any remote fallback in this source-only MVP.
 */
interface NativeTunnelBridge {
    fun start(tun: ParcelFileDescriptor, strategy: DpiStrategy): Result<Unit>
    fun stop()
}

object UnavailableNativeTunnelBridge : NativeTunnelBridge {
    override fun start(tun: ParcelFileDescriptor, strategy: DpiStrategy): Result<Unit> = Result.failure(
        IllegalStateException("Локальный нативный движок не включён в эту сборку. См. licenses/NATIVE-INTEGRATION.md")
    )
    override fun stop() = Unit
}
