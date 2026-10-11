package net.plainnotes.app.pk.experimental

import net.plainnotes.app.pk.DoseEvent
import net.plainnotes.app.pk.Ester
import net.plainnotes.app.pk.Route

/**
 * Read-only input for the opt-in "experimental PK model" page. Dimensionless, never pg/mL.
 *
 * - [legacyRelative]: the production legacy engine on the SAME sublingual E2 events, divided by the legacy
 *   response to 1 mg at 1 h (sublingual tier of the latest event). Taken verbatim from [ResearchShapeComparisonV01].
 * - [m2Relative]: ONE frozen candidate, divided by that candidate's own 1 mg response at 1 h.
 * - [m2Minimum]/[m2Maximum]: pointwise range over the 15 frozen candidates. A scenario range of an explored
 *   parameter set, NOT a confidence/credible interval, and narrower than the long-tail uncertainty in P2-AF.
 * The two normalizations are independent: equal heights do NOT mean equal concentrations.
 */
class ExperimentalSlView(
    /** Absolute hours since the epoch, ascending, ending at the evaluation time (no forecast). */
    val timeHours: DoubleArray,
    val legacyRelative: DoubleArray,
    val m2Relative: DoubleArray,
    val m2Minimum: DoubleArray,
    val m2Maximum: DoubleArray,
    /** True where more than [ExperimentalSlModelView.TAIL_AFTER_HOURS] have passed since the latest dose at or before t. */
    val longTail: BooleanArray,
    val candidate: ExperimentalCandidate,
    val consideredDoses: Int,
    val omittedOlderDoses: Int,
    val latestDoseHour: Double,
) {
    init {
        val n = timeHours.size
        require(n > 0 && legacyRelative.size == n && m2Relative.size == n && m2Minimum.size == n && m2Maximum.size == n && longTail.size == n)
        require((0 until n).all { m2Minimum[it] <= m2Relative[it] && m2Relative[it] <= m2Maximum[it] })
    }
}

object ExperimentalSlModelView {
    /** P2-AF: after ~8 h without a new dose the shape is dominated by unidentified slow/elimination rates. */
    const val TAIL_AFTER_HOURS = 8.0
    const val WINDOW_HOURS = ResearchShapeComparisonV01.chartHours
    const val LOOKBACK_HOURS = ResearchShapeComparisonV01.lookbackHours

    /** Frozen candidates in ID order. The order carries NO accuracy ranking. */
    val candidates: List<ExperimentalCandidate> get() = ResearchSublingualV01.candidates

    fun candidate(id: String): ExperimentalCandidate? = candidates.firstOrNull { it.id == id }

    /** Same filter the research comparator applies; kept identical so both models see the same doses. */
    private fun eligible(e: DoseEvent, asOfHour: Double) = e.timeH.isFinite() && e.timeH <= asOfHour &&
        e.route == Route.SUBLINGUAL && e.ester == Ester.E2 && e.doseMG.isFinite() && e.doseMG > 0.0

    /** Returns null when no sublingual E2 dose lies in the window or its look-back. Deterministic for equal inputs. */
    fun compute(events: List<DoseEvent>, asOfHour: Double, candidateId: String): ExperimentalSlView? {
        val selected = requireNotNull(candidate(candidateId)) { "Unknown candidate" }
        val comparison = ResearchShapeComparisonV01.compare(events, asOfHour, selected.assumedPriceBaselinePgMl) ?: return null
        val first = asOfHour - WINDOW_HOURS
        val doses = events.filter { eligible(it, asOfHour) && it.timeH >= first - LOOKBACK_HOURS }
            .sortedBy { it.timeH }.map { ExperimentalDose(it.timeH, it.doseMG) }
        check(doses.size == comparison.consideredRecordedDoses)
        val grid = comparison.timeHours
        val series = ResearchSublingualV01.coherentCandidateSeries(grid.toList(), doses)
        val chosen = series.single { it.candidateId == selected.id }.relativeValues
        val minimum = DoubleArray(grid.size) { i -> series.minOf { it.relativeValues[i] } }
        val maximum = DoubleArray(grid.size) { i -> series.maxOf { it.relativeValues[i] } }
        val tail = BooleanArray(grid.size) { i ->
            val latest = doses.lastOrNull { it.atHour <= grid[i] }?.atHour
            latest == null || grid[i] - latest > TAIL_AFTER_HOURS
        }
        return ExperimentalSlView(
            grid, comparison.legacyRelative, chosen.toDoubleArray(), minimum, maximum, tail, selected,
            comparison.consideredRecordedDoses, comparison.omittedOlderDoses, doses.last().atHour,
        )
    }
}
