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
import net.plainnotes.app.R
import net.plainnotes.app.security.AppLock
import net.plainnotes.app.ui.SectionCard

@Composable fun DisguiseSection(onDisabled: () -> Unit) {
    val context = LocalContext.current
    var shell by remember { mutableStateOf(Disguise.shell(context)) }
    var setup by remember { mutableStateOf(false) }
    var confirmOff by remember { mutableStateOf(false) }
    SectionCard(stringResource(R.string.disguise_title)) {
        Text(stringResource(R.string.disguise_desc), style = MaterialTheme.typography.bodyMedium)
        val on = shell
        if (on == null) Button(onClick = { setup = true }) { Text(stringResource(R.string.disguise_setup)) }
        else {
            Text(stringResource(R.string.disguise_on, stringResource(on.label)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(if (on == Disguise.Shell.CALCULATOR) R.string.disguise_how_calc else R.string.disguise_how_notes), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.disguise_exit), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { confirmOff = true }) { Text(stringResource(R.string.disguise_disable)) }
        }
        Text(stringResource(R.string.disguise_limits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (setup) SetupDialog({ setup = false }) { s, code, decoy -> Disguise.enable(context, s, code, decoy); shell = s; setup = false }
    if (confirmOff) AlertDialog(onDismissRequest = { confirmOff = false }, title = { Text(stringResource(R.string.disguise_disable)) },
        text = { Text(stringResource(R.string.disguise_disable_desc)) },
        confirmButton = { Button(onClick = { Disguise.disable(context); shell = null; confirmOff = false; onDisabled() }) { Text(stringResource(R.string.disguise_disable)) } },
        dismissButton = { TextButton(onClick = { confirmOff = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun CodeField(value: String, label: String, isError: Boolean = false, onChange: (String) -> Unit) =
    OutlinedTextField(value, { if (it.all(Char::isDigit) && it.length <= 12) onChange(it) }, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, isError = isError,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))

@Composable private fun SetupDialog(onDismiss: () -> Unit, onEnable: (Disguise.Shell, String, String?) -> Unit) {
    var shell by remember { mutableStateOf(Disguise.Shell.CALCULATOR) }
    var a by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }; var decoy by remember { mutableStateOf("") }
    var understood by remember { mutableStateOf(false) }
    val decoyOk = decoy.isEmpty() || (AppLock.validPin(decoy) && decoy != a)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.disguise_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.disguise_backup), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
            Row { Checkbox(understood, { understood = it }); Text(stringResource(R.string.disguise_understood), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium) }
        } },
        confirmButton = { Button(enabled = understood && AppLock.validPin(a) && a == b && decoyOk, onClick = { onEnable(shell, a, decoy.ifEmpty { null }) }) { Text(stringResource(R.string.disguise_enable)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
