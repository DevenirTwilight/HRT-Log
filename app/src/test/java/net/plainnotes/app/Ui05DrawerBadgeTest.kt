package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
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

/**
 * UI-05 E: the drawer's Stock entry carries a count of low-stock medications, the same set the home strips show (one
 * helper, StockForecast.isLow), with a spoken description; nothing at zero.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class)
class Ui05DrawerBadgeTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 3, 10)
    private fun day(n: Long) = today.plusDays(n).atTime(8, 0).atZone(zone).toInstant()
    private fun med(id: Long) = MedicationEntity(id, "Synthetic $id", "E2", "ORAL", "MG", 2.0, 30.0, null, 15, 120, false, null, true, true, id.toInt())
    private fun s(id: Int, vararg a: Any) = ui.activity.getString(id, *a)

    @Test fun badgeShowsTheCountWithADescription() {
        ui.setContent { MaterialTheme { DrawerContent(Destination.CALENDAR, stockLow = 2) {} } }
        ui.onNode(hasTestTag("drawer-stock-badge"), useUnmergedTree = true).assertExists()
        ui.onNode(hasContentDescription(s(R.string.drawer_stock_badge, 2)), useUnmergedTree = true).assertExists()
        ui.onNode(hasText("2") and hasAnyAncestor(hasTestTag("drawer-stock-badge")), useUnmergedTree = true).assertExists()
    }

    @Test fun noBadgeAtZero() {
        ui.setContent { MaterialTheme { DrawerContent(Destination.CALENDAR, stockLow = 0) {} } }
        ui.onNode(hasTestTag("drawer-stock-badge"), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun countIsTheHomeStripSet() {
        val meds = (1L..4L).map(::med)
        val forecasts = mapOf(1L to StockForecast(1, 6.0, 8, day(2), day(3)), 2L to StockForecast(2, 0.0, 8, null, day(0)),
            3L to StockForecast(3, 20.0, 9, day(8), null), 4L to StockForecast(4, 6.0, 8, day(1), day(2)))
        val low = lowStockMedications(meds, forecasts, today, zone)
        assertEquals(listOf(1L, 2L, 4L), low.map { it.id })
        ui.setContent { MaterialTheme { CalendarScreen(NotesState(meds, loading = false, calendarStart = today), today, {}, {}, {}, {}, PaddingValues(),
            extra = NotesViewModel.ExtraState(stockForecasts = forecasts)) } }
        ui.onAllNodes(hasTestTag("home-stock-view")).assertCountEquals(low.size)
    }
}
