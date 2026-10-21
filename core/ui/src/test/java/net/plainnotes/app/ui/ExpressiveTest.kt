package net.plainnotes.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** UI-07 A: what each status icon draws, from the UI-06 tokens only. */
class StatusIconSpecTest {
    private val light = semanticColors(notesColorScheme(false), false)
    private val dark = semanticColors(notesColorScheme(true), true)

    @Test fun eachKindDrawsTheTaskBookShape() {
        for (sem in listOf(light, dark)) {
            statusIconSpec(StatusIconKind.TAKEN, sem).let { assertEquals(sem.success.accent, it.fill); assertNull(it.stroke); assertEquals(StatusGlyph.CHECK, it.glyph) }
            statusIconSpec(StatusIconKind.PARTIAL, sem).let { assertEquals(sem.missed.container, it.fill); assertEquals(sem.success.accent, it.half); assertEquals(StatusGlyph.NONE, it.glyph) }
            statusIconSpec(StatusIconKind.MISSED, sem).let { assertEquals(sem.missed.container, it.fill); assertEquals(sem.missed.accent, it.stroke); assertFalse(it.dashed)
                assertEquals(StatusGlyph.CROSS, it.glyph); assertEquals(sem.missed.onContainer, it.glyphColor) }
            statusIconSpec(StatusIconKind.SKIPPED, sem).let { assertEquals(sem.skipped.container, it.fill); assertEquals(StatusGlyph.DASH, it.glyph); assertEquals(sem.skipped.onContainer, it.glyphColor) }
            statusIconSpec(StatusIconKind.UNCONFIRMED, sem).let { assertEquals(Color.Transparent, it.fill); assertEquals(sem.unconfirmed.accent, it.stroke); assertTrue(it.dashed)
                assertEquals(StatusGlyph.QUESTION, it.glyph); assertEquals(sem.unconfirmed.onContainer, it.glyphColor) }
            statusIconSpec(StatusIconKind.TODAY, sem, ring = Color.White).let { assertEquals(Color.Transparent, it.fill); assertEquals(Color.White, it.stroke); assertEquals(StatusGlyph.DOT, it.glyph) }
        }
        // Light theme: the taken circle is the primary teal with a white check.
        assertEquals(Color(0xFF006A63), statusIconSpec(StatusIconKind.TAKEN, light).fill)
        assertEquals(Color.White, statusIconSpec(StatusIconKind.TAKEN, light).glyphColor)
    }

    @Test fun highContrastOutlinesAndGlyphsUseOnSurface() {
        for (d in listOf(false, true)) {
            val scheme = notesColorScheme(d, Contrast.HIGH); val sem = semanticColors(scheme, d, Contrast.HIGH)
            for (k in listOf(StatusIconKind.MISSED, StatusIconKind.SKIPPED, StatusIconKind.UNCONFIRMED)) {
                val s = statusIconSpec(k, sem); assertEquals("$k stroke", scheme.onSurface, s.stroke); assertEquals("$k glyph", scheme.onSurface, s.glyphColor)
            }
            assertEquals(scheme.onSurface, statusIconSpec(StatusIconKind.PARTIAL, sem).stroke)
        }
    }

    @Test fun everyDoseStateHasItsIconOrNone() {
        assertEquals(StatusIconKind.TAKEN, statusIconKind(DoseState.ON_TIME)); assertEquals(StatusIconKind.TAKEN, statusIconKind(DoseState.LATE))
        assertEquals(StatusIconKind.PARTIAL, statusIconKind(DoseState.PARTIAL)); assertEquals(StatusIconKind.MISSED, statusIconKind(DoseState.MISSED))
        assertEquals(StatusIconKind.SKIPPED, statusIconKind(DoseState.SKIPPED)); assertEquals(StatusIconKind.UNCONFIRMED, statusIconKind(DoseState.UNCONFIRMED))
        for (s in listOf(DoseState.OVERDUE, DoseState.PENDING, DoseState.SOON)) assertNull(statusIconKind(s))
    }

    @Test fun lineWidthsScaleAndStayLegible() {
        assertEquals(2.6f, statusGlyphWidth(40.dp).value, 0.01f)
        assertEquals(1.5f, statusOutlineWidth(40.dp).value, 0.01f)
        assertTrue(statusGlyphWidth(18.dp) >= 1.25.dp); assertTrue(statusOutlineWidth(18.dp) >= 1.dp); assertTrue(statusOutlineWidth(18.dp, thin = true) >= 0.75.dp)
        StatusIconSizes.all.zipWithNext().forEach { (big, small) -> assertTrue(statusGlyphWidth(big) >= statusGlyphWidth(small)) }
        assertEquals(listOf(40.dp, 32.dp, 22.dp, 18.dp), StatusIconSizes.all)
    }
}

/**
 * UI-07 A contrast: glyph against what it is drawn on ≥ 3:1, and the icon's edge (its fill, or its outline) against the page
 * ≥ 3:1, for 3 contrast levels × light / dark, plus dynamic colour seeds. The kind and the pair are in the test name.
 */
@RunWith(Parameterized::class)
class StatusIconContrastTest(private val name: String, private val fg: Int, private val bg: Int) {
    companion object {
        private fun pairs(sem: SemanticColors, c: ColorScheme): List<Triple<String, Color, Color>> {
            val out = mutableListOf<Triple<String, Color, Color>>()
            for (page in listOf("surface" to c.surface, "surfaceContainerLowest" to c.surfaceContainerLowest)) {
                for (k in StatusIconKind.entries.filter { it != StatusIconKind.TODAY }) {
                    val s = statusIconSpec(k, sem)
                    val base = if (s.fill.alpha == 0f) page.second else s.fill.compositeOver(page.second)
                    if (s.glyph != StatusGlyph.NONE) out += Triple("$k.glyph/${if (s.fill.alpha == 0f) page.first else "fill"}", s.glyphColor, base)
                    val edge = when { s.half != null -> s.half; s.fill.alpha > 0f && SemanticContrastTest.ratio(s.fill, page.second) >= 3.0 -> s.fill; else -> s.stroke!! }
                    out += Triple("$k.edge/${page.first}", edge, page.second)
                }
            }
            // Partly done: the two halves must differ, too.
            statusIconSpec(StatusIconKind.PARTIAL, sem).let { out += Triple("PARTIAL.half/fill", it.half!!, it.fill) }
            return out
        }
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun params(): List<Array<Any>> {
            val out = mutableListOf<Array<Any>>()
            for (dark in listOf(false, true)) { val mode = if (dark) "dark" else "light"
                for (k in Contrast.entries) { val c = notesColorScheme(dark, k)
                    pairs(semanticColors(c, dark, k), c).forEach { (n, a, b) -> out += arrayOf<Any>("$n $mode/$k", a.toArgbInt(), b.toArgbInt()) } }
                StatusStylesTest.SEEDS.forEachIndexed { i, seed -> if (i > 0) { val c = seed(dark)
                    pairs(semanticColors(c, dark, dynamic = true), c).forEach { (n, a, b) -> out += arrayOf<Any>("$n $mode/dynamic-${StatusStylesTest.SEED_NAMES[i]}", a.toArgbInt(), b.toArgbInt()) } } }
            }
            return out
        }
        private fun Color.toArgbInt() = this.toArgb()
    }
    @Test fun atLeastThreeToOne() { val r = SemanticContrastTest.ratio(Color(fg), Color(bg)); println("ICONCONTRAST\t$name\t${"%.2f".format(r)}"); assertTrue("$name ${"%.2f".format(r)} < 3", r >= 3.0) }
}

/** UI-07 B / C / E: shape steps, the number size cap and the grouped-list corners. */
class ExpressiveTokensTest {
    @Test fun shapeSteps() {
        assertEquals(listOf(8.dp, 12.dp, 16.dp, 20.dp, 28.dp, 32.dp), listOf(Radii.xs, Radii.s, Radii.m, Radii.l, Radii.xl, Radii.xxl))
        assertEquals(RoundedCornerShape(28.dp), Corners.xl); assertEquals(RoundedCornerShape(32.dp), Corners.xxl); assertEquals(RoundedCornerShape(percent = 50), Corners.full)
        assertEquals(16.dp, Motion.PRESS_RADIUS); assertEquals(0.96f, Motion.PRESS_SCALE); assertEquals(0.6f, Motion.DAMPING)
    }

    @Test fun numbersStopGrowingAt1point3() {
        // The size actually drawn is the passed size × system font scale.
        for ((fs, expected) in listOf(1f to 64f, 1.3f to 64f * 1.3f, 2f to 64f * 1.3f, 0.85f to 64f * 0.85f))
            assertEquals("font scale $fs", expected, cappedSp(64f, fs).value * fs, 0.01f)
        assertEquals(28f * 1.3f, cappedSp(28f, 2f).value * 2f, 0.01f)
    }

    @Test fun groupedCorners() {
        val o = 24.dp; val i = 6.dp
        fun rc(t: androidx.compose.ui.unit.Dp, b: androidx.compose.ui.unit.Dp) = RoundedCornerShape(topStart = t, topEnd = t, bottomStart = b, bottomEnd = b)
        assertEquals(rc(o, o), groupedShape(0, 1))
        assertEquals(listOf(rc(o, i), rc(i, o)), (0 until 2).map { groupedShape(it, 2) })
        assertEquals(listOf(rc(o, i), rc(i, i), rc(i, i), rc(i, o)), (0 until 4).map { groupedShape(it, 4) })
        assertEquals(3.dp, GroupedGap)
    }
}
