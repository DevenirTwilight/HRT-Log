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
    private fun compatible(a:Identity,b:Identity)=a.compound==b.compound && a.unit==b.unit &&
        (a.route==null || b.route==null || a.route==b.route) && (a.ester==null || b.ester==null || a.ester==b.ester) &&
        (a.formulation==null || b.formulation==null || a.formulation==b.formulation)
    private fun subset(part:List<Double>,whole:List<Double>):Boolean {
        val remaining=whole.toMutableList()
        return part.all{amount->val index=remaining.indexOf(amount);if(index<0)false else {remaining.removeAt(index);true}}
    }
    private data class Saved(val raw:RawTreatmentInterval,val identity:Identity?)
    private data class Fact(val row:RecordEntity,val at:Instant,val med:Long,val identity:Identity,val snapshot:MedicationSnapshot,val date:LocalDate)
    private data class Day(val date:LocalDate,val facts:List<Fact>) {
        val doses=facts.map{it.row.actual_dose!!}.sorted()
        val at get()=facts.minOf{it.at}
        val unambiguous get()=facts.map{it.at to it.row.actual_dose}.distinct().size==facts.size
    }
    private data class Candidate(val med:Long,val from:Instant,val until:Instant,val standard:TherapyStandard,val snapshot:MedicationSnapshot,val facts:List<Fact>)

    fun build(records:List<RecordEntity>,versions:List<RegimenVersionEntity>,zone:ZoneId,now:Instant,ruleSnapshots:Map<Long,String> = emptyMap()):HistoricalTreatmentProjection {
        val saved=versions.map{v->val d=RegimenDefinition.read(v.definition_json)
            Saved(RawTreatmentInterval(v.span(),d.therapyStandard()),d.snapshot(v.medication_id)?.let(::identity))}
        val savedByMedication=saved.groupBy{it.raw.span.medicationId}.mapValues{(_,rows)->rows.sortedBy{it.raw.span.from}}
        val idsByIdentity=saved.filter{it.identity!=null}.groupBy{it.identity!!}.mapValues{(_,rows)->rows.map{it.raw.span.medicationId}.distinct()}
        val snapshots=records.associate{r->r.id to MedicationSnapshot.decode(HistoricalContext.resolved(r,ruleSnapshots),r.medication_id)}
        val facts=records.mapNotNull{r->
            val timestamp=r.taken_utc ?: return@mapNotNull null
            val dose=r.actual_dose ?: return@mapNotNull null
            if(r.deleted_at_utc!=null || r.status !in listOf("ON_TIME","LATE") || !dose.isFinite() || dose<=0) return@mapNotNull null
            val at=Instant.ofEpochMilli(timestamp);if(at>now)return@mapNotNull null
            val snapshot=snapshots[r.id] ?: return@mapNotNull null
            val key=identity(snapshot) ?: return@mapNotNull null
            // Missing route/ester/product is not proof that two different medication IDs are equivalent.
            val identifiable=key.route!=null && (key.compound!="E2" || key.ester!=null) &&
                (key.route !in listOf("GEL","PATCH") || key.formulation!=null)
            val canonical=if(identifiable)idsByIdentity[key]?.singleOrNull() ?: r.medication_id else r.medication_id
            val recordedZone=r.taken_zone?.let{runCatching{ZoneId.of(it)}.getOrNull()} ?: zone
            Fact(r,at,canonical,key,snapshot,at.atZone(recordedZone).toLocalDate())
        }
        val candidates=mutableListOf<Candidate>()
        facts.groupBy{it.med to it.identity}.toList().sortedBy{it.first.toString()}.forEach{(key,rows)->
            val days=rows.groupBy{it.date}.toSortedMap().map{Day(it.key,it.value)}
            // REQUIREMENTS §35a: one segmentation rule for every source; short deviations stay inside a period.
            val segments=SustainedPatterns.segment(days.map{day->LoggedDay(day.date,day.facts.distinctBy{it.at to it.row.actual_dose}.map{it.row.actual_dose!!}.sorted())})
            val firstFact=days.associate{it.date to it.facts.minBy{f->f.at}}
            segments.forEachIndexed{i,segment->
                val next=segments.getOrNull(i+1)
                val from=firstFact.getValue(segment.first).at
                val lastFact=days.single{it.date==segment.last}.facts.maxBy{it.at}
                val dateZone=lastFact.row.taken_zone?.let{runCatching{ZoneId.of(it)}.getOrNull()} ?: zone
                val coveredEnd=segment.last.plusDays(maxOf(segment.interval,1).toLong()).atStartOfDay(dateZone).toInstant()
                // Adjacent segments of one block meet at the next segment's first record; a 30-day gap ends at coverage.
                val joined=next!=null && java.time.temporal.ChronoUnit.DAYS.between(segment.last,next.first)-1<SustainedPatterns.GAP_DAYS
                val end=if(joined)firstFact.getValue(next!!.first).at else coveredEnd
                if(end<=from)return@forEachIndexed
                val evidence=rows.filter{it.at>=from && it.at<end}
                val id=key.second
                val standard=if(segment.frequencyKnown)TherapyStandard(id.compound,id.ester,id.route,id.unit,id.formulation,"EVERY_N_DAYS",segment.interval,0,segment.doses)
                    else TherapyStandard(id.compound,id.ester,id.route,id.unit,id.formulation,"OBSERVED",0,0,segment.doses)
                candidates+=Candidate(key.first,from,end,standard,firstFact.getValue(segment.first).snapshot,evidence)
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
                val adjacent=medicationVersions.firstOrNull{it.raw.span.from>=b}?.takeIf{next->
                    val shortDailyGap=c.standard.kind=="EVERY_N_DAYS" && c.standard.interval==1 && Duration.between(b,next.raw.span.from)<=Duration.ofDays(1)
                    val sameDisplayDay=next.raw.span.from.atZone(zone).toLocalDate()==b.atZone(zone).toLocalDate()
                    next.identity==identity(c.snapshot) && (sameDisplayDay || shortDailyGap) &&
                        next.raw.standard.therapySignatureV2()==c.standard.therapySignatureV2() &&
                        candidates.none{other->other.med==c.med && other.from>=b && other.from<next.raw.span.from && other.standard.therapySignatureV2()!=c.standard.therapySignatureV2()}
                }
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
        val byRecord=facts.associateBy{it.row.id}
        records.forEach{r->
            if(r.deleted_at_utc!=null || r.status !in listOf("ON_TIME","LATE","MISSED","SKIPPED"))return@forEach
            val timestamp=r.taken_utc ?: r.scheduled_utc?.takeIf{r.status in listOf("MISSED","SKIPPED")} ?: return@forEach
            if(Instant.ofEpochMilli(timestamp)>now)return@forEach
            val m=snapshots[r.id] ?: return@forEach
            val fact=byRecord[r.id];val med=fact?.med ?: r.medication_id
            val linked=saved.any{s->s.raw.span.medicationId==med && timestamp>=s.raw.span.from.toEpochMilli() &&
                (s.raw.span.until?.let{timestamp<it.toEpochMilli()} ?: true) && s.identity?.let{key->
                    (m.molecule==null || m.molecule==key.compound) && (m.unit==null || m.unit==key.unit) &&
                    (m.route==null || key.route==null || m.route==key.route) && (m.profile?.ester==null || key.ester==null || m.profile?.ester==key.ester) &&
                    identity(m)?.let{compatible(it,key)}!=false
                }==true}
            if(linked)resolved+=r.id
        }
        return HistoricalTreatmentProjection(observed,resolved)
    }
}
