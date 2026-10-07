package net.plainnotes.app.ui
import net.plainnotes.app.data.unconfirmed
import net.plainnotes.app.data.MedicationSnapshot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.data.RecordEntity
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.plainnotes.app.NotesState
import net.plainnotes.app.R
import net.plainnotes.app.data.AppointmentEntity
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.domain.SlotState
import net.plainnotes.app.domain.TimelineEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class CalView(val label: Int) { DAY(R.string.view_day), WEEK(R.string.view_week), MONTH(R.string.view_month), YEAR(R.string.view_year) }

@OptIn(ExperimentalLayoutApi::class)
@Composable fun CalendarScreen(state: NotesState, today: LocalDate, onComplete: (TimelineEntry) -> Unit, onChange: (TimelineEntry) -> Unit,
                               onAddMedication: () -> Unit, onResetStart: () -> Unit, contentPadding: PaddingValues, onReview: () -> Unit = {},
                               extra: NotesViewModel.ExtraState = NotesViewModel.ExtraState(), onStock: () -> Unit = {}, initialView: CalView = CalView.MONTH,
                               onAppointment: (AppointmentEntity) -> Unit = {}) {
    val zone = ZoneId.systemDefault()
    val meds = state.medications.associateBy { it.id }
    var view by rememberSaveable { mutableStateOf(initialView) }
    var selectedText by rememberSaveable { mutableStateOf(state.calendarStart.toString()) }
    val selected = LocalDate.parse(selectedText)
    LaunchedEffect(state.calendarStart) { selectedText = state.calendarStart.toString() }
    val upcoming = remember(extra.upcoming) { extra.upcoming }
    val runOut = remember(state.medications, extra.containers, upcoming) { forecast(state.medications, extra.containers, upcoming) }
    val displayedUpcoming = remember(upcoming, state.slots) { (state.slots + upcoming).distinctBy { it.slot.key }.filter { it.state in OPEN_STATES } }
    val infos = remember(extra.records, displayedUpcoming, state.appointments, runOut) { dayInfos(extra.records, displayedUpcoming, state.appointments, runOut, zone) }
    fun select(d: LocalDate) { selectedText = d.toString() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp,
        bottom = contentPadding.calculateBottomPadding() + 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val pending = state.medications.count { it.needs_review != null }
        if (pending > 0) item {
            Surface(onClick = onReview, color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onTertiaryContainer); Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.review_banner, pending), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
        if (state.medications.isEmpty()) item {
            EmptyState(Icons.Outlined.Medication, stringResource(R.string.empty_title), stringResource(R.string.empty_body)) { Button(onClick = onAddMedication) { Text(stringResource(R.string.add_medication)) } }
        }
        item {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PeriodBar(view, selected, today, { select(it) })
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        CalView.entries.forEachIndexed { i, v -> SegmentedButton(view == v, { view = v }, SegmentedButtonDefaults.itemShape(i, CalView.entries.size), icon = {}) { Text(stringResource(v.label)) } }
                    }
                    when (view) {
                        CalView.MONTH -> MonthGrid(YearMonth.from(selected), infos, today, selected) { select(it) }
                        CalView.WEEK -> WeekStrip(selected, infos, today, extra.records, upcoming, runOut, zone) { select(it) }
                        CalView.YEAR -> YearGrid(selected.year, infos, today) { select(it); view = CalView.MONTH }
                        CalView.DAY -> {}
                    }
                    Legend(view == CalView.YEAR)
                }
            }
        }
        if (view != CalView.YEAR) {
            item(key = "dayhead") { DayHeader(selected, today) }
            val dayRecords = extra.records.filter { r -> r.deleted_at_utc == null && (r.taken_utc ?: r.scheduled_utc)?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } == selected }
                .sortedBy { it.taken_utc ?: it.scheduled_utc }
            val dayOpen = upcoming.filter { it.slot.at.atZone(zone).toLocalDate() == selected }.sortedBy { it.slot.at }
            val dayAppts = state.appointments.filter { Instant.ofEpochMilli(it.at_utc).atZone(zone).toLocalDate() == selected }
            if (dayRecords.isEmpty() && dayOpen.isEmpty() && dayAppts.isEmpty()) item { Text(stringResource(R.string.calendar_day_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp)) }
            items(dayOpen, key = { it.slot.key }) { e -> DoseCard(e, state.ruleSnapshots[e.slot.ruleId]?.let{MedicationSnapshot.decode(it,e.slot.medicationId)?.medication(e.slot.medicationId)} ?: meds[e.slot.medicationId], { onComplete(e) }, { onChange(e) }) }
            items(dayRecords, key = { "r${it.id}" }) { r -> RecordLine(r, MedicationSnapshot.decode(r.config_snapshot,r.medication_id)?.medication(r.medication_id)) }
            items(dayAppts, key = { "a${it.id}" }) { AppointmentCard(it) { onAppointment(it) } }
        }
        item(key = "stock") { StockForecast(state.medications.filter { it.active }, runOut, upcoming, today, zone, onStock) }
    }
}

@Composable private fun PeriodBar(view: CalView, selected: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    val title = when (view) {
        CalView.DAY -> formatDate(selected)
        CalView.WEEK -> { val s = weekStart(selected); "${formatShortDate(s)} – ${formatShortDate(s.plusDays(6))}" }
        CalView.MONTH -> selected.format(java.time.format.DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(currentLocale(), "yMMMM"), currentLocale()))
        CalView.YEAR -> selected.year.toString()
    }
    fun step(n: Long) = when (view) { CalView.DAY -> selected.plusDays(n); CalView.WEEK -> selected.plusWeeks(n); CalView.MONTH -> selected.plusMonths(n); CalView.YEAR -> selected.plusYears(n) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSelect(step(-1)) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.calendar_previous)) }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
        if (selected != today) TextButton(onClick = { onSelect(today) }) { Text(stringResource(R.string.today)) }
        IconButton(onClick = { onSelect(step(1)) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.calendar_next)) }
    }
}

@Composable private fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(WeekFields.of(currentLocale()).firstDayOfWeek))

@Composable private fun kindColors(kind: DayKind): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    return when (kind) {
        DayKind.TAKEN -> c.primaryContainer to c.onPrimaryContainer
        DayKind.PARTIAL -> c.tertiaryContainer to c.onTertiaryContainer
        DayKind.MISSED -> c.error to c.onError
        DayKind.UNCONFIRMED -> c.surfaceContainerHigh to c.onSurfaceVariant
        DayKind.PLANNED -> c.surfaceContainerHighest to c.onSurface
        DayKind.NONE -> Color.Transparent to c.onSurface
    }
}

@Composable private fun WeekdayHeader() {
    val first = WeekFields.of(currentLocale()).firstDayOfWeek
    Row(Modifier.fillMaxWidth()) {
        (0 until 7).forEach { i -> Text(weekdayShort(first.plus(i.toLong())), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable private fun MonthGrid(month: YearMonth, infos: Map<LocalDate, DayInfo>, today: LocalDate, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val start = weekStart(month.atDay(1))
    val weeks = ((java.time.temporal.ChronoUnit.DAYS.between(start, month.atEndOfMonth()) / 7) + 1).toInt()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        WeekdayHeader()
        (0 until weeks).forEach { w ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 7).forEach { d ->
                    val date = start.plusDays(w * 7L + d)
                    DayCell(date, infos[date], date == today, date == selected, YearMonth.from(date) == month, Modifier.weight(1f).height(54.dp)) { onSelect(date) }
                }
            }
        }
    }
}

@Composable private fun DayCell(date: LocalDate, info: DayInfo?, isToday: Boolean, isSelected: Boolean, inMonth: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val (bg, fg) = kindColors(info?.kind ?: DayKind.NONE)
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(bg).then(if (isSelected) Modifier.border(2.dp, c.primary, RoundedCornerShape(12.dp)) else Modifier)
        .clickable(onClick = onClick).alpha(if (inMonth) 1f else 0.4f).padding(4.dp)) {
        Box(Modifier.align(Alignment.TopCenter).size(26.dp).clip(CircleShape).background(if (isToday) c.primary else Color.Transparent), contentAlignment = Alignment.Center) {
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal, color = if (isToday) c.onPrimary else fg)
        }
        Row(Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (info != null) {
                if (info.runOut.isNotEmpty()) Icon(Icons.Outlined.Inventory2, null, Modifier.size(12.dp), tint = c.error)
                else repeat(minOf(info.open, 3)) { Box(Modifier.size(5.dp).clip(CircleShape).background(if (info.short > 0) c.error else c.primary)) }
                if (info.appointments > 0) Box(Modifier.size(5.dp).clip(RoundedCornerShape(1.dp)).background(c.secondary))
            }
        }
    }
}

@Composable private fun WeekStrip(selected: LocalDate, infos: Map<LocalDate, DayInfo>, today: LocalDate, records: List<RecordEntity>, upcoming: List<TimelineEntry>, runOut: Map<Long, RunOut>, zone: ZoneId, onSelect: (LocalDate) -> Unit) {
    val start = weekStart(selected); val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (0 until 7).forEach { i ->
            val date = start.plusDays(i.toLong()); val info = infos[date]
            val (bg, fg) = kindColors(info?.kind ?: DayKind.NONE)
            val marks = records.filter { it.deleted_at_utc == null && (it.taken_utc ?: it.scheduled_utc)?.let { t -> Instant.ofEpochMilli(t).atZone(zone).toLocalDate() } == date }
                .map { Instant.ofEpochMilli(it.taken_utc ?: it.scheduled_utc!!) to (if(it.unconfirmed) "UNCONFIRMED" else it.status) } +
                upcoming.filter { it.slot.at.atZone(zone).toLocalDate() == date }
                    .map { e -> e.slot.at to (if (runOut[e.slot.medicationId]?.firstShort?.let { !e.slot.at.isBefore(it) } == true) "SHORT" else "OPEN") }
            Column(Modifier.weight(1f).heightIn(min = 140.dp).clip(RoundedCornerShape(12.dp)).background(bg)
                .then(if (date == selected) Modifier.border(2.dp, c.primary, RoundedCornerShape(12.dp)) else Modifier).clickable { onSelect(date) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(weekdayShort(date.dayOfWeek), style = MaterialTheme.typography.labelSmall, color = fg)
                Box(Modifier.size(26.dp).clip(CircleShape).background(if (date == today) c.primary else Color.Transparent), contentAlignment = Alignment.Center) {
                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (date == today) c.onPrimary else fg)
                }
                marks.sortedBy { it.first }.take(5).forEach { (t, st) ->
                    val col = when (st) { "ON_TIME", "LATE" -> c.primary; "MISSED" -> c.error; "SKIPPED" -> c.outline; "SHORT" -> c.error; else -> c.onSurfaceVariant }
                    Text(formatTime(t), style = MaterialTheme.typography.labelSmall, color = col, maxLines = 1)
                }
                if (info?.runOut?.isNotEmpty() == true) Icon(Icons.Outlined.Inventory2, null, Modifier.size(14.dp), tint = c.error)
            }
        }
    }
}

@Composable private fun YearGrid(year: Int, infos: Map<LocalDate, DayInfo>, today: LocalDate, onMonth: (LocalDate) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        (1..12).chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { m ->
                    val ym = YearMonth.of(year, m); val start = weekStart(ym.atDay(1))
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { onMonth(ym.atDay(1)) }.padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(ym.month.getDisplayName(java.time.format.TextStyle.SHORT, currentLocale()), style = MaterialTheme.typography.labelLarge,
                            color = if (YearMonth.from(today) == ym) c.primary else c.onSurface)
                        val weeks = ((java.time.temporal.ChronoUnit.DAYS.between(start, ym.atEndOfMonth()) / 7) + 1).toInt()
                        (0 until weeks).forEach { w ->
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                (0 until 7).forEach { d ->
                                    val date = start.plusDays(w * 7L + d)
                                    val color = if (YearMonth.from(date) != ym) Color.Transparent else when (infos[date]?.kind ?: DayKind.NONE) {
                                        DayKind.TAKEN -> c.primary; DayKind.PARTIAL -> c.tertiary; DayKind.MISSED -> c.error; DayKind.UNCONFIRMED -> c.outline; DayKind.PLANNED -> c.outlineVariant; DayKind.NONE -> c.surfaceContainerHigh
                                    }
                                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(2.dp)).background(color)
                                        .then(if (date == today) Modifier.border(1.dp, c.onSurface, RoundedCornerShape(2.dp)) else Modifier))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Legend(year: Boolean) {
    val c = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val swatches = listOf(c.outline to R.string.status_unconfirmed) + if (year) listOf(c.primary to R.string.legend_taken, c.tertiary to R.string.legend_partial, c.error to R.string.legend_missed, c.outlineVariant to R.string.legend_planned)
            else listOf(c.primaryContainer to R.string.legend_taken, c.tertiaryContainer to R.string.legend_partial, c.error to R.string.legend_missed, c.surfaceContainerHighest to R.string.legend_planned)
        swatches.forEach { (col, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(col).border(0.5.dp, c.outlineVariant, RoundedCornerShape(3.dp))); Spacer(Modifier.width(4.dp))
                Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Inventory2, null, Modifier.size(12.dp), tint = c.error); Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.legend_runout), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
        }
    }
}

@Composable private fun RecordLine(r: RecordEntity, med: MedicationEntity?) {
    val c = MaterialTheme.colorScheme
    val (bg, fg, label) = when (r.status) {
        "ON_TIME" -> Triple(c.primaryContainer, c.onPrimaryContainer, R.string.status_on_time)
        "LATE" -> Triple(c.tertiaryContainer, c.onTertiaryContainer, R.string.status_late)
        "MISSED" -> if(r.unconfirmed)Triple(c.surfaceContainerHigh,c.onSurfaceVariant,R.string.status_unconfirmed) else Triple(c.errorContainer, c.onErrorContainer, R.string.status_missed)
        else -> Triple(c.surfaceContainerHighest, c.onSurfaceVariant, R.string.status_skipped)
    }
    Surface(color = c.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(Instant.ofEpochMilli(r.taken_utc ?: r.scheduled_utc ?: 0)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(64.dp))
            Column(Modifier.weight(1f)) {
                if (!LocalSimpleMode.current) Text(med?.name ?: stringResource(R.string.history_context_unknown), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                (r.actual_dose ?: r.planned_dose)?.let { Text(formatDose(it, med?.unit), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant) }
            }
            StatusPill(stringResource(label), bg, fg, if (r.status in listOf("ON_TIME", "LATE")) Icons.Rounded.Check else null)
        }
    }
}

@Composable private fun StockForecast(meds: List<MedicationEntity>, runOut: Map<Long, RunOut>, upcoming: List<TimelineEntry>, today: LocalDate, zone: ZoneId, onStock: () -> Unit) {
    if (meds.isEmpty()) return
    val c = MaterialTheme.colorScheme
    SectionCard(stringResource(R.string.forecast_title)) {
        meds.forEach { m ->
            val f = runOut[m.id]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Inventory2, null, tint = c.primary); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name, style = MaterialTheme.typography.titleSmall)
                    val hasPlan = upcoming.any { it.slot.medicationId == m.id }
                    val (text, warn) = when {
                        f == null -> stringResource(R.string.forecast_untracked) to false
                        !hasPlan -> stringResource(R.string.forecast_no_plan, formatDose(f.remaining, m.unit)) to false
                        f.lastCovered == null -> stringResource(R.string.forecast_empty) to true
                        f.beyondHorizon -> stringResource(R.string.forecast_year, formatDose(f.remaining, m.unit)) to false
                        else -> { val d = f.lastCovered.atZone(zone).toLocalDate(); val days = f.daysLeft(Instant.now()) ?: 0
                            stringResource(R.string.forecast_until, formatShortDate(d), days, formatDose(f.remaining, m.unit)) to (days < LOW_STOCK_DAYS) }
                    }
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = if (warn) c.error else c.onSurfaceVariant)
                }
                if (f == null) TextButton(onClick = onStock) { Text(stringResource(R.string.forecast_add)) }
            }
        }
        Text(stringResource(R.string.forecast_note), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
    }
}

@Composable private fun DayHeader(date: LocalDate, today: LocalDate) {
    val days = ChronoUnit.DAYS.between(today, date)
    val title = when (days) { 0L -> stringResource(R.string.today); 1L -> stringResource(R.string.tomorrow); -1L -> stringResource(R.string.yesterday); else -> formatShortDate(date) }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        val detail = when {
            days == 0L || days == 1L || days == -1L -> formatShortDate(date)
            days > 1 -> stringResource(R.string.in_days, days.toInt())
            else -> stringResource(R.string.days_ago, (-days).toInt())
        }
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun stateLabel(state: SlotState) = stringResource(when (state) {
    SlotState.PENDING -> R.string.status_pending; SlotState.SOON -> R.string.status_soon; SlotState.OVERDUE -> R.string.status_overdue
    SlotState.ON_TIME -> R.string.status_on_time; SlotState.LATE -> R.string.status_late; SlotState.MISSED -> R.string.status_missed; SlotState.UNCONFIRMED -> R.string.status_unconfirmed; SlotState.SKIPPED -> R.string.status_skipped
})

@Composable private fun DoseCard(entry: TimelineEntry, med: MedicationEntity?, onComplete: () -> Unit, onChange: () -> Unit) {
    val s = entry.slot; val st = entry.state; val c = MaterialTheme.colorScheme
    val done = st == SlotState.ON_TIME || st == SlotState.LATE || st == SlotState.SKIPPED
    val (pillBg, pillFg) = when (st) {
        SlotState.OVERDUE -> c.error to c.onError
        SlotState.SOON -> c.primary to c.onPrimary
        SlotState.ON_TIME -> c.primaryContainer to c.onPrimaryContainer
        SlotState.LATE -> c.tertiaryContainer to c.onTertiaryContainer
        SlotState.MISSED -> c.errorContainer to c.onErrorContainer
        else -> c.surfaceContainerHighest to c.onSurfaceVariant
    }
    val border = if (st == SlotState.OVERDUE) BorderStroke(1.5.dp, c.error) else null
    OutlinedCard(Modifier.fillMaxWidth().alpha(if (done) 0.72f else 1f), border = border ?: CardDefaults.outlinedCardBorder(),
        colors = CardDefaults.outlinedCardColors(containerColor = if (st == SlotState.OVERDUE) c.errorContainer.copy(alpha = 0.35f) else c.surfaceContainerLowest)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.width(64.dp)) {
                Text(formatTime(s.at), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    color = if (st == SlotState.OVERDUE) c.error else c.onSurface)
                if (s.at != s.original) Text(stringResource(R.string.moved_from, formatTime(s.original)), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!LocalSimpleMode.current) Text(med?.name ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(listOfNotNull(formatDose(s.dose, med?.unit), med?.route?.let { choiceLabel(it) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                StatusPill(stateLabel(st), pillBg, pillFg, if (done && st != SlotState.SKIPPED) Icons.Rounded.Check else if (st == SlotState.OVERDUE) Icons.Outlined.ErrorOutline else null)
                if(st==SlotState.UNCONFIRMED)Text(stringResource(R.string.unconfirmed_help),style=MaterialTheme.typography.bodySmall)
                if (st == SlotState.MISSED) Text(stringResource(R.string.missed_note), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                if (!done) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (st == SlotState.OVERDUE || st == SlotState.SOON || st == SlotState.MISSED) Button(onClick = onComplete) { Text(stringResource(R.string.complete)) }
                    else FilledTonalButton(onClick = onComplete) { Text(stringResource(R.string.complete)) }
                    if (st !in listOf(SlotState.MISSED,SlotState.UNCONFIRMED)) TextButton(onClick = onChange) { Text(stringResource(R.string.reschedule)) }
                }
            }
        }
    }
}

@Composable private fun AppointmentCard(a: AppointmentEntity, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Card(onClick, Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = c.secondaryContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.EventNote, null, tint = c.onSecondaryContainer)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(choiceLabel(a.type.ifBlank { "OTHER" }), style = MaterialTheme.typography.titleMedium, color = c.onSecondaryContainer)
                Text(listOfNotNull(formatTime(Instant.ofEpochMilli(a.at_utc)), a.practitioner?.takeIf { it.isNotBlank() }, a.location?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = c.onSecondaryContainer)
            }
        }
    }
}
