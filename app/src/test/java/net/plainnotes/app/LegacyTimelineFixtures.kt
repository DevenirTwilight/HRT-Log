package net.plainnotes.app.timeline

import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import java.time.*

/** Test-only build-23 operations, used to construct and exercise legacy revisions. */
    /** Splits each medicine of [period] at [day]; both halves keep the shown standard and their own bounds. */
    fun TimelineEdits.split(rows:List<HistoryPeriodEntity>,p:TreatmentPeriodProjection,period:DisplayPeriod,day:LocalDate,identity:(Long)->String):TimelineEdits.Edit? {
        val range=rangeOf(period,p.zone);if(day<=range.from || (range.until!=null && day>=range.until))return null
        val meds=medicationsIn(p,period).mapNotNull{m->standardOf(p,period,m)?.let{m to it}};if(meds.isEmpty())return null
        return TimelineEdits.Edit(meds.flatMap{(m,_)->touched(rows,m,range)}.distinct(),meds.flatMap{(m,s)->listOf(
            TimelineEditRow(HistoryPeriods.PERIOD,m,s,range.from,day,p.zone,identity(m)),TimelineEditRow(HistoryPeriods.PERIOD,m,s,day,range.until,p.zone,identity(m)))})
    }

    /**
     * Moves a stop: the new stop range, plus fills where the old stop no longer applies (the plan before continues up to
     * the new start, the plan after starts at the new end).
     */
    fun TimelineEdits.moveStop(rows:List<HistoryPeriodEntity>,p:TreatmentPeriodProjection,stop:TreatmentStop,med:Long,new:TimelineEdits.Range,identity:String):TimelineEdits.Edit {
        val zone=p.zone;val old=TimelineEdits.Range(stop.from.atZone(zone).toLocalDate(),stop.until?.atZone(zone)?.toLocalDate())
        val before=p.standards.filter{it.medicationId==stop.medicationId && it.until?.let{u->u<=stop.from}==true}.maxByOrNull{it.until!!}?.standard
        val after=stop.until?.let{u->p.standards.filter{it.medicationId==stop.medicationId && it.from>=u}.minByOrNull{it.from}?.standard}
        val out=mutableListOf(TimelineEditRow(HistoryPeriods.STOP,med,null,new.from,new.until,zone,identity))
        if(new.from>old.from && before!=null)out+=TimelineEditRow(HistoryPeriods.FILL,med,before,old.from,new.from,zone,identity)
        if(old.until!=null && new.until!=null && new.until<old.until && after!=null)out+=TimelineEditRow(HistoryPeriods.FILL,med,after,new.until,old.until,zone,identity)
        val covered=TimelineEdits.Range(minOf(old.from,new.from),if(old.until==null || new.until==null)null else maxOf(old.until,new.until))
        return TimelineEdits.Edit(touched(rows,med,covered),out)
    }

