package net.plainnotes.app.pk.experimental

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class OfficialEstradiolCypionateScenarioTest {
    private val model = OfficialEstradiolCypionateScenario

    @Test fun cohortPeakOccursAfterThreePointNineDays() {
        val peak = model.peakH
        assertEquals(3.9 * 24.0, peak, 1e-10)
        assertEquals(337.0, model.referenceShape(peak), 0.001)
        assertEquals(1.0, model.normalized(peak), 0.00001)
        val grid = DoubleArray(24 * 18 + 1) { it.toDouble() }
        val index = grid.indices.maxByOrNull { model.referenceShape(grid[it]) }!!
        assertEquals(peak, grid[index], 1.0)
        assertEquals(0.0, model.referenceShape(0.0), 0.0)
    }

    @Test fun observedMeanOverElevenPointEightDaysIsAnExplicitFitTarget() {
        // 0.1 h numerical trapezoid to verify reported window-mean 159 pg/mL.
        val segments = (model.studyWindowH / 0.1).toInt()
        val h = model.studyWindowH / segments
        val area = (0..segments).sumOf { i ->
            (if(i==0 || i==segments) 0.5 else 1.0) * model.referenceShape(i*h)
        } * h
        assertTrue(abs(area / model.studyWindowH - 159.0) < 0.3)
        assertTrue(model.referenceShape(14.0 * 24.0) < model.referencePeakPgMl)
    }

    @Test fun unknownDoseResponseIsNotQuietlyExtrapolated() {
        assertThrows(IllegalArgumentException::class.java) { model.referenceDoseOnly(48.0, 2.0) }
        assertThrows(IllegalArgumentException::class.java) { model.referenceDoseOnly(48.0, Double.NaN) }
        assertEquals(model.referenceShape(48.0), model.referenceDoseOnly(48.0, 5.0), 0.0)
        assertThrows(IllegalArgumentException::class.java) { model.referenceShape(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { model.referenceShape(Double.POSITIVE_INFINITY) }
    }
}
