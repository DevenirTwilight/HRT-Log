package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.saveable.rememberSaveable
import net.plainnotes.app.data.AppointmentEntity
import net.plainnotes.app.data.unconfirmed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.data.StockForecast
import net.plainnotes.app.domain.SlotState
import net.plainnotes.app.domain.TimelineEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * UI-01 A / UI-04: everything about today, shown once. Display only: [open] are today's open doses, [taken] today's taken
 * records (the progress count), [records] all of today's records and [appointments] today's appointments, all already
 * computed for the calendar; with today selected the day list below the calendar is not shown, so nothing here is listed
 * twice. Every action calls the same [onComplete]/[onChange]/[onAppointment] as the day list, so AppShell opens the
 * existing IntakeDialog / OverrideDialog / visit and no new write path exists. [medOf]/[recordMed] are the DoseCard /
 * RecordLine lookups (frozen rule and record snapshots), so names and doses are never recomputed here.
 */
@Composable internal fun TodayCard(today: LocalDate, open: List<TimelineEntry>, taken: List<RecordEntity>, total: Int, allDone: Boolean,
                                   medOf: (TimelineEntry) -> MedicationEntity?, recordMed: (RecordEntity) -> MedicationEntity?,
                                   onComplete: (TimelineEntry) -> Unit, onChange: (TimelineEntry) -> Unit,
                                   records: List<RecordEntity> = taken, appointments: List<AppointmentEntity> = emptyList(),
                                   onAppointment: (AppointmentEntity) -> Unit = {}) {
    val c = MaterialTheme.colorScheme
    val simple = LocalSimpleMode.current
    Surface(color = c.primary, contentColor = c.onPrimary, shape = RoundedCornerShape24, modifier = Modifier.fillMaxWidth().testTag("today-card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.today) + " · " + formatShortDate(today), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() }.testTag("today-date"))
                if (total > 0) Text(stringResource(R.string.today_progress, taken.size, total), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("today-progress"))
            }
            val next = open.firstOrNull()
            when {
                next != null -> {
                    val sameTime = open.filter { it.slot.at == next.slot.at }
                    Text(stringResource(R.string.today_next), style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(formatTime(next.slot.at), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("today-next-time"))
                        // OVERDUE is said in words, not only by colour.
                        if (next.state == SlotState.OVERDUE) Surface(color = c.errorContainer, contentColor = c.onErrorContainer, shape = MaterialTheme.shapes.small) {
                            Text(stringResource(R.string.today_overdue), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).testTag("today-next-status"))
                        }
                    }
                    DoseLine(next, medOf(next), simple, Modifier.testTag("today-next-name"))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button({ onComplete(next) }, Modifier.weight(1f).testTag("today-take-now"),
                            colors = ButtonDefaults.buttonColors(containerColor = c.onPrimary, contentColor = c.primary)) { Text(stringResource(R.string.today_take_now)) }
                        DoseMenu(next, "today-more", onComplete, onChange)
                    }
                    if (sameTime.size > 1) {
                        Text(stringResource(R.string.today_same_time), style = MaterialTheme.typography.labelLarge)
                        sameTime.drop(1).forEach { e ->
                            Row(Modifier.fillMaxWidth().testTag("today-also:${e.slot.key}"), verticalAlignment = Alignment.CenterVertically) {
                                DoseLine(e, medOf(e), simple, Modifier.weight(1f))
                                TextButton({ onComplete(e) }, Modifier.testTag("today-also-take:${e.slot.key}"),
                                    colors = ButtonDefaults.textButtonColors(contentColor = c.onPrimary)) { Text(stringResource(R.string.today_take_now)) }
                                DoseMenu(e, "today-more:${e.slot.key}", onComplete, onChange)
                            }
                        }
                    }
                    // UI-04: the rest of today's open doses, smaller, one line each with only the menu (no button competing with "next").
                    val later = open.filter { it.slot.at != next.slot.at }
                    if (later.isNotEmpty()) Section(R.string.today_later, "today-later", later) { e ->
                        Row(Modifier.fillMaxWidth().testTag("today-later:${e.slot.key}"), verticalAlignment = Alignment.CenterVertically) {
                            val m = medOf(e)
                            Text(listOfNotNull(formatTime(e.slot.at), if (simple) null else m?.name, formatDose(e.slot.dose, m?.unit)).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            DoseMenu(e, "today-later-more:${e.slot.key}", onComplete, onChange)
                        }
                    }
                }
                allDone -> Text(stringResource(R.string.today_all_done), style = MaterialTheme.typography.titleMedium)
                total == 0 -> Text(stringResource(R.string.today_nothing), style = MaterialTheme.typography.bodyLarge)
            }
            // UI-04: all of today's records, each with its status in words (not only colour), as RecordLine said it.
            if (records.isNotEmpty()) Section(R.string.today_log_heading, "today-log", records) { r ->
                val m = recordMed(r)
                Column(Modifier.fillMaxWidth()) {
                    Text(listOfNotNull(formatTime(Instant.ofEpochMilli(r.taken_utc ?: r.scheduled_utc ?: 0)), if (simple) null else m?.name,
                        (r.actual_dose ?: r.planned_dose)?.let { formatDose(it, m?.unit) }, stringResource(recordStatus(r))).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("today-done:${r.id}"))
                    if (r.unconfirmed) Text(stringResource(R.string.unconfirmed_help), style = MaterialTheme.typography.bodySmall)
                }
            }
            // UI-04: today's appointments, one row each; a tap opens the visit as the day list's AppointmentCard did.
            if (appointments.isNotEmpty()) Section(R.string.today_appointment, "today-appts", appointments) { a ->
                Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(role = Role.Button) { onAppointment(a) }.heightIn(min = 48.dp)
                    .testTag("today-appt:${a.id}"), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.EventNote, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(listOfNotNull(choiceLabel(a.type.ifBlank { "OTHER" }), formatTime(Instant.ofEpochMilli(a.at_utc)), a.practitioner?.takeIf { it.isNotBlank() },
                        a.location?.takeIf { it.isNotBlank() }).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** A smaller titled list in the today card: three rows, then "Show all (n)"; the choice survives recreation. */
@Composable private fun <T> Section(title: Int, tag: String, rows: List<T>, row: @Composable (T) -> Unit) {
    var all by rememberSaveable(tag) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() }.testTag(tag))
        (if (all) rows else rows.take(3)).forEach { row(it) }
        if (!all && rows.size > 3) TextButton({ all = true }, Modifier.testTag("today-show-all:" + tag.removePrefix("today-")),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) { Text(stringResource(R.string.today_show_all, rows.size)) }
    }
}

/** The same status words as RecordLine. */
private fun recordStatus(r: RecordEntity) = when (r.status) {
    "ON_TIME" -> R.string.status_on_time; "LATE" -> R.string.status_late
    "MISSED" -> if (r.unconfirmed) R.string.status_unconfirmed else R.string.status_missed
    else -> R.string.status_skipped
}

private val RoundedCornerShape24 = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)

/** Name, then dose and route, as on DoseCard. Simple mode hides the name there too. */
@Composable private fun DoseLine(e: TimelineEntry, med: MedicationEntity?, simple: Boolean, modifier: Modifier) {
    Column(modifier) {
        if (!simple) Text(med?.name ?: "", style = MaterialTheme.typography.titleMedium)
        Text(listOfNotNull(formatDose(e.slot.dose, med?.unit), med?.route?.let { choiceLabel(it) }).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun DoseMenu(e: TimelineEntry, tag: String, onComplete: (TimelineEntry) -> Unit, onChange: (TimelineEntry) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }, Modifier.testTag(tag)) {
            Icon(Icons.Outlined.MoreVert, stringResource(R.string.today_more))
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text(stringResource(R.string.today_other_time)) }, { open = false; onComplete(e) })
            DropdownMenuItem({ Text(stringResource(R.string.today_change_or_skip)) }, { open = false; onChange(e) })
        }
    }
}

/**
 * UI-01 B: only medications the shared forecast calls low or out ([StockForecast.isLow], the stock page's and stock
 * reminder's rule; no new threshold). Nothing actionable, nothing shown: untracked or no-plan stays on the stock page.
 */
@Composable internal fun HomeStockCard(meds: List<MedicationEntity>, forecasts: Map<Long, StockForecast>, today: LocalDate, zone: ZoneId, onStock: () -> Unit,
                                       onAdd: (MedicationEntity) -> Unit = {}) {
    val low = meds.filter { forecasts[it.id]?.isLow(today, zone) == true }
    if (low.isEmpty()) return
    val c = MaterialTheme.colorScheme
    Surface(color = c.tertiaryContainer, contentColor = c.onTertiaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().testTag("home-stock")) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Inventory2, null); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_stock_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            low.forEach { m ->
                Column(Modifier.fillMaxWidth().testTag("home-stock-row:${m.id}"), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(m.name, style = MaterialTheme.typography.titleSmall)
                    Text(stockForecastText(forecasts[m.id], m, today, zone).first, style = MaterialTheme.typography.bodyMedium)
                    // UI-02 A1: "Add stock" opens AppShell's existing AddStockDialog (the stock page's own wiring); side by side
                    // when both fit, stacked otherwise.
                    val labels = listOf(stringResource(R.string.home_stock_view), stringResource(R.string.stock_add))
                    AdaptiveActions(labels, contentInset = 48.dp) { i, mod ->
                        if (i == 0) OutlinedButton(onStock, mod.testTag("home-stock-view"), colors = ButtonDefaults.outlinedButtonColors(contentColor = c.onTertiaryContainer)) { Text(labels[0]) }
                        else Button({ onAdd(m) }, mod.testTag("home-stock-add:${m.id}"), colors = ButtonDefaults.buttonColors(containerColor = c.tertiary, contentColor = c.onTertiary)) { Text(labels[1]) }
                    }
                }
            }
        }
    }
}
