package net.plainnotes.app.attachments

import android.content.Intent
import androidx.activity.result.ActivityResult
import android.app.Activity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.plainnotes.app.data.MedicalAttachmentOperations
import net.plainnotes.app.domain.AttachmentSession
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Scoped production use case, constructed only by RuntimeLibrarySession. No paths/Room/keys.
 * One controller owns one immutable operation. New selection requires an explicit new controller.
 * No URI, plaintext, unlock grant or pending write is persisted/replayed on process restart.
 */
internal class AttachmentFlowController(
    private val backend:MedicalAttachmentOperations,
    private val session:AttachmentSession,
    private val registration:AutoCloseable,
    val request:MedicalAttachmentOperations.Import,
    private val permission:()->Unit,
):AutoCloseable {
    sealed interface State {
        data object Selecting:State
        data object Pending:State
        data class Saved(val receipt:MedicalAttachmentOperations.Receipt):State
        data object Failed:State
        data object Cancelled:State
        data object ReconcileRequired:State
        data object Closed:State
    }
    private val mutable=MutableStateFlow<State>(State.Selecting)
    val state=mutable.asStateFlow()
    private val busy=AtomicBoolean(false)
    private val closed=AtomicBoolean(false)
    private val cancelled=AtomicBoolean(false)
    @Volatile private var input:AttachmentSession.Capability?=null
    fun pickerIntent()=Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).apply {
        type="*/*";putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("application/pdf","image/png","image/jpeg","application/octet-stream"))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    /** Consume a single transient SAF result. Provider length, extension and MIME are not trusted. */
    suspend fun selected(result:ActivityResult) {
        permission();check(!closed.get())
        if(!busy.compareAndSet(false,true))return
        try {
            check(mutable.value==State.Selecting) {"Reconcile the existing operation; do not repeat selection"}
            if(result.resultCode!=Activity.RESULT_OK) {mutable.value=State.Cancelled;return}
            val intent=requireNotNull(result.data);require((intent.clipData?.itemCount ?: 1)==1)
            val uri=requireNotNull(intent.data);require(uri.scheme=="content")
            val cap=session.issue().also {input=it}
            if(cancelled.get()) {cap.close();input=null;mutable.value=State.Cancelled;return}
            mutable.value=State.Pending
            try {
                val receipt=backend.import(cap,request,uri)
                // No list refresh, reminder, rendering or backup check can reverse this real COMMIT.
                if(!closed.get())mutable.value=State.Saved(receipt)
            }catch(e:Exception) {
                if(e is MedicalAttachmentOperations.ExistingOperation) {
                    if(!closed.get())mutable.value=State.ReconcileRequired
                    return
                }
                // Dispatcher cancellation may happen after SQLite COMMIT. Query facts, never guess.
                // A concurrently committed UUID may belong to a different input that won while
                // this provider was being read. Only the returned receipt acknowledges this
                // attempt. A queried COMMIT after an exception needs explicit reconciliation.
                withContext(NonCancellable) {reconcileFact(acknowledgeCommit=false)}
            }finally {input=null;cap.close()}
        }catch(_:Exception) {if(!closed.get() && mutable.value==State.Selecting)mutable.value=State.Failed}
        finally {busy.set(false)}
    }
    /** Cancellation revokes only this input attempt; it cannot undo an already committed record. */
    fun cancel() {cancelled.set(true);input?.close();if(mutable.value==State.Selecting)mutable.value=State.Cancelled}
    suspend fun reconcile() {
        if(closed.get())return
        // NOT_FOUND cannot establish failure while our provider read/transaction is still live.
        if(busy.get()) {mutable.value=State.ReconcileRequired;return}
        reconcileFact()
    }
    private suspend fun reconcileFact(acknowledgeCommit:Boolean=true) {
        if(closed.get())return
        try {
            permission()
            session.issue().let {cap->try {
                val fact=backend.reconcile(cap,request.operation,request.target,request.document)
                if(!closed.get())mutable.value=when(fact.phase) {
                    MedicalAttachmentOperations.Phase.COMMITTED->if(acknowledgeCommit)State.Saved(requireNotNull(fact.receipt))else State.ReconcileRequired
                    MedicalAttachmentOperations.Phase.NOT_FOUND,MedicalAttachmentOperations.Phase.ABORTED->State.Failed
                    MedicalAttachmentOperations.Phase.PENDING->State.ReconcileRequired
                }
            }finally {cap.close()}}
        }catch(_:Exception) {if(!closed.get())mutable.value=State.ReconcileRequired}
    }
    suspend fun recover() {
        permission();check(!closed.get())
        if(busy.get()) {mutable.value=State.ReconcileRequired;return}
        session.issue().let {cap->try {backend.recover(cap)}finally {cap.close()}}
        reconcile()
    }
    suspend fun activate()=session.issue().let {cap->try {permission();check(!closed.get());backend.activate(cap)}finally {cap.close()}}
    suspend fun documents()=session.issue().let {cap->try {permission();request.target?.let {backend.documents(cap,it)} ?: backend.libraryDocuments(cap)}finally {cap.close()}}
    suspend fun unlink(document:UUID)=session.issue().let {cap->try {permission();check(!closed.get());backend.unlink(cap,requireNotNull(request.target) {"Choose a specific association"},document)}finally {cap.close()}}
    suspend fun open(document:UUID):MedicalAttachmentOperations.Read {
        permission();check(!closed.get());val cap=session.issue()
        try {
            val read=backend.open(cap,request.target,document)
            return object:MedicalAttachmentOperations.Read by read {
                override fun close() {try {read.close()}finally {cap.close()}}
            }
        }catch(e:Throwable) {cap.close();throw e}
    }
    suspend fun backupEligibility()=session.issue().let {cap->try {permission();backend.exportEligibility(cap)}finally {cap.close()}}
    override fun close() {
        if(closed.compareAndSet(false,true)) {
            mutable.value=State.Closed
            try {session.revoke()}finally {registration.close()}
        }
    }
    companion object {
        /** Product navigation is allowed only through the authorized runtime and Room21 scoped
         * activation; this constant alone never grants access to records or decrypted files. */
        const val PUBLIC_ENTRY_ENABLED=true
    }
}
