package net.plainnotes.app.pk

import java.util.Random
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class CalibrationMode { RETROSPECTIVE, CAUSAL }

/**
 * Personal adjustment learned from estradiol labs (docs/pk-model.md, "化验校准"). Shared amplitude applies to estradiol events: amplitude e^[logAmplitude]; rate e^[logRate] applies only to non-SL events.
 * Pure SL fits have fixed logRate=0 and a zero second covariance dimension. Neither factor is a validated physiological clearance.
 * [cov] is the posterior covariance of
 * (logAmplitude, logRate) as [[a, b], [b, d]].
 */
data class LabFitModel(
    val logAmplitude: Double = 0.0,
    val logRate: Double = 0.0,
    val cov: DoubleArray = doubleArrayOf(LabFit.PRIOR_SD_AMPLITUDE_DEFAULT.let { it * it }, 0.0, 0.0, LabFit.PRIOR_SD_RATE * LabFit.PRIOR_SD_RATE),
    /** Estradiol baseline from independently confirmed pre-treatment lab IDs only (pg/mL), added to the curve. */
    val baselinePGmL: Double? = null,
    val postDoseObservationCount: Int = 0,
    /** Labs left out as outliers (more than 4-fold off the fitted curve). */
    val excludedLabIds: Set<String> = emptySet(),
    val priorSdAmplitude: Double = LabFit.PRIOR_SD_AMPLITUDE_DEFAULT,
    val rateAdjustable: Boolean = true,
    val algorithmVersion: Int = LabFit.ALGORITHM_VERSION,
) {
    /** 0 = labs taught nothing yet, 1 = amplitude fully determined by labs. */
    val convergenceScore: Double get() = (1 - cov[0] / (priorSdAmplitude * priorSdAmplitude)).coerceIn(0.0, 1.0)
}

/** How the most recent lab compares with the prediction made from the labs before it. */
data class LabDiagnostics(val predictedPGmL: Double, val observedPGmL: Double, val residualLog: Double, val isOutlier: Boolean, val convergenceScore: Double)

/** Curve with its 5 %–95 % and 25 %–75 % bands (Monte Carlo). */
class BandedCurve(val timeH: DoubleArray, val center: DoubleArray, val p5: DoubleArray, val p25: DoubleArray, val p75: DoubleArray, val p95: DoubleArray)

/** Query-specific center, quantiles and fit, from one sample-time prefix. */
data class LabEvaluation(val timeH: Double, val center: Double, val p5: Double, val p25: Double, val p75: Double, val p95: Double,
    val model: LabFitModel, val diagnostics: LabDiagnostics?, val labCount: Int)

object LabFit {
    /** Version 1 scaled all E2 rates; version 2 fixes entire SL events and estimates their amplitude only. */
    const val ALGORITHM_VERSION = 2

    fun rateAdjustable(events: List<DoseEvent>): Boolean = events.any { e ->
        e.route != Route.SUBLINGUAL && e.route != Route.PATCH_REMOVE &&
            (Engine.choose(e) as? ModelChoice.Use)?.parts?.any { it.first == Curve.E2 } == true
    }

    /** Within-person variability of a lab around the curve: Zhang 2024 intra-individual CV of E2 Cmax 29.9 %. */
    val SIGMA_LAB = sqrt(ln(1 + 0.299 * 0.299))
    /** Assumed non-SL model-rate spread: half-life CV 29 % (oral EV, Zhang 2024); not a validated physiological clearance prior. */
    val PRIOR_SD_RATE = sqrt(ln(1 + 0.29 * 0.29))
    val PRIOR_SD_AMPLITUDE_DEFAULT = sqrt(ln(1 + 0.48 * 0.48))
    /** A lab more than 4-fold away from the fitted curve is treated as an outlier. */
    val OUTLIER_LOG = ln(4.0)
    const val SAMPLES = 200

    fun toPgMl(value: Double, unit: LabUnit) = if (unit == LabUnit.PG_ML) value else value / Pk.PMOL_PER_PG
    private fun sdOf(cv: Double) = sqrt(ln(1 + cv * cv))

    /** Prior spread of the amplitude: the largest between-person CV among the estradiol models in use. */
    fun priorSdAmplitude(events: List<DoseEvent>): Double {
        if (events.isEmpty()) return PRIOR_SD_AMPLITUDE_DEFAULT
        val r = Engine.simulate(events, grid = doubleArrayOf(events.minOf { it.timeH })) ?: return PRIOR_SD_AMPLITUDE_DEFAULT
        return r.models[Curve.E2]?.maxOfOrNull { sdOf(it.cv) } ?: PRIOR_SD_AMPLITUDE_DEFAULT
    }

    private fun e2At(events: List<DoseEvent>, times: DoubleArray, u: Double, v: Double): DoubleArray {
        val e2Events = events.filter { e -> (Engine.choose(e) as? ModelChoice.Use)?.parts?.any { it.first == Curve.E2 } == true || e.route == Route.PATCH_REMOVE }
        if (e2Events.none { it.route != Route.PATCH_REMOVE }) return DoubleArray(times.size)
        val r = Engine.simulate(e2Events, grid = times) { c, _ -> if (c == Curve.E2) Scale(exp(u), exp(v)) else Scale() } ?: return DoubleArray(times.size)
        return r.curves[Curve.E2] ?: DoubleArray(times.size)
    }

    /** Fit using labs up to [untilH] (inclusive). */
    fun fit(events: List<DoseEvent>, labs: List<LabResult>, untilH: Double = Double.POSITIVE_INFINITY, confirmedBaselineLabIds: Set<String> = emptySet()): LabFitModel {
        val sdA = priorSdAmplitude(events)
        val firstDose = events.filter { it.route != Route.PATCH_REMOVE && (Engine.choose(it) as? ModelChoice.Use)?.parts?.any { part -> part.first == Curve.E2 } == true }.minOfOrNull { it.timeH } ?: return LabFitModel(priorSdAmplitude = sdA)
        val used = labs.filter { it.timeH <= untilH }.sortedWith(compareBy<LabResult> { it.timeH }.thenBy { it.id })
        // Caller must independently confirm pre-treatment status; event-list start is not evidence.
        val baselineLabs = used.filter { it.id in confirmedBaselineLabIds && it.timeH < firstDose && it.concValue > 0 }
        val baseline = baselineLabs.takeIf { it.isNotEmpty() }?.map { toPgMl(it.concValue, it.unit) }?.average()
        var post = used.filter { it.timeH >= firstDose && it.concValue > 0 }
        var model = solve(events, post, baseline ?: 0.0, sdA)
        // One pass of outlier removal: drop labs more than 4-fold off the fitted curve, then refit.
        val pred = e2At(events, post.map { it.timeH }.toDoubleArray(), model.logAmplitude, model.logRate)
        val outliers = post.filterIndexed { i, l -> abs(ln(toPgMl(l.concValue, l.unit)) - ln(pred[i] + (baseline ?: 0.0) + 1e-9)) > OUTLIER_LOG }.map { it.id }.toSet()
        if (outliers.isNotEmpty() && outliers.size < post.size) { post = post.filter { it.id !in outliers }; model = solve(events, post, baseline ?: 0.0, sdA) }
        return model.copy(baselinePGmL = baseline, postDoseObservationCount = post.size, excludedLabIds = outliers)
    }

    /** Maximum a posteriori (u, v) by damped Gauss–Newton; covariance from the Laplace approximation. */
    private fun solve(events: List<DoseEvent>, labs: List<LabResult>, baseline: Double, sdA: Double): LabFitModel {
        if (!rateAdjustable(events)) return solveAmplitude(events, labs, baseline, sdA)
        val prior = doubleArrayOf(sdA * sdA, 0.0, 0.0, PRIOR_SD_RATE * PRIOR_SD_RATE)
        if (labs.isEmpty()) return LabFitModel(cov = prior, priorSdAmplitude = sdA)
        val times = labs.map { it.timeH }.toDoubleArray()
        val y = labs.map { ln(toPgMl(it.concValue, it.unit)) }.toDoubleArray()
        fun residuals(u: Double, v: Double): DoubleArray {
            val mu = e2At(events, times, u, v)
            val r = DoubleArray(y.size + 2)
            for (i in y.indices) r[i] = (y[i] - ln(mu[i] + baseline + 1e-9)) / SIGMA_LAB
            r[y.size] = -u / sdA; r[y.size + 1] = -v / PRIOR_SD_RATE
            return r
        }
        var u = 0.0; var v = 0.0; val h = 1e-4
        var jtj = DoubleArray(4)
        repeat(30) {
            val r0 = residuals(u, v); val ru = residuals(u + h, v); val rv = residuals(u, v + h)
            var a = 0.0; var b = 0.0; var d = 0.0; var gu = 0.0; var gv = 0.0
            for (i in r0.indices) {
                val ju = -(ru[i] - r0[i]) / h; val jv = -(rv[i] - r0[i]) / h   // derivative of the model side
                a += ju * ju; b += ju * jv; d += jv * jv; gu += ju * r0[i]; gv += jv * r0[i]
            }
            jtj = doubleArrayOf(a, b, b, d)
            val det = a * d - b * b
            if (det <= 1e-12) return@repeat
            val du = (d * gu - b * gv) / det; val dv = (a * gv - b * gu) / det
            val stepU = du.coerceIn(-1.0, 1.0); val stepV = dv.coerceIn(-1.0, 1.0)
            u += stepU; v += stepV
            if (abs(stepU) + abs(stepV) < 1e-6) return@repeat
        }
        val det = jtj[0] * jtj[3] - jtj[1] * jtj[2]
        val cov = if (det > 1e-12) doubleArrayOf(jtj[3] / det, -jtj[1] / det, -jtj[2] / det, jtj[0] / det) else prior
        return LabFitModel(u.coerceIn(-3.0, 3.0), v.coerceIn(-2.0, 2.0), cov, priorSdAmplitude = sdA)
    }

    /** One-dimensional MAP. No rate prior or latent rate draw for a pure SL history.
     * Backtracking ensures the objective decreases; covariance is evaluated at the returned mode.
     * No concentration cap, forced zero, or interval clipping is used. */
    private fun solveAmplitude(events: List<DoseEvent>, labs: List<LabResult>, baseline: Double, sdA: Double): LabFitModel {
        val priorVariance = sdA * sdA
        fun model(u: Double, variance: Double) = LabFitModel(logAmplitude = u,
            cov = doubleArrayOf(variance, 0.0, 0.0, 0.0), priorSdAmplitude = sdA, rateAdjustable = false)
        if (labs.isEmpty()) return model(0.0, priorVariance)
        val population = e2At(events, labs.map { it.timeH }.toDoubleArray(), 0.0, 0.0)
        val observations = labs.map { ln(toPgMl(it.concValue, it.unit)) }
        val noiseVariance = SIGMA_LAB * SIGMA_LAB
        fun objective(u: Double): Double {
            var loss = u * u / priorVariance
            for (i in observations.indices) {
                val residual = observations[i] - ln(population[i] * exp(u) + baseline + 1e-9)
                loss += residual * residual / noiseVariance
            }
            return loss / 2
        }
        fun derivatives(u: Double): Pair<Double, Double> {
            var gradient = -u / priorVariance
            var information = 1 / priorVariance
            for (i in observations.indices) {
                val mu = population[i] * exp(u)
                val total = mu + baseline + 1e-9
                val derivative = mu / total
                gradient += derivative * (observations[i] - ln(total)) / noiseVariance
                information += derivative * derivative / noiseVariance
            }
            return gradient to information
        }
        var u = 0.0
        for (iteration in 0 until 100) {
            val (gradient, information) = derivatives(u)
            // A step bound is numerical damping, not a bound on the parameter or concentration.
            var step = (gradient / information).coerceIn(-1.0, 1.0)
            if (abs(step) < 1e-9) break
            val loss = objective(u)
            var accepted = false
            for (attempt in 0 until 40) {
                val next = u + step
                if (objective(next) < loss) { u = next; accepted = true; break }
                step *= 0.5
            }
            if (!accepted) break
        }
        return model(u, 1 / derivatives(u).second)
    }

    /** Diagnostics for the most recent lab against the fit from the labs before it. */
    fun lastDiagnostics(events: List<DoseEvent>, labs: List<LabResult>, confirmedBaselineLabIds: Set<String> = emptySet()): LabDiagnostics? {
        val sorted = labs.sortedWith(compareBy<LabResult> { it.timeH }.thenBy { it.id })
        val last = sorted.lastOrNull() ?: return null
        if (events.isEmpty()) return null
        val before = fit(events, sorted.filter { it.timeH < last.timeH }, last.timeH, confirmedBaselineLabIds)
        val pred = e2At(events, doubleArrayOf(last.timeH), before.logAmplitude, before.logRate)[0] + (before.baselinePGmL ?: 0.0)
        val obs = toPgMl(last.concValue, last.unit)
        val res = ln(obs) - ln(pred + 1e-9)
        return LabDiagnostics(pred, obs, res, abs(res) > OUTLIER_LOG, fit(events, sorted, confirmedBaselineLabIds = confirmedBaselineLabIds).convergenceScore)
    }

    /**
     * Monte Carlo bands for every curve. Estradiol uses parameter uncertainty only (not predictive error or structural uncertainty), other curves
     * use their model's between-person variability. Fixed seed so the bands do not flicker between redraws.
     */
    fun bands(events: List<DoseEvent>, grid: DoubleArray, labs: List<LabResult>, mode: CalibrationMode, samples: Int = SAMPLES, confirmedBaselineLabIds: Set<String> = emptySet(), checkCancelled: () -> Unit = {}): Map<Curve, BandedCurve> =
        bandsWithFit(events,grid,labs,mode,samples,confirmedBaselineLabIds,checkCancelled,null)

    private fun bandsWithFit(events: List<DoseEvent>, grid: DoubleArray, labs: List<LabResult>, mode: CalibrationMode, samples: Int, confirmedBaselineLabIds: Set<String>, checkCancelled: () -> Unit, fixedFit: LabFitModel?): Map<Curve, BandedCurve> {
        require(samples > 0 && grid.all { it.isFinite() } && (1 until grid.size).all { grid[it-1] <= grid[it] })
        checkCancelled()
        val fits = fixedFit?.let { listOf(Double.NEGATIVE_INFINITY to it) } ?: segmentFits(events, labs, mode, grid, confirmedBaselineLabIds, checkCancelled)
        val base = Engine.simulate(events, grid = grid) ?: return emptyMap()
        val fixedPopulation = base.curves[Curve.E2]?.takeIf { !rateAdjustable(events) }
        val center = HashMap<Curve, DoubleArray>()
        for ((curve, arr) in base.curves) center[curve] = if (curve == Curve.E2) e2Curve(events, grid, fits, fixedPopulation) { it.logAmplitude to it.logRate } else arr
        val draws = HashMap<Curve, Array<DoubleArray>>()
        base.curves.keys.forEach { draws[it] = Array(grid.size) { DoubleArray(samples) } }
        val rnd = Random(20261006L)
        // Non-estradiol curves are sampled from their own events only (estradiol is handled by the lab fit).
        val otherEvents = events.filter { e -> (Engine.choose(e) as? ModelChoice.Use)?.parts?.any { it.first != Curve.E2 } == true }
        for (k in 0 until samples) {
            checkCancelled()
            val z = DoubleArray(4) { rnd.nextGaussian() }
            // Estradiol: correlated draw from each segment's posterior.
            val e2 = e2Curve(events, grid, fits, fixedPopulation) { m ->
                val l0 = sqrt(max(m.cov[0], 0.0)); val l1 = if (l0 > 0) m.cov[1] / l0 else 0.0; val l2 = sqrt(max(m.cov[3] - l1 * l1, 0.0))
                (m.logAmplitude + l0 * z[0]) to (m.logRate + l1 * z[0] + l2 * z[1])
            }
            val others = if (base.curves.keys.any { it != Curve.E2 }) Engine.simulate(otherEvents, grid = grid) { _, m ->
                Scale(exp(sdOf(m.cv) * z[2] - sdOf(m.cv).let { it * it } / 2), exp(sdOf(m.rateCv) * z[3]))
            } else null
            for ((curve, arr) in draws) {
                val src = if (curve == Curve.E2) e2 else others!!.curves.getValue(curve)
                for (i in grid.indices) arr[i][k] = src[i]
            }
        }
        return draws.mapValues { (curve, arr) ->
            val p5 = DoubleArray(grid.size); val p25 = DoubleArray(grid.size); val p75 = DoubleArray(grid.size); val p95 = DoubleArray(grid.size)
            fun idx(p: Double) = min(samples - 1, (p * (samples - 1)).toInt())
            for (i in grid.indices) {
                val s = arr[i]; s.sort()   // sort each point's samples once
                p5[i] = s[idx(0.05)]; p25[i] = s[idx(0.25)]; p75[i] = s[idx(0.75)]; p95[i] = s[idx(0.95)]
            }
            BandedCurve(grid, center.getValue(curve), p5, p25, p75, p95)
        }
    }

    /** P1-C1: caller supplies history-qualified labs. Sample-time availability is enforced here too.
     * Two bracketing population-grid points use ONE query-specific fit; never interpolate two causal fits.
     * Preserves the existing population interpolation error; outside the grid evaluates the requested point.
     * Only 1/2 points are sampled, not 200 complete long-history curves. */
    fun evaluateAt(events: List<DoseEvent>, populationGrid: DoubleArray, qualifiedLabs: List<LabResult>, timeH: Double,
        mode: CalibrationMode, availableThroughH: Double, samples: Int = SAMPLES, checkCancelled: () -> Unit = {}): LabEvaluation? {
        require(timeH.isFinite() && availableThroughH.isFinite() && populationGrid.all { it.isFinite() } && (1 until populationGrid.size).all { populationGrid[it-1] <= populationGrid[it] })
        checkCancelled()
        val cutoff = if (mode == CalibrationMode.CAUSAL) min(timeH,availableThroughH) else availableThroughH
        val prefix = qualifiedLabs.filter { it.timeH <= cutoff }.sortedWith(compareBy<LabResult> { it.timeH }.thenBy { it.id })
        val model = fit(events,prefix)
        val points = queryBracket(populationGrid,timeH)
        val b = bandsWithFit(events,points,prefix,CalibrationMode.RETROSPECTIVE,samples,emptySet(),checkCancelled,model)[Curve.E2] ?: return null
        fun value(a:DoubleArray) = Pk.interpolate(points,a,timeH)!!
        checkCancelled()
        return LabEvaluation(timeH,value(b.center),value(b.p5),value(b.p25),value(b.p75),value(b.p95),model,lastDiagnostics(events,prefix),prefix.size)
    }

    fun queryBracket(grid: DoubleArray, timeH: Double): DoubleArray {
        if (grid.isEmpty() || timeH < grid.first() || timeH > grid.last()) return doubleArrayOf(timeH)
        val i = lowerBound(grid,timeH)
        return if (grid[i] == timeH) doubleArrayOf(timeH) else doubleArrayOf(grid[i-1],grid[i])
    }
    private fun lowerBound(a:DoubleArray,x:Double):Int { var lo=0;var hi=a.size
        while(lo<hi){val m=(lo+hi) ushr 1;if(a[m]<x)lo=m+1 else hi=m};return lo }

    /** Fits that apply on each part of the grid: one for retrospective mode, one per lab interval for causal mode. */
    private fun segmentFits(events: List<DoseEvent>, labs: List<LabResult>, mode: CalibrationMode, grid: DoubleArray, confirmedBaselineLabIds: Set<String>, checkCancelled: () -> Unit): List<Pair<Double, LabFitModel>> {
        if (labs.isEmpty()) return listOf(Double.NEGATIVE_INFINITY to fit(events, emptyList()))
        if (mode == CalibrationMode.RETROSPECTIVE) return listOf(Double.NEGATIVE_INFINITY to fit(events, labs, confirmedBaselineLabIds = confirmedBaselineLabIds))
        val times = labs.map { it.timeH }.distinct().sorted()
        return listOf(Double.NEGATIVE_INFINITY to fit(events, emptyList())) + times.filter { it <= (grid.lastOrNull() ?: Double.NEGATIVE_INFINITY) }.map { t -> checkCancelled(); t to fit(events, labs, t, confirmedBaselineLabIds) }
    }

    /** Estradiol curve where each grid point uses the fit of its segment; baseline added. */
    private fun e2Curve(events: List<DoseEvent>, grid: DoubleArray, fits: List<Pair<Double, LabFitModel>>, fixedPopulation: DoubleArray?, param: (LabFitModel) -> Pair<Double, Double>): DoubleArray {
        val out = DoubleArray(grid.size)
        fits.forEachIndexed { idx, (from, m) ->
            val to = fits.getOrNull(idx + 1)?.first ?: Double.POSITIVE_INFINITY
            val (u, v) = param(m)
            val first = lowerBound(grid, from); val last = lowerBound(grid, to)
            if (first == last) return@forEachIndexed
            val curve = if (fixedPopulation != null) DoubleArray(last-first) { fixedPopulation[first+it] * exp(u) }
                else e2At(events, grid.copyOfRange(first,last), u, v)
            for (i in first until last) out[i] = curve[i-first] + (m.baselinePGmL ?: 0.0)
        }
        return out
    }
}
