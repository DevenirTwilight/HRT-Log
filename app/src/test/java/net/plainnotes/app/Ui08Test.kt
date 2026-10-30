package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

/** Synthetic UI-08 data: the four daily items plus one custom item. */
internal object Ui08Fixture {
    val today: LocalDate = LocalDate.now()
    val items = listOf(CheckinItemEntity(1, "DAY_MOOD", null, true, 0), CheckinItemEntity(2, "DAY_ENERGY", null, true, 1),
        CheckinItemEntity(3, "DAY_SLEEP", null, true, 2), CheckinItemEntity(4, "DAY_BODY", null, true, 3), CheckinItemEntity(5, null, "Synthetic custom", true, 4))
    fun scores(n: Int, day: LocalDate = today) = items.take(n).mapIndexed { i, it -> CheckinScoreEntity(day.toString(), it.id, i % 5 + 1) }
}

/** UI-08 C: the five buttons. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class)
class Ui08LevelButtonsTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private fun s(id: Int, vararg a: Any) = ui.activity.getString(id, *a)
    private val label = "Synthetic item"
    private fun level(n: Int) = ui.onNodeWithContentDescription(s(R.string.wb_level_spoken, label, n))

    @Test fun tapsWriteTheLevelClearWritesNullUntouchedWritesNothing() {
        var value by mutableStateOf<Int?>(null); val writes = mutableListOf<Int?>()
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { Column { FiveLevelInput(value, "Low", "High", label) { value = it; writes += it } } } }
        ui.onNodeWithText(s(R.string.wb_item_unrecorded)).assertIsDisplayed(); ui.runOnIdle { assertTrue(writes.isEmpty()) }
        for (n in 1..5) { level(n).performClick(); ui.runOnIdle { assertEquals(n, value) } }
        // Tapping the chosen level changes nothing; clearing is only "Clear".
        level(5).performClick(); ui.runOnIdle { assertEquals(listOf<Int?>(1, 2, 3, 4, 5), writes) }
        ui.onNodeWithText(s(R.string.wb_item_value, 5)).assertExists()
        ui.onNodeWithText(s(R.string.wb_clear)).performClick()
        ui.runOnIdle { assertNull(value); assertEquals(listOf<Int?>(1, 2, 3, 4, 5, null), writes) }
        // Same onScore sequence the slider test recorded before UI-08 (set 4, then clear): [4, null].
        writes.clear(); level(4).performClick(); ui.onNodeWithText(s(R.string.wb_clear)).performClick()
        ui.runOnIdle { assertEquals(listOf<Int?>(4, null), writes) }
    }

    @Test fun radioGroupSemanticsAndEveryChosenLevelLooksTheSame() {
        var value by mutableStateOf<Int?>(1); var sem: SemanticColors? = null
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { sem = NotesTheme.semantic; Column { FiveLevelButtons(value, label) { value = it } } } }
        for (n in 1..5) {
            val node = level(n).assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f)).fetchSemanticsNode()
            assertEquals(Role.RadioButton, node.config[SemanticsProperties.Role]); assertEquals(n == value, node.config[SemanticsProperties.Selected])
        }
        val one = level(1).fetchSemanticsNode().config[LevelSelectedColorKey]
        ui.runOnIdle { value = 5 }; ui.waitForIdle()
        val five = level(5).fetchSemanticsNode().config[LevelSelectedColorKey]
        assertEquals(one, five); assertEquals(sem!!.action.container, five)
        // The action colour is the primary, which success also uses; it must not be error or any other state colour.
        val states = listOf(sem!!.danger, sem!!.attention, sem!!.missed, sem!!.skipped, sem!!.unconfirmed).flatMap { listOf(it.container, it.accent) } + MaterialTheme.let { listOf(notesColorScheme(false).error, notesColorScheme(false).errorContainer) }
        assertFalse("not a state colour", five in states)
    }
}

/** UI-08 B: the day card. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h2400dp")
class Ui08DayCardTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val f = Ui08Fixture
    private fun s(id: Int, vararg a: Any) = ui.activity.getString(id, *a)
    private fun angle() = ui.onNodeWithTag("wb-sun").fetchSemanticsNode().config[SunAngleKey]

    @Test fun titleCountAndNextDay() {
        var scores by mutableStateOf(f.scores(0)); var date by mutableStateOf(f.today.toString())
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { WellbeingScreen(f.items, scores, emptyList(), { _, _, _ -> }, { _, _ -> }, PaddingValues(), date) { date = it } } }
        ui.onNodeWithTag("wb-card-title").assertTextEquals(s(R.string.wb_card_title_none_today))
        ui.onNodeWithTag("wb-card-count").assertTextEquals(s(R.string.wb_card_count, 0, 5))
        ui.onNodeWithTag("wb-next-day").assertIsNotEnabled()
        ui.runOnIdle { scores = f.scores(2) }; ui.onNodeWithTag("wb-card-title").assertTextEquals(s(R.string.wb_card_title_some))
        ui.onNodeWithTag("wb-card-count").assertTextEquals(s(R.string.wb_card_count, 2, 5))
        ui.runOnIdle { scores = f.scores(5) }; ui.onNodeWithTag("wb-card-title").assertTextEquals(s(R.string.wb_card_title_all))
        // A disabled item does not count; another day: "How did you feel that day?" and next is enabled.
        ui.onNodeWithTag("wb-prev-day").performClick(); ui.runOnIdle { assertEquals(f.today.minusDays(1).toString(), date) }
        ui.onNodeWithTag("wb-card-title").assertTextEquals(s(R.string.wb_card_title_none_other)); ui.onNodeWithTag("wb-next-day").assertIsEnabled()
        ui.onNodeWithTag("wb-next-day").performClick(); ui.runOnIdle { assertEquals(f.today.toString(), date) }
    }

    @Test fun disabledItemsAreNotCounted() {
        val items = f.items.mapIndexed { i, it -> if (i == 4) it.copy(enabled = false) else it }
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { WellbeingScreen(items, f.scores(5), emptyList(), { _, _, _ -> }, { _, _ -> }, PaddingValues()) } }
        ui.onNodeWithTag("wb-card-count").assertTextEquals(s(R.string.wb_card_count, 4, 4))
        ui.onNodeWithTag("wb-card-title").assertTextEquals(s(R.string.wb_card_title_all))
    }

    @Test fun sunTurnsPerNotedItemWithASpring() {
        var scores by mutableStateOf(f.scores(0))
        ui.mainClock.autoAdvance = false
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { CompositionLocalProvider(LocalReduceMotion provides false) {
            WellbeingScreen(f.items, scores, emptyList(), { _, _, _ -> }, { _, _ -> }, PaddingValues()) } } }
        ui.mainClock.advanceTimeBy(500); assertEquals(0f, angle(), 0.01f)
        ui.runOnIdle { scores = f.scores(3); androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }; ui.mainClock.advanceTimeBy(48)
        assertTrue("springs: ${angle()}", angle() > 0f && angle() < 3 * SUN_STEP_DEGREES)
        ui.mainClock.advanceTimeBy(5_000); assertEquals(3 * SUN_STEP_DEGREES, angle(), 0.01f)
    }

    @Test fun reducedMotionSunHasNoTransition() {
        var scores by mutableStateOf(f.scores(0))
        ui.mainClock.autoAdvance = false
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { CompositionLocalProvider(LocalReduceMotion provides true) {
            WellbeingScreen(f.items, scores, emptyList(), { _, _, _ -> }, { _, _ -> }, PaddingValues()) } } }
        ui.mainClock.advanceTimeBy(500)
        ui.runOnIdle { scores = f.scores(2); androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }; ui.mainClock.advanceTimeBy(32)
        assertEquals(2 * SUN_STEP_DEGREES, angle(), 0.01f)
    }

    @Test fun noStreaksBadgesOrJudgementWords() {
        val forbidden = listOf("坚持", "打卡", "连续", "得分", "正常", "异常", "偏低", "偏高", "堅持", "打卡", "連續", "得分", "正常", "異常")
        for (dir in listOf("values-zh", "values-b+zh+Hant")) {
            val text = File("src/main/res/$dir/strings.xml").readText().substringAfter("<!-- UI-08 (REQUIREMENTS §140)")
            forbidden.forEach { w -> assertFalse("$dir: $w", text.contains(w)) }
        }
    }
}

/** UI-08 F: the trend line — gaps stay gaps, lone days are dots, nothing else is drawn. */
class Ui08TrendShapeTest {
    private val d0 = LocalDate.of(2026, 3, 1)
    @Test fun consecutiveDaysJoinGapsBreakLoneDaysAreDots() {
        val pts = mapOf(d0 to 3, d0.plusDays(1) to 4, d0.plusDays(2) to 2, d0.plusDays(5) to 5, d0.plusDays(9) to 1, d0.plusDays(10) to 1)
        val s = trendShape(pts)
        assertEquals(listOf(d0 to d0.plusDays(1), d0.plusDays(1) to d0.plusDays(2), d0.plusDays(9) to d0.plusDays(10)), s.lines)
        assertEquals(listOf(d0.plusDays(5)), s.dots)
        assertEquals(TrendShape(emptyList(), listOf(d0)), trendShape(mapOf(d0 to 2)))
    }
}

/** The drawing itself, recorded on a Canvas proxy: only segments between consecutive days and lone-day dots. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class Ui08TrendDrawingTest {
    private data class Call(val name: String, val args: List<Any?>)
    @Test fun noMeanBandOrReferenceLine() {
        val d0 = LocalDate.of(2026, 3, 1)
        val pts = mapOf(d0 to 3, d0.plusDays(1) to 4, d0.plusDays(5) to 5, d0.plusDays(20) to 2, d0.plusDays(21) to 2)
        val calls = mutableListOf<Call>()
        val canvas = java.lang.reflect.Proxy.newProxyInstance(Canvas::class.java.classLoader, arrayOf(Canvas::class.java)) { _, m, args ->
            calls += Call(m.name.substringBefore('-'), args?.toList().orEmpty())
            when (m.returnType) { java.lang.Boolean.TYPE -> false; java.lang.Integer.TYPE -> 0; java.lang.Float.TYPE -> 0f; else -> null } } as Canvas
        CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, canvas, Size(600f, 128f)) { drawWellbeingTrend(pts, d0, d0.plusDays(29), Color.Blue, 4f) }
        val draws = calls.filter { it.name.startsWith("draw") }
        // 1→2 and 21→22 joined, nothing across the gaps; a dot for each of the five values (day 6 has only its dot).
        assertEquals(listOf("drawLine", "drawLine") + List(5) { "drawCircle" }, draws.map { it.name })
        draws.forEach { c -> assertEquals(Color.Blue, (c.args.last() as Paint).color) }
        assertEquals(PaintingStyle.Fill, (draws.last().args.last() as Paint).style)
        // Small dots in the line's colour: at most 1.2 × the line width (owner, 2026-10-22).
        draws.filter { it.name == "drawCircle" }.forEach { c -> assertTrue("small dot", (c.args[1] as Float) <= 4f * 1.2f + 0.01f) }
        // No horizontal line across the chart (a mean or reference line would span it).
        draws.filter { it.name == "drawLine" }.forEach { c ->
            val a = c.args[0] as Long; val b = c.args[1] as Long
            fun x(v: Long) = java.lang.Float.intBitsToFloat((v shr 32).toInt())
            assertTrue("short segment", kotlin.math.abs(x(b) - x(a)) < 600f / 10) }
    }
}

/** UI-08 F on screen: the range pills and the spoken line. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h2400dp")
class Ui08RecentTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    @Test fun rangeAndSpokenText() {
        val f = Ui08Fixture
        val scores = listOf(CheckinScoreEntity(f.today.toString(), 1, 3), CheckinScoreEntity(f.today.minusDays(2).toString(), 1, 4), CheckinScoreEntity(f.today.minusDays(40).toString(), 1, 2))
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { WellbeingScreen(f.items, scores, emptyList(), { _, _, _ -> }, { _, _ -> }, PaddingValues()) } }
        val c = ui.activity; val label = c.getString(R.string.wb_day_mood)
        fun said() = ui.onNodeWithTag("wb-trend:1").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        ui.onNodeWithTag("wb-recent").performScrollTo()
        assertEquals(c.getString(R.string.wb_trend_spoken, label, 30, 2), said())
        ui.onNodeWithTag("wb-range:90").performScrollTo().performClick(); assertEquals(c.getString(R.string.wb_trend_spoken, label, 90, 3), said())
        ui.onNodeWithTag("wb-range:0").performClick(); assertEquals(c.getString(R.string.wb_trend_spoken_all, label, 3), said())
        ui.onNodeWithTag("wb-range:7").performClick(); assertEquals(c.getString(R.string.wb_trend_spoken, label, 7, 2), said())
        ui.onNodeWithTag("wb-trend:2").assertDoesNotExist()   // no values, no line
        ui.onNodeWithText(c.getString(R.string.wb_recent_note)).assertExists()
    }
}

/** UI-08 G: the daily and symptoms tabs share one day, kept across recreation. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h2400dp")
class Ui08SharedDateTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    @Test fun yesterdayStaysWhenSwitchingTabsAndAfterRecreation() {
        val f = Ui08Fixture
        val restore = androidx.compose.ui.test.junit4.StateRestorationTester(ui)
        restore.setContent { NotesTheme(ThemeMode.LIGHT) { WellbeingHubContent(NotesState(loading = false), NotesViewModel.ExtraState(items = f.items), null, PaddingValues(),
            { _, _, _ -> }, { _, _ -> }, { _, _, _, _ -> }, {}, {}) } }
        ui.onNodeWithTag("wb-prev-day").performClick()
        val yesterday = ui.onNodeWithTag("wb-date").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
        ui.onNodeWithTag("wb-tab:1").performClick(); ui.waitForIdle()
        ui.onNodeWithTag("wb-tab:1").assertIsSelected()
        assertEquals(yesterday, ui.onNodeWithTag("wb-date").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString())
        ui.onNodeWithTag("wb-day-card").assertDoesNotExist()
        restore.emulateSavedInstanceStateRestore(); ui.waitForIdle()
        ui.onNodeWithTag("wb-tab:1").assertIsSelected()
        assertEquals(yesterday, ui.onNodeWithTag("wb-date").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString())
        ui.onNodeWithTag("wb-tab:0").performClick(); ui.waitForIdle()
        assertEquals(yesterday, ui.onNodeWithTag("wb-date").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString())
        // Tabs: tab role, selected state, ≥ 44 dp.
        (0..2).forEach { ui.onNodeWithTag("wb-tab:$it").assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(44f))
            assertEquals(Role.Tab, ui.onNodeWithTag("wb-tab:$it").fetchSemanticsNode().config[SemanticsProperties.Role]) }
    }
}

/**
 * UI-08 §6: four languages × 320/411 dp × font 1/2 × light/dark: nothing cut, every button ≥ 48 dp, radio semantics read.
 * Native graphics: the legacy mode does not measure text, so text bounds would be meaningless.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class) @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class Ui08LayoutTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
    companion object { @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr").flatMap { l -> listOf(320, 411).flatMap { w -> listOf(1f, 2f).flatMap { f -> listOf(false, true).map { arrayOf<Any>(l, w, f, it) } } } } }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    @Test fun nothingCutAndTargetsBigEnough() {
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h4000dp-xxhdpi"); RuntimeEnvironment.setFontScale(font)
        val f = Ui08Fixture
        ui.setContent { NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            WellbeingScreen(f.items, f.scores(2), emptyList(), { _, _, _ -> }, { _, _ -> }, PaddingValues()) } } }
        // Nothing ellipsized, and each text sits whole inside the card (its node is as tall as its laid-out lines).
        val cardBox = ui.onNodeWithTag("wb-day-card").fetchSemanticsNode().boundsInRoot
        for (tag in listOf("wb-card-title", "wb-card-subtitle", "wb-card-count")) {
            val node = ui.onNodeWithTag(tag).fetchSemanticsNode(); val b = node.boundsInRoot
            val r = mutableListOf<TextLayoutResult>(); node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(r); val t = r.single()
            println("UI08LAYOUT\t$tag\tlines=${t.lineCount}\tbounds=$b")
            assertTrue("$tag ellipsized", (0 until t.lineCount).none { t.isLineEllipsized(it) })
            assertTrue("$tag inside the card: $b in $cardBox", b.left >= cardBox.left - 0.5f && b.right <= cardBox.right + 0.5f && b.bottom <= cardBox.bottom + 0.5f && b.width > 0f)
        }
        val density = ui.activity.resources.displayMetrics.density
        val label = ui.activity.getString(R.string.wb_day_mood)
        for (n in 1..5) {
            val node = ui.onNodeWithContentDescription(ui.activity.getString(R.string.wb_level_spoken, label, n)).fetchSemanticsNode()
            assertTrue("$n ≥ 48dp", node.boundsInRoot.height / density >= 47.5f)
            assertEquals(Role.RadioButton, node.config[SemanticsProperties.Role]); assertEquals(n == 1, node.config[SemanticsProperties.Selected])
        }
        listOf("wb-prev-day", "wb-next-day").forEach { assertTrue(ui.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.height / density >= 47.5f) }
        // The card stays inside the screen.
        val card = ui.onNodeWithTag("wb-day-card").fetchSemanticsNode().boundsInRoot; assertTrue(card.right / density <= width + 0.5f)
    }
}

/** UI-08 §6 contrast, 3 levels × light/dark: card text (also over the sun), level buttons, their outline, the trend line. */
@RunWith(Parameterized::class)
class Ui08ContrastTest(private val name: String, private val fg: Int, private val bg: Int, private val min: Double) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun params(): List<Array<Any>> {
            val out = mutableListOf<Array<Any>>()
            for (dark in listOf(false, true)) for (k in Contrast.entries) {
                val c = notesColorScheme(dark, k); val s = semanticColors(c, dark, k); val t = "${if (dark) "dark" else "light"}/$k"
                fun add(n: String, a: Color, b: Color, m: Double) { out += arrayOf<Any>("$n $t", a.toArgbInt(), b.toArgbInt(), m) }
                add("card text", s.success.onContainer, s.success.container, 4.5)
                add("card text over the sun", s.success.onContainer, sunColor(s.success.container), 4.5)
                add("count pill text", s.success.onContainer, s.success.onContainer.copy(alpha = 0.10f).compositeOverC(s.success.container), 4.5)
                add("chosen level", s.action.onContainer, s.action.container, 4.5)
                add("level", c.onSurface, c.surfaceContainerLow, 4.5)
                add("level outline", c.outline, c.surfaceContainerLowest, 3.0)
                add("trend line", c.primary, c.surfaceContainerLow, 3.0)
            }
            return out
        }
        private fun Color.toArgbInt() = toArgb()
        private fun Color.compositeOverC(b: Color) = compositeOver(b)
        fun ratio(a: Color, b: Color): Double { val l1 = a.luminance() + 0.05; val l2 = b.luminance() + 0.05; return maxOf(l1, l2) / minOf(l1, l2) }
    }
    @Test fun meetsMinimum() { val r = ratio(Color(fg), Color(bg)); println("UI08CONTRAST\t$name\t${"%.2f".format(r)}"); assertTrue("$name ${"%.2f".format(r)} < $min", r >= min) }
}

/**
 * UI-08 §3.7: the wellbeing PDF summary is unchanged — its code and every existing string are the base bytes (only new keys added).
 * PDF-01 (REQUIREMENTS §145) migration: Exports.kt now carries the new PDF layout, so its byte pin became "text identical + layout
 * per PDF-01": the pre-PDF-01 renderer is frozen as LegacyPdfReport (pinned here) and Pdf01Test shows, for the summary in four
 * languages with every part and with a partial set, that the new layout writes exactly the same words.
 */
class Ui08PdfSummaryUnchangedTest {
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    @Test fun summaryCodeAndStringsAreTheBase() {
        assertEquals("df0d246ba308f43dd869c131b5d781dfba4125ec940df9fd55dcc15ddb1c497b", sha(File("src/main/java/net/plainnotes/app/export/WellbeingSummary.kt").readBytes()))
        assertEquals("a9a2de731d4d0ffcfbd7c4ce154eef6a720f4c7b0420e5098c51d65026ba4fc0", sha(File("src/test/java/net/plainnotes/app/export/LegacyPdfReport.kt").readBytes()))
        // ConcChart.kt: the original hash, or an approved one: UI-12 (REQUIREMENTS §162, display-approvals id PD-2026-10-29-UI12-CONCCHART:
        // readout card, dose ticks, day axis, no red "now" line) and the axis-label fix (§171, PD-2026-10-29-CONCCHART-AXIS-LABELS:
        // overlapping/cut-off axis labels only). The wellbeing summary does not use the chart.
        assertTrue("ConcChart.kt must be the original or an approved version", sha(File("src/main/java/net/plainnotes/app/ui/ConcChart.kt").readBytes()) in
            setOf("51d0f88bf158113f9a08415a7498043930055096194b7f0f967e7bcb59b26c06", "9fca1d03a34499c9597dcb2b4cdaf7a9f8e0e33cb784981845145277d675023a",
                "20f224d607ebc48d9a0dd036f4b070ba0935f26bae7d67f6b2fd37916528c5dc",
                "9fe98011e02aba34058f0e2ca77a7ac480123af9cc7c1f0d13827abfe13cecbe"))
        val base = mapOf("values" to "caa533fbbe4165f32d7ba7f7a1f9c3620995c4b4a2de1e9f81353c9fd3a4638a", "values-zh" to "7d0d69f1e8734cde743aa0e94f669895003c4b6508acf76b6d2920b269f1ef84",
            "values-b+zh+Hant" to "2c53efeec7e47153485abac6f345dd11f43047849e9ebc140067b991be459a29", "values-fr" to "b12b41def544ec6069e8e941af03589bb9837403cb8426b4a1c16975c254eaf0")
        for ((dir, digest) in base) {
            val current = File("src/main/res/$dir/strings.xml").readText()
            // MED-02 §144 authorizes only this Tink attribution addition. Normalize that
            // exact edit inside licenses_body; keep all original resource/PDF digests.
            val (added, original) = when (dir) {
                "values" -> "WorkManager, kotlinx.coroutines and Tink" to "WorkManager and kotlinx.coroutines"
                "values-fr" -> "WorkManager, kotlinx.coroutines et Tink" to "WorkManager et kotlinx.coroutines"
                else -> "kotlinx.coroutines、Tink" to "kotlinx.coroutines"
            }
            val start = current.indexOf("<string name=\"licenses_body\">")
            val end = current.indexOf("</string>", start)
            assertTrue("$dir licence element", start >= 0 && end > start)
            val licence = current.substring(start, end)
            assertEquals("$dir exact approved attribution edit", 1, Regex(Regex.escape(added)).findAll(licence).count())
            var text = current.replaceRange(start, end, licence.replace(added, original))
            // §154 authorizes precisely two privacy statements, not arbitrary PDF/string changes.
            // Verify each new element's immutable approved hash before normalizing to the base.
            val approved = org.json.JSONObject(File("src/test/resources/med02-approved-privacy-activation.json").readText()).getJSONObject(dir)
            for (name in listOf("privacy_notice_backup_body", "privacy_notice_changes_body")) {
                val element = requireNotNull(Regex("<string name=\"$name\">.*?</string>").find(text))
                val evidence = approved.getJSONObject(name)
                // MED-03 (REQUIREMENTS §161.B) extends exactly one statement, the backup notice, to describe independent
                // files, whole-draw links and the recycle bin. Its exact text is pinned by its own hash record; any other
                // edit of a privacy statement still fails here.
                val med03 = org.json.JSONObject(File("src/test/resources/med03-approved-privacy-files.json").readText()).getJSONObject(dir)
                val accepted = listOfNotNull(evidence.getString("approvedSha256"), med03.optJSONObject(name)?.getString("approvedSha256"))
                assertTrue("$dir exact approved $name", sha(element.value.toByteArray()) in accepted)
                text = text.replaceRange(element.range, evidence.getString("original"))
            }
            val a = text.indexOf("<!-- UI-08 (REQUIREMENTS §140)"); val b = text.lastIndexOf("</resources>")
            assertTrue(dir, a in 0 until b)
            assertEquals(dir, digest, sha((text.substring(0, a) + text.substring(b)).toByteArray()))
        }
    }
}
