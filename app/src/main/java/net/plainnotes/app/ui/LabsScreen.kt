package net.plainnotes.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import net.plainnotes.app.R
import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.data.LabPanelEntity
import net.plainnotes.app.data.LabValueEntity
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** Analyte code → units; the first unit is canonical for charts, the factor converts from that unit. */
class Analyte(val code: String, val units: List<Pair<String, Double>>)
val ANALYTES = listOf(
    Analyte("E2", listOf("pg/mL" to 1.0, "pmol/L" to 3.671)),
    Analyte("T", listOf("ng/dL" to 1.0, "nmol/L" to 0.03467)),
    // Progesterone by assay method: results from different methods never share a chart. "P4" = method unknown (also imports).
    Analyte("P4", listOf("ng/mL" to 1.0, "nmol/L" to 3.18)),
    Analyte("P4_IA", listOf("ng/mL" to 1.0, "nmol/L" to 3.18)),
    Analyte("P4_MS", listOf("ng/mL" to 1.0, "nmol/L" to 3.18)),
    Analyte("PRL", listOf("ng/mL" to 1.0, "mIU/L" to 21.2)),
    Analyte("LH", listOf("IU/L" to 1.0)), Analyte("FSH", listOf("IU/L" to 1.0)),
    Analyte("SHBG", listOf("nmol/L" to 1.0)),
    Analyte("ALT", listOf("U/L" to 1.0)), Analyte("AST", listOf("U/L" to 1.0)), Analyte("GGT", listOf("U/L" to 1.0)),
    Analyte("CREA", listOf("µmol/L" to 1.0, "mg/dL" to 1 / 88.42)),
    Analyte("K", listOf("mmol/L" to 1.0)),
)
private val P4_CODES = setOf("P4", "P4_IA", "P4_MS")
fun analyte(code: String) = ANALYTES.firstOrNull { it.code == code } ?: Analyte(code, emptyList())
/** Converts a value in [unit] to the analyte's canonical unit; null when the unit is unknown. */
fun toCanonical(a: Analyte, value: Double, unit: String): Double? = a.units.firstOrNull { it.first == unit }?.let { value / it.second }

@Composable fun analyteLabel(code: String) = stringResource(when (code) {
    "E2" -> R.string.lab_e2; "T" -> R.string.lab_t; "P4" -> R.string.lab_p4; "P4_IA" -> R.string.lab_p4_ia; "P4_MS" -> R.string.lab_p4_ms; "PRL" -> R.string.lab_prl; "LH" -> R.string.lab_lh; "FSH" -> R.string.lab_fsh
    "SHBG" -> R.string.lab_shbg; "ALT" -> R.string.lab_alt; "AST" -> R.string.lab_ast; "GGT" -> R.string.lab_ggt; "CREA" -> R.string.lab_crea; "K" -> R.string.lab_k
    else -> R.string.choice_other
})

@Composable fun LabsScreen(labs: List<LabValueEntity>, doseTimes: List<Instant>, onEdit: (LabValueEntity?) -> Unit, onDelete: (LabValueEntity) -> Unit, contentPadding: PaddingValues,contexts:List<net.plainnotes.app.data.LabContextEntity> = emptyList(),onRebuild:((LabValueEntity,Boolean)->Unit)?=null,
               panels:List<net.plainnotes.app.data.LabPanelEntity> = emptyList(),onPanel:((net.plainnotes.app.data.LabPanelEntity)->Unit)?=null,onSaveAsPanel:((List<LabValueEntity>)->Unit)?=null) {
    var category by remember { mutableStateOf<LabHomeCategory?>(null) }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    val labels = labs.map { it.analyte_code }.distinct().associateWith { analyteLabel(it) }
    val visible = labs.filter { v -> (category == null || labHomeCategory(v.analyte_code) == category) &&
        (query.isBlank() || labels[v.analyte_code].orEmpty().contains(query.trim(), ignoreCase = true) || v.analyte_code.contains(query.trim(), ignoreCase = true) || v.laboratory.orEmpty().contains(query.trim(), ignoreCase = true)) }
    val present = ANALYTES.filter { a -> visible.any { it.analyte_code == a.code } }
    // Filters affect presentation only. Saved panel membership and legacy grouping are never rewritten.
    var selected by remember { mutableStateOf<String?>(null) }
    val code = selected?.takeIf { s -> present.any { it.code == s } }
    var detail by remember { mutableStateOf<LabValueEntity?>(null) }
    LazyColumn(Modifier.fillMaxSize().testTag("labs-home"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp,
        bottom = contentPadding.calculateBottomPadding() + 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Button(onClick = { onEdit(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("labs-home-add")) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.lab_batch_title))
            }
        }
        item {
            TextButton(onClick = { searchOpen = !searchOpen }, modifier = Modifier.testTag("labs-home-search-toggle")) { Text(stringResource(R.string.lab_home_search)) }
            if (searchOpen) OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().testTag("labs-home-search"),
                label = { Text(stringResource(R.string.lab_home_search)) }, singleLine = true)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(category == null, { category = null; selected = null }, label = { Text(stringResource(R.string.lab_filter_all)) }, modifier = Modifier.testTag("lab-category-all"))
                LabHomeCategory.entries.forEach { c -> FilterChip(category == c, { category = c; selected = null },
                    label = { Text(stringResource(c.label)) }, modifier = Modifier.testTag("lab-category:${c.name}")) }
            }
        }
        if (labs.isNotEmpty() && visible.isEmpty()) item { Text(stringResource(R.string.lab_home_no_results), modifier = Modifier.testTag("labs-home-no-results")) }
        if (labs.isEmpty()) item { EmptyState(Icons.Outlined.Science, stringResource(R.string.labs_empty_title), stringResource(R.string.labs_empty_body)) { Button(onClick = { onEdit(null) }) { Text(stringResource(R.string.lab_add)) } } }
        if (present.isNotEmpty()) item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(code == null, { selected = null }, label = { Text(stringResource(R.string.lab_filter_all)) }, modifier = Modifier.testTag("lab-filter-all"))
                present.forEach { a -> FilterChip(code == a.code, { selected = a.code }, label = { Text(analyteLabel(a.code)) }) }
            }
        }
        if (code != null) {
            val a = analyte(code)
            val values = visible.filter { it.analyte_code == code }.sortedBy { it.sampled_utc }
            val points = values.mapNotNull { v -> toCanonical(a, v.value, v.unit)?.let { ConcentrationCalculator.hours(v.sampled_utc) to it } }
            if (points.size >= 2) item {
                SectionCard(stringResource(R.string.lab_trend, analyteLabel(code))) {
                    val latestRange = values.lastOrNull { it.reference_lower != null && it.reference_upper != null && it.reference_unit != null }
                        ?.let { r -> val u = r.reference_unit!!; toCanonical(a, r.reference_lower!!, u)?.let { lo -> toCanonical(a, r.reference_upper!!, u)?.let { hi -> lo to hi } } }
                    val xs = points.map { it.first }.toDoubleArray(); val ys = points.map { it.second }.toDoubleArray()
                    val pad = max(24.0, (xs.last() - xs.first()) * 0.08)
                    val data = ChartData(doubleArrayOf(xs.first() - pad) + xs + doubleArrayOf(xs.last() + pad), doubleArrayOf(ys.first()) + ys + doubleArrayOf(ys.last()),
                        points = points, range = latestRange, unit = a.units.firstOrNull()?.first ?: "")
                    val sep = java.text.DecimalFormatSymbols.getInstance(currentLocale()).decimalSeparator
                    ConcChart(data, xs.first() - pad, xs.last() + pad, Modifier.fillMaxWidth().height(200.dp)) { v -> if (v >= 10) v.roundToInt().toString() else inputNumber((v * 100).roundToInt() / 100.0).replace('.', sep) }
                    if (latestRange != null) Text(stringResource(R.string.lab_range_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(labDraws(values, panels), key = { d -> "filtered:" + (d.panel?.id ?: -d.labs.first().id) }) { d ->
                LabHomeCard(d, { detail = it }, onEdit, onDelete, contexts, onRebuild,
                    d.panel?.let { p -> onPanel?.let { open -> { open(p) } } }, null)
            }
        // LAB-02: real lab panels (even with one result) are cards; results without a panel keep the LAB-01 display grouping.
        } else items(labDraws(visible, panels), key = { d -> d.panel?.let { "panel:${it.id}" } ?: ("draw:" + d.labs.first().id) }) { d ->
            LabHomeCard(d, { detail = it }, onEdit, onDelete, contexts, onRebuild,
                d.panel?.let { p -> onPanel?.let { open -> { open(p) } } },
                if (d.panel == null && d.labs.size > 1) onSaveAsPanel?.let { save -> { save(d.labs) } } else null)
        }
    }
    detail?.let { v -> LabDetailDialog(v, contexts.filter { it.lab_id == v.id }, { detail = null }) { detail = null; onEdit(v) } }
}

private fun max(a: Double, b: Double) = if (a > b) a else b

@Composable fun LabDialog(initial: LabValueEntity?, onDismiss: () -> Unit, onSave: (LabValueEntity) -> Unit,inGroup:Boolean=false,onSaveWithEstimate:((LabValueEntity,Boolean)->Unit)?=null) {
    var includeEstimate by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf(initial?.analyte_code ?: "E2") }
    val a = analyte(code)
    var unit by remember { mutableStateOf(initial?.unit ?: a.units.first().first) }
    var time by remember { mutableStateOf((initial?.let { Instant.ofEpochMilli(it.sampled_utc) } ?: Instant.now()).toLocalHere()) }
    var value by remember { mutableStateOf(initial?.value?.let(::inputNumber) ?: "") }
    var lo by remember { mutableStateOf(initial?.reference_lower?.let(::inputNumber) ?: "") }
    var hi by remember { mutableStateOf(initial?.reference_upper?.let(::inputNumber) ?: "") }
    var lab by remember { mutableStateOf(initial?.laboratory ?: "") }; var note by remember { mutableStateOf(initial?.note ?: "") }
    val valueV = value.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    val loV = lo.toDoubleOrNull(); val hiV = hi.toDoubleOrNull()
    val rangeOk = (lo.isBlank() || loV != null) && (hi.isBlank() || hiV != null) && (loV == null || hiV == null || loV <= hiV)
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.Science, null) }, title = { Text(stringResource(if (initial == null) R.string.lab_add else R.string.lab_edit)) },
        properties = DialogProperties(dismissOnBackPress = true),
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val p4 = code in P4_CODES
            DropdownField(stringResource(R.string.lab_analyte), ANALYTES.map { it.code }.filter { it !in P4_CODES || it == "P4" }, if (p4) "P4" else code,
                { if (it == "P4") stringResource(R.string.lab_p4_family) else analyteLabel(it) }, { code = it; unit = analyte(it).units.first().first })
            if (p4) {
                Text(stringResource(R.string.lab_p4_method), style = MaterialTheme.typography.labelLarge)
                listOf("P4_IA" to R.string.lab_p4_method_ia, "P4_MS" to R.string.lab_p4_method_ms, "P4" to R.string.lab_p4_method_unknown).forEach { (c, label) ->
                    Row(Modifier.fillMaxWidth().selectable(code == c, onClick = { code = c }, role = Role.RadioButton), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(code == c, null); Spacer(Modifier.width(8.dp)); Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(stringResource(R.string.lab_p4_method_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DateTimeRow(time, { time = it })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(value, { value = it }, stringResource(R.string.lab_value), Modifier.weight(1f), isError = value.isNotEmpty() && valueV == null)
                DropdownField(stringResource(R.string.unit), a.units.map { it.first }, unit, { it }, { unit = it }, Modifier.weight(1f))
            }
            Text(stringResource(R.string.lab_range_optional), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(lo, { lo = it }, stringResource(R.string.lab_range_low), Modifier.weight(1f), isError = !rangeOk)
                NumberField(hi, { hi = it }, stringResource(R.string.lab_range_high), Modifier.weight(1f), isError = !rangeOk, suffix = unit)
            }
            OutlinedTextField(lab, { lab = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.lab_laboratory)) }, singleLine = true)
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.note)) })
            if(onSaveWithEstimate!=null) {
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(includeEstimate,{includeEstimate=it});Text(stringResource(R.string.lab_context_include_estimate))}
                Text(stringResource(R.string.lab_context_save_note),style=MaterialTheme.typography.bodySmall)
            }
            // LAB-01 D: a hint only, saving is not blocked.
            if (initial != null && inGroup && (time != Instant.ofEpochMilli(initial.sampled_utc).toLocalHere() || lab.trim() != initial.laboratory?.trim().orEmpty()))
                Text(stringResource(R.string.lab_group_split_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("lab-split-hint"))
        } },
        confirmButton = { Button(enabled = valueV != null && rangeOk, onClick = {
            val hasRange = loV != null || hiV != null
            val saved=LabValueEntity(initial?.id ?: 0, code, valueV!!, unit, time.toInstantHere().toEpochMilli(), ZoneId.systemDefault().id,
                loV, hiV, if (hasRange) unit else null, lab.ifBlank { null }, note.ifBlank { null })
            if(onSaveWithEstimate!=null)onSaveWithEstimate(saved,includeEstimate) else onSave(saved)
        }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}


/** Read the original lab and its frozen context; no duplicate row, inferred visit association, or new estimate. */
@Composable fun LabDetailDialog(v:LabValueEntity,contexts:List<net.plainnotes.app.data.LabContextEntity>,onDismiss:()->Unit,onEdit:()->Unit) {
    val local=Instant.ofEpochMilli(v.sampled_utc).atZone(ZoneId.of(v.sampled_zone))
    AlertDialog(onDismissRequest=onDismiss,title={Text(analyteLabel(v.analyte_code))},properties = DialogProperties(dismissOnBackPress = true),text={Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(displayNumber(v.value)+" "+v.unit)
        Text(formatDate(local.toLocalDate())+" · "+formatTime(local.toLocalTime())+" · "+v.sampled_zone)
        if(v.reference_lower!=null || v.reference_upper!=null)Text(stringResource(R.string.lab_range_value,v.reference_lower?.let{displayNumber(it)} ?: "–",v.reference_upper?.let{displayNumber(it)} ?: "–",v.reference_unit ?: ""))
        listOfNotNull(v.laboratory,v.note).forEach{Text(it)}
        LabContextSection(v,contexts,null)
    }},confirmButton={AdaptiveActions(listOf(stringResource(R.string.edit),stringResource(R.string.ok)),contentInset=48.dp){i,mod->
        if(i==0)OutlinedButton(onClick=onEdit,modifier=mod){Text(stringResource(R.string.edit))}
        else TextButton(onClick=onDismiss,modifier=mod){Text(stringResource(R.string.ok))}
    }})
}
