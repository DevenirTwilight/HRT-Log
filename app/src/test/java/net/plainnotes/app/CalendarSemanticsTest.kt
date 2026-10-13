package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
import java.time.format.DateTimeFormatter

/**
 * B3-B (roadmap R-13), synthetic data on the real CalendarScreen: every date is its own clickable node whose spoken name
 * carries the full date, "today", the dose/appointment/stock state in words (not only colour), and the selected state;
 * the decorative digit and dots are not read separately; the grid is not merged into one node.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "w411dp-h891dp")
class CalendarSemanticsTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ui.activity
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 3, 10)
    private fun at(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(zone).toInstant()
    private val med = MedicationEntity(1, "Synthetic E2", "E2", "ORAL", "MG", 2.0, 30.0, null, 15, 120, false, null, true, true, 0)
    private fun record(id: Long, d: LocalDate, status: String) = RecordEntity(id, 1, scheduled_utc = at(d, 8).toEpochMilli(),
        taken_utc = if (status == "MISSED") null else at(d, 8).toEpochMilli(), taken_zone = zone.id, actual_dose = 2.0, status = status, origin = "APP", revision = 1, config_snapshot = "{}")
    private fun open(d: LocalDate, h: Int) = TimelineEntry(Slot("wall:1@${at(d, h)}", 1, 1, at(d, h), at(d, h), zone, 2.0, 15, 120, at(d, h).minusSeconds(86400 * 30)), SlotState.PENDING)

    private fun show(view: CalView = CalView.MONTH) {
        val records = listOf(record(1, today.minusDays(1), "ON_TIME"), record(2, today.minusDays(1), "MISSED"),
            record(3, today.minusDays(2), "MISSED"), record(4, today.minusDays(3), "ON_TIME"))
        val upcoming = listOf(open(today.plusDays(1), 8), open(today.plusDays(1), 20))
        val appt = AppointmentEntity(1, "ENDO", at(today.plusDays(2), 10).toEpochMilli(), zone.id, null, "Synthetic clinic", null, 60)
        val state = NotesState(listOf(med), emptyList(), listOf(appt), calendarStart = today)
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            CalendarScreen(state, today, {}, {}, {}, {}, PaddingValues(), extra = NotesViewModel.ExtraState(records = records, upcoming = upcoming), initialView = view)
        } } }
        ui.waitForIdle()
    }
    private fun fullDate(d: LocalDate): String {
        val locale = context.resources.configuration.locales[0]
        return d.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMMdEEEE"), locale))
    }
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun dayNode(d: LocalDate) = ui.onNode(hasContentDescription(fullDate(d), substring = true) and hasClickAction())
    private fun description(d: LocalDate) = dayNode(d).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString(" ")
    private fun selected(d: LocalDate) = dayNode(d).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)

    private val dateCell = hasClickAction() and SemanticsMatcher("date cell") { n -> n.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.isNotEmpty() } == true && n.config.getOrNull(SemanticsProperties.Role) == Role.Button && n.config.contains(SemanticsProperties.Selected) }

    private fun monthGridSpeaksEachDay() {
        show()
        // One node per visible date (5 or 6 weeks), each its own clickable button; the grid is not merged into one node.
        val days = ui.onAllNodes(dateCell)
        val count = days.fetchSemanticsNodes().size
        assertTrue("one node per date, got $count", count == 35 || count == 42)

        val todayText = description(today)
        assertTrue(todayText, todayText.contains(s(R.string.today)))
        assertEquals(true, selected(today)); assertEquals(false, selected(today.minusDays(1)))
        assertTrue(description(today.minusDays(1)).contains(s(R.string.legend_partial)))
        assertTrue(description(today.minusDays(2)).contains(s(R.string.legend_missed)))
        assertTrue(description(today.minusDays(3)).contains(s(R.string.legend_taken)))
        assertTrue(description(today.plusDays(1)).contains(s(R.string.calendar_a11y_planned, 2)))
        assertTrue(description(today.plusDays(2)).contains(s(R.string.calendar_a11y_appointments, 1)))
        assertFalse("Only the selected date says today", description(today.plusDays(1)).contains(s(R.string.today)))

        // The day number and status dots are decorative inside the cell and not read again.
        ui.onAllNodesWithText("10").assertCountEquals(0)

        // Clicking a date selects it: the selection moves and the day list follows.
        dayNode(today.plusDays(1)).assertHasClickAction().performClick(); ui.waitForIdle()
        assertEquals(true, selected(today.plusDays(1))); assertEquals(false, selected(today))
        ui.onNodeWithText(s(R.string.tomorrow)).assertExists()
    }

    @Test @Config(qualifiers = "+en") fun monthGridEnglish() = monthGridSpeaksEachDay()
    @Test @Config(qualifiers = "+zh-rCN") fun monthGridSimplifiedChinese() = monthGridSpeaksEachDay()
    @Test @Config(qualifiers = "+zh-rTW") fun monthGridTraditionalChinese() = monthGridSpeaksEachDay()
    @Test @Config(qualifiers = "+fr") fun monthGridFrench() = monthGridSpeaksEachDay()

    @Test @Config(qualifiers = "+en") fun weekStripNamesEachDoseStatusInWords() {
        show(CalView.WEEK)
        val days = ui.onAllNodes(dateCell).fetchSemanticsNodes()
        assertEquals(7, days.size)
        val partial = description(today.minusDays(1))
        assertTrue(partial, partial.contains(s(R.string.status_on_time)) && partial.contains(s(R.string.status_missed)))
        assertTrue(description(today.plusDays(1)).contains(s(R.string.status_pending)))
        assertEquals(true, selected(today))
        dayNode(today.minusDays(1)).performClick(); ui.waitForIdle()
        assertEquals(true, selected(today.minusDays(1)))
    }

    @Test @Config(qualifiers = "+fr") fun yearGridMonthsAreNamedButtonsThatOpenTheMonth() {
        show(CalView.YEAR)
        val locale = context.resources.configuration.locales[0]
        val march = YearMonth.of(2026, 3).format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMM"), locale))
        val node = ui.onNode(hasContentDescription(march) and hasClickAction())
        assertEquals(s(R.string.calendar_a11y_open_month), node.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        ui.onAllNodes(hasClickAction() and hasContentDescription(YearMonth.of(2026, 1).format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMM"), locale)))).assertCountEquals(1)
        node.performClick(); ui.waitForIdle()
        dayNode(LocalDate.of(2026, 3, 1)).assertExists()
    }
}
