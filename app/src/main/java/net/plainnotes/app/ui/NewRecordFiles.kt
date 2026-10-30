package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.R
import net.plainnotes.app.attachments.ui.*
import net.plainnotes.app.attachments.ui.library.FileWindow
import net.plainnotes.app.data.MedicalAttachmentOperations as Ops

/** Before the record exists this only holds the real picker result in memory; it never reads or imports it. */
@Composable internal fun NewRecordFileChoice(files:RecordFiles,saving:Boolean) {
    val state by files.controller.state.collectAsState(kotlinx.coroutines.Dispatchers.Main.immediate)
    Column(Modifier.fillMaxWidth().testTag("new-record-files"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.attach_section),style=MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.med03_save_record_first),style=MaterialTheme.typography.bodySmall)
        if(state.import is MedicalAttachmentsController.Import.Confirming) {
            Text(stringResource(R.string.med03_file_chosen),Modifier.testTag("new-record-file-chosen"))
            TextButton(files.controller::cancelImport,enabled=!saving){Text(stringResource(R.string.remove))}
        } else OutlinedButton(files.onAdd,enabled=!saving,modifier=Modifier.heightIn(min=48.dp).testTag("new-record-choose-file")){Text(stringResource(R.string.attach_add))}
        if(state.revoked || state.ended)Text(stringResource(R.string.attach_data_changed))
    }
}

/** Two separate facts: the record has committed; the file still requires its own explicit confirmation and receipt. */
@Composable internal fun NewRecordFiles(model:NotesViewModel,target:Ops.Target,draft:RecordFiles?,onDone:()->Unit) {
    val files=rememberRecordFiles(model,target) ?: return
    val label=stringResource(if(target.kind==Ops.Kind.APPOINTMENT)R.string.attach_default_visit else R.string.attach_default_lab)
    LaunchedEffect(files.controller) {
        draft?.controller?.takeUncommittedSelection()?.let {selected->
            resumeOnMain {files.controller.acceptUncommittedSelection(selected,label)}
        }
    }
    val state by files.controller.state.collectAsState(kotlinx.coroutines.Dispatchers.Main.immediate)
    FileWindow(stringResource(R.string.attach_section),onDone) {
        Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            val outcome=when(state.import) {
                is MedicalAttachmentsController.Import.Saved->net.plainnotes.app.attachments.ui.library.RecordFileOutcome.ADDED
                MedicalAttachmentsController.Import.Unknown,MedicalAttachmentsController.Import.Checking->net.plainnotes.app.attachments.ui.library.RecordFileOutcome.UNKNOWN
                MedicalAttachmentsController.Import.Saving,MedicalAttachmentsController.Import.Idle,MedicalAttachmentsController.Import.Cancelled,is MedicalAttachmentsController.Import.Confirming->net.plainnotes.app.attachments.ui.library.RecordFileOutcome.NONE
                else->net.plainnotes.app.attachments.ui.library.RecordFileOutcome.NOT_SAVED
            }
            if(state.import is MedicalAttachmentsController.Import.Confirming || state.import==MedicalAttachmentsController.Import.Saving)
                Text(stringResource(R.string.lib_record_saved,label),Modifier.testTag("record-committed-before-file"))
            else net.plainnotes.app.attachments.ui.library.RecordFileOutcomeCard(label,outcome,files.onAdd,onDone,files.controller::checkStatus)
            RecordFilesSection(files,stringResource(if(target.kind==Ops.Kind.APPOINTMENT)R.string.attach_default_visit else R.string.attach_default_lab),Modifier.weight(1f))
            TextButton(onDone,Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("new-record-files-done")){Text(stringResource(R.string.attach_dismiss))}
        }
        RecordFilesViewer(files,stringResource(R.string.attach_section))
    }
}
