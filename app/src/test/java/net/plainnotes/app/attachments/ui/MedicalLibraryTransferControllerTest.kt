package net.plainnotes.app.attachments.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.*
import net.plainnotes.app.attachments.MedicalLibraryGateway
import net.plainnotes.app.data.MedicalLibraryTransfer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import net.plainnotes.app.attachments.ui.MedicalLibraryTransferController.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class MedicalLibraryTransferControllerTest {
    private class Fake:MedicalLibraryGateway {
        val operation=UUID.randomUUID();var reads=0;var restores=0;var exports=0;var checks=0;var aborts=0
        var hidden:(()->Unit)?=null;var revoked:(()->Unit)?=null
        var afterCommit:(()->Unit)?=null;var failAfterCommit=false;var status:MedicalLibraryTransfer.Status?=null
        override var committedReceipt:MedicalLibraryTransfer.Committed?=null
        override suspend fun export(uri:Uri,password:CharArray){exports++}
        override suspend fun prepare(uri:Uri,password:CharArray):MedicalLibraryGateway.Prepared {reads++;return MedicalLibraryGateway.Prepared(operation,MedicalLibraryTransfer.Summary(2,2,3))}
        override suspend fun restore():MedicalLibraryTransfer.Committed {restores++;val r=MedicalLibraryTransfer.Committed(operation,UUID.randomUUID(),2);committedReceipt=r;afterCommit?.invoke();if(failAfterCommit)error("late reply");return r}
        override suspend fun status(operation:UUID):MedicalLibraryTransfer.Status? {checks++;return status}
        override suspend fun abortPending(operation:UUID):Boolean {aborts++;return true}
        override fun cancel(){}
        override fun close(){}
        override fun onHidden(listener:()->Unit):AutoCloseable {hidden=listener;return AutoCloseable {hidden=null}}
        override fun onRevoked(listener:()->Unit):AutoCloseable {revoked=listener;return AutoCloseable{revoked=null}}
    }
    private fun c(fake:Fake,saved:SavedStateHandle=SavedStateHandle())=MedicalLibraryTransferController(fake,CoroutineScope(Dispatchers.Unconfined),saved)
    private val picked=ActivityResult(Activity.RESULT_OK,Intent().setData(Uri.parse("content://synthetic/archive")))
    @Test fun selectionAndCancellationNeverWriteAndAuthenticationRequiresConfirmation() {
        val f=Fake();val c=c(f)
        c.choose(Mode.RESTORE);assertNull(c.choose(Mode.EXPORT));c.picked(picked)
        assertEquals(0,f.reads);c.cancel();assertEquals(0,f.restores)
        c.choose(Mode.RESTORE);c.picked(picked)
        val password="SYNTHETIC password".toCharArray();c.password(password)
        assertTrue(password.all{it=='\u0000'});assertEquals(1,f.reads);assertEquals(0,f.restores);assertEquals(Phase.REVIEW,c.state.value.phase)
        c.confirmRestore();c.confirmRestore();assertEquals(1,f.restores);assertEquals(Phase.RESTORED,c.state.value.phase);c.close()
    }
    @Test fun commitSurvivesRevocationAndLostReplyWithoutAnotherRestore() {
        val f=Fake();val saved=SavedStateHandle();val c=c(f,saved)
        c.choose(Mode.RESTORE);c.picked(picked);c.password("SYNTHETIC password".toCharArray())
        f.afterCommit={f.revoked?.invoke()};f.failAfterCommit=true
        c.confirmRestore();assertEquals(Phase.RESTORED,c.state.value.phase);assertTrue(c.state.value.revoked)
        assertEquals(f.operation.toString(),saved.get<String>(MedicalLibraryTransferController.OPERATION));assertEquals(1,f.restores);c.close()
        val reopened=c(f,saved);assertEquals(Phase.UNKNOWN,reopened.state.value.phase)
        f.status=MedicalLibraryTransfer.Status("COMMITTED",UUID.randomUUID(),true)
        reopened.checkStatus();assertEquals(1,f.checks);assertEquals(1,f.restores);assertEquals(Phase.CHECKED,reopened.state.value.phase);reopened.close()
    }
    @Test fun unknownOnlyChecksAndExplicitAbortRequiresVerifiedPendingJournal() {
        val f=Fake();val saved=SavedStateHandle(mapOf(MedicalLibraryTransferController.OPERATION to f.operation.toString()));val c=c(f,saved)
        c.confirmRestore();c.abortPending();assertNull(c.choose(Mode.RESTORE));assertEquals(0,f.aborts);assertEquals(0,f.restores)
        f.status=MedicalLibraryTransfer.Status("READY",UUID.randomUUID(),false);c.checkStatus();c.abortPending()
        assertEquals(1,f.aborts);assertEquals(0,f.restores);assertEquals(Phase.CANCELLED,c.state.value.phase);c.close()
    }
    @Test fun pauseDiscardsPasswordSelectionButPickerReturnDoesNotGrantAccessAfterRevocation() {
        val f=Fake();val c=c(f)
        c.choose(Mode.RESTORE);c.picked(picked);f.hidden?.invoke()
        val password="SYNTHETIC password".toCharArray();c.password(password);assertTrue(password.all{it=='\u0000'});assertEquals(0,f.reads)
        c.choose(Mode.RESTORE);f.revoked?.invoke();c.picked(picked);c.password("SYNTHETIC password".toCharArray())
        assertEquals(0,f.reads);assertEquals(Phase.REVOKED,c.state.value.phase);c.close()
    }
    @Test fun cancelledScopeBeforeDispatchStillErasesOwnedPassword() {
        val f=Fake();val job=SupervisorJob();job.cancel()
        val c=MedicalLibraryTransferController(f,CoroutineScope(job+Dispatchers.Unconfined),SavedStateHandle())
        c.choose(Mode.RESTORE);c.picked(picked)
        val password="SYNTHETIC password".toCharArray();c.password(password)
        assertTrue(password.all{it=='\u0000'});assertEquals(0,f.reads);assertEquals(Phase.CANCELLED,c.state.value.phase);c.close()
    }

}
