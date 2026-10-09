package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.biometric.BiometricManager
import net.plainnotes.app.R
import net.plainnotes.app.reminder.NotificationPrefs
import net.plainnotes.app.security.AppLock

/** True when simple mode hides multi-medication UI. */
val LocalSimpleMode = compositionLocalOf { false }

@Composable fun PrivacySection(activeMedications: Int, simpleMode: Boolean, onSimpleMode: (Boolean) -> Unit) {
    val context = LocalContext.current
    val lock = remember { AppLock(context) }; val prefs = remember { UiPrefs(context) }; val notify = remember { NotificationPrefs(context) }
    var lockOn by remember { mutableStateOf(lock.enabled) }
    var bio by remember { mutableStateOf(lock.biometric) }
    var after by remember { mutableLongStateOf(prefs.lockAfterMillis) }
    var setPin by remember { mutableStateOf(false) }
    var confirmOff by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(notify.title ?: "") }; var body by remember { mutableStateOf(notify.body ?: "") }
    var details by remember { mutableStateOf(notify.details) }
    val bioAvailable = remember { BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS }
    SectionCard(stringResource(R.string.privacy_security)) {
        SwitchRow(stringResource(R.string.lock_enable), lockOn, stringResource(R.string.lock_enable_desc)) { if (it) setPin = true else confirmOff = true }
        if (lockOn) {
            if (bioAvailable) SwitchRow(stringResource(R.string.lock_biometric), bio) { bio = it; lock.biometric = it }
            Text(stringResource(R.string.lock_after), style = MaterialTheme.typography.labelLarge)
            val times=listOf(0L,30_000L,300_000L)
            val labels=listOf(R.string.lock_after_now,R.string.lock_after_30s,R.string.lock_after_5m).map{stringResource(it)}
            AdaptiveButtonChoice(labels,times.indexOf(after),{i->after=times[i];prefs.lockAfterMillis=times[i]})
            TextButton(onClick = { setPin = true }) { Text(stringResource(R.string.lock_change_pin)) }
        }
        HorizontalDivider()
        Text(stringResource(R.string.notif_text_title), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.notif_text_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(title, { title = it; notify.title = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.notif_title)) }, placeholder = { Text(stringResource(R.string.neutral_reminder_default)) }, singleLine = true)
        OutlinedTextField(body, { body = it; notify.body = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.notif_body)) }, placeholder = { Text(stringResource(R.string.neutral_open_default)) }, singleLine = true)
        SwitchRow(stringResource(R.string.notif_details), details, stringResource(if (notify.disguised) R.string.notif_details_disguised else R.string.notif_details_desc)) { details = it; notify.details = it }
        HorizontalDivider()
        SwitchRow(stringResource(R.string.simple_mode), simpleMode && activeMedications <= 1, stringResource(if (activeMedications <= 1) R.string.simple_mode_desc else R.string.simple_mode_unavailable)) {
            if (activeMedications <= 1) onSimpleMode(it) }
        Text(stringResource(R.string.security_limits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (setPin) NewPinDialog({ setPin = false }) { pin -> lock.setPin(pin); lockOn = true; setPin = false }
    if (confirmOff) AlertDialog(onDismissRequest = { confirmOff = false }, icon = { Icon(Icons.Outlined.Lock, null) }, text = {
        PinEntry(stringResource(R.string.lock_enter_current)) { pin -> if (lock.verify(pin)) { lock.disable(); lockOn = false; bio = false; confirmOff = false; true } else false } },
        confirmButton = {}, dismissButton = { TextButton(onClick = { confirmOff = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun PinEntry(label: String, onSubmit: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }; var wrong by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(pin, { if (it.all(Char::isDigit) && it.length <= 12) pin = it }, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, isError = wrong,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        if (wrong) Text(stringResource(R.string.lock_wrong), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(enabled = AppLock.validPin(pin), onClick = { wrong = !onSubmit(pin); if (wrong) pin = "" }) { Text(stringResource(R.string.ok)) }
    }
}

@Composable fun NewPinDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var a by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.Lock, null) }, title = { Text(stringResource(R.string.lock_set_pin)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.lock_pin_rules), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(a, { if (it.all(Char::isDigit) && it.length <= 12) a = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.lock_new_pin)) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            OutlinedTextField(b, { if (it.all(Char::isDigit) && it.length <= 12) b = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.lock_repeat_pin)) }, singleLine = true,
                isError = b.isNotEmpty() && a != b, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        } },
        confirmButton = { Button(enabled = AppLock.validPin(a) && a == b, onClick = { onSet(a) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
