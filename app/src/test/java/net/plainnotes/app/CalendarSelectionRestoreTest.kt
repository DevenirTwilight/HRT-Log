package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
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
import java.time.format.DateTimeFormatter

/**
 * B3-C selection rules on the real CalendarScreen with synthetic data: recreation keeps the user's day (historical,
 * across a year boundary, or with no data at all) and its records; a real change of the external start date still wins.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "en-w411dp-h891dp")
class CalendarSelectionRestoreTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ui.activity
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 1, 10)
    private val med = MedicationEntity(1, "Synthetic E2", "E2", "ORAL", "MG", 2.0, 30.0, null, 15, 120, false, null, true, true, 0)
    private fun taken(id: Long, d: LocalDate) = RecordEntity(id, 1, taken_utc = d.atTime(8, 0).atZone(zone).toInstant().toEpochMilli(), taken_zone = zone.id,
        actual_dose = 2.0, status = "ON_TIME", origin = "APP", revision = 1, config_snapshot = MedicationSnapshot.encode(med, null))
    private var start by mutableStateOf(today)
    private val restore by lazy { StateRestorationTester(ui) }

    private fun show(meds: List<MedicationEntity>, records: List<RecordEntity>) = restore.setContent {
        NotesTheme(ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            CalendarScreen(NotesState(meds, calendarStart = start), today, {}, {}, {}, {}, PaddingValues(), extra = NotesViewModel.ExtraState(records = records))
        } }
    }
    private fun name(d: LocalDate) = d.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(context.resources.configuration.locales[0], "yMMMMdEEEE"), context.resources.configuration.locales[0]))
    private fun cell(d: LocalDate) = ui.onNode(hasContentDescription(name(d), substring = true) and hasClickAction())
    private fun selected(d: LocalDate) = cell(d).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true
    private fun recreate() { restore.emulateSavedInstanceStateRestore(); ui.waitForIdle() }

    @Test fun historicalDayAcrossTheYearKeepsItsSelectionAndRecords() {
        val oldDay = LocalDate.of(2025, 12, 30)
        show(listOf(med), listOf(taken(1, oldDay)))
        ui.onNodeWithContentDescription(context.getString(R.string.calendar_previous)).performClick(); ui.waitForIdle()  // back into December 2025
        cell(oldDay).performClick(); ui.waitForIdle()
        assertTrue(selected(oldDay))
        val onDay = context.getString(R.string.status_on_time)
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(onDay)); ui.onAllNodesWithText(onDay).assertCountEquals(1)
        recreate()
        assertTrue("selection survives recreation", selected(oldDay))
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(onDay))
        ui.onAllNodesWithText(onDay).assertCountEquals(1)  // the day's single record, not duplicated
        ui.onNodeWithText(context.getString(R.string.days_ago, 11)).assertExists()
    }

    @Test fun aRealStartDateChangeStillMovesTheSelection() {
        show(listOf(med), emptyList())
        cell(today.plusDays(3)).performClick(); ui.waitForIdle()
        recreate(); assertTrue(selected(today.plusDays(3)))
        start = LocalDate.of(2026, 2, 2); ui.waitForIdle()  // "Show from another date" picks a new start
        assertTrue("external start wins", selected(LocalDate.of(2026, 2, 2)))
        recreate(); assertTrue(selected(LocalDate.of(2026, 2, 2)))
        cell(LocalDate.of(2026, 2, 5)).performClick(); ui.waitForIdle()
        recreate(); assertTrue("a later user choice is not overwritten by the old start", selected(LocalDate.of(2026, 2, 5)))
    }

    @Test fun emptyDataKeepsTheSelectionAndSaysNothingIsRecorded() {
        show(emptyList(), emptyList())
        cell(today.minusDays(4)).performClick(); ui.waitForIdle()
        recreate()
        assertTrue(selected(today.minusDays(4)))
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(context.getString(R.string.calendar_day_empty)))
        ui.onNodeWithText(context.getString(R.string.calendar_day_empty)).assertIsDisplayed()
    }
}
