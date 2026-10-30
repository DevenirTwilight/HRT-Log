package net.plainnotes.app.pk.experimental

import kotlin.math.exp
import kotlin.math.pow

/**
 * EC intramuscular *research-only* response. Gamma-shaped empirical candidate, NOT a
 * clinically validated personal estradiol concentration, and not an Engine dispatch.
 *
 * Original cohort: Oriowo et al., 1980 (n=10 for 5 mg EC in arachis oil); quantitative
 * peak/mean as described in Rothman et al., 2024.
 * https://pubmed.ncbi.nlm.nih.gov/7389356/
 * https://pmc.ncbi.nlm.nih.gov/articles/PMC11669617/
 *
 * The observed mean 337 pg/mL peak at 3.9 days and mean 159 pg/mL over ~11.8 days
 * are FIT targets (group-level serum total E2 with incompletely specified baseline);
 * they do not validate every intermediate timepoint or doses other than the labelled
 * 5 mg reference. A mean over an "elevated" observation window is not AUC(0, infinity).
 *
 * Shape exponent ~3.139 solves the mean/peak constraints for a positive gamma curve
 * with an assumed exactly zero response at administration. The exponent has no
 * demonstrated physiological interpretation.
 */
object OfficialEstradiolCypionateScenario {
    const val referenceEsterMg: Double = 5.0
    const val referencePeakPgMl: Double = 337.0
    const val peakH: Double = 3.9 * 24.0
    const val studyWindowH: Double = 11.8 * 24.0
    private const val SHAPE = 3.1390014044326717

    /** Population summary illustration for the exact 5 mg cohort-reference dose only. */
    fun referenceShape(elapsedH: Double): Double {
        require(elapsedH.isFinite())
        if (elapsedH <= 0.0) return 0.0
        val x = elapsedH / peakH
        return referencePeakPgMl * x.pow(SHAPE) * exp(SHAPE * (1.0 - x))
    }

    /** Dimensionless shape, explicit that arbitrary dosing is NOT calibrated. */
    fun normalized(elapsedH: Double): Double = referenceShape(elapsedH) / referencePeakPgMl

    /**
     * Exact source dose check: dosing outside 5mg would require a validated
     * dose-exposure relationship, which is NOT present in the cohort.
     */
    fun referenceDoseOnly(elapsedH: Double, esterDoseMg: Double): Double {
        require(esterDoseMg == referenceEsterMg) { "No EC dose-response evidence outside 5mg source cohort" }
        return referenceShape(elapsedH)
    }
}
