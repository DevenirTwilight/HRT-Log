package net.plainnotes.app.calendar

import android.view.KeyEvent
import androidx.activity.OnBackPressedCallback
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
        ui.runOnUiThread { context.onBackPressedDispatcher.addCallback(context, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} }) }
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
        ui.waitUntil(20_000) { ui.onAllNodes(hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).fetchSemanticsNodes().size >= 7 }
        if (runCatching { cell(tomorrow).fetchSemanticsNode() }.isFailure) ui.onNodeWithContentDescription(s(R.string.calendar_next)).performClick()
        cell(tomorrow).performClick()
        ui.waitForIdle()
        assertEquals(true, cell(tomorrow).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        ui.waitUntil(20_000) { runCatching { ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(s(R.string.complete))) }.isSuccess }
    }
    private fun complete() = ui.onAllNodes(hasText(s(R.string.complete)) and hasClickAction()).onFirst()
    private fun dialogOpen() = ui.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()

    @Test fun cancelAndSystemBackOnTheIntakeDialogWriteNothing() {
        val before = intakes()
        complete().performClick(); ui.waitUntil(10_000) { dialogOpen() }
        ui.onNodeWithText(s(R.string.cancel)).performClick(); ui.waitUntil(10_000) { !dialogOpen() }
        complete().performClick(); ui.waitUntil(10_000) { dialogOpen() }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); ui.waitUntil(10_000) { !dialogOpen() }
        ui.onNodeWithContentDescription(s(R.string.calendar_pick_start)).assertExists()  // Back closed only the dialog
        assertEquals(before, intakes())
    }

    @Test fun oneSaveThenBackNavigationLeavesExactlyOneIntake() {
        val before = intakes().size
        complete().performClick(); ui.waitUntil(10_000) { dialogOpen() }
        ui.onNodeWithText(s(R.string.save)).performClick(); ui.waitUntil(10_000) { !dialogOpen() }
        ui.waitUntil(20_000) { intakes().size == before + 1 }
        repeat(2) { ui.runOnUiThread { context.onBackPressedDispatcher.onBackPressed() }; ui.waitForIdle() }
        ui.onNodeWithContentDescription(s(R.string.menu)).performClick()
        ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(s(R.string.history))).performClick(); ui.waitForIdle()
        repeat(2) { ui.runOnUiThread { context.onBackPressedDispatcher.onBackPressed() }; ui.waitForIdle() }
        ui.onNodeWithContentDescription(s(R.string.calendar_pick_start)).assertExists()
        assertEquals(before + 1, intakes().size)
    }
}
