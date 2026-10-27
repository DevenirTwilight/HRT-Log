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

/** Production real-record page; runtime authorization and scoped activation remain mandatory.
 * OFF is checked before the ViewModel/runtime is touched. No URI or capability is saved. */
@Composable internal fun MedicalAttachmentRecordPage(model:NotesViewModel,target:MedicalAttachmentOperations.Target,targetLabel:String,onBack:()->Unit) {
    if(!AttachmentFlowController.PUBLIC_ENTRY_ENABLED)return
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val scope=rememberCoroutineScope()
    val controller=remember(model,target,owner) {model.medicalAttachmentsForRecord(context,owner,target,scope)}
    val generation=remember(controller) {Session.generation}
    var awaiting by remember(controller) {mutableStateOf(false)}
    val title=stringResource(if(target.kind==MedicalAttachmentOperations.Kind.LAB)R.string.attach_default_lab else R.string.attach_default_visit)
    val picker=key(controller) {rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {result->
        val accepted=awaiting && Session.consumePicker(generation)
        awaiting=false
        if(accepted)controller.picked(result,title)
    }}
    DisposableEffect(controller) {onDispose {
        if(awaiting)Session.consumePicker(generation)
        controller.close()
    }}
    LaunchedEffect(controller) {controller.refresh()}
    MedicalAttachmentRecordContent(controller,targetLabel,onAdd={scope.launch {
        controller.startImport()?.let {intent->
            awaiting=true
            try {picker.launchPicker(intent)}catch(_:Exception) {
                awaiting=false;controller.picked(ActivityResult(Activity.RESULT_CANCELED,null),title)
            }
        }
    }},onExit=onBack)
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
        MedicalAttachmentViewer(state.viewer,controller::closeViewer)
    }
}
