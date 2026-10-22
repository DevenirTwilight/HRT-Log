package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.plainnotes.app.R
import net.plainnotes.app.data.CheckinItemEntity
import net.plainnotes.app.data.CheckinScoreEntity
import net.plainnotes.app.data.DayNoteEntity
import java.time.LocalDate

@Composable fun checkinLabel(item: CheckinItemEntity) = item.custom_label ?: stringResource(when (item.builtin_key) {
    "DAY_MOOD" -> R.string.wb_day_mood; "DAY_ENERGY" -> R.string.wb_day_energy; "DAY_SLEEP" -> R.string.wb_day_sleep; "DAY_BODY" -> R.string.wb_day_body;
    "OVERALL" -> R.string.wb_overall; "MOOD" -> R.string.wb_mood; "EMO_STABILITY" -> R.string.wb_emo_stability; "ENERGY" -> R.string.wb_energy
    "AGGRESSIVENESS" -> R.string.wb_aggressiveness; "LIBIDO" -> R.string.wb_libido; "PAIN" -> R.string.wb_pain; "PERIOD_LIKE" -> R.string.wb_period_like
    "APPETITE" -> R.string.wb_appetite; "SLEEP_QUALITY" -> R.string.wb_sleep; "SKIN_QUALITY" -> R.string.wb_skin; else -> R.string.choice_other
})

/**
 * UI-08 (§138) daily tab: the day card, the items as a grouped list of five-pill inputs, the day's note and recent entries.
 * [date] / [onDate] come from the hub so the symptoms tab shares the day; without them the screen keeps its own.
 */
@Composable fun WellbeingScreen(items: List<CheckinItemEntity>, scores: List<CheckinScoreEntity>, notes: List<DayNoteEntity>, onScore: (LocalDate, Long, Int?) -> Unit,
                                onNote: (LocalDate, String) -> Unit, contentPadding: PaddingValues, date: String? = null, onDate: ((String) -> Unit)? = null) {
    var own by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val current = date ?: own
    fun setDate(d: LocalDate) { if (d <= LocalDate.now()) { own = d.toString(); onDate?.invoke(d.toString()) } }
    val day = LocalDate.parse(current)
    var pick by remember { mutableStateOf(false) }
    val enabled = items.filter { it.enabled }
    val valueOf = { item: CheckinItemEntity -> scores.firstOrNull { it.date == current && it.item_id == item.id }?.value }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WellbeingDayCard(day, ::setDate, { pick = true }, enabled.count { valueOf(it) != null }, enabled.size)
        Column(verticalArrangement = Arrangement.spacedBy(GroupedGap)) {
            enabled.forEachIndexed { i, item -> CheckinItemRow(item, valueOf(item), i, enabled.size) { onScore(day, item.id, it) } }
        }
        key(current) {
            val saved = notes.firstOrNull { it.date == current }?.text ?: ""
            var text by remember { mutableStateOf(saved) }
            LaunchedEffect(text) { if (text != saved) { delay(700); onNote(day, text) } }
            SectionCard(stringResource(R.string.wb_note_title), shape = Corners.xl) {
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("wb-note"),
                    placeholder = { Text(stringResource(R.string.wb_note_placeholder)) })
            }
        }
        WellbeingStats(items, scores)
    }
    if (pick) DatePickerModal(day, { pick = false }) { setDate(it); pick = false }
}

/** F: "Recent entries": the range pills and one thin line per item with values; gaps stay empty, nothing is averaged or judged. */
@Composable private fun WellbeingStats(items: List<CheckinItemEntity>, scores: List<CheckinScoreEntity>) {
    var days by rememberSaveable { mutableIntStateOf(30) }
    val today = LocalDate.now()
    val since = if (days == 0) LocalDate.MIN else today.minusDays(days.toLong() - 1)
    SectionCard(null, shape = Corners.xl, modifier = Modifier.testTag("wb-recent")) {
        Text(stringResource(R.string.wb_recent_title), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() })
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(7, 30, 90, 0).forEach { d ->
                FilterChip(days == d, { days = d }, { Text(if (d == 0) stringResource(R.string.range_all) else stringResource(R.string.days_short, d)) },
                    Modifier.heightIn(min = 48.dp).testTag("wb-range:$d"), shape = Corners.full)
            }
        }
        val any = items.mapNotNull { item ->
            val pts = scores.filter { it.item_id == item.id && LocalDate.parse(it.date).let { d -> d >= since && d <= today } }.associate { LocalDate.parse(it.date) to it.value }
            if (pts.isEmpty()) null else item to pts
        }
        if (any.isEmpty()) Text(stringResource(R.string.wb_no_data), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        any.forEach { (item, pts) ->
            Text(checkinLabel(item) + " · " + stringResource(R.string.wb_recorded_days, pts.size), style = MaterialTheme.typography.labelLarge)
            WellbeingTrendLine(checkinLabel(item), pts, if (days == 0) pts.keys.min() else since, today, days, Modifier.testTag("wb-trend:${item.id}"))
        }
        Text(stringResource(R.string.wb_recent_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun ManageCheckinItemsDialog(items:List<CheckinItemEntity>,onDismiss:()->Unit,onSave:(CheckinItemEntity)->Unit,
    effects:List<net.plainnotes.app.data.ReviewEffectEntity> = emptyList(),onEffect:(String,Boolean)->Unit={_,_->},onOrder:(List<Long>)->Unit={}) {
    var newLabel by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.wb_manage))},text={Column(Modifier.verticalScroll(rememberScrollState())){
        listOf(false,true).forEach { legacy ->
            Text(stringResource(if(legacy)R.string.wb_previous else R.string.wb_daily),style=MaterialTheme.typography.titleSmall)
            items.filter{it.legacy==legacy}.sortedBy{it.sort_order}.forEach { item ->
                Box(Modifier.heightIn(min=56.dp),contentAlignment=Alignment.CenterStart){SwitchRow(checkinLabel(item),item.enabled){onSave(item.copy(enabled=it))}}
                Row {
                    val ordered=items.sortedBy{it.sort_order}.map{it.id};val index=ordered.indexOf(item.id)
                    IconButton(enabled=index>0,onClick={val ids=ordered.toMutableList();java.util.Collections.swap(ids,index,index-1);onOrder(ids)}){Icon(Icons.Outlined.ArrowUpward,stringResource(R.string.wb_move_up))}
                    IconButton(enabled=index<ordered.lastIndex,onClick={val ids=ordered.toMutableList();java.util.Collections.swap(ids,index,index+1);onOrder(ids)}){Icon(Icons.Outlined.ArrowDownward,stringResource(R.string.wb_move_down))}
                }
            }
        }
        Text(stringResource(R.string.wb_custom_group),style=MaterialTheme.typography.titleSmall)
        Row(verticalAlignment=Alignment.CenterVertically){
            OutlinedTextField(newLabel,{newLabel=it},Modifier.weight(1f),label={Text(stringResource(R.string.wb_custom))},singleLine=true)
            IconButton(enabled=newLabel.isNotBlank(),onClick={onSave(CheckinItemEntity(custom_label=newLabel.trim(),enabled=true,sort_order=0));newLabel=""}){Icon(Icons.Outlined.Add,stringResource(R.string.add))}
        }
        HorizontalDivider();Text(stringResource(R.string.wb_effects),style=MaterialTheme.typography.titleSmall)
        REVIEW_EFFECTS.forEach{effect->Box(Modifier.heightIn(min=56.dp),contentAlignment=Alignment.CenterStart){SwitchRow(stringResource(effect.label),effects.firstOrNull{it.effect_id==effect.id}?.enabled!=false){onEffect(effect.id,it)}}}
    }},confirmButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.ok))}})
}
