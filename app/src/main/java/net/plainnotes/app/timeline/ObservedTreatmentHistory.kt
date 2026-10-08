package net.plainnotes.app.timeline

import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import java.time.*

/** Read-only evidence, never a prescription, reminder rule or frozen regimen/context ID. */
data class ObservedTreatment(val interval:RawTreatmentInterval,val snapshot:MedicationSnapshot,val records:List<RecordEntity>)
data class HistoricalTreatmentProjection(val observed:List<ObservedTreatment>,val resolvedRecordIds:Set<Long>)

object ObservedTreatmentHistory {
    private data class Identity(val compound:String,val route:String?,val unit:String,val ester:String?,val formulation:String?)
    private fun identity(m:MedicationSnapshot):Identity? {
        val compound=m.molecule ?: return null;val unit=m.unit ?: return null
        val formulation=when(m.route){"GEL"->m.profile?.gel_product_id?.let{"gel:$it"};"PATCH"->m.profile?.patch_release_ug_day?.let{"patch:$it"};else->null}
        return Identity(compound,m.route,unit,m.profile?.ester,formulation)
    }
    private data class Saved(val raw:RawTreatmentInterval,val identity:Identity?)
    private data class Fact(val row:RecordEntity,val at:Instant,val med:Long,val identity:Identity,val snapshot:MedicationSnapshot)
    private data class Day(val date:LocalDate,val facts:List<Fact>) {
        val doses get()=facts.map{it.row.actual_dose!!}.sorted()
        val at get()=facts.minOf{it.at}
        val unambiguous get()=facts.map{it.at to it.row.actual_dose}.distinct().size==facts.size
    }
    private data class Run(val first:Day,var last:Day,val interval:Int,val doses:List<Double>)
    private data class Candidate(val med:Long,val from:Instant,val until:Instant,val standard:TherapyStandard,val snapshot:MedicationSnapshot,val facts:List<Fact>)

    fun build(records:List<RecordEntity>,versions:List<RegimenVersionEntity>,zone:ZoneId,now:Instant):HistoricalTreatmentProjection {
        val saved=versions.map{v->val d=RegimenDefinition.read(v.definition_json)
            Saved(RawTreatmentInterval(v.span(),d.therapyStandard()),d.snapshot(v.medication_id)?.let(::identity))}
        val savedByMedication=saved.groupBy{it.raw.span.medicationId}.mapValues{(_,rows)->rows.sortedBy{it.raw.span.from}}
        val idsByIdentity=saved.filter{it.identity!=null}.groupBy{it.identity!!}.mapValues{(_,rows)->rows.map{it.raw.span.medicationId}.distinct()}
        val facts=records.mapNotNull{r->
            val timestamp=r.taken_utc ?: return@mapNotNull null
            val dose=r.actual_dose ?: return@mapNotNull null
            if(r.deleted_at_utc!=null || r.status !in listOf("ON_TIME","LATE") || !dose.isFinite() || dose<=0) return@mapNotNull null
            val at=Instant.ofEpochMilli(timestamp);if(at>now)return@mapNotNull null
            val snapshot=MedicationSnapshot.decode(r.config_snapshot,r.medication_id) ?: return@mapNotNull null
            val key=identity(snapshot) ?: return@mapNotNull null
            // Missing route/ester/product is not proof that two different medication IDs are equivalent.
            val identifiable=key.route!=null && (key.compound!="E2" || key.ester!=null) &&
                (key.route !in listOf("GEL","PATCH") || key.formulation!=null)
            val canonical=if(identifiable)idsByIdentity[key]?.singleOrNull() ?: r.medication_id else r.medication_id
            Fact(r,at,canonical,key,snapshot)
        }
        val candidates=mutableListOf<Candidate>()
        // Dates and amount multisets are factual. Clock times never identify a dose slot or a prescription.
        facts.groupBy{it.med to it.identity}.toList().sortedBy{it.first.toString()}.forEach{(key,rows)->
            val days=rows.groupBy{it.at.atZone(zone).toLocalDate()}.toSortedMap().map{Day(it.key,it.value)}
            val runs=mutableListOf<Run>()
            for(i in 0 until (days.size-2).coerceAtLeast(0)) {
                val a=days[i];val b=days[i+1];val c=days[i+2]
                val gap=java.time.temporal.ChronoUnit.DAYS.between(a.date,b.date).toInt()
                if(!a.unambiguous || !b.unambiguous || !c.unambiguous || gap !in 1..365 || gap.toLong()!=java.time.temporal.ChronoUnit.DAYS.between(b.date,c.date) || a.doses!=b.doses || b.doses!=c.doses)continue
                // Non-daily cadence needs four occurrences; a couple of omissions is not sufficient evidence.
                val last=if(gap==1)c else days.getOrNull(i+3)?.takeIf{it.unambiguous && it.doses==a.doses && java.time.temporal.ChronoUnit.DAYS.between(c.date,it.date)==gap.toLong()} ?: continue
                val previous=runs.lastOrNull()
                if(previous!=null && previous.interval==gap && previous.doses==a.doses &&
                    java.time.temporal.ChronoUnit.DAYS.between(previous.last.date,a.date)<=3L*gap)previous.last=last
                else runs+=Run(a,last,gap,a.doses)
            }
            runs.forEachIndexed{i,run->
                val next=runs.getOrNull(i+1)
                val from=run.first.at
                val coveredEnd=run.last.date.plusDays(run.interval.toLong()).atStartOfDay(zone).toInstant()
                val end=minOf(coveredEnd,next?.first?.at ?: Instant.MAX)
                if(end<=from)return@forEachIndexed
                val evidence=rows.filter{it.at>=from && it.at<end}
                val id=key.second
                candidates+=Candidate(key.first,from,end,TherapyStandard(id.compound,id.ester,id.route,id.unit,id.formulation,"EVERY_N_DAYS",run.interval,0,run.doses),
                    run.first.facts.first().snapshot,evidence)
            }
        }
        // Saved prescriptions take precedence, without modifying a single stored version or record.
        val clipped=mutableListOf<Candidate>()
        candidates.sortedWith(compareBy({it.med},{it.from})).forEach{c->
            var pieces=listOf(c.from to c.until)
            val medicationVersions=savedByMedication[c.med].orEmpty()
            medicationVersions.forEach{s->
                val from=s.raw.span.from;val until=s.raw.span.until ?: Instant.MAX
                pieces=pieces.flatMap{(a,b)->if(from>=b || until<=a)listOf(a to b) else buildList {
                    if(a<from)add(a to from);if(until<b)add(until to b)
                }}
            }
            pieces.forEach{(a,b)->
                // Only the immediately next saved boundary can be joined. Never skip an intervening version.
                val adjacent=medicationVersions.firstOrNull{it.raw.span.from>=b}?.takeIf{it.identity==identity(c.snapshot) && it.raw.span.from.atZone(zone).toLocalDate()==b.atZone(zone).toLocalDate() &&
                    it.raw.standard.therapySignatureV2()==c.standard.therapySignatureV2()}
                val end=adjacent?.raw?.span?.from ?: b
                val evidence=c.facts.filter{it.at>=a && it.at<end}
                if(evidence.isNotEmpty())clipped+=c.copy(from=a,until=end,facts=evidence)
            }
        }
        // If the same medication ID has overlapping incompatible identities, do not invent a combination.
        val unambiguous=clipped.filter{c->clipped.none{other->other!==c && other.med==c.med && other.from<c.until && c.from<other.until}}
        val observed=unambiguous.sortedWith(compareBy({it.from},{it.med})).mapIndexed{i,c->
            ObservedTreatment(RawTreatmentInterval(RegimenSpan(-1L-i,c.med,c.from,c.until,true),c.standard),c.snapshot,c.facts.map{it.row}.sortedWith(compareBy({it.taken_utc},{it.id})))
        }
        val resolved=observed.flatMap{it.records}.map{it.id}.toMutableSet()
        facts.filter{f->saved.any{s->s.raw.span.medicationId==f.med && s.identity==f.identity && f.at>=s.raw.span.from && (s.raw.span.until==null || f.at<s.raw.span.until)}}.forEach{resolved+=it.row.id}
        return HistoricalTreatmentProjection(observed,resolved)
    }
}
