package net.plainnotes.app.shell

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.R
import net.plainnotes.app.debug.CalendarTestActivity
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * UI-02B safety net on a real device (synthetic data): a reminder notification's slot, handed to the model as
 * MainActivity does, opens the shared intake dialog for that slot and is consumed. Written before the AppShell state split.
 */
class NotificationEntryAndroidTest {
    @get:Rule val ui = createAndroidComposeRule<CalendarTestActivity>()
    private val context get() = ui.activity
    private fun dialogOpen() = ui.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()

    @Test fun notificationSlotOpensTheIntakeDialogOnce() {
        ui.waitUntil(20_000) { context.model.state.value.slots.isNotEmpty() }
        val slot = context.model.state.value.slots.first().slot
        ui.runOnUiThread { context.model.notificationSlot.value = slot }
        ui.waitUntil(10_000) { dialogOpen() }
        ui.onNode(hasText(context.getString(R.string.complete)) and hasAnyAncestor(isDialog())).assertExists()
        assertNull("the slot is consumed", context.model.notificationSlot.value)
        ui.onNode(hasText(context.getString(R.string.cancel)) and hasAnyAncestor(isDialog())).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        ui.waitUntil(10_000) { !dialogOpen() }
        val before = runBlocking { context.repository.records() }.count { it.status != "MISSED" }
        ui.waitForIdle()
        org.junit.Assert.assertEquals("cancel wrote nothing", before, runBlocking { context.repository.records() }.count { it.status != "MISSED" })
    }
}
