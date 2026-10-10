package net.plainnotes.app.pk.experimental

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResearchSublingualV01Test {
    private val model = ResearchSublingualV01.candidates.last()

    @Test fun frozenNearOptimalCandidateSetHasExpectedProvenance() {
        val all = ResearchSublingualV01.candidates
        assertEquals(15, all.size)
        assertEquals(15, all.map { it.id }.toSet().size)
        assertTrue(all.all { it.exposedDataPseudoLoss <= 0.03348696307362355 + 0.1000000001 })
        assertFalse(ResearchSublingualV01.productionEnabled)
        assertFalse(ResearchSublingualV01.humanCoverageValidated)
        assertFalse(ResearchSublingualV01.individualPredictionValidated)
    }

    @Test fun allCandidatesAreFiniteNormalizedAndNonnegative() {
        for (c in ResearchSublingualV01.candidates) {
            assertEquals(0.0, ResearchSublingualV01.relativeIncrement(0.0, c))
            assertEquals(1.0, ResearchSublingualV01.relativeIncrement(1.0, c), 1e-12)
            for (t in listOf(1e-3, 0.1, 0.33, 0.5, 1.5, 4.0, 12.0, 24.0, 120.0)) {
                val value = ResearchSublingualV01.relativeIncrement(t, c)
                assertTrue(value.isFinite() && value >= 0.0)
            }
        }
    }

    @Test fun p2xReferencePointsAgreeWithPythonResearchKernel() {
        // From p2x-39 Python normalized_curve (scanned candidate, NOT clinical truth).
        val references = mapOf(
            0.25 to 0.09896392290807818,
            0.5 to 0.523468732217862,
            1.0 to 1.0,
            2.0 to 0.417126368058965,
            4.0 to 0.1445976827100983,
            6.0 to 0.07922205475571546,
            12.0 to 0.016324428940983113,
            24.0 to 0.0007067671260269512,
        )
        references.forEach { (t, expected) ->
            assertEquals(expected, ResearchSublingualV01.relativeIncrement(t, model), 1e-9, "time=$t")
        }
    }

    @Test fun noBackdatingOrInventedDoseSchedule() {
        val events = listOf(ExperimentalDose(0.0, 1.0), ExperimentalDose(2.0, 0.5))
        assertEquals(0.0, ResearchSublingualV01.relativeHistory(-1.0, events, model))
        assertEquals(1.0, ResearchSublingualV01.relativeHistory(1.0, events, model), 1e-12)
        val expected = ResearchSublingualV01.relativeIncrement(3.0, model) +
            0.5 * ResearchSublingualV01.relativeIncrement(1.0, model)
        assertEquals(expected, ResearchSublingualV01.relativeHistory(3.0, events, model), 1e-12)
    }

    @Test fun multiModelSpreadIsDescriptiveNotClinicalCoverage() {
        val values = ResearchSublingualV01.spread(24.0, listOf(ExperimentalDose(0.0, 1.0)))
        assertTrue(values.minimum <= values.median)
        assertTrue(values.median <= values.maximum)
        assertTrue(values.maximum > values.minimum)
        assertTrue(values.minimum > 0.0)
    }

    @Test fun explicitStudyAnchorAndInputGuards() {
        val anchor = ExperimentalStudyAnchor("Price1997 Figure1 study-only", "pg/mL", 24.0, 434.8357220077087)
        assertEquals(24.0 + 434.8357220077087,
            ResearchSublingualV01.studyScenario(1.0, listOf(ExperimentalDose(0.0, 1.0)), model, anchor), 1e-9)
        assertThrows(IllegalArgumentException::class.java) { ResearchSublingualV01.relativeIncrement(Double.NaN, model) }
        assertThrows(IllegalArgumentException::class.java) { ResearchSublingualV01.relativeIncrement(-1.0, model) }
        assertThrows(IllegalArgumentException::class.java) { ExperimentalDose(Double.POSITIVE_INFINITY, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { ExperimentalDose(0.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { ExperimentalStudyAnchor("", "pg/mL", 0.0, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { ExperimentalStudyAnchor("study", "pg/mL", -1.0, 1.0) }
    }
}
