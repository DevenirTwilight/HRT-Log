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

/** OFF application use case, constructed only by RuntimeLibrarySession. No paths/Room/keys.
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
            mutable.value=State.Pending
            try {
                val receipt=backend.import(cap,request,uri)
                // No list refresh, reminder, rendering or backup check can reverse this real COMMIT.
                if(!closed.get())mutable.value=State.Saved(receipt)
            }catch(_:Exception) {
                // Dispatcher cancellation may happen after SQLite COMMIT. Query facts, never guess.
                withContext(NonCancellable) {reconcile()}
            }finally {input=null;cap.close()}
        }catch(_:Exception) {if(!closed.get() && mutable.value==State.Selecting)mutable.value=State.Failed}
        finally {busy.set(false)}
    }
    /** Cancellation revokes only this input attempt; it cannot undo an already committed record. */
    fun cancel() {input?.close();if(mutable.value==State.Selecting)mutable.value=State.Cancelled}
    suspend fun reconcile() {
        if(closed.get())return
        try {
            permission()
            session.issue().let {cap->try {
                val fact=backend.reconcile(cap,request.operation)
                if(!closed.get())mutable.value=when(fact.phase) {
                    MedicalAttachmentOperations.Phase.COMMITTED->State.Saved(requireNotNull(fact.receipt))
                    MedicalAttachmentOperations.Phase.NOT_FOUND,MedicalAttachmentOperations.Phase.ABORTED->State.Failed
                    MedicalAttachmentOperations.Phase.PENDING->State.ReconcileRequired
                }
            }finally {cap.close()}}
        }catch(_:Exception) {if(!closed.get())mutable.value=State.ReconcileRequired}
    }
    suspend fun documents()=session.issue().let {cap->try {permission();backend.documents(cap,request.target)}finally {cap.close()}}
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
        /** This is not a user/debug enable toggle. No main navigation or release entry calls it. */
        const val PUBLIC_ENTRY_ENABLED=false
    }
}
