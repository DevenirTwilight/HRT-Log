package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RuleKind
import net.plainnotes.app.reminder.ReminderCoordinator
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.time.format.DateTimeFormatter

/**
 * B3-C (roadmap R-24), synthetic data on the real AppShell and calendar: pick a day and see its doses, keep the selected
 * day and page across state restoration, and keep dates and main actions reachable at 320/411dp, normal and 2× font,
 * light and dark, in four languages. Intake-dialog cancel/Back/no-duplicate runs on device (CalendarIntakeAndroidTest).
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class CalendarCoreFlowTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ui.activity
    private lateinit var db: NotesDatabase
    private lateinit var repo: NotesRepository
    private lateinit var model: NotesViewModel
    private val tomorrow = LocalDate.now().plusDays(1)
    private val medName = "Synthetic Estradiol"

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        repo = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = db })
        model = NotesViewModel(repo, ReminderCoordinator(context, repo), context)
        UiPrefs(context).disclaimerAccepted = true
        runBlocking {
            val m = MedicationEntity(name = medName, molecule = "E2", route = "ORAL", unit = "MG", dose_per_intake = 2.0, container_capacity = 40.0,
                soon_alert_minutes = 15, late_after_minutes = 60, site_rotation = false, notifications_on = false, active = true, sort_order = 0)
            repo.saveMedication(m, "E2", RuleKind.EVERY_N_DAYS, 1, listOf(LocalTime.of(8, 0)), emptySet(), Instant.now().minusSeconds(3 * 86400))
        }
        model.sync()
        ui.runOnUiThread { context.onBackPressedDispatcher.addCallback(context, object : androidx.activity.OnBackPressedCallback(true) { override fun handleOnBackPressed() {} }) }
    }
    @After fun close() { androidx.lifecycle.ViewModelStore().apply { put("model", model); clear() }; db.close() }

    private fun s(id: Int) = context.getString(id)
    private fun records() = runBlocking { db.dao().records() }
    /** User-written intakes only; sync may add MISSED rows for past slots on its own. */
    private fun intakes() = records().filter { it.status != "MISSED" && it.deleted_at_utc == null }
    private fun fullDate(d: LocalDate): String {
        val locale = context.resources.configuration.locales[0]
        return d.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMMdEEEE"), locale))
    }
    private fun cell(d: LocalDate) = ui.onNode(hasContentDescription(fullDate(d), substring = true) and hasClickAction())
    private val dateCell = hasClickAction() and SemanticsMatcher("date cell") { n -> n.config.getOrNull(SemanticsProperties.Role) == Role.Button && n.config.contains(SemanticsProperties.Selected) && n.config.contains(SemanticsProperties.ContentDescription) }
    private fun selectTomorrow() {
        // UI-06: the "no entry yet" card (this fixture's past slots are unconfirmed) sits under the today card, so on a
        // 568dp screen the month grid is not composed until scrolled to; scroll to it as the user does.
        ui.waitUntil(10_000) { runCatching { dayList.performScrollToNode(dateCell) }.isSuccess }
        if (ui.onAllNodes(hasContentDescription(fullDate(tomorrow), substring = true) and hasClickAction()).fetchSemanticsNodes().isEmpty())
            ui.onNodeWithContentDescription(s(R.string.calendar_next)).performClick()
        // UI-04: the today card now also lists today's records, so on a 568dp screen the grid row starts below the fold.
        // Scroll to the cell as the user does, then activate it through its click action, as CalendarIntakeAndroidTest
        // does: scrolled to the bottom edge, the floating Add button covers the right-hand cells, so a tap would hit it.
        dayList.performScrollToNode(hasContentDescription(fullDate(tomorrow), substring = true))
        cell(tomorrow).performSemanticsAction(SemanticsActions.OnClick); ui.waitForIdle()
    }
    private fun completeButton() = ui.onAllNodes(hasText(s(R.string.complete)) and hasClickAction()).onFirst()
    private val dayList get() = ui.onNode(hasScrollToIndexAction())
    private fun awaitDose() = ui.waitUntil(15_000) { runCatching { dayList.performScrollToNode(hasText(s(R.string.complete))) }.isSuccess }
    private fun show() { val a = UiPrefs(context).appearance; ui.setContent { NotesTheme(ThemeMode.LIGHT) { NotesApp(model, a, {}) } }; ui.waitForIdle() }
    private fun back() { ui.runOnUiThread { context.onBackPressedDispatcher.onBackPressed() }; ui.waitForIdle() }

    @Test fun pickingADayShowsItsDoses() {
        show(); selectTomorrow()
        assertEquals(true, cell(tomorrow).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        // LazyColumn need not compose the day header below the calendar grid yet.
        // Bring each checked list item into view, as the user does on a short viewport.
        dayList.performScrollToNode(hasText(s(R.string.tomorrow)))
        ui.onNodeWithText(s(R.string.tomorrow)).assertExists()
        awaitDose(); completeButton().assertIsDisplayed()
        dayList.performScrollToNode(hasText(medName))
        ui.onAllNodesWithText(medName).onFirst().assertIsDisplayed()
    }

    @Test fun selectedDayAndPageSurviveStateRestoration() {
        val restore = StateRestorationTester(ui)
        val a = UiPrefs(context).appearance
        restore.setContent { NotesTheme(ThemeMode.LIGHT) { NotesApp(model, a, {}) } }
        selectTomorrow()
        restore.emulateSavedInstanceStateRestore(); ui.waitForIdle()
        assertEquals(true, cell(tomorrow).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        ui.onNodeWithContentDescription(s(R.string.menu)).performClick()
        ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(s(R.string.stock))).performClick(); ui.waitForIdle()
        restore.emulateSavedInstanceStateRestore(); ui.waitForIdle()
        ui.onNodeWithContentDescription(s(R.string.calendar_pick_start)).assertDoesNotExist()
        back()
        ui.onNodeWithContentDescription(s(R.string.calendar_pick_start)).assertExists()
    }

    /** Dates stay separate on-screen buttons and the main actions stay reachable for every theme and font scale. */
    private fun layoutHolds() {
        var dark by mutableStateOf(false); var scale by mutableStateOf(1f)
        val a = UiPrefs(context).appearance
        ui.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) { NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { NotesApp(model, a, {}) } }
        }
        selectTomorrow(); awaitDose()
        val before = intakes()
        val width = ui.onRoot().fetchSemanticsNode().size.width
        for ((d, f) in listOf(false to 1f, false to 2f, true to 1f, true to 2f)) {
            dark = d; scale = f; ui.waitForIdle()
            dayList.performScrollToIndex(0); dayList.performScrollToNode(dateCell)   // UI-06: the grid may start below the card
            val cells = ui.onAllNodes(dateCell).fetchSemanticsNodes()
            assertTrue("dark=$d scale=$f: ${cells.size}", cells.size in setOf(7, 35, 42))
            cells.forEach { n -> val x = n.positionInRoot.x; assertTrue("dark=$d scale=$f cell outside the width: $x+${n.size.width}/$width", x >= 0f && x + n.size.width <= width + 0.5f && n.size.height > 0) }
            ui.onNodeWithTag("calendar-add").assertIsDisplayed().assertHasClickAction()
            dayList.performScrollToNode(hasText(s(R.string.complete))); completeButton().assertIsDisplayed().assertHasClickAction()
            dayList.performScrollToNode(hasContentDescription(fullDate(tomorrow), substring = true)); cell(tomorrow).assertIsDisplayed()
        }
        assertEquals(before, intakes())
    }
    @Test @Config(qualifiers = "en-w320dp-h568dp") fun layoutEnglish320() = layoutHolds()
    @Test @Config(qualifiers = "en-w411dp-h891dp") fun layoutEnglish411() = layoutHolds()
    @Test @Config(qualifiers = "zh-rCN-w320dp-h568dp") fun layoutSimplified320() = layoutHolds()
    @Test @Config(qualifiers = "zh-rCN-w411dp-h891dp") fun layoutSimplified411() = layoutHolds()
    @Test @Config(qualifiers = "zh-rTW-w320dp-h568dp") fun layoutTraditional320() = layoutHolds()
    @Test @Config(qualifiers = "zh-rTW-w411dp-h891dp") fun layoutTraditional411() = layoutHolds()
    @Test @Config(qualifiers = "fr-w320dp-h568dp") fun layoutFrench320() = layoutHolds()
    @Test @Config(qualifiers = "fr-w411dp-h891dp") fun layoutFrench411() = layoutHolds()
}
