package net.plainnotes.app.attachments.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.result.ActivityResult
import kotlinx.coroutines.flow.StateFlow
import net.plainnotes.app.attachments.AttachmentFlowController
import net.plainnotes.app.attachments.AttachmentViewer
import net.plainnotes.app.data.MedicalAttachmentOperations
import java.util.UUID

/**
 * What the attachment UI may do for ONE explicit, already-existing visit or individual lab result.
 * The target is fixed by the caller from a real record identity; nothing here derives it from a date,
 * file name or bare number. No Room, path, key or URI persistence is reachable through this boundary.
 */
internal interface MedicalAttachmentGateway {
    val scopeEnded:Boolean get()=false
    fun onHidden(listener:()->Unit):AutoCloseable=AutoCloseable {}
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

