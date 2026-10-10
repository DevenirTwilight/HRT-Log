package net.plainnotes.app.pk.experimental

import net.plainnotes.app.pk.Curve
import net.plainnotes.app.pk.DoseEvent
import net.plainnotes.app.pk.Engine
import net.plainnotes.app.pk.Ester
import net.plainnotes.app.pk.Route

/** Separate, opt-in visualization; neither the production model nor a clinical pg/mL series. */
data class ResearchShapeComparison(
    val timeHours: DoubleArray,
    /** Legacy sublingual E2 contribution divided by a synthetic 1mg, 1h legacy reference. */
    val legacyRelative: DoubleArray,
    /** Same actual timestamps/mg, but divided by the experimental candidate's own 1mg, 1h response. */
    val experimentalRelative: DoubleArray,
    val candidateId: String,
    val assumedPriceBaselinePgMl: Double,
    val matchingCandidateCount: Int,
    val consideredRecordedDoses: Int,
    val omittedOlderDoses: Int,
) {
    init {
        require(timeHours.size == legacyRelative.size && timeHours.size == experimentalRelative.size)
        require(timeHours.isNotEmpty() && timeHours.all { it.isFinite() })
        require(legacyRelative.all { it.isFinite() && it >= 0.0 })
        require(experimentalRelative.all { it.isFinite() && it >= 0.0 })
        require(consideredRecordedDoses > 0 && omittedOlderDoses >= 0)
    }
}

/**
 * Research-only comparison of SHAPES, never absolute concentrations.
 * Inputs are EITHER independently qualified historic DoseEvents, OR explicitly labelled
 * synthetic manual events in the DEBUG-only sandbox. Never fetch or infer user records.
 * No planned doses, laboratory values, patient amplitude, or API side effects.
 */
object ResearchShapeComparisonV01 {
    const val lookbackHours = 30.0 * 24.0
    const val chartHours = 48.0
    const val chartStepHours = 0.5
    val assumedPriceBaselines: List<Double> = listOf(0.0, 6.0, 12.0, 18.0, 24.0)

    fun compare(
        verifiedRecordedEvents: List<DoseEvent>,
        asOfHour: Double,
        assumedPriceBaselinePgMl: Double,
    ): ResearchShapeComparison? {
        require(asOfHour.isFinite())
        require(assumedPriceBaselinePgMl in assumedPriceBaselines)
        require(verifiedRecordedEvents.size <= 10_000)
        // Caller must establish historical eligibility, or use explicitly synthetic debug inputs.
        // Our route filter additionally protects against other medicines and EV.
        val history = verifiedRecordedEvents.filter {
            it.timeH.isFinite() && it.timeH <= asOfHour && it.route == Route.SUBLINGUAL &&
                it.ester == Ester.E2 && it.doseMG.isFinite() && it.doseMG > 0.0
        }
        val first = asOfHour - chartHours
        val recent = history.filter { it.timeH >= first - lookbackHours }
        if (recent.isEmpty()) return null
        val group = ResearchSublingualV01.candidates
            .filter { it.assumedPriceBaselinePgMl == assumedPriceBaselinePgMl }
        val selected = group.minWithOrNull(compareBy<ExperimentalCandidate> { it.exposedDataPseudoLoss }.thenBy { it.id })
            ?: return null
        val grid = DoubleArray((chartHours / chartStepHours).toInt() + 1) { first + it * chartStepHours }

        // The old model is evaluated for the SAME SL-only records; mixed-route production E2,
        // calibration and endogenous background are intentionally excluded.
        val old = Engine.simulate(recent, grid = grid)?.curves?.get(Curve.E2) ?: return null
        val standard = recent.maxBy { it.timeH }.copy(id = "research-reference", timeH = 0.0, doseMG = 1.0)
        val oldReference = Engine.simulate(listOf(standard), grid = doubleArrayOf(1.0))
            ?.curves?.get(Curve.E2)?.singleOrNull() ?: return null
        if (!oldReference.isFinite() || oldReference <= 0.0) return null
        val newDoses = recent.map { ExperimentalDose(it.timeH, it.doseMG) }
        val new = DoubleArray(grid.size) { ResearchSublingualV01.relativeHistory(grid[it], newDoses, selected) }
        val legacy = DoubleArray(old.size) { old[it] / oldReference }
        return ResearchShapeComparison(
            grid, legacy, new, selected.id, assumedPriceBaselinePgMl, group.size,
            recent.size, history.size - recent.size,
        )
    }
}
