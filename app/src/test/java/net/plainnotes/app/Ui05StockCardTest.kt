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
 * UI-05 C, synthetic data on the real StockScreen: a route · schedule line under the name (the existing scheduleText and
 * route label, nothing recomputed), "about n days" next to the remaining amount from the forecast already computed (none
 * without a plan), and the forecast sentence as small text under the progress bar. Actions stay as StockCardActionsTest checks.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "w411dp-h891dp")
class Ui05StockCardTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 3, 10)
    private fun day(n: Long) = today.plusDays(n).atTime(8, 0).atZone(zone).toInstant()
    private val med = MedicationEntity(1, "Synthetic A", "E2", "ORAL", "MG", 2.0, 28.0, null, 15, 60, false, null, true, true, 0)
    private val open = ContainerEntity(1, 1, 28.0, 0.0, 10.0, "2026-03-01", "IN_USE")
    private val schedule = ScheduleSummary(RuleKind.EVERY_N_DAYS, 1, emptySet(), listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
    private fun s(id: Int, vararg a: Any) = ui.activity.getString(id, *a)
    private var expectedSub = ""
    private fun show(forecast: StockForecast) {
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            expectedSub = choiceLabel(med.route!!) + " · " + scheduleText(schedule)
            StockScreen(NotesState(listOf(med), loading = false, schedules = mapOf(1L to schedule)), listOf(open), emptyList(), {}, {}, {}, PaddingValues(),
                forecasts = mapOf(1L to forecast), today = today, zone = zone)
        } } }
        ui.waitForIdle()
    }

    @Test fun routeAndScheduleLineUnderTheName() {
        show(StockForecast(1, 18.0, 30, day(20), day(21)))
        ui.onNodeWithTag("stock-sub-1").assertTextEquals(expectedSub)
    }

    @Test fun aboutDaysFromTheExistingForecastNextToTheAmount() {
        val f = StockForecast(1, 18.0, 30, day(20), day(21))
        show(f)
        ui.onNodeWithTag("stock-days-1").assertTextEquals(s(R.string.stock_about_days, f.daysUntilShort(today, zone)!!))
    }

    @Test fun noPlanMeansNoDays() {
        show(StockForecast(1, 18.0, 0, null, null))
        ui.onNodeWithTag("stock-days-1").assertDoesNotExist()
    }

    @Test fun forecastSentenceIsSmallTextUnderTheProgressBar() {
        show(StockForecast(1, 18.0, 30, day(20), day(21)))
        val bar = ui.onNodeWithTag("stock-bar-1").fetchSemanticsNode().boundsInRoot
        val sentence = ui.onNodeWithTag("stock-forecast-1").fetchSemanticsNode().boundsInRoot
        assertTrue("sentence under the bar", sentence.top >= bar.bottom)
    }
}
