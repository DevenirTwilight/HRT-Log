package net.plainnotes.app.pk.experimental

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs

/** Literature-anchored scenarios: verified anchors only, units from the anchor, conflicts shown, never forced. */
class StudyAnchorsTest {
    private val frozen = File("../docs/pk-research/p2/p2x-frozen-40-candidates.csv").readLines()
    private fun frozen(c: ExperimentalCandidate, column: String): Double {
        val header = frozen.first().split(",")
        return frozen[1 + c.id.removePrefix("p2x-").toInt()].split(",")[header.indexOf(column)].toDouble()
    }

    @Test fun priceAmplitudeReproducesTheFrozenP2xFitForEveryCandidate() {
        for (c in ExperimentalSlModelView.candidates) {
            assertEquals(frozen(c, "Price_baseline"), c.assumedPriceBaselinePgMl, 0.0, c.id)
            assertEquals(frozen(c, "Price_model_auc"), PriceFigure1997.incrementalAuc0to24(c), 1e-9 * frozen(c, "Price_model_auc"), c.id)
            assertTrue(PriceFigure1997.amplitude(c) in 300.0..500.0, "${c.id} amplitude ${PriceFigure1997.amplitude(c)}")
        }
    }

    @Test fun scenarioIsBackgroundPlusAmplitudeTimesTheM2Sum() {
        val c = ExperimentalSlModelView.candidates[3]
        val anchor = StudyAnchors.anchor(PriceFigure1997.STUDY_ID, c)!!
        assertEquals("pg/mL", anchor.concentrationUnit)
        assertEquals(c.assumedPriceBaselinePgMl, anchor.background, 0.0)
        val one = listOf(ExperimentalDose(0.0, 1.0))
        assertEquals(anchor.background + anchor.oneMgIncrementAtOneHour, StudyAnchors.scenario(PriceFigure1997.STUDY_ID, 1.0, one, c)!!, 1e-9)
        val doses = listOf(ExperimentalDose(0.0, 1.0), ExperimentalDose(5.5, 0.5), ExperimentalDose(11.0, 2.0))
        for (t in listOf(0.5, 3.0, 12.0, 30.0))
            assertEquals(anchor.background + anchor.oneMgIncrementAtOneHour * ResearchSublingualV01.relativeHistory(t, doses, c),
                StudyAnchors.scenario(PriceFigure1997.STUDY_ID, t, doses, c)!!, 1e-9)
        // Before any dose only the study's ASSUMED background remains; it is not the user's baseline.
        assertEquals(anchor.background, StudyAnchors.scenario(PriceFigure1997.STUDY_ID, -1.0, doses, c)!!, 0.0)
    }

    @Test fun studiesWithoutAVerifiedAnchorProduceNoAbsoluteValues() {
        val c = ExperimentalSlModelView.candidates.first()
        val refused = StudyAnchors.catalog.filter { it.status == AnchorStatus.INSUFFICIENT_EVIDENCE }
        assertEquals(setOf("Doll2022_1mg_1h", "Rosano1997_PK25", "Komesaroff1998_n10"), refused.map { it.studyId }.toSet())
        for (e in refused) {
            assertNotNull(e.missing)
            assertNull(StudyAnchors.anchor(e.studyId, c)); assertNull(StudyAnchors.scenario(e.studyId, 1.0, listOf(ExperimentalDose(0.0, 1.0)), c))
        }
        assertNull(StudyAnchors.anchor("legacy-pk", c))
        assertEquals(listOf(PriceFigure1997.STUDY_ID), StudyAnchors.catalog.filter { it.status == AnchorStatus.AVAILABLE }.map { it.studyId })
    }

    @Test fun priceFigureAndTableAucConflictStaysVisible() {
        // Digitized figure, 0 h assumed 0 pg/mL: the same frozen points as P2-U (1557.5).
        assertEquals(1557.5, PriceFigure1997.figureAuc0to24AssumingZeroPredose(), 1e-9)
        assertEquals(2109.0, PriceFigure1997.TABLE1_AUC_0_24, 0.0)
        for (c in ExperimentalSlModelView.candidates) {
            val model = PriceFigure1997.incrementalAuc0to24(c)
            // The fit follows the figure; it is NOT forced to Table 1.
            assertTrue(abs(model - PriceFigure1997.TABLE1_AUC_0_24) / PriceFigure1997.TABLE1_AUC_0_24 > 0.2, "${c.id} $model")
        }
    }

    @Test fun scenarioRangeUsesEachCandidatesOwnAnchor() {
        val doses = listOf(ExperimentalDose(0.0, 1.0), ExperimentalDose(8.0, 0.5))
        val grid = DoubleArray(97) { it * 0.25 }
        val (lo, hi) = StudyAnchors.scenarioRange(PriceFigure1997.STUDY_ID, grid, doses)!!
        for (c in ExperimentalSlModelView.candidates) grid.indices.forEach {
            val v = StudyAnchors.scenario(PriceFigure1997.STUDY_ID, grid[it], doses, c)!!
            assertTrue(lo[it] - 1e-9 <= v && v <= hi[it] + 1e-9)
        }
        assertNull(StudyAnchors.scenarioRange("Doll2022_1mg_1h", grid, doses))
    }
}

