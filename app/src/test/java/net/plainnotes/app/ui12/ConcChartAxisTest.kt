package net.plainnotes.app.ui12

import net.plainnotes.app.ui.axisLabelsToDraw
import net.plainnotes.app.ui.clampAxisLabelCenter
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

/** The concentration chart's time axis at 2x text on a narrow screen: dates keep their place, half-day marks give way, nothing is drawn over anything. */
class ConcChartAxisTest {
    private fun keep(centers: List<Float>, widths: List<Float>, ranks: List<Int>, gap: Float = 6f) =
        axisLabelsToDraw(centers.toFloatArray(), widths.toFloatArray(), ranks.toIntArray(), gap).toList()

    @Test fun roomyLabelsAreAllKept() {
        assertEquals(List(7) { true }, keep(List(7) { it * 100f }, List(7) { 40f }, List(7) { it % 2 }))
    }

    @Test fun halfDayMarksGiveWayToDatesWhenTheyWouldTouch() {
        // Date, noon, date, noon ... 37 px apart with 45 px wide labels: neighbours collide, dates alone (74 px apart) do not.
        val centers = List(7) { 20f + it * 37f }
        val ranks = List(7) { if (it % 2 == 0) 0 else 1 }
        assertEquals(listOf(true, false, true, false, true, false, true), keep(centers, List(7) { 45f }, ranks))
    }

    @Test fun aNoonMarkAtTheEdgeNeverBeatsTheDateNextToIt() {
        assertEquals(listOf(false, true, true), keep(listOf(10f, 47f, 130f), listOf(45f, 45f, 45f), listOf(1, 0, 1)))
    }

    @Test fun datesThemselvesAreThinnedWhenEvenTheyCollide() {
        val kept = keep(List(6) { 20f + it * 30f }, List(6) { 45f }, List(6) { 0 })
        assertEquals(listOf(true, false, true, false, true, false), kept)
    }

    @Test fun nanPositionsAreSkippedNotDrawn() {
        assertEquals(listOf(true, false, true), keep(listOf(0f, Float.NaN, 200f), listOf(40f, 40f, 40f), listOf(0, 0, 0)))
    }

    @Test fun aLabelNearTheEdgeIsMovedInsideTheCanvasNeverCutOff() {
        assertEquals(300f, clampAxisLabelCenter(300f, 40f, 1000f), 0f)      // roomy: untouched
        assertEquals(980f, clampAxisLabelCenter(1000f, 40f, 1000f), 0f)     // right edge: 1000 - 20
        assertEquals(20f, clampAxisLabelCenter(0f, 40f, 1000f), 0f)         // left edge
        assertEquals(15f, clampAxisLabelCenter(10f, 40f, 30f), 0f)          // wider than the canvas: centred
        assertTrue(clampAxisLabelCenter(Float.NaN, 40f, 1000f).isNaN())      // never invents a position
    }

    @Test fun sizesMustMatch() {
        assertThrows(IllegalArgumentException::class.java) { axisLabelsToDraw(FloatArray(2), FloatArray(3), IntArray(2), 6f) }
    }

    @Test fun noKeptLabelsEverOverlapAndTheTopRankedFirstOneIsAlwaysKept() {
        val random = Random(20261029)
        repeat(500) {
            val n = 1 + random.nextInt(14)
            // Axis ticks are evenly spaced and increasing; the spacing varies from case to case.
            val step = 15f + random.nextFloat() * 60f
            val centers = FloatArray(n) { 10f + it * step }
            val widths = FloatArray(n) { 20f + random.nextFloat() * 50f }
            val ranks = IntArray(n) { random.nextInt(2) }
            val kept = axisLabelsToDraw(centers, widths, ranks, 6f)
            val chosen = (0 until n).filter { kept[it] }
            chosen.forEach { a -> chosen.filter { it > a }.forEach { b ->
                val gapBetween = (centers[b] - widths[b] / 2f) - (centers[a] + widths[a] / 2f)
                assertTrue("labels $a and $b overlap (gap $gapBetween)", gapBetween >= 6f - 1e-3f)
            } }
            val best = (0 until n).minWith(compareBy({ ranks[it] }, { it }))
            assertTrue("the best label must always be drawn", kept[best])
        }
    }
}
