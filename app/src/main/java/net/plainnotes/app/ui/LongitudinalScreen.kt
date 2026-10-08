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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.MoreVert
import net.plainnotes.app.R
import net.plainnotes.app.NotesState
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import net.plainnotes.app.timeline.*
import java.time.*

@Composable fun LongitudinalScreen(state:NotesState,extra:NotesViewModel.ExtraState,onSave:(MilestoneEntity)->Unit,
    onDelete:(Long)->Unit,onOpen:(EventKind)->Unit,contentPadding:PaddingValues,onAppointment:(Long)->Unit={},
    saveState:MilestoneSaveState=MilestoneSaveState(),onSaveHandled:()->Unit={},onImportedHistory:(List<Long>)->Unit={onOpen(EventKind.DOSE)},
    periodActions:PeriodActions=PeriodActions(),timelineActions:TimelineActions=TimelineActions()) {
    var periodDraft by remember{mutableStateOf<Pair<PeriodDraft,String?>?>(null)}
    var now by remember{mutableStateOf(Instant.now())}
    LaunchedEffect(Unit){while(true){kotlinx.coroutines.delay(60_000);now=Instant.now()}}
    val record=remember(extra.records,extra.regimens,extra.labs,extra.reviews,extra.milestones,extra.historyPeriods,state.appointments,state.ruleSnapshots,now){PeriodTimelineProjection.build(extra,state.appointments,now,ruleSnapshots=state.ruleSnapshots)}
    val projection=record.projection;val zone=projection.zone;val simple=LocalSimpleMode.current
    val list=rememberLazyListState()
    var edit by rememberSaveable(stateSaver=milestoneSaver){mutableStateOf<MilestoneEntity?>(null)}
    var removeId by rememberSaveable{mutableStateOf<Long?>(null)}
    var detailKey by rememberSaveable{mutableStateOf<String?>(null)}
    var importKey by rememberSaveable{mutableStateOf<String?>(null)}
    var auditKey by rememberSaveable{mutableStateOf<String?>(null)}
    var focusKey by rememberSaveable{mutableStateOf<String?>(null)}
    var feedback by rememberSaveable{mutableStateOf<Int?>(null)}
    // §37b timeline edits
    var editRequest by remember{mutableStateOf<Pair<PeriodEditRequest,(Long,TherapyStandard,TimelineEdits.Range)->Unit>?>(null)}
    var splitFor by remember{mutableStateOf<DisplayPeriod?>(null)}
    var deleteFor by remember{mutableStateOf<DisplayPeriod?>(null)}
    var stopFor by remember{mutableStateOf<TreatmentStop?>(null)}
    var shortDelete by remember{mutableStateOf<RawTreatmentInterval?>(null)}
    fun identity(med:Long)=state.medications.firstOrNull{it.id==med}?.let{MedicationSnapshot.encode(it,state.profiles[med])} ?: "{}"
    fun apply(edit:TimelineEdits.Edit?){edit?.let{timelineActions.edit(it.replace,it.rows)}}
    fun editPeriod(title:Int,meds:List<Long>,med:Long?,range:TimelineEdits.Range,standard:TherapyStandard?,target:TimelineEdits.Range?,merge:Pair<TherapyStandard?,TherapyStandard?>?=null,
                   exact:Pair<Instant,Instant?>?=null) {
        editRequest=PeriodEditRequest(title,meds.ifEmpty{state.medications.map{it.id}},med,range,standard,target,listOfNotNull(target),merge) to {m,std,r->
            // §38: kept dates mean the exact part; changed dates mean whole days.
            apply(TimelineEdits.period(extra.historyPeriods,m,std,r,zone,identity(m),listOfNotNull(target),exact?.takeIf{r==range}))}
    }
    val periods=projection.periods.filter{it.from<=Instant.now()}.asReversed()
    val blocks=buildList<StoryBlock> {
        if(record.upcoming.isNotEmpty() || record.importedHistory.any{it.future}){
            add(StoryBlock("upcoming"));record.upcoming.forEach{add(StoryBlock(it.key,event=it))}
            record.importedHistory.filter{it.future}.forEach{add(StoryBlock(it.key,imported=it))}
        }
        periods.forEach{period->add(StoryBlock(period.key,period=period));record.eventsIn(period).forEach{add(StoryBlock(it.key,event=it))}
            record.importedHistory.filter{!it.future && it.displayPeriodKey==period.key}.forEach{add(StoryBlock(it.key,imported=it))}
        }
        if(record.unknownEvents.isNotEmpty() || periods.isEmpty() || record.importedHistory.any{!it.future && it.displayPeriodKey==null}){
            add(StoryBlock("unknown"));record.unknownEvents.forEach{add(StoryBlock(it.key,event=it))}
            record.importedHistory.filter{!it.future && it.displayPeriodKey==null}.forEach{add(StoryBlock(it.key,imported=it))}
        }
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
            if(record.recognitionUnavailable)Text(stringResource(R.string.period_recognition_unavailable),modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite},style=MaterialTheme.typography.bodySmall)
            feedback?.let{Text(stringResource(it),modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})}
        }
        item(key="actions") {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick={onSaveHandled();edit=MilestoneEntity(date=LocalDate.now(zone).toString())},enabled=!saveState.saving){Text(stringResource(R.string.milestone_add))}
                // §37b: a past period the import missed, or any period the user wants to state.
                OutlinedButton(onClick={editPeriod(R.string.period_edit_title_create,emptyList(),null,TimelineEdits.Range(LocalDate.now(zone).minusDays(30),LocalDate.now(zone)),null,null)},
                    enabled=state.medications.isNotEmpty(),modifier=Modifier.testTag("timeline-new-period")){Text(stringResource(R.string.timeline_new_period))}
            }
        }
        items(blocks,key={it.key}){block->
            val event=block.event;val period=block.period
            when {
                block.imported!=null->{val imported=block.imported
                    Surface(modifier=Modifier.padding(start=12.dp),shape=MaterialTheme.shapes.medium,tonalElevation=1.dp){
                        TextButton(onClick={importKey=imported.key},modifier=Modifier.fillMaxWidth().testTag("timeline:${imported.key}")){
                            Column(Modifier.fillMaxWidth()){
                                Text(stringResource(R.string.timeline_imported_history))
                                Text(importedRange(imported,zone))
                                Text(stringResource(R.string.timeline_imported_count,imported.records.size),style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                event!=null->Surface(modifier=Modifier.padding(start=12.dp),shape=MaterialTheme.shapes.medium,tonalElevation=1.dp){EventRow(event,::open)}
                period!=null->{
                    val current=period.contains(now)
                    val standards=projection.standards.filter{it.key in period.finalStandardSpanKeys}
                    SectionCard(stringResource(if(standards.isEmpty())R.string.timeline_epoch_unknown else if(current)R.string.period_current else R.string.period_past)) {
                        val end=period.until?.minusNanos(1)?.atZone(zone)?.toLocalDate()
                        Text(formatDate(period.from.atZone(zone).toLocalDate())+" → "+(end?.let{formatDate(it)} ?: stringResource(R.string.epoch_ongoing)))
                        if(standards.isEmpty())Text(stringResource(R.string.epoch_no_plan))
                        // REQUIREMENTS §36: explicit stops and why this period differs from the previous one.
                        if(!simple) {
                            fun medName(id:Long)=state.medications.firstOrNull{it.id==id}?.name ?: projection.standards.firstOrNull{it.medicationId==id}?.standard?.compound ?: "—"
                            @Composable fun day(at:Instant)=formatDate(at.atZone(zone).toLocalDate())
                            projection.stops.filter{it.from>=period.from && (period.until==null || it.from<period.until)}.forEach{stop->
                                Text(stringResource(R.string.period_stopped,medName(stop.medicationId),day(stop.from),stop.until?.let{day(it)} ?: stringResource(R.string.epoch_ongoing)),
                                    style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("period-stop:${stop.medicationId}:${stop.from.toEpochMilli()}"))
                            }
                            val changes=projection.changes(period)
                            if(changes.isNotEmpty()) {
                                Text(stringResource(R.string.period_change_header),style=MaterialTheme.typography.labelMedium)
                                changes.forEach{c->Text(medName(c.medicationId)+" · "+c.kinds.sortedBy{it.ordinal}.map{stringResource(changeRes(it))}.joinToString(" · "),style=MaterialTheme.typography.bodySmall)}
                            }
                        }
                        if(!simple)standards.forEach{span->
                            val observed=record.observed.firstOrNull{it.interval.span.id in span.rawVersionIds}
                            val saved=extra.regimens.firstOrNull{it.id in span.rawVersionIds}
                            val confirmedRow=span.rawVersionIds.filter(ObservedTreatmentHistory::isConfirmedSpan).firstNotNullOfOrNull{id->
                                record.confirmedHistory.firstOrNull{it.id==(ObservedTreatmentHistory.CONFIRMED_SPAN_BASE-id)/100}}
                            StandardSummary(saved?.let{RegimenDefinition.read(it.definition_json).snapshot(it.medication_id)} ?: confirmedRow?.let{MedicationSnapshot.decode(it.identity_json,it.medication_id)} ?: observed?.snapshot,span.standard)
                            // REQUIREMENTS §35a: recognised periods stay "to confirm" until the user confirms them.
                            if(confirmedRow!=null) {
                                Text(stringResource(if(saved!=null)R.string.period_partly_confirmed else R.string.period_confirmed_by_user),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                                val rowZone=ZoneId.of(confirmedRow.zone);val rows=extra.records.filter{r->r.taken_utc!=null && r.deleted_at_utc==null &&
                                    record.coverage[r.id]?.interval?.span?.id?.let{id->ObservedTreatmentHistory.isConfirmedSpan(id) && (ObservedTreatmentHistory.CONFIRMED_SPAN_BASE-id)/100==confirmedRow.id}==true}
                                val next=record.confirmedHistory.filter{it.medication_id==confirmedRow.medication_id && it.from_date>confirmedRow.from_date}.minByOrNull{it.from_date}
                                    ?.takeIf{HistoryPeriods.readStandard(it.standard_json)==HistoryPeriods.readStandard(confirmedRow.standard_json)}
                                TextButton(onClick={periodDraft=PeriodDraft(confirmedRow.period_key,confirmedRow.medication_id,MedicationSnapshot.decode(confirmedRow.identity_json,confirmedRow.medication_id),
                                    confirmedRow.identity_json,HistoryPeriods.readStandard(confirmedRow.standard_json),LocalDate.parse(confirmedRow.from_date),confirmedRow.until_date?.let{LocalDate.parse(it).minusDays(1)},
                                    rowZone,rows.map{it.id},rows.map{Instant.ofEpochMilli(it.taken_utc!!).atZone(rowZone).toLocalDate()}.distinct().size) to next?.period_key},
                                    modifier=Modifier.testTag("period-edit:${confirmedRow.period_key}")){Text(stringResource(R.string.period_edit))}
                            } else if(observed!=null) {
                                Text(stringResource(if(saved!=null)R.string.period_partly_pending else R.string.period_pending),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.tertiary)
                                val obsZone=observed.records.firstOrNull()?.taken_zone?.let{runCatching{ZoneId.of(it)}.getOrNull()} ?: zone
                                if(record.confirmedHistory.any{it.medication_id==observed.interval.span.medicationId && it.until_date!=null &&
                                        HistoryPeriods.readStandard(it.standard_json)==observed.interval.standard.copy(kind="EVERY_N_DAYS")})
                                    Text(stringResource(R.string.period_mergeable),style=MaterialTheme.typography.bodySmall)
                                TextButton(onClick={periodDraft=PeriodDraft(null,observed.interval.span.medicationId,observed.snapshot,observed.records.firstOrNull()?.config_snapshot?.takeIf{runCatching{org.json.JSONObject(it)}.isSuccess} ?: "{}",
                                    observed.interval.standard,observed.interval.span.from.atZone(obsZone).toLocalDate(),observed.interval.span.until?.minusNanos(1)?.atZone(obsZone)?.toLocalDate(),
                                    obsZone,observed.records.map{it.id},observed.records.mapNotNull{r->r.taken_utc?.let{Instant.ofEpochMilli(it).atZone(obsZone).toLocalDate()}}.distinct().size) to null},
                                    modifier=Modifier.testTag("period-confirm:${observed.interval.span.id}")){Text(stringResource(R.string.period_confirm))}
                            }
                            if(span.standard.kind!="OBSERVED" && span.standard.slotIdentityUnknown)Text(stringResource(R.string.period_slot_unknown),style=MaterialTheme.typography.bodySmall)
                        }
                        // §37a: short saved versions shown inside this period, with what they actually said.
                        projection.raw.filter{r->r.kind==SpanKind.SAVED && r.span.from>=period.from && (period.until==null || r.span.from<period.until) &&
                            projection.effective[r.span.id]?.let{it!=r.standard}==true}.forEach{r->
                            val hours=java.time.Duration.between(r.span.from,r.span.until ?: r.span.from).toHours().coerceAtLeast(1)
                            val length=if(hours<48)pluralStringResource(R.plurals.period_duration_hours,hours.toInt(),hours.toInt()) else pluralStringResource(R.plurals.period_duration_days,(hours/24).toInt(),(hours/24).toInt())
                            val what=(if(simple)"" else r.standard.doses.distinct().map{formatDose(it,r.standard.unit)}.joinToString(" / ")+" · ")+
                                stringResource(R.string.period_frequency_days,r.standard.doses.size,r.standard.interval)
                            Text(stringResource(R.string.period_short_saved,formatDate(r.span.from.atZone(zone).toLocalDate()),what,length),
                                style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("period-short:${r.span.id}"))
                            // It can be taken out, changed or deleted on its own (§37b); days are the smallest unit of an edit.
                            // §38: these act on exactly this version's time, never on the whole day, and deleting asks first.
                            val shortRange=TimelineEdits.daysOf(r.span.from,r.span.until,zone)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick={editPeriod(R.string.period_edit_title_edit,listOf(r.span.medicationId),r.span.medicationId,shortRange,r.standard,shortRange,exact=r.span.from to r.span.until)},
                                    modifier=Modifier.testTag("period-short-edit:${r.span.id}")){Text(stringResource(R.string.period_short_edit))}
                                TextButton(onClick={shortDelete=r},modifier=Modifier.testTag("period-short-delete:${r.span.id}")){Text(stringResource(R.string.period_short_delete))}
                            }
                        }
                        if(period.segments.size>1)Text(stringResource(R.string.period_same_day),style=MaterialTheme.typography.bodySmall)
                        if(standards.any{span->extra.regimens.any{it.id in span.rawVersionIds && it.origin=="LEGACY_RULE"}})Text(stringResource(R.string.epoch_reconstructed),style=MaterialTheme.typography.bodySmall)
                        val sourceRows=record.resolvedRecords.filter{r->(r.taken_utc ?: r.scheduled_utc)?.let{period.contains(Instant.ofEpochMilli(it))}==true}
                        if(sourceRows.isNotEmpty())TextButton(onClick={onImportedHistory(sourceRows.map{it.id})},modifier=Modifier.testTag("period-history:${period.key}")){Text(stringResource(R.string.period_evidence,sourceRows.size))}
                        if(!simple)TextButton(onClick={auditKey=period.key}){Text(stringResource(R.string.period_saved_changes))}
                        // §37b: every period can be edited, whatever made it; §36b diagnostics stay (hidden in simple mode).
                        val meds=TimelineEdits.medicationsIn(projection,period);val range=TimelineEdits.rangeOf(period,zone)
                        val next=projection.periods.getOrNull(projection.periods.indexOf(period)+1)?.takeIf{it.from<=now}
                        val mine=TimelineEdits.userRowsIn(extra.historyPeriods,period,zone)
                        val deleted=mine.filter{it.kind==HistoryPeriods.DELETED};val edited=mine.filter{it.kind!=HistoryPeriods.DELETED}
                        val stop=projection.stops.firstOrNull{it.from>=period.from && (period.until==null || it.from<period.until)}
                        if(deleted.isNotEmpty())Text(stringResource(R.string.period_deleted),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.error)
                        if(edited.isNotEmpty())Text(stringResource(R.string.period_user_edited),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                        Box(Modifier.fillMaxWidth(),contentAlignment=androidx.compose.ui.Alignment.CenterEnd) {
                            var menu by remember{mutableStateOf(false)}
                            val context=androidx.compose.ui.platform.LocalContext.current
                            IconButton(onClick={menu=true},modifier=Modifier.testTag("period-menu:${period.key}")){
                                Icon(androidx.compose.material.icons.Icons.Outlined.MoreVert,stringResource(R.string.period_menu))}
                            DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                                @Composable fun item(text:Int,tag:String,action:()->Unit)=DropdownMenuItem(text={Text(stringResource(text))},
                                    modifier=Modifier.testTag("$tag:${period.key}"),onClick={menu=false;action()})
                                item(R.string.period_menu_edit,"period-edit-any"){val m=meds.firstOrNull()
                                    editPeriod(R.string.period_edit_title_edit,meds,m,range,m?.let{TimelineEdits.standardOf(projection,period,it)},range)}
                                if(meds.isNotEmpty())item(R.string.period_menu_split,"period-split"){splitFor=period}
                                if(next!=null)item(R.string.period_menu_merge_next,"period-merge"){
                                    val nextMeds=TimelineEdits.medicationsIn(projection,next);val all=(meds+nextMeds).distinct()
                                    val m=all.firstOrNull();val target=TimelineEdits.Range(range.from,TimelineEdits.rangeOf(next,zone).until)
                                    val a=m?.let{TimelineEdits.standardOf(projection,period,it)};val b=m?.let{TimelineEdits.standardOf(projection,next,it)}
                                    editPeriod(R.string.period_edit_title_merge,all,m,target,a ?: b,target,a to b)}
                                if(meds.isNotEmpty())item(R.string.period_menu_delete,"period-delete"){deleteFor=period}
                                if(stop!=null)item(R.string.period_menu_edit_stop,"period-stop-edit"){stopFor=stop}
                                deleted.firstOrNull()?.group_key?.let{g->item(R.string.period_menu_restore,"period-restore"){timelineActions.undo(g)}}
                                edited.firstOrNull()?.group_key?.let{g->item(R.string.period_menu_undo,"period-undo"){timelineActions.undo(g)}}
                                if(!simple)item(R.string.period_copy_diagnostics,"period-diagnostics"){
                                    val version=androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName,0)).toInt()
                                    val text=MergeDiagnostics.text(record,extra,period,version)
                                    context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText("HRT Log",text))
                                    android.widget.Toast.makeText(context,context.getString(R.string.period_diagnostics_copied),android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
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
    record.importedHistory.firstOrNull{it.key==importKey}?.let{summary->
        ImportedHistoryDialog(summary,zone,{importKey=null}){onImportedHistory(summary.records.map{it.id});importKey=null}
    }
    val audit=periods.firstOrNull{it.key==auditKey}
    periodDraft?.let{(draft,next)->HistoryPeriodDialog(draft,next,periodActions){periodDraft=null}}
    editRequest?.let{(request,save)->PeriodEditDialog(request,state,{m,r,t->TimelineEdits.conflicts(projection,m,r,t)},save){editRequest=null}}
    splitFor?.let{period->val range=TimelineEdits.rangeOf(period,zone)
        DatePickerModal(range.from.plusDays(1),{splitFor=null}){day->splitFor=null;apply(TimelineEdits.split(extra.historyPeriods,projection,period,day,::identity))}}
    deleteFor?.let{period->AlertDialog(onDismissRequest={deleteFor=null},text={Text(stringResource(R.string.period_delete_confirm))},modifier=Modifier.testTag("period-delete-dialog"),
        confirmButton={Button(onClick={deleteFor=null;apply(TimelineEdits.delete(extra.historyPeriods,projection,period,::identity))}){Text(stringResource(R.string.period_menu_delete))}},
        dismissButton={TextButton(onClick={deleteFor=null}){Text(stringResource(R.string.cancel))}})}
    shortDelete?.let{r->AlertDialog(onDismissRequest={shortDelete=null},text={Text(stringResource(R.string.period_delete_confirm))},modifier=Modifier.testTag("period-short-delete-dialog"),
        confirmButton={Button(onClick={shortDelete=null;val range=TimelineEdits.daysOf(r.span.from,r.span.until,zone)
            apply(TimelineEdits.Edit(TimelineEdits.touched(extra.historyPeriods,r.span.medicationId,range),listOf(TimelineEditRow(HistoryPeriods.DELETED,r.span.medicationId,null,
                range.from,range.until,zone,identity(r.span.medicationId),exactFromUtc=r.span.from.toEpochMilli(),exactUntilUtc=r.span.until?.toEpochMilli()))))}){Text(stringResource(R.string.period_menu_delete))}},
        dismissButton={TextButton(onClick={shortDelete=null}){Text(stringResource(R.string.cancel))}})}
    stopFor?.let{stop->StopEditDialog(TimelineEdits.Range(stop.from.atZone(zone).toLocalDate(),stop.until?.atZone(zone)?.toLocalDate()),{r->
        val med=projection.raw.firstOrNull{it.span.medicationId==stop.medicationId}?.span?.medicationId ?: stop.medicationId
        apply(TimelineEdits.moveStop(extra.historyPeriods,projection,stop,med,r,identity(med)))}){stopFor=null}}
    audit?.let{period->AlertDialog(onDismissRequest={auditKey=null},title={Text(stringResource(R.string.period_saved_changes))},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            projection.raw.filter{it.span.from<(period.until ?: Instant.MAX) && (it.span.until?.let{end->end>period.from} ?: true)}.forEach{raw->
                Text((if(raw.span.id>0)"#${raw.span.id}" else stringResource(R.string.period_observed))+" · "+raw.span.from.toString()+" → "+(raw.span.until?.toString() ?: stringResource(R.string.epoch_ongoing)))
                val saved=extra.regimens.firstOrNull{it.id==raw.span.id}
                StandardSummary(saved?.let{RegimenDefinition.read(it.definition_json).snapshot(it.medication_id)} ?: record.observed.firstOrNull{it.interval.span.id==raw.span.id}?.snapshot,
                    saved?.let{RegimenDefinition.read(it.definition_json).therapyStandard(normalizeCadence=false)} ?: raw.standard)
            }
        }},confirmButton={TextButton(onClick={auditKey=null}){Text(stringResource(R.string.ok))}})}
    removeId?.let{id->AlertDialog(onDismissRequest={removeId=null},text={Text(stringResource(R.string.milestone_delete_confirm))},
        confirmButton={Button(onClick={onDelete(id);removeId=null}){Text(stringResource(R.string.remove))}},
        dismissButton={TextButton(onClick={removeId=null}){Text(stringResource(R.string.cancel))}})}
}

@Composable private fun StandardSummary(m:MedicationSnapshot?,standard:TherapyStandard) {
    Text(listOfNotNull(m?.name,m?.route?.let{choiceLabel(it)},standard.ester?.takeIf{it!=standard.compound}?.let{choiceLabel(it)},
        standard.doses.distinct().map{formatDose(it,standard.unit)}.joinToString(" / ")).joinToString(" · "),style=MaterialTheme.typography.bodyMedium)
    if(standard.kind=="OBSERVED")Text(stringResource(R.string.period_frequency_unconfirmed),style=MaterialTheme.typography.bodySmall)
    else Text(stringResource(when(standard.kind){"EVERY_N_HOURS"->R.string.period_frequency_hours;"WEEKLY"->R.string.period_frequency_weeks;else->R.string.period_frequency_days},
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
@Composable private fun milestoneKindLabel(kind:String)=stringResource(when(kind){"STARTED"->R.string.milestone_started;"ROUTE"->R.string.milestone_route;"SURGERY"->R.string.appt_surgery;"PAUSED"->R.string.milestone_paused;"STOPPED"->R.string.milestone_stopped;"RESUMED"->R.string.milestone_resumed;else->R.string.milestone_custom})
@Composable private fun MilestoneDialog(initial:MilestoneEntity,saveState:MilestoneSaveState,onDismiss:()->Unit,onSave:(MilestoneEntity)->Unit) {
    var dateText by rememberSaveable(initial.id){mutableStateOf(initial.date)};val date=LocalDate.parse(dateText);var kind by rememberSaveable(initial.id){mutableStateOf(initial.kind)}
    var title by rememberSaveable(initial.id){mutableStateOf(initial.title.orEmpty())};var note by rememberSaveable(initial.id){mutableStateOf(initial.note.orEmpty())};var pick by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(if(initial.id==0L)R.string.milestone_add else R.string.edit))},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(saveState.failed)Text(stringResource(R.string.operation_error),color=MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.milestone_intro),style=MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled=!saveState.saving,onClick={pick=true}){Text(formatDate(date))}
            DropdownField(stringResource(R.string.timeline_type),MILESTONE_KINDS,kind,{milestoneKindLabel(it)},{kind=it})
            if(kind in listOf("PAUSED","STOPPED","RESUMED"))Text(stringResource(R.string.milestone_history_only),style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(title,{title=it},enabled=!saveState.saving,label={Text(stringResource(R.string.milestone_title))},modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(note,{note=it},enabled=!saveState.saving,label={Text(stringResource(R.string.note))},modifier=Modifier.fillMaxWidth(),minLines=2)
        }},confirmButton={Button(enabled=!saveState.saving && (kind!="CUSTOM" || title.isNotBlank()),onClick={onSave(initial.copy(date=date.toString(),kind=kind,title=title.trim().takeIf{it.isNotEmpty()},note=note.trim().takeIf{it.isNotEmpty()}))}){Text(stringResource(if(saveState.saving)R.string.milestone_saving else R.string.save))}},
        dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.cancel))}})
    if(pick)DatePickerModal(date,{pick=false}){dateText=it.toString();pick=false}
}

private val milestoneSaver=androidx.compose.runtime.saveable.Saver<MilestoneEntity?,List<String>>(
    save={v->v?.let{listOf(it.id.toString(),it.date,it.kind,it.title.orEmpty(),it.note.orEmpty())} ?: emptyList()},
    restore={v->v.takeIf{it.isNotEmpty()}?.let{MilestoneEntity(it[0].toLong(),it[1],it[2],it[3].takeIf(String::isNotEmpty),it[4].takeIf(String::isNotEmpty))}})

private data class StoryBlock(val key:String,val period:DisplayPeriod?=null,val event:PeriodEvent?=null,val imported:ImportedHistorySummary?=null)

@Composable private fun importedRange(summary:ImportedHistorySummary,zone:ZoneId)=
    formatDate(summary.from.atZone(zone).toLocalDate())+" → "+formatDate(summary.through.atZone(zone).toLocalDate())

@Composable private fun ImportedHistoryDialog(summary:ImportedHistorySummary,zone:ZoneId,onDismiss:()->Unit,onHistory:()->Unit) {
    // Decode frozen contexts only; current medication names, doses and routes must not reinterpret imports.
    data class SavedMedication(val id:Long,val name:String?,val compound:String?,val route:String?,val unit:String?,val ester:String?)
    val simple=LocalSimpleMode.current
    val groups=remember(summary.records){
        val decoded=summary.records.map{it.medication_id to it.config_snapshot}.distinct().associateWith{(id,json)->MedicationSnapshot.decode(json,id)}
        summary.records.groupBy{r->val m=decoded[r.medication_id to r.config_snapshot]
            SavedMedication(r.medication_id,m?.name,m?.molecule,m?.route,m?.unit,m?.profile?.ester)}.toList()
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.timeline_imported_history))},text={
        LazyColumn(Modifier.heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            item{
                Text(importedRange(summary,zone)+" · "+zone.id)
                Text(stringResource(R.string.timeline_imported_count,summary.records.size))
                Text(stringResource(R.string.timeline_imported_context),style=MaterialTheme.typography.bodySmall)
            }
            if(!simple)items(groups){(m,rows)->Column{
                Text(m.name ?: "#${m.id}",style=MaterialTheme.typography.titleSmall)
                Text(listOfNotNull(m.compound?.let{choiceLabel(it)},m.ester?.takeIf{it!=m.compound}?.let{choiceLabel(it)},
                    m.route?.let{choiceLabel(it)} ?: stringResource(R.string.timeline_imported_unknown_route)).joinToString(" · "))
                val amounts=rows.mapNotNull{it.actual_dose}
                if(amounts.isNotEmpty())Text(stringResource(R.string.timeline_imported_amounts,
                    formatDose(amounts.min(),m.unit),formatDose(amounts.max(),m.unit)),style=MaterialTheme.typography.bodySmall)
                val missing=rows.count{it.taken_utc!=null && it.actual_dose==null}
                if(missing>0)Text(stringResource(R.string.timeline_imported_unknown_amount,missing),style=MaterialTheme.typography.bodySmall)
                // IMPORT_HT uses ON_TIME as a storage category, even without a planned time.
                // Counts here describe records, never an inferred adherence classification.
                Text(stringResource(R.string.timeline_imported_count,rows.size),style=MaterialTheme.typography.bodySmall)
            }}
        }
    },confirmButton={TextButton(onClick=onHistory){Text(stringResource(R.string.timeline_imported_open_history))}},
        dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.ok))}})
}

private fun changeRes(kind:net.plainnotes.app.domain.ChangeKind)=when(kind) {
    net.plainnotes.app.domain.ChangeKind.STARTED->R.string.period_change_started
    net.plainnotes.app.domain.ChangeKind.ENDED->R.string.period_change_ended
    net.plainnotes.app.domain.ChangeKind.STOPPED->R.string.period_change_stopped
    net.plainnotes.app.domain.ChangeKind.DOSE->R.string.period_change_dose
    net.plainnotes.app.domain.ChangeKind.FREQUENCY->R.string.period_change_frequency
    net.plainnotes.app.domain.ChangeKind.FREQUENCY_UNKNOWN->R.string.period_change_frequency_unknown
    net.plainnotes.app.domain.ChangeKind.ROUTE->R.string.period_change_route
    net.plainnotes.app.domain.ChangeKind.ESTER->R.string.period_change_ester
    net.plainnotes.app.domain.ChangeKind.FORMULATION->R.string.period_change_formulation
    net.plainnotes.app.domain.ChangeKind.IDENTITY->R.string.period_change_identity
    net.plainnotes.app.domain.ChangeKind.GAP->R.string.period_change_gap
    net.plainnotes.app.domain.ChangeKind.UNJOINED->R.string.period_change_unjoined
}
