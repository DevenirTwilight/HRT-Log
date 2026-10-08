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

/** Callbacks for timeline edits (REQUIREMENTS §37b): append one edit, or undo one by its group key. */
class TimelineActions(val edit:(List<String>,List<TimelineEditRow>)->Unit={_,_->},val undo:(String)->Unit={})

/** What the period editor opens with. [merge] holds the standards of the two periods being merged. */
data class PeriodEditRequest(val title:Int,val medications:List<Long>,val medicationId:Long?,val range:TimelineEdits.Range,val standard:TherapyStandard?,
                             val target:TimelineEdits.Range?,val also:List<TimelineEdits.Range> = emptyList(),val merge:Pair<TherapyStandard?,TherapyStandard?>?=null)

/** The identity a user edit stores and the standard it starts from for a medicine (its compound, ester, route and unit). */
fun medicationStandard(m:MedicationEntity,profile:ProfileEntity?,interval:Int,doses:List<Double>)=
    TherapyStandard(m.molecule,profile?.ester,m.route,m.unit,null,"EVERY_N_DAYS",interval,0,doses)

/**
 * Create, edit or merge a period. A merge of two different standards cannot be saved until the user picks the previous
 * one, the next one, or fills their own (§37b item 4). Same-medicine overlaps outside the target are refused (rule c).
 */
@Composable fun PeriodEditDialog(request:PeriodEditRequest,state:net.plainnotes.app.NotesState,conflicts:(Long,TimelineEdits.Range,TimelineEdits.Range?)->Boolean,
                                 onSave:(Long,TherapyStandard,TimelineEdits.Range)->Unit,onDismiss:()->Unit) {
    val simple=LocalSimpleMode.current
    var med by remember{mutableStateOf(request.medicationId ?: request.medications.firstOrNull())}
    var from by remember{mutableStateOf(request.range.from)}
    var ongoing by remember{mutableStateOf(request.range.until==null)}
    var lastDay by remember{mutableStateOf(request.range.until?.minusDays(1) ?: LocalDate.now())}
    val differentMerge=request.merge?.let{(a,b)->a!=null && b!=null && a!=b}==true
    var choice by remember{mutableStateOf(if(differentMerge)null else "keep")}
    fun preset(s:TherapyStandard?)=s?.takeIf{it.kind=="EVERY_N_DAYS"}
    var interval by remember{mutableStateOf(preset(request.standard)?.interval?.toString() ?: "1")}
    var doses by remember{mutableStateOf((preset(request.standard)?.doses ?: request.standard?.doses?.take(1) ?: listOf(state.medications.firstOrNull{it.id==med}?.dose_per_intake ?: 1.0)).map{inputNumber(it)})}
    fun use(s:TherapyStandard?){s?.let{interval=(preset(it)?.interval ?: 1).toString();doses=(preset(it)?.doses ?: it.doses.take(1)).map{d->inputNumber(d)}}}
    var pick by remember{mutableIntStateOf(0)}
    val intervalV=interval.toIntOrNull()?.takeIf{it in 1..365}
    val doseV=doses.map{it.toDoubleOrNull()?.takeIf{d->d.isFinite() && d>0}}
    val range=TimelineEdits.Range(from,if(ongoing)null else lastDay.plusDays(1))
    val overlap=med?.let{conflicts(it,range,request.target)}==true
    val medication=state.medications.firstOrNull{it.id==med}
    val valid=med!=null && medication!=null && intervalV!=null && doseV.isNotEmpty() && doseV.all{it!=null} && (ongoing || lastDay>=from) && !overlap && choice!=null
    fun label(id:Long)=if(simple)"#"+(request.medications.indexOf(id)+1).coerceAtLeast(1) else state.medications.firstOrNull{it.id==id}?.name ?: "#$id"
    AlertDialog(onDismissRequest=onDismiss,modifier=Modifier.testTag("period-edit-dialog"),title={Text(stringResource(request.title))},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(request.medications.size>1)DropdownField(stringResource(R.string.period_medication),request.medications,med,{label(it)},{med=it})
            else if(!simple)medication?.let{Text(it.name,style=MaterialTheme.typography.titleSmall)}
            if(differentMerge) {
                Text(stringResource(R.string.period_merge_choose),style=MaterialTheme.typography.bodyMedium)
                listOf("previous" to R.string.period_merge_use_previous,"next" to R.string.period_merge_use_next,"custom" to R.string.period_merge_custom).forEach{(key,text)->
                    Row(Modifier.fillMaxWidth().selectable(choice==key,role=Role.RadioButton){choice=key
                        when(key){"previous"->use(request.merge!!.first);"next"->use(request.merge!!.second)}}.testTag("merge-choice:$key"),verticalAlignment=Alignment.CenterVertically) {
                        RadioButton(choice==key,null);Spacer(Modifier.width(8.dp));Text(stringResource(text))
                    }
                }
            }
            Text(stringResource(R.string.period_from),style=MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick={pick=1},modifier=Modifier.fillMaxWidth()){Text(formatDate(from))}
            Row(Modifier.fillMaxWidth().toggleable(ongoing,role=Role.Checkbox){ongoing=it},verticalAlignment=Alignment.CenterVertically) {
                Checkbox(ongoing,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.period_ongoing))
            }
            if(!ongoing) {
                Text(stringResource(R.string.period_until),style=MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick={pick=2},modifier=Modifier.fillMaxWidth()){Text(formatDate(lastDay))}
            }
            if(choice!=null) {
                NumberField(interval,{interval=it;if(differentMerge)choice="custom"},stringResource(R.string.period_interval_days),decimal=false,isError=intervalV==null)
                DropdownField(stringResource(R.string.period_times_per_day),(1..6).toList(),doses.size,{it.toString()},{n->
                    doses=List(n){i->doses.getOrNull(i) ?: doses.lastOrNull() ?: ""};if(differentMerge)choice="custom"})
                doses.forEachIndexed{i,v->NumberField(v,{doses=doses.toMutableList().also{l->l[i]=it};if(differentMerge)choice="custom"},stringResource(R.string.period_dose_n,i+1),
                    suffix=medication?.unit?.let{unitLabel(it)},isError=doseV[i]==null)}
            }
            if(overlap)Text(stringResource(R.string.period_overlap_error),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("period-overlap"))
        }},
        confirmButton={Button(enabled=valid,onClick={
            onSave(med!!,medicationStandard(medication!!,state.profiles[med!!],intervalV!!,doseV.filterNotNull()),range);onDismiss()
        },modifier=Modifier.testTag("period-edit-save")){Text(stringResource(R.string.save))}},
        dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.cancel))}})
    when(pick) {
        1->DatePickerModal(from,{pick=0}){from=it;pick=0}
        2->DatePickerModal(lastDay,{pick=0}){lastDay=it;pick=0}
    }
}

/** Start and end of a stop (§37b: stops can be moved). */
@Composable fun StopEditDialog(initial:TimelineEdits.Range,onSave:(TimelineEdits.Range)->Unit,onDismiss:()->Unit) {
    var from by remember{mutableStateOf(initial.from)}
    var ongoing by remember{mutableStateOf(initial.until==null)}
    var lastDay by remember{mutableStateOf(initial.until?.minusDays(1) ?: initial.from)}
    var pick by remember{mutableIntStateOf(0)}
    AlertDialog(onDismissRequest=onDismiss,modifier=Modifier.testTag("stop-edit-dialog"),title={Text(stringResource(R.string.period_menu_edit_stop))},
        text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.period_stop_from),style=MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick={pick=1},modifier=Modifier.fillMaxWidth()){Text(formatDate(from))}
            Row(Modifier.fillMaxWidth().toggleable(ongoing,role=Role.Checkbox){ongoing=it},verticalAlignment=Alignment.CenterVertically) {
                Checkbox(ongoing,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.period_ongoing))
            }
            if(!ongoing) {
                Text(stringResource(R.string.period_stop_last),style=MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick={pick=2},modifier=Modifier.fillMaxWidth()){Text(formatDate(lastDay))}
            }
        }},
        confirmButton={Button(enabled=ongoing || lastDay>=from,onClick={onSave(TimelineEdits.Range(from,if(ongoing)null else lastDay.plusDays(1)));onDismiss()}){Text(stringResource(R.string.save))}},
        dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.cancel))}})
    when(pick) {
        1->DatePickerModal(from,{pick=0}){from=it;pick=0}
        2->DatePickerModal(lastDay,{pick=0}){lastDay=it;pick=0}
    }
}
