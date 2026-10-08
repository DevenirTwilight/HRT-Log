package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.TherapyStandard
import net.plainnotes.app.timeline.TimelineEdits
import java.time.LocalDate

/** §40: the editor submits one atomic change; restoring is available only from Settings. */
class TimelineActions(val edit:(List<String>,List<TimelineEditRow>)->Unit={_,_->},val delete:(List<String>,List<TimelineEditRow>)->Unit={_,_->})

data class PeriodEditRequest(val title:Int,val medications:List<Long>,val medicationId:Long?,val range:TimelineEdits.Range,val standard:TherapyStandard?,
                             val target:TimelineEdits.Range?,val exactTarget:Pair<java.time.Instant,java.time.Instant?>?=null,
                             val overlapChanges:(Long,TimelineEdits.Range,TimelineEdits.Range?)->List<TimelineEdits.OverlapChange> = {_,_,_->emptyList()},
                             val zone:java.time.ZoneId=java.time.ZoneId.systemDefault())

/** The identity a user edit stores and the standard it starts from for a medicine (its compound, ester, route and unit). */
fun medicationStandard(m:MedicationEntity,profile:ProfileEntity?,interval:Int,doses:List<Double>)=
    TherapyStandard(m.molecule,profile?.ester,m.route,m.unit,when(m.route){"GEL"->profile?.gel_product_id?.let{"gel:$it"};"PATCH"->profile?.patch_release_ug_day?.let{"patch:$it"};else->null},"EVERY_N_DAYS",interval,0,doses)

/** The same form edits every source. Overlap changes require the explicit “automatically shorten” decision. */
@Composable fun PeriodEditDialog(request:PeriodEditRequest,state:net.plainnotes.app.NotesState,conflicts:(Long,TimelineEdits.Range,TimelineEdits.Range?)->Boolean,
                                 onSave:(Long,TherapyStandard,TimelineEdits.Range)->Unit,onDismiss:()->Unit) {
    val zone=request.zone
    val simple=LocalSimpleMode.current
    var med by remember{mutableStateOf(request.medicationId ?: request.medications.firstOrNull())}
    var from by remember{mutableStateOf(request.range.from)}
    var ongoing by remember{mutableStateOf(request.range.until==null)}
    var lastDay by remember{mutableStateOf(request.range.until?.minusDays(1) ?: LocalDate.now())}
    var interval by remember{mutableStateOf(request.standard?.takeIf{it.kind=="EVERY_N_DAYS"}?.interval?.toString() ?: "1")}
    var doses by remember{mutableStateOf((request.standard?.doses ?: listOf(state.medications.firstOrNull{it.id==med}?.dose_per_intake ?: 1.0)).map{inputNumber(it)})}
    var pick by remember{mutableIntStateOf(0)}
    var preview by remember{mutableStateOf(false)}
    var canceledOverlap by remember{mutableStateOf(false)}
    val intervalV=interval.toIntOrNull()?.takeIf{it in 1..365}
    val doseV=doses.map{it.toDoubleOrNull()?.takeIf{d->d.isFinite() && d>0}}
    val range=TimelineEdits.Range(from,if(ongoing)null else lastDay.plusDays(1))
    val medication=state.medications.firstOrNull{it.id==med}
    val valid=medication!=null && intervalV!=null && doseV.isNotEmpty() && doseV.all{it!=null} && (ongoing || lastDay>=from)
    fun label(id:Long)=if(simple)"#"+(state.medications.indexOfFirst{it.id==id}+1).coerceAtLeast(1) else state.medications.firstOrNull{it.id==id}?.name ?: "#$id"
    fun save(){onSave(med!!,request.standard?.takeIf{med==request.medicationId}?.copy(kind="EVERY_N_DAYS",interval=intervalV!!,weeklyCount=0,doses=doseV.filterNotNull())
        ?: medicationStandard(medication!!,state.profiles[med!!],intervalV!!,doseV.filterNotNull()),range);onDismiss()}
    AlertDialog(onDismissRequest=onDismiss,modifier=Modifier.testTag("period-edit-dialog"),title={Text(stringResource(request.title))},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(request.medications.size>1)DropdownField(stringResource(R.string.period_medication),request.medications,med,{label(it)},{med=it})
            else med?.let{Text(label(it),style=MaterialTheme.typography.titleSmall)}
            Text(stringResource(R.string.period_from),style=MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick={pick=1},modifier=Modifier.fillMaxWidth()){Text(formatDate(from))}
            Row(Modifier.fillMaxWidth().toggleable(ongoing,role=Role.Checkbox){ongoing=it},verticalAlignment=Alignment.CenterVertically) {
                Checkbox(ongoing,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.period_ongoing))
            }
            if(!ongoing) {
                Text(stringResource(R.string.period_until),style=MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick={pick=2},modifier=Modifier.fillMaxWidth()){Text(formatDate(lastDay))}
            }
            NumberField(interval,{interval=it},stringResource(R.string.period_interval_days),decimal=false,isError=intervalV==null)
            DropdownField(stringResource(R.string.period_times_per_day),(1..6).toList(),doses.size,{it.toString()},{n->doses=List(n){i->doses.getOrNull(i) ?: doses.lastOrNull() ?: ""}})
            if(!simple)doses.forEachIndexed{i,v->NumberField(v,{doses=doses.toMutableList().also{l->l[i]=it}},stringResource(R.string.period_dose_n,i+1),
                suffix=medication?.unit?.let{unitLabel(it)},isError=doseV[i]==null)}
            if(canceledOverlap)Text(stringResource(R.string.period_overlap_cancelled),style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={Button(enabled=valid,onClick={if(conflicts(med!!,range,request.target))preview=true else save()},modifier=Modifier.testTag("period-edit-save")){Text(stringResource(R.string.save))}},
        dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.cancel))}})
    if(preview)AlertDialog(onDismissRequest={preview=false},modifier=Modifier.testTag("period-overlap-preview"),title={Text(stringResource(R.string.period_overlap_title))},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            @Composable fun day(at:java.time.Instant)=formatDate(at.atZone(zone).toLocalDate())
            @Composable fun end(at:java.time.Instant?)=if(at==null)stringResource(R.string.period_ongoing) else day(at.minusNanos(1))
            request.overlapChanges(med!!,range,request.target).forEach{c->
                Text(day(c.from)+" → "+end(c.until))
                when(c.remaining.size) {
                    0->Text(stringResource(R.string.period_overlap_trash))
                    1->Text(stringResource(R.string.period_overlap_shorten,day(c.remaining[0].first),end(c.remaining[0].second)))
                    else->Text(stringResource(R.string.period_overlap_split,day(c.remaining[0].first),end(c.remaining[0].second),day(c.remaining[1].first),end(c.remaining[1].second)))
                }
            }
        }},confirmButton={Button(onClick={preview=false;save()},modifier=Modifier.testTag("period-overlap-accept")){Text(stringResource(R.string.period_overlap_accept))}},
        dismissButton={TextButton(onClick={preview=false;canceledOverlap=true},modifier=Modifier.testTag("period-overlap-cancel")){Text(stringResource(R.string.period_overlap_cancel))}})
    when(pick) {
        1->DatePickerModal(from,{pick=0}){from=it;pick=0}
        2->DatePickerModal(lastDay,{pick=0}){lastDay=it;pick=0}
    }
}
