package net.plainnotes.app.pk.formulations

import kotlin.math.exp
import kotlin.math.expm1
import kotlin.math.ln
import kotlin.math.max

/** Separate from the E2 engine, its fitted amplitudes, persisted snapshots and calibration. */
object TestosteroneFormulations {
    enum class Status { RESEARCH_SHAPE_ONLY, RECORD_ONLY }
    data class Product(val code: String, val name: String, val route: String, val ester: String,
        val parentMassFraction: Double, val status: Status, val source: String, val version: String = "T-SHAPE-1",
        /** Only fixed strengths verified in the exact product's official RCP, never a route default. */
        val compoundMgPerMl: Double? = null)
    private const val ANSM = "https://base-donnees-publique.medicaments.gouv.fr/medicament/"
    val products = listOf(
        Product("T_ANDROGEL_50", "ANDROGEL 50 mg", "GEL", "T", 1.0, Status.RESEARCH_SHAPE_ONLY, ANSM + "64205548/extrait"),
        Product("T_ANDROGEL_162", "ANDROGEL 16.2 mg/g", "GEL", "T", 1.0, Status.RECORD_ONLY, ANSM + "68097237/extrait"),
        Product("T_ANDROTARDYL_IM", "ANDROTARDYL 250 mg / 1 mL · IM", "IM", "TE", 288.42 / 400.59, Status.RESEARCH_SHAPE_ONLY, ANSM + "68178899/extrait", compoundMgPerMl = 250.0),
        Product("T_NEBIDO_IM", "NEBIDO 1000 mg / 4 mL · IM", "IM", "TU", 288.42 / 456.70, Status.RESEARCH_SHAPE_ONLY, ANSM + "69033757/extrait", compoundMgPerMl = 250.0),
        Product("T_XYOSTED_SC", "XYOSTED", "SC", "TE", 288.42 / 400.59, Status.RECORD_ONLY, "https://dailymed.nlm.nih.gov/dailymed/search.cfm?query=XYOSTED"),
        Product("T_CYPIONATE_SC", "Testosterone cypionate · SC", "SC", "TC", 288.42 / 412.61, Status.RECORD_ONLY, "https://pmc.ncbi.nlm.nih.gov/articles/PMC5686655/"),
        Product("T_CYPIONATE_IM", "Testosterone cypionate · IM", "IM", "TC", 288.42 / 412.61, Status.RECORD_ONLY, "https://dailymed.nlm.nih.gov/dailymed/search.cfm?query=testosterone+cypionate"),
        Product("T_ANDRODERM", "ANDRODERM", "PATCH", "T", 1.0, Status.RECORD_ONLY, "https://dailymed.nlm.nih.gov/dailymed/search.cfm?query=ANDRODERM"),
        Product("T_JATENZO", "JATENZO", "ORAL", "TU", 288.42 / 456.70, Status.RECORD_ONLY, "https://dailymed.nlm.nih.gov/dailymed/search.cfm?query=JATENZO"),
        Product("T_NATESTO", "NATESTO", "NASAL", "T", 1.0, Status.RECORD_ONLY, "https://dailymed.nlm.nih.gov/dailymed/search.cfm?query=NATESTO"),
        Product("T_TESTOPEL", "TESTOPEL", "IMPLANT", "T", 1.0, Status.RECORD_ONLY, "https://dailymed.nlm.nih.gov/dailymed/search.cfm?query=TESTOPEL"),
    )
    fun product(code: String?): Product? = products.singleOrNull { it.code == code }

    /** No guessed tablet fraction, pump amount, patch contents or unspecified product strength. */
    fun parentMg(amount: Double, unit: String, product: Product): Double? {
        if (!amount.isFinite() || amount <= 0) return null
        val mg = when (unit) {
            "MG" -> amount; "UG" -> amount / 1_000; "NG" -> amount / 1_000_000
            "ML" -> amount * (product.compoundMgPerMl ?: return null)
            else -> return null
        }
        return (mg * product.parentMassFraction).takeIf { it.isFinite() && it > 0 }
    }

    data class Event(val timeH: Double, val parentMg: Double)
    data class Shape(val timeH: DoubleArray, val relative: DoubleArray, val variants: List<DoubleArray>, val version: String)

    /** Stable causal convolution of a first-order input with first-order removal, in days. */
    internal fun firstOrder(t: Double, input: Double, removal: Double): Double {
        require(t.isFinite() && input.isFinite() && removal.isFinite() && input > 0 && removal > 0)
        if (t <= 0) return 0.0
        val d = input - removal
        if (kotlin.math.abs(d * t) < 1e-7) return input * t * exp(-removal * t)
        return if (d < 0) input * exp(-input * t) * expm1(d * t) / d
            else input * exp(-removal * t) * -expm1(-d * t) / d
    }

    /** Erlang-2 transit input, not a generic Bateman substitution for every depot. */
    internal fun transit(t: Double, rate: Double, removal: Double): Double {
        require(t.isFinite() && rate.isFinite() && removal.isFinite() && rate > 0 && removal > 0)
        if (t <= 0) return 0.0
        val d = removal - rate
        if (kotlin.math.abs(d * t) < 1e-4) {
            val x = d * t
            return rate * rate * t * t * exp(-rate * t) * (0.5 - x / 6 + x * x / 24)
        }
        // Avoid exp(+large) when removal < input.
        return rate * rate * (exp(-rate * t) * (d * t - 1) + exp(-removal * t)) / (d * d)
    }

    private fun response(code: String, variant: Int, days: Double): Double {
        if (days <= 0) return 0.0
        val ke = 24 * ln(2.0) // ASSUMED parent half-life 1 h, not inferred from depot half-life.
        return max(0.0, when (code) {
            "T_ANDROGEL_50" -> firstOrder(days, listOf(1.0, 1.5, 2.5)[variant], ke)
            "T_ANDROTARDYL_IM" -> {
                val w = listOf(0.2, 0.4, 0.6)[variant]
                (1 - w) * transit(days, 0.5, ke) + w * firstOrder(days, ln(2.0) / 4.5, ke)
            }
            "T_NEBIDO_IM" -> {
                val w = listOf(0.05, 0.4, 0.8)[variant]
                (1 - w) * transit(days, 0.14, ke) + w * firstOrder(days, ln(2.0) / 90, ke)
            }
            else -> error("No research response for $code")
        })
    }

    /**
     * Sensitivity scenarios, NEVER serum T. Each scenario is normalized to its own maximum on
     * this displayed window. Values are not comparable across windows/products. No baseline,
     * absorption fraction, clearance, feedback, aromatization or lab calibration is inferred.
     * Actual dose history is summed; no planned dose is added. All three alternatives are returned
     * (not a confidence interval or a preferred fitted physiological parameter).
     */
    fun shape(product: Product, events: List<Event>, grid: DoubleArray, cancelled: () -> Unit = {}): Shape? {
        if (product.status != Status.RESEARCH_SHAPE_ONLY || events.isEmpty()) return null
        require(grid.isNotEmpty() && grid.all { it.isFinite() } && (1 until grid.size).all { grid[it - 1] < grid[it] })
        require(events.all { it.timeH.isFinite() && it.parentMg.isFinite() && it.parentMg > 0 })
        val variants = (0..2).map { variant ->
            val values = DoubleArray(grid.size) { i ->
                cancelled()
                events.sumOf { e -> if (grid[i] <= e.timeH) 0.0 else e.parentMg * response(product.code, variant, (grid[i] - e.timeH) / 24) }
            }
            require(values.all { it.isFinite() && it >= 0 })
            val peak = values.maxOrNull() ?: 0.0
            if (peak > 0) values.indices.forEach { values[it] /= peak }
            values
        }
        return Shape(grid.copyOf(), variants[1], variants, product.version)
    }
}
