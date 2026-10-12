package net.plainnotes.app.pk.experimental

import net.plainnotes.app.pk.Curve
import net.plainnotes.app.pk.DoseEvent
import net.plainnotes.app.pk.Engine
import net.plainnotes.app.pk.Ester
import net.plainnotes.app.pk.Route

/**
 * Quantitative Legacy-vs-M2 comparison on ONE set of sublingual E2 doses and ONE time grid (REQUIREMENTS §57).
 *
 * Unit of every relative value: multiples of the model's own response 1 h after a single 1 mg dose,
 * i.e. Σ (Dᵢ / 1 mg) · h(t − tᵢ) / h(1 h). It scales with dose, is not a concentration, and the two models are
 * normalized separately, so a difference between them is a difference in shape, never in pg/mL.
 */
class ModelMetrics(
    /** Highest value in [origin, origin + 24 h] of the total response (earlier and later doses included). */
    val peak: Double,
    /** Time of [peak] after the origin dose; with overlapping doses this is NOT a single-dose Tmax. */
    val peakHoursAfterOrigin: Double,
    /** Trapezoid area of the total response from the origin over 4, 8 and 24 h, unit "(× 1 mg/1 h response)·h". */
    val auc0to4: Double, val auc0to8: Double, val auc0to24: Double,
    val at8h: Double, val at12h: Double, val at24h: Double,
    /** Model property: time of the maximum after ONE isolated 1 mg dose (1-minute grid, 0–12 h). */
    val singleDoseTmaxHours: Double,
) {
    init {
        require(listOf(peak, peakHoursAfterOrigin, auc0to4, auc0to8, auc0to24, at8h, at12h, at24h, singleDoseTmaxHours).all { it.isFinite() && it >= 0.0 })
        require(auc0to4 <= auc0to8 && auc0to8 <= auc0to24)
    }
}

class ModelComparisonResult(
    /** Absolute hours since the epoch, ascending, equally spaced. */
    val timeHours: DoubleArray,
    val legacy: DoubleArray,
    val m2: DoubleArray,
    val m2Minimum: DoubleArray,
    val m2Maximum: DoubleArray,
    /** True where more than [ConcentrationModelComparison.TAIL_AFTER_HOURS] have passed since the latest dose at or before t. */
    val longTail: BooleanArray,
    /** Every dose both models used, as (absolute hour, mg). */
    val doses: List<Pair<Double, Double>>,
    val omittedOlderDoses: Int,
    val originHour: Double,
    val candidate: ExperimentalCandidate,
    /** Sublingual tier whose 1 mg/1 h legacy response normalizes the legacy curve (tier of the latest dose). */
    val legacyReferenceTier: Double?,
    val legacyMetrics: ModelMetrics,
    val m2Metrics: ModelMetrics,
    /** Dose-selection window; [ConcentrationModelComparison.valuesAt] reuses it so read-outs use identical inputs. */
    internal val inputFromHour: Double,
    internal val inputToHour: Double,
) {
    init {
        val n = timeHours.size
        require(n >= 2 && listOf(legacy, m2, m2Minimum, m2Maximum).all { it.size == n } && longTail.size == n)
        require((0 until n).all { m2Minimum[it] <= m2[it] && m2[it] <= m2Maximum[it] })
        require(doses.isNotEmpty())
    }
}

/** Exact values of both models at one instant, for the tap read-out. */
class ModelValuesAt(val hour: Double, val legacy: Double, val m2: Double, val m2Minimum: Double, val m2Maximum: Double, val longTail: Boolean)

object ConcentrationModelComparison {
    const val TAIL_AFTER_HOURS = 8.0
    const val LOOKBACK_HOURS = 30.0 * 24.0
    const val METRIC_WINDOW_HOURS = 24.0
    /** 1-minute metric grid; convergence against finer grids is verified in tests. */
    const val METRIC_STEP_HOURS = 1.0 / 60.0
    const val MAX_DISPLAY_POINTS = 2001

    private fun eligible(e: DoseEvent) = e.timeH.isFinite() && e.route == Route.SUBLINGUAL && e.ester == Ester.E2 && e.doseMG.isFinite() && e.doseMG > 0.0

    private class Inputs(val doses: List<DoseEvent>, val omitted: Int, val reference: DoseEvent, val legacyScale: Double)

    /** Doses that can influence [fromHour, toHour]: recorded up to toHour and no older than the 30-day look-back. */
    private fun inputs(events: List<DoseEvent>, fromHour: Double, toHour: Double): Inputs? {
        val all = events.filter(::eligible).filter { it.timeH <= toHour }.sortedBy { it.timeH }
        val doses = all.filter { it.timeH >= fromHour - LOOKBACK_HOURS }
        if (doses.isEmpty()) return null
        // Same legacy normalization as ResearchShapeComparisonV01: 1 mg, 1 h, sublingual tier of the latest dose.
        val reference = doses.last().copy(id = "comparison-reference", timeH = 0.0, doseMG = 1.0)
        val scale = Engine.simulate(listOf(reference), grid = doubleArrayOf(1.0))?.curves?.get(Curve.E2)?.singleOrNull() ?: return null
        if (!scale.isFinite() || scale <= 0.0) return null
        return Inputs(doses, all.size - doses.size, reference, scale)
    }

    private fun legacy(inputs: Inputs, grid: DoubleArray): DoubleArray {
        val curve = Engine.simulate(inputs.doses, grid = grid)?.curves?.get(Curve.E2) ?: DoubleArray(grid.size)
        return DoubleArray(grid.size) { curve[it] / inputs.legacyScale }
    }

    private fun m2Doses(inputs: Inputs) = inputs.doses.map { ExperimentalDose(it.timeH, it.doseMG) }

    private fun series(inputs: Inputs, grid: DoubleArray) = ResearchSublingualV01.coherentCandidateSeries(grid.toList(), m2Doses(inputs))

    private fun equalGrid(from: Double, to: Double, step: Double): DoubleArray {
        val n = Math.round((to - from) / step).toInt()
        return DoubleArray(n + 1) { from + it * (to - from) / n }
    }

    private fun trapezoid(grid: DoubleArray, y: DoubleArray, untilIndex: Int): Double {
        var sum = 0.0
        for (i in 1..untilIndex) sum += (grid[i] - grid[i - 1]) * (y[i] + y[i - 1]) / 2.0
        return sum
    }

    private fun singleDoseTmax(values: (DoubleArray) -> DoubleArray): Double {
        val grid = equalGrid(0.0, 12.0, METRIC_STEP_HOURS)
        val v = values(grid)
        return grid[v.indices.maxBy { v[it] }]
    }

    private fun metrics(grid: DoubleArray, y: DoubleArray, singleTmax: Double): ModelMetrics {
        val perHour = Math.round(1.0 / METRIC_STEP_HOURS).toInt()
        val peakIndex = y.indices.maxBy { y[it] }
        return ModelMetrics(
            peak = y[peakIndex], peakHoursAfterOrigin = grid[peakIndex] - grid[0],
            auc0to4 = trapezoid(grid, y, 4 * perHour), auc0to8 = trapezoid(grid, y, 8 * perHour), auc0to24 = trapezoid(grid, y, 24 * perHour),
            at8h = y[8 * perHour], at12h = y[12 * perHour], at24h = y[24 * perHour], singleDoseTmaxHours = singleTmax,
        )
    }

    private fun tail(grid: DoubleArray, doses: List<DoseEvent>) = BooleanArray(grid.size) { i ->
        val latest = doses.lastOrNull { it.timeH <= grid[i] }?.timeH
        latest == null || grid[i] - latest > TAIL_AFTER_HOURS
    }

    /**
     * Both curves over [displayStart, displayEnd], plus metrics over [originHour, originHour + 24 h].
     * Only recorded doses are used: a window reaching past the last dose shows decay with no further dose assumed.
     * Returns null when no sublingual E2 dose can influence the window. Deterministic for equal inputs.
     */
    fun compute(events: List<DoseEvent>, displayStart: Double, displayEnd: Double, originHour: Double, candidateId: String): ModelComparisonResult? {
        require(displayStart.isFinite() && displayEnd.isFinite() && displayEnd > displayStart && originHour.isFinite())
        require(events.size <= 10_000)
        val candidate = requireNotNull(ExperimentalSlModelView.candidate(candidateId)) { "Unknown candidate" }
        val metricEnd = originHour + METRIC_WINDOW_HOURS
        val from = minOf(displayStart, originHour); val to = maxOf(displayEnd, metricEnd)
        val inputs = inputs(events, from, to) ?: return null
        val span = displayEnd - displayStart
        val grid = equalGrid(displayStart, displayEnd, maxOf(METRIC_STEP_HOURS, span / (MAX_DISPLAY_POINTS - 1)))
        val all = series(inputs, grid)
        val chosen = all.single { it.candidateId == candidate.id }.relativeValues.toDoubleArray()

        val metricGrid = equalGrid(originHour, metricEnd, METRIC_STEP_HOURS)
        val legacyMetricValues = legacy(inputs, metricGrid)
        val m2MetricValues = ResearchSublingualV01.coherentCandidateSeries(metricGrid.toList(), m2Doses(inputs))
            .single { it.candidateId == candidate.id }.relativeValues.toDoubleArray()
        val reference = Inputs(listOf(inputs.reference), 0, inputs.reference, inputs.legacyScale)
        val legacyTmax = singleDoseTmax { legacy(reference, it) }
        val m2Tmax = singleDoseTmax { g -> DoubleArray(g.size) { ResearchSublingualV01.relativeIncrement(g[it], candidate) } }

        return ModelComparisonResult(
            grid, legacy(inputs, grid), chosen,
            DoubleArray(grid.size) { i -> all.minOf { it.relativeValues[i] } },
            DoubleArray(grid.size) { i -> all.maxOf { it.relativeValues[i] } },
            tail(grid, inputs.doses), inputs.doses.map { it.timeH to it.doseMG }, inputs.omitted, originHour, candidate,
            inputs.reference.extras.sublingualTier,
            metrics(metricGrid, legacyMetricValues, legacyTmax), metrics(metricGrid, m2MetricValues, m2Tmax), from, to,
        )
    }

    /** Exact kernel values at [hour] (no interpolation), with the same doses, candidate and normalization as [result]. */
    fun valuesAt(events: List<DoseEvent>, result: ModelComparisonResult, hour: Double): ModelValuesAt? {
        require(hour.isFinite())
        val candidate = result.candidate
        val inputs = inputs(events, result.inputFromHour, result.inputToHour) ?: return null
        check(inputs.doses.map { it.timeH to it.doseMG } == result.doses) { "Inputs changed since the comparison was computed" }
        val doses = m2Doses(inputs)
        val all = ResearchSublingualV01.candidates.map { ResearchSublingualV01.relativeHistory(hour, doses, it) }
        val active = inputs.doses.filter { it.timeH <= hour }
        return ModelValuesAt(hour, legacy(inputs, doubleArrayOf(hour))[0], ResearchSublingualV01.relativeHistory(hour, doses, candidate),
            all.min(), all.max(), tail(doubleArrayOf(hour), active)[0])
    }

    /** Meaningful only for strictly positive finite denominators; null otherwise. */
    fun ratio(numerator: Double, denominator: Double): Double? =
        if (numerator.isFinite() && denominator.isFinite() && denominator > 1e-9) numerator / denominator else null
}
