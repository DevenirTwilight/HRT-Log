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
import net.plainnotes.app.R
import net.plainnotes.app.security.launchPicker
import net.plainnotes.app.ui.PasswordDialog
import net.plainnotes.app.security.AppLock
import net.plainnotes.app.ui.SectionCard

@Composable fun DisguiseSection(onDisabled: () -> Unit, backup: suspend (Uri, CharArray) -> Boolean) {
    val context = LocalContext.current
    var shell by remember { mutableStateOf(Disguise.shell(context)) }
    var setup by remember { mutableStateOf(false) }
    var explained by remember { mutableStateOf<Disguise.Shell?>(null) }
    var confirmOff by remember { mutableStateOf(false) }
    SectionCard(stringResource(R.string.disguise_title)) {
        Text(stringResource(R.string.disguise_desc), style = MaterialTheme.typography.bodyMedium)
        val on = shell
        if (on == null) Button(onClick = { setup = true }) { Text(stringResource(R.string.disguise_setup)) }
        else {
            Text(stringResource(R.string.disguise_on, stringResource(on.label)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(if (on == Disguise.Shell.CALCULATOR) R.string.disguise_how_calc else R.string.disguise_how_notes), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.disguise_exit), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.disguise_lock_note), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { confirmOff = true }) { Text(stringResource(R.string.disguise_disable)) }
        }
        Text(stringResource(R.string.disguise_limits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (setup) SetupDialog({ setup = false }, backup) { s, code, decoy -> Disguise.enable(context, s, code, decoy); shell = s; setup = false; explained = s }
    explained?.let { s -> AlertDialog(onDismissRequest = { explained = null }, title = { Text(stringResource(R.string.disguise_enabled_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.disguise_guide_enter, stringResource(s.label)), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(if (s == Disguise.Shell.CALCULATOR) R.string.disguise_how_calc else R.string.disguise_how_notes), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.disguise_exit), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.disguise_lock_note), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.disguise_guide_disable), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.disguise_guide_forgot), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { Button(onClick = { explained = null }) { Text(stringResource(R.string.ok)) } }) }
    if (confirmOff) AlertDialog(onDismissRequest = { confirmOff = false }, title = { Text(stringResource(R.string.disguise_disable)) },
        text = { Text(stringResource(R.string.disguise_disable_desc)) },
        confirmButton = { Button(onClick = { Disguise.disable(context); shell = null; confirmOff = false; onDisabled() }) { Text(stringResource(R.string.disguise_disable)) } },
        dismissButton = { TextButton(onClick = { confirmOff = false }) { Text(stringResource(R.string.cancel)) } })
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
    var a by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }; var decoy by remember { mutableStateOf("") }
    val decoyOk = decoy.isEmpty() || (AppLock.validPin(decoy) && decoy != a)
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
            CodeField(decoy, stringResource(R.string.disguise_decoy), !decoyOk) { decoy = it }
            Text(stringResource(if (decoy.isNotEmpty() && decoy == a) R.string.disguise_same else R.string.disguise_decoy_desc), style = MaterialTheme.typography.bodySmall,
                color = if (decoy.isNotEmpty() && decoy == a) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.disguise_exit), style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button(enabled = backedUp && AppLock.validPin(a) && a == b && decoyOk, onClick = { onEnable(shell, a, decoy.ifEmpty { null }) }) { Text(stringResource(R.string.disguise_enable)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
    if (askPassword) PasswordDialog(stringResource(R.string.backup_export), stringResource(R.string.backup_password_note), confirm = true, { askPassword = false }) { pw ->
        askPassword = false; password = pw; backupFailed = false; saveLauncher.launchPicker("notes-${java.time.LocalDate.now()}.pnbak") }
}
