package net.plainnotes.app.attachments.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.R
import net.plainnotes.app.attachments.AttachmentFlowController
import net.plainnotes.app.attachments.RuntimeMedicalLibraryGateway
import net.plainnotes.app.security.Session
import net.plainnotes.app.security.launchPicker
import net.plainnotes.app.attachments.ui.MedicalLibraryTransferController.*

/** Full-library transfer page, separate from ordinary PNBAK1 record backup. */
@Composable internal fun MedicalLibraryTransferPage(model:NotesViewModel,onReopen:()->Unit,onBack:()->Unit) {
    if(!AttachmentFlowController.PUBLIC_ENTRY_ENABLED)return
    val context=LocalContext.current
    val scope=net.plainnotes.app.ui.rememberMainThreadScope()
    val controller=remember(model) {MedicalLibraryTransferController(RuntimeMedicalLibraryGateway(context.applicationContext,model.libraryRuntime),scope,model.libraryUiState)}
    val generation=remember(controller) {Session.generation}
    var awaiting by remember(controller) {mutableStateOf(false)}
    val picker=key(controller) {rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {result->
        // The Activity may already have consumed the lock-on-leave exemption.
        // Accept only this live pending owner; runtime/capability checks still guard all IO.
        val valid=awaiting && generation==Session.generation &&
            Session.target!=net.plainnotes.app.security.UnlockTarget.PRIVATE &&
            runCatching {model.libraryRuntime.checkRequest()}.isSuccess
        Session.consumePicker(generation);awaiting=false
        if(valid)controller.picked(result)
    }}
    DisposableEffect(controller) {onDispose {if(awaiting)Session.consumePicker(generation);controller.close()}}
    BackHandler {controller.cancel();onBack()}
    MedicalLibraryTransferContent(controller,onChoose={mode->controller.choose(mode)?.let {intent->
        awaiting=true
        try {picker.launchPicker(intent)}catch(_:Exception) {awaiting=false;controller.cancel()}
    }},onReopen=onReopen)
}

@Composable internal fun MedicalLibraryTransferContent(controller:MedicalLibraryTransferController,onChoose:(Mode)->Unit,onReopen:()->Unit) {
    val state by controller.state.collectAsState()
    var abortConfirmation by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.med_transfer_title),style=MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.med_transfer_scope))
        val canChoose=!state.revoked && state.phase !in setOf(Phase.PICKING,Phase.PASSWORD,Phase.WORKING,Phase.REVIEW,Phase.RESTORING,Phase.UNKNOWN)
        Button({onChoose(Mode.EXPORT)},enabled=canChoose) {Text(stringResource(R.string.med_transfer_export))}
        OutlinedButton({onChoose(Mode.RESTORE)},enabled=canChoose) {Text(stringResource(R.string.med_transfer_restore))}
        when(state.phase) {
            Phase.PICKING->Text(stringResource(R.string.med_transfer_picking))
            Phase.WORKING,Phase.RESTORING->{LinearProgressIndicator(Modifier.fillMaxWidth());Text(stringResource(R.string.working))}
            Phase.REVIEW->state.prepared?.let {ready->
                Text(stringResource(R.string.med_transfer_preview,ready.summary.format,ready.summary.libraries,ready.summary.versions))
                Text(stringResource(R.string.med_transfer_switch_warning))
                OutlinedButton({if(controller.prepareSafetyBackup())onChoose(Mode.EXPORT)}) {Text(stringResource(R.string.med_transfer_backup_first))}
                Button(controller::confirmRestore) {Text(stringResource(R.string.med_transfer_confirm))}
            }
            Phase.EXPORTED->Text(stringResource(R.string.med_transfer_exported))
            Phase.RESTORED->{Text(stringResource(R.string.med_transfer_restored));Button(onReopen) {Text(stringResource(R.string.med_transfer_reopen))}}
            Phase.UNKNOWN->{Text(stringResource(R.string.med_transfer_unknown));Button(controller::checkStatus) {Text(stringResource(R.string.med_transfer_check))};OutlinedButton(onReopen) {Text(stringResource(R.string.med_transfer_reopen))}}
            Phase.CHECKED->{
                val fact=state.status
                Text(stringResource(when {fact==null->R.string.med_transfer_not_found;fact.committed->R.string.med_transfer_committed;else->R.string.med_transfer_pending}))
                if(!state.revoked && fact?.phase in MedicalLibraryTransferController.PENDING)OutlinedButton({abortConfirmation=true}) {Text(stringResource(R.string.med_transfer_abort))}
                if(state.revoked || fact?.committed==true)Button(onReopen) {Text(stringResource(R.string.med_transfer_reopen))}
            }
            Phase.FAILED->Text(stringResource(R.string.med_transfer_failed))
            Phase.CANCELLED->Text(stringResource(R.string.med_transfer_cancelled))
            Phase.REVOKED->Text(stringResource(R.string.med_transfer_revoked))
            else->{}
        }
        if(state.phase in setOf(Phase.PICKING,Phase.PASSWORD,Phase.WORKING,Phase.REVIEW,Phase.RESTORING))TextButton(controller::cancel) {Text(stringResource(R.string.cancel))}
    }
    if(state.phase==Phase.PASSWORD)MedicalTransferPasswordDialog(
        stringResource(if(state.mode==Mode.EXPORT)R.string.med_transfer_export else R.string.med_transfer_restore),
        stringResource(R.string.med_transfer_password_note),state.mode==Mode.EXPORT,controller::cancel,controller::password)
    if(abortConfirmation)AlertDialog(onDismissRequest={abortConfirmation=false},title={Text(stringResource(R.string.med_transfer_abort))},
        text={Text(stringResource(R.string.med_transfer_abort_warning))},confirmButton={TextButton({abortConfirmation=false;controller.abortPending()}){Text(stringResource(R.string.med_transfer_abort))}},
        dismissButton={TextButton({abortConfirmation=false}){Text(stringResource(R.string.cancel))}})
}

/** Restore accepts every protocol-valid length; new exports retain the existing 8-character UI minimum. */
@Composable private fun MedicalTransferPasswordDialog(title:String,note:String,confirm:Boolean,onDismiss:()->Unit,onOk:(CharArray)->Unit) {
    var password by remember {mutableStateOf("")};var repeated by remember {mutableStateOf("")}
    fun dismiss() {password="";repeated="";onDismiss()}
    AlertDialog(onDismissRequest=::dismiss,title={Text(title)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(note)
        OutlinedTextField(password,{password=it},Modifier.fillMaxWidth(),label={Text(stringResource(R.string.backup_password))},singleLine=true,
            visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
        if(confirm)OutlinedTextField(repeated,{repeated=it},Modifier.fillMaxWidth(),label={Text(stringResource(R.string.backup_password_repeat))},singleLine=true,
            visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),isError=repeated.isNotEmpty() && password!=repeated)
    }},confirmButton={Button(enabled=password.length in (if(confirm)8 else 1)..4096 && (!confirm || password==repeated),onClick={
        val secret=password.toCharArray();password="";repeated=""
        try {onOk(secret)}catch(t:Throwable){secret.fill('\u0000');throw t}
    }){Text(stringResource(R.string.ok))}},dismissButton={TextButton(::dismiss){Text(stringResource(R.string.cancel))}})
}
