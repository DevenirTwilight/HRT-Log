package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.time.LocalDateTime
import java.time.ZoneId

/** Picker values are wall times; DST gaps move forward, overlaps take the earlier offset. */
fun LocalDateTime.toInstantHere(): Instant = ScheduleEngine.wallInstant(this, ZoneId.systemDefault())
fun Instant.toLocalHere(): LocalDateTime = atZone(ZoneId.systemDefault()).toLocalDateTime().withSecond(0).withNano(0)

@Composable fun IntakeDialog(title: String, medication: MedicationEntity?, plannedDose: Double, initial: Instant, onDismiss: () -> Unit, onSave: (Instant, Double) -> Unit) {
    var time by remember { mutableStateOf(initial.toLocalHere()) }
    var dose by remember { mutableStateOf(formatNumber(plannedDose)) }
    val doseV = dose.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    val future = time.toInstantHere().isAfter(Instant.now().plusSeconds(60))
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.CheckCircle, null) }, title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            medication?.let { Text(it.name, style = MaterialTheme.typography.titleMedium) }
            Text(stringResource(R.string.actual_time), style = MaterialTheme.typography.labelLarge)
            DateTimeRow(time, { time = it })
            if (future) Text(stringResource(R.string.future_time_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            NumberField(dose, { dose = it }, stringResource(R.string.actual_dose), suffix = medication?.let { unitLabel(it.unit) }, isError = doseV == null)
        } },
        confirmButton = { Button(onClick = { onSave(time.toInstantHere(), doseV!!) }, enabled = doseV != null && !future) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable fun OverrideDialog(entry: TimelineEntry, medication: MedicationEntity?, initial: SlotOverride, onDismiss: () -> Unit, onSave: (SlotOverride) -> Unit) {
    val slot = entry.slot
    var moved by remember { mutableStateOf(initial.rescheduled != null) }
    var time by remember { mutableStateOf((initial.rescheduled ?: slot.original).toLocalHere()) }
    var customDose by remember { mutableStateOf(initial.dose != null) }
    var dose by remember { mutableStateOf(formatNumber(initial.dose ?: slot.originalDose)) }
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

@Composable fun ManualIntakeDialog(meds: List<MedicationEntity>, onDismiss: () -> Unit, onSave: (Long, Instant, Double) -> Unit) {
    var chosen by remember { mutableStateOf(meds.firstOrNull()) }
    var time by remember { mutableStateOf(Instant.now().toLocalHere()) }
    var dose by remember { mutableStateOf(chosen?.dose_per_intake?.let(::formatNumber) ?: "") }
    val doseV = dose.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    val future = time.toInstantHere().isAfter(Instant.now().plusSeconds(60))
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.AddTask, null) }, title = { Text(stringResource(R.string.manual)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DropdownField(stringResource(R.string.medication), meds, chosen, { it.name }, { chosen = it; dose = formatNumber(it.dose_per_intake) })
            DateTimeRow(time, { time = it })
            if (future) Text(stringResource(R.string.future_time_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            NumberField(dose, { dose = it }, stringResource(R.string.actual_dose), suffix = chosen?.let { unitLabel(it.unit) }, isError = doseV == null)
        } },
        confirmButton = { Button(enabled = chosen != null && doseV != null && !future, onClick = { onSave(chosen!!.id, time.toInstantHere(), doseV!!) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

val APPOINTMENT_TYPES = listOf("ENDO", "GP", "LAB", "PSY", "SURGERY", "OTHER")

@Composable fun AppointmentDialog(onDismiss: () -> Unit, onSave: (AppointmentEntity) -> Unit) {
    var time by remember { mutableStateOf(Instant.now().plusSeconds(86400).toLocalHere().withMinute(0)) }
    var type by remember { mutableStateOf("ENDO") }
    var location by remember { mutableStateOf("") }; var practitioner by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }
    var reminder by remember { mutableStateOf("60") }
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
            onSave(AppointmentEntity(type = type, at_utc = time.toInstantHere().toEpochMilli(), at_zone = ZoneId.systemDefault().id, location = location.ifBlank { null },
                practitioner = practitioner.ifBlank { null }, note = note.ifBlank { null }, remind_minutes_before = reminderV!!))
        }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
