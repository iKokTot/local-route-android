package org.localroute.android.core

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** App-private rotating snapshot; no network, no external-storage permission, no automatic export. */
object DiagnosticJournal {
    private val buffer=DiagnosticBuffer()
    private val mutable=MutableStateFlow<List<String>>(emptyList())
    val lines=mutable.asStateFlow()
    private var file: AtomicFile?=null
    private var pending=false
    private val writer=Executors.newSingleThreadScheduledExecutor { task -> Thread(task,"local-diagnostics").apply { isDaemon=true } }
    @Synchronized fun initialize(context: Context) {
        if(file != null)return
        val directory=File(context.applicationContext.filesDir,"diagnostics").apply { mkdirs() }
        file=AtomicFile(File(directory,"errors.txt"))
        runCatching {
            file!!.openRead().use { input ->
                val bytes=input.readBytesBounded(512_000)
                bytes.toString(Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }.forEach(buffer::add)
            }
        }
        mutable.value=buffer.snapshot()
    }
    @Synchronized fun record(source: String, message: String) {
        val time=OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        buffer.add("$time [$source] $message")
        mutable.value=buffer.snapshot()
        scheduleWrite()
    }
    @Synchronized fun clear() {
        buffer.clear(); mutable.value=emptyList()
        // Serialized with writes so an older pending snapshot cannot resurrect the log.
        file?.delete()
        scheduleWrite()
    }
    @Synchronized private fun scheduleWrite() {
        if(pending || file==null)return
        pending=true
        writer.schedule({ synchronized(this) {
            pending=false
            val target=file ?: return@synchronized
            var stream: java.io.FileOutputStream?=null
            try {
                stream=target.startWrite()
                stream.write(buffer.snapshot().joinToString("\n").toByteArray(Charsets.UTF_8))
                target.finishWrite(stream)
            } catch(_: Exception) { stream?.let(target::failWrite) }
        } },350,TimeUnit.MILLISECONDS)
    }
    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
        val output=java.io.ByteArrayOutputStream(); val chunk=ByteArray(8192)
        while(true) { val count=read(chunk); if(count<0)break; require(output.size()+count<=limit);output.write(chunk,0,count) }
        return output.toByteArray()
    }
}
