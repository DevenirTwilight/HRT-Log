package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

/** UI-01 D: one main button per stock card, every other existing action in its ⋮ menu, packages collapsed by default. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "w411dp-h891dp")
class StockCardActionsTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 3, 10)
    private val med = MedicationEntity(1, "Synthetic E2", "E2", "ORAL", "MG", 2.0, 28.0, null, 15, 60, false, null, true, true, 0)
    private val open = ContainerEntity(1, 1, 28.0, 0.0, 10.0, "2026-03-01", "IN_USE")
    private val sealed = ContainerEntity(2, 1, 28.0, 0.0, 0.0, null, "SEALED")
    private val calls = mutableListOf<String>()
    private fun s(id: Int, vararg a: Any) = ui.activity.getString(id, *a)
    private fun show(boxes: List<ContainerEntity>, forecast: StockForecast? = null) {
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            StockScreen(NotesState(listOf(med), loading = false), boxes, emptyList(), { calls += "replace" }, { calls += "check" }, { calls += "add" }, PaddingValues(),
                onManage = { calls += "manage" }, forecasts = forecast?.let { mapOf(1L to it) } ?: emptyMap(), today = today, zone = zone)
        } } }
        ui.waitForIdle()
    }
    private fun card() = hasAnyAncestor(hasTestTag("stock-card-1"))
    /** Buttons drawn on the card itself (not menu items, not the ⋮ icon, not the section toggle). */
    private fun cardButtons() = ui.onAllNodes(card() and SemanticsMatcher("button") { it.config.getOrNull(SemanticsProperties.Role) == Role.Button } and hasClickAction() and
        hasTestTag("stock-primary-1")).fetchSemanticsNodes()
    private fun menuItems(): List<String> { ui.onNodeWithTag("stock-more-1").performClick(); ui.waitForIdle()
        val tags = listOf("stock-replace-1", "stock-check-1", "stock-add-1").filter { ui.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }
        ui.activity.runOnUiThread { }; return tags }

    @Test fun sealedPackageMakesOpeningTheNextOneTheMainAction() {
        show(listOf(open, sealed))
        ui.onNodeWithTag("stock-primary-1").assertTextEquals(s(R.string.stock_replace)).performClick()
        assertEquals(listOf("replace"), calls)
        assertEquals(1, cardButtons().size)
        assertEquals(listOf("stock-check-1", "stock-add-1"), menuItems())
        ui.onNodeWithTag("stock-add-1").performClick(); assertEquals(listOf("replace", "add"), calls)
        ui.onNodeWithTag("stock-more-1").performClick(); ui.onNodeWithTag("stock-check-1").performClick()
        assertEquals(listOf("replace", "add", "check"), calls)
    }

    @Test fun withoutSealedPackagesAddingStockIsTheMainAction() {
        show(listOf(open))
        ui.onNodeWithTag("stock-primary-1").assertTextEquals(s(R.string.stock_add)).performClick()
        assertEquals(listOf("add"), calls)
        // Everything the old button row offered is still in the menu.
        assertEquals(listOf("stock-replace-1", "stock-check-1"), menuItems())
        ui.onNodeWithTag("stock-replace-1").performClick(); assertEquals(listOf("add", "replace"), calls)
    }

    @Test fun packageRecordsAreCollapsedAndStillOpenTheSameDialog() {
        show(listOf(open, sealed))
        ui.onNodeWithTag("stock-packages-1").assertTextContains(s(R.string.stock_packages_section, 2))
        ui.onNodeWithTag("stock-manage-1").assertDoesNotExist()
        ui.onNodeWithTag("stock-packages-1").performClick()
        ui.onNodeWithTag("stock-manage-1").performClick()
        assertEquals(listOf("manage"), calls)
        ui.onNodeWithTag("stock-packages-1").performClick()
        ui.onNodeWithTag("stock-manage-1").assertDoesNotExist()
    }

    @Test fun lowStockSaysTheDaysInWords() {
        val at = { n: Long -> today.plusDays(n).atTime(8, 0).atZone(zone).toInstant() }
        show(listOf(open), StockForecast(1, 10.0, 8, at(3), at(4)))
        ui.onNodeWithText(s(R.string.stock_runs_out_in, 4)).assertExists()
    }

    @Test fun untrackedHasOneSentenceAndASecondaryStartButton() {
        show(emptyList())
        ui.onNodeWithText(s(R.string.stock_not_tracked)).assertExists()
        ui.onNodeWithTag("stock-primary-1").assertTextEquals(s(R.string.stock_start)).performClick()
        assertEquals(listOf("add"), calls)
        ui.onNodeWithTag("stock-more-1").assertDoesNotExist()
    }
}
