package net.plainnotes.app.pk.experimental

import net.plainnotes.app.pk.DoseEvent
import net.plainnotes.app.pk.DoseExtras
import net.plainnotes.app.pk.Ester
import net.plainnotes.app.pk.Route
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ResearchShapeComparisonV01Test {
    private fun sl(id: String, at: Double, mg: Double = 1.0) =
        DoseEvent(id, Route.SUBLINGUAL, at, mg, Ester.E2, 70.0, DoseExtras(sublingualTier = 2.0))

    @Test fun requiresActualSublingualEstradiolHistoryOnly() {
        assertNull(ResearchShapeComparisonV01.compare(emptyList(), 48.0, 12.0))
        val onlyOther = listOf(
            DoseEvent("oral", Route.ORAL, 1.0, 1.0, Ester.E2, 70.0),
            DoseEvent("ev", Route.SUBLINGUAL, 2.0, 1.0, Ester.EV, 70.0)
        )
        assertNull(ResearchShapeComparisonV01.compare(onlyOther, 48.0, 12.0))
    }

    @Test fun bothModelsUseActualTimestampsAndOwnExplicitReference() {
        // Chart covers 0..48 h in half-hour steps, with one event at t=2.
        val comparison = ResearchShapeComparisonV01.compare(listOf(sl("one", 2.0)), 48.0, 12.0)!!
        assertEquals(97, comparison.timeHours.size)
        assertEquals(2.0, comparison.timeHours[4], 1e-12)
        assertEquals("p2x-30", comparison.candidateId)
        assertEquals(2, comparison.matchingCandidateCount)
        assertEquals(1, comparison.consideredRecordedDoses)
        assertEquals(0, comparison.omittedOlderDoses)
        assertEquals(0.0, comparison.legacyRelative[2], 1e-12)
        assertEquals(0.0, comparison.experimentalRelative[2], 1e-12)
        // Both curves equal 1 at 1 hour after the standardized 1mg reference event.
        assertEquals(1.0, comparison.legacyRelative[6], 1e-9)
        assertEquals(1.0, comparison.experimentalRelative[6], 1e-9)
        assertTrue(comparison.legacyRelative.all { it.isFinite() && it >= 0.0 })
        assertTrue(comparison.experimentalRelative.all { it.isFinite() && it >= 0.0 })
    }

    @Test fun ignoresFuturePlannedDoseAndNonSlRecords() {
        val base = ResearchShapeComparisonV01.compare(listOf(sl("a", 1.0)), 48.0, 24.0)!!
        val extra = ResearchShapeComparisonV01.compare(
            listOf(sl("a", 1.0), sl("future", 49.0),
                DoseEvent("oral", Route.ORAL, 10.0, 10.0, Ester.E2, 70.0)), 48.0, 24.0
        )!!
        assertArrayEquals(base.legacyRelative, extra.legacyRelative, 1e-10)
        assertArrayEquals(base.experimentalRelative, extra.experimentalRelative, 1e-10)
        assertEquals(base.consideredRecordedDoses, extra.consideredRecordedDoses)
    }

    @Test fun historicalTailTruncationIsExplicit() {
        val comparison = ResearchShapeComparisonV01.compare(
            listOf(sl("old", -900.0), sl("recent", 30.0)), 48.0, 0.0
        )!!
        assertEquals(1, comparison.consideredRecordedDoses)
        assertEquals(1, comparison.omittedOlderDoses)
        assertEquals(1, comparison.matchingCandidateCount)
        assertEquals("p2x-22", comparison.candidateId)
    }

    @Test fun baselineSelectionDoesNotInferMostProbableModel() {
        for ((b, count) in listOf(0.0 to 1, 6.0 to 1, 12.0 to 2, 18.0 to 5, 24.0 to 6)) {
            val v = ResearchShapeComparisonV01.compare(listOf(sl("single", 20.0)), 48.0, b)!!
            assertEquals(count, v.matchingCandidateCount)
            assertEquals(b, v.assumedPriceBaselinePgMl)
        }
    }

    @Test fun guardsUnsupportedBaselineAndLargeUntrustedInput() {
        assertThrows(IllegalArgumentException::class.java) {
            ResearchShapeComparisonV01.compare(listOf(sl("a", 1.0)), 48.0, 5.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResearchShapeComparisonV01.compare(listOf(sl("a", 1.0)), Double.NaN, 12.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResearchShapeComparisonV01.compare(List(10001) { sl(it.toString(), 0.0) }, 48.0, 12.0)
        }
    }
}
