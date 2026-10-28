package net.plainnotes.app.attachments.ui

import net.plainnotes.app.attachments.AttachmentReconciliationStore
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.result.ActivityResult
import kotlinx.coroutines.flow.StateFlow
import net.plainnotes.app.attachments.AttachmentFlowController
import net.plainnotes.app.attachments.AttachmentViewer
import net.plainnotes.app.data.MedicalAttachmentOperations
import net.plainnotes.app.runtime.RuntimeLibrarySession
import java.util.UUID

/**
 * What the attachment UI may do for ONE explicit, already-existing visit or individual lab result.
 * The target is fixed by the caller from a real record identity; nothing here derives it from a date,
 * file name or bare number. No Room, path, key or URI persistence is reachable through this boundary.
 */
internal interface MedicalAttachmentGateway {
    val scopeEnded:Boolean get()=false
    fun onHidden(listener:()->Unit):AutoCloseable=AutoCloseable {}
    fun pending():Pair<UUID,UUID>?=null
    fun retain(operation:UUID,document:UUID) {}
    fun resolved(operation:UUID,document:UUID) {}
    val target:MedicalAttachmentOperations.Target?
    suspend fun documents():List<MedicalAttachmentOperations.Document>
    /** Removes one explicit association without deleting the underlying medical document. */
    suspend fun unlink(document:UUID):Boolean = error("Unlink unavailable for this gateway")
    /** The system picker for one transient document; choosing creates no operation. */
    suspend fun pickerIntent():Intent
    /** Opens an authenticated preview; the returned object owns the reader and must be closed. */
    suspend fun preview(document:UUID):Preview
    /** One import attempt with its own operation identity; a new selection needs a new attempt. */
    suspend fun newImport(title:String):Import
    /** Read-only status of an earlier attempt, using the same identities; never re-reads the file. */
    suspend fun reconcile(operation:UUID,document:UUID):AttachmentFlowController.State
    /** Called (possibly off the main thread) when lock, disguise or a data switch revokes access. */
    fun onRevoked(listener:()->Unit):AutoCloseable

    interface Preview:AutoCloseable {
        fun <T> publish(block:()->T):T=block()
        fun pdfFirstPage():Bitmap
        fun image():Bitmap
        fun release(bitmap:Bitmap)
    }
    interface Import:AutoCloseable {
        val operation:UUID
        val document:UUID
        val state:StateFlow<AttachmentFlowController.State>
        suspend fun selected(result:ActivityResult)
        fun cancel()
    }
}

/** Production wiring: every call goes through the screen's RuntimeLibrarySession scope and the existing
 * AttachmentFlowController/AttachmentViewer. No second store or decryption path exists. */
internal class RuntimeAttachmentGateway(private val context:Context,private val runtime:RuntimeLibrarySession,
    override val target:MedicalAttachmentOperations.Target?):MedicalAttachmentGateway {
    private val recovery=AttachmentReconciliationStore(context,
        requireNotNull((runtime.state.value as? RuntimeLibrarySession.State.Ready)?.scope),target)
    override fun pending()=recovery.pending()
    override fun retain(operation:UUID,document:UUID)=recovery.retain(operation,document)
    override fun resolved(operation:UUID,document:UUID)=recovery.resolved(operation,document)
    override val scopeEnded get()=runtime.ended
    override fun onHidden(listener:()->Unit)=runtime.observePresentationHidden(listener)
    private suspend fun browse()=runtime.attachments(target,BROWSE_TITLE).also {flow->
        try {if(AttachmentFlowController.PUBLIC_ENTRY_ENABLED)flow.activate()}catch(t:Throwable) {flow.close();throw t}
    }
    override suspend fun documents()=browse().use {it.documents()}
    override suspend fun unlink(document:UUID)=browse().use {it.unlink(document)}
    override suspend fun pickerIntent()=browse().use {it.pickerIntent()}
    override suspend fun preview(document:UUID):MedicalAttachmentGateway.Preview {
        val flow=browse()
        try {
            val viewer=AttachmentViewer(context,flow.open(document))
            return object:MedicalAttachmentGateway.Preview {
                override fun <T> publish(block:()->T)=viewer.publishFrame(block)
                override fun pdfFirstPage()=viewer.pdfPage(0)
                override fun image()=viewer.image()
                override fun release(bitmap:Bitmap)=viewer.release(bitmap)
                override fun close() {try {viewer.close()}finally {flow.close()}}
            }
        }catch(e:Throwable) {flow.close();throw e}
    }
    override suspend fun newImport(title:String):MedicalAttachmentGateway.Import {
        check(pending()==null) {"Reconcile existing attachment operation first"}
        val flow=runtime.attachments(target,title)
        try {if(AttachmentFlowController.PUBLIC_ENTRY_ENABLED)flow.activate()}catch(t:Throwable) {flow.close();throw t}
        return object:MedicalAttachmentGateway.Import {
            override val operation get()=flow.request.operation
            override val document get()=flow.request.document
            override val state get()=flow.state
            override suspend fun selected(result:ActivityResult)=flow.selected(result)
            override fun cancel()=flow.cancel()
            override fun close()=flow.close()
        }
    }
    override suspend fun reconcile(operation:UUID,document:UUID)=runtime.attachments(target,BROWSE_TITLE,operation,document).use {
        it.reconcile();it.state.value
    }
    override fun onRevoked(listener:()->Unit):AutoCloseable=runtime.protect(AutoCloseable(listener))
    private companion object {const val BROWSE_TITLE="browse"} // Never written: only an import attempt stores its title.
}
