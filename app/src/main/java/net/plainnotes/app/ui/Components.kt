package net.plainnotes.app.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import java.time.*

/** Picker dates are UTC-midnight millis; convert without the device offset. */
private fun LocalDate.pickerMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.pickerDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DatePickerModal(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.pickerMillis())
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPick(it.pickerDate()) }; onDismiss() }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }) { DatePicker(state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TimePickerModal(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(LocalContext.current))
    AlertDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state) } })
}

/** Date and time chosen with pickers; the value is a local wall time in the device zone. */
@Composable fun DateTimeRow(value: LocalDateTime, onChange: (LocalDateTime) -> Unit, modifier: Modifier = Modifier) {
    var pickDate by remember { mutableStateOf(false) }; var pickTime by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pickDate = true }, Modifier.weight(1.4f)) {
            Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(formatShortDate(value.toLocalDate()), maxLines = 1)
        }
        OutlinedButton(onClick = { pickTime = true }, Modifier.weight(1f)) {
            Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(formatTime(value.toLocalTime()), maxLines = 1)
        }
    }
    if (pickDate) DatePickerModal(value.toLocalDate(), { pickDate = false }) { onChange(value.with(it)) }
    if (pickTime) TimePickerModal(value.toLocalTime(), { pickTime = false }) { onChange(value.with(it)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun <T> DropdownField(label: String, options: List<T>, selected: T?, display: @Composable (T) -> String, onSelect: (T) -> Unit,
                                  modifier: Modifier = Modifier, isError: Boolean = false, supporting: String? = null) {
    var expanded by remember { mutableStateOf(false) }
    val labels = options.map { display(it) }
    ExposedDropdownMenuBox(expanded, { expanded = it }, modifier) {
        OutlinedTextField(options.indexOf(selected).takeIf { it >= 0 }?.let { labels[it] } ?: "", {}, readOnly = true, label = { Text(label) }, singleLine = true, isError = isError,
            supportingText = supporting?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
        ExposedDropdownMenu(expanded, { expanded = false }) {
            options.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(labels[i]) }, onClick = { onSelect(o); expanded = false }) }
        }
    }
}

@Composable fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, suffix: String? = null,
                            isError: Boolean = false, supporting: String? = null, decimal: Boolean = true) {
    OutlinedTextField(value, { v -> onChange(v.replace(',', '.')) }, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, isError = isError,
        suffix = suffix?.let { { Text(it) } }, supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number))
}

@Composable fun SectionCard(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
            content()
        }
    }
}

@Composable fun EmptyState(icon: ImageVector, title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(icon, null, Modifier.padding(20.dp).size(40.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action?.invoke()
    }
}

@Composable fun StatusPill(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color, icon: ImageVector? = null) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}
