package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

/**
 * UI-05 B, synthetic data on the real CalendarScreen: low stock is a compact strip per medication right under the today
 * card (before the calendar), at most three, then "n more"; the whole strip opens Stock, "Add stock" calls the same onAddStock.
 * The low rule is still StockForecast.isLow (HomeStockCardTest keeps covering it).
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "w411dp-h891dp")
class Ui05HomeStockTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 3, 10)
    private fun day(n: Long) = today.plusDays(n).atTime(8, 0).atZone(zone).toInstant()
    private fun med(id: Long) = MedicationEntity(id, "Synthetic $id", "E2", "ORAL", "MG", 2.0, 30.0, null, 15, 120, false, null, true, true, id.toInt())
    private val low = { id: Long -> StockForecast(id, 6.0, 8, day(2), day(3)) }
    private var stockOpened = 0
    private val added = mutableListOf<Long>()
    private fun s(id: Int, vararg a: Any) = ui.activity.getString(id, *a)
    private fun show(meds: List<MedicationEntity>, forecasts: Map<Long, StockForecast>) {
        val state = NotesState(meds, emptyList(), emptyList(), loading = false, calendarStart = today)
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            CalendarScreen(state, today, {}, {}, {}, {}, PaddingValues(), extra = NotesViewModel.ExtraState(stockForecasts = forecasts),
                onStock = { stockOpened++ }, onAddStock = { added += it.id })
        } } }
        ui.waitForIdle()
    }

    @Test fun oneLowMedicationIsOneStripUnderTheTodayCardBeforeTheCalendar() {
        show(listOf(med(1)), mapOf(1L to low(1)))
        ui.onNode(hasTestTag("home-stock-row:1") and hasAnyDescendant(hasText(s(R.string.home_stock_low, "Synthetic 1")))).assertExists()
        val days = low(1).daysUntilShort(today, zone)!!
        ui.onNode(hasText(s(R.string.stock_runs_out_in, days), substring = true) and hasAnyAncestor(hasTestTag("home-stock-row:1"))).assertExists()
        val today = ui.onNodeWithTag("today-card").fetchSemanticsNode().boundsInRoot
        val strip = ui.onNodeWithTag("home-stock").fetchSemanticsNode().boundsInRoot
        val calendar = ui.onNodeWithTag("calendar-card").fetchSemanticsNode().boundsInRoot
        assertTrue("strip under the today card", strip.top >= today.bottom)
        assertTrue("strip before the calendar", strip.bottom <= calendar.top)
        ui.onNodeWithTag("home-stock-more").assertDoesNotExist()
        ui.onNodeWithText(s(R.string.home_stock_title)).assertDoesNotExist()
    }

    @Test fun theWholeStripOpensStockAndAddStockCallsTheSameCallback() {
        show(listOf(med(1)), mapOf(1L to low(1)))
        ui.onNodeWithTag("home-stock-view").performClick()
        assertEquals(1, stockOpened)
        ui.onNodeWithTag("home-stock-add:1").assertTextEquals(s(R.string.stock_add)).performClick()
        assertEquals(listOf(1L), added); assertEquals("add does not also open Stock", 1, stockOpened)
    }

    @Test fun fourLowMedicationsShowThreeStripsAndOneMore() {
        val meds = (1L..4L).map(::med)
        show(meds, meds.associate { it.id to low(it.id) })
        ui.onAllNodes(hasTestTag("home-stock-view")).assertCountEquals(3)
        ui.onNodeWithTag("home-stock-row:4").assertDoesNotExist()
        ui.onNodeWithTag("home-stock-more").assertTextEquals(s(R.string.home_stock_more, 1)).performClick()
        assertEquals(1, stockOpened)
    }

    @Test fun normalStockShowsNoStrip() {
        show(listOf(med(1)), mapOf(1L to StockForecast(1, 20.0, 9, day(8), null)))
        ui.onNodeWithTag("home-stock").assertDoesNotExist()
    }
}
