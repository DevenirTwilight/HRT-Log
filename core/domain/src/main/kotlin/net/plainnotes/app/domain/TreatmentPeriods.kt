package net.plainnotes.app.domain

import java.security.MessageDigest
import java.time.Duration
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
    /** REQUIREMENTS §36: same medicine by identity, not by entry ID; a field missing on one side is compatible. */
    fun compatibleIdentity(a:TherapyStandard,b:TherapyStandard):Boolean {
        fun same(x:String?,y:String?)=x==null || y==null || x==y
        return a.compound!=null && a.compound==b.compound && a.unit!=null && a.unit==b.unit && same(a.route,b.route) && same(a.ester,b.ester) && same(a.formulation,b.formulation)
    }
    /** Same standard whatever its source: identity compatible, same cadence and the same doses per dosing day. */
    fun sameStandard(a:TherapyStandard,b:TherapyStandard)=compatibleIdentity(a,b) && a.kind==b.kind && a.interval==b.interval &&
        a.weeklyCount==b.weeklyCount && a.doses.sorted()==b.doses.sorted()
    /** A gap shorter than this between two parts with the same standard does not end the period (same as SustainedPatterns.GAP_DAYS). */
    val JOIN_GAP:Duration=Duration.ofDays(SustainedPatterns.GAP_DAYS.toLong())

    /**
     * Medication entries that never overlap in time and whose every part is identity-compatible form one lane, so history
     * stored on an import-created entry and the app's own entry can continue each other. Overlapping entries stay apart.
     */
    private fun lanes(raw:List<RawTreatmentInterval>):Map<Long,Long> {
        val groups=raw.groupBy{it.span.medicationId}.toSortedMap().map{(id,rows)->mutableListOf(id) to rows.toMutableList()}.toMutableList()
        fun overlap(x:RawTreatmentInterval,y:RawTreatmentInterval)=x.span.from<(y.span.until ?: Instant.MAX) && y.span.from<(x.span.until ?: Instant.MAX)
        var merged=true
        while(merged) {
            merged=false
            loop@ for(i in groups.indices)for(j in i+1 until groups.size) {
                val (ids,rows)=groups[i];val (otherIds,other)=groups[j]
                if(rows.all{x->other.all{y->!overlap(x,y) && compatibleIdentity(x.standard,y.standard)}}) {
                    ids+=otherIds;rows+=other;groups.removeAt(j);merged=true;break@loop
                }
            }
        }
        return groups.flatMap{(ids,_)->ids.map{it to ids.min()}}.toMap()
    }

    fun build(raw:List<RawTreatmentInterval>,zone:ZoneId):TreatmentPeriodProjection {
        require(raw.map{it.span.id}.distinct().size==raw.size)
        require(raw.all{it.span.until==null || it.span.until>it.span.from})
        val lane=lanes(raw)
        val standards=raw.groupBy{lane.getValue(it.span.medicationId)}.toSortedMap().flatMap{(med,rows)->
            val result=mutableListOf<TreatmentStandardSpan>()
            var currentIds=mutableListOf<Long>()
            rows.sortedWith(compareBy({it.span.from},{it.span.id})).forEach{r->
                val prev=result.lastOrNull()
                require(prev==null || prev.until!=null && prev.until<=r.span.from){"Overlapping recorded regimen"}
                // §36: parts with the same standard continue one period across a short gap, whatever their source.
                if(prev!=null && sameStandard(prev.standard,r.standard) && Duration.between(prev.until,r.span.from)<JOIN_GAP) {
                    currentIds.add(r.span.id)
                    fun pick(a:String?,b:String?)=b ?: a
                    val standard=r.standard.copy(compound=pick(prev.standard.compound,r.standard.compound),ester=pick(prev.standard.ester,r.standard.ester),
                        route=pick(prev.standard.route,r.standard.route),formulation=pick(prev.standard.formulation,r.standard.formulation))
                    result[result.lastIndex]=prev.copy(until=r.span.until,standard=standard,reconstructed=prev.reconstructed||r.span.reconstructed)
                } else {
                    currentIds=mutableListOf(r.span.id)
                    result+=TreatmentStandardSpan("standard-v2:$med:${r.span.from.toEpochMilli()}:${r.span.id}",med,r.span.from,r.span.until,currentIds,r.standard,r.span.reconstructed)
                }
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
