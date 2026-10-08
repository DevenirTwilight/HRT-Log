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
    fun rangeOf(period:DisplayPeriod,zone:ZoneId):Range {
        val from=period.from.atZone(zone).toLocalDate()
        // A period ending during a day leaves that day to the next period; a period within one day keeps its day.
        val until=period.until?.let{u->u.atZone(zone).toLocalDate().let{d->if(d<=from)from.plusDays(1) else d}}
        return Range(from,until)
    }
    private fun overlaps(a:Instant,b:Instant?,c:Instant,d:Instant?)=a<(d ?: Instant.MAX) && c<(b ?: Instant.MAX)

    /** Medicines with a plan part inside [period] (entry IDs as stored, not lanes). */
    fun medicationsIn(p:TreatmentPeriodProjection,period:DisplayPeriod)=
        p.raw.filter{overlaps(it.span.from,it.span.until,period.from,period.until)}.map{it.span.medicationId}.distinct().sorted()

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

    /** Splits each medicine of [period] at [day]; both halves keep the shown standard and their own bounds. */
    fun split(rows:List<HistoryPeriodEntity>,p:TreatmentPeriodProjection,period:DisplayPeriod,day:LocalDate,identity:(Long)->String):Edit? {
        val range=rangeOf(period,p.zone);if(day<=range.from || (range.until!=null && day>=range.until))return null
        val meds=medicationsIn(p,period).mapNotNull{m->standardOf(p,period,m)?.let{m to it}};if(meds.isEmpty())return null
        return Edit(meds.flatMap{(m,_)->touched(rows,m,range)}.distinct(),meds.flatMap{(m,s)->listOf(
            TimelineEditRow(HistoryPeriods.PERIOD,m,s,range.from,day,p.zone,identity(m)),TimelineEditRow(HistoryPeriods.PERIOD,m,s,day,range.until,p.zone,identity(m)))})
    }

    /** Marks every medicine of [period] (or [only]) as deleted over the period's days; records are never touched. */
    fun delete(rows:List<HistoryPeriodEntity>,p:TreatmentPeriodProjection,period:DisplayPeriod,identity:(Long)->String,only:Long?=null):Edit? {
        val range=rangeOf(period,p.zone);val meds=(only?.let(::listOf) ?: medicationsIn(p,period));if(meds.isEmpty())return null
        return Edit(meds.flatMap{touched(rows,it,range)}.distinct(),meds.map{TimelineEditRow(HistoryPeriods.DELETED,it,null,range.from,range.until,p.zone,identity(it))})
    }

    /**
     * Moves a stop: the new stop range, plus fills where the old stop no longer applies (the plan before continues up to
     * the new start, the plan after starts at the new end).
     */
    fun moveStop(rows:List<HistoryPeriodEntity>,p:TreatmentPeriodProjection,stop:TreatmentStop,med:Long,new:Range,identity:String):Edit {
        val zone=p.zone;val old=Range(stop.from.atZone(zone).toLocalDate(),stop.until?.atZone(zone)?.toLocalDate())
        val before=p.standards.filter{it.medicationId==stop.medicationId && it.until?.let{u->u<=stop.from}==true}.maxByOrNull{it.until!!}?.standard
        val after=stop.until?.let{u->p.standards.filter{it.medicationId==stop.medicationId && it.from>=u}.minByOrNull{it.from}?.standard}
        val out=mutableListOf(TimelineEditRow(HistoryPeriods.STOP,med,null,new.from,new.until,zone,identity))
        if(new.from>old.from && before!=null)out+=TimelineEditRow(HistoryPeriods.FILL,med,before,old.from,new.from,zone,identity)
        if(old.until!=null && new.until!=null && new.until<old.until && after!=null)out+=TimelineEditRow(HistoryPeriods.FILL,med,after,new.until,old.until,zone,identity)
        val covered=Range(minOf(old.from,new.from),if(old.until==null || new.until==null)null else maxOf(old.until,new.until))
        return Edit(touched(rows,med,covered),out)
    }

    /** User edit rows shown in [period] (their group can be undone; a DELETED one can be restored). */
    fun userRowsIn(rows:List<HistoryPeriodEntity>,period:DisplayPeriod,zone:ZoneId)=HistoryPeriods.userEdits(rows).filter{r->
        val (from,until)=Range(LocalDate.parse(r.from_date),r.until_date?.let(LocalDate::parse)).instants(ZoneId.of(r.zone))
        overlaps(from,until,period.from,period.until)
    }
}
