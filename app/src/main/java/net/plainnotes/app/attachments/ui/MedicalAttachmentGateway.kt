package net.plainnotes.app.attachments.ui

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
    val target:MedicalAttachmentOperations.Target
    suspend fun documents():List<MedicalAttachmentOperations.Document>
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
    override val target:MedicalAttachmentOperations.Target):MedicalAttachmentGateway {
    private suspend fun browse()=runtime.attachments(target,BROWSE_TITLE)
    override suspend fun documents()=browse().use {it.documents()}
    override suspend fun pickerIntent()=browse().use {it.pickerIntent()}
    override suspend fun preview(document:UUID):MedicalAttachmentGateway.Preview {
        val flow=browse()
        try {
            val viewer=AttachmentViewer(context,flow.open(document))
            return object:MedicalAttachmentGateway.Preview {
                override fun pdfFirstPage()=viewer.pdfPage(0)
                override fun image()=viewer.image()
                override fun release(bitmap:Bitmap)=viewer.release(bitmap)
                override fun close() {try {viewer.close()}finally {flow.close()}}
            }
        }catch(e:Throwable) {flow.close();throw e}
    }
    override suspend fun newImport(title:String):MedicalAttachmentGateway.Import {
        val flow=runtime.attachments(target,title)
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
