package org.localroute.android

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.localroute.android.openflux.*
import java.io.File
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OpenFluxIntegrationTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    @Test fun nativeParserAndEncryptedLocalExitWork() = runBlocking {
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Thread.sleep(1500)
        val fixture=ProcessBuilder(File(instrumentation.context.applicationInfo.nativeLibraryDir,"libopenflux_fixture.so").absolutePath)
            .redirectError(File("/dev/null")).start()
        val executor=Executors.newSingleThreadExecutor()
        var client: Process?=null
        try {
            val link=executor.submit<String> { fixture.inputStream.bufferedReader().readLine() }.get(20,TimeUnit.SECONDS)
            assertTrue(link.startsWith("openflux://v1/"))
            val store=OpenFluxStore(context)
            store.save(link)
            assertEquals(link,store.link())
            for(bad in listOf("vless://bad","openflux://v1/invalid","openflux://v1/"+"a".repeat(33000))) {
                assertTrue(runCatching { store.save(bad) }.isFailure)
                assertEquals(link,store.link())
            }
            client=ProcessBuilder(File(context.applicationInfo.nativeLibraryDir,"libopenflux.so").absolutePath).redirectError(File("/dev/null")).start()
            client.outputStream.bufferedWriter().use { it.write(link);it.newLine() }
            val reader=client.inputStream.bufferedReader()
            executor.submit<Boolean> { var connected=false;while(!connected){ val line=reader.readLine() ?: error("client stopped");connected=org.json.JSONObject(line).optString("event")=="connected" };true }.get(30,TimeUnit.SECONDS)
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1",1082),3000);socket.soTimeout=15000
                val out=socket.getOutputStream();val input=DataInputStream(socket.getInputStream())
                out.write(byteArrayOf(5,1,0));out.flush()
                assertEquals(5,input.readUnsignedByte());assertEquals(0,input.readUnsignedByte())
                out.write(byteArrayOf(5,1,0,1,10,0,2,16,0x46,0xa1.toByte()));out.flush()
                assertEquals(5,input.readUnsignedByte());assertEquals(0,input.readUnsignedByte())
                input.readUnsignedByte();val type=input.readUnsignedByte()
                val bytes=when(type){1->4;4->16;3->input.readUnsignedByte();else->error("SOCKS address")}
                input.readFully(ByteArray(bytes+2))
                out.write("GET / HTTP/1.0\r\nHost: localhost\r\n\r\n".toByteArray());out.flush()
                val response=input.bufferedReader().readText()
                assertTrue(response.contains("local-route-openflux-fixture"))
            }
            client.destroyForcibly();client.waitFor(5,TimeUnit.SECONDS);client=null
            assertNull("Test APK must have owner-granted VPN consent",android.net.VpnService.prepare(context))
            ContextCompat.startForegroundService(context,Intent(context,OpenFluxVpnService::class.java).setAction(OpenFluxVpnService.START))
            val deadline=System.currentTimeMillis()+30000
            while(OpenFluxRuntime.phase!="connected" && System.currentTimeMillis()<deadline) Thread.sleep(100)
            assertEquals("connected",OpenFluxRuntime.phase)
            if(BuildConfig.DEBUG) {
                val uri=android.net.Uri.parse("content://org.localroute.networkprobe/check?waitvpn=1&openflux=1")
                val response=context.contentResolver.query(uri,null,null,null,null).use { cursor -> requireNotNull(cursor);check(cursor.moveToFirst());cursor.getString(0) }
                assertEquals("local-route-openflux-tun-fixture",response)
                val udp=context.contentResolver.query(android.net.Uri.parse("content://org.localroute.networkprobe/check?waitvpn=1&openfluxudp=1"),null,null,null,null).use { cursor -> requireNotNull(cursor);check(cursor.moveToFirst());cursor.getString(0) }
                assertEquals("local-route-udp",udp)
            }
            context.startService(Intent(context,OpenFluxVpnService::class.java).setAction(OpenFluxVpnService.STOP))
            Thread.sleep(1000)
            assertEquals("stopped",OpenFluxRuntime.phase)
        } finally {
            context.startService(Intent(context,OpenFluxVpnService::class.java).setAction(OpenFluxVpnService.STOP))
            client?.destroyForcibly();fixture.destroyForcibly();executor.shutdownNow()
            context.getSharedPreferences("local_route_openflux",0).edit().clear().commit()
        }
    }
}
