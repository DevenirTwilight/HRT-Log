package net.plainnotes.app.privacy

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Two actual application processes, coordinated by scripts/run_import_cleanup_restart.py. */
class ImportCleanupRestartAndroidTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="tm-import-12345678-1234-1234-1234-123456789abc.db"
    private fun directory()=File(context.noBackupFilesDir,"import")
    private fun waitForStartup() {
        val deadline=System.nanoTime()+20_000_000_000L
        while(TransMemoImportCopies.startupCleanupState==TransMemoImportCopies.CleanupState.WAITING_FOR_UNLOCK && System.nanoTime()<deadline)Thread.sleep(20)
        assertEquals("Application startup must complete own-file cleanup",TransMemoImportCopies.CleanupState.CLEAN,TransMemoImportCopies.startupCleanupState)
    }
    @Test fun prepare() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("cleanup_restart_phase")=="prepare")
        waitForStartup()
        directory().mkdirs()
        listOf("transmemo.db",name,"$name-wal","$name-shm","$name-journal").forEach{File(directory(),it).writeText("synthetic interrupted import")}
        File(directory(),"b2-unrelated-sentinel").writeText("keep synthetic sentinel")
        assertTrue(File(directory(),name).exists())
    }
    @Test fun verifyFreshProcess() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("cleanup_restart_phase")=="verify")
        waitForStartup()
        listOf("transmemo.db",name,"$name-wal","$name-shm","$name-journal").forEach{assertFalse("Owned residue must disappear after restart",File(directory(),it).exists())}
        val sentinel=File(directory(),"b2-unrelated-sentinel")
        assertEquals("keep synthetic sentinel",sentinel.readText());assertTrue(sentinel.delete())
    }
}
