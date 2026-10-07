package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.AppointmentEntity
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.domain.ScheduleEngine
import net.plainnotes.app.domain.SlotOverride
import net.plainnotes.app.domain.TimelineEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Picker values are wall times; DST gaps move forward, overlaps take the earlier offset. */
fun LocalDateTime.toInstantHere(): Instant = ScheduleEngine.wallInstant(this, ZoneId.systemDefault())
fun Instant.toLocalHere(): LocalDateTime = atZone(ZoneId.systemDefault()).toLocalDateTime().withSecond(0).withNano(0)

@Composable fun IntakeDialog(title: String, medication: MedicationEntity?, plannedDose: Double, initial: Instant, onDismiss: () -> Unit, siteSuggestion: String? = null,
                             onSave: (Instant, Double, String?) -> Unit) {
    var time by remember { mutableStateOf(initial.toLocalHere()) }
    var site by remember { mutableStateOf(siteSuggestion) }
    var dose by remember { mutableStateOf(inputNumber(plannedDose)) }
    val doseV = dose.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    val future = time.toInstantHere().isAfter(Instant.now().plusSeconds(60))
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.CheckCircle, null) }, title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            medication?.let { Text(it.name, style = MaterialTheme.typography.titleMedium) }
            Text(stringResource(R.string.actual_time), style = MaterialTheme.typography.labelLarge)
            DateTimeRow(time, { time = it })
            if (future) Text(stringResource(R.string.future_time_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            NumberField(dose, { dose = it }, stringResource(R.string.actual_dose), suffix = medication?.let { unitLabel(it.unit) }, isError = doseV == null)
            if (siteSuggestion != null) SitePicker(site, siteSuggestion) { site = it }
        } },
        confirmButton = { Button(onClick = { onSave(time.toInstantHere(), doseV!!, site) }, enabled = doseV != null && !future) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable fun OverrideDialog(entry: TimelineEntry, medication: MedicationEntity?, initial: SlotOverride, onDismiss: () -> Unit, onSave: (SlotOverride) -> Unit) {
    val slot = entry.slot
    var moved by remember { mutableStateOf(initial.rescheduled != null) }
    var time by remember { mutableStateOf((initial.rescheduled ?: slot.original).toLocalHere()) }
    var customDose by remember { mutableStateOf(initial.dose != null) }
    var dose by remember { mutableStateOf(inputNumber(initial.dose ?: slot.originalDose)) }
    var skip by remember { mutableStateOf(initial.skipped) }
    val doseV = dose.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.EditCalendar, null) }, title = { Text(stringResource(R.string.reschedule)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(listOfNotNull(medication?.name, stringResource(R.string.originally, formatDateTime(slot.original))).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            SwitchRow(stringResource(R.string.skipped), skip) { skip = it }
            if (!skip) {
                SwitchRow(stringResource(R.string.change_time), moved) { moved = it }
                if (moved) DateTimeRow(time, { time = it })
                SwitchRow(stringResource(R.string.change_dose), customDose) { customDose = it }
                if (customDose) NumberField(dose, { dose = it }, stringResource(R.string.dose), suffix = medication?.let { unitLabel(it.unit) }, isError = doseV == null)
            }
            if (!initial.isDefault) TextButton(onClick = { onSave(SlotOverride(slot.key)) }) { Text(stringResource(R.string.reset_all)) }
        } },
        confirmButton = { Button(enabled = skip || !customDose || doseV != null, onClick = {
            val t = if (moved && !skip) time.toInstantHere() else null
            onSave(SlotOverride(slot.key, t, t?.let { ZoneId.systemDefault() }, if (customDose && !skip) doseV else null, skip))
        }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable fun SitePicker(site: String?, suggestion: String, onChange: (String) -> Unit) {
    Text(stringResource(R.string.site_title, siteLabel(suggestion)), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SITES.forEach { s -> FilterChip(site == s, { onChange(s) }, label = { Text(siteLabel(s)) }) } }
}

@Composable fun ManualIntakeDialog(meds: List<MedicationEntity>, siteFor: (MedicationEntity) -> String?, onDismiss: () -> Unit, onSave: (Long, Instant, Double, String?) -> Unit) {
    var chosen by remember { mutableStateOf(meds.firstOrNull()) }
    var time by remember { mutableStateOf(Instant.now().toLocalHere()) }
    var dose by remember { mutableStateOf(chosen?.dose_per_intake?.let(::inputNumber) ?: "") }
    var site by remember(chosen) { mutableStateOf(chosen?.let(siteFor)) }
    val doseV = dose.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    val future = time.toInstantHere().isAfter(Instant.now().plusSeconds(60))
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.AddTask, null) }, title = { Text(stringResource(R.string.manual)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (meds.size > 1) DropdownField(stringResource(R.string.medication), meds, chosen, { it.name }, { chosen = it; dose = inputNumber(it.dose_per_intake) })
            else chosen?.let { Text(it.name, style = MaterialTheme.typography.titleMedium) }
            DateTimeRow(time, { time = it })
            if (future) Text(stringResource(R.string.future_time_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            NumberField(dose, { dose = it }, stringResource(R.string.actual_dose), suffix = chosen?.let { unitLabel(it.unit) }, isError = doseV == null)
            chosen?.let(siteFor)?.let { sug -> SitePicker(site, sug) { site = it } }
        } },
        confirmButton = { Button(enabled = chosen != null && doseV != null && !future, onClick = { onSave(chosen!!.id, time.toInstantHere(), doseV!!, site) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

val APPOINTMENT_TYPES = listOf("ENDO", "GP", "LAB", "PSY", "SURGERY", "OTHER")

@Composable fun AppointmentDialog(onDismiss: () -> Unit, initial: AppointmentEntity? = null, onSave: (AppointmentEntity) -> Unit) {
    var time by remember { mutableStateOf(initial?.let { Instant.ofEpochMilli(it.at_utc).toLocalHere() } ?: Instant.now().plusSeconds(86400).toLocalHere().withMinute(0)) }
    var type by remember { mutableStateOf(initial?.type?.takeIf { it in APPOINTMENT_TYPES } ?: if (initial == null) "ENDO" else "OTHER") }
    var location by remember { mutableStateOf(initial?.location.orEmpty()) }; var practitioner by remember { mutableStateOf(initial?.practitioner.orEmpty()) }; var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var reminder by remember { mutableStateOf(initial?.remind_minutes_before?.toString() ?: "60") }
    val reminderV = reminder.toIntOrNull()?.takeIf { it >= 0 }
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.AutoMirrored.Outlined.EventNote, null) }, title = { Text(stringResource(R.string.appointment)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DropdownField(stringResource(R.string.appointment_type), APPOINTMENT_TYPES, type, { choiceLabel(it) }, { type = it })
            DateTimeRow(time, { time = it })
            OutlinedTextField(practitioner, { practitioner = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.practitioner)) }, singleLine = true)
            OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.location)) }, singleLine = true)
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.note)) })
            NumberField(reminder, { reminder = it }, stringResource(R.string.reminder_before), suffix = stringResource(R.string.minutes_unit), decimal = false, isError = reminderV == null)
        } },
        confirmButton = { Button(enabled = reminderV != null, onClick = {
            onSave(AppointmentEntity(id = initial?.id ?: 0, completed_utc = initial?.completed_utc, type = type, at_utc = time.toInstantHere().toEpochMilli(), at_zone = ZoneId.systemDefault().id, location = location.ifBlank { null },
                practitioner = practitioner.ifBlank { null }, note = note.ifBlank { null }, remind_minutes_before = reminderV!!))
        }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

/** Batch backfill of past intakes over a date range at fixed daily times (HRT tracker's "batch add"). History only, no stock change. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun BatchAddDialog(meds: List<MedicationEntity>, timesFor: (MedicationEntity) -> List<LocalTime>, onDismiss: () -> Unit,
                               onSave: (Long, LocalDate, LocalDate, List<LocalTime>, Double) -> Unit) {
    var chosen by remember { mutableStateOf(meds.firstOrNull()) }
    var from by remember { mutableStateOf(LocalDate.now().minusDays(13)) }
    var to by remember { mutableStateOf(LocalDate.now()) }
    var times by remember { mutableStateOf(chosen?.let(timesFor) ?: TWICE_DAILY) }
    var dose by remember { mutableStateOf(chosen?.dose_per_intake?.let(::inputNumber) ?: "") }
    var pickFrom by remember { mutableStateOf(false) }; var pickTo by remember { mutableStateOf(false) }; var addTime by remember { mutableStateOf(false) }
    val doseV = dose.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    val now = Instant.now()
    val count = if (to.isBefore(from)) 0 else generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
        .sumOf { d -> times.count { d.atTime(it).atZone(ZoneId.systemDefault()).toInstant().isBefore(now) } }
    val rangeOk = !to.isBefore(from) && java.time.temporal.ChronoUnit.DAYS.between(from, to) <= net.plainnotes.app.data.BACKFILL_MAX_DAYS
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.Layers, null) }, title = { Text(stringResource(R.string.batch_add)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (meds.size > 1) DropdownField(stringResource(R.string.medication), meds, chosen, { it.name }, { chosen = it; dose = inputNumber(it.dose_per_intake); times = timesFor(it) })
            else chosen?.let { Text(it.name, style = MaterialTheme.typography.titleMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickFrom = true }, Modifier.weight(1f)) { Column { Text(stringResource(R.string.batch_from), style = MaterialTheme.typography.labelSmall); Text(formatShortDate(from)) } }
                OutlinedButton(onClick = { pickTo = true }, Modifier.weight(1f)) { Column { Text(stringResource(R.string.batch_to), style = MaterialTheme.typography.labelSmall); Text(formatShortDate(to)) } }
            }
            if (!rangeOk) Text(stringResource(R.string.batch_range_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.batch_times), style = MaterialTheme.typography.labelLarge)
            TimesPerDayRow(times.size.coerceIn(1, MAX_TIMES_PER_DAY)) { times = evenTimes(it) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                times.forEach { t -> InputChip(true, { times = times - t }, label = { Text(formatTime(t)) }, trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.remove), Modifier.size(16.dp)) }) }
                AssistChip({ addTime = true }, label = { Text(stringResource(R.string.add_time)) }, leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)) })
            }
            NumberField(dose, { dose = it }, stringResource(R.string.batch_dose), suffix = chosen?.let { unitLabel(it.unit) }, isError = doseV == null)
            Text(stringResource(R.string.batch_summary, count), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.batch_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { Button(enabled = chosen != null && doseV != null && rangeOk && times.isNotEmpty() && count > 0, onClick = { onSave(chosen!!.id, from, to, times.sorted(), doseV!!) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
    if (pickFrom) DatePickerModal(from, { pickFrom = false }) { from = it; pickFrom = false }
    if (pickTo) DatePickerModal(to, { pickTo = false }) { to = it; pickTo = false }
    if (addTime) TimePickerModal(LocalTime.of(12, 0), { addTime = false }) { if (it !in times) times = (times + it).sorted(); addTime = false }
}

/** Morning and evening: the default times of batch add when the medication has no daily schedule. */
val TWICE_DAILY: List<LocalTime> = evenTimes(2)

const val MAX_TIMES_PER_DAY = 6

/** [n] intake times a day: 09:00 for one, otherwise spread evenly from 08:00 to 20:00 and rounded to the half hour. Times only, never a dose. */
fun evenTimes(n: Int): List<LocalTime> {
    require(n in 1..MAX_TIMES_PER_DAY)
    if (n == 1) return listOf(LocalTime.of(9, 0))
    return (0 until n).map { i -> val min = 8 * 60 + Math.round(i * 720.0 / (n - 1) / 30).toInt() * 30; LocalTime.of(min / 60, min % 60) }.distinct()
}

/** "Times a day" stepper: changing the count refills the times evenly; each time stays editable afterwards. */
@Composable fun TimesPerDayRow(count: Int, onCount: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.times_per_day), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = { onCount(count - 1) }, enabled = count > 1) { Icon(Icons.Outlined.Remove, stringResource(R.string.times_fewer)) }
        Text(stringResource(R.string.times_per_day_value, count), style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = { onCount(count + 1) }, enabled = count < MAX_TIMES_PER_DAY) { Icon(Icons.Outlined.Add, stringResource(R.string.times_more)) }
    }
}
