package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import net.plainnotes.app.reminder.ReminderCoordinator
import net.plainnotes.app.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.*

/** Synthetic data for the UI-07 screen tests: placeholder names, a fixed "now". */
internal object Ui07Fixture {
    val zone: ZoneId = ZoneId.systemDefault()
    val today: LocalDate = LocalDate.of(2026, 3, 10)
    val now: Instant = today.atTime(18, 0).atZone(zone).toInstant()
    val medA = MedicationEntity(1, "药物 A", "E2", "ORAL", "MG", 2.0, 30.0, null, 15, 120, false, null, true, true, 0)
    val medB = MedicationEntity(2, "药物 B", "SPIRO", "ORAL", "MG", 50.0, 30.0, null, 15, 120, false, null, true, true, 1)
    fun at(d: LocalDate, h: Int, m: Int = 0): Instant = d.atTime(h, m).atZone(zone).toInstant()
    fun entry(m: MedicationEntity, t: Instant, st: SlotState = SlotState.PENDING) =
        TimelineEntry(Slot("wall:${m.id}@$t", m.id, m.id, t, t, zone, m.dose_per_intake, 15, 120, t.minusSeconds(86400 * 30)), st)
    fun record(id: Long, m: MedicationEntity, t: Instant, status: String, origin: String = "APP") = RecordEntity(id, m.id, scheduled_utc = t.toEpochMilli(), scheduled_zone = zone.id,
        planned_dose = m.dose_per_intake, taken_utc = if (status == "MISSED" || status == "SKIPPED") null else t.toEpochMilli(), taken_zone = zone.id,
        actual_dose = if (status == "MISSED" || status == "SKIPPED") null else m.dose_per_intake, status = status, origin = origin, revision = 1,
        config_snapshot = MedicationSnapshot.encode(m, null))
}

private fun SemanticsNodeInteraction.kind() = fetchSemanticsNode().config.getOrNull(StatusIconKindKey)

/** UI-07 A: the status icon on screen — kind, the four sizes, nothing spoken, and the five states apart in grayscale. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class Ui07StatusIconTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val kinds = listOf(StatusIconKind.TAKEN, StatusIconKind.PARTIAL, StatusIconKind.MISSED, StatusIconKind.SKIPPED, StatusIconKind.UNCONFIRMED)

    @Test fun everyKindAndSizeRendersSilently() {
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { Column(Modifier.verticalScroll(rememberScrollState())) { StatusIconKind.entries.forEach { k -> StatusIconSizes.all.forEach { s ->
            StatusIcon(k, s, Modifier.testTag("i:$k:${s.value.toInt()}")) } } } } }
        val density = ui.activity.resources.displayMetrics.density
        for (k in StatusIconKind.entries) for (s in StatusIconSizes.all) {
            val n = ui.onNodeWithTag("i:$k:${s.value.toInt()}")
            assertEquals(k, n.kind())
            val b = n.fetchSemanticsNode().size
            assertEquals("$k ${s}", s.value, b.width / density, 0.6f); assertEquals(s.value, b.height / density, 0.6f)
            // Decorative: no words of its own, so a row that says the state is never read twice.
            assertNull(n.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))
            assertNull(n.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text))
        }
    }

    private fun shot(tag: String): IntArray {
        val view = ui.activity.window.decorView
        val all = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888); view.draw(android.graphics.Canvas(all))
        val b = ui.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        val img = android.graphics.Bitmap.createBitmap(all, b.left.toInt(), b.top.toInt(), b.width.toInt(), b.height.toInt())
        val px = IntArray(img.width * img.height); img.getPixels(px, 0, img.width, 0, 0, img.width, img.height)
        System.getProperty("ui07.grayscaleDir")?.let { dir -> File(dir).mkdirs()
            val g = android.graphics.Bitmap.createBitmap(img.width, img.height, android.graphics.Bitmap.Config.ARGB_8888)
            g.setPixels(IntArray(px.size) { i -> val p = px[i]; val y = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000; (0xFF shl 24) or (y shl 16) or (y shl 8) or y },
                0, img.width, 0, 0, img.width, img.height)
            File(dir, "$tag.png".replace(':', '-')).outputStream().use { g.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        return IntArray(px.size) { i -> val p = px[i]; ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000 }
    }

    @Test fun fiveStatesStayApartInGrayscale() {
        var mode by mutableStateOf(ThemeMode.LIGHT); var contrast by mutableStateOf(Contrast.STANDARD)
        ui.setContent { NotesTheme(mode, contrast = contrast) { androidx.compose.material3.Surface { Column { kinds.forEach { k ->
            Box(Modifier.testTag("g:$k")) { StatusIcon(k, StatusIconSizes.XL) } } } } } }
        for (m in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) for (k in Contrast.entries) {
            mode = m; contrast = k; ui.waitForIdle()
            val imgs = kinds.associateWith { shot("g:$it") }
            for (i in kinds.indices) for (j in i + 1 until kinds.size) {
                val a = imgs.getValue(kinds[i]); val b = imgs.getValue(kinds[j])
                val diff = a.indices.count { p -> kotlin.math.abs(a[p] - b[p]) > 32 }
                assertTrue("$m/$k ${kinds[i]} vs ${kinds[j]}: $diff px differ", diff >= 40)
            }
        }
    }
}

/** UI-07 B: press morph — corners to 16 dp and 96 % while pressed, back on release; reduced motion has no in-between frame. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class Ui07PressMorphTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private fun morph() = ui.onNodeWithTag("b").fetchSemanticsNode().config[PressMorphKey]

    private fun show(reduce: Boolean) {
        ui.mainClock.autoAdvance = false
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { CompositionLocalProvider(LocalReduceMotion provides reduce) {
            val press = remember { MutableInteractionSource() }; val m = rememberPressMorph(press, rest = Radii.xl)
            Button({}, Modifier.heightIn(min = 56.dp).pressMorph(m).testTag("b"), shape = m.shape, interactionSource = press) { Text("x") }
        } } }
        ui.mainClock.advanceTimeBy(100)
    }

    @Test fun springsToTargetsAndBack() {
        show(false)
        assertEquals(PressMorph(Radii.xl, 1f), morph())
        ui.onNodeWithTag("b").performTouchInput { down(center) }
        ui.mainClock.advanceTimeBy(48)
        val mid = morph(); assertTrue("an in-between frame: $mid", mid.radius < Radii.xl && mid.radius > 16.dp)
        ui.mainClock.advanceTimeBy(2_000)
        morph().let { assertEquals(16f, it.radius.value, 0.05f); assertEquals(0.96f, it.scale, 0.002f) }
        ui.onNodeWithTag("b").performTouchInput { up() }
        ui.mainClock.advanceTimeBy(2_000)
        morph().let { assertEquals(Radii.xl.value, it.radius.value, 0.05f); assertEquals(1f, it.scale, 0.002f) }
    }

    @Test fun reducedMotionJumps() {
        show(true)
        ui.onNodeWithTag("b").performTouchInput { down(center) }
        ui.mainClock.advanceTimeByFrame(); ui.mainClock.advanceTimeByFrame()
        assertEquals(PressMorph(16.dp, 0.96f), morph())
        ui.onNodeWithTag("b").performTouchInput { up() }
        ui.mainClock.advanceTimeByFrame(); ui.mainClock.advanceTimeByFrame()
        assertEquals(PressMorph(Radii.xl, 1f), morph())
    }
}

/** UI-07 E: grouped list corners, 56 dp items and the click callback. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class Ui07GroupedListTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    @Test fun cornersHeightAndClicks() {
        var n by mutableIntStateOf(1); val clicked = mutableListOf<Int>()
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { GroupedList((1..n).toList(), onClick = { clicked += it }, itemModifier = { Modifier.testTag("g$it") }) { Text("Item $it") } } }
        val density = ui.activity.resources.displayMetrics.density
        for (count in listOf(1, 2, 4)) {
            n = count; ui.waitForIdle()
            (1..count).forEach { i ->
                val node = ui.onNodeWithTag("g$i").fetchSemanticsNode()
                assertEquals(groupedShape(i - 1, count), node.config[GroupedShapeKey])
                assertTrue(node.boundsInRoot.height / density >= 55.5f)
            }
            if (count > 1) { // 3 dp between items
                val a = ui.onNodeWithTag("g1").fetchSemanticsNode().boundsInRoot; val b = ui.onNodeWithTag("g2").fetchSemanticsNode().boundsInRoot
                assertEquals(3f, (b.top - a.bottom) / density, 0.5f)
            }
        }
        ui.onNodeWithTag("g3").assertHasClickAction().performClick(); assertEquals(listOf(3), clicked)
    }
}

/**
 * UI-07 C / D on the real CalendarScreen: hero time size cap, 2× font at 320 dp, the today card switch, the new-record pop,
 * the confirm-card collapse and "Show all" — immediate with reduced motion, same final state without.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk = [35])
class Ui07TodayTest(private val reduce: Boolean) {
    companion object { @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "reduce={0}") fun params() = listOf(arrayOf<Any>(true), arrayOf<Any>(false)) }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val f = Ui07Fixture
    private val context get() = ui.activity
    private fun s(id: Int, vararg a: Any) = context.getString(id, *a)
    private var records by mutableStateOf(listOf<RecordEntity>())
    private var open by mutableStateOf(listOf<TimelineEntry>())
    private var undone: RecordEntity? = null

    private fun show(full: Boolean = false) {
        ui.setContent { CompositionLocalProvider(LocalTodayClock provides { f.now }) { NotesTheme(ThemeMode.LIGHT) { CompositionLocalProvider(LocalReduceMotion provides reduce) {
            androidx.compose.material3.Surface {
                CalendarScreen(NotesState(listOf(f.medA, f.medB), open, emptyList(), loading = false, calendarStart = f.today), f.today, {}, {}, {}, {}, PaddingValues(),
                    extra = NotesViewModel.ExtraState(records = records, upcoming = open), onUndoRecord = { undone = it }, initialFull = full)
            } } } } }
        ui.waitForIdle()
    }
    /** With the clock paused, state written from the test thread reaches composition only once apply notifications are sent. */
    private fun apply() { ui.runOnUiThread { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() } }
    private fun fontSizeOf(tag: String): Float {
        val r = mutableListOf<TextLayoutResult>(); ui.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(r)
        return r.single().layoutInput.style.fontSize.value
    }

    @Test fun heroNumberStopsGrowingAt1point3() {
        for (fs in listOf(1f, 1.3f, 2f)) {
            RuntimeEnvironment.setFontScale(fs)
            open = listOf(f.entry(f.medA, f.at(f.today, 20)))
            show()
            val drawn = fontSizeOf("today-next-time") * context.resources.configuration.fontScale
            assertEquals("font scale $fs", 64f * minOf(fs, 1.3f), drawn, 0.05f)
        }
    }

    @Test fun twiceFontAt320dpNothingCut() {
        RuntimeEnvironment.setQualifiers("zh-rCN-w320dp-h1800dp-xxhdpi"); RuntimeEnvironment.setFontScale(2f)
        open = listOf(f.entry(f.medA, f.at(f.today, 17), SlotState.OVERDUE), f.entry(f.medB, f.at(f.today, 20)))
        show()
        val card = ui.onNodeWithTag("today-card").fetchSemanticsNode().boundsInRoot
        for (tag in listOf("today-next-time", "today-next-status", "today-take-now", "today-more")) {
            val b = ui.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$tag inside the card", b.left >= card.left - 0.5f && b.right <= card.right + 0.5f)
        }
        val r = mutableListOf<TextLayoutResult>(); ui.onNodeWithTag("today-next-status").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(r)
        assertTrue((0 until r.single().lineCount).none { r.single().isLineEllipsized(it) })
        ui.onNodeWithTag("today-take-now").assertHasClickAction()
    }

    @Test fun motionEndsInTheSameStateAndIsImmediateWhenReduced() {
        RuntimeEnvironment.setQualifiers("w411dp-h1800dp")   // the whole today section on screen, so its bounds are not clipped
        ui.mainClock.autoAdvance = false
        open = listOf(f.entry(f.medA, f.at(f.today, 20)), f.entry(f.medB, f.at(f.today, 21)))
        records = listOf(f.record(1, f.medA, f.at(f.today, 8), "ON_TIME"))
        show(); ui.mainClock.advanceTimeBy(1000)
        val density = context.resources.displayMetrics.density
        // Log the 20:00 dose: "next" switches, the new row pops in and Undo appears.
        records = records + f.record(2, f.medA, f.at(f.today, 20), "ON_TIME"); open = open.drop(1)
        apply(); ui.mainClock.advanceTimeBy(64)   // a few frames: the springs need ~330 ms to settle, snap() is done at once
        val iconWidth = { ui.onNodeWithTag("today-done-icon:2:TAKEN", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width / density }
        val nextTimes = { ui.onAllNodesWithTag("today-next-time").fetchSemanticsNodes().size }
        if (reduce) { assertEquals(32f, iconWidth(), 0.6f); assertEquals(1, nextTimes()) }
        else { assertTrue("pops from 0.3×: ${iconWidth()}", iconWidth() < 31f); assertEquals("old and new during the switch", 2, nextTimes()) }
        ui.mainClock.advanceTimeBy(1500)
        assertEquals(32f, iconWidth(), 0.6f); assertEquals(1, nextTimes())
        ui.onNode(hasTestTag("today-next-name") and hasAnyDescendant(hasText("药物 B"))).assertExists()
        // Undo for 4 s calls the existing delete with the logged record.
        ui.onNodeWithTag("today-undo").assertTextEquals(s(R.string.today_undo)).performClick(); assertEquals(2L, undone?.id)
        apply(); ui.mainClock.advanceTimeBy(64); ui.onNodeWithTag("today-undo").assertDoesNotExist()
        // A record logged later and left alone: Undo goes away by itself after 4 s.
        records = records + f.record(3, f.medB, f.at(f.today, 21), "LATE"); open = emptyList()
        apply(); ui.mainClock.advanceTimeBy(500); ui.onNodeWithTag("today-undo").assertExists()
        ui.mainClock.advanceTimeBy(UNDO_WINDOW_MS); ui.onNodeWithTag("today-undo").assertDoesNotExist()
    }

    @Test fun showAllSpringsOpen() {
        ui.mainClock.autoAdvance = false
        records = (1L..5L).map { f.record(it, f.medA, f.at(f.today, 6 + it.toInt()), "ON_TIME") }
        show(); ui.mainClock.advanceTimeBy(1000)
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("today-show-all:log"))
        ui.onNodeWithTag("today-show-all:log").performClick()
        apply(); ui.mainClock.advanceTimeBy(64)
        (1L..5L).forEach { ui.onNodeWithTag("today-done:$it", useUnmergedTree = true).assertExists() }
        val section = { ui.onNodeWithTag("today-section").fetchSemanticsNode().boundsInRoot.height }
        val early = section(); ui.mainClock.advanceTimeBy(1500); val end = section()
        if (reduce) assertEquals(end, early, 0.5f) else assertTrue("height still growing: $early < $end", early < end)
    }

    @Test fun confirmCardCollapsesToAResult() {
        ui.mainClock.autoAdvance = false
        val auto = f.record(9, f.medB, f.now.minusSeconds(3600 * 5), "MISSED", "AUTO_MISSED")
        records = listOf(auto); show(); ui.mainClock.advanceTimeBy(1000)
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("unconfirmed-card"))
        ui.onNodeWithTag("unconfirmed-not-taken:9").performClick()
        records = listOf(auto.copy(origin = "APP", revision = 2))   // what confirmMissed leaves
        apply(); ui.mainClock.advanceTimeBy(64)
        ui.onNodeWithTag("unconfirmed-row:9").assertDoesNotExist()
        ui.onNodeWithTag("unconfirmed-result:9").assertExists()
        ui.mainClock.advanceTimeBy(1500)
        ui.onNode(hasTestTag("unconfirmed-result:9") and hasAnyDescendant(hasText(s(R.string.confirm_result_not_taken), substring = true))).assertIsDisplayed()
    }
}

/**
 * UI-07 F: the home "last 7 days" strip — seven days ending today, today filled, the same spoken names as the month grid,
 * "Full calendar" and back, kept across recreation. Four languages.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk = [35])
class Ui07RecentDaysTest(private val locale: String) {
    companion object { @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}") fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr").map { arrayOf<Any>(it) } }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val f = Ui07Fixture
    private val context get() = ui.activity
    private fun s(id: Int) = context.getString(id)

    @Test fun sevenDaysTodayLastAndFullCalendar() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h1800dp-xxhdpi"); RuntimeEnvironment.setFontScale(2f)
        val records = listOf(f.record(1, f.medA, f.at(f.today.minusDays(1), 8), "ON_TIME"), f.record(2, f.medA, f.at(f.today.minusDays(2), 8), "MISSED"),
            f.record(3, f.medA, f.at(f.today.minusDays(3), 8), "MISSED", "AUTO_MISSED"), f.record(4, f.medA, f.at(f.today.minusDays(9), 8), "ON_TIME"))
        val restore = androidx.compose.ui.test.junit4.StateRestorationTester(ui)
        restore.setContent { CompositionLocalProvider(LocalTodayClock provides { f.now }) { NotesTheme(ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            CalendarScreen(NotesState(listOf(f.medA), emptyList(), emptyList(), loading = false, calendarStart = f.today), f.today, {}, {}, {}, {}, PaddingValues(),
                extra = NotesViewModel.ExtraState(records = records))
        } } } }
        ui.waitForIdle()
        val list = ui.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasTestTag("recent-card"))
        // Home default: the strip, not the month grid.
        ui.onNodeWithTag("calendar-card").assertDoesNotExist()
        val days = (6 downTo 0).map { f.today.minusDays(it.toLong()) }
        days.forEach { d -> ui.onNodeWithTag("recent-day:$d").assertExists().assertHasClickAction() }
        ui.onNodeWithTag("recent-day:${f.today.minusDays(7)}").assertDoesNotExist(); ui.onNodeWithTag("recent-day:${f.today.plusDays(1)}").assertDoesNotExist()
        val xs = days.map { ui.onNodeWithTag("recent-day:$it").fetchSemanticsNode().boundsInRoot.left }
        assertEquals("oldest first, today last", xs.sorted(), xs)
        // Icons: taken / missed / unconfirmed as on the month grid; today without a state shows the today ring.
        fun icon(d: LocalDate) = ui.onNodeWithTag("recent-icon:$d", useUnmergedTree = true).kind()
        assertEquals(StatusIconKind.TAKEN, icon(f.today.minusDays(1))); assertEquals(StatusIconKind.MISSED, icon(f.today.minusDays(2)))
        assertEquals(StatusIconKind.UNCONFIRMED, icon(f.today.minusDays(3))); assertEquals(StatusIconKind.TODAY, icon(f.today))
        // Same spoken name as the month grid ("<date>, missed"), and one node per day.
        val l = context.resources.configuration.locales[0]
        fun full(d: LocalDate) = d.format(java.time.format.DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(l, "yMMMMdEEEE"), l))
        fun said(d: LocalDate) = ui.onNodeWithTag("recent-day:$d").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue(said(f.today.minusDays(2)).startsWith(full(f.today.minusDays(2)))); assertTrue(said(f.today.minusDays(2)).contains(s(R.string.legend_missed)))
        assertTrue(said(f.today.minusDays(3)).contains(s(R.string.status_unconfirmed))); assertTrue(said(f.today).contains(s(R.string.today)))
        ui.onAllNodesWithText(f.today.minusDays(2).dayOfMonth.toString()).assertCountEquals(0)   // the digit is not read again
        // Selecting a day shows that day's list, as on the month grid.
        ui.onNodeWithTag("recent-day:${f.today.minusDays(2)}").performClick(); ui.waitForIdle()
        ui.onNode(hasTestTag("recent-day:${f.today.minusDays(2)}")).assertIsSelected()
        list.performScrollToNode(hasTestTag("day-head")); ui.onNodeWithTag("day-record:2").assertExists()
        // Full calendar: the existing month grid with day / week / month / year; back to the strip; the choice survives recreation.
        list.performScrollToNode(hasTestTag("calendar-full"))
        ui.onNodeWithTag("calendar-full").assertTextEquals(s(R.string.full_calendar)).performClick(); ui.waitForIdle()
        ui.onNodeWithTag("calendar-card").assertExists(); ui.onNodeWithTag("recent-card").assertDoesNotExist()
        listOf(R.string.view_day, R.string.view_week, R.string.view_month, R.string.view_year).forEach { ui.onAllNodesWithText(s(it)).onFirst().assertExists() }
        restore.emulateSavedInstanceStateRestore(); ui.waitForIdle()
        ui.onNodeWithTag("calendar-card").assertExists()
        list.performScrollToNode(hasTestTag("calendar-recent")); ui.onNodeWithTag("calendar-recent").performClick(); ui.waitForIdle()
        ui.onNodeWithTag("recent-card").assertExists()
        restore.emulateSavedInstanceStateRestore(); ui.waitForIdle()
        ui.onNodeWithTag("recent-card").assertExists()
    }
}

/**
 * UI-07 D undo on the real AppShell and an in-memory database: "Logged · Undo" leaves the database, the stock and today's
 * progress exactly as deleting the same dose from the intake history does.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class)
class Ui07UndoShellTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ui.activity
    private lateinit var db: NotesDatabase
    private lateinit var repo: NotesRepository
    private lateinit var model: NotesViewModel
    private fun s(id: Int) = context.getString(id)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        repo = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = db })
        model = NotesViewModel(repo, ReminderCoordinator(context, repo), context)
        UiPrefs(context).disclaimerAccepted = true
    }
    @After fun close() { androidx.lifecycle.ViewModelStore().apply { put("model", model); clear() }; db.close() }

    private data class Snapshot(val records: List<RecordEntity>, val containers: List<ContainerEntity>, val trash: Int, val progress: String)
    private fun snapshot(): Snapshot {
        ui.waitForIdle()
        val progress = ui.onAllNodesWithTag("today-progress").fetchSemanticsNodes().firstOrNull()?.config?.get(SemanticsProperties.Text)?.joinToString() ?: ""
        return Snapshot(runBlocking { db.dao().records() + db.dao().deletedRecords() }.sortedBy { it.id }, runBlocking { db.dao().containers() }.sortedBy { it.id },
            trashCount(), progress)
    }
    private fun trashCount(): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM trash_item WHERE kind='RECORD'").use { it.moveToFirst(); it.getInt(0) }
    private fun openSlot(): Slot = runBlocking { repo.calendar(Instant.now()) }.first { it.state in OPEN_STATES && it.slot.at.atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now() }.slot
    private fun log() { val slot = openSlot(); model.complete(slot, Instant.now(), 2.0); ui.waitUntil(10_000) { runBlocking { db.dao().records() }.any { it.slot_key == slot.key && it.status != "MISSED" } } }

    @Test fun undoEqualsDeletingFromTheHistory() {
        val nowLocal = LocalTime.now(); val at = if (nowLocal.isBefore(LocalTime.of(0, 2))) nowLocal.plusMinutes(1) else nowLocal.minusMinutes(1)
        runBlocking {
            val m = MedicationEntity(name = "药物 A", molecule = "E2", route = "ORAL", unit = "MG", dose_per_intake = 2.0, container_capacity = 40.0,
                soon_alert_minutes = 15, late_after_minutes = 600, site_rotation = false, notifications_on = false, active = true, sort_order = 0)
            val id = repo.saveMedication(m, "E2", RuleKind.EVERY_N_DAYS, 1, listOf(at.withSecond(0).withNano(0)), emptySet(), Instant.now().minusSeconds(3600 * 3))
            repo.addContainers(id, 40.0, 1, true)
        }
        model.sync()
        ui.setContent { androidx.compose.material3.MaterialTheme { NotesApp(model, UiPrefs(context).appearance, {}) } }
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("today-card").fetchSemanticsNodes().isNotEmpty() }
        val before = snapshot()
        // A: log, then Undo on the today card.
        log(); ui.waitUntil(10_000) { ui.onAllNodesWithTag("today-undo").fetchSemanticsNodes().isNotEmpty() }
        val a = runBlocking { db.dao().records() }.single { it.status != "MISSED" }
        ui.onNodeWithTag("today-undo").performClick()
        ui.waitUntil(10_000) { runBlocking { db.dao().recordById(a.id) }!!.deleted_at_utc != null }
        val afterUndo = snapshot()
        // B: log the same dose again, then delete it from the intake history (menu → Remove → confirm).
        log(); val b = runBlocking { db.dao().records() }.single { it.status != "MISSED" }
        ui.onNodeWithContentDescription(s(R.string.menu)).performClick(); ui.waitForIdle()
        ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(s(Destination.HISTORY.title)))
        ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(s(Destination.HISTORY.title))).performClick(); ui.waitForIdle()
        ui.waitUntil(10_000) { runCatching { ui.onNodeWithTag("history-records").performScrollToNode(hasTestTag("history-record:${b.id}")) }.isSuccess }
        ui.onNodeWithTag("history-record:${b.id}").performClick(); ui.waitForIdle()
        ui.onNode(hasText(s(R.string.remove)) and hasClickAction()).performClick(); ui.waitForIdle()
        ui.onNode(hasAnyAncestor(isDialog()) and hasText(s(R.string.remove)) and hasClickAction()).performClick()
        ui.waitUntil(10_000) { runBlocking { db.dao().recordById(b.id) }!!.deleted_at_utc != null }
        ui.onNodeWithContentDescription(s(R.string.menu)).performClick(); ui.waitForIdle()
        ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(s(Destination.CALENDAR.title)))
        ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(s(Destination.CALENDAR.title))).performClick(); ui.waitForIdle()
        val afterDelete = snapshot()
        // Stock and today's progress are back where they were, both ways.
        assertEquals(before.containers, afterUndo.containers); assertEquals(before.containers, afterDelete.containers)
        assertEquals(before.progress, afterUndo.progress); assertEquals(before.progress, afterDelete.progress)
        // The two deleted records are the same field for field (apart from their own id and deletion time).
        val ra = runBlocking { db.dao().recordById(a.id) }!!; val rb = runBlocking { db.dao().recordById(b.id) }!!
        assertEquals(rb.copy(id = ra.id, deleted_at_utc = ra.deleted_at_utc, taken_utc = ra.taken_utc), ra)
        // Each went to the bin once, as the history delete does; nothing else was written.
        assertEquals(before.trash + 1, afterUndo.trash); assertEquals(before.trash + 2, afterDelete.trash)
        assertEquals(before.records.size + 1, afterUndo.records.size); assertEquals(before.records.size + 2, afterDelete.records.size)
    }
}
