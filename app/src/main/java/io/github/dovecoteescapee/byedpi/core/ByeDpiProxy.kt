/* Adapted boundary for the audited ByeDPI Android JNI reference. GPL-3.0-or-later. */
package io.github.dovecoteescapee.byedpi.core

class ByeDpiProxy {
    companion object { init { System.loadLibrary("byedpi") } }
    private var fd = -1
    fun open(arguments: Array<String>): Int { fd = jniCreateSocketWithCommandLine(arguments); return fd }
    fun run(): Int = jniStartProxy(fd)
    fun close(): Int = if (fd >= 0) jniStopProxy(fd).also { fd = -1 } else 0
    private external fun jniCreateSocketWithCommandLine(args: Array<String>): Int
    private external fun jniStartProxy(fd: Int): Int
    private external fun jniStopProxy(fd: Int): Int
}
