package net.plainnotes.app.pk

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** One concentration curve the engine can produce. */
enum class Curve(val unit: String) { E2("pg/mL"), CPA("ng/mL"), SPIRONOLACTONE("ng/mL"), CANRENONE("ng/mL"), PROGESTERONE("ng/mL") }

/** Things the interface must say about a curve. */
enum class CurveFlag {
    /** Sublingual tier other than the calibrated one (no literature). */
    EXTRAPOLATED_TIER,
    /** Sublingual data only cover 8 h after a dose. */
    EXTRAPOLATED_AFTER_CALIBRATED_HOURS,
    /** Gel product without its own data (Estreva, home-made). */
    NO_PRODUCT_DATA,
    /** Illustrative curve only (progesterone). */
    ILLUSTRATIVE,
}

/** Why an event gets no curve: no reliable source, so nothing is drawn (user rule: no source, no value). */
enum class Unsupported { SUBLINGUAL_EV, NO_LITERATURE_ESTER, NO_LITERATURE_ROUTE, BICALUTAMIDE, PATCH_WITHOUT_RATE }

/** Which fitted model(s) an event uses. */
sealed interface ModelChoice {
    /** [parts]: (curve, model, share of the dose); several parts for sublingual (fast + swallowed) and spironolactone (+ canrenone). */
    data class Use(val parts: List<Triple<Curve, FittedModel, Double>>, val flags: Set<CurveFlag>) : ModelChoice
    data class None(val reason: Unsupported) : ModelChoice
}

/** Per-model multipliers for calibration and Monte Carlo: amplitude × [amplitude], elimination rates × [rate]. */
data class Scale(val amplitude: Double = 1.0, val rate: Double = 1.0)

class EngineResult(
    val timeH: DoubleArray,
    val curves: Map<Curve, DoubleArray>,
    val flags: Map<Curve, Set<CurveFlag>>,
    /** Fitted models used per curve, so the interface can show parameters and literature. */
    val models: Map<Curve, Set<FittedModel>>,
    val unsupported: Map<String, Unsupported>,
)

/**
 * Literature-based engine (docs/pk-model.md). Each event's response is the fitted model's
 * Σ A_j (e^(−λ_j τ) − e^(−ka τ)); patches use constant release during wear. Bolus events are summed with
 * running exponential sums, so the cost grows with grid points + events, not their product.
 */
object Engine {
    private val GEL_ESTROGEL = setOf(1, 3)   // Oestrogel, EstroGel (same 0.06 % formulation)
    private const val GEL_DIVIGEL = 4

    fun choose(e: DoseEvent): ModelChoice {
        val m = PkParams.models
        fun use(curve: Curve, key: String, vararg flags: CurveFlag) = ModelChoice.Use(listOf(Triple(curve, m.getValue(key), 1.0)), flags.toSet())
        return when (e.route) {
            Route.PATCH_REMOVE -> ModelChoice.None(Unsupported.NO_LITERATURE_ROUTE)
            Route.PATCH_APPLY -> if (e.ester != Ester.E2) ModelChoice.None(Unsupported.NO_LITERATURE_ESTER)
                else if ((e.extras.releaseRateUGPerDay ?: 0.0) <= 0) ModelChoice.None(Unsupported.PATCH_WITHOUT_RATE) else use(Curve.E2, "E2_PATCH")
            Route.GEL -> if (e.ester != Ester.E2) ModelChoice.None(Unsupported.NO_LITERATURE_ESTER) else when (e.extras.gelProductId?.toInt()) {
                in GEL_ESTROGEL -> use(Curve.E2, "E2_GEL_ESTROGEL")
                GEL_DIVIGEL -> use(Curve.E2, "E2_GEL_DIVIGEL")
                else -> use(Curve.E2, "E2_GEL_OTHER", CurveFlag.NO_PRODUCT_DATA)
            }
            Route.INJECTION -> if (e.ester == Ester.EV) use(Curve.E2, "EV_IM") else ModelChoice.None(Unsupported.NO_LITERATURE_ESTER)
            Route.SUBLINGUAL -> when (e.ester) {
                Ester.E2 -> sublingual(e)
                Ester.EV -> ModelChoice.None(Unsupported.SUBLINGUAL_EV)
                else -> ModelChoice.None(Unsupported.NO_LITERATURE_ESTER)
            }
            Route.ORAL -> when (e.ester) {
                Ester.E2 -> use(Curve.E2, "E2_ORAL")
                Ester.EV -> use(Curve.E2, "EV_ORAL")
                Ester.CPA -> use(Curve.CPA, "CPA_ORAL")
                Ester.SPI -> ModelChoice.Use(listOf(Triple(Curve.SPIRONOLACTONE, m.getValue("SPI_PARENT"), 1.0), Triple(Curve.CANRENONE, m.getValue("SPI_CANRENONE"), 1.0)), emptySet())
                Ester.P4 -> use(Curve.PROGESTERONE, "P4_ORAL", CurveFlag.ILLUSTRATIVE)
                Ester.BICA -> ModelChoice.None(Unsupported.BICALUTAMIDE)
                else -> ModelChoice.None(Unsupported.NO_LITERATURE_ESTER)
            }
        }
    }

    /** Doll 2022 calibrates one tier; others scale the mucosal share with hold time (extrapolation). */
    private fun sublingual(e: DoseEvent): ModelChoice {
        val sl = PkParams.model("E2_SL")
        val default = sl.defaultTier ?: 2
        val tier = e.extras.sublingualTier?.toInt()?.coerceIn(0, sl.tierMinutes.size - 1) ?: default
        val mucosal0 = 1 - sl.swallowedShare
        val mucosal = min(1.0, mucosal0 * sl.tierMinutes[tier] / sl.tierMinutes[default])
        val flags = buildSet { add(CurveFlag.EXTRAPOLATED_AFTER_CALIBRATED_HOURS); if (tier != default) add(CurveFlag.EXTRAPOLATED_TIER) }
        val parts = buildList {
            add(Triple(Curve.E2, sl, mucosal / mucosal0))
            val swallowed = 1 - mucosal
            if (swallowed > 0) add(Triple(Curve.E2, PkParams.model(sl.swallowedModel ?: "E2_ORAL"), swallowed))
        }
        return ModelChoice.Use(parts, flags)
    }

    /** Wear time of a patch: until its paired removal, else until the end of the simulation. */
    private fun wearOf(apply: DoseEvent, events: List<DoseEvent>): Double {
        val id = apply.extras.patchInstanceId ?: apply.id
        val removal = events.filter { it.route == Route.PATCH_REMOVE && it.timeH >= apply.timeH && (it.extras.patchRemovalFor == null || it.extras.patchRemovalFor == id) }
            .minByOrNull { it.timeH }
        return (removal?.timeH ?: Double.POSITIVE_INFINITY) - apply.timeH
    }

    /** Patch: constant release of [rateMgH] for [wear] hours into the fitted model, evaluated at [tau]. */
    private fun infusion(m: FittedModel, tau: Double, rateMgH: Double, wear: Double, s: Scale): Double {
        if (tau <= 0) return 0.0
        val te = min(tau, wear)
        fun g(x: Double) = (1 - exp(-x * te)) * exp(-x * (tau - te)) / x
        return max(0.0, rateMgH * s.amplitude * m.terms.sumOf { (a, lam) -> a * (g(lam * s.rate) - g(m.ka)) })
    }

    fun gridFor(events: List<DoseEvent>, endTimeH: Double?): DoubleArray {
        val sorted = events.sortedBy { it.timeH }
        val start = sorted.first().timeH - 24
        val end = max(sorted.last().timeH + 24 * 14, endTimeH ?: Double.NEGATIVE_INFINITY)
        val routes = sorted.map { it.route }.toSet()
        val maxStep = when { Route.SUBLINGUAL in routes -> 0.25; Route.ORAL in routes -> 0.5; Route.GEL in routes -> 1.0; else -> 2.0 }
        val steps = max(1000, ceil((end - start) / maxStep).toInt() + 1)
        val step = (end - start) / (steps - 1)
        return DoubleArray(steps) { start + it * step }
    }

    /**
     * Population curves (or scaled ones for calibration / Monte Carlo via [scale]).
     * Returns null for no events.
     */
    fun simulate(events: List<DoseEvent>, endTimeH: Double? = null, grid: DoubleArray? = null, scale: (Curve, FittedModel) -> Scale = { _, _ -> Scale() }): EngineResult? {
        if (events.isEmpty()) return null
        val sorted = events.sortedBy { it.timeH }
        val t = grid ?: gridFor(sorted, endTimeH)
        val curves = HashMap<Curve, DoubleArray>(); val flags = HashMap<Curve, MutableSet<CurveFlag>>()
        val used = HashMap<Curve, MutableSet<FittedModel>>(); val unsupported = LinkedHashMap<String, Unsupported>()
        // Bolus contributions grouped by (curve, model, absorption rate) so each group shares running sums.
        data class Key(val curve: Curve, val model: FittedModel, val ka: Double)
        val bolus = LinkedHashMap<Key, MutableList<Pair<Double, Double>>>()   // (time, effective mg)
        for (e in sorted) {
            when (val c = choose(e)) {
                is ModelChoice.None -> if (e.route != Route.PATCH_REMOVE) unsupported[e.id] = c.reason
                is ModelChoice.Use -> for ((curve, model, share) in c.parts) {
                    curves.getOrPut(curve) { DoubleArray(t.size) }
                    flags.getOrPut(curve) { mutableSetOf() }.addAll(c.flags)
                    if (model.illustrative) flags.getValue(curve).add(CurveFlag.ILLUSTRATIVE)
                    used.getOrPut(curve) { mutableSetOf() }.add(model)
                    if (e.route == Route.PATCH_APPLY) {
                        val rate = e.extras.releaseRateUGPerDay!! / 1000.0 / 24.0
                        val wear = wearOf(e, sorted); val s = scale(curve, model); val arr = curves.getValue(curve)
                        for (i in t.indices) arr[i] += infusion(model, t[i] - e.timeH, rate * share, wear, s)
                    } else {
                        val w = model.refWeightKg?.let { it / e.weightKG } ?: 1.0
                        bolus.getOrPut(Key(curve, model, model.kaAt(e.doseMG))) { mutableListOf() } += e.timeH to e.doseMG * share * w
                    }
                }
            }
        }
        for ((key, doses) in bolus) {
            val s = scale(key.curve, key.model); val arr = curves.getValue(key.curve)
            val ka = key.ka
            for ((a0, lam0) in key.model.terms) {
                // Keep each term's AUC when the absorption rate differs from the fitted one (dose-dependent absorption).
                val a = if (ka == key.model.ka) a0 else a0 * (1 / lam0 - 1 / key.model.ka) / (1 / lam0 - 1 / ka)
                val lam = lam0 * s.rate
                runningSum(t, doses, lam, a * s.amplitude, arr)
                runningSum(t, doses, ka, -a * s.amplitude, arr)
            }
        }
        for (arr in curves.values) for (i in arr.indices) if (arr[i] < 0) arr[i] = 0.0
        return EngineResult(t, curves, flags, used, unsupported)
    }

    /** arr[i] += coef · Σ_{t_k ≤ t_i} dose_k · e^(−x (t_i − t_k)), in one pass over grid and doses (both sorted). */
    private fun runningSum(t: DoubleArray, doses: List<Pair<Double, Double>>, x: Double, coef: Double, arr: DoubleArray) {
        var sum = 0.0; var last = Double.NaN; var k = 0
        for (i in t.indices) {
            if (!last.isNaN()) sum *= exp(-x * (t[i] - last))
            while (k < doses.size && doses[k].first <= t[i]) { sum += doses[k].second * exp(-x * (t[i] - doses[k].first)); k++ }
            last = t[i]
            arr[i] += coef * sum
        }
    }
}
