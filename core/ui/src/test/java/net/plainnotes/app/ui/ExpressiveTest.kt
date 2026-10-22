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

/** UI-07b (REQUIREMENTS §136): what each line icon draws and in which colour, from the UI-06 tokens only. */
class StatusIconSpecTest {
    private val light = semanticColors(notesColorScheme(false), false)
    private val dark = semanticColors(notesColorScheme(true), true)
    private val tones = mapOf(StatusIconKind.TAKEN to { s: SemanticColors -> s.success }, StatusIconKind.PARTIAL to { s: SemanticColors -> s.success },
        StatusIconKind.MISSED to { s: SemanticColors -> s.missed }, StatusIconKind.SKIPPED to { s: SemanticColors -> s.skipped },
        StatusIconKind.UNCONFIRMED to { s: SemanticColors -> s.unconfirmed })

    @Test fun eachKindDrawsItsOwnGlyph() {
        val glyphs = StatusIconKind.entries.associateWith { statusIconSpec(it, light).glyph }
        assertEquals(mapOf(StatusIconKind.TAKEN to StatusGlyph.CHECK, StatusIconKind.PARTIAL to StatusGlyph.HALF, StatusIconKind.MISSED to StatusGlyph.CROSS,
            StatusIconKind.SKIPPED to StatusGlyph.DASH, StatusIconKind.UNCONFIRMED to StatusGlyph.QUESTION, StatusIconKind.TODAY to StatusGlyph.RING_DOT), glyphs)
        assertEquals(StatusIconKind.entries.size, glyphs.values.distinct().size)
    }

    @Test fun colourFollowsTheBackground() {
        for (sem in listOf(light, dark)) for ((k, tone) in tones) {
            assertEquals("$k default", tone(sem).accent, statusIconSpec(k, sem).color)
            assertEquals("$k on its own cell", tone(sem).onContainer, statusIconSpec(k, sem, onOwnContainer = true).color)
            assertEquals("$k on primary", Color.White, statusIconSpec(k, sem, tint = Color.White).color)
        }
        // The task book's light values.
        assertEquals(Color(0xFF006A63), statusIconSpec(StatusIconKind.TAKEN, light).color)
        assertEquals(Color(0xFF7A6A63), statusIconSpec(StatusIconKind.MISSED, light).color)
        assertEquals(Color(0xFF6F7977), statusIconSpec(StatusIconKind.SKIPPED, light).color)
        assertEquals(Color(0xFF6F7977), statusIconSpec(StatusIconKind.UNCONFIRMED, light).color)
        // Today keeps the caller's ring colour.
        assertEquals(StatusIconSpec(StatusGlyph.RING_DOT, Color.Red), statusIconSpec(StatusIconKind.TODAY, light, ring = Color.Red))
    }

    @Test fun highContrastDrawsInOnSurfaceOrTheGivenTint() {
        for (d in listOf(false, true)) {
            val scheme = notesColorScheme(d, Contrast.HIGH); val sem = semanticColors(scheme, d, Contrast.HIGH)
            for (k in tones.keys) {
                assertEquals("$k", scheme.onSurface, statusIconSpec(k, sem).color)
                assertEquals("$k on its own cell", scheme.onSurface, statusIconSpec(k, sem, onOwnContainer = true).color)
                assertEquals("$k on primary", scheme.onPrimary, statusIconSpec(k, sem, tint = scheme.onPrimary).color)
            }
        }
    }

    @Test fun dynamicColourMovesOnlyTheSuccessLine() {
        for (dark in listOf(false, true)) for (seed in StatusStylesTest.SEEDS.drop(1)) {
            val scheme = seed(dark); val fixed = semanticColors(scheme, dark); val dyn = semanticColors(scheme, dark, dynamic = true)
            assertEquals(scheme.primary, statusIconSpec(StatusIconKind.TAKEN, dyn).color)
            assertEquals(scheme.onPrimaryContainer, statusIconSpec(StatusIconKind.TAKEN, dyn, onOwnContainer = true).color)
            for (k in listOf(StatusIconKind.MISSED, StatusIconKind.SKIPPED, StatusIconKind.UNCONFIRMED)) assertEquals("$k", statusIconSpec(k, fixed), statusIconSpec(k, dyn))
        }
    }

    @Test fun everyDoseStateHasItsIconOrNone() {
        assertEquals(StatusIconKind.TAKEN, statusIconKind(DoseState.ON_TIME)); assertEquals(StatusIconKind.TAKEN, statusIconKind(DoseState.LATE))
        assertEquals(StatusIconKind.PARTIAL, statusIconKind(DoseState.PARTIAL)); assertEquals(StatusIconKind.MISSED, statusIconKind(DoseState.MISSED))
        assertEquals(StatusIconKind.SKIPPED, statusIconKind(DoseState.SKIPPED)); assertEquals(StatusIconKind.UNCONFIRMED, statusIconKind(DoseState.UNCONFIRMED))
        for (s in listOf(DoseState.OVERDUE, DoseState.PENDING, DoseState.SOON)) assertNull(statusIconKind(s))
    }

    @Test fun lineWidthsScaleAndStayLegible() {
        assertEquals(3.2f, statusGlyphWidth(40.dp).value, 0.01f)
        assertEquals(1.5f, statusGlyphWidth(18.dp).value, 0.01f)
        StatusIconSizes.all.forEach { assertTrue("$it", statusGlyphWidth(it) >= 1.5.dp) }
        StatusIconSizes.all.zipWithNext().forEach { (big, small) -> assertTrue(statusGlyphWidth(big) >= statusGlyphWidth(small)) }
        assertEquals(1.5f, statusOutlineWidth(40.dp).value, 0.01f); assertTrue(statusOutlineWidth(18.dp) >= 1.dp)
        assertEquals(0.6f, STATUS_GLYPH_SPAN)
        assertEquals(listOf(40.dp, 32.dp, 22.dp, 18.dp), StatusIconSizes.all)
    }
}

/**
 * UI-07b §3 acceptance: every call site, the line colour it passes against the background the icon actually sits on, ≥ 3:1,
 * for light / dark × standard, medium, high contrast, plus dynamic colour for three wallpaper seeds. The test name lists
 * call site, kind, theme; the printed line adds background, line colour and the measured ratio.
 */
@RunWith(Parameterized::class)
class StatusIconContrastTest(private val name: String, private val fg: Int, private val bg: Int) {
    /** A call site: the kinds it can show, the background under the icon, and what it passes ([onOwn], or a [tint]). */
    private class Site(val name: String, val kinds: List<StatusIconKind>, val onOwn: Boolean = false, val tint: ((ColorScheme) -> Color)? = null,
                       val ring: ((ColorScheme) -> Color)? = null, val bg: (ColorScheme, SemanticColors, StatusIconKind) -> Color)
    companion object {
        private val DAY = listOf(StatusIconKind.TAKEN, StatusIconKind.PARTIAL, StatusIconKind.MISSED, StatusIconKind.UNCONFIRMED)
        private val ALL = listOf(StatusIconKind.TAKEN, StatusIconKind.PARTIAL, StatusIconKind.MISSED, StatusIconKind.SKIPPED, StatusIconKind.UNCONFIRMED)
        private fun state(k: StatusIconKind) = when (k) { StatusIconKind.TAKEN -> DoseState.ON_TIME; StatusIconKind.PARTIAL -> DoseState.PARTIAL
            StatusIconKind.MISSED -> DoseState.MISSED; StatusIconKind.SKIPPED -> DoseState.SKIPPED; else -> DoseState.UNCONFIRMED }
        /** A tinted day cell or chip: the state's own container (UI-06 dayTone / StatusChip) over the page under it. */
        private fun own(page: (ColorScheme) -> Color) = { c: ColorScheme, s: SemanticColors, k: StatusIconKind -> s.of(StatusStyles.of(state(k)).tone)!!.container.compositeOver(page(c)) }
        private val SITES = listOf(
            Site("today-card.logged", listOf(StatusIconKind.TAKEN), tint = { it.onPrimary }) { c, _, _ -> c.primary },
            Site("today-log", ALL) { c, _, _ -> c.surfaceContainerLowest },
            Site("confirm-card", listOf(StatusIconKind.TAKEN, StatusIconKind.MISSED, StatusIconKind.UNCONFIRMED)) { c, _, _ -> c.surfaceContainerLowest },
            Site("recent-7.day", DAY) { c, _, _ -> c.surfaceContainerHighest },
            Site("recent-7.today", DAY, tint = { it.onPrimary }) { c, _, _ -> c.primary },
            Site("recent-7.today-ring", listOf(StatusIconKind.TODAY), ring = { it.onPrimary }) { c, _, _ -> c.primary },
            Site("month-day", DAY, onOwn = true, bg = own { it.surface }),
            Site("week-day", DAY, onOwn = true, bg = own { it.surface }),
            Site("legend", DAY) { c, _, _ -> c.surface },
            Site("history.taken", listOf(StatusIconKind.TAKEN)) { c, _, _ -> c.surfaceContainerLow },
            Site("history.chip", listOf(StatusIconKind.TAKEN, StatusIconKind.MISSED, StatusIconKind.SKIPPED, StatusIconKind.UNCONFIRMED), onOwn = true, bg = own { it.surfaceContainerLow }),
            Site("day-list.chip", ALL, onOwn = true, bg = own { it.surface }),
            Site("timeline.missed", listOf(StatusIconKind.MISSED)) { c, _, _ -> c.surfaceContainerLowest },
            Site("timeline.missed.upcoming", listOf(StatusIconKind.MISSED)) { c, _, _ -> c.surface },
        )
        private fun rows(c: ColorScheme, sem: SemanticColors, theme: String, out: MutableList<Array<Any>>) {
            for (site in SITES) for (k in site.kinds) {
                val line = statusIconSpec(k, sem, ring = site.ring?.invoke(c) ?: Color.White, onOwnContainer = site.onOwn, tint = site.tint?.invoke(c)).color
                out += arrayOf<Any>("${site.name} $k $theme", line.toArgb(), site.bg(c, sem, k).toArgb())
            }
        }
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun params(): List<Array<Any>> {
            val out = mutableListOf<Array<Any>>()
            for (dark in listOf(false, true)) { val mode = if (dark) "dark" else "light"
                for (k in Contrast.entries) { val c = notesColorScheme(dark, k); rows(c, semanticColors(c, dark, k), "$mode/$k", out) }
                StatusStylesTest.SEEDS.forEachIndexed { i, seed -> if (i > 0) { val c = seed(dark)
                    rows(c, semanticColors(c, dark, dynamic = true), "$mode/dynamic-${StatusStylesTest.SEED_NAMES[i]}", out) } }
            }
            return out
        }
        private fun hex(i: Int) = "#%06X".format(i and 0xFFFFFF)
    }
    @Test fun atLeastThreeToOne() {
        val r = SemanticContrastTest.ratio(Color(fg), Color(bg))
        println("ICONCONTRAST\t$name\tbg ${hex(bg)}\tline ${hex(fg)}\t${"%.2f".format(r)}")
        assertTrue("$name: line ${hex(fg)} on ${hex(bg)} ${"%.2f".format(r)} < 3", r >= 3.0)
    }
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
