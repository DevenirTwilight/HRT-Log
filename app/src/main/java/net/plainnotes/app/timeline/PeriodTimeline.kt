package net.plainnotes.app.timeline

import net.plainnotes.app.NotesViewModel.ExtraState
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import java.time.*

data class PeriodEvent(val key:String,val kind:EventKind,val at:Instant?,val date:LocalDate,val source:EventSource,
    val displayPeriodKey:String?,val exactRegimenIds:Set<Long>,val dateOnly:Boolean)
data class PeriodTimeline(val projection:TreatmentPeriodProjection,val events:List<PeriodEvent>,val upcoming:List<PeriodEvent>,
    val importedHistory:List<ImportedHistorySummary> = emptyList(),val observed:List<ObservedTreatment> = emptyList(),
    val resolvedRecords:List<RecordEntity> = emptyList(),val recognitionUnavailable:Boolean = false,
    val coverage:Map<Long,RecordCoverage> = emptyMap(),val confirmedHistory:List<HistoryPeriodEntity> = emptyList()) {
    fun eventsIn(period:DisplayPeriod)=events.filter{it.displayPeriodKey==period.key}
    val unknownEvents get()=events.filter{it.displayPeriodKey==null}
}
/** Invalid derived intervals must not take the confirmed plans and original history off screen. */
internal data class PeriodHistory(val projection:TreatmentPeriodProjection,val historical:HistoricalTreatmentProjection,val unavailable:Boolean)
internal fun projectHistory(historical:HistoricalTreatmentProjection,zone:ZoneId,withRecords:Set<Long>?=null,legacy:Boolean=false):PeriodHistory =
    try {
        PeriodHistory(TreatmentPeriods.build(historical.saved+historical.user+historical.confirmed+historical.observed.map{it.interval},zone,withRecords,historical.userStops,historical.markers,protectUserBoundaries=!legacy),historical,false)
    } catch (_:IllegalArgumentException) {
        PeriodHistory(TreatmentPeriods.build(historical.saved+historical.user,zone,withRecords,historical.userStops,historical.markers,protectUserBoundaries=!legacy),
            HistoricalTreatmentProjection(emptyList(),emptySet(),saved=historical.saved,user=historical.user,userStops=historical.userStops,markers=historical.markers),true)
    }

/** Saved plan versions with at least one record (taken, missed or skipped) of their medication inside their span. */
fun recordsInside(versions:List<RegimenVersionEntity>,records:List<RecordEntity>):Map<Long,Int> = versions.associate{v->
    v.id to records.count{r->r.deleted_at_utc==null && r.medication_id==v.medication_id &&
        (r.taken_utc ?: r.scheduled_utc)?.let{t->t>=v.effective_from_utc && (v.effective_until_utc?.let{u->t<u} ?: true)}==true}
}

/**
 * REQUIREMENTS §36: a recognised part lying between confirmed or saved parts of one merged period (for example the
 * hours between a confirmed period ending on 10-06 and a plan saved at noon) is judged by the part before it.
 * A recognised part at either end of a period stays "to confirm".
 */
internal fun mergedCoverage(projection:TreatmentPeriodProjection,historical:HistoricalTreatmentProjection):Map<Long,RecordCoverage> {
    val rawById=projection.raw.associateBy{it.span.id}
    val remap=mutableMapOf<Long,RawTreatmentInterval>()
    projection.standards.forEach{span->
        val members=span.rawVersionIds.mapNotNull(rawById::get).sortedBy{it.span.from}
        val anchors=members.filter{it.kind!=SpanKind.OBSERVED}
        members.filter{it !in anchors}.forEach{m->
            val before=anchors.lastOrNull{it.span.from<m.span.from}
            if(before!=null && anchors.any{it.span.from>m.span.from})remap[m.span.id]=before
        }
    }
    val observedOf=historical.observed.flatMap{o->o.records.map{it.id to o.interval.span.id}}.toMap()
    // §36c/37a: a corrected or absorbed saved version is judged by the standard it is shown with.
    fun shown(c:RecordCoverage)=c.interval?.let{i->projection.effective[i.span.id]?.takeIf{it!=i.standard}?.let{RecordCoverage(i.copy(standard=it),false)}} ?: c
    return historical.coverage.mapValues{(id,c)->
        val result=shown(if(c.pending)observedOf[id]?.let(remap::get)?.let{RecordCoverage(it,false)} ?: c else c)
        result.interval?.let{i->projection.standards.firstOrNull{i.span.id in it.rawVersionIds}?.let{s->
            RecordCoverage(i.copy(span=i.span.copy(id=s.rawVersionIds.first()),standard=s.standard),false)}} ?: result
    }
}

/** Active user edits as projection inputs (REQUIREMENTS §37b); span IDs are display-only negatives. */
fun userEditInputs(rows:List<HistoryPeriodEntity>):List<UserEditInput> = HistoryPeriods.userEdits(rows).sortedBy{it.id}.mapNotNull{row->
    runCatching {
        val zone=ZoneId.of(row.zone)
        val exact=HistoryPeriods.exactBounds(row)
        UserEditInput(ObservedTreatmentHistory.USER_SPAN_BASE-HistoryPeriods.stableId(rows,row.period_key)*100,row.period_key,row.group_key,row.kind,row.medication_id,
            exact?.first?.let(Instant::ofEpochMilli) ?: LocalDate.parse(row.from_date).atStartOfDay(zone).toInstant(),
            if(exact!=null)exact.second?.let(Instant::ofEpochMilli) else row.until_date?.let{LocalDate.parse(it).atStartOfDay(zone).toInstant()},
            if(row.kind in listOf(HistoryPeriods.PERIOD,HistoryPeriods.FILL))HistoryPeriods.readStandard(row.standard_json) else null,MedicationSnapshot.decode(row.identity_json,row.medication_id),org.json.JSONObject(row.evidence_json).let{if(it.has("stated_utc"))it.getLong("stated_utc") else null},org.json.JSONObject(row.evidence_json).optInt("migration")==25)
    }.getOrNull()
}
/** The user edit row behind a display span ID, if any. */
fun userEditRow(rows:List<HistoryPeriodEntity>,spanId:Long)=if(ObservedTreatmentHistory.isUserSpan(spanId))HistoryPeriods.userEdits(rows).firstOrNull{ObservedTreatmentHistory.USER_SPAN_BASE-HistoryPeriods.stableId(rows,it.period_key)*100==spanId} else null
/** The confirmed period behind a display span ID (pieces of one period share its base). */
fun confirmedRow(rows:List<HistoryPeriodEntity>,spanId:Long)=if(ObservedTreatmentHistory.isConfirmedSpan(spanId))HistoryPeriods.confirmed(rows).firstOrNull{
    val base=ObservedTreatmentHistory.CONFIRMED_SPAN_BASE-HistoryPeriods.stableId(rows,it.period_key)*100;spanId<=base && spanId>base-100} else null

/** Current confirmed past periods as projection inputs; span IDs are display-only negatives, never stored or frozen. */
fun confirmedPeriodInputs(rows:List<HistoryPeriodEntity>):List<ConfirmedPeriodInput> = HistoryPeriods.confirmed(rows).sortedBy{it.id}.mapNotNull{row->
    runCatching {
        val zone=ZoneId.of(row.zone)
        ConfirmedPeriodInput(ObservedTreatmentHistory.CONFIRMED_SPAN_BASE-HistoryPeriods.stableId(rows,row.period_key)*100,row.period_key,row.medication_id,
            LocalDate.parse(row.from_date).atStartOfDay(zone).toInstant(),row.until_date?.let{LocalDate.parse(it).atStartOfDay(zone).toInstant()},
            HistoryPeriods.readStandard(row.standard_json),MedicationSnapshot.decode(row.identity_json,row.medication_id))
    }.getOrNull()
}

object PeriodTimelineProjection {
    fun build(extra:ExtraState,appointments:List<AppointmentEntity>,now:Instant=Instant.now(),displayZone:ZoneId?=null,ruleSnapshots:Map<Long,String> = emptyMap(),legacy:Boolean=false):PeriodTimeline {
        val ordered=extra.regimens.sortedWith(compareBy({it.effective_from_utc},{it.id}))
        // Deterministic display policy: changing the device zone must not regroup historical transitions.
        val zone=displayZone ?: ordered.firstOrNull()?.let{ZoneId.of(it.zone)} ?: ZoneId.of("UTC")
        val inside=recordsInside(ordered,extra.records).filterValues{it>0}.keys
        val result=projectHistory(ObservedTreatmentHistory.build(extra.records,ordered,zone,now,ruleSnapshots,confirmedPeriodInputs(extra.historyPeriods),
            userEditInputs(extra.historyPeriods),legacy),zone,inside,legacy)
        val historical=result.historical;val projection=result.projection
        val today=now.atZone(zone).toLocalDate();val events=mutableListOf<PeriodEvent>();val upcoming=mutableListOf<PeriodEvent>()
        fun add(key:String,kind:EventKind,at:Instant?,day:LocalDate?,source:EventSource) {
            val date=day ?: requireNotNull(at).atZone(zone).toLocalDate()
            val future=if(at!=null)at>now else date>today
            val period=if(future)null else if(at!=null)projection.periodAt(at) else projection.periodOn(date)
            val event=PeriodEvent(key,kind,at,date,source,period?.key,
                if(at!=null && !future)projection.exactAt(at).filter{it.kind==SpanKind.SAVED}.map{it.sourceId}.toSet() else emptySet(),at==null)
            if(future)upcoming+=event else events+=event
        }
        extra.labs.forEach{add("lab:${it.id}",EventKind.LAB,Instant.ofEpochMilli(it.sampled_utc),null,EventSource.Lab(it))}
        extra.reviews.forEach{add("review:${it.id}",EventKind.REVIEW,null,LocalDate.parse(it.date),EventSource.Review(it))}
        extra.milestones.forEach{add("milestone:${it.id}",EventKind.MILESTONE,null,LocalDate.parse(it.date),EventSource.Milestone(it))}
        appointments.forEach{add("appointment:${it.id}",EventKind.APPOINTMENT,Instant.ofEpochMilli(it.at_utc),null,EventSource.Appointment(it))}
        val order=compareByDescending<PeriodEvent>{it.date}.thenByDescending{it.at}.thenBy{it.key}
        return PeriodTimeline(projection,events.sortedWith(order),upcoming.sortedWith(compareBy<PeriodEvent>{it.date}.thenBy{it.at}.thenBy{it.key}),
            ImportedHistoryProjection.build(extra.records.filter{it.id !in historical.resolvedRecordIds},projection,now),historical.observed,
            extra.records.filter{it.id in historical.resolvedRecordIds},result.unavailable,mergedCoverage(projection,historical),HistoryPeriods.confirmed(extra.historyPeriods))
    }
}
