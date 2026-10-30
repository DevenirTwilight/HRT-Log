package net.plainnotes.app.pk.experimental

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class OfficialTestosteroneDepotShapesTest {
    @Test fun officialSourceAndEsterParentConversion() {
        assertEquals(180.0, TestosteroneDepot.ENANTHATE_IM.referenceParentMg, 0.0)
        assertEquals(250.0, TestosteroneDepot.ENANTHATE_IM.referenceEsterMg, 0.0)
        assertEquals(631.5, TestosteroneDepot.UNDECANOATE_IM.referenceParentMg, 0.0)
        assertEquals(1000.0, TestosteroneDepot.UNDECANOATE_IM.referenceEsterMg, 0.0)
        assertTrue(TestosteroneDepot.entries.all { it.officialSource.startsWith("https://base-donnees-publique.medicaments.gouv.fr/medicament/") })
    }

    @Test fun referenceDosesPeakAtRcpTimesButNeverClaimAbsoluteSerumNgMl() {
        for (depot in TestosteroneDepot.entries) {
            val peakH = depot.labelTmaxDays * 24
            val grid = DoubleArray(5000) { it.toDouble() }
            val curve = OfficialTestosteroneDepotShapes.relativeScenario(
                depot, listOf(TestosteroneDepotDose(0.0, depot.referenceEsterMg)), grid
            )
            val index = curve.indices.maxByOrNull { curve[it] }!!
            assertEquals(peakH, index.toDouble(), 1.0)
            assertEquals(1.0, curve[index], 0.001)
            assertTrue(curve.all { it.isFinite() && it >= 0 })
            assertEquals(0.0, curve.first(), 0.0)
        }
    }

    @Test fun halfLifeIsApparentReleaseAfterDepotAbsorption() {
        for (depot in TestosteroneDepot.entries) {
            val peakH = depot.labelTmaxDays * 24
            val a = OfficialTestosteroneDepotShapes.normalizedResponse(depot, peakH + 8000.0)
            val b = OfficialTestosteroneDepotShapes.normalizedResponse(depot, peakH + 8000.0 + depot.releaseHalfLifeDays * 24.0)
            assertTrue(abs(b / a - 0.5) < 0.002, "release half-life in days, NOT intrinsic elimination")
        }
    }

    @Test fun doseScalingAndTimedSumAreOnlyDimensionlessScenarios() {
        val depot = TestosteroneDepot.ENANTHATE_IM
        val one = listOf(TestosteroneDepotDose(24.0, 250.0))
        val x = OfficialTestosteroneDepotShapes.relativeScenario(depot, one, doubleArrayOf(0.0, 24.0, 78.0, 150.0))
        val two = OfficialTestosteroneDepotShapes.relativeScenario(depot, listOf(TestosteroneDepotDose(24.0, 500.0)), doubleArrayOf(0.0, 24.0, 78.0, 150.0))
        assertEquals(0.0, x[0], 0.0)
        assertEquals(0.0, x[1], 0.0)
        assertEquals(1.0, x[2], 0.001)
        assertEquals(2.0, two[2], 0.001)
        val sum = OfficialTestosteroneDepotShapes.relativeScenario(depot, one + TestosteroneDepotDose(78.0, 250.0), doubleArrayOf(150.0))[0]
        assertTrue(sum > x.last())
        assertThrows(IllegalArgumentException::class.java) {
            OfficialTestosteroneDepotShapes.relativeScenario(depot, listOf(TestosteroneDepotDose(0.0, Double.NaN)), doubleArrayOf(40.0))
        }
    }
}
