package net.plainnotes.app.pk

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/*
 * Lab-based personalisation ported from TransmtfTeam/Transmtf-HRT-Tracker (commit 8c9abdde,
 * personalModel.ts — the default "EKF" calibration model), MIT License,
 * Copyright (c) 2025 Transmtf Team.
 *
 * theta = [theta_s, theta_k]: log-scale multipliers on the whole E2 amplitude and on the
 * clearance rate. A 2-state extended Kalman filter updates theta in log-concentration space
 * from each lab; pre-dose labs instead form an endogenous baseline that is added on top.
 */

class Mat2(val a: Double, val b: Double, val c: Double, val d: Double)

data class PersonalModel(
    val thetaS: Double = 0.0,
    val thetaK: Double = 0.0,
    val cov: Mat2 = Calibration.INITIAL_COV,
    val observationCount: Int = 0,
    val postDoseObservationCount: Int = 0,
    val baselinePGmL: Double? = null,
)

data class EkfDiagnostics(
    val nis: Double, val isOutlier: Boolean, val residualLog: Double,
    val predictedPGmL: Double, val observedPGmL: Double,
    val ci95Low: Double, val ci95High: Double, val convergenceScore: Double,
    val scale: Double, val clearanceScale: Double,
)


class CalibratedCurve(
    val timeH: DoubleArray, val e2: DoubleArray,
    val ci95Low: DoubleArray, val ci95High: DoubleArray, val ci68Low: DoubleArray, val ci68High: DoubleArray,
    val antiandrogen: Map<Ester, Triple<DoubleArray, DoubleArray, DoubleArray>>,
)

object Calibration {
    val INITIAL_COV = Mat2(0.25, 0.0, 0.0, 0.09)
    private val Q = Mat2(0.0004, 0.0, 0.0, 0.0001)
    private const val RLOG = 0.04
    private const val EPS = 0.1
    private const val EPS_CPA = 0.001
    private const val CHI2_95 = 3.841
    private const val DELTA_K = 0.01
    /** Upper bound of the personalised E2 curve (pg/mL). */
    const val CEILING_PGML = 5000.0
    private const val SIGMA_RESIDUAL_LOG = 0.27
    private const val Q_REF_PERIOD_H = 30.0 * 24
    private const val CPA_EMAX = 0.20; private const val CPA_IC50 = 50.0; private const val CPA_N = 1.0

    fun toPgMl(value: Double, unit: LabUnit) = if (unit == LabUnit.PG_ML) value else value / Pk.PMOL_PER_PG

    private fun e2AtSorted(sorted: List<DoseEvent>, timeH: Double, thetaS: Double, thetaK: Double): Double {
        val s = exp(thetaS); val kScale = exp(thetaK)
        var total = 0.0
        for (e in sorted) {
            if (e.timeH > timeH) break
            total += Pk.e2Amount(e, sorted, timeH - e.timeH, kScale)
        }
        val volume = CorePK.VD_PER_KG * Pk.weightAt(sorted, timeH) * 1000
        return max(0.0, total * 1e9 / volume * s)
    }

    fun e2At(events: List<DoseEvent>, timeH: Double, thetaS: Double, thetaK: Double) = e2AtSorted(events.sortedBy { it.timeH }, timeH, thetaS, thetaK)

    private fun trace(m: Mat2) = m.a + m.d

    /** Incorporates one lab into [state]. [prevLabTimeH] scales process noise by elapsed time. */
    fun update(events: List<DoseEvent>, state: PersonalModel, lab: LabResult, prevLabTimeH: Double? = null): Pair<PersonalModel, EkfDiagnostics> {
        val hasDose = events.any { it.timeH <= lab.timeH && it.route != Route.PATCH_REMOVE && !Antiandrogens.isAntiandrogen(it.ester) }
        val obs = toPgMl(lab.concValue, lab.unit)
        val dt = if (prevLabTimeH != null) max(24.0, lab.timeH - prevLabTimeH) else Q_REF_PERIOD_H
        val q = dt / Q_REF_PERIOD_H
        val p = Mat2(state.cov.a + Q.a * q, state.cov.b + Q.b * q, state.cov.c + Q.c * q, state.cov.d + Q.d * q)
        val pred = e2At(events, lab.timeH, state.thetaS, state.thetaK)
        val yhat = ln(max(pred, EPS))
        if (!hasDose) {
            val convergence = max(0.0, min(1.0, 1 - trace(state.cov) / trace(INITIAL_COV)))
            val prev = state.baselinePGmL ?: 0.0; val n = state.observationCount
            val baseline = if (n == 0) obs else (prev * n + obs) / (n + 1)
            return state.copy(baselinePGmL = baseline, observationCount = n + 1) to
                EkfDiagnostics(0.0, false, 0.0, pred, obs, obs, obs, convergence, exp(state.thetaS), exp(state.thetaK))
        }
        val yhatK = ln(max(e2At(events, lab.timeH, state.thetaS, state.thetaK + DELTA_K), EPS))
        val h0 = 1.0; val h1 = (yhatK - yhat) / DELTA_K
        val baseline = state.baselinePGmL?.takeIf { it.isFinite() }?.let { max(0.0, it) } ?: 0.0
        val obsDrug = max(obs - baseline, EPS)
        val innovation = ln(obsDrug) - yhat
        val s = h0 * h0 * p.a + 2 * h0 * h1 * p.b + h1 * h1 * p.d + RLOG
        val nis = if (s > 0) innovation * innovation / s else 0.0
        val outlier = nis > CHI2_95
        val rEff = if (outlier) RLOG * 4.0 else RLOG
        val sEff = h0 * h0 * p.a + 2 * h0 * h1 * p.b + h1 * h1 * p.d + rEff
        val k0 = (p.a * h0 + p.b * h1) / sEff
        val k1 = (p.c * h0 + p.d * h1) / sEff
        val thetaS = state.thetaS + k0 * innovation
        val thetaK = state.thetaK + k1 * innovation
        val i00 = 1 - k0 * h0; val i01 = -k0 * h1; val i10 = -k1 * h0; val i11 = 1 - k1 * h1
        val n00 = i00 * p.a + i01 * p.c; var n01 = i00 * p.b + i01 * p.d
        val n10 = i10 * p.a + i11 * p.c; val n11 = i10 * p.b + i11 * p.d
        n01 = (n01 + n10) / 2
        val cov = Mat2(max(n00, 1e-6), n01, n01, max(n11, 1e-6))
        val newPred = e2At(events, lab.timeH, thetaS, thetaK)
        val varY = cov.a + 2 * cov.b * h1 + cov.d * h1 * h1
        val std = sqrt(max(0.0, varY + rEff))
        val logNew = ln(max(newPred, EPS))
        val convergence = max(0.0, min(1.0, 1 - trace(cov) / trace(INITIAL_COV)))
        val next = PersonalModel(thetaS, thetaK, cov, state.observationCount + 1, state.postDoseObservationCount + 1, state.baselinePGmL)
        return next to EkfDiagnostics(nis, outlier, innovation, pred, obs, exp(logNew - 1.96 * std), exp(logNew + 1.96 * std), convergence, exp(thetaS), exp(thetaK))
    }

    /** Model state after each lab (index 0 is the population prior, valid from −∞). */
    fun timeline(events: List<DoseEvent>, labs: List<LabResult>): List<Pair<Double, PersonalModel>> {
        var state = PersonalModel()
        val sorted = labs.sortedBy { it.timeH }
        val out = mutableListOf(Double.NEGATIVE_INFINITY to state)
        sorted.forEachIndexed { i, lab ->
            state = update(events, state, lab, if (i > 0) sorted[i - 1].timeH else null).first
            out += lab.timeH to state
        }
        return out
    }

    fun replay(events: List<DoseEvent>, labs: List<LabResult>): PersonalModel = timeline(events, labs).last().second

    /** Diagnostics for the most recent lab, computed against the state before it (as upstream does). */
    fun lastDiagnostics(events: List<DoseEvent>, labs: List<LabResult>): EkfDiagnostics? {
        if (labs.isEmpty()) return null
        val sorted = labs.sortedBy { it.timeH }
        val prior = if (sorted.size > 1) replay(events, sorted.dropLast(1)) else PersonalModel()
        return update(events, prior, sorted.last(), if (sorted.size > 1) sorted[sorted.size - 2].timeH else null).second
    }

    fun cpaInhibition(cpaNgML: Double): Double {
        if (!cpaNgML.isFinite() || cpaNgML <= 0) return 0.0
        val dn = cpaNgML.pow(CPA_N)
        return min(max(0.0, CPA_EMAX * dn / (CPA_IC50.pow(CPA_N) + dn)), CPA_EMAX * 0.9999)
    }

    private fun clamp(low: Double, high: Double, hardMax: Double): Pair<Double, Double> {
        val lo = if (low.isFinite()) max(0.0, low) else 0.0
        val hi = if (high.isFinite()) max(lo, high) else lo
        return min(lo, hardMax) to min(hi, hardMax)
    }

    /**
     * Personalised curve with 68 %/95 % bands on the simulation grid. RETROSPECTIVE applies the
     * final learned state everywhere (upstream default); CAUSAL uses only labs up to each point.
     */
    fun calibrate(sim: SimulationResult, events: List<DoseEvent>, labs: List<LabResult>, mode: CalibrationMode = CalibrationMode.RETROSPECTIVE,
                  applyE2LearningToCpa: Boolean = true, applyCpaInhibitionToE2: Boolean = false): CalibratedCurve {
        val n = sim.timeH.size
        val line = timeline(events, labs)
        val final = line.last().second
        val resolve: (Double) -> PersonalModel = if (mode == CalibrationMode.RETROSPECTIVE) { _ -> final } else { t ->
            var lo = 0; var hi = line.size - 1; var ans = 0
            while (lo <= hi) { val mid = (lo + hi) ushr 1; if (line[mid].first <= t) { ans = mid; lo = mid + 1 } else hi = mid - 1 }
            line[ans].second
        }
        fun baselineOf(st: PersonalModel) = st.baselinePGmL?.takeIf { it.isFinite() }?.let { max(0.0, it) } ?: 0.0
        val sorted = events.sortedBy { it.timeH }
        val e2 = DoubleArray(n); val lo95 = DoubleArray(n); val hi95 = DoubleArray(n); val lo68 = DoubleArray(n); val hi68 = DoubleArray(n)
        for (i in 0 until n) {
            val t = sim.timeH[i]; val st = resolve(t); val base = baselineOf(st)
            val e2Base = e2AtSorted(sorted, t, st.thetaS, st.thetaK)
            val yhat = ln(max(e2Base, EPS))
            val h1 = (ln(max(e2AtSorted(sorted, t, st.thetaS, st.thetaK + DELTA_K), EPS)) - yhat) / DELTA_K
            val raw = st.cov.a + 2 * h1 * st.cov.b + h1 * h1 * st.cov.d
            val sigma2 = if (raw.isFinite() && raw > 0) raw else 0.0
            val sigma = sqrt(sigma2 + SIGMA_RESIDUAL_LOG * SIGMA_RESIDUAL_LOG)
            e2[i] = min(base + e2Base * exp(0.5 * sigma2), CEILING_PGML)
            clamp(base + e2Base * exp(-1.96 * sigma), base + e2Base * exp(1.96 * sigma), CEILING_PGML).let { lo95[i] = it.first; hi95[i] = it.second }
            clamp(base + e2Base * exp(-sigma), base + e2Base * exp(sigma), CEILING_PGML).let { lo68[i] = it.first; hi68[i] = it.second }
        }
        val aa = LinkedHashMap<Ester, Triple<DoubleArray, DoubleArray, DoubleArray>>()
        for ((ester, spec) in Antiandrogens.SPECS) {
            val series = sim.byCompound[ester] ?: continue
            val useAdherence = spec.adherenceFromE2 && applyE2LearningToCpa
            val adj = DoubleArray(n); val l = DoubleArray(n); val h = DoubleArray(n)
            for (i in 0 until n) {
                val st = if (useAdherence) resolve(sim.timeH[i]) else final
                val scale = if (useAdherence) exp(st.thetaS) else 1.0
                val adhVar = if (useAdherence) max(0.0, st.cov.a) else 0.0
                val std = sqrt(max(0.0, adhVar + spec.popLogVar))
                val pred = max(0.0, series[i] * scale)
                val y = ln(max(pred, EPS_CPA))
                val (lo, hi) = clamp(exp(y - 1.96 * std), exp(y + 1.96 * std), spec.ciMaxNative)
                adj[i] = min(pred, spec.ciMaxNative); l[i] = lo; h[i] = hi
            }
            aa[ester] = Triple(adj, l, h)
        }
        val cpa = aa[Ester.CPA]?.first
        if (applyCpaInhibitionToE2 && cpa != null) for (i in 0 until n) {
            val scale = 1 / (1 - cpaInhibition(cpa[i]))
            e2[i] = min(e2[i] * scale, CEILING_PGML)
            val h95 = min(hi95[i] * scale, CEILING_PGML); val h68 = min(hi68[i] * scale, CEILING_PGML)
            lo95[i] = min(lo95[i] * scale, h95); hi95[i] = h95
            lo68[i] = min(lo68[i] * scale, h68); hi68[i] = h68
        }
        return CalibratedCurve(sim.timeH, e2, lo95, hi95, lo68, hi68, aa)
    }
}
