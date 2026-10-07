package net.plainnotes.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.NotesState
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.ScheduleSummary
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import net.plainnotes.app.symptoms.SymptomCatalog
import net.plainnotes.app.timeline.*
import java.time.*

@Composable fun LongitudinalScreen(state:NotesState,extra:NotesViewModel.ExtraState,onSave:(MilestoneEntity)->Unit,
    onDelete:(Long)->Unit,onOpen:(EventKind)->Unit,contentPadding:PaddingValues,onAppointment:(Long)->Unit={}) {
    var stages by rememberSaveable{mutableStateOf(false)}
    var days by rememberSaveable{mutableIntStateOf(90)}
    var kind by rememberSaveable{mutableStateOf<EventKind?>(null)}
    var medication by rememberSaveable{mutableStateOf<Long?>(null)}
    var epochKey by rememberSaveable{mutableStateOf<String?>(null)}
    var planned by rememberSaveable{mutableStateOf(false)}
    var filters by rememberSaveable{mutableStateOf(false)}
    var edit by remember{mutableStateOf<MilestoneEntity?>(null)}
    var remove by remember{mutableStateOf<MilestoneEntity?>(null)}
    val zone=ZoneId.systemDefault();val simple=LocalSimpleMode.current
    val record=remember(extra,state.appointments,zone,planned,medication){LongitudinalProjection.build(extra,state.appointments,zone,planned,medication)}
    val epochs=record.epochs;val versions=extra.regimens.associateBy{it.id}
    val selected=epochs.firstOrNull{it.key==epochKey}
    val today=LocalDate.now(zone)
    val events=remember(record,days,kind,selected,today){record.events.filter{e->
        (days==0 || e.date?.let{!it.isBefore(today.minusDays(days.toLong()-1)) && (planned || !it.isAfter(today))}==true) &&
            (kind==null || e.kind==kind) && (selected==null || selected.key in e.epochs.keys)
    }}
    fun select(e:TreatmentEpoch){epochKey=e.key;stages=false;days=0}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=16.dp,end=16.dp,
        top=contentPadding.calculateTopPadding()+8.dp,bottom=contentPadding.calculateBottomPadding()+32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(!stages,{stages=false},label={Text(stringResource(R.string.timeline))})
                FilterChip(stages,{stages=true},label={Text(stringResource(R.string.epochs))})
            }
            Text(stringResource(R.string.timeline_intro),style=MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.timeline_zone,zone.id),style=MaterialTheme.typography.labelSmall)
        }
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={filters=!filters}){Icon(Icons.Outlined.FilterList,null);Text(stringResource(R.string.timeline_filters))}
                FilledTonalButton(onClick={edit=MilestoneEntity(date=today.toString())}){Text(stringResource(R.string.milestone_add))}
            }
            if(filters)Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf(30,90,365,0).forEach{n->FilterChip(days==n,{days=n},label={Text(if(n==0)stringResource(R.string.timeline_all_time) else stringResource(R.string.timeline_days,n))})}
                }
                DropdownField(stringResource(R.string.timeline_type),listOf<EventKind?>(null)+EventKind.entries,kind,
                    {it?.let{eventKindLabel(it)} ?: stringResource(R.string.timeline_all_types)},{kind=it})
                if(!simple)DropdownField(stringResource(R.string.medications),listOf<Long?>(null)+state.medications.map{it.id},medication,
                    {id->id?.let{state.medications.firstOrNull{m->m.id==id}?.name}.orEmpty().ifEmpty{stringResource(R.string.all_medications)}},{medication=it})
                DropdownField(stringResource(R.string.epochs),listOf<String?>(null)+epochs.map{it.key},selected?.key,
                    {key->key?.let{epochLabel(epochs.indexOfFirst{it.key==key})} ?: stringResource(R.string.timeline_all_epochs)},{epochKey=it})
                if(!stages)SwitchRow(stringResource(R.string.timeline_show_planned),planned){planned=it}
                if(medication!=null)Text(stringResource(R.string.timeline_global_filter),style=MaterialTheme.typography.bodySmall)
            }
            if(selected!=null)Row { AssistChip(onClick={epochKey=null},label={Text(epochLabel(epochs.indexOf(selected)))},trailingIcon={Icon(Icons.Outlined.Close,stringResource(R.string.timeline_all_epochs))}) }
        }
        if(stages) {
            val shown=epochs.filter{e->(medication==null || e.regimenIds.any{versions[it]?.medication_id==medication}) &&
                (days==0 || e.until==null || e.until?.let{!it.atZone(zone).toLocalDate().isBefore(today.minusDays(days.toLong()-1))}==true)}.asReversed()
            if(shown.isEmpty())item{Text(stringResource(R.string.epochs_empty))}
            items(shown,key={it.key}){e->EpochCard(e,epochs.indexOf(e),versions){select(e)}}
        } else {
            if(events.isEmpty())item{Text(stringResource(R.string.timeline_empty))}
            items(events,key={it.key}){event->
                EventCard(event,epochs,versions,extra,{kind->(event.source as? EventSource.Appointment)?.let{onAppointment(it.value.id)} ?: onOpen(kind)},{edit=it},{remove=it},::select)
            }
        }
    }
    edit?.let{value->MilestoneDialog(value,{edit=null}){onSave(it);edit=null}}
    remove?.let{value->AlertDialog(onDismissRequest={remove=null},text={Text(stringResource(R.string.milestone_delete_confirm))},
        confirmButton={Button(onClick={onDelete(value.id);remove=null}){Text(stringResource(R.string.remove))}},
        dismissButton={TextButton(onClick={remove=null}){Text(stringResource(R.string.cancel))}})}
}

@Composable private fun timelineDateTime(at:Instant)=formatDate(at.atZone(ZoneId.systemDefault()).toLocalDate())+" · "+formatTime(at)
@Composable private fun epochLabel(index:Int)=stringResource(R.string.epoch_number,index+1)
@Composable private fun EpochCard(epoch:TreatmentEpoch,index:Int,versions:Map<Long,RegimenVersionEntity>,onSelect:()->Unit) {
    SectionCard(epochLabel(index)) {
        Text(timelineDateTime(epoch.from)+" → "+
            (epoch.until?.let{timelineDateTime(it)} ?: stringResource(R.string.epoch_ongoing)))
        if(epoch.reconstructed)Text(stringResource(R.string.epoch_reconstructed),style=MaterialTheme.typography.bodySmall)
        if(epoch.regimenIds.isEmpty())Text(stringResource(R.string.epoch_no_plan))
        if(!LocalSimpleMode.current)epoch.regimenIds.sorted().forEach{id->versions[id]?.let{FrozenRegimen(it)}}
        TextButton(onClick=onSelect){Text(stringResource(R.string.epoch_view))}
    }
}
@Composable private fun FrozenRegimen(version:RegimenVersionEntity) {
    val d=remember(version.definition_json){RegimenDefinition.read(version.definition_json)}
    val m=remember(d){d.snapshot(version.medication_id)}
    val schedule=ScheduleSummary(RuleKind.valueOf(d.kind),d.interval,DayOfWeek.entries.filter{d.weekdays and (1 shl(it.value-1))!=0}.toSet(),d.times.map{LocalTime.parse(it.first)},d.dose,d.times.map{it.second})
    Text(m?.name ?: stringResource(R.string.timeline_medication_unknown),style=MaterialTheme.typography.titleSmall)
    Text(listOfNotNull(m?.molecule?.let{choiceLabel(it)},m?.profile?.ester?.takeIf{it!=m.molecule}?.let{choiceLabel(it)},m?.route?.let{choiceLabel(it)},formatDose(d.dose,m?.unit)).joinToString(" · "))
    Text(scheduleText(schedule),style=MaterialTheme.typography.bodySmall)
    if(d.times.any{it.second!=null})Text(d.times.map{formatTime(LocalTime.parse(it.first))+" · "+formatDose(it.second ?: d.dose,m?.unit)}.joinToString(" / "))
    m?.profile?.gel_product_id?.let{Text(gelProductLabel(it),style=MaterialTheme.typography.bodySmall)}
    m?.profile?.patch_release_ug_day?.let{Text(displayNumber(it)+" µg/24h",style=MaterialTheme.typography.bodySmall)}
}

@Composable private fun EventCard(event:LongitudinalEvent,epochs:List<TreatmentEpoch>,versions:Map<Long,RegimenVersionEntity>,extra:NotesViewModel.ExtraState,
    onOpen:(EventKind)->Unit,onEdit:(MilestoneEntity)->Unit,onDelete:(MilestoneEntity)->Unit,onEpoch:(TreatmentEpoch)->Unit) {
    var expanded by rememberSaveable(event.key){mutableStateOf(false)}
    val simple=LocalSimpleMode.current
    val s=event.source
    SectionCard(eventKindLabel(event.kind)) {
        Text(event.at?.let{timelineDateTime(it)} ?: event.date?.let{formatDate(it)} ?: stringResource(R.string.timeline_time_unknown),style=MaterialTheme.typography.labelLarge)
        if(event.at==null && event.date!=null)Text(stringResource(R.string.timeline_date_only),style=MaterialTheme.typography.labelSmall)
        val membership=event.epochs.keys.mapNotNull{key->epochs.firstOrNull{it.key==key}}
        Text(if(event.epochs.uncertain)stringResource(R.string.timeline_epoch_uncertain) else membership.singleOrNull()?.let{epochLabel(epochs.indexOf(it))} ?: stringResource(R.string.timeline_epoch_unknown),style=MaterialTheme.typography.bodySmall)
        if(event.epochs.unknownPortion)Text(stringResource(R.string.timeline_epoch_unknown),style=MaterialTheme.typography.labelSmall)
        if(!simple)when(s) {
            is EventSource.Intake->{val r=s.record;val m=MedicationSnapshot.decode(r.config_snapshot,r.medication_id)
                Text(m?.name ?: stringResource(R.string.timeline_medication_unknown))
                (if(event.kind==EventKind.DOSE)r.actual_dose else r.planned_dose)?.let{Text(formatDose(it,m?.unit))}
                if(r.status=="LATE")Text(stringResource(R.string.timeline_late))
                if(s.plannedRegimenId!=null && membership.none{s.plannedRegimenId in it.regimenIds})Text(stringResource(R.string.timeline_other_plan),style=MaterialTheme.typography.bodySmall)
            }
            is EventSource.Unconfirmed->Text(stringResource(R.string.timeline_unconfirmed_count,s.records.size))
            is EventSource.Lab->Text(analyteLabel(s.value.analyte_code)+" · "+displayNumber(s.value.value)+" "+s.value.unit)
            is EventSource.Symptom->Text(SymptomCatalog.saved(s.value)?.names?.localized(currentLocale()) ?: stringResource(R.string.wb_context_unknown))
            is EventSource.Milestone->Text(s.value.title ?: milestoneKindLabel(s.value.kind))
            is EventSource.Appointment->Text(choiceLabel(s.value.type))
            is EventSource.Planned->{val slot=s.entry.slot;val version=extra.regimenLinks.firstOrNull{it.rule_id==slot.ruleId}?.regimen_id?.let(versions::get)
                Text(version?.let{RegimenDefinition.read(it.definition_json).snapshot(slot.medicationId)?.name} ?: stringResource(R.string.timeline_medication_unknown))
                Text(stringResource(R.string.timeline_planned_note))
            }
            else->{}
        }
        if(!simple)TextButton(onClick={expanded=!expanded}){Text(stringResource(if(expanded)R.string.timeline_less else R.string.timeline_details))}
        if(expanded && !simple) {
            when(s) {
                is EventSource.Regimen->{if(s.epoch.regimenIds.isEmpty())Text(stringResource(R.string.epoch_no_plan));s.epoch.regimenIds.sorted().forEach{id->versions[id]?.let{FrozenRegimen(it)}}}
                is EventSource.Intake->{s.record.scheduled_utc?.let{Text(stringResource(R.string.history_planned_at,formatDateTime(Instant.ofEpochMilli(it))))};s.record.site?.let{Text(it)};s.record.note?.let{Text(it)}}
                is EventSource.Unconfirmed->{Text(stringResource(R.string.timeline_unconfirmed_note));s.records.mapNotNull{it.scheduled_utc}.minOrNull()?.let{Text(formatDateTime(Instant.ofEpochMilli(it)))}}
                is EventSource.Lab->{s.value.laboratory?.let{Text(it)};s.value.note?.let{Text(it)};if(s.value.reference_lower!=null || s.value.reference_upper!=null)Text(stringResource(R.string.lab_range_value,s.value.reference_lower?.let{displayNumber(it)} ?: "—",s.value.reference_upper?.let{displayNumber(it)} ?: "—",s.value.reference_unit ?: s.value.unit))}
                is EventSource.Symptom->{s.value.note?.let{Text(it)};val names=SymptomCatalog.savedMedicationNames(s.value);if(names.isNotEmpty())Text(names.joinToString(" · "))}
                is EventSource.Daily->{s.scores.forEach{score->extra.items.firstOrNull{it.id==score.item_id}?.let{Text(checkinLabel(it)+" · "+score.value+"/5")}};s.note?.let{Text(it.text)}}
                is EventSource.Review->{val v=s.value;listOfNotNull(v.tolerance_note,v.risk_note,v.satisfaction_note).forEach{Text(it)};v.weight_kg?.let{Text(displayNumber(it)+" kg")};if(v.systolic!=null || v.diastolic!=null)Text("${v.systolic ?: "—"}/${v.diastolic ?: "—"} mmHg")}
                is EventSource.Appointment->listOfNotNull(s.value.practitioner,s.value.location,s.value.note).forEach{Text(it)}
                is EventSource.Milestone->s.value.note?.let{Text(it)}
                is EventSource.Planned->{val slot=s.entry.slot;Text(formatDose(slot.dose,null))}
            }
            membership.forEach{epoch->TextButton(onClick={onEpoch(epoch)}){Text(epochLabel(epochs.indexOf(epoch)))}}
        }
        if(s is EventSource.Milestone)Row {
            TextButton(onClick={onEdit(s.value)}){Text(stringResource(R.string.edit))}
            TextButton(onClick={onDelete(s.value)}){Text(stringResource(R.string.remove))}
        } else if(event.kind !in listOf(EventKind.REGIMEN,EventKind.PLANNED))TextButton(onClick={onOpen(event.kind)}){Text(stringResource(R.string.timeline_open))}
    }
}
@Composable fun eventKindLabel(kind:EventKind)=stringResource(when(kind){
    EventKind.REGIMEN->R.string.timeline_regimen;EventKind.DOSE->R.string.timeline_dose;EventKind.MISSED->R.string.timeline_missed
    EventKind.UNCONFIRMED->R.string.status_unconfirmed;EventKind.SKIPPED->R.string.timeline_skipped;EventKind.LAB->R.string.labs
    EventKind.SYMPTOM->R.string.wb_symptoms;EventKind.WELLBEING->R.string.wellbeing;EventKind.REVIEW->R.string.wb_reviews
    EventKind.APPOINTMENT->R.string.appointment;EventKind.MILESTONE->R.string.milestone;EventKind.PLANNED->R.string.timeline_planned
})
@Composable private fun milestoneKindLabel(kind:String)=stringResource(when(kind){"STARTED"->R.string.milestone_started;"ROUTE"->R.string.milestone_route;"SURGERY"->R.string.appt_surgery;else->R.string.milestone_custom})
@Composable private fun MilestoneDialog(initial:MilestoneEntity,onDismiss:()->Unit,onSave:(MilestoneEntity)->Unit) {
    var date by remember{mutableStateOf(LocalDate.parse(initial.date))};var kind by remember{mutableStateOf(initial.kind)}
    var title by remember{mutableStateOf(initial.title.orEmpty())};var note by remember{mutableStateOf(initial.note.orEmpty())};var pick by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(if(initial.id==0L)R.string.milestone_add else R.string.edit))},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.milestone_intro),style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={pick=true}){Text(formatDate(date))}
            DropdownField(stringResource(R.string.timeline_type),listOf("CUSTOM","STARTED","ROUTE","SURGERY"),kind,{milestoneKindLabel(it)},{kind=it})
            OutlinedTextField(title,{title=it},label={Text(stringResource(R.string.milestone_title))},modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(note,{note=it},label={Text(stringResource(R.string.note))},modifier=Modifier.fillMaxWidth(),minLines=2)
        }},confirmButton={Button(enabled=kind!="CUSTOM" || title.isNotBlank(),onClick={onSave(initial.copy(date=date.toString(),kind=kind,title=title.trim().takeIf{it.isNotEmpty()},note=note.trim().takeIf{it.isNotEmpty()}))}){Text(stringResource(R.string.save))}},
        dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.cancel))}})
    if(pick)DatePickerModal(date,{pick=false}){date=it;pick=false}
}
