package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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

/** UI-05 A: "now" for the today card's countdown only (display, never written); tests provide a fixed instant. */
internal val LocalTodayClock = staticCompositionLocalOf<() -> Instant> { Instant::now }

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
            // UI-05 A: progress as a translucent pill at the right of the date row.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.today) + " · " + formatShortDate(today), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).semantics { heading() }.testTag("today-date"))
                if (total > 0) Surface(color = c.onPrimary.copy(alpha = 0.16f), contentColor = c.onPrimary, shape = Corners.pill, modifier = Modifier.testTag("today-progress-pill")) {
                    Text(stringResource(R.string.today_progress, taken.size, total), style = MaterialTheme.typography.labelLarge.tnum(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp).testTag("today-progress"))
                }
            }
            val next = open.firstOrNull()
            when {
                next != null -> {
                    val sameTime = open.filter { it.slot.at == next.slot.at }
                    Text(stringResource(R.string.today_next), style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(formatTime(next.slot.at), style = MaterialTheme.typography.headlineMedium.tnum(), fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("today-next-time"))
                        // OVERDUE is said in words, not only by colour; UI-06: attention, not red ("past time · you can still log it").
                        if (next.state == SlotState.OVERDUE) Surface(color = NotesTheme.semantic.attention.container, contentColor = NotesTheme.semantic.attention.onContainer, shape = Corners.pill) {
                            Text(statusText(StatusStyles.of(SlotState.OVERDUE)), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp).testTag("today-next-status"))
                        } else Countdown(next.slot.at)
                    }
                    DoseLine(next, medOf(next), simple, Modifier.testTag("today-next-name"))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        Button({ onComplete(next) }, Modifier.weight(1f).heightIn(min = 52.dp).testTag("today-take-now"),
                            colors = ButtonDefaults.buttonColors(containerColor = c.onPrimary, contentColor = c.primary)) {
                            Icon(Icons.Outlined.Check, null, Modifier.size(20.dp).testTag("today-take-now-icon")); Spacer(Modifier.width(Spacing.s))
                            Text(stringResource(R.string.today_take_now), fontWeight = FontWeight.SemiBold)
                        }
                        DoseMenu(next, "today-more", onComplete, onChange, round = true)
                    }
                    // UI-05 A: a thin line between "next" and the sections below it (only when something follows).
                    if (open.size > 1 || records.isNotEmpty() || appointments.isNotEmpty())
                        HorizontalDivider(color = c.onPrimary.copy(alpha = 0.2f), modifier = Modifier.testTag("today-divider"))
                    if (sameTime.size > 1) {
                        Text(stringResource(R.string.today_same_time), style = sectionTitleStyle)
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    // UI-05 A: a small status icon before the unchanged status words (decorative: the words carry the meaning).
                    // UI-06: colours and symbol from the status table; unconfirmed is a dashed ring (on the card's own colour).
                    val (kind, icon) = recordIcon(r); val style = StatusStyles.ofRecord(r.status, r.unconfirmed); val tone = statusTone(style)
                    Box(Modifier.size(28.dp).clip(CircleShape).background(if (style.dashed) androidx.compose.ui.graphics.Color.Transparent else tone?.container ?: c.onPrimary.copy(alpha = 0.16f))
                        .statusOutline(style, tone?.copy(accent = if (style.dashed) c.onPrimary else tone.accent), CircleShape)
                        .testTag("today-done-icon:${r.id}:$kind"), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(16.dp), tint = if (style.dashed) c.onPrimary else tone?.onContainer ?: c.onPrimary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(listOfNotNull(formatTime(Instant.ofEpochMilli(r.taken_utc ?: r.scheduled_utc ?: 0)), if (simple) null else m?.name,
                            (r.actual_dose ?: r.planned_dose)?.let { formatDose(it, m?.unit) }, stringResource(recordStatus(r))).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium.tnum(), modifier = Modifier.testTag("today-done:${r.id}"))
                        if (r.unconfirmed) Text(stringResource(R.string.unconfirmed_help), style = MaterialTheme.typography.bodySmall)
                    }
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
        Text(stringResource(title), style = sectionTitleStyle, modifier = Modifier.semantics { heading() }.testTag(tag))
        (if (all) rows else rows.take(3)).forEach { row(it) }
        if (!all && rows.size > 3) TextButton({ all = true }, Modifier.testTag("today-show-all:" + tag.removePrefix("today-")),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) { Text(stringResource(R.string.today_show_all, rows.size)) }
    }
}

/** UI-05 A / UI-06: icon kind per record status from the status table: taken ✓, missed ✕, skipped –, unconfirmed ?. */
private fun recordIcon(r: RecordEntity): Pair<String, androidx.compose.ui.graphics.vector.ImageVector> {
    val style = StatusStyles.ofRecord(r.status, r.unconfirmed)
    val kind = when (style.state) { DoseState.ON_TIME, DoseState.LATE -> "TAKEN"; DoseState.UNCONFIRMED -> "UNCONFIRMED"; DoseState.MISSED -> "MISSED"; else -> "SKIPPED" }
    return kind to statusIcon(style.symbol)!!
}

/** UI-05 A: "in n h / in n min" to the next dose, from slot.at and now only; refreshed each minute, never written. */
@Composable private fun Countdown(at: Instant) {
    val clock = LocalTodayClock.current
    var now by remember { mutableStateOf(clock()) }
    LaunchedEffect(at) { while (true) { kotlinx.coroutines.delay(60_000 - now.toEpochMilli() % 60_000); now = clock() } }
    val minutes = java.time.Duration.between(now, at).toMinutes()
    if (minutes < 0) return
    Text(if (minutes >= 60) stringResource(R.string.today_in_hours, (minutes / 60).toInt()) else stringResource(R.string.today_in_minutes, maxOf(1, minutes.toInt())),
        style = MaterialTheme.typography.bodyMedium.tnum(), modifier = Modifier.testTag("today-countdown"))
}

/** UI-05 G: tabular figures so times and amounts line up. */
internal fun androidx.compose.ui.text.TextStyle.tnum() = copy(fontFeatureSettings = "tnum")

/** The same status words as RecordLine (UI-06: from the status table). */
private fun recordStatus(r: RecordEntity) = statusTextRes(StatusStyles.ofRecord(r.status, r.unconfirmed))

private val RoundedCornerShape24 = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)

/** Name, then dose and route, as on DoseCard. Simple mode hides the name there too. */
@Composable private fun DoseLine(e: TimelineEntry, med: MedicationEntity?, simple: Boolean, modifier: Modifier) {
    Column(modifier) {
        if (!simple) Text(med?.name ?: "", style = MaterialTheme.typography.titleMedium)
        Text(listOfNotNull(formatDose(e.slot.dose, med?.unit), med?.route?.let { choiceLabel(it) }).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun DoseMenu(e: TimelineEntry, tag: String, onComplete: (TimelineEntry) -> Unit, onChange: (TimelineEntry) -> Unit, round: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Box {
        // UI-05 A: next to the main button the menu is a 52 dp round outlined button of the same height.
        if (round) OutlinedIconButton({ open = true }, Modifier.size(52.dp).testTag(tag),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f))) {
            Icon(Icons.Outlined.MoreVert, stringResource(R.string.today_more), tint = MaterialTheme.colorScheme.onPrimary)
        }
        else IconButton({ open = true }, Modifier.testTag(tag)) {
            Icon(Icons.Outlined.MoreVert, stringResource(R.string.today_more))
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text(stringResource(R.string.today_other_time)) }, { open = false; onComplete(e) })
            DropdownMenuItem({ Text(stringResource(R.string.today_change_or_skip)) }, { open = false; onChange(e) })
        }
    }
}

/**
 * UI-01 B / UI-05 B: only medications the shared forecast calls low or out ([StockForecast.isLow], the stock page's and stock
 * reminder's rule; no new threshold), each as a compact strip: the whole strip opens Stock, "Add stock" opens AppShell's
 * existing AddStockDialog. At most three strips, then "n more" (also to Stock). Nothing low, nothing shown.
 */
@Composable internal fun HomeStockCard(meds: List<MedicationEntity>, forecasts: Map<Long, StockForecast>, today: LocalDate, zone: ZoneId, onStock: () -> Unit,
                                       onAdd: (MedicationEntity) -> Unit = {}) {
    val unknown=meds.filter {forecasts[it.id]?.status==StockForecast.Status.UNKNOWN}
    if(unknown.isNotEmpty()) Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth().testTag("home-stock-unknown")) {
        Column(Modifier.padding(Spacing.l),verticalArrangement=Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.forecast_needs_check),style=MaterialTheme.typography.bodyMedium)
            unknown.forEach {Text(it.name,style=MaterialTheme.typography.titleSmall)}
            OutlinedButton(onStock,Modifier.heightIn(min=48.dp).testTag("home-stock-unknown-view")) {Text(stringResource(R.string.home_stock_view))}
        }
    }
    val low = lowStockMedications(meds, forecasts, today, zone)
    if (low.isEmpty()) return
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().testTag("home-stock"), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        low.take(3).forEach { m ->
            val f = forecasts.getValue(m.id)
            val days = f.daysUntilShort(today, zone)
            Box(Modifier.testTag("home-stock-row:${m.id}")) {
                Surface(onClick = onStock, color = c.tertiaryContainer, contentColor = c.onTertiaryContainer, shape = Corners.list,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("home-stock-view")) {
                    Row(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        Icon(Icons.Outlined.Inventory2, null)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.home_stock_low, m.name), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(if (f.lastCovered != null && days != null && days >= 1) stringResource(R.string.stock_runs_out_in, days) else stringResource(R.string.forecast_empty),
                                style = MaterialTheme.typography.bodySmall.tnum())
                        }
                        Button({ onAdd(m) }, Modifier.testTag("home-stock-add:${m.id}"), colors = ButtonDefaults.buttonColors(containerColor = c.tertiary, contentColor = c.onTertiary)) {
                            Text(stringResource(R.string.stock_add))
                        }
                    }
                }
            }
        }
        if (low.size > 3) TextButton(onStock, Modifier.align(Alignment.End).testTag("home-stock-more")) {
            Text(stringResource(R.string.home_stock_more, low.size - 3))
        }
    }
}

/** UI-05 B/E: the medications the shared forecast calls low or out (StockForecast.isLow): home strips and the drawer count. */
internal fun lowStockMedications(meds: List<MedicationEntity>, forecasts: Map<Long, StockForecast>, today: LocalDate, zone: ZoneId) =
    meds.filter { forecasts[it.id]?.isLow(today, zone) == true }
