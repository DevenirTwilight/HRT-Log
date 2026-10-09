package net.plainnotes.app.conc

import net.plainnotes.app.pk.*
import kotlin.math.*

/** Full saved-store read is not proof of all real-life exposures or of treatment initiation. */
data class HistoryRead(val completeSavedRead: Boolean = false, val throughH: Double = Double.NEGATIVE_INFINITY)
enum class HistoryCoverage { COMPLETE, INCOMPLETE, UNKNOWN }
enum class FrozenCoverage { KNOWN, INCOMPLETE, UNKNOWN }
enum class BaselineEligibility { NOT_PRE_TREATMENT, UNKNOWN }
enum class FitEligibility { ELIGIBLE, EXCLUDED, NEEDS_REVIEW }
enum class EligibilityReason { READ_SCOPE_UNKNOWN, SAMPLE_OUTSIDE_WINDOW, READ_END_BEFORE_SAMPLE, UNKNOWN_CONTEXT, UNKNOWN_DOSE, UNKNOWN_TIME, UNMODELLED_EXPOSURE, OMITTED_EXPOSURE, NO_TREATMENT_EVIDENCE, RESOURCE_LIMIT }
data class ExposureEvidence(val recordId: Long, val timeH: Double?, val ingredient: String?, val event: DoseEvent?, val included: Boolean, val problem: EligibilityReason? = null)
data class LabEligibility(val labId: Long, val sampleH: Double, val coverage: HistoryCoverage, val frozen: FrozenCoverage,
    val baseline: BaselineEligibility, val fit: FitEligibility, val reasons: Set<EligibilityReason>, val evidenceRecordIds: Set<Long>,
    val simulationFromH: Double, val readThroughH: Double, val omittedLogFractionBound: Double? = null) {
    val eligible: Boolean get() = fit == FitEligibility.ELIGIBLE
}

/** Deterministic eligibility only. Does not fit labs, change records, or use current medication settings. */
object CalibrationEligibility {
    const val MAX_FACTS = 50_000
    const val MAX_RESIDUAL_WORK = 200_000L
    const val MAX_OMITTED_FRACTION = 1e-6

    fun evaluate(samples: List<Pair<Long, Double>>, facts: List<ExposureEvidence>, fromH: Double, read: HistoryRead, checkCancelled: () -> Unit = {}): List<LabEligibility> {
        checkCancelled()
        val related = facts.filter { it.ingredient == null || it.ingredient == "E2" }
        val known = related.filter { it.ingredient == "E2" && it.timeH != null }.sortedBy { it.timeH }
        val firstKnown = known.firstOrNull()
        // Earliest blocker per reason suffices for all later samples; no N-lab DB queries.
        val blockers = related.filter { it.problem != null || it.event == null }.groupBy { it.problem ?: EligibilityReason.UNKNOWN_CONTEXT }
            .mapValues { (_, rows) -> rows.minBy { it.timeH ?: Double.NEGATIVE_INFINITY } }
        val omitted = related.filter { !it.included && it.event != null }.sortedBy { it.timeH }
        val includedSl = related.filter { it.included && it.event?.route == Route.SUBLINGUAL }
        val residualBudget = (omitted.size.toLong() + includedSl.size) * samples.size <= MAX_RESIDUAL_WORK
        return samples.map { (id, time) ->
            checkCancelled()
            val reasons = linkedSetOf<EligibilityReason>(); val evidence = linkedSetOf<Long>()
            val baseline = if (firstKnown?.timeH?.let { it <= time } == true) BaselineEligibility.NOT_PRE_TREATMENT else BaselineEligibility.UNKNOWN
            firstKnown?.takeIf { it.timeH!! <= time }?.let { evidence += it.recordId }
            var frozen = if (baseline == BaselineEligibility.NOT_PRE_TREATMENT) FrozenCoverage.KNOWN else FrozenCoverage.UNKNOWN
            if (!read.completeSavedRead) reasons += EligibilityReason.READ_SCOPE_UNKNOWN
            if (time < fromH) reasons += EligibilityReason.SAMPLE_OUTSIDE_WINDOW
            if (time > read.throughH) reasons += EligibilityReason.READ_END_BEFORE_SAMPLE
            if (related.size > MAX_FACTS) reasons += EligibilityReason.RESOURCE_LIMIT
            blockers.forEach { (reason, row) -> if (row.timeH == null || row.timeH <= time) {
                reasons += reason; evidence += row.recordId; frozen = FrozenCoverage.INCOMPLETE
            } }
            if (baseline == BaselineEligibility.UNKNOWN) reasons += EligibilityReason.NO_TREATMENT_EVIDENCE
            var logFraction: Double? = null
            if (omitted.firstOrNull()?.timeH?.let { it <= time } == true) {
                if (!residualBudget) reasons += EligibilityReason.RESOURCE_LIMIT
                else {
                    val relevantOmitted = omitted.takeWhile { it.timeH!! <= time }
                    if (relevantOmitted.any { it.event!!.route != Route.SUBLINGUAL }) reasons += EligibilityReason.OMITTED_EXPOSURE
                    else {
                        val reference = includedSl.sumOf { concentration(it.event!!, time) }
                        val upper = logSum(relevantOmitted.flatMap { boundLogTerms(it.event!!, time) })
                        logFraction = if (reference > 0) upper - ln(reference) else Double.POSITIVE_INFINITY
                        if (logFraction > ln(MAX_OMITTED_FRACTION)) reasons += EligibilityReason.OMITTED_EXPOSURE
                    }
                    if (EligibilityReason.OMITTED_EXPOSURE in reasons) evidence += relevantOmitted.first().recordId
                }
            }
            val unknown = reasons.any { it in setOf(EligibilityReason.READ_SCOPE_UNKNOWN, EligibilityReason.NO_TREATMENT_EVIDENCE, EligibilityReason.RESOURCE_LIMIT) }
            val coverage = when { reasons.isEmpty() -> HistoryCoverage.COMPLETE; unknown -> HistoryCoverage.UNKNOWN; else -> HistoryCoverage.INCOMPLETE }
            if (!read.completeSavedRead && frozen == FrozenCoverage.KNOWN) frozen = FrozenCoverage.UNKNOWN
            LabEligibility(id,time,coverage,frozen,baseline,when { reasons.isEmpty() -> FitEligibility.ELIGIBLE; unknown -> FitEligibility.NEEDS_REVIEW; else -> FitEligibility.EXCLUDED },reasons,evidence,fromH,read.throughH,logFraction)
        }
    }

    private fun concentration(e: DoseEvent, time: Double): Double {
        if (time < e.timeH) return 0.0
        val choice = Engine.choose(e) as? ModelChoice.Use ?: return 0.0
        return choice.parts.filter { it.first == Curve.E2 }.sumOf { (_, m, share) -> share * m.response(time-e.timeH,e.doseMG,e.weightKG) }
    }
    /** Triangle bound, in log domain: sum |A|*(exp(-lambda*t)+exp(-ka*t)). SL has no dose-dependent ka. */
    private fun boundLogTerms(e: DoseEvent, time: Double): List<Double> {
        val choice = Engine.choose(e) as ModelChoice.Use
        return choice.parts.filter { it.first == Curve.E2 }.flatMap { (_, m, share) ->
            if (share <= 0) emptyList() else m.terms.flatMap { (a, k) ->
                if (a == 0.0) emptyList() else {
                    val amplitude = ln(e.doseMG) + ln(share) + ln(abs(a))
                    listOf(amplitude-k*(time-e.timeH),amplitude-m.ka*(time-e.timeH))
                }
            }
        }
    }
    private fun logSum(values: List<Double>): Double {
        val top = values.maxOrNull() ?: return Double.NEGATIVE_INFINITY
        return top + ln(values.sumOf { exp(it-top) })
    }
}
