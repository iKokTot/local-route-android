/* Adapted boundary for the audited ByeDPI Android JNI reference. GPL-3.0-or-later. */
package io.github.dovecoteescapee.byedpi.core

class ByeDpiProxy {
    companion object { init { System.loadLibrary("byedpi") } }
    fun run(arguments: Array<String>): Int = jniStartProxy(arguments)
    fun close(): Int = jniStopProxy()
    private external fun jniStartProxy(args: Array<String>): Int
    private external fun jniStopProxy(): Int
}
