package net.plainnotes.app.pk

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.exp

/**
 * Mathematical, NOT clinical-validation test for UTROGESTAN 200 mg oral ANSM RCP §5.2.
 *
 * For a NONNEGATIVE mixture of simple one-step absorption/clearance Bateman kernels,
 * c(2t)/c(t) < 2 at any t>0: each term obeys
 * (exp(-lambda*2t)-exp(-ka*2t))/(exp(-lambda*t)-exp(-ka*t))
 * = exp(-lambda*t)+exp(-ka*t) < 2.
 *
 * A mean table that violates this cannot be fit *exactly* by the stated simple structure
 * without changes (e.g. transit delay, non-stationary input, baseline/assay correction).
 * Lack of RCP variance means this is NOT evidence of statistically significant disagreement.
 */
class FrenchUtrogestanStructuralLimitTest {
    private val officialPreDose = 0.13
    private val officialAt1 = 4.25
    private val officialAt2 = 11.75

    @Test fun officialGroupMeanRiseExceedsSimplePositiveOneStageKernelBound() {
        val observedIncrementRatio = (officialAt2 - officialPreDose) / (officialAt1 - officialPreDose)
        assertTrue(observedIncrementRatio > 2.8)
        assertTrue(observedIncrementRatio > 2.0)
    }

    @Test fun positivityBoundForModelWithoutTransitDelayIsNotATunableHalfLifeIssue() {
        for (lambda in listOf(0.005, 0.03, 0.3, 2.0)) {
            for (ka in listOf(0.02, 0.2, 1.0, 4.0)) {
                if (ka == lambda) continue
                val first = exp(-lambda) - exp(-ka)
                val second = exp(-2 * lambda) - exp(-2 * ka)
                val ratio = second / first
                assertTrue(ratio < 2.0, "positive Bateman input never increases by >2x in doubled time")
                assertTrue(ratio > 0.0)
            }
        }
    }

    @Test fun existingP4MustRemainIllustrativeNotSilentlyCalledVerified() {
        val m = PkParams.model("P4_ORAL")
        assertTrue(m.illustrative)
        // The old model's day-1 6h prediction is much higher than the French RCP table
        // under a common zero-dose-response baseline, and warrants further study.
        val predicted = m.response(6.0, 200.0, 70.0)
        assertTrue(predicted > 4 * (2.0 - officialPreDose))
    }
}
