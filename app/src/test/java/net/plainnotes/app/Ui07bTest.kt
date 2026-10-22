package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * UI-07b (REQUIREMENTS §136): no filled disc and no outer ring. Method: a drawing record. [drawStatusIcon] runs on a
 * Canvas proxy that records every call (name, arguments, paint style); the test then reads what was drawn, for every kind
 * at every size.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class Ui07bDrawingRecordTest {
    private data class Call(val name: String, val args: List<Any?>)
    private fun record(kind: StatusIconKind, size: androidx.compose.ui.unit.Dp, density: Float): List<Call> {
        val calls = mutableListOf<Call>()
        val canvas = java.lang.reflect.Proxy.newProxyInstance(Canvas::class.java.classLoader, arrayOf(Canvas::class.java)) { _, m, args ->
            calls += Call(m.name.substringBefore('-'), args?.toList().orEmpty())
            when (m.returnType) { java.lang.Boolean.TYPE -> false; java.lang.Integer.TYPE -> 0; java.lang.Float.TYPE -> 0f; else -> null }
        } as Canvas
        val px = size.value * density
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, canvas, Size(px, px)) {
            drawStatusIcon(statusIconSpec(kind, semanticColors(notesColorScheme(false), false)), size)
        }
        return calls.filter { it.name.startsWith("draw") }
    }
    private fun Call.paint() = args.last() as Paint
    /** Offsets travel packed in a Long (value class); x is the high 32 bits. */
    private fun offset(v: Any?) = (v as Long).let { Offset(java.lang.Float.intBitsToFloat((it shr 32).toInt()), java.lang.Float.intBitsToFloat(it.toInt())) }

    @Test fun onlyLinesAndTheirSmallPartsAreDrawn() {
        for (density in listOf(1f, 2f, 3f)) for (size in StatusIconSizes.all) for (kind in StatusIconKind.entries) {
            val d = size.value * density; val calls = record(kind, size, density); val at = "$kind ${size} ×$density"
            assertTrue("$at draws something", calls.isNotEmpty())
            assertTrue("$at: only circles, arcs, lines and paths: ${calls.map { it.name }}", calls.all { it.name in setOf("drawCircle", "drawArc", "drawLine", "drawPath") })
            for (c in calls) {
                val p = c.paint()
                when (c.name) {
                    "drawCircle" -> {
                        val r = c.args[1] as Float; val centre = offset(c.args[0])
                        if (kind == StatusIconKind.TODAY) continue
                        if (p.style == PaintingStyle.Fill) assertTrue("$at: a filled circle of r=$r is only the question dot", r <= 0.1f * d && kind == StatusIconKind.UNCONFIRMED)
                        else assertTrue("$at: a ring of r=$r stays inside 0.6 d (no outer ring)", r + p.strokeWidth / 2 <= 0.3f * d + 0.01f && kind == StatusIconKind.PARTIAL)
                        assertTrue("$at: centred", kotlin.math.abs(centre.x - d / 2) <= 0.5f * d)
                    }
                    "drawArc" -> {
                        assertEquals("$at: only the partial pie", StatusIconKind.PARTIAL, kind)
                        val l = c.args[0] as Float; val r = c.args[2] as Float
                        assertTrue("$at: pie inside the ring", (r - l) / 2 <= 0.3f * d)
                        assertEquals(PaintingStyle.Fill, p.style); assertEquals(180f, c.args[5] as Float, 0.01f)
                    }
                    else -> {
                        assertEquals("$at: lines are strokes", PaintingStyle.Stroke, p.style)
                        assertEquals("$at: round caps", androidx.compose.ui.graphics.StrokeCap.Round, p.strokeCap)
                        assertEquals("$at: 8 % width, ≥ 1.5 dp", statusGlyphWidth(size).value * density, p.strokeWidth, 0.01f)
                    }
                }
            }
            if (kind == StatusIconKind.TODAY) {
                // Today keeps UI-07's thin solid ring at the full diameter, plus the centre dot.
                val ring = calls.single { it.name == "drawCircle" && it.paint().style == PaintingStyle.Stroke }
                assertEquals(d / 2 - ring.paint().strokeWidth / 2, ring.args[1] as Float, 0.01f)
                assertEquals(1, calls.count { it.name == "drawCircle" && it.paint().style == PaintingStyle.Fill })
            }
        }
    }

    @Test fun eachGlyphIsDrawnFromItsOwnStrokes() {
        fun names(k: StatusIconKind) = record(k, StatusIconSizes.XL, 1f).map { it.name }
        assertEquals(listOf("drawPath"), names(StatusIconKind.TAKEN))
        assertEquals(listOf("drawLine", "drawLine"), names(StatusIconKind.MISSED))
        assertEquals(listOf("drawLine"), names(StatusIconKind.SKIPPED))
        assertEquals(listOf("drawPath", "drawCircle"), names(StatusIconKind.UNCONFIRMED))
        assertEquals(listOf("drawCircle", "drawArc"), names(StatusIconKind.PARTIAL))
    }
}

/**
 * UI-07b §4: the 18 dp icons at 1× and 2× density, light and dark: the five states differ pixel by pixel in grayscale, and
 * each glyph covers a modest part of its box (not a blob, not a speck). PNGs (upscaled 8× / 4×, nearest neighbour) go to
 * the directory in the environment variable UI07B_SMALL_DIR when set.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class Ui07bSmallIconTest(private val dpi: String) {
    companion object { @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}") fun params() = listOf("mdpi", "xhdpi").map { arrayOf<Any>(it) } }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val kinds = listOf(StatusIconKind.TAKEN, StatusIconKind.PARTIAL, StatusIconKind.MISSED, StatusIconKind.SKIPPED, StatusIconKind.UNCONFIRMED)

    private fun grab(tag: String): android.graphics.Bitmap {
        val view = ui.activity.window.decorView
        val all = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888); view.draw(android.graphics.Canvas(all))
        val b = ui.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        return android.graphics.Bitmap.createBitmap(all, b.left.toInt(), b.top.toInt(), b.width.toInt(), b.height.toInt())
    }
    private fun gray(img: android.graphics.Bitmap): IntArray {
        val px = IntArray(img.width * img.height); img.getPixels(px, 0, img.width, 0, 0, img.width, img.height)
        return IntArray(px.size) { i -> val p = px[i]; ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000 }
    }

    @Test fun eighteenDpStaysApartAndClear() {
        RuntimeEnvironment.setQualifiers("w360dp-h640dp-$dpi")
        var mode by mutableStateOf(ThemeMode.LIGHT)
        ui.setContent { NotesTheme(mode) { androidx.compose.material3.Surface { Column {
            Row(Modifier.padding(8.dp).testTag("row")) { kinds.forEach { k -> Box(Modifier.padding(4.dp)) { Box(Modifier.testTag("s:$k")) { StatusIcon(k, StatusIconSizes.S) } } } }
        } } } }
        val dir = System.getenv("UI07B_SMALL_DIR")?.let { File(it).apply { mkdirs() } }
        for (m in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            mode = m; ui.waitForIdle()
            val imgs = kinds.associateWith { gray(grab("s:$it")) }
            val n = imgs.values.first().size; val bg = imgs.values.first()[0]
            for (i in kinds.indices) for (j in i + 1 until kinds.size) {
                val a = imgs.getValue(kinds[i]); val b = imgs.getValue(kinds[j])
                val diff = a.indices.count { p -> kotlin.math.abs(a[p] - b[p]) > 32 }
                println("SMALLDIFF\t$dpi\t$m\t${kinds[i]}\t${kinds[j]}\t$diff/$n")
                assertTrue("$dpi $m ${kinds[i]} vs ${kinds[j]}: $diff of $n px differ", diff >= n * 0.06)
            }
            for (k in kinds) {
                val ink = imgs.getValue(k).count { kotlin.math.abs(it - bg) > 32 }
                println("SMALLINK\t$dpi\t$m\t$k\t$ink/$n")
                assertTrue("$dpi $m $k: ink $ink of $n", ink >= n * 0.06 && ink <= n * 0.45)
            }
            dir?.let { d ->
                val row = grab("row"); val scale = if (dpi == "mdpi") 8 else 4
                android.graphics.Bitmap.createScaledBitmap(row, row.width * scale, row.height * scale, false).let { up ->
                    File(d, "18dp_${dpi}_${m.name.lowercase()}.png").outputStream().use { up.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
                val g = gray(row); val gb = android.graphics.Bitmap.createBitmap(row.width, row.height, android.graphics.Bitmap.Config.ARGB_8888)
                gb.setPixels(IntArray(g.size) { (0xFF shl 24) or (g[it] shl 16) or (g[it] shl 8) or g[it] }, 0, row.width, 0, 0, row.width, row.height)
                android.graphics.Bitmap.createScaledBitmap(gb, row.width * scale, row.height * scale, false).let { up ->
                    File(d, "18dp_${dpi}_${m.name.lowercase()}_gray.png").outputStream().use { up.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
            }
        }
    }
}

private fun SemanticsNodeInteraction.iconColor() = fetchSemanticsNode().config.getOrNull(StatusIconColorKey)

/**
 * UI-07b §3 at the real call sites: each passes the colour the contrast matrix (StatusIconContrastTest) checks — onPrimary on
 * the today card and today's pill, onContainer on the tinted month / week cells and chips, the accent elsewhere.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "w411dp-h1800dp")
class Ui07bCallSiteColourTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val f = Ui07Fixture
    private lateinit var c: ColorScheme
    private lateinit var sem: SemanticColors
    private var records by mutableStateOf(listOf<RecordEntity>())
    private var open by mutableStateOf(listOf<net.plainnotes.app.domain.TimelineEntry>())

    private fun show(full: Boolean = false) {
        ui.setContent { CompositionLocalProvider(LocalTodayClock provides { f.now }) { NotesTheme(ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            c = MaterialTheme.colorScheme; sem = NotesTheme.semantic
            CalendarScreen(NotesState(listOf(f.medA, f.medB), open, emptyList(), loading = false, calendarStart = f.today), f.today, {}, {}, {}, {}, PaddingValues(),
                extra = NotesViewModel.ExtraState(records = records, upcoming = open), initialFull = full)
        } } } }
        ui.waitForIdle()
    }
    private fun icon(tag: String) = ui.onNodeWithTag(tag, useUnmergedTree = true).iconColor()

    @Test fun homeTodayCardLogAndRecentDays() {
        ui.mainClock.autoAdvance = false
        open = listOf(f.entry(f.medA, f.at(f.today, 20)), f.entry(f.medB, f.at(f.today, 21)))
        records = listOf(f.record(1, f.medA, f.at(f.today.minusDays(1), 8), "ON_TIME"), f.record(2, f.medA, f.at(f.today.minusDays(2), 8), "MISSED"),
            f.record(3, f.medB, f.at(f.today, 9), "SKIPPED"))
        show(); ui.mainClock.advanceTimeBy(1000)
        // Log a dose here: "Logged · Undo" on the primary card draws its check in onPrimary.
        records = records + f.record(4, f.medA, f.at(f.today, 20), "ON_TIME"); open = open.drop(1)
        ui.runOnUiThread { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }; ui.mainClock.advanceTimeBy(500)
        val logged = ui.onNode(hasAnyAncestor(hasTestTag("today-logged")) and SemanticsMatcher.keyIsDefined(StatusIconColorKey), useUnmergedTree = true)
        assertEquals(c.onPrimary, logged.iconColor())
        // Today's log on surfaceContainerLowest: the accent.
        assertEquals(sem.success.accent, icon("today-done-icon:4:TAKEN")); assertEquals(sem.skipped.accent, icon("today-done-icon:3:SKIPPED"))
        ui.mainClock.advanceTimeBy(5000)
        // Last 7 days: other days sit on surfaceContainerHighest (accent); today's primary pill with a mark draws in onPrimary.
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("recent-card"))
        assertEquals(sem.success.accent, icon("recent-icon:${f.today.minusDays(1)}")); assertEquals(sem.missed.accent, icon("recent-icon:${f.today.minusDays(2)}"))
        assertEquals(c.onPrimary, icon("recent-icon:${f.today}"))
    }

    @Test fun todayRingWithoutAMark() {
        show()
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("recent-card"))
        assertEquals(c.onPrimary, icon("recent-icon:${f.today}"))
        assertEquals(StatusIconKind.TODAY, ui.onNodeWithTag("recent-icon:${f.today}", useUnmergedTree = true).fetchSemanticsNode().config[StatusIconKindKey])
    }

    @Test fun monthAndWeekCellsUseTheirOwnContainerText() {
        records = listOf(f.record(1, f.medA, f.at(f.today.minusDays(1), 8), "ON_TIME"), f.record(2, f.medA, f.at(f.today.minusDays(2), 8), "MISSED"),
            f.record(3, f.medA, f.at(f.today.minusDays(3), 8), "MISSED", "AUTO_MISSED"),
            f.record(4, f.medA, f.at(f.today.minusDays(4), 8), "ON_TIME"), f.record(5, f.medB, f.at(f.today.minusDays(4), 9), "MISSED"))
        show(full = true)
        val list = ui.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasTestTag("calendar-card"))
        assertEquals(sem.success.onContainer, icon("day-icon:${f.today.minusDays(1)}"))
        assertEquals(sem.missed.onContainer, icon("day-icon:${f.today.minusDays(2)}"))
        assertEquals(sem.unconfirmed.onContainer, icon("day-icon:${f.today.minusDays(3)}"))
        assertEquals(sem.success.onContainer, icon("day-icon:${f.today.minusDays(4)}"))   // partial: the success line on the partial cell
        ui.onAllNodesWithText(ui.activity.getString(R.string.view_week)).onFirst().performSemanticsAction(SemanticsActions.OnClick); ui.waitForIdle()
        ui.onNodeWithTag("day-icon:${f.today.minusDays(1)}", useUnmergedTree = true).assertDoesNotExist()
        list.performScrollToNode(hasTestTag("calendar-card"))
        assertEquals(sem.success.onContainer, icon("week-icon:${f.today.minusDays(1)}"))
    }

    @Test fun chipAndConfirmCard() {
        ui.setContent { NotesTheme(ThemeMode.DARK) { androidx.compose.material3.Surface { Column {
            c = MaterialTheme.colorScheme; sem = NotesTheme.semantic
            listOf(DoseState.LATE, DoseState.MISSED, DoseState.SKIPPED, DoseState.UNCONFIRMED).forEach { s -> StatusChip(StatusStyles.of(s), modifier = Modifier.testTag("chip:$s")) }
            UnconfirmedCard(listOf(f.record(9, f.medB, f.now.minusSeconds(3600 * 5), "MISSED", "AUTO_MISSED")), f.today, f.zone, { f.medB }, {}, {}, {})
        } } } }
        fun chip(s: DoseState) = ui.onNode(hasAnyAncestor(hasTestTag("chip:$s")) and SemanticsMatcher.keyIsDefined(StatusIconColorKey), useUnmergedTree = true).iconColor()
        // Dark skipped: the accent on the skipped chip would be 2.94:1; the chip draws onContainer.
        assertEquals(sem.success.onContainer, chip(DoseState.LATE)); assertEquals(sem.missed.onContainer, chip(DoseState.MISSED))
        assertEquals(sem.skipped.onContainer, chip(DoseState.SKIPPED)); assertEquals(sem.unconfirmed.onContainer, chip(DoseState.UNCONFIRMED))
        assertEquals(sem.unconfirmed.accent, icon("unconfirmed-icon:9"))
    }
}
