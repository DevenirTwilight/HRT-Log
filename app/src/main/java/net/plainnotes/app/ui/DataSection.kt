package net.plainnotes.app.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.R
import net.plainnotes.app.security.launchPicker
import net.plainnotes.app.importer.TmExport
import net.plainnotes.app.importer.TransMemo
import java.time.LocalDate

@Composable fun DataSection(model: NotesViewModel, scheduleTexts: Map<Long, String>) {
    val context = LocalContext.current
    val job by model.dataJob.collectAsStateWithLifecycle()
    var backupPassword by remember { mutableStateOf<CharArray?>(null) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var askBackup by remember { mutableStateOf(false) }
    var pdfOptions by remember { mutableStateOf(false) }
    var pdfChoice by remember { mutableStateOf(90 to true) }
    var wipe by remember { mutableStateOf(false) }
    val labels = checkinLabels()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::openTransMemo) }
    val htLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::openHrtTracker) }
    val state by model.state.collectAsStateWithLifecycle()
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pw = backupPassword; backupPassword = null; if (uri != null && pw != null) model.exportBackup(uri, pw) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreUri = it }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let { u -> model.exportCsv(u, labels, scheduleTexts) } }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { it?.let { u -> model.exportPdf(u, pdfChoice.first, pdfChoice.second, context, labels, scheduleTexts) } }

    SectionCard(stringResource(R.string.data)) {
        DataRow(Icons.Outlined.MoveToInbox, stringResource(R.string.import_ht), stringResource(R.string.import_ht_desc)) { htLauncher.launchPicker(arrayOf("application/json", "text/plain", "*/*")) }
        DataRow(Icons.Outlined.MoveToInbox, stringResource(R.string.import_tm), stringResource(R.string.import_tm_desc)) { importLauncher.launchPicker(arrayOf("*/*")) }
        DataRow(Icons.Outlined.Lock, stringResource(R.string.backup_export), stringResource(R.string.backup_export_desc)) { askBackup = true }
        DataRow(Icons.Outlined.SettingsBackupRestore, stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_desc)) { restoreLauncher.launchPicker(arrayOf("*/*")) }
        DataRow(Icons.Outlined.TableChart, stringResource(R.string.export_csv), stringResource(R.string.export_csv_desc)) { csvLauncher.launchPicker("notes-export-${LocalDate.now()}.zip") }
        DataRow(Icons.Outlined.PictureAsPdf, stringResource(R.string.export_pdf), stringResource(R.string.export_pdf_desc)) { pdfOptions = true }
        HorizontalDivider()
        DataRow(Icons.Outlined.DeleteForever, stringResource(R.string.wipe), stringResource(R.string.wipe_desc), danger = true) { wipe = true }
    }

    if (askBackup) PasswordDialog(stringResource(R.string.backup_export), stringResource(R.string.backup_password_note), confirm = true, { askBackup = false }) { pw ->
        askBackup = false; backupPassword = pw; backupLauncher.launchPicker("notes-${LocalDate.now()}.pnbak") }
    restoreUri?.let { uri -> PasswordDialog(stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_warning), confirm = false, { restoreUri = null }) { pw ->
        restoreUri = null; model.restoreBackup(uri, pw) } }
    if (pdfOptions) AlertDialog(onDismissRequest = { pdfOptions = false }, icon = { Icon(Icons.Outlined.PictureAsPdf, null) }, title = { Text(stringResource(R.string.export_pdf)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.report_period_label), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { listOf(30, 90, 180, 365).forEachIndexed { i, d ->
                SegmentedButton(pdfChoice.first == d, { pdfChoice = d to pdfChoice.second }, SegmentedButtonDefaults.itemShape(i, 4)) { Text(stringResource(R.string.days_short, d)) } } }
            SwitchRow(stringResource(R.string.report_include_chart), pdfChoice.second, stringResource(R.string.report_include_chart_desc)) { pdfChoice = pdfChoice.first to it }
        } },
        confirmButton = { Button(onClick = { pdfOptions = false; if (pdfChoice.second) model.loadConcentration(); pdfLauncher.launchPicker("notes-report-${LocalDate.now()}.pdf") }) { Text(stringResource(R.string.export_action)) } },
        dismissButton = { TextButton(onClick = { pdfOptions = false }) { Text(stringResource(R.string.cancel)) } })
    if (wipe) WipeDialog({ wipe = false }) { wipe = false; model.wipeAll { (context as? Activity)?.let { a -> a.finish(); a.startActivity(Intent(a, a.javaClass)) } } }

    when (val j = job) {
        is NotesViewModel.DataJob.Working -> AlertDialog(onDismissRequest = {}, confirmButton = {}, text = { Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(16.dp)); Text(stringResource(R.string.working)) } })
        is NotesViewModel.DataJob.Done -> AlertDialog(onDismissRequest = model::clearDataJob, icon = { Icon(Icons.Outlined.CheckCircle, null) }, text = { Text(stringResource(j.message)) },
            confirmButton = { TextButton(onClick = model::clearDataJob) { Text(stringResource(R.string.ok)) } })
        is NotesViewModel.DataJob.Failed -> AlertDialog(onDismissRequest = model::clearDataJob, icon = { Icon(Icons.Outlined.ErrorOutline, null) }, text = { Text(stringResource(j.message)) },
            confirmButton = { TextButton(onClick = model::clearDataJob) { Text(stringResource(R.string.ok)) } })
        is NotesViewModel.DataJob.ImportReady -> ImportWizard(j.export, j.preview, model::cancelImport) { c, overwrite -> model.runImport(j.export, c, overwrite) }
        is NotesViewModel.DataJob.Imported -> AlertDialog(onDismissRequest = model::clearDataJob, icon = { Icon(Icons.Outlined.CheckCircle, null) }, title = { Text(stringResource(R.string.import_done)) },
            text = { Text(stringResource(R.string.import_done_body, j.summary.medications, j.summary.reusedMedications, j.summary.intakes, j.summary.duplicates, j.summary.containers,
                j.summary.scores, j.summary.notes, j.summary.appointments)) },
            confirmButton = { TextButton(onClick = model::clearDataJob) { Text(stringResource(R.string.ok)) } })
        is NotesViewModel.DataJob.HtReady -> HtImportWizard(j.export, j.preview, state.medications, state.profiles, model::clearDataJob) { d, t, n, w -> model.runHtImport(j.export, d, t, n, w) }
        is NotesViewModel.DataJob.HtImported -> AlertDialog(onDismissRequest = model::clearDataJob, icon = { Icon(Icons.Outlined.CheckCircle, null) }, title = { Text(stringResource(R.string.import_done)) },
            text = { Text(stringResource(R.string.ht_done, j.summary.intakes, j.summary.medications, j.summary.alreadyImported, j.summary.labs)) },
            confirmButton = { TextButton(onClick = model::clearDataJob) { Text(stringResource(R.string.ok)) } })
        else -> {}
    }
}

@Composable private fun DataRow(icon: ImageVector, title: String, desc: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (danger) color else MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp))
            Column { Text(title, style = MaterialTheme.typography.bodyLarge, color = color); Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable fun PasswordDialog(title: String, note: String, confirm: Boolean, onDismiss: () -> Unit, onOk: (CharArray) -> Unit) {
    var pw by remember { mutableStateOf("") }; var pw2 by remember { mutableStateOf("") }
    val ok = pw.length >= 8 && (!confirm || pw == pw2)
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.Lock, null) }, title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(note, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.backup_password)) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = { Text(stringResource(R.string.backup_password_min)) })
            if (confirm) OutlinedTextField(pw2, { pw2 = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.backup_password_repeat)) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), isError = pw2.isNotEmpty() && pw != pw2)
        } },
        confirmButton = { Button(enabled = ok, onClick = { onOk(pw.toCharArray()) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun WipeDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val word = stringResource(R.string.wipe_word)
    var typed by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error) }, title = { Text(stringResource(R.string.wipe)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.wipe_body, word))
            OutlinedTextField(typed, { typed = it }, Modifier.fillMaxWidth(), singleLine = true)
        } },
        confirmButton = { Button(enabled = typed.trim().equals(word, ignoreCase = true), onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.wipe_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun <T> Radio(label: String, selected: Boolean, value: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected) { onSelect(value) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, { onSelect(value) }); Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ImportWizard(export: TmExport, p: TransMemo.Preview, onCancel: () -> Unit, onImport: (TransMemo.Choices, Boolean) -> Unit) {
    val withIntakes = export.products.filter { pr -> export.intakes.any { it.productId == pr.id && it.takenAt != null && (it.state == "TAKEN" || it.state == "LATE") } }
    val late = remember { mutableStateMapOf<Long, String>() }
    var lateHandling by remember { mutableStateOf<TransMemo.LateHandling?>(null) }
    var scale by remember { mutableStateOf<TransMemo.WellbeingScale?>(null) }
    var overwrite by remember { mutableStateOf<Boolean?>(null) }
    val lateValues = withIntakes.associate { it.id to late[it.id]?.toIntOrNull()?.takeIf { v -> v >= 0 } }
    val ready = lateValues.values.all { it != null } && (!p.needsLateChoice || lateHandling != null) && (!p.needsWellbeingChoice || scale != null) && overwrite != null
    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.import_tm)) }, navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, stringResource(R.string.cancel)) } },
            actions = { TextButton(enabled = ready, onClick = { onImport(TransMemo.Choices(lateValues.mapValues { it.value!! }, lateHandling, scale), overwrite!!) }) { Text(stringResource(R.string.import_action)) } }) }) { pad ->
            Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionCard(stringResource(R.string.import_preview)) {
                    Text(stringResource(R.string.import_preview_body, p.products, p.takenIntakes, p.missed, p.pending, p.containers, p.wellbeingScores, p.notesWithText, p.appointments), style = MaterialTheme.typography.bodyMedium)
                    p.intakeRange?.let { (a, b) -> Text(stringResource(R.string.import_range, formatDateTime(a), formatDateTime(b)), style = MaterialTheme.typography.bodySmall) }
                    val notes = buildList {
                        if (p.takenWithoutTime > 0) add(stringResource(R.string.import_note_taken_no_time, p.takenWithoutTime))
                        if (p.unknownStates > 0) add(stringResource(R.string.import_note_unknown_states, p.unknownStates))
                        if (p.unknownContainerStates > 0) add(stringResource(R.string.import_note_containers, p.unknownContainerStates))
                        if (p.emptyNotes > 0) add(stringResource(R.string.import_note_empty_notes, p.emptyNotes))
                        if (p.unknownMolecules.isNotEmpty() || p.unknownUnits.isNotEmpty()) add(stringResource(R.string.import_note_unknown_codes, (p.unknownMolecules + p.unknownUnits).joinToString()))
                        if (p.inferredMolecules.isNotEmpty()) add(stringResource(R.string.import_note_inferred, p.inferredMolecules.joinToString()))
                        add(stringResource(R.string.import_note_paused))
                    }
                    notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                SectionCard(stringResource(R.string.import_mode)) {
                    Radio(stringResource(R.string.import_merge), overwrite == false, false) { overwrite = it }
                    Radio(stringResource(R.string.import_overwrite), overwrite == true, true) { overwrite = it }
                    if (overwrite == true) Text(stringResource(R.string.import_overwrite_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (withIntakes.isNotEmpty()) SectionCard(stringResource(R.string.import_late_title)) {
                    Text(stringResource(R.string.import_late_body), style = MaterialTheme.typography.bodySmall)
                    withIntakes.forEach { pr -> NumberField(late[pr.id] ?: "", { late[pr.id] = it }, pr.name?.takeIf { it.isNotBlank() } ?: (pr.molecule ?: "?"),
                        suffix = stringResource(R.string.minutes_unit), decimal = false, supporting = stringResource(R.string.import_late_raw, pr.lateAlertDelay?.toString() ?: "—"), isError = late[pr.id] != null && lateValues[pr.id] == null) }
                }
                if (p.needsLateChoice) SectionCard(stringResource(R.string.import_late_state_title)) {
                    Text(stringResource(R.string.import_late_state_body, p.lateWithoutTime), style = MaterialTheme.typography.bodySmall)
                    Radio(stringResource(R.string.import_late_skip), lateHandling == TransMemo.LateHandling.SKIP, TransMemo.LateHandling.SKIP) { lateHandling = it }
                    Radio(stringResource(R.string.import_late_missed), lateHandling == TransMemo.LateHandling.AS_MISSED, TransMemo.LateHandling.AS_MISSED) { lateHandling = it }
                }
                if (p.needsWellbeingChoice) SectionCard(stringResource(R.string.import_scale_title)) {
                    Text(stringResource(R.string.import_scale_body, p.wellbeingValues.toSortedMap().entries.joinToString { "${it.key} × ${it.value}" }), style = MaterialTheme.typography.bodySmall)
                    Radio(stringResource(R.string.import_scale_1_5), scale == TransMemo.WellbeingScale.ONE_TO_FIVE, TransMemo.WellbeingScale.ONE_TO_FIVE) { scale = it }
                    Radio(stringResource(R.string.import_scale_0_4), scale == TransMemo.WellbeingScale.ZERO_BASED, TransMemo.WellbeingScale.ZERO_BASED) { scale = it }
                    Radio(stringResource(R.string.import_scale_skip), scale == TransMemo.WellbeingScale.SKIP, TransMemo.WellbeingScale.SKIP) { scale = it }
                }
                if (!ready) Text(stringResource(R.string.import_need_choices), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Label resolver usable outside composition (exports run in coroutines). */
@Composable fun checkinLabels(): (net.plainnotes.app.data.CheckinItemEntity) -> String {
    val res = LocalContext.current.resources
    return { item -> item.custom_label ?: res.getString(when (item.builtin_key) {
        "OVERALL" -> R.string.wb_overall; "MOOD" -> R.string.wb_mood; "EMO_STABILITY" -> R.string.wb_emo_stability; "ENERGY" -> R.string.wb_energy
        "AGGRESSIVENESS" -> R.string.wb_aggressiveness; "LIBIDO" -> R.string.wb_libido; "PAIN" -> R.string.wb_pain; "PERIOD_LIKE" -> R.string.wb_period_like
        "APPETITE" -> R.string.wb_appetite; "SLEEP_QUALITY" -> R.string.wb_sleep; "SKIN_QUALITY" -> R.string.wb_skin; else -> R.string.choice_other }) }
}
