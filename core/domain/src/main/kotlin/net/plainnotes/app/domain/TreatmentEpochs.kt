package net.plainnotes.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Recorded regimen intervals, not a claim about treatment before the first interval. */
data class RegimenSpan(val id:Long,val medicationId:Long,val from:Instant,val until:Instant?,val reconstructed:Boolean=false)
data class TreatmentEpoch(val key:String,val from:Instant,val until:Instant?,val regimenIds:Set<Long>,val reconstructed:Boolean) {
    fun contains(at:Instant)=at>=from && (until==null || at<until)
}
data class EpochResolution(val keys:Set<String>,val unknownPortion:Boolean=false) {
    val uncertain get()=keys.size>1 || unknownPortion
}
object TreatmentEpochs {
    fun build(spans:List<RegimenSpan>):List<TreatmentEpoch> {
        require(spans.map{it.id}.distinct().size==spans.size)
        require(spans.all{it.until==null || it.until>it.from})
        val starts=spans.groupBy{it.from}
        val ends=spans.filter{it.until!=null}.groupBy{it.until!!}
        val boundaries=(starts.keys+ends.keys).sorted()
        val active=mutableMapOf<Long,RegimenSpan>()
        val result=mutableListOf<TreatmentEpoch>()
        boundaries.forEachIndexed{i,start->
            val end=boundaries.getOrNull(i+1)
            ends[start].orEmpty().forEach{active.remove(it.id)}
            starts[start].orEmpty().forEach{active[it.id]=it}
            val ids=active.keys.toSortedSet()
            val previous=result.lastOrNull()
            if(previous!=null && previous.regimenIds==ids) result[result.lastIndex]=previous.copy(until=end)
            else result+=TreatmentEpoch("epoch:${start.toEpochMilli()}:${ids.joinToString(",")}",start,end,ids,active.values.any{it.reconstructed})
        }
        return result
    }
    fun at(epochs:List<TreatmentEpoch>,instant:Instant):EpochResolution = EpochResolution(epochs.filter{it.contains(instant)}.map{it.key}.toSet(),epochs.none{it.contains(instant)})
    /** A civil day can intersect multiple intervals or an unknown portion; never fabricate midnight attribution. */
    fun onDate(epochs:List<TreatmentEpoch>,date:LocalDate,zone:ZoneId):EpochResolution {
        val start=date.atStartOfDay(zone).toInstant();val end=date.plusDays(1).atStartOfDay(zone).toInstant()
        val candidates=epochs.filter{it.from<end && (it.until==null || it.until>start)}
        val unknown=candidates.isEmpty() || candidates.first().from>start || candidates.last().until?.let{it<end}==true
        return EpochResolution(candidates.map{it.key}.toSet(),unknown)
    }
}
