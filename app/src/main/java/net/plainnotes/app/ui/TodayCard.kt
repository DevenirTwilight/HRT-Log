package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
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
 *
 * UI-07: the primary card keeps only next / same time / later (32 dp corners, 64 sp time, press-morph main button, 56 dp
 * menu square, a spring when "next" changes). Today's records and appointments follow it as connected grouped lists
 * ("today-section" holds all three). A newly logged dose pops its icon in and, for 4 s, offers "Logged · Undo", which calls
 * [onUndo] — AppShell's existing delete (the intake history's "delete record"); nothing new is written.
 */
@Composable internal fun TodayCard(today: LocalDate, open: List<TimelineEntry>, taken: List<RecordEntity>, total: Int, allDone: Boolean,
                                   medOf: (TimelineEntry) -> MedicationEntity?, recordMed: (RecordEntity) -> MedicationEntity?,
                                   onComplete: (TimelineEntry) -> Unit, onChange: (TimelineEntry) -> Unit,
                                   records: List<RecordEntity> = taken, appointments: List<AppointmentEntity> = emptyList(),
                                   onAppointment: (AppointmentEntity) -> Unit = {}, onUndo: (RecordEntity) -> Unit = {}) {
    val c = MaterialTheme.colorScheme
    val simple = LocalSimpleMode.current
    val reduce = LocalReduceMotion.current
    val type = NotesTheme.typeExtra
    // UI-07 D: records that appear while the card is shown are "new": their icon pops in, and a taken one can be undone for 4 s.
    val seen = remember { mutableStateListOf<Long>().apply { addAll(records.map { it.id }) } }
    val fresh = records.filter { it.id !in seen }.map { it.id }.toSet()
    var justLogged by remember { mutableStateOf<RecordEntity?>(null) }
    LaunchedEffect(fresh) {
        if (fresh.isEmpty()) return@LaunchedEffect
        records.filter { it.id in fresh && (it.status == "ON_TIME" || it.status == "LATE") }.maxByOrNull { it.id }?.let { justLogged = it }
        seen.addAll(fresh)
    }
    LaunchedEffect(justLogged) { if (justLogged != null) { kotlinx.coroutines.delay(UNDO_WINDOW_MS); justLogged = null } }
    val undoable = justLogged?.takeIf { j -> records.any { it.id == j.id } }
    Column(Modifier.fillMaxWidth().testTag("today-section"), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
    Surface(color = c.primary, contentColor = c.onPrimary, shape = Corners.xxl, modifier = Modifier.fillMaxWidth().testTag("today-card")) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // UI-05 A: progress as a translucent pill at the right of the date row.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.today) + " · " + formatShortDate(today), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).semantics { heading() }.testTag("today-date"))
                if (total > 0) Surface(color = c.onPrimary.copy(alpha = 0.16f), contentColor = c.onPrimary, shape = Corners.pill, modifier = Modifier.testTag("today-progress-pill")) {
                    Text(stringResource(R.string.today_progress, taken.size, total), style = MaterialTheme.typography.labelLarge.tnum(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp).testTag("today-progress"))
                }
            }
            // UI-07 D: "Logged · Undo" for 4 s after a dose is logged here; Undo is the existing delete of that record.
            undoable?.let { r ->
                Row(Modifier.fillMaxWidth().testTag("today-logged"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    StatusIcon(StatusIconKind.TAKEN, StatusIconSizes.M, ring = c.onPrimary)
                    Text(stringResource(R.string.today_logged), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton({ justLogged = null; onUndo(r) }, Modifier.heightIn(min = 48.dp).testTag("today-undo"),
                        colors = ButtonDefaults.textButtonColors(contentColor = c.onPrimary)) { Text(stringResource(R.string.today_undo), fontWeight = FontWeight.SemiBold) }
                }
            }
            val next = open.firstOrNull()
            // UI-07 D: when "next" changes the old content fades out and the new one fades in, rising a little.
            AnimatedContent(NextView(next, open, if (next == null) (if (allDone) 1 else if (total == 0) 2 else 3) else 0), label = "todayNext",
                contentKey = { it.next?.slot?.key to it.mode },
                transitionSpec = {
                    if (reduce) EnterTransition.None togetherWith ExitTransition.None
                    else (fadeIn(motionSpec(false)) + slideInVertically(motionSpec(false)) { it / 8 }) togetherWith fadeOut(motionSpec(false))
                }) { view ->
            // The content draws the state it was given, so the outgoing "next" stays as it was while it fades out.
            val next = view.next; val open = view.open
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                next != null -> {
                    val sameTime = open.filter { it.slot.at == next.slot.at }
                    Text(stringResource(R.string.today_next), style = MaterialTheme.typography.labelLarge)
                    // UI-07 C: the time in the hero style (64 sp, capped at 1.3× font size); the label wraps below when it does not fit.
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically), horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        Text(formatTime(next.slot.at), style = type.heroNumber, modifier = Modifier.testTag("today-next-time"))
                        // OVERDUE is said in words, not only by colour; UI-06: attention, not red ("past time · you can still log it").
                        if (next.state == SlotState.OVERDUE) Surface(color = NotesTheme.semantic.attention.container, contentColor = NotesTheme.semantic.attention.onContainer, shape = Corners.pill) {
                            Text(statusText(StatusStyles.of(SlotState.OVERDUE)), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp).testTag("today-next-status"))
                        } else Countdown(next.slot.at)
                    }
                    DoseLine(next, medOf(next), simple, Modifier.testTag("today-next-name"), title = type.cardTitle)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        // UI-07 B: 56 dp tall; pressing springs the corners from full to 16 dp and the button to 96 %.
                        val press = remember { MutableInteractionSource() }; val morph = rememberPressMorph(press, rest = Radii.xl)
                        Button({ onComplete(next) }, Modifier.weight(1f).heightIn(min = 56.dp).pressMorph(morph).testTag("today-take-now"), shape = morph.shape,
                            interactionSource = press, colors = ButtonDefaults.buttonColors(containerColor = c.onPrimary, contentColor = c.primary)) {
                            Icon(Icons.Outlined.Check, null, Modifier.size(20.dp).testTag("today-take-now-icon")); Spacer(Modifier.width(Spacing.s))
                            Text(stringResource(R.string.today_take_now), fontWeight = FontWeight.SemiBold)
                        }
                        DoseMenu(next, "today-more", onComplete, onChange, round = true)
                    }
                    // UI-05 A: a thin line between "next" and the sections below it (only when something follows).
                    if (open.size > 1) HorizontalDivider(color = c.onPrimary.copy(alpha = 0.2f), modifier = Modifier.testTag("today-divider"))
                    if (sameTime.size > 1) {
                        Text(stringResource(R.string.today_same_time), style = type.sectionTitle)
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
                    if (later.isNotEmpty()) Section(R.string.today_later, "today-later", later, c.onPrimary) { e ->
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
            }
            }
        }
    }
    // UI-04 / UI-07 E: all of today's records as a connected grouped list under the card, each with its status icon and its
    // status in words (not only colour), as RecordLine said it. A new row slides in from above and its icon pops.
    if (records.isNotEmpty()) Section(R.string.today_log_heading, "today-log", records, c.primary, grouped = true) { r, i, n ->
        val m = recordMed(r)
        val pop = remember(r.id) { Animatable(if (r.id in fresh && !reduce) 0.3f else 1f) }
        val slide = remember(r.id) { Animatable(if (r.id in fresh && !reduce) -Motion.SLIDE_IN_DP.toFloat() else 0f) }
        LaunchedEffect(r.id) {
            launch { pop.animateTo(1f, motionSpec(reduce, Motion.POP_DAMPING, Motion.POP_STIFFNESS)) }
            launch { slide.animateTo(0f, motionSpec(reduce)) }
        }
        GroupedItem(i, n, modifier = Modifier.graphicsLayer { translationY = slide.value * density }.testTag("today-log-item:${r.id}")) {
            Row(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                val (kind, icon) = recordIcon(r)
                StatusIcon(icon, StatusIconSizes.L, Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.testTag("today-done-icon:${r.id}:$kind"))
                Column(Modifier.weight(1f)) {
                    Text(listOfNotNull(formatTime(Instant.ofEpochMilli(r.taken_utc ?: r.scheduled_utc ?: 0)), if (simple) null else m?.name,
                        (r.actual_dose ?: r.planned_dose)?.let { formatDose(it, m?.unit) }, stringResource(recordStatus(r))).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium.tnum(), modifier = Modifier.testTag("today-done:${r.id}"))
                    if (r.unconfirmed) Text(stringResource(R.string.unconfirmed_help), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
            }
        }
    }
    // UI-04: today's appointments, one grouped item each; a tap opens the visit as the day list's AppointmentCard did.
    if (appointments.isNotEmpty()) Section(R.string.today_appointment, "today-appts", appointments, c.primary, grouped = true) { a, i, n ->
        GroupedItem(i, n, onClick = { onAppointment(a) }, modifier = Modifier.testTag("today-appt:${a.id}")) {
            Row(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.EventNote, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text(listOfNotNull(choiceLabel(a.type.ifBlank { "OTHER" }), formatTime(Instant.ofEpochMilli(a.at_utc)), a.practitioner?.takeIf { it.isNotBlank() },
                    a.location?.takeIf { it.isNotBlank() }).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    }
}

/** What the today card's "next" part shows; [mode] 0 next dose, 1 all done, 2 nothing today, 3 none open. */
private data class NextView(val next: TimelineEntry?, val open: List<TimelineEntry>, val mode: Int)

/** How long "Logged · Undo" stays after a dose is logged. */
internal const val UNDO_WINDOW_MS = 4_000L

/** A titled list: three rows, then "Show all (n)"; the choice survives recreation. UI-07 D: the list's height springs open. */
@Composable private fun <T> Section(title: Int, tag: String, rows: List<T>, accent: androidx.compose.ui.graphics.Color, row: @Composable (T) -> Unit) =
    Section(title, tag, rows, accent, grouped = false) { r, _, _ -> row(r) }

@Composable private fun <T> Section(title: Int, tag: String, rows: List<T>, accent: androidx.compose.ui.graphics.Color, grouped: Boolean, row: @Composable (T, Int, Int) -> Unit) {
    var all by rememberSaveable(tag) { mutableStateOf(false) }
    val reduce = LocalReduceMotion.current
    Column(Modifier.animateContentSize(motionSpec(reduce)), verticalArrangement = Arrangement.spacedBy(if (grouped) GroupedGap else 4.dp)) {
        Text(stringResource(title), style = NotesTheme.typeExtra.sectionTitle, modifier = Modifier.padding(bottom = if (grouped) Spacing.xs else 0.dp).semantics { heading() }.testTag(tag))
        val shown = if (all) rows else rows.take(3)
        shown.forEachIndexed { i, r -> row(r, i, shown.size) }
        if (!all && rows.size > 3) TextButton({ all = true }, Modifier.testTag("today-show-all:" + tag.removePrefix("today-")),
            colors = ButtonDefaults.textButtonColors(contentColor = accent)) { Text(stringResource(R.string.today_show_all, rows.size)) }
    }
}

/** UI-05 A / UI-06 / UI-07: kind per record status from the status table, and its filled-circle icon. */
private fun recordIcon(r: RecordEntity): Pair<String, StatusIconKind> {
    val style = StatusStyles.ofRecord(r.status, r.unconfirmed)
    val kind = when (style.state) { DoseState.ON_TIME, DoseState.LATE -> "TAKEN"; DoseState.UNCONFIRMED -> "UNCONFIRMED"; DoseState.MISSED -> "MISSED"; else -> "SKIPPED" }
    return kind to statusIconKind(style.state)!!
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


/** Name, then dose and route, as on DoseCard. Simple mode hides the name there too. */
@Composable private fun DoseLine(e: TimelineEntry, med: MedicationEntity?, simple: Boolean, modifier: Modifier,
                                 title: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium) {
    Column(modifier) {
        if (!simple) Text(med?.name ?: "", style = title)
        Text(listOfNotNull(formatDose(e.slot.dose, med?.unit), med?.route?.let { choiceLabel(it) }).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun DoseMenu(e: TimelineEntry, tag: String, onComplete: (TimelineEntry) -> Unit, onChange: (TimelineEntry) -> Unit, round: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Box {
        // UI-07 B: next to the main button the menu is a 56 dp outlined square (18 dp corners) of the same height.
        if (round) OutlinedIconButton({ open = true }, Modifier.size(56.dp).testTag(tag), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
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
    val low = lowStockMedications(meds, forecasts, today, zone)
    if (low.isEmpty()) return
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().testTag("home-stock"), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        low.take(3).forEach { m ->
            val f = forecasts.getValue(m.id)
            val days = f.daysUntilShort(today, zone)
            Box(Modifier.testTag("home-stock-row:${m.id}")) {
                // UI-07 B: home cards are 28 dp.
                Surface(onClick = onStock, color = c.tertiaryContainer, contentColor = c.onTertiaryContainer, shape = Corners.xl,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("home-stock-view")) {
                    Row(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        Icon(Icons.Outlined.Inventory2, null)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.home_stock_low, m.name), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(if (f.lastCovered != null && days != null && days >= 1) stringResource(R.string.stock_runs_out_in, days) else stringResource(R.string.forecast_empty),
                                style = MaterialTheme.typography.bodySmall.tnum())
                        }
                        val press = remember { MutableInteractionSource() }; val morph = rememberPressMorph(press, rest = 24.dp)
                        Button({ onAdd(m) }, Modifier.heightIn(min = 48.dp).pressMorph(morph).testTag("home-stock-add:${m.id}"), shape = morph.shape, interactionSource = press,
                            colors = ButtonDefaults.buttonColors(containerColor = c.tertiary, contentColor = c.onTertiary)) {
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
