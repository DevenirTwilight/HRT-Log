package net.plainnotes.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import net.plainnotes.app.domain.DoseStatus
import net.plainnotes.app.domain.SlotState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** UI-06 B: the one status table, checked for every state the app can draw. */
class StatusStylesTest {
    private data class Row(val tone: StatusTone, val symbol: StatusSymbol, val text: StatusText, val outlined: Boolean = false, val dashed: Boolean = false, val late: Boolean = false)
    private val table = mapOf(
        DoseState.ON_TIME to Row(StatusTone.SUCCESS, StatusSymbol.CHECK, StatusText.TAKEN),
        DoseState.LATE to Row(StatusTone.SUCCESS, StatusSymbol.CHECK, StatusText.TAKEN, late = true),
        DoseState.PARTIAL to Row(StatusTone.MISSED, StatusSymbol.HALF, StatusText.PARTIAL),
        DoseState.MISSED to Row(StatusTone.MISSED, StatusSymbol.CROSS, StatusText.MISSED, outlined = true),
        DoseState.UNCONFIRMED to Row(StatusTone.UNCONFIRMED, StatusSymbol.QUESTION, StatusText.UNCONFIRMED, dashed = true),
        DoseState.SKIPPED to Row(StatusTone.SKIPPED, StatusSymbol.DASH, StatusText.SKIPPED),
        DoseState.OVERDUE to Row(StatusTone.ATTENTION, StatusSymbol.TEXT, StatusText.PAST_TIME),
        DoseState.PENDING to Row(StatusTone.NONE, StatusSymbol.NONE, StatusText.PENDING),
        DoseState.SOON to Row(StatusTone.NONE, StatusSymbol.NONE, StatusText.SOON),
    )

    @Test fun everyDoseStateMatchesTheTable() {
        assertEquals(DoseState.entries.toSet(), table.keys)
        for (s in DoseState.entries) {
            val st = StatusStyles.of(s); val r = table.getValue(s)
            assertEquals(s, st.state)
            assertEquals("$s", r, Row(st.tone, st.symbol, st.text, st.outlined, st.dashed, st.lateNote))
        }
    }

    @Test fun everySlotStateAndDoseStatusGoesThroughTheTable() {
        for (s in SlotState.entries) assertEquals(StatusStyles.of(DoseState.valueOf(s.name)), StatusStyles.of(s))
        for (s in DoseStatus.entries) assertEquals(StatusStyles.of(DoseState.valueOf(s.name)), StatusStyles.of(s))
    }

    @Test fun storedRecords() {
        assertEquals(DoseState.UNCONFIRMED, StatusStyles.ofRecord("MISSED", unconfirmed = true).state)
        assertEquals(DoseState.MISSED, StatusStyles.ofRecord("MISSED", unconfirmed = false).state)
        for (s in listOf("ON_TIME", "LATE", "SKIPPED")) for (u in listOf(true, false)) assertEquals(DoseState.valueOf(s), StatusStyles.ofRecord(s, u).state)
    }

    @Test fun noStateIsRedAndNoColouredStateRestsOnColourAlone() {
        for (s in DoseState.entries) {
            val st = StatusStyles.of(s)
            assertNotEquals("$s must not use danger", StatusTone.DANGER, st.tone)
            if (st.tone != StatusTone.NONE) assertNotEquals("$s needs a symbol or words", StatusSymbol.NONE, st.symbol)
        }
        // Grayscale: every coloured state differs from the others in symbol, outline or words.
        val cues = DoseState.entries.filter { StatusStyles.of(it).tone != StatusTone.NONE }.map { StatusStyles.of(it).let { st -> listOf(st.symbol, st.outlined, st.dashed, st.lateNote) } }
        assertEquals(cues.size, cues.distinct().size)
    }

    @Test fun toneLookup() {
        val sem = semanticColors(notesColorScheme(false), false)
        assertNull(sem.of(StatusTone.NONE))
        assertEquals(sem.attention, sem.of(StatusTone.ATTENTION)); assertEquals(sem.missed, sem.of(StatusTone.MISSED))
        assertEquals(sem.danger, sem.of(StatusTone.DANGER)); assertEquals(sem.unconfirmed, sem.of(StatusTone.UNCONFIRMED))
    }

    @Test fun dynamicColourMovesOnlyActionSuccessAndDanger() {
        for (dark in listOf(false, true)) for (seed in SEEDS) {
            val scheme = seed(dark); val fixed = semanticColors(scheme, dark); val dyn = semanticColors(scheme, dark, dynamic = true)
            assertEquals(SemanticTone(scheme.primary, scheme.onPrimary, scheme.primary), dyn.action)
            assertEquals(SemanticTone(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary), dyn.success)
            assertEquals(SemanticTone(scheme.errorContainer, scheme.onErrorContainer, scheme.error), dyn.danger)
            assertEquals(fixed.attention, dyn.attention); assertEquals(fixed.missed, dyn.missed)
            assertEquals(fixed.unconfirmed, dyn.unconfirmed); assertEquals(fixed.skipped, dyn.skipped)
        }
    }

    @Test fun taskBookValues() {
        val l = semanticColors(notesColorScheme(false), false); val d = semanticColors(notesColorScheme(true), true)
        assertEquals(SemanticTone(Color(0xFFFFDBCE), Color(0xFF5C2410), Color(0xFF8E4D35)), l.attention)
        assertEquals(SemanticTone(Color(0xFF71361F), Color(0xFFFFDBCE), Color(0xFFFFB599)), d.attention)
        assertEquals(Color(0xFFEDE4DF), l.missed.container); assertEquals(Color(0xFF3B322E), d.missed.container)
        assertEquals(Color.Transparent, l.unconfirmed.container); assertEquals(Color.Transparent, d.unconfirmed.container)
    }

    companion object {
        /** Synthetic dynamic schemes (Material tonal values for three wallpaper seeds), plus the app's own palette. */
        val SEEDS: List<(Boolean) -> ColorScheme> = listOf(
            { d -> notesColorScheme(d) },
            { d -> if (!d) lightColorScheme(primary = Color(0xFF415F91), onPrimary = Color.White, primaryContainer = Color(0xFFD6E3FF), onPrimaryContainer = Color(0xFF001B3E), surface = Color(0xFFF9F9FF), onSurface = Color(0xFF191C20), surfaceContainerLowest = Color.White)
                    else darkColorScheme(primary = Color(0xFFAAC7FF), onPrimary = Color(0xFF0A305F), primaryContainer = Color(0xFF284777), onPrimaryContainer = Color(0xFFD6E3FF), surface = Color(0xFF111318), onSurface = Color(0xFFE2E2E9), surfaceContainerLowest = Color(0xFF0C0E13)) },
            { d -> if (!d) lightColorScheme(primary = Color(0xFF65558F), onPrimary = Color.White, primaryContainer = Color(0xFFE9DDFF), onPrimaryContainer = Color(0xFF21005D), surface = Color(0xFFFEF7FF), onSurface = Color(0xFF1D1B20), surfaceContainerLowest = Color.White)
                    else darkColorScheme(primary = Color(0xFFCFBCFF), onPrimary = Color(0xFF381E72), primaryContainer = Color(0xFF4F378A), onPrimaryContainer = Color(0xFFE9DDFF), surface = Color(0xFF141218), onSurface = Color(0xFFE6E0E9), surfaceContainerLowest = Color(0xFF0F0D13)) },
            { d -> if (!d) lightColorScheme(primary = Color(0xFF8F4C38), onPrimary = Color.White, primaryContainer = Color(0xFFFFDBD1), onPrimaryContainer = Color(0xFF3A0B01), surface = Color(0xFFFFF8F6), onSurface = Color(0xFF231917), surfaceContainerLowest = Color.White)
                    else darkColorScheme(primary = Color(0xFFFFB5A0), onPrimary = Color(0xFF561F0F), primaryContainer = Color(0xFF723523), onPrimaryContainer = Color(0xFFFFDBD1), surface = Color(0xFF1A110F), onSurface = Color(0xFFF1DFDA), surfaceContainerLowest = Color(0xFF140C0A)) },
        )
        val SEED_NAMES = listOf("app", "blue", "purple", "orange")
    }
}

/**
 * UI-06 accessibility: WCAG contrast of every token pair the screens draw, for 3 contrast levels × light/dark, plus dynamic
 * colour for three wallpaper seeds. The token is in the test name. Text 4.5:1; borders and symbols 3:1.
 */
@RunWith(Parameterized::class)
// Colours travel as ARGB ints: Color is a value class and JUnit cannot pass it to the constructor.
class SemanticContrastTest(private val name: String, private val fg: Int, private val bg: Int, private val min: Double) {
    companion object {
        fun ratio(a: Color, b: Color): Double { val l1 = a.luminance() + 0.05; val l2 = b.luminance() + 0.05; return maxOf(l1, l2) / minOf(l1, l2) }
        private fun pairs(sem: SemanticColors, c: ColorScheme): List<Triple<String, Pair<Color, Color>, Double>> = listOf(
            Triple("action.text", sem.action.onContainer to sem.action.container, 4.5),
            Triple("attention.text", sem.attention.onContainer to sem.attention.container, 4.5),
            Triple("attention.accent/container", sem.attention.accent to sem.attention.container, 3.0),
            Triple("attention.accent/surface", sem.attention.accent to c.surface, 3.0),
            Triple("success.text", sem.success.onContainer to sem.success.container, 4.5),
            Triple("success.accent/surface", sem.success.accent to c.surface, 3.0),
            Triple("missed.text", sem.missed.onContainer to sem.missed.container, 4.5),
            Triple("missed.accent/container", sem.missed.accent to sem.missed.container, 3.0),
            Triple("missed.accent/surface", sem.missed.accent to c.surface, 3.0),
            Triple("missed.accent/surfaceContainerHigh", sem.missed.accent to c.surfaceContainerHigh, 3.0),
            Triple("unconfirmed.text/surface", sem.unconfirmed.onContainer to c.surface, 4.5),
            Triple("unconfirmed.accent/surface", sem.unconfirmed.accent to c.surface, 3.0),
            Triple("unconfirmed.accent/surfaceContainerLowest", sem.unconfirmed.accent to c.surfaceContainerLowest, 3.0),
            Triple("skipped.text", sem.skipped.onContainer to sem.skipped.container, 4.5),
            Triple("danger.text", sem.danger.onContainer to sem.danger.container, 4.5),
        )
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun params(): List<Array<Any>> {
            val out = mutableListOf<Array<Any>>()
            for (dark in listOf(false, true)) {
                val mode = if (dark) "dark" else "light"
                for (k in Contrast.entries) { val c = notesColorScheme(dark, k)
                    pairs(semanticColors(c, dark, k), c).forEach { (n, p, m) -> out += arrayOf<Any>("$n $mode/$k", p.first.toArgb(), p.second.toArgb(), m) } }
                StatusStylesTest.SEEDS.forEachIndexed { i, seed -> if (i > 0) { val c = seed(dark)
                    pairs(semanticColors(c, dark, dynamic = true), c).forEach { (n, p, m) -> out += arrayOf<Any>("$n $mode/dynamic-${StatusStylesTest.SEED_NAMES[i]}", p.first.toArgb(), p.second.toArgb(), m) } } }
            }
            return out
        }
    }
    @Test fun meetsMinimum() { val r = ratio(Color(fg), Color(bg)); println("CONTRAST\t$name\t${"%.2f".format(r)}\t$min"); assertTrue("$name ${"%.2f".format(r)} < $min", r >= min) }
}
