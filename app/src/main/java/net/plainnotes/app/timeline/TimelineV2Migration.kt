package net.plainnotes.app.timeline

import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.*

/** A build-24 projection is the migration authority. Only new user revisions are persisted. */
object TimelineV2Migration {
    data class Conversion(val replace:List<String>,val rows:List<TimelineEditRow>)
    fun needed(rows:List<HistoryPeriodEntity>)=HistoryPeriods.latest(rows).any{it.state==HistoryPeriods.CONFIRMED &&
        (it.kind in listOf(HistoryPeriods.CONFIRMED,HistoryPeriods.FILL) || it.kind==HistoryPeriods.PERIOD && !JSONObject(it.evidence_json).has("stated_utc"))}
    fun plan(extra:NotesViewModel.ExtraState,now:Instant,ruleSnapshots:Map<Long,String> = emptyMap()):Conversion? {
        if(!needed(extra.historyPeriods))return null
        val old=PeriodTimelineProjection.build(extra,emptyList(),now,ruleSnapshots=ruleSnapshots,legacy=true).projection
        val active=HistoryPeriods.latest(extra.historyPeriods).filter{it.state==HistoryPeriods.CONFIRMED && it.kind in listOf(HistoryPeriods.CONFIRMED,HistoryPeriods.FILL,HistoryPeriods.PERIOD)}
        val rows=old.standards.filter{s->s.rawVersionIds.any{id->old.raw.any{it.span.id==id && it.kind in listOf(SpanKind.CONFIRMED,SpanKind.FILL,SpanKind.USER)}}}.map{s->
            val parts=old.raw.filter{it.span.id in s.rawVersionIds}
            val userPart=parts.first{it.kind in listOf(SpanKind.CONFIRMED,SpanKind.FILL,SpanKind.USER)}
            val med=userPart.span.medicationId
            val source=active.firstOrNull{it.medication_id==med} ?: error("Missing migration source")
            val end=s.until?.takeIf{it<=now};val dates=TimelineEdits.daysOf(s.from,end,old.zone)
            val short=JSONArray(parts.filter{it.span.id in old.absorbed || old.effective[it.span.id]!=it.standard}.map{r->
                JSONObject().put("source_id",r.sourceId).put("from_utc",r.span.from.toEpochMilli()).put("until_utc",r.span.until?.toEpochMilli()).put("standard",JSONObject(HistoryPeriods.standardJson(r.standard)))})
            TimelineEditRow(HistoryPeriods.PERIOD,med,s.standard,dates.from,dates.until,old.zone,source.identity_json,
                exactFromUtc=s.from.toEpochMilli(),exactUntilUtc=end?.toEpochMilli(),evidenceJson=JSONObject().put("stated_utc",now.toEpochMilli()).put("short_saved",short).put("migration",25).toString())
        }
        return Conversion(active.map{it.period_key},rows)
    }
}
