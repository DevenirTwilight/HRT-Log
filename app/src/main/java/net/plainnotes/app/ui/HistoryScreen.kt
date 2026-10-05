package net.plainnotes.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import net.plainnotes.app.domain.SlotState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Adherence counts for scheduled intakes; unscheduled intakes are listed but not counted. */
class Adherence(val onTime: Int, val late: Int, val missed: Int, val skipped: Int) {
    val due get() = onTime + late + missed
    val onTimeRate get() = if (due == 0) null else onTime.toDouble() / due
}
fun adherence(records: List<RecordEntity>) = records.filter { it.slot_key != null || it.scheduled_utc != null }.let { r ->
    Adherence(r.count { it.status == "ON_TIME" }, r.count { it.status == "LATE" }, r.count { it.status == "MISSED" }, r.count { it.status == "SKIPPED" })
}
private fun RecordEntity.at(): Instant = Instant.ofEpochMilli(taken_utc ?: scheduled_utc ?: 0)

@Composable fun HistoryScreen(state: NotesState, records: List<RecordEntity>, onEdit: (RecordEntity) -> Unit, onDelete: (RecordEntity) -> Unit, contentPadding: PaddingValues) {
    val meds = state.medications.associateBy { it.id }
    var medFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var days by rememberSaveable { mutableIntStateOf(30) }
    val zone = ZoneId.systemDefault()
    val since = if (days == 0) Instant.EPOCH else LocalDate.now().minusDays(days.toLong() - 1).atStartOfDay(zone).toInstant()
    val shown = records.filter { it.deleted_at_utc == null && (medFilter == null || it.medication_id == medFilter) && !it.at().isBefore(since) }.sortedByDescending { it.at() }
    val next = state.slots.filter { it.state in listOf(SlotState.PENDING, SlotState.SOON, SlotState.OVERDUE) && (medFilter == null || it.slot.medicationId == medFilter) }.minByOrNull { it.slot.at }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp,
        bottom = contentPadding.calculateBottomPadding() + 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        next?.let { e -> item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), elevation = CardDefaults.elevatedCardElevation(0.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Alarm, null, tint = MaterialTheme.colorScheme.onPrimaryContainer); Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.history_next), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("${meds[e.slot.medicationId]?.name ?: ""} · ${formatDateTime(e.slot.at)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(formatDose(e.slot.dose, meds[e.slot.medicationId]?.unit), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        } }
        item {
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
        items(shown, key = { it.id }) { r -> RecordRow(r, meds[r.medication_id], { onEdit(r) }, { onDelete(r) }) }
    }
}

@Composable private fun AdherenceCard(a: Adherence) {
    val c = MaterialTheme.colorScheme
    SectionCard(stringResource(R.string.adherence_title)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(a.onTimeRate?.let { "${(it * 100).roundToInt()}%" } ?: "—", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.adherence_on_time_rate), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
        }
        val parts = listOf(a.onTime to c.primary, a.late to c.tertiary, a.missed to c.error, a.skipped to c.outline)
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

@Composable private fun RecordRow(r: RecordEntity, med: MedicationEntity?, onEdit: () -> Unit, onDelete: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val (bg, fg, label) = when (r.status) {
        "ON_TIME" -> Triple(c.primaryContainer, c.onPrimaryContainer, R.string.status_on_time)
        "LATE" -> Triple(c.tertiaryContainer, c.onTertiaryContainer, R.string.status_late)
        "MISSED" -> Triple(c.errorContainer, c.onErrorContainer, R.string.status_missed)
        else -> Triple(c.surfaceContainerHighest, c.onSurfaceVariant, R.string.status_skipped)
    }
    ElevatedCard(onClick = { if (r.status != "SKIPPED") onEdit() }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = c.surfaceContainerLow), elevation = CardDefaults.elevatedCardElevation(0.dp)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(med?.name ?: "", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false)); Spacer(Modifier.width(8.dp))
                    StatusPill(stringResource(label), bg, fg, if (r.status in listOf("ON_TIME", "LATE")) Icons.Rounded.Check else null)
                }
                r.taken_utc?.let { Text(stringResource(R.string.history_taken_at, formatDateTime(Instant.ofEpochMilli(it))), style = MaterialTheme.typography.bodyMedium) }
                r.scheduled_utc?.let { Text(stringResource(R.string.history_planned_at, formatDateTime(Instant.ofEpochMilli(it))), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant) }
                    ?: Text(stringResource(R.string.history_unscheduled), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                val dose = r.actual_dose?.let { formatDose(it, med?.unit) } ?: r.planned_dose?.let { stringResource(R.string.history_planned_dose, formatDose(it, med?.unit)) }
                listOfNotNull(dose, r.site?.let { siteLabel(it) }).joinToString(" · ").takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant) }
                if ((r.unallocated_supply_amount ?: 0.0) > 0) Text(stringResource(R.string.history_unallocated, formatDose(r.unallocated_supply_amount!!, med?.unit)), style = MaterialTheme.typography.labelSmall, color = c.tertiary)
            }
            if (r.status != "SKIPPED") Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(if (r.status == "MISSED") R.string.history_backfill else R.string.edit)) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit() })
                    if (r.status in listOf("ON_TIME", "LATE")) DropdownMenuItem(text = { Text(stringResource(R.string.remove)) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

val SITES = listOf("LEFT", "RIGHT")
@Composable fun siteLabel(code: String) = when (code) { "LEFT" -> stringResource(R.string.site_left); "RIGHT" -> stringResource(R.string.site_right); else -> code }
/** Suggests the side opposite to the last recorded one. */
fun suggestSite(records: List<RecordEntity>, medicationId: Long): String =
    records.filter { it.medication_id == medicationId && it.deleted_at_utc == null && it.site in SITES }.maxByOrNull { it.taken_utc ?: 0 }?.site?.let { if (it == "LEFT") "RIGHT" else "LEFT" } ?: "LEFT"
