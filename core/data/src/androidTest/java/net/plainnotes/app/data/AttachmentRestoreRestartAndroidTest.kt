package net.plainnotes.app.data

import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch

/** Synthetic fixed test namespace only. Runner kills an actually live separate process. */
class AttachmentRestoreRestartAndroidTest {
    @Test fun quarantineNeverActivatesAfterActualProcessDeath() {
        val args=InstrumentationRegistry.getArguments();val phase=args.getString("restore_phase")
        assumeTrue(phase in listOf("prepare","verify"))
        val seam=requireNotNull(args.getString("restore_seam"));require(seam in setOf("COPY","VERIFIED"))
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(app.noBackupFilesDir,"synthetic-restore-restart-$seam")
        val name="synthetic-restore-restart-$seam.db"
        val marker=File(app.filesDir,"synthetic-restore-restart-ready")
        if(phase=="prepare") {
            app.deleteDatabase(name);directory.deleteRecursively();check(directory.mkdir());marker.delete()
            val f=AttachmentRestoreStagingAndroidTest.Fixture(app,name,directory)
            val a=RestoreSyntheticArchive();var reads=0
            fun ready() {
                f.safety()
                marker.writeText(JSONObject().put("seam",seam).put("pid",Process.myPid()).put("dataset",f.scope.dataset).toString())
                check(!CountDownLatch(1).await(90,java.util.concurrent.TimeUnit.SECONDS))
                error("Runner failed to kill live restore seam")
            }
            val receipt=f.store().stage(f.scope,f.session.issue(),a.plan(),a.source {reads++;if(seam=="COPY" && reads==2)ready()})
            assertFalse(receipt.activatable);if(seam=="VERIFIED")ready()
            error("Restore seam not reached")
        } else {
            val previous=JSONObject(marker.readText());assertEquals(seam,previous.getString("seam"));assertNotEquals(Process.myPid(),previous.getInt("pid"))
            AttachmentRestoreStagingAndroidTest.Fixture(app,name,directory,reopen=true).use {f->
                assertEquals(previous.getString("dataset"),f.scope.dataset);f.safety()
                val first=f.store().retained(f.scope,f.session.issue())
                assertTrue(first.entries.isNotEmpty());assertTrue(first.entries.all {it.privateRegular})
                assertEquals(first.entries.toSet(),f.store().retained(f.scope,f.session.issue()).entries.toSet())
                f.safety() // no journal, version, association, namespace or old clinical-row change
            }
            marker.delete()
        }
    }
}
