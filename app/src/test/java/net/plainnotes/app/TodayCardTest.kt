package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsNode
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

/**
 * UI-01 A, synthetic data on the real CalendarScreen: the "today" card shows progress and the next open dose, and its
 * buttons call the very same onComplete/onChange callbacks as the day's DoseCard (AppShell opens IntakeDialog /
 * OverrideDialog from them; no new write path). Names come from the frozen rule snapshot, as on DoseCard.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "w411dp-h891dp")
class TodayCardTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ui.activity
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 3, 10)
    private fun at(h: Int, m: Int = 0) = today.atTime(h, m).atZone(zone).toInstant()
    private val med = MedicationEntity(1, "Synthetic E2", "E2", "ORAL", "MG", 2.0, 30.0, null, 15, 120, false, null, true, true, 0)
    private val med2 = MedicationEntity(2, "Synthetic Spiro", "SPIRO", "ORAL", "MG", 50.0, 30.0, null, 15, 120, false, null, true, true, 1)
    private fun entry(medId: Long, h: Int, st: SlotState = SlotState.PENDING, dose: Double = 2.0) =
        TimelineEntry(Slot("wall:$medId@${at(h)}", medId, medId, at(h), at(h), zone, dose, 15, 120, at(h).minusSeconds(86400 * 30)), st)
    private fun record(id: Long, medId: Long, h: Int, status: String = "ON_TIME", m: MedicationEntity = med) = RecordEntity(id, medId, scheduled_utc = at(h).toEpochMilli(),
        taken_utc = if (status == "MISSED") null else at(h).toEpochMilli(), taken_zone = zone.id, actual_dose = 2.0, status = status, origin = "APP", revision = 1,
        config_snapshot = MedicationSnapshot.encode(m, null))
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    private val completed = mutableListOf<TimelineEntry>()
    private val changed = mutableListOf<TimelineEntry>()
    private fun show(slots: List<TimelineEntry>, records: List<RecordEntity> = emptyList(), meds: List<MedicationEntity> = listOf(med, med2),
                     snapshots: Map<Long, String> = emptyMap()) {
        val state = NotesState(meds, slots, emptyList(), loading = false, calendarStart = today, ruleSnapshots = snapshots)
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            CalendarScreen(state, today, { completed += it }, { changed += it }, {}, {}, PaddingValues(),
                extra = NotesViewModel.ExtraState(records = records, upcoming = slots.filter { it.state in OPEN_STATES }))
        } } }
        ui.waitForIdle()
    }
    private fun card() = ui.onNodeWithTag("today-card")
    private fun inCard(m: SemanticsMatcher) = ui.onNode(m and hasAnyAncestor(hasTestTag("today-card")))

    @Test fun noMedicationsShowsTheEmptyStateNotTheCard() {
        show(emptyList(), meds = emptyList())
        card().assertDoesNotExist()
        ui.onNodeWithText(s(R.string.empty_title)).assertExists()
    }

    @Test fun nothingPlannedToday() {
        show(emptyList())
        card().assertExists()
        inCard(hasText(s(R.string.today_nothing))).assertExists()
        inCard(hasTestTag("today-take-now")).assertDoesNotExist()
    }

    @Test fun partlyDoneShowsProgressNextDoseAndDoneList() {
        val next = entry(1, 20)
        show(listOf(next), listOf(record(5, 1, 8)))
        inCard(hasTestTag("today-progress")).assertTextEquals(s(R.string.today_progress, 1, 2))
        inCard(hasTestTag("today-next-time")).assertExists()
        inCard(hasAnyAncestor(hasTestTag("today-next-name")) and hasText("Synthetic E2")).assertExists()
        inCard(hasTestTag("today-done:5") and hasText("Synthetic E2", substring = true)).assertExists()
        inCard(hasTestTag("today-take-now")).performClick()
        assertEquals(listOf(next), completed)
        assertTrue(changed.isEmpty())
    }

    @Test fun allDone() {
        show(emptyList(), listOf(record(5, 1, 8), record(6, 2, 9, m = med2)))
        inCard(hasText(s(R.string.today_all_done))).assertExists()
        inCard(hasTestTag("today-progress")).assertTextEquals(s(R.string.today_progress, 2, 2))
        inCard(hasTestTag("today-take-now")).assertDoesNotExist()
    }

    @Test fun overdueSaysSoInWords() {
        show(listOf(entry(1, 8, SlotState.OVERDUE)))
        inCard(hasTestTag("today-next-status")).assertTextEquals(s(R.string.today_overdue))
    }

    @Test fun severalMedicationsAtTheSameTimeEachHaveAnAction() {
        val a = entry(1, 20); val b = entry(2, 20, dose = 50.0); val later = entry(1, 22)
        show(listOf(a, b, later))
        inCard(hasAnyAncestor(hasTestTag("today-next-name")) and hasText("Synthetic E2")).assertExists()
        inCard(hasTestTag("today-also:${b.slot.key}")).assertExists()
        inCard(hasText("Synthetic Spiro")).assertExists()
        inCard(hasTestTag("today-also-take:${b.slot.key}")).performClick()
        inCard(hasTestTag("today-take-now")).performClick()
        assertEquals(listOf(b, a), completed)
        // Only the doses due at the next time are listed here; later ones are in the "Later today" section (UI-04).
        inCard(hasTestTag("today-also:${later.slot.key}")).assertDoesNotExist()
    }

    @Test fun menuOffersAnotherTimeAndRescheduleOrSkip() {
        val next = entry(1, 20)
        show(listOf(next))
        inCard(hasTestTag("today-more")).performClick()
        ui.onNodeWithText(s(R.string.today_other_time)).performClick()
        assertEquals(listOf(next), completed)
        inCard(hasTestTag("today-more")).performClick()
        ui.onNodeWithText(s(R.string.today_change_or_skip)).performClick()
        assertEquals(listOf(next), changed)
    }

    @Test fun frozenRuleSnapshotNamesTheDoseLikeDoseCard() {
        val frozen = med.copy(name = "Frozen name at plan time")
        show(listOf(entry(1, 20)), snapshots = mapOf(1L to MedicationSnapshot.encode(frozen, null)))
        inCard(hasAnyAncestor(hasTestTag("today-next-name")) and hasText("Frozen name at plan time")).assertExists()
    }

    @Test fun talkBackReadsDateProgressNextDoseThenButton() {
        show(listOf(entry(1, 20)), listOf(record(5, 1, 8)))
        val order = traversal(card().fetchSemanticsNode())
        fun idx(tag: String) = order.indexOfFirst { it.config.getOrNull(SemanticsProperties.TestTag) == tag }.also { assertTrue("$tag missing", it >= 0) }
        val i = listOf(idx("today-date"), idx("today-progress"), idx("today-next-time"), idx("today-take-now"))
        assertEquals(i.sorted(), i)
        // Each piece is its own node, read in that order, and the card is not merged into one long label.
        val nodes = listOf("today-date", "today-progress", "today-next-time", "today-take-now").map { inCard(hasTestTag(it)).fetchSemanticsNode() }
        val ys = nodes.map { it.positionInRoot.y }
        assertEquals(ys.sorted(), ys)
    }

    private fun traversal(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::traversal)
}

/** UI-01 B: the calendar's stock card appears only when the shared forecast says low or out (the stock page's rule). */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "w411dp-h891dp")
class HomeStockCardTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 3, 10)
    private fun day(n: Long) = today.plusDays(n).atTime(8, 0).atZone(zone).toInstant()
    private val med = MedicationEntity(1, "Synthetic E2", "E2", "ORAL", "MG", 2.0, 30.0, null, 15, 120, false, null, true, true, 0)
    private val med2 = MedicationEntity(2, "Synthetic Spiro", "SPIRO", "ORAL", "MG", 50.0, 30.0, null, 15, 120, false, null, true, true, 1)
    private var stockOpened = 0
    private fun show(forecasts: Map<Long, StockForecast>) {
        val state = NotesState(listOf(med, med2), emptyList(), emptyList(), loading = false, calendarStart = today)
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            CalendarScreen(state, today, {}, {}, {}, {}, PaddingValues(), extra = NotesViewModel.ExtraState(stockForecasts = forecasts), onStock = { stockOpened++ })
        } } }
        ui.waitForIdle()
    }
    private fun list() = ui.onNode(hasScrollToIndexAction())
    private fun s(id: Int) = ui.activity.getString(id)

    @Test fun untrackedOrNoPlanMeansNoCard() {
        show(mapOf(1L to StockForecast(1, null, 0, null, null),                       // untracked
            2L to StockForecast(2, 10.0, 0, null, null)))                             // no plan
        ui.onNodeWithTag("home-stock").assertDoesNotExist()
        ui.onNodeWithText(s(R.string.forecast_untracked)).assertDoesNotExist()
    }

    @Test fun coveredMeansNoCard() {
        show(mapOf(1L to StockForecast(1, 20.0, 9, day(8), null)))
        ui.onNodeWithTag("home-stock").assertDoesNotExist()
    }

    @Test fun shortOnlyOnTheSeventhDayIsNotLowSoNoCard() {
        // Day 7 is outside the low window (StockForecast.isLow): same rule as the stock page and reminder, so no card.
        val seven = StockForecast(1, 14.0, 8, day(6), day(7))
        assertFalse(seven.isLow(today, zone))
        show(mapOf(1L to seven))
        ui.onNodeWithTag("home-stock").assertDoesNotExist()
    }

    @Test fun lowOrOutListsOnlyThoseWithOneButton() {
        val low = StockForecast(1, 6.0, 8, day(2), day(3)); val out = StockForecast(2, 0.0, 8, null, day(0))
        assertTrue(low.isLow(today, zone)); assertTrue(out.isLow(today, zone))
        show(mapOf(1L to low, 2L to out))
        list().performScrollToNode(hasTestTag("home-stock"))
        ui.onNodeWithTag("home-stock").assertExists()
        // UI-05 B: the name is now inside the strip's "<name> is running low" line, so the match is a substring; the meaning
        // (this row is for that medication) is unchanged.
        ui.onNode(hasAnyAncestor(hasTestTag("home-stock-row:1")) and hasText("Synthetic E2", substring = true)).assertExists()
        ui.onNode(hasTestTag("home-stock-row:2")).assertExists()
        ui.onNode(hasText(s(R.string.forecast_empty), substring = true) and hasAnyAncestor(hasTestTag("home-stock"))).assertExists()
        ui.onAllNodes(hasTestTag("home-stock-view")).assertCountEquals(2)
        ui.onAllNodes(hasTestTag("home-stock-view")).onFirst().performClick()
        assertEquals(1, stockOpened)
        // The removed general footnote (former forecast_note, deleted in UI-02 A1) is not shown on the home card.
        ui.onNodeWithText("Based on your schedule and the open and sealed packages in Stock.").assertDoesNotExist()
    }
}
