@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package net.plainnotes.app.ui

import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import net.plainnotes.app.data.ProfileEntity
import net.plainnotes.app.pk.Ester
import net.plainnotes.app.pk.Pk
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.plainnotes.app.NotesState
import net.plainnotes.app.R
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.data.MedicationSnapshot
import net.plainnotes.app.data.unconfirmed
import net.plainnotes.app.domain.SlotState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Adherence counts for scheduled intakes; unscheduled intakes are listed but not counted. */
class Adherence(val onTime: Int, val late: Int, val missed: Int, val skipped: Int, val unconfirmed: Int = 0) {
    val due get() = onTime + late + missed
    val onTimeRate get() = if (due == 0 || unconfirmed > 0) null else onTime.toDouble() / due
}
fun adherence(records: List<RecordEntity>) = records.filter { it.slot_key != null || it.scheduled_utc != null }.let { r ->
    Adherence(r.count { it.status == "ON_TIME" }, r.count { it.status == "LATE" }, r.count { it.status == "MISSED" && !it.unconfirmed }, r.count { it.status == "SKIPPED" },r.count { it.unconfirmed })
}
private fun RecordEntity.at(): Instant = Instant.ofEpochMilli(taken_utc ?: scheduled_utc ?: 0)

@Composable fun HistoryScreen(state: NotesState, records: List<RecordEntity>, onEdit: (RecordEntity) -> Unit, onDelete: (RecordEntity) -> Unit, contentPadding: PaddingValues,
                              onAdd: () -> Unit = {}, onBatch: () -> Unit = {}, onLink: (RecordEntity) -> Unit = {}, onConfirmMissed:(RecordEntity)->Unit = {},selectedRecordIds:Set<Long>?=null,onClearSelection:()->Unit={}) {
    val meds = state.medications.associateBy { it.id }
    var medFilter by rememberSaveable(selectedRecordIds) { mutableStateOf<Long?>(null) }
    var days by rememberSaveable(selectedRecordIds) { mutableIntStateOf(if(selectedRecordIds==null)30 else 0) }
    val zone = ZoneId.systemDefault()
    val since = if (days == 0) Instant.EPOCH else LocalDate.now().minusDays(days.toLong() - 1).atStartOfDay(zone).toInstant()
    val shown = records.filter { it.deleted_at_utc == null && (selectedRecordIds==null || it.id in selectedRecordIds) && (medFilter == null || it.medication_id == medFilter) && !it.at().isBefore(since) }.sortedByDescending { it.at() }
    val byDay = shown.groupBy { it.at().atZone(zone).toLocalDate() }
    val simple = LocalSimpleMode.current
    val canAdd = state.medications.any { it.active }
    LazyColumn(Modifier.fillMaxSize().testTag("history-records"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp,
        bottom = contentPadding.calculateBottomPadding() + 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { HistoryHeader(canAdd, onAdd, onBatch) }
        if(selectedRecordIds!=null)item{TextButton(onClick=onClearSelection){Text(stringResource(R.string.timeline_imported_clear_selection))}}
        if (!simple && state.medications.size > 1) item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(medFilter == null, { medFilter = null }, label = { Text(stringResource(R.string.all_medications)) })
                state.medications.forEach { m -> FilterChip(medFilter == m.id, { medFilter = m.id }, label = { Text(m.name) }) }
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(7, 30, 90, 0).forEachIndexed { i, d ->
                    SegmentedButton(days == d, { days = d }, SegmentedButtonDefaults.itemShape(i, 4)) { Text(if (d == 0) stringResource(R.string.range_all) else stringResource(R.string.days_short, d)) }
                }
            }
        }
        item { AdherenceCard(adherence(shown)) }
        if (shown.isEmpty()) item { EmptyState(Icons.Outlined.History, stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_body)) }
        byDay.forEach { (day, list) -> item(key = day.toString()) { DayCard(day, list, meds, state.profiles, onEdit, onDelete, onLink,onConfirmMissed) } }
    }
}

@Composable private fun HistoryHeader(canAdd: Boolean, onAdd: () -> Unit, onBatch: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = c.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).widthIn(min = 140.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.MonitorHeart, null, tint = c.primary); Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBatch, enabled = canAdd, contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Outlined.Layers, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.batch_add))
                }
                Button(onClick = onAdd, enabled = canAdd, contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.history_add))
                }
            }
        }
    }
}

@Composable private fun DayCard(day: LocalDate, list: List<RecordEntity>, meds: Map<Long, MedicationEntity>, profiles: Map<Long, ProfileEntity>,
                                onEdit: (RecordEntity) -> Unit, onDelete: (RecordEntity) -> Unit, onLink: (RecordEntity) -> Unit,onConfirmMissed:(RecordEntity)->Unit) {
    val c = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = c.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().background(c.surfaceContainer).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.primary)); Spacer(Modifier.width(10.dp))
                Text(formatDate(day), style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
            }
            list.forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = c.outlineVariant.copy(alpha = 0.5f))
                RecordRow(r, meds[r.medication_id], profiles[r.medication_id], { onEdit(r) }, { onDelete(r) }, { onLink(r) },{onConfirmMissed(r)})
            }
        }
    }
}

/** E2-equivalent of an ester dose (molar mass ratio), shown like HRT tracker; null when it does not apply. */
fun e2Equivalent(dose: Double, unit: String?, ester: String?): Double? {
    if (unit != "MG" || ester == null || ester == "E2") return null
    val e = runCatching { Ester.valueOf(ester) }.getOrNull()?.takeUnless { it == Ester.CPA || it == Ester.BICA } ?: return null
    return dose * Pk.toE2Factor(e)
}

fun routeIcon(route: String?): ImageVector = when (route) {
    "INJECTION" -> Icons.Outlined.Vaccines; "GEL" -> Icons.Outlined.WaterDrop; "PATCH" -> Icons.Outlined.CropSquare
    "SUBLINGUAL" -> Icons.Outlined.Medication; else -> Icons.Outlined.Medication
}

@Composable private fun AdherenceCard(a: Adherence) {
    val c = MaterialTheme.colorScheme
    SectionCard(stringResource(R.string.adherence_title)) {
        if(a.unconfirmed>0)Text(stringResource(R.string.unconfirmed_count,a.unconfirmed),style=MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(a.onTimeRate?.let { "${(it * 100).roundToInt()}%" } ?: "—", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.adherence_on_time_rate), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
        }
        val parts = listOf(a.onTime to c.primary, a.late to c.tertiary, a.missed to c.error, a.skipped to c.outline,a.unconfirmed to c.outlineVariant)
        val total = parts.sumOf { it.first }
        if (total > 0) Row(Modifier.fillMaxWidth().height(12.dp).clip(MaterialTheme.shapes.small)) {
            parts.filter { it.first > 0 }.forEach { (n, col) -> Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(col)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Count(stringResource(R.string.status_on_time), a.onTime, c.primary); Count(stringResource(R.string.status_late), a.late, c.tertiary)
            Count(stringResource(R.string.status_missed), a.missed, c.error); Count(stringResource(R.string.status_skipped), a.skipped, c.outline)
        }
    }
}

@Composable private fun Count(label: String, n: Int, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(MaterialTheme.shapes.small).background(color)); Spacer(Modifier.width(4.dp)); Text(n.toString(), style = MaterialTheme.typography.titleMedium) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun RecordRow(r: RecordEntity, med: MedicationEntity?, profile: ProfileEntity?, onEdit: () -> Unit, onDelete: () -> Unit, onLink: () -> Unit,onConfirmMissed:()->Unit) {
    val c = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val simple = LocalSimpleMode.current
    val taken = r.status in listOf("ON_TIME", "LATE")
    val context=MedicationSnapshot.decode(r.config_snapshot,r.medication_id)
    val historyMed=context?.medication(r.medication_id)
    val historyProfile=context?.profile
    val route = context?.route
    Box {
        Row(Modifier.fillMaxWidth().testTag("history-record:${r.id}").clickable(enabled = r.status != "SKIPPED") { menu = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val injection = route == "INJECTION"
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(if (injection) c.primaryContainer else c.surfaceContainerHigh)
                .border(1.dp, c.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Icon(routeIcon(route), null, tint = if (taken) c.primary else c.onSurfaceVariant)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (simple) choiceLabel(historyMed?.molecule ?: "OTHER") else historyMed?.name ?: stringResource(R.string.history_context_unknown), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                val sub = listOfNotNull(route?.let { choiceLabel(it) }, historyProfile?.ester?.takeIf { it != "E2" && !simple }?.let { choiceLabel(it) }, r.site?.let { siteLabel(it) })
                if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                val dose = r.actual_dose ?: r.planned_dose
                if (dose != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (r.actual_dose != null) R.string.history_dose else R.string.history_planned_dose_label, formatDose(dose, historyMed?.unit)), style = MaterialTheme.typography.bodyLarge)
                    e2Equivalent(dose, historyMed?.unit, historyProfile?.ester)?.let {
                        Text(stringResource(R.string.history_e2_eq, displayNumber(it, 2)), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                    }
                }
                if ((r.unallocated_supply_amount ?: 0.0) > 0) Text(stringResource(R.string.history_unallocated, formatDose(r.unallocated_supply_amount!!, historyMed?.unit)), style = MaterialTheme.typography.labelSmall, color = c.tertiary)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(formatTime(r.at()))
                when (r.status) {
                    "LATE" -> StatusPill(stringResource(R.string.status_late), c.tertiaryContainer, c.onTertiaryContainer, null)
                    "MISSED" -> StatusPill(stringResource(if(r.unconfirmed)R.string.status_unconfirmed else R.string.status_missed), if(r.unconfirmed)c.surfaceContainerHigh else c.errorContainer, c.onSurface, null)
                    "SKIPPED" -> StatusPill(stringResource(R.string.status_skipped), c.surfaceContainerHighest, c.onSurfaceVariant, null)
                    // An unplanned timestamp has the same presentation for every record source.
                    else -> if (r.scheduled_utc == null) Chip(stringResource(R.string.history_unscheduled_short))
                }
            }
        }
        DropdownMenu(menu, { menu = false }) {
            if(r.unconfirmed)DropdownMenuItem(text={Text(stringResource(R.string.confirm_missed))},onClick={menu=false;onConfirmMissed()})
            if (taken && r.origin.startsWith("IMPORT_") && r.slot_key == null && r.scheduled_utc == null) DropdownMenuItem(text = { Text(stringResource(R.string.import_link)) }, onClick = { menu = false; onLink() })
            DropdownMenuItem(text = { Text(stringResource(if (r.status == "MISSED") R.string.history_backfill else R.string.edit)) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit() })
            if (taken) DropdownMenuItem(text = { Text(stringResource(R.string.remove)) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete() })
        }
    }
}

@Composable private fun Chip(text: String) {
    val c = MaterialTheme.colorScheme
    Text(text, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = c.onSurfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.surfaceContainerHigh).border(1.dp, c.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
}

val SITES = listOf("LEFT", "RIGHT")
@Composable fun siteLabel(code: String) = when (code) { "LEFT" -> stringResource(R.string.site_left); "RIGHT" -> stringResource(R.string.site_right); else -> code }
/** Suggests the side opposite to the last recorded one. */
fun suggestSite(records: List<RecordEntity>, medicationId: Long): String =
    records.filter { it.medication_id == medicationId && it.deleted_at_utc == null && it.site in SITES }.maxByOrNull { it.taken_utc ?: 0 }?.site?.let { if (it == "LEFT") "RIGHT" else "LEFT" } ?: "LEFT"

/** Explicit choice: never preselect a likely match or create a second intake. */
@Composable fun ImportedPlanDialog(link: net.plainnotes.app.NotesViewModel.ImportedLink, medication: MedicationEntity?, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var selected by remember(link) { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.import_link)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(medication?.name ?: "")
            Text(formatDate(link.record.at().atZone(ZoneId.systemDefault()).toLocalDate()) + " · " + formatTime(link.record.at()))
            link.record.actual_dose?.let { Text(formatDose(it, medication?.unit)) }
            Text(stringResource(R.string.import_link_help))
            if (link.candidates.isEmpty()) Text(stringResource(R.string.import_link_empty))
            LazyColumn(Modifier.heightIn(max = 240.dp)) {
                items(link.candidates.size) { index ->
                    val slot = link.candidates[index].slot
                    Row(Modifier.fillMaxWidth().clickable { selected = slot.key }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected == slot.key, { selected = slot.key })
                        Text(formatTime(slot.at) + " · " + formatDose(slot.dose, medication?.unit))
                    }
                }
            }
        }
    }, confirmButton = { Button(enabled = selected != null, onClick = { selected?.let(onConfirm) }) { Text(stringResource(R.string.import_link)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
