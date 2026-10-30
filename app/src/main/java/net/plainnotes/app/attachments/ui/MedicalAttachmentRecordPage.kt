package net.plainnotes.app.attachments.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.R
import net.plainnotes.app.attachments.AttachmentFlowController
import net.plainnotes.app.data.MedicalAttachmentOperations
import net.plainnotes.app.security.Session
import net.plainnotes.app.security.launchPicker

/** One record's attachments as the production UI uses them: the scoped controller and the system-picker launcher.
 * Shared by the visit page, the lab result page and the full-page host so there is exactly one authorization path. */
internal class RecordFiles(val controller:MedicalAttachmentsController,val onAdd:()->Unit,val model:NotesViewModel?=null,val target:MedicalAttachmentOperations.Target?=null)

/** Production real-record files. Runtime authorization and scoped activation remain mandatory.
 * OFF is checked before the ViewModel/runtime is touched (null when OFF). No URI or capability is saved. */
@Composable internal fun rememberRecordFiles(model:NotesViewModel,target:MedicalAttachmentOperations.Target?):RecordFiles? {
    if(!AttachmentFlowController.PUBLIC_ENTRY_ENABLED)return null
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val scope=net.plainnotes.app.ui.rememberMainThreadScope()
    val controller=remember(model,target,owner) {model.medicalAttachmentsForRecord(context,owner,target,scope)}
    val title=stringResource(when(target?.kind) {null->R.string.med03_default_file;MedicalAttachmentOperations.Kind.APPOINTMENT->R.string.attach_default_visit;else->R.string.attach_default_lab})
    val onAdd=rememberAttachmentPicker(model,controller,title)
    LaunchedEffect(controller) {controller.refresh()}
    return remember(controller,onAdd) {RecordFiles(controller,onAdd,model,target)}
}

/** Shared real SAF lifecycle for record, file-first and not-yet-saved-record selections. */
@Composable internal fun rememberAttachmentPicker(model:NotesViewModel,controller:MedicalAttachmentsController,title:String):()->Unit {
    val scope=net.plainnotes.app.ui.rememberMainThreadScope()
    val generation=remember(controller) {Session.generation}
    var awaiting by remember(controller) {mutableStateOf(false)}
    val picker=key(controller) {rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {result->
        // MainActivity consumes the one-shot lock exemption in onStart. ActivityResult
        // delivery can precede OR follow it across Android versions. That exemption is
        // not the result's authority: the pending owner, session and scoped runtime are.
        val accepted=awaiting && generation==Session.generation &&
            Session.target!=net.plainnotes.app.security.UnlockTarget.PRIVATE &&
            runCatching {model.libraryRuntime.checkRequest()}.isSuccess
        Session.consumePicker(generation)
        awaiting=false
        if(accepted)controller.picked(result,title)
    }}
    DisposableEffect(controller) {onDispose {
        if(awaiting)Session.consumePicker(generation)
        controller.close()
    }}
    return remember(controller,picker,title) {{scope.launch {
        controller.startImport()?.let {intent->
            awaiting=true
            try {picker.launchPicker(intent)}catch(_:Exception) {
                awaiting=false;controller.picked(ActivityResult(Activity.RESULT_CANCELED,null),title)
            }
        }
    };Unit}}
}

/** Full-page host kept for the guarded flows that still open the files on their own page. */
@Composable internal fun MedicalAttachmentRecordPage(model:NotesViewModel,target:MedicalAttachmentOperations.Target,targetLabel:String,onBack:()->Unit) {
    val files=rememberRecordFiles(model,target) ?: return
    MedicalAttachmentRecordContent(files.controller,targetLabel,onAdd=files.onAdd,onExit=onBack)
}

/** The related-files block of a record page: stateless over the controller's state. */
@Composable internal fun RecordFilesSection(files:RecordFiles,targetLabel:String,modifier:Modifier=Modifier,scopeNote:String?=null) {
    val state by files.controller.state.collectAsState()
    val controller=files.controller
    var choose by remember {mutableStateOf(false)}
    Column(modifier.testTag("record-files:${files.target?.kind}:${files.target?.id}")) {MedicalAttachmentsSection(state,targetLabel,onAdd=files.onAdd,onConfirm=controller::confirmImport,
        onCancelImport=controller::cancelImport,onCheckStatus=controller::checkStatus,
        onDismissMessage=controller::dismissImportMessage,onRetry=controller::refresh,onOpen=controller::open,modifier=Modifier,
        onUnlink=controller::unlink,onDismissUnlinkMessage=controller::dismissUnlinkMessage,scopeNote=scopeNote,
        onChooseExisting=if(files.model!=null && files.target!=null)({choose=true})else null)}
    if(choose)files.model?.let {model->
        val notes by model.state.collectAsState();val extra by model.extra.collectAsState()
        net.plainnotes.app.attachments.ui.library.FileWindow(stringResource(R.string.attach_choose_existing),{choose=false}) {
            net.plainnotes.app.attachments.ui.library.MedicalFileLibraryHost(model,notes.appointments,extra.labs,extra.labPanels,
                onRecord={},onNewVisit={},onNewDraw={},onPrepare={},onBackup={},chooseFor=files.target,onChosen={choose=false;controller.refresh()})
        }
    }
}

/** The page-sized file page over a record page. System BACK closes it first; nothing stale stays when it is closed or revoked. */
@Composable internal fun RecordFilesViewer(files:RecordFiles,targetLabel:String,modifier:Modifier=Modifier) {
    val state by files.controller.state.collectAsState()
    val open=state.viewer!=MedicalAttachmentsController.Viewer.Closed
    BackHandler(open) {files.controller.closeViewer()}
    MedicalAttachmentViewer(state.viewer,files.controller::closeViewer,modifier,targetLabel,onPage=files.controller::showPage)
}

/** Shared by the production page and the existing guarded synthetic host.
 * The caller supplies its already-scoped controller; no alternate authorization exists here. */
@Composable internal fun MedicalAttachmentRecordContent(controller:MedicalAttachmentsController,targetLabel:String,onAdd:()->Unit,onExit:(()->Unit)?=null) {
    val state by controller.state.collectAsState()
    BackHandler(state.viewer!=MedicalAttachmentsController.Viewer.Closed || onExit!=null) {
        if(state.viewer!=MedicalAttachmentsController.Viewer.Closed)controller.closeViewer()else onExit?.invoke()
    }
    Box(Modifier.fillMaxWidth().fillMaxHeight()) {
        Column(Modifier.padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            MedicalAttachmentsSection(state,targetLabel,onAdd=onAdd,onConfirm=controller::confirmImport,
                onCancelImport=controller::cancelImport,onCheckStatus=controller::checkStatus,
                onDismissMessage=controller::dismissImportMessage,onRetry=controller::refresh,onOpen=controller::open,
                onUnlink=controller::unlink,onDismissUnlinkMessage=controller::dismissUnlinkMessage)
        }
        MedicalAttachmentViewer(state.viewer,controller::closeViewer,targetLabel=targetLabel,onPage=controller::showPage)
    }
}
