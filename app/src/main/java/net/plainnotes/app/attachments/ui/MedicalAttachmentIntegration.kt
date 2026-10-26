package net.plainnotes.app.attachments.ui

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.attachments.AttachmentFlowController
import net.plainnotes.app.data.MedicalAttachmentOperations

/** Proposed visit/individual-lab integration. No navigation, persisted state or enablement grant.
 * The release flag is checked BEFORE accessing the real user's runtime or target. Debug synthetic
 * hosts continue to construct their gateway directly from their isolated fixture only. */
internal fun NotesViewModel.medicalAttachmentsForRecord(context:Context,target:MedicalAttachmentOperations.Target,scope:CoroutineScope):MedicalAttachmentsController {
    check(AttachmentFlowController.PUBLIC_ENTRY_ENABLED) {"Medical attachment entry is OFF"}
    return MedicalAttachmentsController(RuntimeAttachmentGateway(context,libraryRuntime,target),scope)
}
