/* Adapted from ByeDPIAndroid v1.2.0. GPL-3.0-or-later. */
package io.github.dovecoteescapee.byedpi.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ByeDpiProxy {
    companion object { init { System.loadLibrary("byedpi") } }

    private val mutex = Mutex()
    private var fd = -1

    /**
     * This is deliberately the same two-stage lifecycle as ByeDPIAndroid:
     * create the listener once, then keep its native event loop alive until
     * the VPN service explicitly stops it.  Do not restart it for probes.
     */
    suspend fun startProxy(arguments: Array<String>): Int {
        val listener = mutex.withLock {
            check(fd < 0) { "ByeDPI уже запущен" }
            jniCreateSocketWithCommandLine(arrayOf("ciadpi", *arguments)).also { fd = it }
        }
        if (listener < 0) return -1
        return jniStartProxy(listener)
    }

    suspend fun stopProxy(): Int = mutex.withLock {
        if (fd < 0) return@withLock 0
        val result = jniStopProxy(fd)
        if (result == 0) fd = -1
        result
    }

    private external fun jniCreateSocketWithCommandLine(args: Array<String>): Int
    private external fun jniStartProxy(fd: Int): Int
    private external fun jniStopProxy(fd: Int): Int
}
