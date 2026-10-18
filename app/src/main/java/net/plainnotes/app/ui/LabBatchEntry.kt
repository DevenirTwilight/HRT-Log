package net.plainnotes.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.plainnotes.app.R
import net.plainnotes.app.data.LabPanelEntity
import net.plainnotes.app.data.LabValueEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * LAB-01 (REQUIREMENTS §111): one blood draw, many results. Date, time and laboratory are entered once; each row becomes an
 * ordinary lab result written exactly as the single-result dialog writes it. Grouping on the labs page is display only
 * (same sampled_utc and same trimmed laboratory text); it is never stored, backed up or exported.
 */
private val P4_FAMILY = setOf("P4", "P4_IA", "P4_MS")

/** Shortcuts that add empty rows; input convenience only, not advice about which tests to take. */
enum class LabPreset(val label: Int, val codes: List<String>) {
    HORMONES(R.string.lab_preset_hormones, listOf("FSH", "LH", "E2", "P4", "T", "PRL")),
    LIVER(R.string.lab_preset_liver, listOf("ALT", "AST", "GGT")),
    KIDNEY(R.string.lab_preset_kidney, listOf("CREA", "K")),
}

@Stable class LabBatchRow(val key: Int, code: String) {
    var code by mutableStateOf(code)
    var unit by mutableStateOf(analyte(code).units.first().first)
    var value by mutableStateOf("")
    var lo by mutableStateOf("")
    var hi by mutableStateOf("")
    var note by mutableStateOf("")
    var rangeOpen by mutableStateOf(false)
    fun pick(c: String) { code = c; unit = analyte(c).units.first().first }
}

/** The single-result dialog's rules, row by row: value finite and > 0; each range bound blank or a number; low ≤ high. */
internal object LabBatchRules {
    fun value(s: String) = s.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    fun rangeOk(lo: String, hi: String): Boolean {
        val l = lo.toDoubleOrNull(); val h = hi.toDoubleOrNull()
        return (lo.isBlank() || l != null) && (hi.isBlank() || h != null) && (l == null || h == null || l <= h)
    }
    fun blank(r: LabBatchRow) = r.value.isBlank() && r.lo.isBlank() && r.hi.isBlank() && r.note.isBlank()
    fun valid(r: LabBatchRow) = value(r.value) != null && rangeOk(r.lo, r.hi)
    /** Codes used by more than one non-blank row. */
    fun duplicates(rows: List<LabBatchRow>) = rows.filterNot(::blank).groupingBy { it.code }.eachCount().filterValues { it > 1 }.keys
    fun canSave(rows: List<LabBatchRow>) = rows.filterNot(::blank).let { it.isNotEmpty() && it.all(::valid) } && duplicates(rows).isEmpty()
    /** The same fields LabDialog fills for a new result. */
    fun build(rows: List<LabBatchRow>, time: LocalDateTime, lab: String, zone: ZoneId = ZoneId.systemDefault()): List<LabValueEntity> =
        rows.filterNot(::blank).map { r ->
            val lo = r.lo.toDoubleOrNull(); val hi = r.hi.toDoubleOrNull()
            LabValueEntity(0, r.code, value(r.value)!!, r.unit, time.toInstantHere().toEpochMilli(), zone.id,
                lo, hi, if (lo != null || hi != null) r.unit else null, lab.trim().ifEmpty { null }, r.note.ifBlank { null })
        }
    /** A preset adds rows for codes not already present (any progesterone method counts as P4); one untouched row is replaced. */
    fun applyPreset(rows: List<LabBatchRow>, preset: LabPreset, nextKey: () -> Int): List<LabBatchRow> {
        val base = if (rows.size == 1 && blank(rows[0])) emptyList() else rows
        val present = base.map { if (it.code in P4_FAMILY) "P4" else it.code }.toSet()
        return base + preset.codes.filter { it !in present }.map { LabBatchRow(nextKey(), it) }
    }
}

/** Display grouping: identical sampled_utc and identical trimmed laboratory text (both empty counts as equal); newest first. */
internal fun labGroups(labs: List<LabValueEntity>): List<List<LabValueEntity>> {
    val order = ANALYTES.map { it.code }
    return labs.groupBy { it.sampled_utc to it.laboratory?.trim().orEmpty() }.values
        .map { g -> g.sortedWith(compareBy<LabValueEntity>({ order.indexOf(it.analyte_code).let { i -> if (i < 0) order.size else i } }, { it.id })) }
        .sortedWith(compareByDescending<List<LabValueEntity>> { it.first().sampled_utc }.thenByDescending { g -> g.maxOf { it.id } })
}

/** LAB-02: what the labs page shows for one draw: a real panel (even with one result), or a LAB-01 display group of results without a panel. */
data class LabDraw(val panel: LabPanelEntity?, val labs: List<LabValueEntity>)
internal fun labDraws(labs: List<LabValueEntity>, panels: List<LabPanelEntity>): List<LabDraw> {
    val order = ANALYTES.map { it.code }
    val byPanel = panels.associateBy { it.id }
    val real = labs.filter { it.panel_id != null && it.panel_id in byPanel }.groupBy { it.panel_id!! }.map { (id, g) ->
        LabDraw(byPanel.getValue(id), g.sortedWith(compareBy<LabValueEntity>({ order.indexOf(it.analyte_code).let { i -> if (i < 0) order.size else i } }, { it.id }))) }
    val loose = labGroups(labs.filter { it.panel_id == null || it.panel_id !in byPanel }).map { LabDraw(null, it) }
    return (real + loose).sortedWith(compareByDescending<LabDraw> { it.panel?.sampled_utc ?: it.labs.first().sampled_utc }.thenByDescending { d -> d.labs.maxOf { it.id } })
}

enum class LabFlag { HIGH, LOW }
/** Outside the report's own range, compared in one unit (converted when both units are known); null when it cannot be told. */
internal fun labFlag(v: LabValueEntity): LabFlag? {
    val lo = v.reference_lower; val hi = v.reference_upper; val ru = v.reference_unit ?: return null
    if (lo == null && hi == null) return null
    val a = analyte(v.analyte_code)
    val (x, l, h) = if (ru == v.unit) Triple(v.value, lo, hi) else {
        val x = toCanonical(a, v.value, v.unit) ?: return null
        Triple(x, lo?.let { toCanonical(a, it, ru) ?: return null }, hi?.let { toCanonical(a, it, ru) ?: return null })
    }
    return when { h != null && x > h -> LabFlag.HIGH; l != null && x < l -> LabFlag.LOW; else -> null }
}

/** One draw with several results: "date time · laboratory · n tests" over an inset list; a row opens the existing detail. */
/**
 * One draw: "date time · laboratory · n tests" over an inset list; a row opens the existing detail. A real lab panel (LAB-02)
 * shows its note and opens its own detail from the title; a LAB-01 display group without a panel offers "save as one blood test".
 */
@Composable fun LabGroupCard(group: List<LabValueEntity>, onOpen: (LabValueEntity) -> Unit, panel: LabPanelEntity? = null,
                             onPanel: (() -> Unit)? = null, onSaveAsPanel: (() -> Unit)? = null) {
    val first = group.first()
    val at = panel?.sampled_utc ?: first.sampled_utc; val place = (panel?.laboratory ?: first.laboratory)?.trim()?.takeIf { it.isNotEmpty() }
    ElevatedCard(Modifier.fillMaxWidth().testTag(if (panel != null) "lab-panel:${panel.id}" else "lab-group:${first.sampled_utc}:${first.laboratory?.trim().orEmpty()}"), shape = CardDefaults.elevatedShape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), elevation = CardDefaults.elevatedCardElevation(0.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val title = listOfNotNull(formatDateTime(Instant.ofEpochMilli(at)), place, stringResource(R.string.lab_group_count, group.size)).joinToString(" · ")
            if (onPanel != null) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = stringResource(R.string.lab_panel_open), onClick = onPanel)
                .testTag("lab-panel-open"), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = sectionTitleStyle, modifier = Modifier.weight(1f).semantics { heading() }.testTag("lab-group-title"))
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
            } else Text(title, style = sectionTitleStyle, modifier = Modifier.semantics { heading() }.testTag("lab-group-title"))
            panel?.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("lab-panel-note")) }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = Corners.list, modifier = Modifier.fillMaxWidth()) {
                Column { group.forEachIndexed { i, v ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    LabGroupRow(v) { onOpen(v) }
                } }
            }
            if (onSaveAsPanel != null) OutlinedButton(onClick = onSaveAsPanel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("lab-save-as-panel")) {
                Text(stringResource(R.string.lab_panel_save_as))
            }
        }
    }
}

@Composable internal fun LabGroupRow(v: LabValueEntity, onOpen: () -> Unit) {
    val name = analyteLabel(v.analyte_code); val number = displayNumber(v.value)
    val flag = labFlag(v)?.let { stringResource(if (it == LabFlag.HIGH) R.string.lab_flag_high else R.string.lab_flag_low) }
    val spoken = stringResource(R.string.lab_row_spoken, name, number, v.unit) + (flag?.let { stringResource(R.string.lab_row_spoken_flag, it) } ?: "")
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("lab-group-row:${v.id}").clickable(role = Role.Button, onClick = onOpen).clearAndSetSemantics {
        contentDescription = spoken; role = Role.Button; onClick { onOpen(); true } }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Two lines (name, then value and flag) so nothing is cut mid-word at 320dp and font 2x.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                Text("$number ${v.unit}", style = MaterialTheme.typography.bodyLarge.tnum(), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                flag?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("lab-flag:${v.id}")) }
            }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
    }
}

/**
 * "Record a blood test": full screen so the form scrolls at 320dp and font 2x, with save and cancel fixed at the bottom.
 * [onSave] gets every non-blank row as a new result and the estimate choice; the caller closes the form only when all were saved.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun LabBatchDialog(onDismiss: () -> Unit, onSave: (List<LabValueEntity>, Boolean) -> Unit, saving: Boolean = false, failed: Boolean = false,
                               showEstimate: Boolean = true) {
    var nextKey by remember { mutableIntStateOf(1) }
    val rows = remember { mutableStateListOf(LabBatchRow(0, "E2")) }
    var time by remember { mutableStateOf(Instant.now().toLocalHere()) }
    var lab by remember { mutableStateOf("") }
    var includeEstimate by remember { mutableStateOf(false) }
    val duplicates = LabBatchRules.duplicates(rows)
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("lab-batch"), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, enabled = !saving) { Icon(Icons.Outlined.Close, stringResource(R.string.cancel)) }
                    Text(stringResource(R.string.lab_batch_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).testTag("lab-batch-scroll"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateTimeRow(time, { time = it })
                    OutlinedTextField(lab, { lab = it }, Modifier.fillMaxWidth().testTag("lab-batch-lab"), label = { Text(stringResource(R.string.lab_laboratory)) }, singleLine = true)
                    Text(stringResource(R.string.lab_batch_presets), style = MaterialTheme.typography.labelLarge)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabPreset.entries.forEach { p ->
                            AssistChip(onClick = { val next = LabBatchRules.applyPreset(rows.toList(), p) { nextKey++ }; rows.clear(); rows.addAll(next) },
                                label = { Text(stringResource(p.label)) }, modifier = Modifier.testTag("lab-preset:${p.name}"))
                        }
                    }
                    rows.forEachIndexed { i, r -> key(r.key) {
                        LabBatchRowEditor(i, r, r.code in duplicates && !LabBatchRules.blank(r), rows.size > 1) { rows.remove(r) }
                    } }
                    OutlinedButton(onClick = { rows.add(LabBatchRow(nextKey++, "E2")) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("lab-batch-add-row")) {
                        Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.lab_batch_add_row))
                    }
                    if (showEstimate) {
                        Row(Modifier.fillMaxWidth().selectable(includeEstimate, onClick = { includeEstimate = !includeEstimate }, role = Role.Checkbox).testTag("lab-batch-estimate"),
                            verticalAlignment = Alignment.CenterVertically) { Checkbox(includeEstimate, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.lab_context_include_estimate)) }
                        Text(stringResource(R.string.lab_context_save_note), style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (failed) Text(stringResource(R.string.lab_batch_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("lab-batch-failed"))
                    AdaptiveActions(listOf(stringResource(R.string.cancel), stringResource(R.string.save))) { i, mod ->
                        if (i == 0) TextButton(onClick = onDismiss, enabled = !saving, modifier = mod.testTag("lab-batch-cancel")) { Text(stringResource(R.string.cancel)) }
                        else Button(onClick = { onSave(LabBatchRules.build(rows, time, lab), includeEstimate) }, enabled = !saving && LabBatchRules.canSave(rows),
                            modifier = mod.testTag("lab-batch-save")) { Text(stringResource(R.string.save)) }
                    }
                }
            }
        }
    }
}

@Composable internal fun LabBatchRowEditor(index: Int, r: LabBatchRow, duplicate: Boolean, removable: Boolean, onRemove: () -> Unit) {
    val a = analyte(r.code); val p4 = r.code in P4_FAMILY
    val valueV = LabBatchRules.value(r.value); val rangeOk = LabBatchRules.rangeOk(r.lo, r.hi)
    OutlinedCard(Modifier.fillMaxWidth().testTag("lab-batch-row:$index"), shape = Corners.list,
        border = CardDefaults.outlinedCardBorder().let { if (duplicate) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.error) else it }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    DropdownField(stringResource(R.string.lab_analyte), ANALYTES.map { it.code }.filter { it !in P4_FAMILY || it == "P4" }, if (p4) "P4" else r.code,
                        { if (it == "P4") stringResource(R.string.lab_p4_family) else analyteLabel(it) }, { r.pick(it) })
                }
                if (removable) IconButton(onClick = onRemove, modifier = Modifier.testTag("lab-batch-remove:$index")) {
                    Icon(Icons.Outlined.Delete, stringResource(R.string.lab_batch_remove_row, index + 1))
                }
            }
            if (duplicate) Text(stringResource(R.string.lab_batch_duplicate), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("lab-batch-duplicate:$index"))
            if (p4) {
                Text(stringResource(R.string.lab_p4_method), style = MaterialTheme.typography.labelLarge)
                listOf("P4_IA" to R.string.lab_p4_method_ia, "P4_MS" to R.string.lab_p4_method_ms, "P4" to R.string.lab_p4_method_unknown).forEach { (c, label) ->
                    Row(Modifier.fillMaxWidth().selectable(r.code == c, onClick = { r.code = c }, role = Role.RadioButton), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(r.code == c, null); Spacer(Modifier.width(8.dp)); Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(stringResource(R.string.lab_p4_method_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(r.value, { r.value = it }, stringResource(R.string.lab_value), Modifier.weight(1f).testTag("lab-batch-value:$index"), isError = r.value.isNotEmpty() && valueV == null)
                DropdownField(stringResource(R.string.unit), a.units.map { it.first }, r.unit, { it }, { r.unit = it }, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { r.rangeOpen = !r.rangeOpen }.testTag("lab-batch-range-toggle:$index"),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lab_range_optional), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Icon(if (r.rangeOpen || !rangeOk) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            if (r.rangeOpen || !rangeOk) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(r.lo, { r.lo = it }, stringResource(R.string.lab_range_low), Modifier.weight(1f).testTag("lab-batch-lo:$index"), isError = !rangeOk)
                NumberField(r.hi, { r.hi = it }, stringResource(R.string.lab_range_high), Modifier.weight(1f).testTag("lab-batch-hi:$index"), isError = !rangeOk, suffix = r.unit)
            }
            OutlinedTextField(r.note, { r.note = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.note)) })
        }
    }
}
