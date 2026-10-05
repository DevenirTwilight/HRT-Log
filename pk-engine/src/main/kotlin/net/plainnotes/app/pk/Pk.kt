package net.plainnotes.app.pk

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/*
 * Population PK engine ported from TransmtfTeam/Transmtf-HRT-Tracker (commit 8c9abdde, pk.ts),
 * MIT License, Copyright (c) 2025 Transmtf Team. The code values are authoritative where they
 * differ from the upstream prose documentation; see docs/pk-model.md.
 */

object CorePK {
    const val VD_PER_KG = 2.0
    const val K_CLEAR = 0.41
    const val K_CLEAR_INJECTION = 0.041
    const val DEPOT_K1_CORR = 1.0
}

object CpaPK {
    const val F = 0.88; const val KA = 0.60; const val ALPHA = 0.20; const val BETA = 0.01579; const val K21 = 0.04
    const val V1_PER_KG = 2.666; const val POP_LOG_VAR = 0.09
}

object BicaPK {
    const val KA = 0.10
    val KE = ln(2.0) / (5.8 * 24)
    const val V_OVER_F = 50.2
    const val POP_LOG_VAR = 0.10
}

object EuDepotPK { const val KA = 0.00082; const val K_CLEAVE = 2.0; const val RELEASE_SCALE = 0.542 }

/** Non-E2 compounds tracked on their own axis (ng/mL). */
class AntiandrogenSpec(val ester: Ester, val popLogVar: Double, val ciMaxNative: Double, val adherenceFromE2: Boolean,
                       val concFromAmountMG: (amountMG: Double, weightKG: Double) -> Double)

object Antiandrogens {
    val SPECS: Map<Ester, AntiandrogenSpec> = linkedMapOf(
        Ester.CPA to AntiandrogenSpec(Ester.CPA, CpaPK.POP_LOG_VAR, 500.0, true) { amount, weight ->
            val v1mL = CpaPK.V1_PER_KG * weight * 1000
            if (v1mL > 0) max(0.0, amount * 1e6 / v1mL) else 0.0
        },
        Ester.BICA to AntiandrogenSpec(Ester.BICA, BicaPK.POP_LOG_VAR, 20000.0, false) { amount, _ -> max(0.0, amount / BicaPK.V_OVER_F * 1000) },
    )
    fun isAntiandrogen(ester: Ester) = ester in SPECS
}

object Pk {
    private val MW = mapOf(Ester.E2 to 272.38, Ester.EB to 376.50, Ester.EV to 356.50, Ester.EC to 396.58,
        Ester.EN to 384.56, Ester.EU to 440.66, Ester.CPA to 416.94, Ester.BICA to 430.37)
    const val E2_MW = 272.38
    /** 1 pg/mL of E2 equals this many pmol/L. */
    const val PMOL_PER_PG = 3.671

    fun toE2Factor(ester: Ester): Double = if (ester == Ester.E2) 1.0 else MW.getValue(Ester.E2) / MW.getValue(ester)

    private val FRAC_FAST = mapOf(Ester.EB to 0.90, Ester.EV to 0.40, Ester.EC to 0.229164549, Ester.EN to 0.05, Ester.EU to 0.0, Ester.E2 to 1.0)
    private val K1_FAST = mapOf(Ester.EB to 0.144, Ester.EV to 0.0216, Ester.EC to 0.005035046, Ester.EN to 0.0010, Ester.EU to EuDepotPK.KA, Ester.E2 to 0.5)
    private val K1_SLOW = mapOf(Ester.EB to 0.114, Ester.EV to 0.0138, Ester.EC to 0.004510574, Ester.EN to 0.0050, Ester.EU to EuDepotPK.KA, Ester.E2 to 0.0)
    private val FORMATION = mapOf(Ester.EB to 0.1092, Ester.EV to 0.0623, Ester.EC to 0.1173, Ester.EN to 0.12, Ester.EU to EuDepotPK.RELEASE_SCALE, Ester.E2 to 1.0)
    private val K2 = mapOf(Ester.EB to 0.090, Ester.EV to 0.070, Ester.EC to 0.045, Ester.EN to 0.015, Ester.EU to EuDepotPK.K_CLEAVE, Ester.E2 to 0.0)
    private const val ORAL_K_ABS_E2 = 0.32
    private const val ORAL_K_ABS_EV = 0.05
    const val ORAL_BIOAVAILABILITY = 0.03
    private const val K_ABS_SL = 1.8
    /** Sublingual hold tiers: quick 2 min, casual 5, standard 10, strict 15. */
    val SL_TIER_THETA = doubleArrayOf(0.01, 0.04, 0.11, 0.18)
    val SL_TIER_HOLD_MIN = intArrayOf(2, 5, 10, 15)

    class Params(val fracFast: Double, val k1Fast: Double, val k1Slow: Double, val k2: Double, val k3: Double, val f: Double,
                 val rateMGh: Double, val fFast: Double, val fSlow: Double) {
        fun withK3(k: Double) = Params(fracFast, k1Fast, k1Slow, k2, k, f, rateMGh, fFast, fSlow)
    }

    private fun sublingualTheta(extras: DoseExtras, checkTierFinite: Boolean): Double {
        var theta = 0.11
        val custom = extras.sublingualTheta
        if (custom != null) {
            if (custom.isFinite()) theta = min(1.0, max(0.0, custom))
        } else extras.sublingualTier?.let { raw ->
            if (!checkTierFinite || raw.isFinite()) {
                val idx = min(SL_TIER_THETA.size - 1.0, max(0.0, jsRound(raw))).toInt()
                theta = SL_TIER_THETA[idx]
            }
        }
        return theta
    }

    fun bioavailabilityMultiplier(route: Route, ester: Ester, extras: DoseExtras = DoseExtras()): Double {
        val mw = toE2Factor(ester)
        return when (route) {
            Route.INJECTION -> (FORMATION[ester] ?: 0.08) * mw
            Route.ORAL -> ORAL_BIOAVAILABILITY * mw
            Route.SUBLINGUAL -> { val t = sublingualTheta(extras, false); (t + (1 - t) * ORAL_BIOAVAILABILITY) * mw }
            Route.GEL -> {
                val product = Gel.product(extras.gelProductId)
                val area = extras.areaCM2?.takeIf { it > 0 } ?: product.defaultAreaCM2
                val k = Gel.resolveKinetics(product, Gel.siteOf(extras), product.refDoseMG, area)
                k.kPen / (k.kPen + k.kLoss) * Gel.coApplicationFactor(extras) * mw
            }
            Route.PATCH_APPLY -> 1.0 * mw
            Route.PATCH_REMOVE -> 0.0
        }
    }

    fun resolveParams(event: DoseEvent): Params {
        val k3 = if (event.route == Route.INJECTION && event.ester != Ester.EU) CorePK.K_CLEAR_INJECTION else CorePK.K_CLEAR
        val toE2 = toE2Factor(event.ester)
        val ex = event.extras
        return when (event.route) {
            Route.INJECTION -> {
                val f = bioavailabilityMultiplier(Route.INJECTION, event.ester, ex)
                Params(FRAC_FAST[event.ester] ?: 0.5, (K1_FAST[event.ester] ?: 0.1) * CorePK.DEPOT_K1_CORR,
                    (K1_SLOW[event.ester] ?: 0.01) * CorePK.DEPOT_K1_CORR, K2[event.ester] ?: 0.0, k3, f, 0.0, f, f)
            }
            Route.SUBLINGUAL -> {
                val theta = sublingualTheta(ex, true)
                val fFast = toE2; val fSlow = ORAL_BIOAVAILABILITY * toE2
                Params(theta, K_ABS_SL, if (event.ester == Ester.EV) ORAL_K_ABS_EV else ORAL_K_ABS_E2, K2[event.ester] ?: 0.0, k3,
                    theta * fFast + (1 - theta) * fSlow, 0.0, fFast, fSlow)
            }
            Route.GEL -> {
                val f = bioavailabilityMultiplier(Route.GEL, event.ester, ex)
                Params(1.0, 0.022, 0.0, 0.0, k3, f, 0.0, f, f)
            }
            Route.PATCH_APPLY -> {
                val f = bioavailabilityMultiplier(Route.PATCH_APPLY, event.ester, ex)
                val rel = ex.releaseRateUGPerDay
                val rate = if (rel != null && rel.isFinite() && rel > 0) rel / 24 / 1000 * f else 0.0
                if (rate > 0) Params(1.0, 0.0, 0.0, 0.0, k3, f, rate, f, f) else Params(1.0, 0.0075, 0.0, 0.0, k3, f, 0.0, f, f)
            }
            Route.PATCH_REMOVE -> Params(0.0, 0.0, 0.0, 0.0, k3, 0.0, 0.0, 0.0, 0.0)
            Route.ORAL -> if (Antiandrogens.isAntiandrogen(event.ester)) Params(1.0, 1.0, 0.0, 0.0, 0.017, 0.7, 0.0, 0.7, 0.7) else {
                val f = ORAL_BIOAVAILABILITY * toE2
                Params(1.0, if (event.ester == Ester.EV) ORAL_K_ABS_EV else ORAL_K_ABS_E2, 0.0,
                    if (event.ester == Ester.EV) K2.getValue(Ester.EV) else 0.0, k3, f, 0.0, f, f)
            }
        }
    }

    fun cpaCentralAmount(doseMG: Double, tau: Double): Double {
        if (tau < 0 || doseMG <= 0) return 0.0
        val f = CpaPK.F; val ka = CpaPK.KA; val alpha = CpaPK.ALPHA; val beta = CpaPK.BETA; val k21 = CpaPK.K21
        val eps = 1e-8
        if (abs(alpha - ka) < eps || abs(beta - ka) < eps || abs(alpha - beta) < eps) {
            if (abs(ka - beta) < eps) return max(0.0, doseMG * f * ka * tau * exp(-beta * tau))
            return max(0.0, doseMG * f * ka / (ka - beta) * (exp(-beta * tau) - exp(-ka * tau)))
        }
        val a = (k21 - ka) / ((alpha - ka) * (beta - ka))
        val b = (k21 - alpha) / ((ka - alpha) * (beta - alpha))
        val c = (k21 - beta) / ((ka - beta) * (alpha - beta))
        return max(0.0, doseMG * f * ka * (a * exp(-ka * tau) + b * exp(-alpha * tau) + c * exp(-beta * tau)))
    }

    fun bicalutamideAmount(doseMG: Double, tau: Double): Double {
        if (tau < 0 || doseMG <= 0) return 0.0
        val ka = BicaPK.KA; val ke = BicaPK.KE
        if (abs(ka - ke) < 1e-9) return max(0.0, doseMG * ka * tau * exp(-ke * tau))
        return max(0.0, doseMG * ka / (ka - ke) * (exp(-ke * tau) - exp(-ka * tau)))
    }

    /** Absorption k1 → ester hydrolysis k2 → clearance k3, closed form. */
    fun analytic3C(tau: Double, doseMG: Double, f: Double, k1: Double, k2: Double, k3: Double): Double {
        if (k1 <= 0 || doseMG <= 0) return 0.0
        val k1k2 = k1 - k2; val k1k3 = k1 - k3; val k2k3 = k2 - k3
        if (abs(k1k2) < 1e-9 || abs(k1k3) < 1e-9 || abs(k2k3) < 1e-9) return 0.0
        val t1 = exp(-k1 * tau) / (k1k2 * k1k3)
        val t2 = exp(-k2 * tau) / (-k1k2 * k2k3)
        val t3 = exp(-k3 * tau) / (k1k3 * k2k3)
        return doseMG * f * k1 * k2 * (t1 + t2 + t3)
    }

    /** One-compartment Bateman form with ka = k1Fast. */
    fun oneCompAmount(tau: Double, doseMG: Double, p: Params): Double {
        val k1 = p.k1Fast
        if (abs(k1 - p.k3) < 1e-9) return doseMG * p.f * k1 * tau * exp(-p.k3 * tau)
        return doseMG * p.f * k1 / (k1 - p.k3) * (exp(-p.k3 * tau) - exp(-k1 * tau))
    }

    private fun branch(dose: Double, f: Double, ka: Double, ke: Double, t: Double): Double =
        if (abs(ka - ke) < 1e-9) dose * f * ka * t * exp(-ke * t) else dose * f * ka / (ka - ke) * (exp(-ke * t) - exp(-ka * t))

    fun patchInstanceIdOf(event: DoseEvent): String = event.extras.patchInstanceId ?: event.id

    /** Targeted removals pair by instance id; legacy removals end the first apply strictly before them. */
    fun findPatchRemoval(apply: DoseEvent, events: List<DoseEvent>): DoseEvent? {
        val applyId = patchInstanceIdOf(apply)
        var targeted: DoseEvent? = null; var targetedT = Double.POSITIVE_INFINITY
        var legacy: DoseEvent? = null; var legacyT = Double.POSITIVE_INFINITY
        for (ev in events) {
            if (ev.route != Route.PATCH_REMOVE) continue
            val target = ev.extras.patchRemovalFor
            if (target != null) {
                if (ev.timeH < apply.timeH) continue
                if (target == applyId && ev.timeH < targetedT) { targeted = ev; targetedT = ev.timeH }
            } else {
                if (ev.timeH <= apply.timeH) continue
                if (ev.timeH < legacyT) { legacy = ev; legacyT = ev.timeH }
            }
        }
        return targeted ?: legacy
    }

    /**
     * Central amount (mg) of one event at [tau] hours after it, with the clearance
     * scaled by [kScale] (1.0 = population). Anti-androgens contribute 0 here.
     */
    fun e2Amount(event: DoseEvent, allEvents: List<DoseEvent>, tau: Double, kScale: Double = 1.0, params: Params = resolveParams(event),
                 wearH: Double? = null): Double {
        if (tau < 0) return 0.0
        if (event.route == Route.PATCH_REMOVE || Antiandrogens.isAntiandrogen(event.ester)) return 0.0
        return routeAmount(event, allEvents, tau, kScale, params, wearH)
    }

    /** Route model without the anti-androgen exclusion; the population simulation sums these per compound. */
    private fun routeAmount(event: DoseEvent, allEvents: List<DoseEvent>, tau: Double, kScale: Double, params: Params, wearH: Double?): Double {
        if (tau < 0) return 0.0
        val k3 = params.k3 * kScale
        val dose = event.doseMG
        return when (event.route) {
            Route.INJECTION -> analytic3C(tau, dose * params.fracFast, params.f, params.k1Fast, params.k2, k3) +
                analytic3C(tau, dose * (1.0 - params.fracFast), params.f, params.k1Slow, params.k2, k3)
            Route.GEL -> Gel.eventCentralAmount(event, tau, k3)
            Route.ORAL -> oneCompAmount(tau, dose, params.withK3(k3))
            Route.SUBLINGUAL -> {
                val df = dose * params.fracFast; val ds = dose * (1.0 - params.fracFast)
                if (params.k2 > 0) analytic3C(tau, df, params.fFast, params.k1Fast, params.k2, k3) + analytic3C(tau, ds, params.fSlow, params.k1Slow, params.k2, k3)
                else branch(df, params.fFast, params.k1Fast, k3, tau) + branch(ds, params.fSlow, params.k1Slow, k3, tau)
            }
            Route.PATCH_APPLY -> {
                val wear = wearH ?: ((findPatchRemoval(event, allEvents)?.timeH ?: Double.MAX_VALUE) - event.timeH)
                if (params.rateMGh > 0) {
                    if (tau <= wear) params.rateMGh / k3 * (1 - exp(-k3 * tau))
                    else params.rateMGh / k3 * (1 - exp(-k3 * wear)) * exp(-k3 * (tau - wear))
                } else {
                    val pk = params.withK3(k3)
                    if (tau > wear) oneCompAmount(wear, dose, pk) * exp(-k3 * (tau - wear)) else oneCompAmount(tau, dose, pk)
                }
            }
            Route.PATCH_REMOVE -> 0.0
        }
    }

    /** Per-event amount model used by [simulate]; precomputes route parameters once. */
    private class EventModel(val event: DoseEvent, all: List<DoseEvent>) {
        private val params = resolveParams(event)
        private val wear: Double? = if (event.route == Route.PATCH_APPLY) (findPatchRemoval(event, all)?.timeH ?: Double.MAX_VALUE) - event.timeH else null
        private val all = all
        fun amount(t: Double): Double {
            val tau = t - event.timeH
            return when {
                event.route == Route.GEL -> Gel.eventCentralAmount(event, tau, CorePK.K_CLEAR)
                event.route == Route.ORAL && event.ester == Ester.CPA -> if (tau < 0) 0.0 else cpaCentralAmount(event.doseMG, tau)
                event.route == Route.ORAL && event.ester == Ester.BICA -> if (tau < 0) 0.0 else bicalutamideAmount(event.doseMG, tau)
                else -> routeAmount(event, all, tau, 1.0, params, wear)
            }
        }
    }

    /** Body weight at [t] as a step function over time-sorted events (earliest weight extends backward). */
    fun weightAt(sorted: List<DoseEvent>, t: Double): Double {
        if (sorted.isEmpty()) return 70.0
        var result = sorted[0].weightKG
        for (e in sorted) { if (e.timeH <= t) result = e.weightKG else break }
        return result
    }

    /**
     * Deterministic population simulation. The grid spans the first event −24 h to the last event
     * +14 d (or [endTimeH] if later), with a route-dependent maximum step and at least 1000 points.
     */
    fun simulate(events: List<DoseEvent>, endTimeH: Double? = null): SimulationResult? {
        if (events.isEmpty()) return null
        val sorted = events.sortedBy { it.timeH }
        val models = sorted.filter { it.route != Route.PATCH_REMOVE }.map { EventModel(it, sorted) }
        val start = sorted.first().timeH - 24
        val end = max(sorted.last().timeH + 24 * 14, endTimeH ?: Double.NEGATIVE_INFINITY)
        val routes = sorted.map { it.route }.toSet()
        val maxStep = when { Route.SUBLINGUAL in routes -> 0.25; Route.ORAL in routes -> 0.5; Route.GEL in routes -> 1.0; else -> 2.0 }
        val steps = max(1000, ceil((end - start) / maxStep).toInt() + 1)
        val present = Antiandrogens.SPECS.keys.filter { a -> sorted.any { it.ester == a } }
        val timeH = DoubleArray(steps); val total = DoubleArray(steps); val e2 = DoubleArray(steps); val cpa = DoubleArray(steps)
        val by = present.associateWith { DoubleArray(steps) }
        val stepSize = (end - start) / (steps - 1)
        var auc = 0.0
        for (i in 0 until steps) {
            val t = start + i * stepSize
            var e2Amount = 0.0
            val aa = HashMap<Ester, Double>()
            for (m in models) {
                val a = m.amount(t)
                if (Antiandrogens.isAntiandrogen(m.event.ester)) aa[m.event.ester] = (aa[m.event.ester] ?: 0.0) + a else e2Amount += a
            }
            val w = weightAt(sorted, t)
            val concE2 = e2Amount * 1e9 / (CorePK.VD_PER_KG * w * 1000)
            var concCpa = 0.0
            for (a in present) {
                val c = Antiandrogens.SPECS.getValue(a).concFromAmountMG(aa[a] ?: 0.0, w)
                by.getValue(a)[i] = c
                if (a == Ester.CPA) concCpa = c
            }
            val conc = concE2 + concCpa * 1000
            timeH[i] = t; total[i] = conc; e2[i] = concE2; cpa[i] = concCpa
            if (i > 0) auc += 0.5 * (conc + total[i - 1]) * stepSize
        }
        return SimulationResult(timeH, total, e2, cpa, by, auc)
    }

    /** Linear interpolation on a simulation grid, clamped at both ends. */
    fun interpolate(timeH: DoubleArray, values: DoubleArray, hour: Double): Double? {
        if (timeH.isEmpty()) return null
        if (hour <= timeH[0]) return values[0]
        if (hour >= timeH.last()) return values.last()
        var lo = 0; var hi = timeH.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (timeH[mid] == hour) return values[mid]
            if (timeH[mid] < hour) lo = mid else hi = mid
        }
        val t0 = timeH[lo]; val t1 = timeH[hi]
        if (t1 == t0) return values[lo]
        return values[lo] + (values[hi] - values[lo]) * ((hour - t0) / (t1 - t0))
    }
}
