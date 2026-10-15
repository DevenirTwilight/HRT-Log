package net.plainnotes.app.shell

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.R
import net.plainnotes.app.debug.CalendarTestActivity
import net.plainnotes.app.ui.UiPrefs
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * UI-02B safety net on a real device (synthetic data): after an intake is saved from the shared intake dialog, the
 * well-being prompt snackbar appears on the calendar and its action opens the Well-being page. Written and run before the
 * AppShell state split (the text-field intake dialog cannot idle under Robolectric).
 */
class AfterIntakePromptAndroidTest {
    @get:Rule val ui = createAndroidComposeRule<CalendarTestActivity>()
    private val context get() = ui.activity
    private fun s(id: Int) = context.getString(id)
    private fun dialogOpen() = ui.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()

    @Test fun savingAnIntakeOffersTheWellbeingCheckInAndItsActionOpensThePage() {
        assumeTrue("the prompt setting is on by default", UiPrefs(context).wellbeingPrompt)
        ui.waitUntil(20_000) { context.model.state.value.slots.isNotEmpty() }
        val taken = runBlocking { context.repository.records() }.count { it.status != "MISSED" }
        ui.runOnUiThread { context.model.notificationSlot.value = context.model.state.value.slots.first().slot }
        ui.waitUntil(10_000) { dialogOpen() }
        ui.onNode(hasText(s(R.string.complete)) and hasAnyAncestor(isDialog())).performSemanticsAction(SemanticsActions.OnClick)
        ui.waitUntil(10_000) { !dialogOpen() }
        ui.waitUntil(10_000) { runBlocking { context.repository.records() }.count { it.status != "MISSED" } == taken + 1 }
        ui.waitUntil(10_000) { ui.onAllNodesWithText(s(R.string.wb_prompt)).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(s(R.string.wb_prompt_action)).performSemanticsAction(SemanticsActions.OnClick)
        ui.waitUntil(10_000) { ui.onAllNodesWithContentDescription(s(R.string.calendar_pick_start)).fetchSemanticsNodes().isEmpty() }
        assertTrue("on the Well-being page", ui.onAllNodesWithText(s(R.string.wellbeing)).fetchSemanticsNodes().isNotEmpty())
    }
}
