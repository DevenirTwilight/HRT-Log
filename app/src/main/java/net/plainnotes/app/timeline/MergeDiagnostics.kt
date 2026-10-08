package net.plainnotes.app.timeline

import net.plainnotes.app.NotesViewModel.ExtraState
import net.plainnotes.app.domain.*
import net.plainnotes.app.data.HistoryPeriods
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
        val confirmedRows=timeline.confirmedHistory.associateBy{ObservedTreatmentHistory.CONFIRMED_SPAN_BASE-HistoryPeriods.stableId(extra.historyPeriods,it.period_key)*100}
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
        val versionLine="HRT Log merge diagnostics v3 · versionCode=$versionCode · display_zone=${p.zone.id} · period ${index+1}/${p.periods.size}"
        fun spansAt(d:DisplayPeriod,first:Boolean)=p.standards.filter{it.key in (if(first)d.segments.first().standardSpanKeys else d.finalStandardSpanKeys)}
        fun userRows(d:DisplayPeriod)=extra.historyPeriods.groupBy{it.period_key}.values.map{it.maxBy{r->r.revision}}.filter{r->r.kind in HistoryPeriods.USER_KINDS}.filter{r->
            val z=java.time.ZoneId.of(r.zone);val exact=runCatching{HistoryPeriods.exactBounds(r)}.getOrNull()
            val from=exact?.first?.let(Instant::ofEpochMilli) ?: java.time.LocalDate.parse(r.from_date).atStartOfDay(z).toInstant()
            val until=if(exact!=null)exact.second?.let(Instant::ofEpochMilli) else r.until_date?.let{java.time.LocalDate.parse(it).atStartOfDay(z).toInstant()}
            from<(d.until ?: Instant.MAX) && (until ?: Instant.MAX)>d.from}
        return buildList {
            add(versionLine)
            addAll(describe("previous",previous));addAll(describe("this",period))
            // Every user correction touching this period (active or undone), with exact bounds when it has them (§38).
            userRows(period).forEach{r->add("  user_edit kind=${r.kind} state=${r.state} medication_id=${r.medication_id} dates=${r.from_date}..${r.until_date ?: "open"} zone=${r.zone} " +
                "exact=${runCatching{HistoryPeriods.exactBounds(r)}.getOrNull()?.let{"${it.first}..${it.second ?: "open"}"} ?: "whole days"} group=${r.group_key} revision=${r.revision}")}
            // A plan shown where no part lies: it continues across a gap without records (fewer than 30 days).
            if(period.finalStandardSpanKeys.isNotEmpty() && parts(period).isEmpty())add("  note: plan continues across days with no part (record gap under 30 days)")
            if(previous==null){add("[boundary] first period");return@buildList}
            // Why a boundary exists at the start of this period (§38): every cause at every boundary instant of its first day.
            period.segments.map{it.from}.forEach{at->
                val causes=buildList {
                    p.standards.filter{it.from==at}.forEach{add("plan part starts (${it.key})")}
                    p.standards.filter{it.until==at}.forEach{add("plan part ends (${it.key})")}
                    p.userGaps.filter{it.from==at}.forEach{add("user ${it.kind} starts (medication ${it.medicationId})")}
                    p.userGaps.filter{it.until==at}.forEach{add("user ${it.kind} ends (medication ${it.medicationId})")}
                }
                add("[boundary] ${time(at)}: "+(causes.ifEmpty{listOf("none")}).joinToString("; "))
            }
            // Per medicine: the plan in effect just before and just after this period's start.
            val before=spansAt(previous,false).associateBy{it.medicationId};val after=spansAt(period,true).associateBy{it.medicationId}
            (before.keys+after.keys).sorted().forEach{lane->
                val a=before[lane];val b=after[lane]
                when {
                    a!=null && b!=null && a.key==b.key->add("[join] medication $lane: same plan continues (${a.key}); this medicine does not split here")
                    a!=null && b!=null->{
                        add("[join] medication $lane: ${a.key} -> ${b.key}")
                        val checks=p.joinChecks(a,b);checks.forEach{add("  ${it.name}: ${if(it.passed)"PASS" else "FAIL"} (${it.detail})")}
                        add("  result: "+(checks.firstOrNull{!it.passed}?.let{"NOT JOINED, first failing check = ${it.name}"} ?: "all checks pass"))
                    }
                    a!=null->add("[join] medication $lane: plan ends; after: "+(p.userGaps.filter{it.medicationId==lane && it.from<=period.from && (it.until ?: Instant.MAX)>period.from}
                        .map{"user ${it.kind} ${time(it.from)}..${time(it.until)}"}.ifEmpty{p.stops.filter{it.medicationId==lane && it.from<=period.from}.map{"stop ${time(it.from)}..${time(it.until)}"}}.ifEmpty{listOf("no plan or records")}).joinToString("; "))
                    b!=null->add("[join] medication $lane: plan starts (${b.key}); before: "+(p.userGaps.filter{it.medicationId==lane && it.until==period.from}.map{"user ${it.kind} ${time(it.from)}..${time(it.until)}"}
                        .ifEmpty{p.stops.filter{it.medicationId==lane && it.until==period.from}.map{"stopped in app ${time(it.from)}..${time(it.until)}"}}.ifEmpty{listOf("no plan")}).joinToString("; "))
                }
            }
        }.joinToString("\n")
    }
}
