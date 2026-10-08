package net.plainnotes.app.timeline

import net.plainnotes.app.NotesViewModel.ExtraState
import net.plainnotes.app.domain.*
import java.time.Instant

/**
 * REQUIREMENTS §36b: plain text explaining why a period did or did not join the previous one, for the user to paste
 * into a report. Only structure: sources, medication IDs, standards, exact bounds and every join check. No intake
 * details, doses taken, notes or names.
 */
object MergeDiagnostics {
    private fun time(at:Instant?)=at?.let{"${it.toEpochMilli()} (${it})"} ?: "open"
    private fun standard(s:TherapyStandard)="compound=${s.compound} ester=${s.ester} route=${s.route} unit=${s.unit} formulation=${s.formulation} " +
        "kind=${s.kind} interval=${s.interval} weekly_count=${s.weeklyCount} doses=${s.doses}"

    fun text(timeline:PeriodTimeline,extra:ExtraState,period:DisplayPeriod,versionCode:Int):String {
        val p=timeline.projection;val index=p.periods.indexOf(period);val previous=p.periods.getOrNull(index-1)
        val observed=timeline.observed.associateBy{it.interval.span.id}
        val confirmedRows=timeline.confirmedHistory.associateBy{ObservedTreatmentHistory.CONFIRMED_SPAN_BASE-it.id*100}
        val inside=recordsInside(extra.regimens,extra.records)
        fun source(r:RawTreatmentInterval):String {
            val id=r.span.id
            return when {
                r.kind==SpanKind.SAVED->extra.regimens.firstOrNull{it.id==r.sourceId}.let{v->"app_saved_plan version=${r.sourceId} origin=${v?.origin} zone=${v?.zone} records_inside=${inside[r.sourceId] ?: 0}"}
                r.kind==SpanKind.USER || r.kind==SpanKind.FILL->"user_edit kind=${r.kind}"
                ObservedTreatmentHistory.isConfirmedSpan(id)->confirmedRows.entries.firstOrNull{(base,_)->id<=base && id>base-100}?.value
                    .let{row->"confirmed period_key=${row?.period_key} revision=${row?.revision} from_date=${row?.from_date} until_date=${row?.until_date} zone=${row?.zone}"}
                else->observed[id].let{o->"recognized_pending sources=${o?.records?.map{it.origin}?.distinct()?.sorted()} zones=${o?.records?.mapNotNull{it.taken_zone}?.distinct()?.sorted()}"}
            }
        }
        fun parts(d:DisplayPeriod)=p.raw.filter{it.span.from<(d.until ?: Instant.MAX) && (it.span.until ?: Instant.MAX)>d.from}.sortedWith(compareBy({it.span.from},{it.span.id}))
        fun describe(label:String,d:DisplayPeriod?)=buildList {
            if(d==null){add("[$label] none");return@buildList}
            add("[$label] from=${time(d.from)} until=${time(d.until)} segments=${d.segments.size} has_plan=${d.finalStandardSpanKeys.isNotEmpty()}")
            parts(d).forEach{r->
                add("  part id=${r.span.id} medication_id=${r.span.medicationId} source=${source(r)}")
                add("    from=${time(r.span.from)} until=${time(r.span.until)}")
                add("    standard: ${standard(r.standard)}")
                p.effective[r.span.id]?.takeIf{it!=r.standard}?.let{add("    compared_as (${if(r.span.id in p.absorbed)"shorter than 14 days, joined" else "correction"}): ${standard(it)}")}
            }
            p.stops.filter{it.from>=d.from && (d.until==null || it.from<d.until)}.forEach{add("  stop_in_app medication_id=${it.medicationId} from=${time(it.from)} until=${time(it.until)}")}
        }
        return buildList {
            add("HRT Log merge diagnostics v2 · versionCode=$versionCode · display_zone=${p.zone.id} · period ${index+1}/${p.periods.size}")
            addAll(describe("previous",previous));addAll(describe("this",period))
            val starting=p.standards.filter{it.key in period.segments.first().standardSpanKeys && it.from==period.segments.first().from}
            if(starting.isEmpty())add("[join] no plan part starts at this period's start")
            starting.forEach{b->
                val a=p.standards.filter{s->s.until?.let{u->u<=b.from}==true}.sortedWith(compareBy<TreatmentStandardSpan>{it.medicationId!=b.medicationId}.thenByDescending{it.until!!})
                    .firstOrNull()
                if(a==null){add("[join] ${b.key}: nothing before it");return@forEach}
                add("[join] ${a.key} -> ${b.key}")
                val checks=p.joinChecks(a,b)
                checks.forEach{add("  ${it.name}: ${if(it.passed)"PASS" else "FAIL"} (${it.detail})")}
                add("  result: "+(checks.firstOrNull{!it.passed}?.let{"NOT JOINED, first failing check = ${it.name}"} ?: "all checks pass"))
            }
        }.joinToString("\n")
    }
}
