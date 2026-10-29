package net.plainnotes.app.conc

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * SL-CAL-02 (REQUIREMENTS §166): calibration of the estradiol TOTAL by one common coefficient.
 *
 * The total is `C(t) = B + k · (S(t) + N(t))`: `B` the single assumed background (counted once, never scaled), `S` the sublingual part
 * above background (the frozen M2 curve), `N` the other routes above background (the existing compatibility curves), `k` the person's
 * coefficient. Each lab is an observation `ln y ~ Normal(ln C(t), σ²)`; `ln k` has the prior `Normal(0, 0.9²)`; the estimate is the
 * MAP, found by damped Gauss–Newton with backtracking, with a Laplace standard deviation. A lab taken while the curve is steep has its
 * σ widened by the delta method (the recorded time is only trusted to ±15 min), so it is down-weighted, never excluded. When the labs
 * carry too little information about the coefficient (information gain below ½), calibration is refused and the curve stays uncalibrated.
 *
 * Pure and deterministic: no Android, no data access, no clock. It never changes a model, a parameter, a candidate or any saved record,
 * and it never clips a result to a concentration limit. The reference implementation is `tools/pk-research/sl_cal/sl_cal_core.py`
 * (method `C_common`, policy `delta`); `TotalE2CalibrationParityTest` compares them number by number.
 */
object TotalE2Calibration {
    /** Prior sd of ln k. P1-A's 0.455 is the old sublingual model's semantics; the report (4.4) measured 0.9 as better for the M2 total. */
    const val PRIOR_SD = 0.9
    /** Assumed spread of a recorded sampling time (design assumption, 15 minutes). */
    const val TIMING_SD_H = 0.25
    /** Within this many hours of the last sublingual dose the curve is steep: the lab is flagged and (like every lab) widened by the delta method. */
    const val PEAK_WINDOW_H = 2.5
    /** The labs must remove at least half of the prior variance of ln k. */
    const val MIN_INFORMATION_GAIN = 0.5
    const val DERIVATIVE_STEP_H = 0.05
    /** Production `LabFit.SIGMA_LAB` (Zhang 2024 intra-individual CV of Cmax); duplicated here so the engine stays independent of LIVE_LOCK files, and equality is tested. */
    val SIGMA_LAB: Double = sqrt(ln(1 + 0.299 * 0.299))
    private const val MAX_ITERATIONS = 60
    private const val MAX_BACKTRACKS = 25
    private const val STEP_LIMIT = 1.5
    private const val LN_K_LIMIT = 5.0

    enum class Reason { HISTORY_NOT_ELIGIBLE, LAB_TIME_NOT_PRECISE, SL_LAST_DOSE_UNKNOWN, NO_USABLE_LAB, INSUFFICIENT_INFORMATION, PEAK_WINDOW_DOWNWEIGHTED, INVALID_VALUE }
    enum class Use { USE, DOWNWEIGHT, REJECT }

    /**
     * One estradiol lab. [historyEligible] is the existing history gate (P1-B `CalibrationEligibility`, plus at least 24 h of complete records
     * before the sample), decided by the caller. [timeToMinute] says the sampling time is a recorded clock time and not a date placeholder.
     * [hoursSinceLastSublingual] is null when the last sublingual dose before the sample has no recorded time.
     */
    data class Lab(val id: Long, val timeH: Double, val pgPerMl: Double, val historyEligible: Boolean, val timeToMinute: Boolean, val hoursSinceLastSublingual: Double?)
    data class LabDecision(val labId: Long, val use: Use, val reasons: List<Reason>)

    /** Parts of the total, in pg/mL above [background]. [sublingual] and [other] must be finite and non-negative; [other] is zero without other routes. */
    class Curves(val background: Double, val sublingual: (Double) -> Double, val other: (Double) -> Double = { 0.0 })

    sealed interface Outcome { val decisions: List<LabDecision> }
    /**
     * @property factor `k`, the multiplier of the whole curve above background.
     * @property lnSd Laplace standard deviation of `ln k` (not shown to the user as an interval).
     * @property informationGain `1 − posterior variance / prior variance`; at least [MIN_INFORMATION_GAIN] here.
     * @property leaveOneOut how well the other labs predict each lab; null with a single lab.
     */
    data class Calibrated(val factor: Double, val lnSd: Double, val informationGain: Double, override val decisions: List<LabDecision>,
        val usedLabIds: List<Long>, val leaveOneOut: LeaveOneOut?) : Outcome
    data class Refused(val reasons: List<Reason>, override val decisions: List<LabDecision>, val informationGain: Double? = null) : Outcome
    /** [medianAbsLn] is the median of `|ln(prediction / observation)|` over the used labs (0.1 ≈ 10 %); [rmsLn] the root mean square. */
    data class LeaveOneOut(val medianAbsLn: Double, val rmsLn: Double, val labs: Int) {
        val medianPercent: Double get() = (exp(medianAbsLn) - 1.0) * 100.0
    }

    /** Per-lab rules E1–E4 (in this order), the same as `eligibility_rules.assess_lab` in the research reference. */
    fun assess(lab: Lab): LabDecision {
        if (!lab.timeH.isFinite() || !lab.pgPerMl.isFinite() || lab.pgPerMl <= 0.0) return LabDecision(lab.id, Use.REJECT, listOf(Reason.INVALID_VALUE))
        if (!lab.historyEligible) return LabDecision(lab.id, Use.REJECT, listOf(Reason.HISTORY_NOT_ELIGIBLE))
        if (!lab.timeToMinute) return LabDecision(lab.id, Use.REJECT, listOf(Reason.LAB_TIME_NOT_PRECISE))
        val since = lab.hoursSinceLastSublingual
        if (since == null || !since.isFinite() || since < 0.0) return LabDecision(lab.id, Use.REJECT, listOf(Reason.SL_LAST_DOSE_UNKNOWN))
        if (since < PEAK_WINDOW_H) return LabDecision(lab.id, Use.DOWNWEIGHT, listOf(Reason.PEAK_WINDOW_DOWNWEIGHTED))
        return LabDecision(lab.id, Use.USE, emptyList())
    }

    /** Set-level rule E5/E6, the same as `eligibility_rules.assess_set`. Returns null when calibration may go ahead. */
    fun refusal(usableLabs: Int, informationGain: Double): Reason? = when {
        usableLabs <= 0 -> Reason.NO_USABLE_LAB
        !(informationGain >= MIN_INFORMATION_GAIN) -> Reason.INSUFFICIENT_INFORMATION
        else -> null
    }

    fun calibrate(labs: List<Lab>, curves: Curves, checkCancelled: () -> Unit = {}): Outcome {
        checkCancelled()
        val decisions = labs.map(::assess)
        val usable = labs.zip(decisions).filter { it.second.use != Use.REJECT }.map { it.first }
        if (usable.isEmpty()) return Refused(listOf(Reason.NO_USABLE_LAB), decisions)
        val data = prepare(usable, curves)
            ?: return Refused(listOf(Reason.INSUFFICIENT_INFORMATION), decisions, informationGain = 0.0)
        val fit = fit(data, checkCancelled)
        val gain = 1.0 - fit.variance / (PRIOR_SD * PRIOR_SD)
        refusal(usable.size, gain)?.let { return Refused(listOf(it), decisions, gain) }
        return Calibrated(fit.factor, sqrt(fit.variance), gain, decisions, usable.map { it.id }, leaveOneOut(data, checkCancelled))
    }

    /** `C(t)` for a fitted coefficient: the calibrated total at any hour. */
    fun total(curves: Curves, factor: Double, hour: Double): Double = curves.background + factor * (curves.sublingual(hour) + curves.other(hour))

    private class Data(val background: Double, val parts: DoubleArray, val logY: DoubleArray, val sigma: DoubleArray, val timeH: DoubleArray)
    private class Fit(val theta: Double, val variance: Double) { val factor: Double get() = exp(theta) }

    /** Null when the curves cannot describe the labs (non-finite, negative, or no exposure at any lab time): nothing is fitted then. */
    private fun prepare(usable: List<Lab>, curves: Curves): Data? {
        val b = curves.background
        if (!b.isFinite() || b <= 0.0) return null
        fun part(t: Double): Double? {
            val s = curves.sublingual(t); val n = curves.other(t)
            return if (s.isFinite() && n.isFinite() && s >= 0.0 && n >= 0.0) s + n else null
        }
        val parts = DoubleArray(usable.size); val sigma = DoubleArray(usable.size)
        for ((i, lab) in usable.withIndex()) {
            parts[i] = part(lab.timeH) ?: return null
            val up = part(lab.timeH + DERIVATIVE_STEP_H) ?: return null
            val down = part(lab.timeH - DERIVATIVE_STEP_H) ?: return null
            val dLn = (ln(b + up) - ln(b + down)) / (2.0 * DERIVATIVE_STEP_H)
            // Delta method: the recorded time is uncertain by TIMING_SD_H; where the curve is steep that is worth a wider σ.
            sigma[i] = sqrt(SIGMA_LAB * SIGMA_LAB + (TIMING_SD_H * dLn) * (TIMING_SD_H * dLn))
        }
        if (parts.all { it == 0.0 }) return null
        return Data(b, parts, DoubleArray(usable.size) { ln(usable[it].pgPerMl) }, sigma, DoubleArray(usable.size) { usable[it].timeH })
    }

    /** MAP of `ln k`: damped Gauss–Newton with backtracking, then the Laplace variance at the mode (same steps as `map_fit` in the reference). */
    private fun fit(d: Data, checkCancelled: () -> Unit): Fit {
        val prior = 1.0 / (PRIOR_SD * PRIOR_SD)
        fun mean(theta: Double, i: Int) = d.background + exp(theta) * d.parts[i]
        fun objective(theta: Double): Double {
            var sum = 0.0
            for (i in d.parts.indices) {
                val mu = mean(theta, i)
                if (!(mu > 0.0) || !mu.isFinite()) return Double.POSITIVE_INFINITY
                val r = (d.logY[i] - ln(mu)) / d.sigma[i]
                sum += r * r
            }
            return 0.5 * (sum + theta * theta * prior)
        }
        var theta = 0.0; var current = objective(theta); var lambda = 1e-3
        for (iteration in 0 until MAX_ITERATIONS) {
            checkCancelled()
            var gradient = -prior * theta; var hessian = prior
            for (i in d.parts.indices) {
                val mu = mean(theta, i)
                val r = (d.logY[i] - ln(mu)) / d.sigma[i]
                val j = (exp(theta) * d.parts[i] / mu) / d.sigma[i]
                gradient += j * r; hessian += j * j
            }
            var done = true
            for (attempt in 0 until MAX_BACKTRACKS) {
                val step = (gradient / (hessian + lambda)).coerceIn(-STEP_LIMIT, STEP_LIMIT)
                val candidate = (theta + step).coerceIn(-LN_K_LIMIT, LN_K_LIMIT)
                val value = objective(candidate)
                if (value < current) {
                    theta = candidate; current = value; lambda = max(lambda / 3.0, 1e-9); done = abs(step) < 1e-8
                    break
                }
                lambda *= 4.0
            }
            if (done) break
        }
        var information = prior
        for (i in d.parts.indices) {
            val mu = mean(theta, i)
            val j = (exp(theta) * d.parts[i] / mu) / d.sigma[i]
            information += j * j
        }
        return Fit(theta, 1.0 / information)
    }

    /** Predict each lab from the others (same σ rule, same prior); the user-facing consistency figure. Null with fewer than two labs. */
    private fun leaveOneOut(d: Data, checkCancelled: () -> Unit): LeaveOneOut? {
        val n = d.parts.size
        if (n < 2) return null
        val errors = DoubleArray(n)
        for (i in 0 until n) {
            checkCancelled()
            val keep = (0 until n).filter { it != i }
            val rest = Data(d.background, DoubleArray(keep.size) { d.parts[keep[it]] }, DoubleArray(keep.size) { d.logY[keep[it]] },
                DoubleArray(keep.size) { d.sigma[keep[it]] }, DoubleArray(keep.size) { d.timeH[keep[it]] })
            val f = fit(rest, checkCancelled)
            errors[i] = d.logY[i] - ln(d.background + f.factor * d.parts[i])
        }
        val absolute = errors.map { abs(it) }.sorted()
        val median = if (n % 2 == 1) absolute[n / 2] else (absolute[n / 2 - 1] + absolute[n / 2]) / 2.0
        return LeaveOneOut(median, sqrt(errors.sumOf { it * it } / n), n)
    }

    /** The calibration tier shown to the user next to the figure: measured from the information gain, not from an interval. */
    enum class Tier { SUFFICIENT, LIMITED }
    fun tier(informationGain: Double): Tier = if (informationGain >= 0.75) Tier.SUFFICIENT else Tier.LIMITED
}
