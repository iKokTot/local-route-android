/* Adapted from ByeDPIAndroid v1.2.0. GPL-3.0-or-later. */
package io.github.dovecoteescapee.byedpi.core

class ByeDpiProxy {
    companion object { init { System.loadLibrary("byedpi") } }

    fun create(arguments: Array<String>): Int =
        jniCreateSocketWithCommandLine(arrayOf("ciadpi", *arguments))

    fun run(fd: Int): Int = jniStartProxy(fd)
    fun close(fd: Int): Int = jniStopProxy(fd)

    private external fun jniCreateSocketWithCommandLine(args: Array<String>): Int
    private external fun jniStartProxy(fd: Int): Int
    private external fun jniStopProxy(fd: Int): Int
}
