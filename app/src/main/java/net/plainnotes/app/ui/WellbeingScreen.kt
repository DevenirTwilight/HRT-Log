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
    "DAY_MOOD" -> R.string.wb_day_mood; "DAY_ENERGY" -> R.string.wb_day_energy; "DAY_SLEEP" -> R.string.wb_day_sleep; "DAY_BODY" -> R.string.wb_day_body;
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
                val ends = when(item.builtin_key) {
                    "DAY_MOOD" -> R.string.wb_low_mood to R.string.wb_high_mood
                    "DAY_ENERGY" -> R.string.wb_low_energy to R.string.wb_high_energy
                    "DAY_SLEEP" -> R.string.wb_low_sleep to R.string.wb_high_sleep
                    "DAY_BODY" -> R.string.wb_low_body to R.string.wb_high_body
                    else -> R.string.wb_unrecorded to R.string.wb_high_sleep
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(when(item.builtin_key){"DAY_MOOD"->Icons.Outlined.WbSunny;"DAY_ENERGY"->Icons.Outlined.BatteryChargingFull;"DAY_SLEEP"->Icons.Outlined.Bedtime;else->Icons.Outlined.PanTool},null,Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp));Text(checkinLabel(item),Modifier.weight(1f))
                }
                FiveLevelInput(v,if(item.builtin_key?.startsWith("DAY_")==true)stringResource(ends.first)else "1",if(item.builtin_key?.startsWith("DAY_")==true)stringResource(ends.second)else "5",checkinLabel(item)){onScore(day,item.id,it)}
            }
            TextButton(onClick = onManage) { Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.wb_manage)) }
        }
        key(date) {
            val saved = notes.firstOrNull { it.date == date }?.text ?: ""
            var text by remember { mutableStateOf(saved) }
            LaunchedEffect(text) { if (text != saved) { delay(700); onNote(day, text) } }
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp), label = { Text(stringResource(R.string.wb_note)) })
        }
        WellbeingStats(items, scores)
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
            Text(checkinLabel(item) + " · " + stringResource(R.string.wb_recorded_days, pts.size), style = MaterialTheme.typography.labelLarge)
            val xs = pts.map { it.first }.toDoubleArray(); val ys = pts.map { it.second }.toDoubleArray()
            val start = if (days == 0) xs.first() - 24 else net.plainnotes.app.conc.ConcentrationCalculator.hours(since.atStartOfDay(zone).toInstant())
            val end = net.plainnotes.app.conc.ConcentrationCalculator.hours(LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant())
            key(days, item.id) { ConcChart(ChartData(xs, ys, points = pts, range = 5.0 to 5.0), start, end, Modifier.fillMaxWidth().height(120.dp)) { it.toInt().toString() } }
        }
    }
}

/** Null means untouched, including when the thumb initially sits at the middle. */
@Composable fun FiveLevelInput(value:Int?,low:String,high:String,label:String,onValue:(Int?)->Unit) {
    var draft by remember(value){mutableFloatStateOf((value?:3).toFloat())}
    Text(if(value==null)stringResource(R.string.wb_unrecorded) else value.toString(),style=MaterialTheme.typography.labelSmall)
    Slider(draft,{draft=it;onValue(it.toInt().coerceIn(1,5))},valueRange=1f..5f,steps=3,
        modifier=Modifier.fillMaxWidth().semantics{contentDescription=label})
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(low,style=MaterialTheme.typography.bodySmall);Text(high,style=MaterialTheme.typography.bodySmall)}
    if(value!=null) TextButton(onClick={onValue(null)}){Text(stringResource(R.string.wb_clear))}
}

@Composable fun ManageCheckinItemsDialog(items:List<CheckinItemEntity>,onDismiss:()->Unit,onSave:(CheckinItemEntity)->Unit,
    effects:List<net.plainnotes.app.data.ReviewEffectEntity> = emptyList(),onEffect:(String,Boolean)->Unit={_,_->},onOrder:(List<Long>)->Unit={}) {
    var newLabel by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.wb_manage))},text={Column(Modifier.verticalScroll(rememberScrollState())){
        listOf(false,true).forEach { legacy ->
            Text(stringResource(if(legacy)R.string.wb_previous else R.string.wb_daily),style=MaterialTheme.typography.titleSmall)
            items.filter{it.legacy==legacy}.sortedBy{it.sort_order}.forEach { item ->
                SwitchRow(checkinLabel(item),item.enabled){onSave(item.copy(enabled=it))}
                Row {
                    val ordered=items.sortedBy{it.sort_order}.map{it.id};val index=ordered.indexOf(item.id)
                    IconButton(enabled=index>0,onClick={val ids=ordered.toMutableList();java.util.Collections.swap(ids,index,index-1);onOrder(ids)}){Icon(Icons.Outlined.ArrowUpward,stringResource(R.string.wb_move_up))}
                    IconButton(enabled=index<ordered.lastIndex,onClick={val ids=ordered.toMutableList();java.util.Collections.swap(ids,index,index+1);onOrder(ids)}){Icon(Icons.Outlined.ArrowDownward,stringResource(R.string.wb_move_down))}
                }
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically){
            OutlinedTextField(newLabel,{newLabel=it},Modifier.weight(1f),label={Text(stringResource(R.string.wb_custom))},singleLine=true)
            IconButton(enabled=newLabel.isNotBlank(),onClick={onSave(CheckinItemEntity(custom_label=newLabel.trim(),enabled=true,sort_order=0));newLabel=""}){Icon(Icons.Outlined.Add,stringResource(R.string.add))}
        }
        HorizontalDivider();Text(stringResource(R.string.wb_effects),style=MaterialTheme.typography.titleSmall)
        REVIEW_EFFECTS.forEach{effect->SwitchRow(stringResource(effect.label),effects.firstOrNull{it.effect_id==effect.id}?.enabled!=false){onEffect(effect.id,it)}}
    }},confirmButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.ok))}})
}
