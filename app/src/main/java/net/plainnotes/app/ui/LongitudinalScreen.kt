package net.plainnotes.app.ui

import androidx.compose.ui.graphics.luminance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SkipNext
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
    timelineActions:TimelineActions=TimelineActions(),addHost:TimelineAddHost?=null,onLabPanel:(Long)->Unit={}) {
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
    var deleteFor by remember{mutableStateOf<DisplayPeriod?>(null)}
    var shortDelete by remember{mutableStateOf<RawTreatmentInterval?>(null)}
    fun identity(med:Long)=state.medications.firstOrNull{it.id==med}?.let{MedicationSnapshot.encode(it,state.profiles[med])} ?: "{}"
    fun apply(edit:TimelineEdits.Edit?){edit?.let{timelineActions.edit(it.replace,it.rows)}}
    fun editPeriod(title:Int,meds:List<Long>,med:Long?,range:TimelineEdits.Range,standard:TherapyStandard?,target:TimelineEdits.Range?,
                   exact:Pair<Instant,Instant?>?=null) {
        editRequest=PeriodEditRequest(title,meds.ifEmpty{state.medications.map{it.id}},med,range,standard,target,exactTarget=exact,overlapChanges={m,r,t->TimelineEdits.overlapChanges(projection,m,r,t,exact,exact?.takeIf{r==range})},zone=zone) to {m,std,r->
            // §38: kept dates mean the exact part; changed dates mean whole days.
            apply(TimelineEdits.saveV2(extra.historyPeriods,projection,m,std,r,identity(m),target,exact,exact?.takeIf{r==range}))}
    }
    val periods=projection.periods.filter{it.from<=Instant.now()}.asReversed()
    // LAB-04: a lab draw (a panel, or results with the same time and laboratory) is one row, grouped as the labs page groups.
    val draws=remember(record,extra.labPanels){TimelineLabDraws.mergeable(record.events,record.upcoming,extra.labPanels)}
    fun MutableList<StoryBlock>.addEvents(events:List<PeriodEvent>)=TimelineLabDraws.fold(events,draws).forEach{row->when(row){
        is TimelineRow.One->add(StoryBlock(row.event.key,event=row.event));is TimelineRow.Draw->add(StoryBlock(row.draw.key,draw=row.draw))}}
    val blocks=buildList<StoryBlock> {
        if(record.upcoming.isNotEmpty() || record.importedHistory.any{it.future}){
            add(StoryBlock("upcoming"));addEvents(record.upcoming)
            record.importedHistory.filter{it.future}.forEach{add(StoryBlock(it.key,imported=it))}
        }
        periods.forEach{period->add(StoryBlock(period.key,period=period));addEvents(record.eventsIn(period))
            record.importedHistory.filter{!it.future && it.displayPeriodKey==period.key}.forEach{add(StoryBlock(it.key,imported=it))}
        }
        if(record.unknownEvents.isNotEmpty() || periods.isEmpty() || record.importedHistory.any{!it.future && it.displayPeriodKey==null}){
            add(StoryBlock("unknown"));addEvents(record.unknownEvents)
            record.importedHistory.filter{!it.future && it.displayPeriodKey==null}.forEach{add(StoryBlock(it.key,imported=it))}
        }
    }
    // UI-02 A1: front-end type filter only; the projection and period cards are unchanged.
    var filter by rememberSaveable { mutableStateOf(TimelineFilter.ALL) }
    val shown=remember(blocks,filter){if(filter==TimelineFilter.ALL)blocks else blocks.filter{b->b.event?.let{filter.matches(it.kind)} ?: b.draw?.let{filter.matches(EventKind.LAB)} ?: (b.imported==null || filter==TimelineFilter.OTHER)}}
    LaunchedEffect(saveState.saved?.id) {
        saveState.saved?.let{saved->
            edit=null;focusKey="milestone:${saved.id}"
            feedback=if(saveState.refreshFailed)R.string.milestone_saved_refresh_failed else R.string.milestone_saved
            onSaveHandled()
        }
    }
    LaunchedEffect(focusKey,record) {
        val target=focusKey?.let{key->(record.events+record.upcoming).firstOrNull{it.key==key}} ?: return@LaunchedEffect
        // A focused event hidden by the filter brings the full list back first.
        fun StoryBlock.holds()=event?.key==target.key || draw?.events?.any{it.key==target.key}==true
        val visible=if(shown.any{it.holds()})shown else {filter=TimelineFilter.ALL;blocks}
        val index=visible.indexOfFirst{it.holds()}+2 // after the intro and filter items
        list.animateScrollToItem(index)
        detailKey=target.key // exact source is visible even inside a large historical period
        focusKey=null
    }
    fun open(event:PeriodEvent) {
        (event.source as? EventSource.Appointment)?.let{onAppointment(it.value.id)} ?: run{detailKey=event.key}
    }
    // UI-05 D: the two add actions moved to AppShell's top bar "+" menu; they are the same callbacks as the former buttons.
    addHost?.let{host->
        val addMilestone by rememberUpdatedState{onSaveHandled();edit=MilestoneEntity(date=LocalDate.now(zone).toString())}
        // §37b: a past period the import missed, or any period the user wants to state.
        val newPeriod by rememberUpdatedState{editPeriod(R.string.period_edit_title_create,emptyList(),null,TimelineEdits.Range(LocalDate.now(zone).minusDays(30),LocalDate.now(zone)),null,null)}
        // Registered once (stable lambdas reading the latest state), so composing never writes new state into the host.
        DisposableEffect(host){host.milestone={addMilestone()};host.period={newPeriod()};onDispose{host.milestone=null;host.period=null}}
        SideEffect{host.milestoneEnabled=!saveState.saving;host.periodEnabled=state.medications.isNotEmpty()}
    }
    // UI-05 D: events (and imported summaries) are drawn as the inset list of the group card above them: a period, the
    // upcoming card or the unknown card. Group membership is only the order of the unchanged projection's blocks.
    fun isChild(b:StoryBlock)=b.event!=null || b.draw!=null || b.imported!=null
    val groups=remember(shown){var group="";shown.map{b->if(!isChild(b))group=b.period?.key ?: b.key;group}}
    LazyColumn(Modifier.fillMaxSize().testTag("period-timeline"),state=list,contentPadding=PaddingValues(start=16.dp,end=16.dp,
        top=contentPadding.calculateTopPadding()+8.dp,bottom=contentPadding.calculateBottomPadding()+32.dp),verticalArrangement=Arrangement.Top) {
        item(key="intro") {
            // UI-01 E: the viewing zone is named only when it is not the phone's, by name rather than ID.
            TimelineZoneNote(zone)
            if(record.recognitionUnavailable)Text(stringResource(R.string.period_recognition_unavailable),modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite},style=MaterialTheme.typography.bodySmall)
            feedback?.let{Text(stringResource(it),modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})}
        }
        item(key="filters"){Box(Modifier.padding(top=12.dp)){TimelineFilterChips(filter){filter=it}}}
        itemsIndexed(shown,key={_,b->b.key}){index,block->
            val event=block.event;val period=block.period;val group=groups[index]
            val first=index==0 || !isChild(shown[index-1]);val last=index==shown.lastIndex || !isChild(shown[index+1])
            val withList=!isChild(block) && block.key!="upcoming" && index<shown.lastIndex && isChild(shown[index+1])
            val cardShape=if(withList)TopCardShape else CardDefaults.elevatedShape
            when {
                block.imported!=null->{val imported=block.imported
                    GroupChild(group,first,last) {
                        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable(role=androidx.compose.ui.semantics.Role.Button){importKey=imported.key}
                            .padding(horizontal=12.dp,vertical=8.dp).testTag("timeline:${imported.key}"),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            EventTypeBadge(EventKind.DOSE)
                            Column(Modifier.weight(1f)){
                                Text(stringResource(R.string.timeline_imported_history),style=MaterialTheme.typography.titleSmall)
                                Text(importedRange(imported,zone),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.timeline_imported_count,imported.records.size),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null)
                        }
                    }
                }
                event!=null->GroupChild(group,first,last){EventRow(event,::open)}
                // LAB-04: a panel opens its detail; a display-only group opens the labs page (it has no scroll-to-draw).
                block.draw!=null->{val d=block.draw;GroupChild(group,first,last){TimelineLabDrawRow(d){d.draw.panel?.let{onLabPanel(it.id)} ?: onOpen(EventKind.LAB)}}}
                period!=null->{
                    val current=period.contains(now)
                    val standards=projection.standards.filter{it.key in period.finalStandardSpanKeys}
                    val sourceRows=record.resolvedRecords.filter{r->(r.taken_utc ?: r.scheduled_utc)?.let{period.contains(Instant.ofEpochMilli(it))}==true}
                    val meds=TimelineEdits.medicationsIn(projection,period);val range=TimelineEdits.rangeOf(period,zone)
                    SectionCard(null,Modifier.padding(top=12.dp),shape=cardShape) {
                        // UI-01 E: title and ⋮ on one row. Details remain available without occupying every card; editing semantics are unchanged.
                        // UI-05 D: the period kind is a pill (current: filled primary).
                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                            val c=MaterialTheme.colorScheme
                            Box(Modifier.weight(1f)) {
                                Surface(color=if(current && standards.isNotEmpty())c.primary else c.secondaryContainer,contentColor=if(current && standards.isNotEmpty())c.onPrimary else c.onSecondaryContainer,
                                    shape=Corners.pill,modifier=Modifier.testTag("period-pill:${period.key}")) {
                                    Text(stringResource(if(standards.isEmpty())R.string.timeline_epoch_unknown else if(current)R.string.period_current else R.string.period_past),
                                        style=MaterialTheme.typography.labelLarge,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold,
                                        modifier=Modifier.padding(horizontal=8.dp,vertical=4.dp).semantics{heading()})
                                }
                            }
                            Box {
                                var menu by remember{mutableStateOf(false)}
                                IconButton(onClick={menu=true},modifier=Modifier.testTag("period-menu:${period.key}")){
                                    Icon(androidx.compose.material.icons.Icons.Outlined.MoreVert,stringResource(R.string.period_menu))}
                                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                                    @Composable fun item(text:Int,tag:String,action:()->Unit)=DropdownMenuItem(text={Text(stringResource(text))},
                                        modifier=Modifier.testTag("$tag:${period.key}"),onClick={menu=false;action()})
                                    item(R.string.period_menu_edit,"period-edit-any"){val m=meds.firstOrNull()
                                        editPeriod(R.string.period_edit_title_edit,meds,m,range,m?.let{TimelineEdits.standardOf(projection,period,it)},range,exact=period.from to period.until)}
                                    if(meds.isNotEmpty())item(R.string.period_menu_delete,"period-delete"){deleteFor=period}
                                    if(sourceRows.isNotEmpty())item(R.string.period_records,"period-history"){onImportedHistory(sourceRows.map{it.id})}
                                    if(!simple)item(R.string.period_saved_changes,"period-details"){auditKey=period.key}
                                }
                            }
                        }
                        // UI-05 D: "[from] – [to]", or "since [from] · day n" for the current period. Display only: the day count is
                        // from the period's own start date, nothing is written.
                        val end=period.until?.minusNanos(1)?.atZone(zone)?.toLocalDate();val from=formatDate(period.from.atZone(zone).toLocalDate())
                        // UI-07 C: "day n" in the stat style; it wraps under the date when both do not fit.
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        androidx.compose.foundation.layout.FlowRow(itemVerticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(if(end!=null)"$from – ${formatDate(end)}" else stringResource(R.string.period_since,from),style=MaterialTheme.typography.titleMedium.tnum(),
                                fontWeight=androidx.compose.ui.text.font.FontWeight.Bold,modifier=Modifier.testTag("period-since:${period.key}"))
                            if(current && standards.isNotEmpty())Text(stringResource(R.string.period_day_n,
                                java.time.temporal.ChronoUnit.DAYS.between(period.from.atZone(zone).toLocalDate(),now.atZone(zone).toLocalDate()).toInt()+1),
                                style=NotesTheme.typeExtra.statNumber,modifier=Modifier.testTag("period-day:${period.key}"))
                        }
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
                                confirmedRow(extra.historyPeriods,id)}
                            // UI-05 D: one medication per line, each after a small dot.
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.padding(top=8.dp).size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.primary))
                                Column{StandardSummary(saved?.let{RegimenDefinition.read(it.definition_json).snapshot(it.medication_id)} ?: confirmedRow?.let{MedicationSnapshot.decode(it.identity_json,it.medication_id)} ?: observed?.snapshot,span.standard)}
                            }
                            if(span.rawVersionIds.none{userEditRow(extra.historyPeriods,it)!=null} && observed!=null)
                                Text(stringResource(R.string.period_inferred),style=MaterialTheme.typography.bodySmall)
                            if(span.standard.kind!="OBSERVED" && span.standard.slotIdentityUnknown)Text(stringResource(R.string.period_slot_unknown),style=MaterialTheme.typography.bodySmall)
                        }
                        // §37a: short saved versions shown inside this period, with what they actually said.
                        val migratedShort=projection.raw.filter{it.kind==SpanKind.USER}.flatMap{part->
                            userEditRow(extra.historyPeriods,part.span.id)?.let{row->org.json.JSONObject(row.evidence_json).optJSONArray("short_saved")?.let{a->
                                (0 until a.length()).map{index->val e=a.getJSONObject(index);val id=e.getLong("source_id")
                                    RawTreatmentInterval(RegimenSpan(id,row.medication_id,Instant.ofEpochMilli(e.getLong("from_utc")),if(e.isNull("until_utc"))null else Instant.ofEpochMilli(e.getLong("until_utc")),false),
                                        extra.regimens.firstOrNull{it.id==id}?.let{RegimenDefinition.read(it.definition_json).therapyStandard()}
                                            ?: HistoryPeriods.readStandard(e.getJSONObject("standard").toString()),SpanKind.SAVED,id)}
                            }}.orEmpty()
                        }
                        (projection.raw.filter{r->r.kind==SpanKind.SAVED && projection.effective[r.span.id]?.let{it!=r.standard}==true}+migratedShort).distinctBy{it.sourceId}.filter{r->
                            r.span.from>=period.from && (period.until==null || r.span.from<period.until)}.forEach{r->
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
                                    modifier=Modifier.testTag("period-short-edit:${r.span.id}")){Text(stringResource(R.string.period_menu_edit))}
                                TextButton(onClick={shortDelete=r},modifier=Modifier.testTag("period-short-delete:${r.span.id}")){Text(stringResource(R.string.period_menu_delete))}
                            }
                        }
                        if(period.segments.size>1)Text(stringResource(R.string.period_same_day),style=MaterialTheme.typography.bodySmall)
                        if(standards.any{span->extra.regimens.any{it.id in span.rawVersionIds && it.origin=="LEGACY_RULE"}})Text(stringResource(R.string.epoch_reconstructed),style=MaterialTheme.typography.bodySmall)
                    }
                }
                block.key=="upcoming"->SectionCard(stringResource(R.string.period_upcoming),Modifier.padding(top=12.dp)){Text(stringResource(R.string.period_future_note),style=MaterialTheme.typography.bodySmall)}
                else->SectionCard(stringResource(R.string.timeline_epoch_unknown),Modifier.padding(top=12.dp),shape=cardShape){Text(stringResource(R.string.period_unknown_note),style=MaterialTheme.typography.bodySmall)}
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
    editRequest?.let{(request,save)->PeriodEditDialog(request,state,{m,r,t->request.overlapChanges(m,r,t).isNotEmpty()},save,
        onDismiss={editRequest=null})}
    deleteFor?.let{period->AlertDialog(onDismissRequest={deleteFor=null},text={Text(stringResource(R.string.period_delete_confirm))},modifier=Modifier.testTag("period-delete-dialog"),
        confirmButton={Button(onClick={deleteFor=null;TimelineEdits.delete(extra.historyPeriods,projection,period,::identity)?.let{timelineActions.delete(it.replace,it.rows)}}){Text(stringResource(R.string.period_menu_delete))}},
        dismissButton={TextButton(onClick={deleteFor=null}){Text(stringResource(R.string.cancel))}})}
    shortDelete?.let{r->AlertDialog(onDismissRequest={shortDelete=null},text={Text(stringResource(R.string.period_delete_confirm))},modifier=Modifier.testTag("period-short-delete-dialog"),
        confirmButton={Button(onClick={shortDelete=null;val range=TimelineEdits.daysOf(r.span.from,r.span.until,zone)
            timelineActions.delete(TimelineEdits.touched(extra.historyPeriods,r.span.medicationId,range),listOf(TimelineEditRow(HistoryPeriods.DELETED,r.span.medicationId,null,
                range.from,range.until,zone,identity(r.span.medicationId),exactFromUtc=r.span.from.toEpochMilli(),exactUntilUtc=r.span.until?.toEpochMilli())))}){Text(stringResource(R.string.period_menu_delete))}},
        dismissButton={TextButton(onClick={shortDelete=null}){Text(stringResource(R.string.cancel))}})}
    audit?.let{period->AlertDialog(onDismissRequest={auditKey=null},title={Text(stringResource(R.string.period_saved_changes))},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            projection.raw.filter{it.span.from<(period.until ?: Instant.MAX) && (it.span.until?.let{end->end>period.from} ?: true)}.forEach{raw->
                Text(formatDate(raw.span.from.atZone(zone).toLocalDate())+" · "+formatTime(raw.span.from.atZone(zone).toLocalTime())+" → "+
                    (raw.span.until?.atZone(zone)?.let{formatDate(it.toLocalDate())+" · "+formatTime(it.toLocalTime())} ?: stringResource(R.string.epoch_ongoing)))
                if(record.observed.any{it.interval.span.id==raw.span.id})Text(stringResource(R.string.period_inferred),style=MaterialTheme.typography.bodySmall)
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
    is EventSource.Intake->MedicationSnapshot.decode(source.record.config_snapshot,source.record.medication_id)?.name
        ?: stringResource(R.string.history_context_unknown)
    else->eventKindLabel(event.kind)
}
@Composable private fun EventRow(event:PeriodEvent,onOpen:(PeriodEvent)->Unit) {
    // UI-01 E / UI-05 D: a coloured type icon in front; the type is also in the words, so it is never colour or icon alone.
    // Two lines (type · name / date) and a chevron; the whole row opens the same detail as before.
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable(role=androidx.compose.ui.semantics.Role.Button){onOpen(event)}.padding(horizontal=12.dp,vertical=8.dp)
        .testTag("timeline:${event.key}"),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        EventTypeBadge(event.kind)
        Column(Modifier.weight(1f)) {
            Text(eventKindLabel(event.kind)+(if(LocalSimpleMode.current)"" else " · "+eventTitle(event)),style=MaterialTheme.typography.titleSmall)
            Text(formatDate(event.date),style=MaterialTheme.typography.bodySmall.tnum(),color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null)
    }
}
private val TopCardShape=androidx.compose.foundation.shape.RoundedCornerShape(topStart=24.dp,topEnd=24.dp)
/**
 * UI-05 D: one row of a group card's inset white list. The card's colour continues behind the list so the rows read as
 * part of the card above; upcoming rows are separate dashed cards instead. [group] is only a test hook for membership.
 */
@Composable private fun GroupChild(group:String,first:Boolean,last:Boolean,content:@Composable ()->Unit) {
    val c=MaterialTheme.colorScheme
    if(group=="upcoming") {
        val outline=c.outline
        Box(Modifier.padding(top=8.dp).fillMaxWidth().testTag("timeline-group:$group")) {
            Surface(color=c.surface,shape=Corners.list,modifier=Modifier.fillMaxWidth().testTag("timeline-upcoming-card").drawBehind{
                drawRoundRect(outline,cornerRadius=androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                    style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(),pathEffect=androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(),6.dp.toPx()))))
            }){content()}
        }
        return
    }
    val r=16.dp;val bottom=if(last)24.dp else 0.dp
    Box(Modifier.fillMaxWidth().testTag("timeline-group:$group").background(c.surfaceContainerLow,androidx.compose.foundation.shape.RoundedCornerShape(bottomStart=bottom,bottomEnd=bottom))
        .padding(start=12.dp,end=12.dp,bottom=if(last)12.dp else 0.dp)) {
        Surface(color=c.surfaceContainerLowest,shape=androidx.compose.foundation.shape.RoundedCornerShape(if(first)r else 0.dp,if(first)r else 0.dp,if(last)r else 0.dp,if(last)r else 0.dp)) {
            Column { content(); if(!last)HorizontalDivider(Modifier.padding(start=60.dp),color=c.outlineVariant) }
        }
    }
}
/** UI-02 A1: the timeline's type filter. OTHER is every kind not named by another chip. */
enum class TimelineFilter(val label:Int) {
    ALL(R.string.timeline_filter_all),LAB(R.string.timeline_filter_labs),VISIT(R.string.timeline_filter_visits),
    MILESTONE(R.string.timeline_filter_milestones),OTHER(R.string.timeline_filter_other);
    fun matches(kind:EventKind)=when(this){ALL->true;LAB->kind==EventKind.LAB;VISIT->kind==EventKind.APPOINTMENT;MILESTONE->kind==EventKind.MILESTONE
        OTHER->kind!=EventKind.LAB && kind!=EventKind.APPOINTMENT && kind!=EventKind.MILESTONE}
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun TimelineFilterChips(selected:TimelineFilter,onSelect:(TimelineFilter)->Unit) {
    FlowRow(Modifier.fillMaxWidth().testTag("timeline-filters"),horizontalArrangement=Arrangement.spacedBy(Spacing.s)) {
        TimelineFilter.entries.forEach{f->
            FilterChip(selected==f,{onSelect(f)},{Text(stringResource(f.label))},
                Modifier.heightIn(min=48.dp).semantics{this.selected=selected==f}.testTag("timeline-filter:${f.name}"))
        }
    }
}
@Composable internal fun TimelineZoneNote(zone:ZoneId,device:ZoneId=ZoneId.systemDefault()) {
    if(zone.rules!=device.rules)Text(stringResource(R.string.timeline_shown_in,zone.getDisplayName(java.time.format.TextStyle.FULL,currentLocale())),
        style=MaterialTheme.typography.labelMedium,modifier=Modifier.testTag("timeline-zone"))
}
/** Icon for every [EventKind]; decorative next to the kind's name (UI-01 E). */
internal fun eventKindIcon(kind:EventKind):androidx.compose.ui.graphics.vector.ImageVector=when(kind){
    EventKind.REGIMEN->Icons.Outlined.Medication;EventKind.DOSE->Icons.Outlined.CheckCircle;EventKind.MISSED->Icons.Outlined.Cancel
    EventKind.UNCONFIRMED->Icons.AutoMirrored.Outlined.HelpOutline;EventKind.SKIPPED->Icons.Outlined.SkipNext;EventKind.LAB->Icons.Outlined.Science
    EventKind.SYMPTOM->Icons.Outlined.Healing;EventKind.WELLBEING->Icons.Outlined.Mood;EventKind.REVIEW->Icons.Outlined.RateReview
    EventKind.APPOINTMENT->Icons.Outlined.Event;EventKind.MILESTONE->Icons.Outlined.Flag;EventKind.PLANNED->Icons.Outlined.Schedule
}
/**
 * UI-05 D: one colour per type group (lab, visit, milestone, stage review, other), each a container/on-container pair so the icon keeps
 * ≥ 3:1 in light, dark and every contrast level (Ui05TimelineTest). UI-06: a missed dose uses the neutral missed token (never red).
 * UI-13 (2026-10-30): the type colours are the small accents — lab purple, visit blue (owner swap 2026-10-30), stage review rose, milestone teal — so coral
 * (tertiary) is only the reminder colour (low stock, past time) and no longer also a type colour.
 */
internal fun eventTypeColors(kind:EventKind,c:ColorScheme,sem:SemanticColors=semanticColors(c,c.surface.luminance()<0.5f),
                             acc:AccentColors=accentColors(c,c.surface.luminance()<0.5f))=when(kind){
    EventKind.LAB->acc.purple.container to acc.purple.onContainer;EventKind.APPOINTMENT->acc.blue.container to acc.blue.onContainer
    EventKind.MILESTONE->c.primaryContainer to c.onPrimaryContainer;EventKind.REVIEW->acc.rose.container to acc.rose.onContainer
    EventKind.MISSED->sem.missed.container to sem.missed.onContainer;else->c.surfaceVariant to c.onSurfaceVariant}
@Composable internal fun EventTypeBadge(kind:EventKind) {
    // UI-07 A: a missed dose is the same status icon as everywhere else; the row's text names the type.
    if(kind==EventKind.MISSED){Box(Modifier.size(36.dp).testTag("event-badge:${kind.name}"),contentAlignment=androidx.compose.ui.Alignment.Center){StatusIcon(StatusIconKind.MISSED,StatusIconSizes.L)};return}
    val (bg,fg)=eventTypeColors(kind,MaterialTheme.colorScheme,NotesTheme.semantic,NotesTheme.accent)
    Surface(shape=androidx.compose.foundation.shape.CircleShape,color=bg,contentColor=fg,modifier=Modifier.size(36.dp).testTag("event-badge:${kind.name}")){
        Box(contentAlignment=androidx.compose.ui.Alignment.Center){Icon(eventKindIcon(kind),null,Modifier.size(20.dp))}}
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
                is EventSource.Intake->{
                    val r=s.record
                    val snapshot=MedicationSnapshot.decode(r.config_snapshot,r.medication_id)
                    Text(snapshot?.name ?: stringResource(R.string.history_context_unknown))
                    r.planned_dose?.let{dose->Text(stringResource(R.string.history_planned_dose_label,formatDose(dose,snapshot?.unit)))}
                }
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
@Composable fun eventKindLabel(kind:EventKind)=stringResource(eventKindLabelRes(kind))
internal fun eventKindLabelRes(kind:EventKind)=when(kind){
    EventKind.REGIMEN->R.string.timeline_regimen;EventKind.DOSE->R.string.timeline_dose;EventKind.MISSED->R.string.timeline_missed
    EventKind.UNCONFIRMED->R.string.status_unconfirmed;EventKind.SKIPPED->R.string.timeline_skipped;EventKind.LAB->R.string.labs
    EventKind.SYMPTOM->R.string.wb_symptoms;EventKind.WELLBEING->R.string.wellbeing;EventKind.REVIEW->R.string.wb_reviews
    EventKind.APPOINTMENT->R.string.appointment;EventKind.MILESTONE->R.string.milestone;EventKind.PLANNED->R.string.timeline_planned
}
@Composable internal fun milestoneKindLabel(kind:String)=stringResource(when(kind){"STARTED"->R.string.milestone_started;"ROUTE"->R.string.milestone_route;"SURGERY"->R.string.appt_surgery;"PAUSED"->R.string.milestone_paused;"STOPPED"->R.string.milestone_stopped;"RESUMED"->R.string.milestone_resumed;else->R.string.milestone_custom})
@Composable private fun MilestoneDialog(initial:MilestoneEntity,saveState:MilestoneSaveState,onDismiss:()->Unit,onSave:(MilestoneEntity)->Unit) {
    var dateText by rememberSaveable(initial.id){mutableStateOf(initial.date)};val date=LocalDate.parse(dateText);var kind by rememberSaveable(initial.id){mutableStateOf(initial.kind)}
    var title by rememberSaveable(initial.id){mutableStateOf(initial.title.orEmpty())};var note by rememberSaveable(initial.id){mutableStateOf(initial.note.orEmpty())};var pick by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(if(initial.id==0L)R.string.milestone_add else R.string.edit))},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
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

private data class StoryBlock(val key:String,val period:DisplayPeriod?=null,val event:PeriodEvent?=null,val imported:ImportedHistorySummary?=null,val draw:TimelineLabDraw?=null)

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
        LazyColumn(Modifier.heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
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

/** UI-05 D: the timeline's two add actions for AppShell's top bar; the timeline registers its own original callbacks here. */
class TimelineAddHost {
    var milestone by mutableStateOf<(()->Unit)?>(null); var milestoneEnabled by mutableStateOf(true)
    var period by mutableStateOf<(()->Unit)?>(null); var periodEnabled by mutableStateOf(false)
}
/** The top bar "+": "Add milestone" and "New period", enabled exactly as the former buttons were. */
@Composable fun TimelineAddMenu(host:TimelineAddHost) {
    var open by remember{mutableStateOf(false)}
    Box {
        IconButton({open=true},Modifier.testTag("timeline-add")){Icon(Icons.Outlined.Add,stringResource(R.string.add))}
        DropdownMenu(open,{open=false}) {
            DropdownMenuItem({Text(stringResource(R.string.milestone_add))},{open=false;host.milestone?.invoke()},enabled=host.milestone!=null && host.milestoneEnabled,
                modifier=Modifier.testTag("timeline-add-milestone"))
            DropdownMenuItem({Text(stringResource(R.string.timeline_new_period))},{open=false;host.period?.invoke()},enabled=host.period!=null && host.periodEnabled,
                modifier=Modifier.testTag("timeline-new-period"))
        }
    }
}
