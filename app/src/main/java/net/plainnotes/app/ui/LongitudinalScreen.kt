package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.NotesState
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import net.plainnotes.app.timeline.*
import java.time.*

@Composable fun LongitudinalScreen(state:NotesState,extra:NotesViewModel.ExtraState,onSave:(MilestoneEntity)->Unit,
    onDelete:(Long)->Unit,onOpen:(EventKind)->Unit,contentPadding:PaddingValues,onAppointment:(Long)->Unit={},
    saveState:MilestoneSaveState=MilestoneSaveState(),onSaveHandled:()->Unit={}) {
    var now by remember{mutableStateOf(Instant.now())}
    LaunchedEffect(Unit){while(true){kotlinx.coroutines.delay(60_000);now=Instant.now()}}
    val record=remember(extra.regimens,extra.labs,extra.reviews,extra.milestones,state.appointments,now){PeriodTimelineProjection.build(extra,state.appointments,now)}
    val projection=record.projection;val zone=projection.zone;val simple=LocalSimpleMode.current
    val list=rememberLazyListState()
    var edit by rememberSaveable(stateSaver=milestoneSaver){mutableStateOf<MilestoneEntity?>(null)}
    var removeId by rememberSaveable{mutableStateOf<Long?>(null)}
    var detailKey by rememberSaveable{mutableStateOf<String?>(null)}
    var auditKey by rememberSaveable{mutableStateOf<String?>(null)}
    var focusKey by rememberSaveable{mutableStateOf<String?>(null)}
    var feedback by rememberSaveable{mutableStateOf<Int?>(null)}
    val periods=projection.periods.filter{it.from<=Instant.now()}.asReversed()
    val blocks=buildList<StoryBlock> {
        if(record.upcoming.isNotEmpty()){add(StoryBlock("upcoming"));record.upcoming.forEach{add(StoryBlock(it.key,event=it))}}
        periods.forEach{period->add(StoryBlock(period.key,period=period));record.eventsIn(period).forEach{add(StoryBlock(it.key,event=it))}}
        if(record.unknownEvents.isNotEmpty() || periods.isEmpty()){add(StoryBlock("unknown"));record.unknownEvents.forEach{add(StoryBlock(it.key,event=it))}}
    }
    LaunchedEffect(saveState.saved?.id) {
        saveState.saved?.let{saved->
            edit=null;focusKey="milestone:${saved.id}"
            feedback=if(saveState.refreshFailed)R.string.milestone_saved_refresh_failed else R.string.milestone_saved
            onSaveHandled()
        }
    }
    LaunchedEffect(focusKey,record) {
        val target=focusKey?.let{key->(record.events+record.upcoming).firstOrNull{it.key==key}} ?: return@LaunchedEffect
        val index=blocks.indexOfFirst{it.event?.key==target.key}+2
        list.animateScrollToItem(index)
        detailKey=target.key // exact source is visible even inside a large historical period
        focusKey=null
    }
    fun open(event:PeriodEvent) {
        (event.source as? EventSource.Appointment)?.let{onAppointment(it.value.id)} ?: run{detailKey=event.key}
    }
    LazyColumn(Modifier.fillMaxSize().testTag("period-timeline"),state=list,contentPadding=PaddingValues(start=16.dp,end=16.dp,
        top=contentPadding.calculateTopPadding()+8.dp,bottom=contentPadding.calculateBottomPadding()+32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="intro") {
            Text(stringResource(R.string.period_timeline_intro),style=MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.timeline_zone,zone.id),style=MaterialTheme.typography.labelSmall)
            feedback?.let{Text(stringResource(it),modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})}
        }
        item(key="actions") {
            FilledTonalButton(onClick={onSaveHandled();edit=MilestoneEntity(date=LocalDate.now(zone).toString())},enabled=!saveState.saving){Text(stringResource(R.string.milestone_add))}
        }
        items(blocks,key={it.key}){block->
            val event=block.event;val period=block.period
            when {
                event!=null->Surface(modifier=Modifier.padding(start=12.dp),shape=MaterialTheme.shapes.medium,tonalElevation=1.dp){EventRow(event,::open)}
                period!=null->{
                    val current=period.contains(now)
                    SectionCard(stringResource(if(current)R.string.period_current else R.string.period_past)) {
                        val end=period.until?.minusNanos(1)?.atZone(zone)?.toLocalDate()
                        Text(formatDate(period.from.atZone(zone).toLocalDate())+" → "+(end?.let{formatDate(it)} ?: stringResource(R.string.epoch_ongoing)))
                        val standards=projection.standards.filter{it.key in period.finalStandardSpanKeys}
                        if(standards.isEmpty())Text(stringResource(R.string.epoch_no_plan))
                        if(!simple)standards.forEach{span->
                            StandardSummary(extra.regimens.first{it.id==span.rawVersionIds.first()},span.standard)
                            if(span.standard.slotIdentityUnknown)Text(stringResource(R.string.period_slot_unknown),style=MaterialTheme.typography.bodySmall)
                        }
                        if(period.segments.size>1)Text(stringResource(R.string.period_same_day),style=MaterialTheme.typography.bodySmall)
                        if(standards.any{it.reconstructed})Text(stringResource(R.string.epoch_reconstructed),style=MaterialTheme.typography.bodySmall)
                        if(!simple)TextButton(onClick={auditKey=period.key}){Text(stringResource(R.string.period_saved_changes))}
                    }
                }
                block.key=="upcoming"->SectionCard(stringResource(R.string.period_upcoming)){Text(stringResource(R.string.period_future_note),style=MaterialTheme.typography.bodySmall)}
                else->SectionCard(stringResource(R.string.timeline_epoch_unknown)){Text(stringResource(R.string.period_unknown_note),style=MaterialTheme.typography.bodySmall)}
            }
        }
    }
    edit?.let{value->MilestoneDialog(value,saveState,{if(!saveState.saving){edit=null;onSaveHandled()}}){onSave(it)}}
    val detail=(record.events+record.upcoming).firstOrNull{it.key==detailKey}
    detail?.let{event->EventDetail(event,extra,zone,{detailKey=null},{edit=it;detailKey=null},{removeId=it.id;detailKey=null})}
    val audit=periods.firstOrNull{it.key==auditKey}
    audit?.let{period->AlertDialog(onDismissRequest={auditKey=null},title={Text(stringResource(R.string.period_saved_changes))},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            projection.raw.filter{it.span.from<(period.until ?: Instant.MAX) && (it.span.until?.let{end->end>period.from} ?: true)}.forEach{raw->
                Text("#${raw.span.id} · "+raw.span.from.toString()+" → "+(raw.span.until?.toString() ?: stringResource(R.string.epoch_ongoing)))
                StandardSummary(extra.regimens.first{it.id==raw.span.id},raw.standard)
            }
        }},confirmButton={TextButton(onClick={auditKey=null}){Text(stringResource(R.string.ok))}})}
    removeId?.let{id->AlertDialog(onDismissRequest={removeId=null},text={Text(stringResource(R.string.milestone_delete_confirm))},
        confirmButton={Button(onClick={onDelete(id);removeId=null}){Text(stringResource(R.string.remove))}},
        dismissButton={TextButton(onClick={removeId=null}){Text(stringResource(R.string.cancel))}})}
}

@Composable private fun StandardSummary(version:RegimenVersionEntity,standard:TherapyStandard) {
    val m=remember(version.definition_json){RegimenDefinition.read(version.definition_json).snapshot(version.medication_id)}
    Text(listOfNotNull(m?.name,m?.route?.let{choiceLabel(it)},standard.ester?.takeIf{it!=standard.compound}?.let{choiceLabel(it)},
        standard.doses.distinct().map{formatDose(it,standard.unit)}.joinToString(" / ")).joinToString(" · "),style=MaterialTheme.typography.bodyMedium)
    Text(stringResource(when(standard.kind){"EVERY_N_HOURS"->R.string.period_frequency_hours;"WEEKLY"->R.string.period_frequency_weeks;else->R.string.period_frequency_days},
        if(standard.kind=="WEEKLY")standard.weeklyCount*standard.doses.size else standard.doses.size,standard.interval),style=MaterialTheme.typography.bodySmall)
    m?.profile?.gel_product_id?.let{Text(gelProductLabel(it),style=MaterialTheme.typography.bodySmall)}
    m?.profile?.patch_release_ug_day?.let{Text(displayNumber(it)+" µg/24h",style=MaterialTheme.typography.bodySmall)}
}
@Composable private fun eventTitle(event:PeriodEvent):String=when(val source=event.source) {
    is EventSource.Lab->analyteLabel(source.value.analyte_code)
    is EventSource.Milestone->source.value.title ?: milestoneKindLabel(source.value.kind)
    is EventSource.Appointment->choiceLabel(source.value.type)
    else->eventKindLabel(event.kind)
}
@Composable private fun EventRow(event:PeriodEvent,onOpen:(PeriodEvent)->Unit) {
    TextButton(onClick={onOpen(event)},modifier=Modifier.fillMaxWidth().testTag("timeline:${event.key}")) {
        Text(formatDate(event.date)+" · "+eventKindLabel(event.kind)+(if(LocalSimpleMode.current)"" else " · "+eventTitle(event)),modifier=Modifier.fillMaxWidth())
    }
}
@Composable private fun EventDetail(event:PeriodEvent,extra:NotesViewModel.ExtraState,zone:ZoneId,onDismiss:()->Unit,onEdit:(MilestoneEntity)->Unit,onDelete:(MilestoneEntity)->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(eventKindLabel(event.kind))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(event.at?.let{formatDate(it.atZone(zone).toLocalDate())+" · "+formatTime(it.atZone(zone).toLocalTime())+" · "+zone.id} ?: formatDate(event.date),modifier=Modifier.testTag("timeline-event-time"))
        if(event.dateOnly)Text(stringResource(R.string.timeline_date_only),style=MaterialTheme.typography.bodySmall)
        if(!LocalSimpleMode.current) {
            Text(eventTitle(event))
            when(val s=event.source) {
                is EventSource.Lab->{val v=s.value;Text(displayNumber(v.value)+" "+v.unit);listOfNotNull(v.laboratory,v.note).forEach{Text(it)}
                    LabContextSection(v,extra.labContexts.filter{it.lab_id==v.id},null)
                    if(event.at!=null)Text(stringResource(R.string.period_current_mapping,event.exactRegimenIds.sorted().joinToString(", ").ifEmpty{"—"}),style=MaterialTheme.typography.bodySmall)
                }
                is EventSource.Review->{val v=s.value
                    val effects=org.json.JSONObject(v.effects_json)
                    REVIEW_EFFECTS.forEach{effect->
                        effects.optString(effect.id).takeIf{it.isNotEmpty()}?.let{value->Text(stringResource(effect.label)+" · "+stringResource(when(value){"NOT_YET"->R.string.wb_not_yet;"NOTICED"->R.string.wb_noticed;else->R.string.wb_unsure}))}
                        effects.optString(effect.id+":note").takeIf{it.isNotEmpty()}?.let{Text(it)}
                    }
                    listOfNotNull(v.tolerance_note,v.risk_note,v.satisfaction_note).forEach{Text(it)}
                    v.smoking?.let{Text(stringResource(R.string.wb_smoking)+" · "+stringResource(if(it=="YES")R.string.yes else R.string.wb_no))}
                    v.systolic?.let{Text(stringResource(R.string.wb_systolic)+" · $it")}
                    v.diastolic?.let{Text(stringResource(R.string.wb_diastolic)+" · $it")}
                    v.weight_kg?.let{Text(stringResource(R.string.wb_review_weight)+" · "+displayNumber(it))}
                    v.satisfaction?.let{Text(stringResource(R.string.wb_satisfaction)+" · $it/5")}
                }
                is EventSource.Milestone->s.value.note?.let{Text(it)}
                else->{}
            }
        }
    }},confirmButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.ok))}},dismissButton={
        (event.source as? EventSource.Milestone)?.let{s->Row {
            TextButton(onClick={onEdit(s.value)}){Text(stringResource(R.string.edit))}
            TextButton(onClick={onDelete(s.value)}){Text(stringResource(R.string.remove))}
        }}
    })
}
@Composable fun eventKindLabel(kind:EventKind)=stringResource(when(kind){
    EventKind.REGIMEN->R.string.timeline_regimen;EventKind.DOSE->R.string.timeline_dose;EventKind.MISSED->R.string.timeline_missed
    EventKind.UNCONFIRMED->R.string.status_unconfirmed;EventKind.SKIPPED->R.string.timeline_skipped;EventKind.LAB->R.string.labs
    EventKind.SYMPTOM->R.string.wb_symptoms;EventKind.WELLBEING->R.string.wellbeing;EventKind.REVIEW->R.string.wb_reviews
    EventKind.APPOINTMENT->R.string.appointment;EventKind.MILESTONE->R.string.milestone;EventKind.PLANNED->R.string.timeline_planned
})
@Composable private fun milestoneKindLabel(kind:String)=stringResource(when(kind){"STARTED"->R.string.milestone_started;"ROUTE"->R.string.milestone_route;"SURGERY"->R.string.appt_surgery;else->R.string.milestone_custom})
@Composable private fun MilestoneDialog(initial:MilestoneEntity,saveState:MilestoneSaveState,onDismiss:()->Unit,onSave:(MilestoneEntity)->Unit) {
    var dateText by rememberSaveable(initial.id){mutableStateOf(initial.date)};val date=LocalDate.parse(dateText);var kind by rememberSaveable(initial.id){mutableStateOf(initial.kind)}
    var title by rememberSaveable(initial.id){mutableStateOf(initial.title.orEmpty())};var note by rememberSaveable(initial.id){mutableStateOf(initial.note.orEmpty())};var pick by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(if(initial.id==0L)R.string.milestone_add else R.string.edit))},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(saveState.failed)Text(stringResource(R.string.operation_error),color=MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.milestone_intro),style=MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled=!saveState.saving,onClick={pick=true}){Text(formatDate(date))}
            DropdownField(stringResource(R.string.timeline_type),listOf("CUSTOM","STARTED","ROUTE","SURGERY"),kind,{milestoneKindLabel(it)},{kind=it})
            OutlinedTextField(title,{title=it},enabled=!saveState.saving,label={Text(stringResource(R.string.milestone_title))},modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(note,{note=it},enabled=!saveState.saving,label={Text(stringResource(R.string.note))},modifier=Modifier.fillMaxWidth(),minLines=2)
        }},confirmButton={Button(enabled=!saveState.saving && (kind!="CUSTOM" || title.isNotBlank()),onClick={onSave(initial.copy(date=date.toString(),kind=kind,title=title.trim().takeIf{it.isNotEmpty()},note=note.trim().takeIf{it.isNotEmpty()}))}){Text(stringResource(if(saveState.saving)R.string.milestone_saving else R.string.save))}},
        dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.cancel))}})
    if(pick)DatePickerModal(date,{pick=false}){dateText=it.toString();pick=false}
}

private val milestoneSaver=androidx.compose.runtime.saveable.Saver<MilestoneEntity?,List<String>>(
    save={v->v?.let{listOf(it.id.toString(),it.date,it.kind,it.title.orEmpty(),it.note.orEmpty())} ?: emptyList()},
    restore={v->v.takeIf{it.isNotEmpty()}?.let{MilestoneEntity(it[0].toLong(),it[1],it[2],it[3].takeIf(String::isNotEmpty),it[4].takeIf(String::isNotEmpty))}})

private data class StoryBlock(val key:String,val period:DisplayPeriod?=null,val event:PeriodEvent?=null)
