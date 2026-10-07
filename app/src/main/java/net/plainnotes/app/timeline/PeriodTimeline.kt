package net.plainnotes.app.timeline

import net.plainnotes.app.NotesViewModel.ExtraState
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import java.time.*

/** UI-only adapter; storage signatures, context v1 and Visit Pack template 1 keep their own builders. */
fun RegimenDefinition.therapyStandard():TherapyStandard {
    val m=snapshot(0)
    val product=when(m?.route){"GEL"->m.profile?.gel_product_id?.let{"gel:$it"};"PATCH"->m.profile?.patch_release_ug_day?.let{"patch:$it"};else->null}
    return TherapyStandard(m?.molecule,m?.profile?.ester,m?.route,m?.unit,product,kind,interval,
        if(kind=="WEEKLY")Integer.bitCount(weekdays) else 0,
        if(kind=="EVERY_N_HOURS")listOf(dose) else times.map{it.second ?: dose})
}
data class PeriodEvent(val key:String,val kind:EventKind,val at:Instant?,val date:LocalDate,val source:EventSource,
    val displayPeriodKey:String?,val exactRegimenIds:Set<Long>,val dateOnly:Boolean)
data class PeriodTimeline(val projection:TreatmentPeriodProjection,val events:List<PeriodEvent>,val upcoming:List<PeriodEvent>) {
    fun eventsIn(period:DisplayPeriod)=events.filter{it.displayPeriodKey==period.key}
    val unknownEvents get()=events.filter{it.displayPeriodKey==null}
}
object PeriodTimelineProjection {
    fun build(extra:ExtraState,appointments:List<AppointmentEntity>,now:Instant=Instant.now(),displayZone:ZoneId?=null):PeriodTimeline {
        val ordered=extra.regimens.sortedWith(compareBy({it.effective_from_utc},{it.id}))
        // Deterministic display policy: changing the device zone must not regroup historical transitions.
        val zone=displayZone ?: ordered.firstOrNull()?.let{ZoneId.of(it.zone)} ?: ZoneId.of("UTC")
        val projection=TreatmentPeriods.build(ordered.map{RawTreatmentInterval(it.span(),RegimenDefinition.read(it.definition_json).therapyStandard())},zone)
        val today=now.atZone(zone).toLocalDate();val events=mutableListOf<PeriodEvent>();val upcoming=mutableListOf<PeriodEvent>()
        fun add(key:String,kind:EventKind,at:Instant?,day:LocalDate?,source:EventSource) {
            val date=day ?: requireNotNull(at).atZone(zone).toLocalDate()
            val future=if(at!=null)at>now else date>today
            val period=if(future)null else if(at!=null)projection.periodAt(at) else projection.periodOn(date)
            val event=PeriodEvent(key,kind,at,date,source,period?.key,
                if(at!=null && !future)projection.exactAt(at).map{it.span.id}.toSet() else emptySet(),at==null)
            if(future)upcoming+=event else events+=event
        }
        extra.labs.forEach{add("lab:${it.id}",EventKind.LAB,Instant.ofEpochMilli(it.sampled_utc),null,EventSource.Lab(it))}
        extra.reviews.forEach{add("review:${it.id}",EventKind.REVIEW,null,LocalDate.parse(it.date),EventSource.Review(it))}
        extra.milestones.forEach{add("milestone:${it.id}",EventKind.MILESTONE,null,LocalDate.parse(it.date),EventSource.Milestone(it))}
        appointments.forEach{add("appointment:${it.id}",EventKind.APPOINTMENT,Instant.ofEpochMilli(it.at_utc),null,EventSource.Appointment(it))}
        val order=compareByDescending<PeriodEvent>{it.date}.thenByDescending{it.at}.thenBy{it.key}
        return PeriodTimeline(projection,events.sortedWith(order),upcoming.sortedWith(compareBy<PeriodEvent>{it.date}.thenBy{it.at}.thenBy{it.key}))
    }
}
