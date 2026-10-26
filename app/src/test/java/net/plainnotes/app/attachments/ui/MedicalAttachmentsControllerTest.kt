package net.plainnotes.app.attachments.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.ActivityResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.attachments.AttachmentFlowController
import net.plainnotes.app.attachments.AttachmentViewer
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.*
import net.plainnotes.app.data.MedicalAttachmentOperations
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** MED-02 attachment UI state machine (REQUIREMENTS §149) against a scripted gateway: no backend, no real files. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class MedicalAttachmentsControllerTest {
    private val target=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.APPOINTMENT,7)
    private fun doc(title:String,mime:String,state:String="ACTIVE",link:String="ACTIVE")=
        MedicalAttachmentOperations.Document(UUID.randomUUID(),1,title,mime,state,link)
    private val picked=ActivityResult(Activity.RESULT_OK,Intent().setData(Uri.parse("content://synthetic/doc")))

    private class Fake(override val target:MedicalAttachmentOperations.Target):MedicalAttachmentGateway {
        var docs=mutableListOf<MedicalAttachmentOperations.Document>()
        var listFails=false
        var outcome:AttachmentFlowController.State=AttachmentFlowController.State.Saved(MedicalAttachmentOperations.Receipt(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1))
        var reconcileOutcome:AttachmentFlowController.State=AttachmentFlowController.State.ReconcileRequired
        var onSaved:(()->Unit)?=null
        var previewFailure:Exception?=null
        var publishFailure:Exception?=null
        val imports=mutableListOf<Imp>()
        val reconciles=mutableListOf<Pair<UUID,UUID>>()
        val previews=mutableListOf<Prev>()
        override var scopeEnded=false
        var beforeImage:(()->Unit)?=null
        var hide:(()->Unit)?=null
        override fun onHidden(listener:()->Unit):AutoCloseable {hide=listener;return AutoCloseable{hide=null}}
        var listener:(()->Unit)?=null
        var unregistered=false
        override suspend fun documents():List<MedicalAttachmentOperations.Document> {if(listFails)error("list");return docs.toList()}
        override suspend fun pickerIntent()=Intent(Intent.ACTION_OPEN_DOCUMENT)
        override suspend fun preview(document:UUID):MedicalAttachmentGateway.Preview=Prev(previewFailure,beforeImage,publishFailure).also {previews+=it}
        override suspend fun newImport(title:String)=Imp(this,title).also {imports+=it}
        override suspend fun reconcile(operation:UUID,document:UUID):AttachmentFlowController.State {reconciles+=operation to document;return reconcileOutcome}
        override fun onRevoked(listener:()->Unit):AutoCloseable {this.listener=listener;return AutoCloseable {unregistered=true;listener()}}
        fun revoke()=requireNotNull(listener)()
    }
    private class Imp(val fake:Fake,val title:String):MedicalAttachmentGateway.Import {
        override val operation:UUID=UUID.randomUUID()
        override val document:UUID=UUID.randomUUID()
        override val state=MutableStateFlow<AttachmentFlowController.State>(AttachmentFlowController.State.Selecting)
        var selections=0;var cancelled=false;var closed=false
        override suspend fun selected(result:ActivityResult) {selections++;state.value=fake.outcome;fake.onSaved?.invoke()}
        override fun cancel() {cancelled=true}
        override fun close() {closed=true}
    }
    private class Prev(val failure:Exception?,val beforeImage:(()->Unit)?=null,val publishFailure:Exception?=null):MedicalAttachmentGateway.Preview {
        val pages=mutableListOf<Bitmap>();var closed=false
        private fun page()=failure?.let {throw it} ?: Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).also {pages+=it}
        override fun <T> publish(block:()->T):T {publishFailure?.let{throw it};return block()}
        override fun pdfFirstPage()=page()
        override fun image():Bitmap {beforeImage?.invoke();return page()}
        override fun release(bitmap:Bitmap) {bitmap.recycle()}
        override fun close() {closed=true;pages.forEach {it.recycle()}}
    }
    /** Unconfined: every launched step runs to completion before the call returns (no test-dispatcher dependency). */
    private fun controller(fake:Fake)=MedicalAttachmentsController(fake,CoroutineScope(Dispatchers.Unconfined),Dispatchers.Unconfined)

    @Test fun listShowsKindAndAvailability()=runBlocking {
        val fake=Fake(target).apply {docs+=doc("Report","application/pdf");docs+=doc("Photo","image/png");docs+=doc("Raw","application/octet-stream");docs+=doc("Old","image/jpeg",state="TRASHED")}
        val c=controller(fake);c.refresh()
        val items=(c.state.value.files as Files.Ready).items
        assertEquals(listOf(FileKind.PDF,FileKind.IMAGE,FileKind.OTHER,FileKind.IMAGE),items.map {it.kind})
        assertEquals(listOf(true,true,true,false),items.map {it.available})
        c.close()
    }

    @Test fun choosingCreatesNoOperationAndCancelLeavesNothingPending()=runBlocking {
        val fake=Fake(target);val c=controller(fake)
        assertNotNull(c.startImport());assertNull("Only one picker may be pending",c.startImport());c.picked(picked,"Visit document")
        assertEquals(Import.Confirming("Visit document"),c.state.value.import)
        c.resume() // An ordinary onResume (back from the picker) keeps the pending confirmation.
        assertEquals(Import.Confirming("Visit document"),c.state.value.import)
        assertTrue("Choosing must not create an operation",fake.imports.isEmpty())
        c.cancelImport()
        assertEquals(Import.Cancelled,c.state.value.import);assertTrue(fake.imports.isEmpty())
        // The old choice cannot be confirmed afterwards.
        c.confirmImport("Visit document");assertTrue(fake.imports.isEmpty())
        // Picker back/cancel: no file, no operation.
        c.startImport();c.picked(ActivityResult(Activity.RESULT_CANCELED,null),"x")
        assertEquals(Import.Cancelled,c.state.value.import);assertTrue(fake.imports.isEmpty())
        c.close()
    }

    @Test fun savedOnlyFromRealCommitWithTheConfirmedName()=runBlocking {
        val fake=Fake(target);fake.onSaved={fake.docs+=doc("My report","application/pdf")}
        val c=controller(fake);c.refresh()
        var during:Import?=null
        fake.onSaved={during=c.state.value.import;fake.docs+=doc("My report","application/pdf")}
        c.startImport();c.picked(picked,"Visit document");c.confirmImport("My report")
        assertEquals("Saving is shown while the backend works",Import.Saving,during)
        
        assertEquals(Import.Saved(listStale=false),c.state.value.import)
        assertEquals("My report",fake.imports.single().title);assertEquals(1,fake.imports.single().selections);assertTrue(fake.imports.single().closed)
        assertEquals(listOf("My report"),(c.state.value.files as Files.Ready).items.map {it.title})
        c.close()
    }

    @Test fun refreshFailureAfterCommitStillReportsSaved()=runBlocking {
        val fake=Fake(target);val c=controller(fake);c.refresh()
        fake.onSaved={fake.listFails=true}
        c.startImport();c.picked(picked,"t");c.confirmImport("t")
        assertEquals(Import.Saved(listStale=true),c.state.value.import)
        c.close()
    }

    @Test fun failedAndUnknownOutcomes()=runBlocking {
        val fake=Fake(target);val c=controller(fake)
        fake.outcome=AttachmentFlowController.State.Failed
        c.startImport();c.picked(picked,"t");c.confirmImport("t")
        assertEquals(Import.NotSaved,c.state.value.import)
        // Unknown: only a read-only check with the SAME identities, never another selection/write.
        fake.outcome=AttachmentFlowController.State.ReconcileRequired
        c.startImport();c.picked(picked,"t");c.confirmImport("t")
        assertEquals(Import.Unknown,c.state.value.import)
        val attempt=fake.imports.last()
        c.checkStatus()
        assertEquals(listOf(attempt.operation to attempt.document),fake.reconciles);assertEquals(Import.Unknown,c.state.value.import)
        fake.reconcileOutcome=AttachmentFlowController.State.Saved(MedicalAttachmentOperations.Receipt(attempt.operation,attempt.document,UUID.randomUUID(),1))
        c.checkStatus()
        assertTrue(c.state.value.import is Import.Saved)
        assertEquals(2,fake.imports.size);assertTrue(fake.imports.all {it.selections==1})
        c.close()
    }

    @Test fun viewerShowsThenReleasesOnClose()=runBlocking {
        val fake=Fake(target).apply {docs+=doc("Photo","image/png")};val c=controller(fake);c.refresh()
        val item=(c.state.value.files as Files.Ready).items.single()
        c.open(item)
        val showing=c.state.value.viewer as Viewer.Showing;assertFalse(showing.pdf)
        c.closeViewer()
        assertEquals(Viewer.Closed,c.state.value.viewer);assertTrue(showing.page.isRecycled);assertTrue(fake.previews.single().closed)
        c.close()
    }

    @Test fun viewerErrorsCloseResourcesAndExplain()=runBlocking {
        val fake=Fake(target).apply {docs+=doc("Scan","application/pdf");docs+=doc("Raw","application/octet-stream")}
        val c=controller(fake);c.refresh()
        val (pdf,raw)=(c.state.value.files as Files.Ready).items
        fake.previewFailure=AttachmentViewer.PdfPreviewUnsupported()
        c.open(pdf)
        assertEquals(Viewer.Message("Scan",Reason.PDF_NEEDS_ANDROID_9),c.state.value.viewer);assertTrue(fake.previews.last().closed)
        fake.previewFailure=IllegalArgumentException("decode")
        c.open(pdf)
        assertEquals(Viewer.Message("Scan",Reason.FAILED),c.state.value.viewer);assertTrue(fake.previews.last().closed)
        val opened=fake.previews.size
        c.open(raw)
        assertEquals(Viewer.Message("Raw",Reason.NO_PREVIEW_FOR_TYPE),c.state.value.viewer);assertEquals("No read for an opaque file",opened,fake.previews.size)
        c.close()
    }

    @Test fun revocationDropsPageListAndChoiceBeforeAnythingElse()=runBlocking {
        val fake=Fake(target).apply {docs+=doc("Photo","image/jpeg")};val c=controller(fake);c.refresh()
        c.open((c.state.value.files as Files.Ready).items.single())
        val page=(c.state.value.viewer as Viewer.Showing).page
        c.startImport();c.picked(picked,"t")
        fake.revoke()
        val s=c.state.value
        assertTrue(s.revoked);assertEquals(Viewer.Closed,s.viewer);assertEquals(Files.Loading,s.files);assertEquals(Import.Idle,s.import)
        assertTrue(page.isRecycled);assertTrue(fake.previews.single().closed)
        // The revoked choice cannot be confirmed, and no viewer opens while revoked.
        c.confirmImport("t");c.open(MedicalAttachmentsController.Item(UUID.randomUUID(),"x",FileKind.IMAGE,true))
        assertTrue(fake.imports.isEmpty());assertEquals(1,fake.previews.size)
        // Unlock shows the list again, never the old preview.
        c.resume()
        assertFalse(c.state.value.revoked);assertEquals(Viewer.Closed,c.state.value.viewer);assertTrue(c.state.value.files is Files.Ready)
        c.close()
    }

    @Test fun revocationDuringSaveNeedsAReadOnlyCheck()=runBlocking {
        val fake=Fake(target);val c=controller(fake)
        fake.onSaved={fake.revoke()};fake.outcome=AttachmentFlowController.State.Closed
        c.startImport();c.picked(picked,"t");c.confirmImport("t")
        c.resume()
        assertEquals(Import.Unknown,c.state.value.import)
        c.checkStatus()
        assertEquals(1,fake.reconciles.size);assertEquals(1,fake.imports.single().selections)
        c.close()
    }

    @Test fun dataSwitchEndsThePageForGood()=runBlocking {
        val fake=Fake(target).apply {docs+=doc("Photo","image/png")};val c=controller(fake);c.refresh()
        fake.scopeEnded=true;fake.revoke()
        assertTrue(c.state.value.ended);assertTrue(c.state.value.revoked)
        c.resume();c.refresh()
        assertTrue(c.state.value.ended);assertEquals(Files.Loading,c.state.value.files)
        c.close();assertTrue(fake.unregistered)
    }
    @Test fun latePixelsAfterHideOrRevokeAndResumeCannotReappear()=runBlocking {
        for(resume in listOf(false,true)) {
            val fake=Fake(target).apply{docs+=doc("Photo","image/png")};val c=controller(fake);c.refresh()
            fake.beforeImage={if(resume){fake.revoke();c.resume()}else fake.hide?.invoke();assertTrue("Opening reader closes at hide, before decode returns",fake.previews.single().closed)}
            c.open((c.state.value.files as Files.Ready).items.single())
            assertEquals(Viewer.Closed,c.state.value.viewer)
            assertTrue(fake.previews.single().closed)
            assertTrue(fake.previews.single().pages.all{it.isRecycled})
            c.close()
        }
    }

    @Test fun pauseDiscardsUnconfirmedSelectionWithoutStartingAnImport()=runBlocking {
        val fake=Fake(target);val c=controller(fake)
        c.startImport();c.picked(picked,"t")
        fake.hide?.invoke();c.confirmImport("t")
        assertEquals(Import.Cancelled,c.state.value.import);assertTrue(fake.imports.isEmpty())
        c.close()
    }

    @Test fun finalNativeScopeRefusalCannotPublishAnAlreadyDecodedPage()=runBlocking {
        val fake=Fake(target).apply{docs+=doc("Photo","image/png");publishFailure=net.plainnotes.app.data.LibraryScopeMismatch()}
        val c=controller(fake);c.refresh();c.open((c.state.value.files as Files.Ready).items.single())
        assertTrue(c.state.value.viewer is Viewer.Message)
        assertTrue(fake.previews.single().closed);assertTrue(fake.previews.single().pages.all{it.isRecycled});c.close()
    }

}
