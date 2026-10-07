package net.plainnotes.app.domain

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Display policy v2. Never written to clinical_signature or a frozen v1 epoch reference. */
data class TherapyStandard(val compound:String?,val ester:String?,val route:String?,val unit:String?,
    val formulation:String?,val kind:String,val interval:Int,val weeklyCount:Int,val doses:List<Double>) {
    // Clock-independent multiset: legacy slot identities were not recorded. Do not infer a permutation.
    val slotIdentityUnknown get()=doses.distinct().size>1
    fun therapySignatureV2():String {
        val fields=listOf(compound,ester,route,unit,formulation,kind,interval.toString(),weeklyCount.toString())+
            doses.sorted().map{it.toString()}
        val canonical=fields.joinToString(""){it?.let{v->"${v.length}:$v;"} ?: "null;"}
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    }
}
data class RawTreatmentInterval(val span:RegimenSpan,val standard:TherapyStandard)
data class TreatmentStandardSpan(val key:String,val medicationId:Long,val from:Instant,val until:Instant?,
    val rawVersionIds:List<Long>,val standard:TherapyStandard,val reconstructed:Boolean)
data class ClinicalSegment(val key:String,val from:Instant,val until:Instant?,val standardSpanKeys:Set<String>) {
    fun contains(at:Instant)=at>=from && (until==null || at<until)
}
/** One display transition per civil day; its segments retain every exact UTC change, including A→B→A. */
data class DisplayPeriod(val key:String,val from:Instant,val until:Instant?,val segments:List<ClinicalSegment>) {
    val finalStandardSpanKeys get()=segments.last().standardSpanKeys
    fun contains(at:Instant)=at>=from && (until==null || at<until)
}
data class TreatmentPeriodProjection(val zone:ZoneId,val raw:List<RawTreatmentInterval>,val standards:List<TreatmentStandardSpan>,
    val segments:List<ClinicalSegment>,val periods:List<DisplayPeriod>) {
    fun exactAt(at:Instant)=raw.filter{at>=it.span.from && (it.span.until==null || at<it.span.until)}
    fun periodAt(at:Instant)=periods.singleOrNull{it.contains(at)}
    /** Date-only facts belong to the display day, not a fabricated sampledAt. */
    fun periodOn(date:LocalDate)=periods.lastOrNull{it.from.atZone(zone).toLocalDate()<=date &&
        (it.until==null || date<it.until.atZone(zone).toLocalDate())}
}
object TreatmentPeriods {
    fun build(raw:List<RawTreatmentInterval>,zone:ZoneId):TreatmentPeriodProjection {
        require(raw.map{it.span.id}.distinct().size==raw.size)
        require(raw.all{it.span.until==null || it.span.until>it.span.from})
        val standards=raw.groupBy{it.span.medicationId}.toSortedMap().flatMap{(med,rows)->
            val result=mutableListOf<TreatmentStandardSpan>()
            var currentIds=mutableListOf<Long>()
            var previousSignature:String?=null
            rows.sortedWith(compareBy({it.span.from},{it.span.id})).forEach{r->
                val prev=result.lastOrNull()
                require(prev==null || prev.until!=null && prev.until<=r.span.from){"Overlapping recorded regimen"}
                val signature=r.standard.therapySignatureV2()
                if(prev!=null && prev.until==r.span.from && previousSignature==signature) {
                    currentIds.add(r.span.id)
                    result[result.lastIndex]=prev.copy(until=r.span.until,reconstructed=prev.reconstructed||r.span.reconstructed)
                } else {
                    currentIds=mutableListOf(r.span.id)
                    result+=TreatmentStandardSpan("standard-v2:$med:${r.span.from.toEpochMilli()}:${r.span.id}",med,r.span.from,r.span.until,currentIds,r.standard,r.span.reconstructed)
                }
                previousSignature=signature
            };result.map{it.copy(rawVersionIds=it.rawVersionIds.toList())}
        }
        val starts=standards.groupBy{it.from};val ends=standards.filter{it.until!=null}.groupBy{it.until!!}
        val boundaries=(starts.keys+ends.keys).sorted();val active=mutableMapOf<Long,TreatmentStandardSpan>()
        val segments=boundaries.mapIndexed{i,start->
            ends[start].orEmpty().forEach{active.remove(it.medicationId)}
            starts[start].orEmpty().forEach{active[it.medicationId]=it}
            val identities=active.values.map{it.key}.toSortedSet()
            ClinicalSegment("clinical-v2:${start.toEpochMilli()}:${identities.joinToString(",")}",start,boundaries.getOrNull(i+1),identities)
        }
        val groups=segments.groupBy{it.from.atZone(zone).toLocalDate()}.values.toList()
        val periods=groups.mapIndexed{i,parts->DisplayPeriod("period-v2:${zone.id}:${parts.first().key}",parts.first().from,groups.getOrNull(i+1)?.first()?.from,parts)}
        return TreatmentPeriodProjection(zone,raw,standards,segments,periods)
    }
}
