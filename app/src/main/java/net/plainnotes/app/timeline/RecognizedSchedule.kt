package net.plainnotes.app.timeline

import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.domain.LoggedDay
import net.plainnotes.app.domain.SustainedPatterns
import java.time.*

/** What imported history says about a medicine's schedule: every [interval] days, at [times], [doses] per dosing day. */
data class RecognizedSchedule(val interval:Int,val times:List<LocalTime>,val doses:List<Double>)

/** REQUIREMENTS §37a: prefill for the import review sheet; the latest sustained pattern with a known frequency. */
object ScheduleRecognition {
    fun of(records:List<RecordEntity>,fallbackZone:ZoneId):RecognizedSchedule? {
        val taken=records.filter{it.deleted_at_utc==null && it.status in listOf("ON_TIME","LATE") && it.taken_utc!=null && (it.actual_dose ?: 0.0)>0}
            .map{r->Instant.ofEpochMilli(r.taken_utc!!).atZone(r.taken_zone?.let{runCatching{ZoneId.of(it)}.getOrNull()} ?: fallbackZone).toLocalDateTime() to r.actual_dose!!}
        if(taken.isEmpty())return null
        val days=taken.groupBy{it.first.toLocalDate()}.toSortedMap().map{(d,rows)->LoggedDay(d,rows.map{Math.round(it.second*1_000_000.0)/1_000_000.0}.sorted())}
        val segment=SustainedPatterns.segment(days).lastOrNull{it.frequencyKnown} ?: return null
        val n=segment.doses.size
        // Usual clock time of each dose position on full days, rounded to a quarter hour.
        val full=taken.filter{it.first.toLocalDate() in segment.days.map{d->d.date}}.groupBy{it.first.toLocalDate()}.values.filter{it.size==n}
        if(full.isEmpty())return null
        val times=(0 until n).map{i->
            val minutes=full.map{day->day.map{it.first.toLocalTime()}.sorted()[i].let{t->t.hour*60+t.minute}}.sorted().let{it[it.size/2]}
            val rounded=(Math.round(minutes/15.0)*15).toInt().coerceAtMost(23*60+45)
            LocalTime.of(rounded/60,rounded%60)
        }.distinct()
        return if(times.size==n)RecognizedSchedule(segment.interval,times,segment.doses) else null
    }
}
