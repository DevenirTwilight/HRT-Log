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
/** A plan explicitly ended in the app (saved version with an end) before the next part of the same medicine, or for good. */
data class TreatmentStop(val medicationId:Long,val from:Instant,val until:Instant?)
/** Why a display period differs from the previous one, per medicine (lane ID). */
enum class ChangeKind{STARTED,ENDED,STOPPED,DOSE,FREQUENCY,FREQUENCY_UNKNOWN,ROUTE,ESTER,FORMULATION,IDENTITY,GAP,UNJOINED}
data class PeriodChange(val medicationId:Long,val kinds:Set<ChangeKind>)
/** One join condition and whether it held; [detail] shows both sides. */
data class JoinCheck(val name:String,val passed:Boolean,val detail:String)
data class TreatmentPeriodProjection(val zone:ZoneId,val raw:List<RawTreatmentInterval>,val standards:List<TreatmentStandardSpan>,
    val segments:List<ClinicalSegment>,val periods:List<DisplayPeriod>,val stops:List<TreatmentStop> = emptyList(),
    /** The standard each raw part is compared with: a saved correction replaced within a day takes its replacement's. */
    val effective:Map<Long,TherapyStandard> = emptyMap()) {
    /** Every join check between the end of [a] and the start of [b], exactly as [TreatmentPeriods.build] decides (diagnostics). */
    fun joinChecks(a:TreatmentStandardSpan,b:TreatmentStandardSpan):List<JoinCheck> {
        val byId=raw.associateBy{it.span.id}
        val last=byId.getValue(a.rawVersionIds.last());val first=byId.getValue(b.rawVersionIds.first())
        return listOf(JoinCheck("same_lane",a.medicationId==b.medicationId,"${a.medicationId} / ${b.medicationId}"))+
            TreatmentPeriods.joinChecks(a.standard,a.until,last,first,effective[first.span.id] ?: first.standard)
    }
    /** REQUIREMENTS §36: what changed at the start of [period] compared with the end of the previous one. */
    fun changes(period:DisplayPeriod):List<PeriodChange> {
        val i=periods.indexOf(period);if(i<=0)return emptyList()
        val before=standards.filter{it.key in periods[i-1].finalStandardSpanKeys}.associateBy{it.medicationId}
        val after=standards.filter{it.key in period.segments.first().standardSpanKeys}.associateBy{it.medicationId}
        return (before.keys+after.keys).sorted().mapNotNull{m->
            val b=before[m];val a=after[m]
            when {
                b==null && a==null->null
                b==null->PeriodChange(m,setOf(ChangeKind.STARTED))
                a==null->PeriodChange(m,setOf(if(stops.any{it.medicationId==m && it.from==b.until})ChangeKind.STOPPED else ChangeKind.ENDED))
                a.key==b.key->null
                else->PeriodChange(m,difference(b,a))
            }
        }
    }
    private fun difference(b:TreatmentStandardSpan,a:TreatmentStandardSpan):Set<ChangeKind> {
        val x=b.standard;val y=a.standard
        fun differs(p:String?,q:String?)=p!=null && q!=null && p!=q
        val kinds=buildSet {
            if(x.compound!=y.compound || x.unit!=y.unit)add(ChangeKind.IDENTITY)
            if(differs(x.route,y.route))add(ChangeKind.ROUTE)
            if(differs(x.ester,y.ester))add(ChangeKind.ESTER)
            if(differs(x.formulation,y.formulation))add(ChangeKind.FORMULATION)
            if((x.kind=="OBSERVED")!=(y.kind=="OBSERVED"))add(ChangeKind.FREQUENCY_UNKNOWN)
            else if(x.kind!=y.kind || x.interval!=y.interval || x.weeklyCount!=y.weeklyCount)add(ChangeKind.FREQUENCY)
            if(TreatmentPeriods.doseKey(x.doses)!=TreatmentPeriods.doseKey(y.doses))add(ChangeKind.DOSE)
        }
        if(kinds.isNotEmpty())return kinds
        val gap=b.until?.let{Duration.between(it,a.from)}
        return setOf(if(stops.any{it.medicationId==b.medicationId && it.from==b.until})ChangeKind.STOPPED
            else if(gap!=null && gap>=TreatmentPeriods.JOIN_GAP)ChangeKind.GAP else ChangeKind.UNJOINED)
    }
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
        a.weeklyCount==b.weeklyCount && doseKey(a.doses)==doseKey(b.doses)
    /** Doses compared to a millionth, so float noise in an import (2.0000000001) is the same dose shown on the card. */
    fun doseKey(doses:List<Double>)=doses.map{Math.round(it*1_000_000.0)/1_000_000.0}.sorted()
    /** §36a: a saved plan ended and resumed within a day is not a stop period. */
    val MIN_STOP:Duration=Duration.ofDays(1)
    /** A saved version replaced within an hour of being saved is a correction (build 15 same-day changes stay real). */
    val CORRECTION:Duration=Duration.ofHours(1)

    /** The join conditions, in order; two consecutive parts of one lane join only when all pass. */
    fun joinChecks(prev:TherapyStandard,prevUntil:Instant?,prevLast:RawTreatmentInterval,next:RawTreatmentInterval,nextStandard:TherapyStandard):List<JoinCheck> {
        val a=prev;val b=nextStandard;val sameEntry=prevLast.span.medicationId==next.span.medicationId
        fun known(x:String?,y:String?)=x==y || (sameEntry && (x==null || y==null))
        fun loose(x:String?,y:String?)=x==null || y==null || x==y
        val gap=prevUntil?.let{Duration.between(it,next.span.from)}
        return listOf(
            JoinCheck("no_overlap",gap!=null && !gap.isNegative,"$prevUntil / ${next.span.from}"),
            JoinCheck("compound",known(a.compound,b.compound) && (a.compound!=null || b.compound!=null || sameEntry),"${a.compound} / ${b.compound}"),
            JoinCheck("unit",known(a.unit,b.unit) && (a.unit!=null || b.unit!=null || sameEntry),"${a.unit} / ${b.unit}"),
            JoinCheck("route",loose(a.route,b.route),"${a.route} / ${b.route}"),
            JoinCheck("ester",loose(a.ester,b.ester),"${a.ester} / ${b.ester}"),
            JoinCheck("formulation",loose(a.formulation,b.formulation),"${a.formulation} / ${b.formulation}"),
            JoinCheck("frequency_kind",a.kind==b.kind,"${a.kind} / ${b.kind}"),
            JoinCheck("interval",a.interval==b.interval,"${a.interval} / ${b.interval}"),
            JoinCheck("weekly_count",a.weeklyCount==b.weeklyCount,"${a.weeklyCount} / ${b.weeklyCount}"),
            JoinCheck("doses",doseKey(a.doses)==doseKey(b.doses),"${a.doses} / ${b.doses}"),
            JoinCheck("gap_under_30_days",gap!=null && gap<JOIN_GAP,"${gap?.toMillis()} ms"),
            JoinCheck("not_stopped_in_app",!(prevLast.span.id>0 && gap!=null && gap>=MIN_STOP),"last part ${prevLast.span.id}, gap ${gap?.toMillis()} ms"))
    }
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
        val effective=mutableMapOf<Long,TherapyStandard>()
        val standards=raw.groupBy{lane.getValue(it.span.medicationId)}.toSortedMap().flatMap{(med,rows)->
            val result=mutableListOf<TreatmentStandardSpan>()
            var currentIds=mutableListOf<Long>()
            var last:RawTreatmentInterval?=null
            val sorted=rows.sortedWith(compareBy({it.span.from},{it.span.id}))
            // A saved version replaced within an hour (a correction made right after saving) takes its replacement's standard.
            for(i in sorted.indices.reversed()) {
                val r=sorted[i];val next=sorted.getOrNull(i+1);val end=r.span.until
                val correction=r.span.id>0 && end!=null && next!=null && Duration.between(r.span.from,end)<CORRECTION &&
                    !next.span.from.isBefore(end) && Duration.between(end,next.span.from)<CORRECTION
                effective[r.span.id]=if(correction)effective.getValue(next!!.span.id) else r.standard
            }
            sorted.forEach{r->
                val prev=result.lastOrNull()
                require(prev==null || prev.until!=null && prev.until<=r.span.from){"Overlapping recorded regimen"}
                // §36/36a: parts with the same standard continue one period across a short gap whatever their source,
                // except after a plan stopped in the app for a day or more.
                val standardHere=effective.getValue(r.span.id)
                if(prev!=null && last!=null && joinChecks(prev.standard,prev.until,last!!,r,standardHere).all{it.passed}) {
                    currentIds.add(r.span.id)
                    fun pick(a:String?,b:String?)=b ?: a
                    val standard=standardHere.copy(compound=pick(prev.standard.compound,standardHere.compound),ester=pick(prev.standard.ester,standardHere.ester),
                        route=pick(prev.standard.route,standardHere.route),unit=pick(prev.standard.unit,standardHere.unit),formulation=pick(prev.standard.formulation,standardHere.formulation))
                    result[result.lastIndex]=prev.copy(until=r.span.until,standard=standard,reconstructed=prev.reconstructed||r.span.reconstructed)
                } else {
                    currentIds=mutableListOf(r.span.id)
                    result+=TreatmentStandardSpan("standard-v2:$med:${r.span.from.toEpochMilli()}:${r.span.id}",med,r.span.from,r.span.until,currentIds,standardHere,r.span.reconstructed)
                }
                last=r
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
        val lastById=raw.associateBy{it.span.id}
        val stops=standards.groupBy{it.medicationId}.flatMap{(med,spans)->spans.sortedBy{it.from}.let{list->list.mapIndexedNotNull{i,span->
            val end=span.until ?: return@mapIndexedNotNull null
            if(lastById.getValue(span.rawVersionIds.last()).span.id<=0)return@mapIndexedNotNull null
            val next=list.getOrNull(i+1)
            if(next!=null && Duration.between(end,next.from)<MIN_STOP)null else TreatmentStop(med,end,next?.from)
        }}}
        return TreatmentPeriodProjection(zone,raw,standards,segments,periods,stops,effective)
    }
}
