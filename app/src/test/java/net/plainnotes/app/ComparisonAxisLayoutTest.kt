package net.plainnotes.app

import net.plainnotes.app.experimental.axisLabelLeft
import org.junit.Assert.*
import org.junit.Test

class ComparisonAxisLayoutTest {
    @Test fun edgeLabelsFitInsideThePlot() {
        assertEquals(48f, axisLabelLeft(48f, 80f, 48f, 280f, Float.NEGATIVE_INFINITY, 8f)!!, 0f)
        assertEquals(200f, axisLabelLeft(280f, 80f, 48f, 280f, Float.NEGATIVE_INFINITY, 8f)!!, 0f)
    }
    @Test fun largeFontLabelsNeverOverlapOnANarrowAxis() {
        var right = Float.NEGATIVE_INFINITY
        val visible = (0..6).mapNotNull { i ->
            axisLabelLeft(48f + i * 232f / 6, 80f, 48f, 280f, right, 8f)?.also { left ->
                assertTrue(left >= 48f && left + 80 <= 280f)
                assertTrue(left >= right + 8); right = left + 80
            }
        }
        assertTrue(visible.size >= 2)
        assertTrue(visible.size < 7)
    }
    @Test fun impossibleOrNonfiniteLabelsAreNotDrawn() {
        assertNull(axisLabelLeft(60f, 300f, 48f, 280f, Float.NEGATIVE_INFINITY, 8f))
        assertNull(axisLabelLeft(Float.NaN, 20f, 48f, 280f, Float.NEGATIVE_INFINITY, 8f))
        assertNull(axisLabelLeft(60f, Float.POSITIVE_INFINITY, 48f, 280f, Float.NEGATIVE_INFINITY, 8f))
    }
}
