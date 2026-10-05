package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.plainnotes.app.R
import net.plainnotes.app.data.CheckinItemEntity
import net.plainnotes.app.data.CheckinScoreEntity
import net.plainnotes.app.data.DayNoteEntity
import java.time.LocalDate
import java.time.ZoneId

@Composable fun checkinLabel(item: CheckinItemEntity) = item.custom_label ?: stringResource(when (item.builtin_key) {
    "OVERALL" -> R.string.wb_overall; "MOOD" -> R.string.wb_mood; "EMO_STABILITY" -> R.string.wb_emo_stability; "ENERGY" -> R.string.wb_energy
    "AGGRESSIVENESS" -> R.string.wb_aggressiveness; "LIBIDO" -> R.string.wb_libido; "PAIN" -> R.string.wb_pain; "PERIOD_LIKE" -> R.string.wb_period_like
    "APPETITE" -> R.string.wb_appetite; "SLEEP_QUALITY" -> R.string.wb_sleep; "SKIN_QUALITY" -> R.string.wb_skin; else -> R.string.choice_other
})

@Composable fun WellbeingScreen(items: List<CheckinItemEntity>, scores: List<CheckinScoreEntity>, notes: List<DayNoteEntity>, onScore: (LocalDate, Long, Int?) -> Unit,
                                onNote: (LocalDate, String) -> Unit, onManage: () -> Unit, contentPadding: PaddingValues) {
    val today = LocalDate.now()
    var date by rememberSaveable { mutableStateOf(today.toString()) }
    val day = LocalDate.parse(date)
    val enabled = items.filter { it.enabled }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { date = day.minusDays(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.previous_day)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (day == today) stringResource(R.string.today) else formatShortDate(day), style = MaterialTheme.typography.titleMedium)
                if (day == today) Text(formatShortDate(day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { date = day.plusDays(1).toString() }, enabled = day < today) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.next_day)) }
        }
        SectionCard(null) {
            enabled.forEach { item ->
                val v = scores.firstOrNull { it.date == date && it.item_id == item.id }?.value
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(checkinLabel(item), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    (1..5).forEach { n ->
                        IconButton(onClick = { onScore(day, item.id, if (v == n) null else n) }, Modifier.size(36.dp)) {
                            Icon(if (v != null && n <= v) Icons.Rounded.Star else Icons.Rounded.StarBorder, stringResource(R.string.stars, n),
                                tint = if (v != null && n <= v) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
            TextButton(onClick = onManage) { Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.wb_manage)) }
        }
        key(date) {
            val saved = notes.firstOrNull { it.date == date }?.text ?: ""
            var text by remember { mutableStateOf(saved) }
            LaunchedEffect(text) { if (text != saved) { delay(700); onNote(day, text) } }
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp), label = { Text(stringResource(R.string.wb_note)) })
        }
        WellbeingStats(enabled, scores)
    }
}

@Composable private fun WellbeingStats(items: List<CheckinItemEntity>, scores: List<CheckinScoreEntity>) {
    var days by rememberSaveable { mutableIntStateOf(30) }
    val zone = ZoneId.systemDefault()
    val since = if (days == 0) LocalDate.MIN else LocalDate.now().minusDays(days.toLong() - 1)
    SectionCard(stringResource(R.string.wb_stats)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(7, 30, 90, 0).forEachIndexed { i, d -> SegmentedButton(days == d, { days = d }, SegmentedButtonDefaults.itemShape(i, 4)) { Text(if (d == 0) stringResource(R.string.range_all) else stringResource(R.string.days_short, d)) } }
        }
        val any = items.mapNotNull { item ->
            val pts = scores.filter { it.item_id == item.id && LocalDate.parse(it.date) >= since }.sortedBy { it.date }
                .map { net.plainnotes.app.conc.ConcentrationCalculator.hours(LocalDate.parse(it.date).atTime(12, 0).atZone(zone).toInstant()) to it.value.toDouble() }
            if (pts.isEmpty()) null else item to pts
        }
        if (any.isEmpty()) Text(stringResource(R.string.wb_no_data), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        any.forEach { (item, pts) ->
            Text(checkinLabel(item) + " · " + stringResource(R.string.wb_average, displayNumber(pts.map { it.second }.average(), 1)), style = MaterialTheme.typography.labelLarge)
            val xs = pts.map { it.first }.toDoubleArray(); val ys = pts.map { it.second }.toDoubleArray()
            val start = if (days == 0) xs.first() - 24 else net.plainnotes.app.conc.ConcentrationCalculator.hours(since.atStartOfDay(zone).toInstant())
            val end = net.plainnotes.app.conc.ConcentrationCalculator.hours(LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant())
            key(days, item.id) { ConcChart(ChartData(xs, ys, points = pts, range = 5.0 to 5.0), start, end, Modifier.fillMaxWidth().height(120.dp)) { it.toInt().toString() } }
        }
    }
}

@Composable fun ManageCheckinItemsDialog(items: List<CheckinItemEntity>, onDismiss: () -> Unit, onSave: (CheckinItemEntity) -> Unit) {
    var newLabel by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.wb_manage)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEach { item -> SwitchRow(checkinLabel(item), item.enabled) { onSave(item.copy(enabled = it)) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newLabel, { newLabel = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.wb_custom)) }, singleLine = true)
                IconButton(enabled = newLabel.isNotBlank(), onClick = { onSave(CheckinItemEntity(custom_label = newLabel.trim(), enabled = true, sort_order = 0)); newLabel = "" }) { Icon(Icons.Outlined.Add, stringResource(R.string.add)) }
            }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } })
}
