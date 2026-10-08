package net.plainnotes.app.timeline

import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.domain.TreatmentPeriodProjection
import java.time.Instant

/** Unmatched history from any source, never a reconstruction of a prescribed regimen. */
data class ImportedHistorySummary(val key:String,val displayPeriodKey:String?,val future:Boolean,
    val from:Instant,val through:Instant,val records:List<RecordEntity>)

object ImportedHistoryProjection {
    fun build(records:List<RecordEntity>,periods:TreatmentPeriodProjection,now:Instant):List<ImportedHistorySummary> {
        data class Bucket(val period:String?,val future:Boolean)
        fun at(r:RecordEntity)=r.taken_utc ?: r.scheduled_utc
        return records.asSequence().filter{it.deleted_at_utc==null && it.status in setOf("ON_TIME","LATE","MISSED","SKIPPED") && at(it)!=null}
            .groupBy{r->val time=Instant.ofEpochMilli(requireNotNull(at(r)));val future=time>now
                Bucket(if(future)null else periods.periodAt(time)?.key,future)}
            .map{(bucket,rows)->
                val sorted=rows.sortedWith(compareBy<RecordEntity>{at(it)}.thenBy{it.id})
                ImportedHistorySummary("history:${bucket.period ?: "unknown"}:${bucket.future}",bucket.period,bucket.future,
                    Instant.ofEpochMilli(requireNotNull(at(sorted.first()))),Instant.ofEpochMilli(requireNotNull(at(sorted.last()))),sorted)
            }.sortedWith(compareByDescending<ImportedHistorySummary>{it.through}.thenBy{it.key})
    }
}
