package org.localroute.networkprobe

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL

/** Test-only separate process: requests can succeed only through the VLESS fixture. */
class ProbeProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        System.setProperty("http.keepAlive", "false")
        return true
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        // ConnectivityManager must resolve this provider's UID, not the calling
        // instrumentation app (which is deliberately excluded from the VPN).
        val identity = android.os.Binder.clearCallingIdentity()
        try {
        val result = runCatching {
            val connectivity = requireNotNull(context).getSystemService(android.net.ConnectivityManager::class.java)
            if (uri.getQueryParameter("vpnstatus") == "1") {
                val status = if (connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true) "VPN" else "DIRECT"
                return MatrixCursor(arrayOf("result")).apply { addRow(arrayOf(status)) }
            }
            if (uri.getQueryParameter("waitvpn") == "1") {
                val deadline = android.os.SystemClock.elapsedRealtime() + 5000
                while (connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) != true) {
                    check(android.os.SystemClock.elapsedRealtime() < deadline) { "Probe UID has no active VPN" }
                    android.os.SystemClock.sleep(50)
                }
            }
            if (uri.getQueryParameter("localtcp") == "1") {
                val local = requireNotNull(connectivity.activeNetwork).openConnection(URL("http://10.0.2.2:18445/")) as HttpURLConnection
                try {
                    local.connectTimeout = 5000; local.readTimeout = 5000
                    local.setRequestProperty("Connection", "close")
                    return MatrixCursor(arrayOf("result")).apply { addRow(arrayOf(local.inputStream.bufferedReader().use { it.readText() })) }
                } finally { local.disconnect() }
            }
            if (uri.getQueryParameter("localudp") == "1") {
                val payload = "local-route-udp".toByteArray()
                java.net.DatagramSocket().use { socket ->
                    requireNotNull(connectivity.activeNetwork).bindSocket(socket)
                    socket.soTimeout = 5000
                    socket.send(java.net.DatagramPacket(payload, payload.size, java.net.InetAddress.getByName("10.0.2.2"), 18446))
                    // ByeDPI intentionally sends fake UDP packets; an echo fixture returns them too.
                    // Require the real payload, not merely any received datagram.
                    val deadline = android.os.SystemClock.elapsedRealtime() + 5000
                    while (android.os.SystemClock.elapsedRealtime() < deadline) {
                        val received = java.net.DatagramPacket(ByteArray(256), 256)
                        socket.receive(received)
                        val response = String(received.data, 0, received.length)
                        if (response == String(payload)) return MatrixCursor(arrayOf("result")).apply { addRow(arrayOf(response)) }
                    }
                    error("Real UDP payload was not returned")
                }
            }
            val host = if (uri.getQueryParameter("dns") == "1") "example.com" else "93.184.216.34"
            val url = if (uri.getQueryParameter("normal") == "1") "https://example.com/" else "http://$host:18089/route-check"
            // This provider survives multiple TUN replacements. Bind each fresh request to
            // its UID's current network, not an HTTP client's cached former default.
            val connection = requireNotNull(connectivity.activeNetwork).openConnection(URL(url)) as HttpURLConnection
            try {
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("Connection", "close")
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally { connection.disconnect() }
        }.getOrElse { android.util.Log.e("LocalRouteProbe", "Probe failed: ${it.javaClass.name}: ${it.message}", it); "DIRECT_UNREACHABLE" }
        return MatrixCursor(arrayOf("result")).apply { addRow(arrayOf(result)) }
        } finally { android.os.Binder.restoreCallingIdentity(identity) }
    }
    override fun getType(uri: Uri) = "text/plain"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
