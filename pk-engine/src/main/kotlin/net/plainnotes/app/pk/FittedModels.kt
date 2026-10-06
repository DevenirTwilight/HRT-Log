package net.plainnotes.app.pk

import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Literature-fitted concentration model for one formulation (docs/pk-model.md, "按文献重写").
 *
 * Response to 1 mg given at t = 0: h(t) = Σ A_j (e^(−λ_j t) − e^(−ka t)). This is an **assumed structure fitted to
 * observed literature values** (Cmax, Tmax, AUC, apparent half-life), not a published compartment model: the
 * literature reviewed in M4a reports none. The numbers come from `pk-params.json` ("models"), written by
 * `tools/pk-fit/fit.py`, and each model lists the literature entries it was fitted to ([basis]).
 */
class FittedModel(
    val key: String,
    val unit: String,
    val ka: Double,
    /** (A per mg, λ per hour) pairs. */
    val terms: List<Pair<Double, Double>>,
    val basis: List<String>,
    val assumption: String,
    /** Between-person coefficient of variation used for uncertainty bands. */
    val cv: Double,
    val cvSource: String,
    /** E2 content per mg of the compound (EV → 0.764), when the model describes estradiol. */
    val e2PerMg: Double?,
    /** Amplitudes are for this body weight and scale with refWeight / weight (only where clearance is per kg). */
    val refWeightKg: Double?,
    /** Dose-dependent absorption rate: (dose mg, ka) points, interpolated on log(dose). */
    val kaDosePoints: List<Pair<Double, Double>>,
) {
    private fun kaFor(doseMg: Double): Double {
        if (kaDosePoints.size < 2 || doseMg <= 0) return ka
        val (d0, k0) = kaDosePoints.first(); val (d1, k1) = kaDosePoints.last()
        val x = ((ln(doseMg) - ln(d0)) / (ln(d1) - ln(d0))).coerceIn(0.0, 1.0)
        return exp(ln(k0) + x * (ln(k1) - ln(k0)))
    }

    /** Concentration [tau] hours after [doseMg] mg; `amplitudeScale` multiplies all terms (calibration, sampling). */
    fun response(tau: Double, doseMg: Double, weightKg: Double, amplitudeScale: Double = 1.0, rateScale: Double = 1.0): Double {
        if (tau < 0 || doseMg <= 0) return 0.0
        val kd = kaFor(doseMg)
        val w = refWeightKg?.let { it / weightKg } ?: 1.0
        var sum = 0.0
        for ((a, lam0) in terms) {
            val lam = lam0 * rateScale
            // Keep each term's AUC when the absorption rate differs from the fitted one (dose-dependent absorption).
            val aa = if (kd == ka) a else a * (1 / lam0 - 1 / ka) / (1 / lam0 - 1 / kd)
            sum += aa * (exp(-lam * tau) - exp(-kd * tau))
        }
        return max(0.0, sum * doseMg * w * amplitudeScale)
    }

    /** Area under the single-dose curve per mg (0 to infinity), at the reference weight. */
    val aucPerMg: Double get() = terms.sumOf { (a, lam) -> a * (1 / lam - 1 / ka) }
    /** Slowest elimination half-life in hours. */
    val terminalHalfLifeH: Double get() = ln(2.0) / terms.minOf { it.second }
}

/** Reads `pk-params.json` from the classpath once. */
object PkParams {
    private val root: JSONObject by lazy {
        val text = PkParams::class.java.getResourceAsStream("/pk-params.json")!!.bufferedReader().use { it.readText() }
        JSONObject(text)
    }
    val version: String get() = root.getString("version")

    val models: Map<String, FittedModel> by lazy {
        val m = root.getJSONObject("models")
        m.keySet().associateWith { key ->
            val o = m.getJSONObject(key)
            val terms = o.getJSONArray("terms").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { t -> t.getDouble("A_per_mg") to t.getDouble("lambda_per_h") } } }
            val basis = o.getJSONArray("basis").let { a -> (0 until a.length()).map { a.getString(it) } }
            val points = o.optJSONArray("ka_dose_points")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).let { p -> p.getDouble("dose_mg") to p.getDouble("ka_per_h") } } }.orEmpty()
            FittedModel(key, o.getString("unit"), o.getDouble("ka_per_h"), terms, basis, o.getString("assumption"), o.getDouble("cv"),
                o.optString("cv_source"), if (o.has("e2_per_mg")) o.getDouble("e2_per_mg") else null,
                if (o.has("ref_weight_kg")) o.getDouble("ref_weight_kg") else null, points)
        }
    }

    fun model(key: String): FittedModel = models[key] ?: error("No fitted model $key in pk-params.json")

    /** Human-readable reference for a literature reference id such as "transdermal_cpa:Kuhnz1993". */
    fun reference(id: String): JSONObject? {
        val refs = root.getJSONArray("references")
        for (i in 0 until refs.length()) refs.getJSONObject(i).let { if (it.getString("id") == id) return it }
        return null
    }
}

/** Numeric helpers shared by the engine and its validation tests. */
object Curves {
    /** Peak (value, time) of f on [0, tMax] by a scan and golden-section refinement. */
    fun peak(tMax: Double, step: Double = 0.01, f: (Double) -> Double): Pair<Double, Double> {
        var bestT = 0.0; var best = -1.0; var t = 0.0
        while (t <= tMax) { val v = f(t); if (v > best) { best = v; bestT = t }; t += step }
        var lo = max(0.0, bestT - step); var hi = min(tMax, bestT + step)
        val g = (kotlin.math.sqrt(5.0) - 1) / 2
        repeat(60) { val a = hi - g * (hi - lo); val b = lo + g * (hi - lo); if (f(a) > f(b)) hi = b else lo = a }
        val tm = (lo + hi) / 2
        return f(tm) to tm
    }

    /** Trapezoidal area of f on [t0, t1]. */
    fun area(t0: Double, t1: Double, step: Double = 0.05, f: (Double) -> Double): Double {
        var s = 0.0; var t = t0; var prev = f(t0)
        while (t < t1 - 1e-9) { val nt = min(t1, t + step); val v = f(nt); s += (prev + v) / 2 * (nt - t); prev = v; t = nt }
        return s
    }

    /** Apparent half-life from two points of a declining curve. */
    fun apparentHalfLife(f: (Double) -> Double, t1: Double, t2: Double) = ln(2.0) * (t2 - t1) / ln(f(t1) / f(t2))
}
