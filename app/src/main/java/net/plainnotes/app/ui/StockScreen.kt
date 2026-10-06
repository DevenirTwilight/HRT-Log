package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.plainnotes.app.NotesState
import net.plainnotes.app.R
import net.plainnotes.app.ScheduleSummary
import net.plainnotes.app.data.ContainerEntity
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.domain.RuleKind
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.floor

const val LOW_STOCK_DAYS = net.plainnotes.app.reminder.StockAlerts.LOW_STOCK_DAYS
const val EXPIRY_WARNING_DAYS = net.plainnotes.app.reminder.StockAlerts.EXPIRY_WARNING_DAYS

/** Average planned use per day, from the current schedule (null when unscheduled). */
fun dailyUse(m: MedicationEntity, s: ScheduleSummary?): Double? = s?.let {
    when (it.kind) {
        RuleKind.EVERY_N_DAYS -> it.times.size * m.dose_per_intake / it.interval
        RuleKind.EVERY_N_HOURS -> 24.0 / it.interval * m.dose_per_intake
        RuleKind.WEEKLY -> it.weekdays.size * it.times.size * m.dose_per_intake / (7.0 * it.interval)
    }
}?.takeIf { it > 0 }

class StockSummary(val open: ContainerEntity?, val remaining: Double, val sealedCount: Int, val daysLeft: Int?, val expiresOn: LocalDate?) {
    val tracked get() = open != null || sealedCount > 0 || remaining > 0
    val low get() = tracked && daysLeft != null && daysLeft < LOW_STOCK_DAYS
    fun expiringSoon(today: LocalDate) = expiresOn != null && ChronoUnit.DAYS.between(today, expiresOn) <= EXPIRY_WARNING_DAYS
}
fun stockSummary(m: MedicationEntity, containers: List<ContainerEntity>, s: ScheduleSummary?): StockSummary {
    val mine = containers.filter { it.medication_id == m.id }
    val open = mine.filter { it.state == "IN_USE" }.minByOrNull { it.opened_on ?: "" }
    val remaining = mine.filter { it.state == "IN_USE" }.sumOf { it.capacity - it.used_amount } + mine.filter { it.state == "SEALED" }.sumOf { it.capacity }
    val days = dailyUse(m, s)?.let { floor(remaining / it).toInt() }
    val expires = open?.opened_on?.let { d -> m.expiry_days_after_open?.let { LocalDate.parse(d).plusDays(it.toLong()) } }
    return StockSummary(open, remaining, mine.count { it.state == "SEALED" }, days, expires)
}

@Composable fun StockScreen(state: NotesState, containers: List<ContainerEntity>, records: List<RecordEntity>, onReplace: (MedicationEntity) -> Unit,
                            onAdjust: (ContainerEntity, MedicationEntity) -> Unit, onAdd: (MedicationEntity) -> Unit, contentPadding: PaddingValues) {
    val meds = state.medications.filter { it.active }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp,
        bottom = contentPadding.calculateBottomPadding() + 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (meds.isEmpty()) item { EmptyState(Icons.Outlined.Inventory2, stringResource(R.string.no_medications), stringResource(R.string.stock_empty_body)) }
        items(meds, key = { it.id }) { m ->
            val sum = stockSummary(m, containers, state.schedules[m.id])
            val unallocated = records.filter { it.medication_id == m.id && it.deleted_at_utc == null }.sumOf { it.unallocated_supply_amount ?: 0.0 }
            StockCard(m, sum, unallocated, { onReplace(m) }, { sum.open?.let { onAdjust(it, m) } }, { onAdd(m) })
        }
    }
}

@Composable private fun StockCard(m: MedicationEntity, s: StockSummary, unallocated: Double, onReplace: () -> Unit, onAdjust: () -> Unit, onAdd: () -> Unit) {
    val c = MaterialTheme.colorScheme; val today = LocalDate.now()
    SectionCard(null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (s.low) StatusPill(stringResource(R.string.stock_low), c.errorContainer, c.onErrorContainer, Icons.Outlined.Warning)
        }
        if (s.open == null && s.sealedCount == 0) {
            Text(stringResource(R.string.stock_not_tracked), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            Button(onClick = onAdd) { Text(stringResource(R.string.stock_start)) }
            return@SectionCard
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatDose(s.remaining, m.unit), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(s.daysLeft?.let { stringResource(R.string.stock_days_left, it) } ?: stringResource(R.string.stock_no_schedule), style = MaterialTheme.typography.bodyMedium,
                color = if (s.low) c.error else c.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
        val o = s.open
        if (o != null) {
            val left = o.capacity - o.used_amount
            LinearProgressIndicator(progress = { (left / o.capacity).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), drawStopIndicator = {})
            Text(stringResource(R.string.stock_current, formatDose(left, m.unit), formatDose(o.capacity, m.unit)), style = MaterialTheme.typography.bodySmall)
            o.opened_on?.let { Text(stringResource(R.string.stock_opened, formatShortDate(LocalDate.parse(it))), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant) }
            s.expiresOn?.let { e -> Text(stringResource(if (e.isBefore(today)) R.string.stock_expired else R.string.stock_expires, formatShortDate(e)), style = MaterialTheme.typography.bodySmall,
                color = if (s.expiringSoon(today)) c.error else c.onSurfaceVariant) }
        } else Text(stringResource(R.string.stock_none_open), style = MaterialTheme.typography.bodySmall, color = c.error)
        if (s.sealedCount > 0) Text(stringResource(R.string.stock_sealed, s.sealedCount), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        if (unallocated > 0) Text(stringResource(R.string.stock_unallocated, formatDose(unallocated, m.unit)), style = MaterialTheme.typography.bodySmall, color = c.tertiary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onReplace) { Text(stringResource(R.string.stock_replace)) }
            if (s.open != null) OutlinedButton(onClick = onAdjust) { Text(stringResource(R.string.stock_adjust)) }
            TextButton(onClick = onAdd) { Text(stringResource(R.string.stock_add)) }
        }
    }
}

@Composable fun AddStockDialog(m: MedicationEntity, onDismiss: () -> Unit, onSave: (Double, Int, Boolean) -> Unit) {
    var capacity by remember { mutableStateOf(inputNumber(m.container_capacity)) }; var count by remember { mutableStateOf("1") }
    var openNow by remember { mutableStateOf(false) }
    val cap = capacity.toDoubleOrNull()?.takeIf { it > 0 }; val n = count.toIntOrNull()?.takeIf { it in 1..50 }
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.Inventory2, null) }, title = { Text(stringResource(R.string.stock_add)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(m.name, style = MaterialTheme.typography.titleSmall)
            NumberField(count, { count = it }, stringResource(R.string.stock_count), decimal = false, isError = n == null)
            NumberField(capacity, { capacity = it }, stringResource(R.string.capacity), suffix = unitLabel(m.unit), isError = cap == null)
            SwitchRow(stringResource(R.string.stock_open_now), openNow) { openNow = it }
        } },
        confirmButton = { Button(enabled = cap != null && n != null, onClick = { onSave(cap!!, n!!, openNow) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable fun AdjustStockDialog(c: ContainerEntity, m: MedicationEntity, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var left by remember { mutableStateOf(inputNumber(c.capacity - c.used_amount)) }
    val v = left.toDoubleOrNull()?.takeIf { it >= 0 && it <= c.capacity }
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.Tune, null) }, title = { Text(stringResource(R.string.stock_adjust)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.stock_adjust_body), style = MaterialTheme.typography.bodyMedium)
            NumberField(left, { left = it }, stringResource(R.string.stock_remaining), suffix = unitLabel(m.unit), isError = v == null,
                supporting = stringResource(R.string.stock_max, formatDose(c.capacity, m.unit)))
        } },
        confirmButton = { Button(enabled = v != null, onClick = { onSave(v!!) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
