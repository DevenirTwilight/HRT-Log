package net.plainnotes.app.disguise

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import net.plainnotes.app.R
import net.plainnotes.app.security.launchPicker
import net.plainnotes.app.ui.PasswordDialog
import net.plainnotes.app.security.AppLock
import net.plainnotes.app.ui.SectionCard

private tailrec fun Context.activity():Activity?=when(this){is Activity->this;is ContextWrapper->baseContext.activity();else->null}

@Composable fun DisguiseSection(onPrivateDataCleared:()->Unit,onRoutingChanged:()->Unit={},backup:suspend(Uri,CharArray)->Boolean) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var shell by remember{mutableStateOf(Disguise.shell(context))}
    var configured by remember{mutableStateOf(Disguise.hasPrivateCode(context))}
    var setup by remember{mutableStateOf(false)}
    var explained by remember{mutableStateOf<Disguise.Shell?>(null)}
    var confirmOff by remember{mutableStateOf(false)}
    var changeCode by remember{mutableStateOf(false)}
    var removeCode by remember{mutableStateOf(false)}
    var clearNotes by remember{mutableStateOf(false)}
    var working by remember{mutableStateOf(false)}
    var failed by remember{mutableStateOf(false)}
    fun operation(block:()->Unit,onSuccess:()->Unit) {
        working=true;failed=false
        scope.launch {
            try { withContext(Dispatchers.IO){block()};onSuccess() }
            catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(_:Exception){failed=true}
            finally { working=false }
        }
    }
    SectionCard(stringResource(R.string.disguise_title)) {
        Text(stringResource(R.string.disguise_desc),style=MaterialTheme.typography.bodyMedium)
        val on=shell
        if(on==null)Button(enabled=!working,onClick={setup=true}){Text(stringResource(R.string.disguise_setup))}
        else {
            Text(stringResource(R.string.disguise_on,stringResource(on.label)),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
            Text(stringResource(if(on==Disguise.Shell.CALCULATOR)R.string.disguise_how_calc else R.string.disguise_how_notes),style=MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.disguise_exit),style=MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.disguise_lock_note),style=MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text(stringResource(R.string.disguise_private_code),style=MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.disguise_private_desc),style=MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled=!working,onClick={changeCode=true}){Text(stringResource(if(configured)R.string.private_code_change else R.string.private_code_set))}
            if(configured) {
                OutlinedButton(enabled=!working,onClick={context.activity()?.let(Disguise::preparePrivate)}){Text(stringResource(R.string.private_prepare))}
                TextButton(enabled=!working,onClick={removeCode=true}){Text(stringResource(R.string.private_code_remove))}
                TextButton(enabled=!working,onClick={clearNotes=true}){Text(stringResource(R.string.private_clear))}
            }
            HorizontalDivider()
            OutlinedButton(enabled=!working,onClick={confirmOff=true}){Text(stringResource(R.string.disguise_disable))}
        }
        if(working)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(failed)Text(stringResource(R.string.operation_error),color=MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.disguise_limits),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(setup)SetupDialog({setup=false},backup){s,code,alternate->
        val owner=net.plainnotes.app.security.Session.generation
        operation({Disguise.enable(context,s,code,alternate)}){context.activity()?.let{Disguise.completeSetup(it,owner)};shell=s;configured=alternate!=null;setup=false;explained=s;onRoutingChanged()}
    }
    explained?.let{s->AlertDialog(onDismissRequest={explained=null},title={Text(stringResource(R.string.disguise_enabled_title))},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text(stringResource(R.string.disguise_guide_enter,stringResource(s.label)))
            Text(stringResource(if(s==Disguise.Shell.CALCULATOR)R.string.disguise_how_calc else R.string.disguise_how_notes))
            Text(stringResource(R.string.disguise_private_desc))
            Text(stringResource(R.string.disguise_exit));Text(stringResource(R.string.disguise_lock_note))
            Text(stringResource(R.string.disguise_guide_disable));Text(stringResource(R.string.disguise_guide_forgot))
        }},confirmButton={Button(onClick={explained=null}){Text(stringResource(R.string.ok))}})}
    if(changeCode)PrivateCodeDialog({changeCode=false;configured=Disguise.hasPrivateCode(context)}){value->Disguise.setPrivateCode(context,value)}
    if(confirmOff)AlertDialog(onDismissRequest={if(!working)confirmOff=false},title={Text(stringResource(R.string.disguise_disable))},text={Text(stringResource(R.string.disguise_disable_desc))},
        confirmButton={Button(enabled=!working,onClick={operation({Disguise.disable(context)}){shell=null;configured=false;confirmOff=false;onPrivateDataCleared();onRoutingChanged()}}){Text(stringResource(R.string.disguise_disable))}},
        dismissButton={TextButton(enabled=!working,onClick={confirmOff=false}){Text(stringResource(R.string.cancel))}})
    if(removeCode)AlertDialog(onDismissRequest={if(!working)removeCode=false},title={Text(stringResource(R.string.private_code_remove))},text={Text(stringResource(R.string.private_code_remove_desc))},
        confirmButton={Button(enabled=!working,onClick={operation({Disguise.removePrivateCode(context)}){configured=false;removeCode=false;onPrivateDataCleared()}}){Text(stringResource(R.string.private_code_remove))}},
        dismissButton={TextButton(enabled=!working,onClick={removeCode=false}){Text(stringResource(R.string.cancel))}})
    if(clearNotes)AlertDialog(onDismissRequest={if(!working)clearNotes=false},title={Text(stringResource(R.string.private_clear))},text={Text(stringResource(R.string.private_clear_desc))},
        confirmButton={Button(enabled=!working,onClick={operation({Disguise.clearPrivate(context)}){clearNotes=false;onPrivateDataCleared()}}){Text(stringResource(R.string.private_clear))}},
        dismissButton={TextButton(enabled=!working,onClick={clearNotes=false}){Text(stringResource(R.string.cancel))}})
}

@Composable private fun PrivateCodeDialog(onDismiss:()->Unit,onSet:(String)->Unit) {
    val scope=rememberCoroutineScope()
    var a by remember{mutableStateOf("")};var b by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)};var failed by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text(stringResource(R.string.disguise_private_code))},
        text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            CodeField(a,stringResource(R.string.disguise_private_code),failed){a=it;failed=false}
            CodeField(b,stringResource(R.string.disguise_code_repeat),b.isNotEmpty() && a!=b){b=it}
            Text(stringResource(R.string.disguise_private_desc),style=MaterialTheme.typography.bodySmall)
            if(failed)Text(stringResource(R.string.private_code_error),color=MaterialTheme.colorScheme.error)
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        }},confirmButton={Button(enabled=!busy && AppLock.validPin(a) && a==b,onClick={busy=true;scope.launch{
            try { withContext(Dispatchers.IO){onSet(a)};onDismiss() }
            catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(_:Exception){failed=true}
            finally { busy=false }
        }}){Text(stringResource(R.string.save))}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text(stringResource(R.string.cancel))}})
}

@Composable private fun CodeField(value: String, label: String, isError: Boolean = false, onChange: (String) -> Unit) =
    OutlinedTextField(value, { if (it.all(Char::isDigit) && it.length <= 12) onChange(it) }, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, isError = isError,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))

@Composable private fun SetupDialog(onDismiss: () -> Unit, backup: suspend (Uri, CharArray) -> Boolean, onEnable: (Disguise.Shell, String, String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var backedUp by remember { mutableStateOf(false) }; var backingUp by remember { mutableStateOf(false) }; var backupFailed by remember { mutableStateOf(false) }
    var askPassword by remember { mutableStateOf(false) }; var password by remember { mutableStateOf<CharArray?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pw = password; password = null
        if (uri != null && pw != null) { backingUp = true; scope.launch { val ok = backup(uri, pw); backingUp = false; backedUp = ok; backupFailed = !ok } }
    }
    var shell by remember { mutableStateOf(Disguise.Shell.CALCULATOR) }
    var a by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }; var alternate by remember { mutableStateOf("") }
    val alternateOk = alternate.isEmpty() || (AppLock.validPin(alternate) && alternate != a)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.disguise_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Step 1: an encrypted backup must be saved before the disguise can be turned on.
            Text(stringResource(R.string.disguise_backup), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            when {
                backedUp -> Text(stringResource(R.string.disguise_backup_done), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                backingUp -> LinearProgressIndicator(Modifier.fillMaxWidth())
                else -> OutlinedButton(onClick = { askPassword = true }) { Text(stringResource(R.string.disguise_backup_action)) }
            }
            if (backupFailed) Text(stringResource(R.string.disguise_backup_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.disguise_shell), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Disguise.Shell.entries.forEachIndexed { i, s -> SegmentedButton(shell == s, { shell = s }, SegmentedButtonDefaults.itemShape(i, Disguise.Shell.entries.size)) { Text(stringResource(s.label)) } }
            }
            Text(stringResource(if (shell == Disguise.Shell.CALCULATOR) R.string.disguise_how_calc else R.string.disguise_how_notes), style = MaterialTheme.typography.bodySmall)
            CodeField(a, stringResource(R.string.disguise_code)) { a = it }
            CodeField(b, stringResource(R.string.disguise_code_repeat), b.isNotEmpty() && a != b) { b = it }
            if (b.isNotEmpty() && a != b) Text(stringResource(R.string.disguise_mismatch), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            CodeField(alternate, stringResource(R.string.disguise_private_code), !alternateOk) { alternate = it }
            Text(stringResource(if (alternate.isNotEmpty() && alternate == a) R.string.disguise_same else R.string.disguise_private_desc), style = MaterialTheme.typography.bodySmall,
                color = if (alternate.isNotEmpty() && alternate == a) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.disguise_exit), style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button(enabled = backedUp && AppLock.validPin(a) && a == b && alternateOk, onClick = { onEnable(shell, a, alternate.ifEmpty { null }) }) { Text(stringResource(R.string.disguise_enable)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
    if (askPassword) PasswordDialog(stringResource(R.string.backup_export), stringResource(R.string.backup_password_note), confirm = true, { askPassword = false }) { pw ->
        askPassword = false; password = pw; backupFailed = false; saveLauncher.launchPicker("notes-${java.time.LocalDate.now()}.pnbak") }
}
