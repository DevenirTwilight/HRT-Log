package net.plainnotes.app.ui

import net.plainnotes.app.timeline.EventKind
import net.plainnotes.app.timeline.EventSource
import net.plainnotes.app.timeline.PeriodEvent
import java.time.LocalDate
import java.time.ZoneId

/** Display-only runs. Original events, facts and treatment boundaries are never rewritten. */
internal data class TimelineSkippedGroup(val events:List<PeriodEvent>,val zone:ZoneId) {
    val key="skip-group:${events.last().key}"
    val from:LocalDate=events.last().at!!.atZone(zone).toLocalDate()
    val through:LocalDate=events.first().at!!.atZone(zone).toLocalDate()
}

internal object TimelineSkippedGroups {
    private data class Identity(val period:String,val medication:Long,val rule:Long,val snapshot:String,val zone:ZoneId)
    fun build(events:List<PeriodEvent>):List<TimelineSkippedGroup> {
        val eligible=events.mapNotNull { e ->
            val r=(e.source as? EventSource.Intake)?.record ?: return@mapNotNull null
            if(e.kind!=EventKind.SKIPPED || e.displayPeriodKey==null || r.origin!="APP" || r.status!="SKIPPED" ||
                r.deleted_at_utc!=null || r.slot_key.isNullOrBlank() || r.rule_version_id==null || e.at==null ||
                r.scheduled_utc!=e.at.toEpochMilli())return@mapNotNull null
            val zone=runCatching { ZoneId.of(r.scheduled_zone) }.getOrNull() ?: return@mapNotNull null
            Identity(e.displayPeriodKey,r.medication_id,requireNotNull(r.rule_version_id),r.config_snapshot,zone) to e
        }.groupBy({it.first},{it.second})
        return buildList {
            eligible.forEach { (identity,rows) ->
                val run=mutableListOf<PeriodEvent>()
                fun flush() { if(run.size>1)add(TimelineSkippedGroup(run.asReversed().toList(),identity.zone));run.clear() }
                rows.sortedWith(compareBy<PeriodEvent>{it.at}.thenBy{it.key}).forEach { event ->
                    val day=event.at!!.atZone(identity.zone).toLocalDate()
                    val prior=run.lastOrNull()?.at?.atZone(identity.zone)?.toLocalDate()
                    if(prior!=null && day>prior.plusDays(1))flush()
                    run+=event
                }
                flush()
            }
        }
    }
}
