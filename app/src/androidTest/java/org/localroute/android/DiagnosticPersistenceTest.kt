package org.localroute.android

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.localroute.android.core.DiagnosticJournal

/** Run writeFixture, force-stop this isolated test app, then run readFixture. */
class DiagnosticPersistenceTest {
    @Test fun writeFixture() {
        DiagnosticJournal.record("TEST","diagnostic-restart-check token=PRIVATE_RESTART_TOKEN")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=java.io.File(context.filesDir,"diagnostics/errors.txt")
        val deadline=android.os.SystemClock.uptimeMillis()+5000
        while(android.os.SystemClock.uptimeMillis()<deadline && (!file.exists() || !file.readText().contains("diagnostic-restart-check")))Thread.sleep(100)
        assertTrue(file.readText().contains("diagnostic-restart-check"))
        assertFalse(file.readText().contains("PRIVATE_RESTART_TOKEN"))
    }
    @Test fun readFixture() {
        assertTrue(DiagnosticJournal.lines.value.any { it.contains("diagnostic-restart-check") })
        assertFalse(DiagnosticJournal.lines.value.any { it.contains("PRIVATE_RESTART_TOKEN") })
    }
}
