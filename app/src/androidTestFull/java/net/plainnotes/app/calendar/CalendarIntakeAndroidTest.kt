package net.plainnotes.app.calendar

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.R
import net.plainnotes.app.debug.CalendarTestActivity
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import java.time.*
import java.time.format.DateTimeFormatter

/**
 * B3-C (R-24) on a real device with synthetic data: the intake dialog opened from a calendar day writes nothing when
 * cancelled or closed with the system Back key, and one save followed by Back navigation leaves exactly one intake.
 * (Robolectric cannot idle this text-field dialog, so these interactions run here; host: debug CalendarTestActivity.)
 */
class CalendarIntakeAndroidTest {
    @get:Rule val ui = createAndroidComposeRule<CalendarTestActivity>()
    private val context get() = ui.activity
    private val tomorrow = LocalDate.now().plusDays(1)

    @Before fun open() {
        selectTomorrow()
    }

    private fun s(id: Int) = context.getString(id)
    private fun intakes() = runBlocking { context.repository.records() }.filter { it.status != "MISSED" && it.deleted_at_utc == null }
    private fun cell(d: LocalDate): SemanticsNodeInteraction {
        val locale = context.resources.configuration.locales[0]
        val name = d.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMMdEEEE"), locale))
        return ui.onNode(hasContentDescription(name, substring = true) and hasClickAction())
    }
    private fun selectTomorrow() {
        // Wait for the synthetic medication to load: the "Nothing planned yet" card above the calendar is shown only until
        // then, and removing it moves the grid. (Today's dose card may already be gone, so the name is no signal.)
        ui.waitUntil(20_000) {
            ui.onAllNodes(hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).fetchSemanticsNodes().size >= 7 &&
                ui.onAllNodesWithText(s(R.string.empty_title)).fetchSemanticsNodes().isEmpty()
        }
        ui.waitForIdle()
        if (runCatching { cell(tomorrow).fetchSemanticsNode() }.isFailure) ui.onNodeWithContentDescription(s(R.string.calendar_next)).performClick()
        cell(tomorrow).performClick()
        ui.waitUntil(10_000) { cell(tomorrow).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true }
        assertEquals(true, cell(tomorrow).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        ui.waitUntil(20_000) { runCatching { ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(s(R.string.complete))) }.isSuccess }
    }
    private fun complete() = ui.onAllNodes(hasText(s(R.string.complete)) and hasClickAction()).onFirst()
    private fun dialogOpen() = ui.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()
    private fun activityFocused(): Boolean { var focused = false; InstrumentationRegistry.getInstrumentation().runOnMainSync { focused = context.hasWindowFocus() }; return focused }
    /** Opens the intake dialog and waits until its window, not the activity, has input focus, so a real key reaches it. */
    private fun openIntake() { complete().performClick(); ui.waitUntil(10_000) { dialogOpen() && !activityFocused() } }
    /** A real system BACK key. If the soft keyboard is up, Android's first Back only hides it; a second press closes the dialog. */
    private fun systemBack() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        runCatching { ui.waitUntil(3_000) { !dialogOpen() } }.onFailure { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK) }
        ui.waitUntil(10_000) { !dialogOpen() }
    }
    /** A real system BACK key while the activity window has focus. */
    private fun activityBack() { ui.waitUntil(10_000) { activityFocused() }; InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); ui.waitForIdle() }

    @Test fun cancelAndSystemBackOnTheIntakeDialogWriteNothing() {
        val before = intakes()
        openIntake()
        ui.onNodeWithText(s(R.string.cancel)).performClick(); ui.waitUntil(10_000) { !dialogOpen() }
        assertEquals(before, intakes())
        openIntake()
        systemBack()
        ui.onNodeWithContentDescription(s(R.string.calendar_pick_start)).assertExists()  // Back closed only the dialog
        assertEquals("the key reached the dialog, not the activity", 0, context.activityBacks)
        assertEquals(before, intakes())
        complete().assertIsDisplayed().assertHasClickAction()  // the calendar is still usable
    }

    @Test fun oneSaveThenBackNavigationLeavesExactlyOneIntake() {
        val before = intakes().size
        openIntake()
        ui.onNodeWithText(s(R.string.save)).performClick(); ui.waitUntil(10_000) { !dialogOpen() }
        ui.waitUntil(20_000) { intakes().size == before + 1 }
        val saved = intakes().last()
        assertTrue("a taken intake of the synthetic dose", saved.status in setOf("ON_TIME", "LATE") && saved.actual_dose == 2.0 && saved.taken_utc != null)
        repeat(2) { activityBack() }
        assertEquals("Back on the calendar goes to the activity", 2, context.activityBacks)
        ui.onNodeWithContentDescription(s(R.string.menu)).performClick()
        ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(s(R.string.history))).performClick()
        ui.waitUntil(10_000) { runCatching { ui.onNodeWithTag("drawer-content").assertIsNotDisplayed() }.isSuccess }
        ui.onNodeWithContentDescription(s(R.string.calendar_pick_start)).assertDoesNotExist()  // on History
        activityBack()
        ui.onNodeWithContentDescription(s(R.string.calendar_pick_start)).assertExists()  // Back from History lands on the calendar
        assertEquals(2, context.activityBacks)
        activityBack()
        assertEquals("Back on the calendar reaches the activity; Back from History does not", 3, context.activityBacks)
        assertEquals(before + 1, intakes().size)
    }
}
