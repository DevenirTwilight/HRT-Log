package net.plainnotes.app.timeline

import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.domain.TreatmentPeriodProjection
import java.time.Instant

/** Observed imported history, never a reconstruction of a prescribed regimen. */
data class ImportedHistorySummary(val key:String,val origin:String,val displayPeriodKey:String?,val future:Boolean,
    val from:Instant,val through:Instant,val records:List<RecordEntity>)

object ImportedHistoryProjection {
    fun build(records:List<RecordEntity>,periods:TreatmentPeriodProjection,now:Instant):List<ImportedHistorySummary> {
        data class Bucket(val origin:String,val period:String?,val future:Boolean)
        fun at(r:RecordEntity)=r.taken_utc ?: r.scheduled_utc
        return records.asSequence().filter{it.deleted_at_utc==null && it.origin in setOf("IMPORT_HT","IMPORT_TM") && at(it)!=null}
            .groupBy{r->val time=Instant.ofEpochMilli(requireNotNull(at(r)));val future=time>now
                Bucket(r.origin,if(future)null else periods.periodAt(time)?.key,future)}
            .map{(bucket,rows)->
                val sorted=rows.sortedWith(compareBy<RecordEntity>{at(it)}.thenBy{it.id})
                ImportedHistorySummary("import-history:${bucket.origin}:${bucket.period ?: "unknown"}:${bucket.future}",bucket.origin,bucket.period,bucket.future,
                    Instant.ofEpochMilli(requireNotNull(at(sorted.first()))),Instant.ofEpochMilli(requireNotNull(at(sorted.last()))),sorted)
            }.sortedWith(compareByDescending<ImportedHistorySummary>{it.through}.thenBy{it.key})
    }
}
