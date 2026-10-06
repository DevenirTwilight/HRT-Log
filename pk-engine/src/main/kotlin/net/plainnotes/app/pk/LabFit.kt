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
 * Personal adjustment learned from estradiol labs (docs/pk-model.md, "化验校准"). Two factors apply to every
 * estradiol model: amplitude e^[logAmplitude] and elimination rate e^[logRate]. [cov] is the posterior covariance of
 * (logAmplitude, logRate) as [[a, b], [b, d]].
 */
data class LabFitModel(
    val logAmplitude: Double = 0.0,
    val logRate: Double = 0.0,
    val cov: DoubleArray = doubleArrayOf(LabFit.PRIOR_SD_AMPLITUDE_DEFAULT.let { it * it }, 0.0, 0.0, LabFit.PRIOR_SD_RATE * LabFit.PRIOR_SD_RATE),
    /** Endogenous estradiol from labs taken before the first recorded dose (pg/mL), added to the curve. */
    val baselinePGmL: Double? = null,
    val postDoseObservationCount: Int = 0,
    /** Labs left out as outliers (more than 4-fold off the fitted curve). */
    val excludedLabIds: Set<String> = emptySet(),
    val priorSdAmplitude: Double = LabFit.PRIOR_SD_AMPLITUDE_DEFAULT,
) {
    /** 0 = labs taught nothing yet, 1 = amplitude fully determined by labs. */
    val convergenceScore: Double get() = (1 - cov[0] / (priorSdAmplitude * priorSdAmplitude)).coerceIn(0.0, 1.0)
}

/** How the most recent lab compares with the prediction made from the labs before it. */
data class LabDiagnostics(val predictedPGmL: Double, val observedPGmL: Double, val residualLog: Double, val isOutlier: Boolean, val convergenceScore: Double)

/** Curve with its 5 %–95 % and 25 %–75 % bands (Monte Carlo). */
class BandedCurve(val timeH: DoubleArray, val center: DoubleArray, val p5: DoubleArray, val p25: DoubleArray, val p75: DoubleArray, val p95: DoubleArray)

object LabFit {
    /** Within-person variability of a lab around the curve: Zhang 2024 intra-individual CV of E2 Cmax 29.9 %. */
    val SIGMA_LAB = sqrt(ln(1 + 0.299 * 0.299))
    /** Prior spread of the personal clearance factor: half-life CV 29 % (Zhang 2024). */
    val PRIOR_SD_RATE = sqrt(ln(1 + 0.29 * 0.29))
    val PRIOR_SD_AMPLITUDE_DEFAULT = sqrt(ln(1 + 0.48 * 0.48))
    /** A lab more than 4-fold away from the fitted curve is treated as an outlier. */
    val OUTLIER_LOG = ln(4.0)
    const val SAMPLES = 200

    fun toPgMl(value: Double, unit: LabUnit) = if (unit == LabUnit.PG_ML) value else value / Pk.PMOL_PER_PG
    private fun sdOf(cv: Double) = sqrt(ln(1 + cv * cv))

    /** Prior spread of the amplitude: the largest between-person CV among the estradiol models in use. */
    fun priorSdAmplitude(events: List<DoseEvent>): Double {
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
    fun fit(events: List<DoseEvent>, labs: List<LabResult>, untilH: Double = Double.POSITIVE_INFINITY): LabFitModel {
        val sdA = priorSdAmplitude(events)
        val firstDose = events.minOfOrNull { it.timeH } ?: return LabFitModel(priorSdAmplitude = sdA)
        val used = labs.filter { it.timeH <= untilH }.sortedBy { it.timeH }
        val baselineLabs = used.filter { it.timeH < firstDose }
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

    /** Diagnostics for the most recent lab against the fit from the labs before it. */
    fun lastDiagnostics(events: List<DoseEvent>, labs: List<LabResult>): LabDiagnostics? {
        val sorted = labs.sortedBy { it.timeH }
        val last = sorted.lastOrNull() ?: return null
        if (events.isEmpty()) return null
        val before = fit(events, sorted.dropLast(1), last.timeH - 1e-6)
        val pred = e2At(events, doubleArrayOf(last.timeH), before.logAmplitude, before.logRate)[0] + (before.baselinePGmL ?: 0.0)
        val obs = toPgMl(last.concValue, last.unit)
        val res = ln(obs) - ln(pred + 1e-9)
        return LabDiagnostics(pred, obs, res, abs(res) > OUTLIER_LOG, fit(events, sorted).convergenceScore)
    }

    /**
     * Monte Carlo bands for every curve. Estradiol uses the lab fit (or the prior when there is none), other curves
     * use their model's between-person variability. Fixed seed so the bands do not flicker between redraws.
     */
    fun bands(events: List<DoseEvent>, grid: DoubleArray, labs: List<LabResult>, mode: CalibrationMode, samples: Int = SAMPLES): Map<Curve, BandedCurve> {
        val fits = segmentFits(events, labs, mode, grid)
        val base = Engine.simulate(events, grid = grid) ?: return emptyMap()
        val center = HashMap<Curve, DoubleArray>()
        for ((curve, arr) in base.curves) center[curve] = if (curve == Curve.E2) e2Curve(events, grid, fits) { it.logAmplitude to it.logRate } else arr
        val draws = HashMap<Curve, Array<DoubleArray>>()
        base.curves.keys.forEach { draws[it] = Array(grid.size) { DoubleArray(samples) } }
        val rnd = Random(20261006L)
        // Non-estradiol curves are sampled from their own events only (estradiol is handled by the lab fit).
        val otherEvents = events.filter { e -> (Engine.choose(e) as? ModelChoice.Use)?.parts?.any { it.first != Curve.E2 } == true }
        for (k in 0 until samples) {
            val z = DoubleArray(4) { rnd.nextGaussian() }
            // Estradiol: correlated draw from each segment's posterior.
            val e2 = e2Curve(events, grid, fits) { m ->
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

    /** Fits that apply on each part of the grid: one for retrospective mode, one per lab interval for causal mode. */
    private fun segmentFits(events: List<DoseEvent>, labs: List<LabResult>, mode: CalibrationMode, grid: DoubleArray): List<Pair<Double, LabFitModel>> {
        if (labs.isEmpty()) return listOf(Double.NEGATIVE_INFINITY to fit(events, emptyList()))
        if (mode == CalibrationMode.RETROSPECTIVE) return listOf(Double.NEGATIVE_INFINITY to fit(events, labs))
        val times = labs.map { it.timeH }.distinct().sorted()
        return listOf(Double.NEGATIVE_INFINITY to fit(events, emptyList())) + times.map { t -> t to fit(events, labs, t) }
    }

    /** Estradiol curve where each grid point uses the fit of its segment; baseline added. */
    private fun e2Curve(events: List<DoseEvent>, grid: DoubleArray, fits: List<Pair<Double, LabFitModel>>, param: (LabFitModel) -> Pair<Double, Double>): DoubleArray {
        val out = DoubleArray(grid.size)
        fits.forEachIndexed { idx, (from, m) ->
            val to = fits.getOrNull(idx + 1)?.first ?: Double.POSITIVE_INFINITY
            val (u, v) = param(m)
            val curve = e2At(events, grid, u, v)
            for (i in grid.indices) if (grid[i] >= from && grid[i] < to) out[i] = curve[i] + (m.baselinePGmL ?: 0.0)
        }
        return out
    }
}
