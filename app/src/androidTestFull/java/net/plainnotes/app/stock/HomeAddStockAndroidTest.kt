package net.plainnotes.app.stock

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.R
import net.plainnotes.app.debug.CalendarTestActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * UI-02 A1 on a real device with synthetic data: the calendar's low-stock "Add stock" opens AppShell's existing
 * AddStockDialog, and saving it stores exactly what the same dialog stores from the stock page. (Robolectric cannot
 * idle this text-field dialog; see CalendarIntakeAndroidTest.)
 */
class HomeAddStockAndroidTest {
    @get:Rule val ui = createAndroidComposeRule<CalendarTestActivity>()
    private val context get() = ui.activity
    private fun s(id: Int) = context.getString(id)
    private fun containers() = runBlocking { context.repository.containers() }
    private fun dialogOpen() = ui.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()
    private fun saveDialog() {
        ui.waitUntil(10_000) { dialogOpen() }
        ui.onNode(hasText(s(R.string.stock_add)) and hasAnyAncestor(isDialog())).assertExists()
        ui.onNode(hasText(s(R.string.save)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        ui.waitUntil(10_000) { !dialogOpen() }
    }

    @Test fun homeAddStockMatchesTheStockPage() {
        val id = runBlocking { context.repository.medications() }.single().id
        // One opened package with a single 2 mg dose left: low within the shared 7-day window.
        runBlocking { context.repository.addContainers(id, 2.0, 1, true) }
        context.model.sync()
        ui.waitUntil(20_000) { runCatching { ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("home-stock-add:$id")) }.isSuccess }
        val before = containers().size
        ui.onNodeWithTag("home-stock-add:$id").performClick()
        saveDialog()
        ui.waitUntil(10_000) { containers().size == before + 1 }
        val fromHome = containers().maxBy { it.id }
        ui.onNodeWithContentDescription(s(R.string.menu)).performClick()
        ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(s(R.string.stock)))
        ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(s(R.string.stock))).performClick(); ui.waitForIdle()
        // The package just added is sealed, so the card's main action is now "open the next one"; "Add stock" is in ⋮.
        ui.onNodeWithTag("stock-primary-$id").performScrollTo().assertTextEquals(s(R.string.stock_replace))
        ui.onNodeWithTag("stock-more-$id").performClick(); ui.onNodeWithTag("stock-add-$id").performClick()
        saveDialog()
        ui.waitUntil(10_000) { containers().size == before + 2 }
        val fromStock = containers().maxBy { it.id }
        assertEquals("same dialog, same write", fromStock.copy(id = 0), fromHome.copy(id = 0))
    }
}
