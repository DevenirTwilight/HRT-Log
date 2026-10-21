package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
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
 * UI-05 A, synthetic data on the real CalendarScreen: the today card's progress pill, the countdown to the next dose
 * (display only, never shown when overdue), the main button and menu (52 dp in UI-05, 56 dp since UI-07), the divider, and a status icon on each
 * record row next to the unchanged status words. Callbacks are the same as UI-01/UI-04 (TodayCardTest keeps covering them).
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "w411dp-h891dp")
class Ui05TodayCardTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ui.activity
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 3, 10)
    private fun at(h: Int, m: Int = 0) = today.atTime(h, m).atZone(zone).toInstant()
    private val med = MedicationEntity(1, "Synthetic A", "E2", "ORAL", "MG", 2.0, 30.0, null, 15, 120, false, null, true, true, 0)
    private fun entry(h: Int, st: SlotState = SlotState.PENDING) =
        TimelineEntry(Slot("wall:1@${at(h)}", 1, 1, at(h), at(h), zone, 2.0, 15, 120, at(h).minusSeconds(86400 * 30)), st)
    private fun record(id: Long, h: Int, status: String, unconfirmed: Boolean = false) = RecordEntity(id, 1, scheduled_utc = at(h).toEpochMilli(),
        taken_utc = if (status == "MISSED" || status == "SKIPPED") null else at(h).toEpochMilli(), taken_zone = zone.id, actual_dose = 2.0, status = status,
        origin = if (unconfirmed) "AUTO_MISSED" else "APP", revision = 1, config_snapshot = MedicationSnapshot.encode(med, null))
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun show(slots: List<TimelineEntry>, records: List<RecordEntity> = emptyList(), now: Instant = at(18)) {
        val state = NotesState(listOf(med), slots, emptyList(), loading = false, calendarStart = today)
        ui.setContent { CompositionLocalProvider(LocalTodayClock provides { now }) { NotesTheme(ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            CalendarScreen(state, today, {}, {}, {}, {}, PaddingValues(), extra = NotesViewModel.ExtraState(records = records, upcoming = slots.filter { it.state in OPEN_STATES }))
        } } } }
        ui.waitForIdle()
    }
    // UI-07 E: today's records and appointments moved from inside the card to grouped lists right under it; "today-section"
    // holds the card and those lists, so "shown once, in today's section" is still what is checked.
    private fun inCard(m: SemanticsMatcher) = ui.onNode(m and hasAnyAncestor(hasTestTag("today-section")))

    @Test fun progressIsAPillOnTheDateRow() {
        show(listOf(entry(20)), listOf(record(5, 8, "ON_TIME")))
        inCard(hasTestTag("today-progress") and hasAnyAncestor(hasTestTag("today-progress-pill"))).assertTextEquals(s(R.string.today_progress, 1, 2))
        val date = inCard(hasTestTag("today-date")).fetchSemanticsNode().boundsInRoot
        val pill = inCard(hasTestTag("today-progress-pill")).fetchSemanticsNode().boundsInRoot
        assertTrue("pill on the date row, to its right", pill.left > date.left && pill.top < date.bottom && pill.bottom > date.top)
    }

    @Test fun countdownInWholeHours() {
        show(listOf(entry(20)), now = at(17, 50))
        inCard(hasTestTag("today-countdown")).assertTextEquals(s(R.string.today_in_hours, 2))
    }

    @Test fun countdownInMinutesUnderAnHour() {
        show(listOf(entry(20)), now = at(19, 35))
        inCard(hasTestTag("today-countdown")).assertTextEquals(s(R.string.today_in_minutes, 25))
    }

    @Test fun overdueShowsTheWordsAndNoCountdown() {
        show(listOf(entry(9, SlotState.OVERDUE)), now = at(10))
        inCard(hasTestTag("today-next-status")).assertTextEquals(s(R.string.status_past_time))  // UI-06: "Past time · you can still log it"
        inCard(hasTestTag("today-countdown")).assertDoesNotExist()
    }

    // UI-07 B: the main button and the ⋮ menu are now 56 dp (the menu a square with 18 dp corners), still on one row.
    @Test fun mainButtonAndMenuAreFiftySixDpOnOneRow() {
        show(listOf(entry(20)))
        val button = inCard(hasTestTag("today-take-now")).fetchSemanticsNode().boundsInRoot
        val menu = inCard(hasTestTag("today-more")).fetchSemanticsNode().boundsInRoot
        with(ui.density) {
            assertTrue("button ≥ 56dp", button.height >= 56.dp.toPx() - 1); assertEquals(56.dp.toPx(), menu.height, 1f); assertEquals(56.dp.toPx(), menu.width, 1f)
        }
        assertEquals("same row", button.center.y, menu.center.y, 1f)
        ui.onNode(hasTestTag("today-take-now-icon"), useUnmergedTree = true).assertExists()
        inCard(hasTestTag("today-take-now") and hasText(s(R.string.today_take_now))).assertExists()
    }

    @Test fun dividerSeparatesNextFromTheSectionsBelow() {
        show(listOf(entry(20), entry(22)), listOf(record(5, 8, "ON_TIME")))
        ui.onNode(hasTestTag("today-divider"), useUnmergedTree = true).assertExists()
    }

    @Test fun noDividerWhenNothingFollowsNext() {
        show(listOf(entry(20)))
        ui.onNode(hasTestTag("today-divider"), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun everyRecordRowHasAStatusIconAndKeepsItsStatusWords() {
        val rows = listOf(record(1, 7, "ON_TIME") to R.string.status_on_time, record(2, 8, "LATE") to R.string.status_late,
            record(3, 9, "MISSED") to R.string.status_missed, record(4, 10, "MISSED", unconfirmed = true) to R.string.status_unconfirmed)
        show(listOf(entry(20)), rows.map { it.first })
        ui.onNodeWithTag("today-show-all:log", useUnmergedTree = true).performClick()
        val kinds = mapOf(1L to "TAKEN", 2L to "TAKEN", 3L to "MISSED", 4L to "UNCONFIRMED")
        rows.forEach { (r, words) ->
            inCard(hasTestTag("today-done:${r.id}") and hasText(s(words), substring = true)).assertExists()
            ui.onNode(hasTestTag("today-done-icon:${r.id}:${kinds[r.id]}"), useUnmergedTree = true).assertExists()
        }
    }

    @Test fun skippedRecordHasTheDashIcon() {
        show(listOf(entry(20)), listOf(record(7, 8, "SKIPPED")))
        inCard(hasTestTag("today-done:7") and hasText(s(R.string.status_skipped), substring = true)).assertExists()
        ui.onNode(hasTestTag("today-done-icon:7:SKIPPED"), useUnmergedTree = true).assertExists()
    }
}
