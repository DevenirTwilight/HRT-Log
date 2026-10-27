package net.plainnotes.app.attachments.ui

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CoroutineScope
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.attachments.AttachmentFlowController
import net.plainnotes.app.data.MedicalAttachmentOperations

/** Visit/individual-lab integration. This boundary does not itself grant authorization.
 * The release flag is checked BEFORE accessing the real user's runtime or target. Debug synthetic
 * hosts continue to construct their gateway directly from their isolated fixture only. */
internal fun NotesViewModel.medicalAttachmentsForRecord(context:Context,owner:LifecycleOwner,target:MedicalAttachmentOperations.Target,scope:CoroutineScope):MedicalAttachmentsController {
    check(AttachmentFlowController.PUBLIC_ENTRY_ENABLED) {"Medical attachment entry is OFF"}
    check(owner.lifecycle.currentState!=Lifecycle.State.DESTROYED)
    val controller=MedicalAttachmentsController(RuntimeAttachmentGateway(context,libraryRuntime,target),scope)
    owner.lifecycle.addObserver(object:DefaultLifecycleObserver {
        override fun onDestroy(owner:LifecycleOwner) {controller.close();owner.lifecycle.removeObserver(this)}
    })
    return controller
}
