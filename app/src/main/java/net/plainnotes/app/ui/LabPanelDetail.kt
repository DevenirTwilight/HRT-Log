package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.plainnotes.app.R
import net.plainnotes.app.data.LabPanelEntity
import net.plainnotes.app.data.LabValueEntity
import java.time.Instant
import java.time.LocalDateTime

/**
 * LAB-02 (docs/design/lab-panel-adr.md §10): one blood test as a whole. Time, laboratory and note apply to every result;
 * results open the existing detail, can be taken out (they stay, on their own) or added with the batch row editor; the
 * whole draw goes to the recycle bin in one piece.
 */
@Composable fun LabPanelDialog(panel: LabPanelEntity, members: List<LabValueEntity>, onDismiss: () -> Unit,
                               onSave: (LocalDateTime, String, String) -> Unit, onOpen: (LabValueEntity) -> Unit, onRemove: (LabValueEntity) -> Unit,
                               onAdd: (List<LabValueEntity>, Boolean) -> Unit, onDelete: () -> Unit, files: (@Composable () -> Unit)?=null, overlay: (@Composable () -> Unit)?=null) {
    var time by remember(panel) { mutableStateOf(Instant.ofEpochMilli(panel.sampled_utc).toLocalHere()) }
    var lab by remember(panel) { mutableStateOf(panel.laboratory.orEmpty()) }
    var note by remember(panel) { mutableStateOf(panel.note.orEmpty()) }
    var nextKey by remember { mutableIntStateOf(1) }
    val added = remember { mutableStateListOf<LabBatchRow>() }
    var includeEstimate by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val present = members.map { it.analyte_code }.toSet()
    val duplicates = LabBatchRules.duplicates(added) + added.filterNot(LabBatchRules::blank).map { it.code }.filter { it in present }
    val changed = time != Instant.ofEpochMilli(panel.sampled_utc).toLocalHere() || lab.trim() != panel.laboratory.orEmpty() || note.trim() != panel.note.orEmpty()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Keep system BACK in this window. A subsequently composed authenticated
        // file viewer registers above this handler and closes its own page first.
        androidx.activity.compose.BackHandler(onBack=onDismiss)
        val initialFocus=remember {FocusRequester()}
        LaunchedEffect(Unit) {initialFocus.requestFocus()}
        Surface(Modifier.fillMaxSize().testTag("lab-panel-detail"), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss,modifier=Modifier.focusRequester(initialFocus)) { Icon(Icons.Outlined.Close, stringResource(R.string.cancel)) }
                    Text(stringResource(R.string.lab_panel_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).testTag("lab-panel-scroll"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    files?.invoke()
                    DateTimeRow(time, { time = it })
                    OutlinedTextField(lab, { lab = it }, Modifier.fillMaxWidth().testTag("lab-panel-lab"), label = { Text(stringResource(R.string.lab_laboratory)) }, singleLine = true)
                    OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth().testTag("lab-panel-note-field"), label = { Text(stringResource(R.string.lab_panel_note)) })
                    Button(onClick = { onSave(time, lab, note) }, enabled = changed, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("lab-panel-save")) {
                        Text(stringResource(R.string.lab_panel_save_changes))
                    }
                    Text(stringResource(R.string.lab_panel_members), style = sectionTitleStyle, modifier = Modifier.semantics { heading() })
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = Corners.list, modifier = Modifier.fillMaxWidth()) {
                        Column { members.forEachIndexed { i, v ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) { LabGroupRow(v) { onOpen(v) } }
                                IconButton(onClick = { onRemove(v) }, modifier = Modifier.testTag("lab-panel-remove:${v.id}")) {
                                    Icon(Icons.Outlined.RemoveCircleOutline, stringResource(R.string.lab_panel_remove_member, analyteLabel(v.analyte_code)))
                                }
                            }
                        } }
                    }
                    added.forEachIndexed { i, r -> key(r.key) {
                        LabBatchRowEditor(i, r, r.code in duplicates && !LabBatchRules.blank(r), true) { added.remove(r) }
                    } }
                    OutlinedButton(onClick = { added.add(LabBatchRow(nextKey++, "E2")) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("lab-panel-add-row")) {
                        Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.lab_batch_add_row))
                    }
                    if (added.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(includeEstimate, { includeEstimate = it }); Text(stringResource(R.string.lab_context_include_estimate))
                        }
                        Button(onClick = { onAdd(LabBatchRules.build(added, time, lab), includeEstimate); added.clear() },
                            enabled = LabBatchRules.canSave(added) && duplicates.isEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("lab-panel-save-added")) {
                            Text(stringResource(R.string.lab_panel_save_added))
                        }
                    }
                    OutlinedButton(onClick = { deleting = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("lab-panel-delete"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.lab_panel_delete))
                    }
                }
            }
            overlay?.invoke()
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, modifier = Modifier.testTag("lab-panel-delete-dialog"),
        text = { Text(stringResource(R.string.lab_panel_delete_confirm, members.size)) },
        confirmButton = { Button(onClick = { deleting = false; onDelete() }, modifier = Modifier.testTag("lab-panel-delete-confirm")) { Text(stringResource(R.string.remove)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } })
}

/** LAB-02 §3: grouping results that have no panel happens only here, after the user has seen what will be grouped. */
@Composable fun LabSaveAsPanelDialog(group: List<LabValueEntity>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val first = group.first()
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("lab-save-as-panel-dialog"), title = { Text(stringResource(R.string.lab_panel_save_as)) },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(listOfNotNull(formatDateTime(Instant.ofEpochMilli(first.sampled_utc)), first.laboratory?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" · "),
                style = MaterialTheme.typography.titleSmall)
            group.forEach { v -> Text("${analyteLabel(v.analyte_code)} · ${displayNumber(v.value)} ${v.unit}", style = MaterialTheme.typography.bodyMedium) }
            Text(stringResource(R.string.lab_panel_save_as_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { Button(onClick = onConfirm, modifier = Modifier.testTag("lab-save-as-panel-confirm")) { Text(stringResource(R.string.lab_panel_save_as)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
