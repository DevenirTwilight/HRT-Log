package net.plainnotes.app.pk.experimental

import kotlin.math.exp
import kotlin.math.ln

/**
 * ANSM-RCP-based testosterone *shape* prototype. NOT a serum testosterone concentration
 * estimator: the French product labels report TOTAL T (including an unknown endogenous
 * baseline), and their depot-release half-lives are not the intrinsic T elimination half-life.
 *
 * A value of 1.0 means the theoretical peak of one label-reference ester dose. It is
 * dimensionless; it is NOT 1 ng/mL, a fraction of the normal range, or a therapeutic target.
 * This class is intentionally outside production Engine/ConcentrationCalculator dispatch.
 */
enum class TestosteroneDepot(
    val referenceEsterMg: Double,
    val referenceParentMg: Double,
    val labelTmaxDays: Double,
    val releaseHalfLifeDays: Double,
    val officialSource: String,
) {
    ENANTHATE_IM(
        250.0, 180.0, 2.25, 4.5,
        "https://base-donnees-publique.medicaments.gouv.fr/medicament/68178899/extrait",
    ),
    UNDECANOATE_IM(
        1000.0, 631.5, 7.0, 90.0,
        "https://base-donnees-publique.medicaments.gouv.fr/medicament/69033757/extrait",
    ),
}

data class TestosteroneDepotDose(val timeH: Double, val esterMassMg: Double)

/** Pure dimensionless research function; release-based half-life, not parent drug clearance. */
object OfficialTestosteroneDepotShapes {
    /** Solve the first-order input/release peak time. Both parameters are in h^-1. */
    private fun absorptionRate(depot: TestosteroneDepot): Double {
        val lambda = ln(2.0) / (depot.releaseHalfLifeDays * 24.0)
        val target = depot.labelTmaxDays * 24.0
        fun peakTime(ka: Double) = ln(ka / lambda) / (ka - lambda)
        require(target > 0 && target < 1 / lambda)
        var lo = lambda * (1 + 1e-6)
        var hi = 2.0
        repeat(100) {
            val mid = (lo + hi) / 2
            if (peakTime(mid) > target) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    /**
     * The depot-release response, normalized to its own peak.
     * This is not a dose-to-concentration conversion.
     */
    fun normalizedResponse(depot: TestosteroneDepot, elapsedH: Double): Double {
        require(elapsedH.isFinite())
        if (elapsedH <= 0) return 0.0
        val lambda = ln(2.0) / (depot.releaseHalfLifeDays * 24)
        val ka = absorptionRate(depot)
        fun kernel(t: Double) = exp(-lambda * t) - exp(-ka * t)
        return (kernel(elapsedH) / kernel(depot.labelTmaxDays * 24)).coerceAtLeast(0.0)
    }

    /**
     * One exact ester formulation per scenario. Reference-dose equivalents are computed from
     * the **actual ester mass** then converted to parent-T mass by the product RCP ratio;
     * not a linear exposure comparison between different esters or vehicles.
     */
    fun relativeScenario(depot: TestosteroneDepot, doses: List<TestosteroneDepotDose>, gridH: DoubleArray): DoubleArray {
        require(gridH.all { it.isFinite() })
        require(doses.all { it.timeH.isFinite() && it.esterMassMg.isFinite() && it.esterMassMg > 0 })
        return DoubleArray(gridH.size) { i ->
            doses.sumOf { dose ->
                if (gridH[i] < dose.timeH) 0.0 else {
                    val parentMg = dose.esterMassMg * depot.referenceParentMg / depot.referenceEsterMg
                    (parentMg / depot.referenceParentMg) * normalizedResponse(depot, gridH[i] - dose.timeH)
                }
            }
        }
    }
}
