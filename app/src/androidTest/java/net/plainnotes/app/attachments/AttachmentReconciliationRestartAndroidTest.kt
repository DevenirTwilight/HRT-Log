package net.plainnotes.app.attachments

import android.app.Activity
import android.content.Intent
import android.os.Process
import android.provider.DocumentsContract
import androidx.activity.result.ActivityResult
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.plainnotes.app.attachments.ui.*
import net.plainnotes.app.debug.attachments.SyntheticAttachmentFixture
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Persist real COMMIT facts, drop the UI acknowledgement, force-stop, then query only. */
class AttachmentReconciliationRestartAndroidTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get()=app.getSharedPreferences("synthetic-security-restart",0)
    @Test fun prepareRealCommitWithoutAcknowledgement()=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("securityPhase")=="f02-prepare")
        val fixture=SyntheticAttachmentFixture(app)
        try {
            fixture.initialize()
            val gateway=RuntimeAttachmentGateway(app,fixture.runtime,fixture.target)
            val attempt=gateway.newImport("SYNTHETIC recovery")
            try {
                gateway.retain(attempt.operation,attempt.document)
                val uri=DocumentsContract.buildDocumentUri("net.plainnotes.app.test.synthetic-medflow","binary")
                attempt.selected(ActivityResult(Activity.RESULT_OK,Intent().setData(uri)))
                assertTrue(attempt.state.value is AttachmentFlowController.State.Saved)
                assertEquals(1,gateway.documents().size)
                assertTrue(prefs.edit().putString("fixture",fixture.id.toString()).putInt("pid",Process.myPid()).commit())
            }finally {attempt.close()}
        }finally {fixture.close()}
    }
    @Test fun freshProcessOnlyReconcilesOriginalCommit()=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("securityPhase")=="f02-verify")
        assertNotEquals(prefs.getInt("pid",-1),Process.myPid())
        val fixture=SyntheticAttachmentFixture(app,UUID.fromString(prefs.getString("fixture",null)))
        val jobs=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            fixture.initialize()
            val gateway=RuntimeAttachmentGateway(app,fixture.runtime,fixture.target)
            val pending=requireNotNull(gateway.pending())
            val before=app.contentResolver.call(android.net.Uri.parse("content://net.plainnotes.app.test.synthetic-medflow"),"openCount",null,null)!!.getInt("count")
            val c=MedicalAttachmentsController(gateway,jobs,Dispatchers.Unconfined)
            try {
                assertEquals(MedicalAttachmentsController.Import.Unknown,c.state.value.import)
                assertNull(c.startImport());c.confirmImport("must not dispatch")
                c.checkStatus()
                withTimeout(15000) {c.state.first {it.import is MedicalAttachmentsController.Import.Saved}}
                assertNull(gateway.pending())
                assertEquals(listOf(pending.second),gateway.documents().map {it.document})
                val after=app.contentResolver.call(android.net.Uri.parse("content://net.plainnotes.app.test.synthetic-medflow"),"openCount",null,null)!!.getInt("count")
                assertEquals("Read-only reconciliation must not reopen the original",before,after)
            }finally {c.close()}
        }finally {jobs.cancel();fixture.close();prefs.edit().clear().commit()}
    }
}
