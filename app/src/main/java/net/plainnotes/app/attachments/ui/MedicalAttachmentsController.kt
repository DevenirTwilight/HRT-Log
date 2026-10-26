package net.plainnotes.app.attachments.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.result.ActivityResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.plainnotes.app.attachments.AttachmentFlowController
import net.plainnotes.app.attachments.AttachmentViewer
import net.plainnotes.app.data.MedicalAttachmentOperations
import java.util.UUID

/**
 * UI state for one record's attachments. Holds no URI beyond the single in-memory picker result
 * between "chosen" and "attach", never persists anything, and treats only the real COMMIT receipt
 * as "saved". Revocation (lock, disguise, data switch) drops every rendered page reference first,
 * then the gateway's own capability revocation recycles the pixels and closes readers.
 */
internal class MedicalAttachmentsController(private val gateway:MedicalAttachmentGateway,private val scope:CoroutineScope,
    private val io:CoroutineDispatcher=Dispatchers.IO):AutoCloseable {
    enum class FileKind { PDF, IMAGE, OTHER }
    data class Item(val document:UUID,val title:String,val kind:FileKind,val available:Boolean)
    sealed interface Files {
        data object Loading:Files
        data class Ready(val items:List<Item>):Files
        data object Failed:Files
    }
    sealed interface Import {
        data object Idle:Import
        /** A file was chosen; nothing is read or written until the user confirms. */
        data class Confirming(val title:String):Import
        data object Saving:Import
        /** Only from an actual COMMIT receipt. [listStale] if the follow-up refresh failed. */
        data class Saved(val listStale:Boolean):Import
        data object NotSaved:Import
        data object Cancelled:Import
        /** Outcome unknown: offer a read-only status check, never a repeated write. */
        data object Unknown:Import
        data object Checking:Import
    }
    sealed interface Viewer {
        data object Closed:Viewer
        data class Opening(val title:String):Viewer
        data class Showing(val title:String,val page:Bitmap,val pdf:Boolean):Viewer
        data class Message(val title:String,val reason:Reason):Viewer
    }
    enum class Reason { PDF_NEEDS_ANDROID_9, NO_PREVIEW_FOR_TYPE, FAILED }
    data class State(val files:Files=Files.Loading,val import:Import=Import.Idle,val viewer:Viewer=Viewer.Closed,
        /** Lock/disguise/data switch: nothing from this record is shown until access is granted again. */
        val revoked:Boolean=false,
        /** The screen's data scope ended (data switch/restore): this page can never read again. */
        val ended:Boolean=false)

    private val mutable=MutableStateFlow(State())
    val state=mutable.asStateFlow()
    @Volatile private var closed=false
    private var attempt:MedicalAttachmentGateway.Import?=null
    private var chosen:ActivityResult?=null
    private var picking=false
    /** Identities of an attempt whose outcome is unknown; kept only for the read-only check. */
    private var unresolved:Pair<UUID,UUID>?=null
    private var preview:MedicalAttachmentGateway.Preview?=null
    private var job:Job?=null
    private var presentationEpoch=0L
    private val hidden=gateway.onHidden {hidePresentation()}
    private val revocation=try {gateway.onRevoked {if(!closed) {if(gateway.scopeEnded)libraryChanged()else revoke()}}}
        catch(t:Throwable) {hidden.close();throw t}

    fun refresh() {
        if(closed || state.value.revoked)return
        val epoch=presentationEpoch
        scope.launch {
            mutable.update {it.copy(files=if(it.files is Files.Ready)it.files else Files.Loading)}
            val loaded=loadFiles()
            synchronized(this@MedicalAttachmentsController) {
                if(!closed && !state.value.revoked && presentationEpoch==epoch)mutable.update {it.copy(files=loaded ?: Files.Failed)}
            }
        }
    }
    private suspend fun loadFiles():Files.Ready?=try {
        Files.Ready(withContext(io) {gateway.documents()}.map {d->
            Item(d.document,d.title,when(d.mime) {"application/pdf"->FileKind.PDF;"image/png","image/jpeg"->FileKind.IMAGE;else->FileKind.OTHER},
                d.state=="ACTIVE" && d.linkState=="ACTIVE")})
    }catch(e:CancellationException) {throw e}catch(_:Exception) {null}

    /** Returns the system picker intent. Choosing a file creates no operation and reads nothing. */
    suspend fun startImport():Intent? {
        if(closed || state.value.revoked || state.value.import==Import.Saving || state.value.import==Import.Checking)return null
        chosen=null;picking=true
        mutable.update {it.copy(import=Import.Idle)}
        return try {gateway.pickerIntent()}catch(e:CancellationException) {throw e}catch(_:Exception) {picking=false;null}
    }
    /** The picker returned. Cancel/back means no file and no pending operation. */
    fun picked(result:ActivityResult,defaultTitle:String) {
        if(!picking || closed || state.value.revoked)return
        picking=false
        if(result.resultCode!=android.app.Activity.RESULT_OK || result.data?.data==null) {mutable.update {it.copy(import=Import.Cancelled)};return}
        chosen=result
        mutable.update {it.copy(import=Import.Confirming(defaultTitle))}
    }
    /** Nothing was created yet, so cancelling leaves no operation to commit or reconcile. */
    fun cancelImport() {
        if(state.value.import !is Import.Confirming)return
        chosen=null
        mutable.update {it.copy(import=Import.Cancelled)}
    }
    private fun discardAttempt() {
        chosen=null;picking=false
        attempt?.let {runCatching {it.cancel();it.close()}}
        attempt=null
    }
    /** The user explicitly confirmed this record and name; only now one operation exists, reads once and commits. */
    fun confirmImport(title:String) {
        val result=chosen ?: return
        if(state.value.import !is Import.Confirming || title.isBlank() || title.length>256)return
        chosen=null
        mutable.update {it.copy(import=Import.Saving)}
        job=scope.launch {
            val current=try {gateway.newImport(title)}catch(e:CancellationException) {throw e}catch(_:Exception) {null}
            if(current==null) {mutable.update {it.copy(import=Import.NotSaved)};return@launch}
            attempt=current
            if(closed || state.value.revoked) {discardAttempt();return@launch}
            try {withContext(io) {current.selected(result)}}catch(e:CancellationException) {throw e}catch(_:Exception) {}
            settle(current)
        }
    }
    private suspend fun settle(current:MedicalAttachmentGateway.Import) {
        val outcome=current.state.value
        when(outcome) {
            is AttachmentFlowController.State.Saved-> {
                unresolved=null
                // The COMMIT is real; a later list failure must not turn it into "not saved".
                val files=loadFiles()
                mutable.update {it.copy(import=Import.Saved(listStale=files==null),files=if(it.revoked)Files.Loading else files ?: it.files)}
            }
            AttachmentFlowController.State.Failed->{unresolved=null;mutable.update {it.copy(import=Import.NotSaved)}}
            AttachmentFlowController.State.Cancelled->{unresolved=null;mutable.update {it.copy(import=Import.Cancelled)}}
            else-> {
                // Pending, ReconcileRequired, Closed (revoked mid-save) or anything unexpected.
                unresolved=current.operation to current.document
                mutable.update {it.copy(import=Import.Unknown)}
            }
        }
        if(attempt===current) {attempt=null;current.close()}
    }
    /** Read-only status of the unresolved attempt with the same identities. */
    fun checkStatus() {
        val (operation,document)=unresolved ?: return
        if(closed || state.value.revoked)return
        mutable.update {it.copy(import=Import.Checking)}
        scope.launch {
            val outcome=try {withContext(io) {gateway.reconcile(operation,document)}}catch(e:CancellationException) {throw e}catch(_:Exception) {null}
            when(outcome) {
                is AttachmentFlowController.State.Saved-> {
                    unresolved=null;val files=loadFiles()
                    mutable.update {it.copy(import=Import.Saved(listStale=files==null),files=if(it.revoked)Files.Loading else files ?: it.files)}
                }
                AttachmentFlowController.State.Failed->{unresolved=null;mutable.update {it.copy(import=Import.NotSaved)}}
                else->mutable.update {it.copy(import=Import.Unknown)}
            }
        }
    }
    fun dismissImportMessage() {
        val current=state.value.import
        if(current is Import.Saved || current==Import.NotSaved || current==Import.Cancelled)mutable.update {it.copy(import=Import.Idle)}
    }

    @Synchronized fun open(item:Item) {
        if(closed || state.value.revoked || !item.available)return
        closePreview()
        val epoch=++presentationEpoch
        if(item.kind==FileKind.OTHER) {mutable.update {it.copy(viewer=Viewer.Message(item.title,Reason.NO_PREVIEW_FOR_TYPE))};return}
        mutable.update {it.copy(viewer=Viewer.Opening(item.title))}
        scope.launch {
            var opened:MedicalAttachmentGateway.Preview?=null
            try {
                val page=withContext(io) {
                    val p=gateway.preview(item.document).also {opened=it}
                    if(item.kind==FileKind.PDF)p.pdfFirstPage() else p.image()
                }
                val owner=requireNotNull(opened)
                synchronized(this@MedicalAttachmentsController) {
                    if(closed || state.value.revoked || presentationEpoch!=epoch || state.value.viewer !is Viewer.Opening) {owner.close();return@launch}
                    preview=owner
                    mutable.update {it.copy(viewer=Viewer.Showing(item.title,page,item.kind==FileKind.PDF))}
                }
            }catch(e:CancellationException) {opened?.close();throw e}
            catch(e:Exception) {
                opened?.close()
                synchronized(this@MedicalAttachmentsController) {
                    if(!state.value.revoked && presentationEpoch==epoch)mutable.update {it.copy(viewer=Viewer.Message(item.title,if(e is AttachmentViewer.PdfPreviewUnsupported)Reason.PDF_NEEDS_ANDROID_9 else Reason.FAILED))}
                }
            }
        }
    }
    /** A real pause also discards an unconfirmed selection, never a committed receipt. */
    @Synchronized private fun hidePresentation() {closeViewer();cancelImport()}

    /** Leaving the viewer: forget the page first, then release pixels and close the reader. */
    @Synchronized fun closeViewer() {
        presentationEpoch++
        mutable.update {it.copy(viewer=Viewer.Closed)}
        closePreview()
    }
    private fun closePreview() {
        val owned=preview;preview=null
        owned?.let {p->(state.value.viewer as? Viewer.Showing)?.let {runCatching {p.release(it.page)}};runCatching {p.close()}}
    }

    /** Lock or disguise: hide everything at once. Access may be granted again later (resume). */
    @Synchronized fun revoke() {
        presentationEpoch++
        mutable.update {State(files=Files.Loading,revoked=true)}
        val owned=preview;preview=null
        runCatching {owned?.close()}
        chosen=null;picking=false
        attempt?.let {a->
            // A save in flight is not abandoned silently: its outcome is checked read-only later.
            if(job?.isActive==true)unresolved=a.operation to a.document
            runCatching {a.cancel();a.close()}
        }
        attempt=null
    }
    /** The data this page was opened for is no longer current. Old identities are never re-bound. */
    @Synchronized fun libraryChanged() {
        revoke();unresolved=null
        mutable.update {State(files=Files.Loading,revoked=true,ended=true)}
    }
    /** Access granted again (unlock): list only; the viewer never reopens by itself. */
    @Synchronized fun resume() {
        // Ordinary onResume (e.g. back from the system picker) must not reset a pending confirmation.
        if(closed || state.value.ended || !state.value.revoked)return
        mutable.update {it.copy(revoked=false,import=if(unresolved!=null)Import.Unknown else Import.Idle)}
        refresh()
    }
    @Synchronized override fun close() {
        presentationEpoch++
        if(closed)return
        closed=true
        mutable.update {State(files=Files.Loading,revoked=true)}
        runCatching {preview?.close()};preview=null
        discardAttempt()
        runCatching {hidden.close()}
        runCatching {revocation.close()}
    }
}
