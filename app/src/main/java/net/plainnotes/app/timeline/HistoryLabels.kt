package net.plainnotes.app.timeline

import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import java.time.Instant
import java.time.ZoneId

/**
 * History labels (REQUIREMENTS §35/35a). Only saved plans and confirmed periods give a standard; a record covered only by an
 * unconfirmed candidate shows "period to confirm" and nothing is inferred from it.
 */
object HistoryLabels {
    fun build(records:List<RecordEntity>,timeline:PeriodTimeline,annotations:List<RecordAnnotationEntity>,fallbackZone:ZoneId):Map<Long,Set<RecordLabel>> {
        val extra=annotations.filter{it.kind==HistoryPeriods.EXTRA}.map{it.record_id}.toSet()
        val inputs=records.filter{it.deleted_at_utc==null && it.status in listOf("ON_TIME","LATE") && it.taken_utc!=null}.map{r->
            val at=Instant.ofEpochMilli(r.taken_utc!!)
            val zone=r.taken_zone?.let{runCatching{ZoneId.of(it)}.getOrNull()} ?: fallbackZone
            val c=timeline.coverage[r.id]
            AttributionInput(r.id,at,at.atZone(zone).toLocalDate(),r.actual_dose,r.scheduled_utc!=null,c?.interval?.span?.id,c?.interval?.standard,c?.pending==true,r.id in extra)
        }
        return PeriodAttribution.labels(inputs)
    }
}
