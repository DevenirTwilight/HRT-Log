package net.plainnotes.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

private sealed interface CalendarItem { val at: Instant }
private class DoseItem(val entry: TimelineEntry) : CalendarItem { override val at: Instant get() = entry.slot.at }
private class AppointmentItem(val appointment: AppointmentEntity) : CalendarItem { override val at: Instant get() = Instant.ofEpochMilli(appointment.at_utc) }

@Composable fun CalendarScreen(state: NotesState, today: LocalDate, onComplete: (TimelineEntry) -> Unit, onChange: (TimelineEntry) -> Unit,
                               onAddMedication: () -> Unit, onResetStart: () -> Unit, contentPadding: PaddingValues, onReview: () -> Unit = {}) {
    val zone = ZoneId.systemDefault()
    val meds = state.medications.associateBy { it.id }
    val windowStart = state.calendarStart.atStartOfDay(zone).toInstant()
    val items = (state.slots.map { DoseItem(it) } + state.appointments.filter { Instant.ofEpochMilli(it.at_utc) >= windowStart }.map { AppointmentItem(it) })
        .sortedBy { it.at }
    val groups = items.groupBy { it.at.atZone(zone).toLocalDate() }.toSortedMap()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp,
        bottom = contentPadding.calculateBottomPadding() + 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val pending = state.medications.count { it.needs_review != null }
        if (pending > 0) item {
            Surface(onClick = onReview, color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onTertiaryContainer); Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.review_banner, pending), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
        if (state.calendarStart != today) item {
            AssistChip(onClick = onResetStart, label = { Text(stringResource(R.string.calendar_showing_from, formatShortDate(state.calendarStart))) },
                trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.calendar_back_today), Modifier.size(18.dp)) })
        }
        if (groups.isEmpty()) item {
            if (state.medications.isEmpty()) EmptyState(Icons.Outlined.Medication, stringResource(R.string.empty_title), stringResource(R.string.empty_body)) {
                Button(onClick = onAddMedication) { Text(stringResource(R.string.add_medication)) }
            } else EmptyState(Icons.Outlined.EventAvailable, stringResource(R.string.nothing_planned_title), stringResource(R.string.nothing_planned_body))
        }
        groups.forEach { (date, list) ->
            item(key = "h$date") { DayHeader(date, today) }
            items(list, key = { when (it) { is DoseItem -> it.entry.slot.key; is AppointmentItem -> "a${it.appointment.id}" } }) { item ->
                when (item) {
                    is DoseItem -> DoseCard(item.entry, meds[item.entry.slot.medicationId], { onComplete(item.entry) }, { onChange(item.entry) })
                    is AppointmentItem -> AppointmentCard(item.appointment)
                }
            }
        }
    }
}

@Composable private fun DayHeader(date: LocalDate, today: LocalDate) {
    val days = ChronoUnit.DAYS.between(today, date)
    val title = when (days) { 0L -> stringResource(R.string.today); 1L -> stringResource(R.string.tomorrow); -1L -> stringResource(R.string.yesterday); else -> formatShortDate(date) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.Bottom) {
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
    SlotState.ON_TIME -> R.string.status_on_time; SlotState.LATE -> R.string.status_late; SlotState.MISSED -> R.string.status_missed; SlotState.SKIPPED -> R.string.status_skipped
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
                if (st == SlotState.MISSED) Text(stringResource(R.string.missed_note), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                if (!done) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (st == SlotState.OVERDUE || st == SlotState.SOON || st == SlotState.MISSED) Button(onClick = onComplete) { Text(stringResource(R.string.complete)) }
                    else FilledTonalButton(onClick = onComplete) { Text(stringResource(R.string.complete)) }
                    if (st != SlotState.MISSED) TextButton(onClick = onChange) { Text(stringResource(R.string.reschedule)) }
                }
            }
        }
    }
}

@Composable private fun AppointmentCard(a: AppointmentEntity) {
    val c = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = c.secondaryContainer)) {
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
