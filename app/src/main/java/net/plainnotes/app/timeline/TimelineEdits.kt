package net.plainnotes.app.timeline

import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import java.time.*

/**
 * REQUIREMENTS §37b: what each edit writes. Pure functions over the shown timeline; the repository appends the rows
 * under one group key. Ranges are local days in the timeline zone, `until` exclusive.
 */
object TimelineEdits {
    data class Range(val from:LocalDate,val until:LocalDate?) {
        fun instants(zone:ZoneId)=from.atStartOfDay(zone).toInstant() to until?.atStartOfDay(zone)?.toInstant()
    }
    data class Edit(val replace:List<String>,val rows:List<TimelineEditRow>)

    /** The days a display period covers; a period shorter than a day still covers its whole day. */
    fun rangeOf(period:DisplayPeriod,zone:ZoneId):Range=daysOf(period.from,period.until,zone)
    private fun overlaps(a:Instant,b:Instant?,c:Instant,d:Instant?)=a<(d ?: Instant.MAX) && c<(b ?: Instant.MAX)

    /** Medicines with a plan part inside [period] (entry IDs as stored, not lanes). */
    fun medicationsIn(p:TreatmentPeriodProjection,period:DisplayPeriod)=
        (p.raw.filter{overlaps(it.span.from,it.span.until,period.from,period.until)}.map{it.span.medicationId}+
            p.stops.filter{overlaps(it.from,it.until,period.from,period.until)}.map{it.medicationId}).distinct().sorted()

    /** The standard shown for [med] in [period]: its standard span there, or null when it has none. */
    fun standardOf(p:TreatmentPeriodProjection,period:DisplayPeriod,med:Long):TherapyStandard? {
        val ids=p.raw.filter{it.span.medicationId==med && overlaps(it.span.from,it.span.until,period.from,period.until)}.map{it.span.id}.toSet()
        return p.standards.filter{s->s.rawVersionIds.any{it in ids}}.maxByOrNull{s->(s.until ?: Instant.MAX).coerceAtMost(period.until ?: Instant.MAX).toEpochMilli()-maxOf(s.from,period.from).toEpochMilli()}?.standard
    }

    /** Active user edits of [med] that the new range would touch; an edit replaces them. */
    fun touched(rows:List<HistoryPeriodEntity>,med:Long,range:Range)=HistoryPeriods.userEdits(rows).filter{r->r.medication_id==med &&
        (range.until==null || LocalDate.parse(r.from_date)<range.until) && (r.until_date==null || range.from<LocalDate.parse(r.until_date))}.map{it.period_key}

    /**
     * §37b rule c: true when [range] would overlap a plan of the same medicine outside [target] (the period being
     * edited or merged). Other medicines never conflict.
     */
    fun conflicts(p:TreatmentPeriodProjection,med:Long,range:Range,target:Range?):Boolean {
        val (from,until)=range.instants(p.zone);val t=target?.instants(p.zone)
        return p.raw.filter{it.span.medicationId==med}.any{r->
            var parts=listOf(r.span.from to (r.span.until ?: Instant.MAX))
            t?.let{(a,b)->parts=parts.flatMap{(x,y)->val bb=b ?: Instant.MAX;if(a>=y || bb<=x)listOf(x to y) else buildList{if(x<a)add(x to a);if(bb<y)add(bb to y)}}}
            parts.any{(x,y)->x<y && overlaps(x,y.takeIf{it!=Instant.MAX},from,until)}
        }
    }

    /** [exact]: exact bounds inside [range] (§38), for an edit of one short saved version. */
    fun period(rows:List<HistoryPeriodEntity>,med:Long,standard:TherapyStandard,range:Range,zone:ZoneId,identity:String,also:List<Range> = emptyList(),
               exact:Pair<Instant,Instant?>?=null)=
        Edit((listOf(range)+also).flatMap{touched(rows,med,it)}.distinct(),listOf(TimelineEditRow(HistoryPeriods.PERIOD,med,standard,range.from,range.until,zone,identity,
            exactFromUtc=exact?.first?.toEpochMilli(),exactUntilUtc=exact?.second?.toEpochMilli())))
    /** §38: the days an exact part lies in, for the dates of an exact edit. */
    fun daysOf(from:Instant,until:Instant?,zone:ZoneId):Range {
        val a=from.atZone(zone).toLocalDate()
        return Range(a,until?.let{u->u.atZone(zone).let{z->if(z.toLocalTime()==LocalTime.MIDNIGHT)z.toLocalDate() else z.toLocalDate().plusDays(1)}.let{if(it<=a)a.plusDays(1) else it}})
    }


    private fun restoration(r:HistoryPeriodEntity,from:Instant,until:Instant?)=org.json.JSONObject()
        .put("period_key",r.period_key).put("medication_id",r.medication_id).put("from_utc",from.toEpochMilli()).put("until_utc",until?.toEpochMilli() ?: org.json.JSONObject.NULL)
        .put("standard",org.json.JSONObject(r.standard_json)).put("identity",org.json.JSONObject(r.identity_json))
    private fun restoreEvidence(rows:List<org.json.JSONObject>)=org.json.JSONObject().put("restore_rows",org.json.JSONArray(rows)).toString()

    /** §40: the exact remaining pieces after an overlay, never rounded to the display day. */
    private fun subtract(a:Instant,b:Instant?,f:Instant,u:Instant?):List<Pair<Instant,Instant?>> {
        val end=b ?: Instant.MAX;val last=u ?: Instant.MAX
        return if(f>=end || last<=a)listOf(a to b) else buildList {
            if(a<f)add(a to f);if(last<end)add(last to b)
        }
    }
    data class OverlapChange(val from:Instant,val until:Instant?,val remaining:List<Pair<Instant,Instant?>>)
    private fun lane(p:TreatmentPeriodProjection,med:Long)=TreatmentPeriods.medicationLanes(p.raw)[med] ?: med
    /** Shown standards outside the editing target, for the selected medicine only. */
    private fun peers(p:TreatmentPeriodProjection,med:Long,target:Pair<Instant,Instant?>?)=
        p.standards.filter{it.medicationId==lane(p,med)}.flatMap{s->
            (target?.let{(a,b)->subtract(s.from,s.until,a,b)} ?: listOf(s.from to s.until)).map{(a,b)->s.copy(from=a,until=b)}}
    fun overlapChanges(p:TreatmentPeriodProjection,med:Long,range:Range,target:Range?,exactTarget:Pair<Instant,Instant?>?=null,exactNew:Pair<Instant,Instant?>?=null):List<OverlapChange> {
        val (f,u)=exactNew ?: range.instants(p.zone)
        return peers(p,med,exactTarget ?: target?.instants(p.zone)).filter{overlaps(it.from,it.until,f,u)}.map{s->OverlapChange(s.from,s.until,subtract(s.from,s.until,f,u))}
    }
    private fun rowAt(kind:String,med:Long,s:TherapyStandard?,from:Instant,until:Instant?,zone:ZoneId,identity:String,replaces:List<String>?=null,trash:Boolean=false):TimelineEditRow {
        val dates=daysOf(from,until,zone)
        return TimelineEditRow(kind,med,s,dates.from,dates.until,zone,identity,exactFromUtc=from.toEpochMilli(),exactUntilUtc=until?.toEpochMilli(),replaces=replaces,trash=trash)
    }
    /**
     * §40: call only after the overlap preview was accepted. Target leftovers expose the system; affected neighbours
     * become fixed user periods, fully covered neighbours each get their own recycle-bin item.
     */
    fun saveV2(rows:List<HistoryPeriodEntity>,p:TreatmentPeriodProjection,med:Long,standard:TherapyStandard,range:Range,identity:String,
               target:Range?=null,exactTarget:Pair<Instant,Instant?>?=null,exactNew:Pair<Instant,Instant?>?=null):Edit {
        val zone=p.zone;val (f,u)=exactNew ?: range.instants(zone);val t=exactTarget ?: target?.instants(zone)
        val sameLane=TreatmentPeriods.medicationLanes(p.raw)
        val users=HistoryPeriods.userEdits(rows).filter{it.kind==HistoryPeriods.PERIOD && (sameLane[it.medication_id] ?: it.medication_id)==lane(p,med)}
        fun bounds(row:HistoryPeriodEntity):Pair<Instant,Instant?> {
            val shown=p.raw.firstOrNull{userEditRow(rows,it.span.id)?.period_key==row.period_key}
            if(shown!=null)return shown.span.from to shown.span.until
            val exact=HistoryPeriods.exactBounds(row);return if(exact!=null)Instant.ofEpochMilli(exact.first) to exact.second?.let(Instant::ofEpochMilli)
                else Range(LocalDate.parse(row.from_date),row.until_date?.let(LocalDate::parse)).instants(ZoneId.of(row.zone))
        }
        val replaced=users.filter{r->val (a,b)=bounds(r);overlaps(a,b,f,u) || t?.let{(x,y)->overlaps(a,b,x,y)}==true}
        val out=mutableListOf<TimelineEditRow>()
        // Only outside the target survives from its old user revision. A shorter replacement re-exposes system facts.
        replaced.forEach{r->val (a,b)=bounds(r);var parts=listOf(a to b)
            t?.let{(x,y)->parts=parts.flatMap{(c,d)->subtract(c,d,x,y)}}
            parts=parts.flatMap{(c,d)->subtract(c,d,f,u)}
            parts.forEach{(c,d)->out+=rowAt(HistoryPeriods.PERIOD,r.medication_id,HistoryPeriods.readStandard(r.standard_json),c,d,zone,r.identity_json,replaces=emptyList())}}
        peers(p,med,t).filter{overlaps(it.from,it.until,f,u)}.forEach{s->
            val remains=subtract(s.from,s.until,f,u)
            if(remains.isEmpty()) {
                val keys=replaced.filter{r->val (a,b)=bounds(r);a>=s.from && (b ?: Instant.MAX)<=(s.until ?: Instant.MAX)}.map{it.period_key}
                val back=replaced.filter{it.period_key in keys}.map{row->val (a,b)=bounds(row);restoration(row,a,b)}
                out+=rowAt(HistoryPeriods.DELETED,med,null,s.from,s.until,zone,identity,keys,true).copy(evidenceJson=restoreEvidence(back))
            } else {
                // Existing user remainders were emitted above; save only the system neighbour's remaining pieces.
                val source=p.raw.filter{it.span.id in s.rawVersionIds}
                if(source.none{it.kind==SpanKind.USER})remains.forEach{(a,b)->out+=rowAt(HistoryPeriods.PERIOD,med,s.standard,a,b,zone,identity,replaces=emptyList())}
            }
        }
        out+=rowAt(HistoryPeriods.PERIOD,med,standard,f,u,zone,identity,replaces=emptyList())
        return Edit(replaced.map{it.period_key},out)
    }


    /** Marks every medicine of [period] (or [only]) as deleted over the period's days; records are never touched. */
    fun delete(rows:List<HistoryPeriodEntity>,p:TreatmentPeriodProjection,period:DisplayPeriod,identity:(Long)->String,only:Long?=null):Edit? {
        val meds=only?.let(::listOf) ?: medicationsIn(p,period)
        if(meds.isEmpty())return null
        val out=mutableListOf<TimelineEditRow>();val replaced=mutableListOf<String>()
        meds.forEach{m->
            val users=HistoryPeriods.userEdits(rows).filter{it.kind==HistoryPeriods.PERIOD && it.medication_id==m}.filter{row->
                val exact=HistoryPeriods.exactBounds(row);val (a,b)=if(exact!=null)Instant.ofEpochMilli(exact.first) to exact.second?.let(Instant::ofEpochMilli)
                    else Range(LocalDate.parse(row.from_date),row.until_date?.let(LocalDate::parse)).instants(ZoneId.of(row.zone))
                if(!overlaps(a,b,period.from,period.until))false else {
                    replaced+=row.period_key
                    subtract(a,b,period.from,period.until).forEach{(f,u)->out+=rowAt(HistoryPeriods.PERIOD,m,HistoryPeriods.readStandard(row.standard_json),f,u,p.zone,row.identity_json,replaces=emptyList())}
                    true
                }
            }
            val back=users.map{row->
                val shown=p.raw.firstOrNull{userEditRow(rows,it.span.id)?.period_key==row.period_key}
                restoration(row,maxOf(shown?.span?.from ?: period.from,period.from),listOfNotNull(shown?.span?.until,period.until).minOrNull())}
            out+=rowAt(HistoryPeriods.DELETED,m,null,period.from,period.until,p.zone,identity(m),users.map{it.period_key}).copy(evidenceJson=restoreEvidence(back))
        }
        return Edit(replaced,out)
    }


    /** User edit rows shown in [period] (their group can be undone; a DELETED one can be restored). */
    fun userRowsIn(rows:List<HistoryPeriodEntity>,period:DisplayPeriod,zone:ZoneId)=HistoryPeriods.userEdits(rows).filter{r->
        val (from,until)=Range(LocalDate.parse(r.from_date),r.until_date?.let(LocalDate::parse)).instants(ZoneId.of(r.zone))
        overlaps(from,until,period.from,period.until)
    }
}
