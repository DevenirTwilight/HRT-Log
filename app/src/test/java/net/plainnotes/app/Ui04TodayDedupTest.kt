package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.AppointmentEntity
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.data.MedicationSnapshot
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.domain.Slot
import net.plainnotes.app.domain.SlotState
import net.plainnotes.app.domain.TimelineEntry
import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * UI-04 (REQUIREMENTS §101): with today selected, today's doses, records and appointments appear only in the today card;
 * the day list below the calendar is for other dates. Written before the change (they fail on the UI-01 calendar).
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "w411dp-h891dp")
class Ui04TodayDedupTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ui.activity
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 3, 10)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).atZone(zone).toInstant()
    private val med = MedicationEntity(1, "Synthetic E2", "E2", "ORAL", "MG", 2.0, 30.0, null, 15, 120, false, null, true, true, 0)
    private fun entry(h: Int, d: LocalDate = today, st: SlotState = SlotState.PENDING, m: Int = 0) =
        TimelineEntry(Slot("wall:1@${at(d, h, m)}", 1, 1, at(d, h, m), at(d, h, m), zone, 2.0, 15, 120, at(d, h, m).minusSeconds(86400 * 30)), st)
    private fun record(id: Long, h: Int, status: String, origin: String = "APP", d: LocalDate = today) = RecordEntity(id, 1, scheduled_utc = at(d, h).toEpochMilli(),
        taken_utc = if (status in setOf("MISSED", "SKIPPED")) null else at(d, h).toEpochMilli(), taken_zone = zone.id, actual_dose = 2.0, status = status, origin = origin,
        revision = 1, config_snapshot = MedicationSnapshot.encode(med, null))
    private fun appt(id: Long, h: Int, d: LocalDate = today) = AppointmentEntity(id, "ENDO", at(d, h).toEpochMilli(), zone.id, "Synthetic doctor", "Synthetic clinic", null, 0)
    private fun s(id: Int, vararg a: Any) = context.getString(id, *a)

    private val completed = mutableListOf<TimelineEntry>()
    private val changed = mutableListOf<TimelineEntry>()
    private val opened = mutableListOf<AppointmentEntity>()
    private fun state(open: List<TimelineEntry>, appts: List<AppointmentEntity>, start: LocalDate) =
        NotesState(listOf(med), open, appts, loading = false, calendarStart = start)
    private fun extra(open: List<TimelineEntry>, records: List<RecordEntity>) = NotesViewModel.ExtraState(records = records, upcoming = open.filter { it.state in OPEN_STATES })
    private fun show(open: List<TimelineEntry>, records: List<RecordEntity> = emptyList(), appts: List<AppointmentEntity> = emptyList(), start: LocalDate = today, full: Boolean = false) {
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            CalendarScreen(state(open, appts, start), today, { completed += it }, { changed += it }, {}, {}, PaddingValues(), extra = extra(open, records),
                onAppointment = { opened += it }, initialFull = full)
        } } }
        ui.waitForIdle()
    }
    private val list get() = ui.onNode(hasScrollToIndexAction())
    // UI-07 E: today's records and appointments moved from inside the card to grouped lists right under it; "today-section"
    // holds the card and those lists, so "shown once, in today's section" is still what is checked.
    private fun inCard(m: SemanticsMatcher) = ui.onNode(m and hasAnyAncestor(hasTestTag("today-section")))
    private fun tags(prefix: String) = ui.onAllNodes(SemanticsMatcher("tag $prefix") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true }, useUnmergedTree = true)
        .fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
    /** Every node in the list, composed or not: scroll through the whole list collecting tags. */
    private fun allTags(): List<String> {
        val seen = linkedSetOf<String>()
        var i = 0
        while (runCatching { list.performScrollToIndex(i) }.isSuccess) { seen += tags(""); i++ }
        assertTrue("scanned the list", i > 2)
        return seen.toList()
    }

    @Test fun todaySelectedShowsEachDoseRecordAndAppointmentOnce() {
        val a = entry(8, st = SlotState.OVERDUE); val b = entry(21)
        show(listOf(a, b), listOf(record(5, 6, "ON_TIME"), record(6, 7, "MISSED")), listOf(appt(9, 15)))
        val all = allTags()
        // The day list below the calendar is not shown for today: no header, no dose cards, no record lines, no appointment card.
        assertTrue(all.toString(), all.none { it == "day-head" || it.startsWith("day-dose:") || it.startsWith("day-record:") || it.startsWith("day-appt:") })
        ui.onAllNodesWithText(s(R.string.calendar_day_empty)).assertCountEquals(0)
        // Each of today's items is in the today card instead.
        listOf("today-next-time", "today-later:${b.slot.key}", "today-done:5", "today-done:6", "today-appt:9").forEach { t -> assertTrue(t, all.contains(t)) }
    }

    @Test fun anotherDateStillShowsItsDayList() {
        val tomorrow = today.plusDays(1)
        val t1 = entry(8, tomorrow); val t2 = entry(21, tomorrow)
        show(listOf(t1, t2), listOf(record(7, 9, "ON_TIME", d = tomorrow)), listOf(appt(3, 10, tomorrow)), start = tomorrow)
        val all = allTags()
        assertTrue(all.contains("day-head"))
        assertTrue(all.contains("day-dose:${t1.slot.key}") && all.contains("day-dose:${t2.slot.key}"))
        assertTrue(all.contains("day-record:7") && all.contains("day-appt:3"))
        list.performScrollToNode(hasTestTag("day-dose:${t1.slot.key}"))
        ui.onNode(hasText(s(R.string.complete)) and hasAnyAncestor(hasTestTag("day-dose:${t1.slot.key}"))).performClick()
        assertEquals(listOf(t1), completed)
    }

    @Test fun laterDosesAreListedWithTheSameMenu() {
        val next = entry(8); val noon = entry(13); val night = entry(21)
        show(listOf(next, noon, night))
        inCard(hasTestTag("today-later")).assertExists()
        listOf(noon, night).forEach { e -> inCard(hasAnyAncestor(hasTestTag("today-later:${e.slot.key}")) and hasText("Synthetic E2", substring = true)).assertExists() }
        inCard(hasTestTag("today-later:${next.slot.key}")).assertDoesNotExist()
        // No large button per row: only the menu, which offers the same two actions as the next dose's menu.
        inCard(hasText(s(R.string.today_take_now)) and hasAnyAncestor(hasTestTag("today-later:${night.slot.key}"))).assertDoesNotExist()
        list.performScrollToNode(hasTestTag("today-later-more:${night.slot.key}"))
        inCard(hasTestTag("today-later-more:${night.slot.key}")).performClick()
        ui.onNodeWithText(s(R.string.today_other_time)).performClick()
        inCard(hasTestTag("today-later-more:${night.slot.key}")).performClick()
        ui.onNodeWithText(s(R.string.today_change_or_skip)).performClick()
        assertEquals(listOf(night), completed); assertEquals(listOf(night), changed)
    }

    @Test fun todaysRecordsSayTheirStatusInWords() {
        show(listOf(entry(21)), listOf(record(2, 8, "MISSED"), record(3, 9, "MISSED", origin = "AUTO_MISSED"), record(4, 10, "SKIPPED")))
        inCard(hasTestTag("today-log")).assertTextEquals(s(R.string.today_log_heading))
        list.performScrollToNode(hasTestTag("today-done:4"))
        inCard(hasTestTag("today-done:2") and hasText(s(R.string.status_missed), substring = true)).assertExists()
        inCard(hasTestTag("today-done:3") and hasText(s(R.string.status_unconfirmed), substring = true)).assertExists()
        inCard(hasText(s(R.string.unconfirmed_help))).assertExists()
        inCard(hasTestTag("today-done:4") and hasText(s(R.string.status_skipped), substring = true)).assertExists()
        // Progress still counts taken doses only: none taken of 1 open + 3 records.
        inCard(hasTestTag("today-progress")).assertTextEquals(s(R.string.today_progress, 0, 4))
    }

    @Test fun todaysAppointmentIsARowThatOpensIt() {
        val a = appt(9, 15)
        show(listOf(entry(21)), appts = listOf(a))
        list.performScrollToNode(hasTestTag("today-appt:9"))
        inCard(hasTestTag("today-appts")).assertTextEquals(s(R.string.today_appointment))
        inCard(hasTestTag("today-appt:9") and hasText("Synthetic doctor", substring = true)).assertHasClickAction().performClick()
        assertEquals(listOf(a), opened)
    }

    @Test @Config(qualifiers = "w320dp-h640dp") fun goingBackToTodayScrollsTheTodayCardIntoView() {
        val tomorrow = today.plusDays(1)
        // UI-07 F: the "Today" button is in the full calendar's period bar; open the full calendar explicitly.
        show((8..20 step 2).map { entry(it, tomorrow) } + entry(21), start = tomorrow, full = true)
        list.performScrollToNode(hasTestTag("day-dose:${entry(20, tomorrow).slot.key}"))   // down in tomorrow's list
        list.performScrollToIndex(1)                                                          // the calendar at the top, the card above it
        assertTrue("precondition: the today card's title is out of view", runCatching { ui.onNodeWithTag("today-date").assertIsDisplayed() }.isFailure)
        ui.onNode(hasText(s(R.string.today)) and hasClickAction()).performClick()
        ui.waitForIdle()
        ui.onNodeWithTag("today-date").assertIsDisplayed()
        ui.onNodeWithTag("today-take-now").assertIsDisplayed()
    }

    @Test fun moreThanThreeCollapseAndStayExpandedAfterRestoration() {
        val restore = StateRestorationTester(ui)
        val later = (10..20 step 2).map { entry(it) }                          // 6 later doses after 08:00
        val open = listOf(entry(8)) + later
        restore.setContent { NotesTheme(ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            CalendarScreen(state(open, emptyList(), today), today, {}, {}, {}, {}, PaddingValues(), extra = extra(open, emptyList()))
        } } }
        ui.waitForIdle()
        fun shown() = later.count { e -> ui.onAllNodes(hasTestTag("today-later:${e.slot.key}")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(3, shown())
        list.performScrollToNode(hasTestTag("today-show-all:later"))
        inCard(hasTestTag("today-show-all:later")).assertTextEquals(s(R.string.today_show_all, 6)).performClick()
        ui.waitForIdle(); assertEquals(6, shown())
        restore.emulateSavedInstanceStateRestore(); ui.waitForIdle()
        assertEquals(6, shown())
    }

    @Test fun talkBackReadsDateProgressNextButtonLaterLogAppointment() {
        show(listOf(entry(8), entry(21)), listOf(record(5, 6, "ON_TIME")), listOf(appt(9, 15)))
        // UI-07 E: the log and appointments follow the card in the same section; the reading order is unchanged.
        val order = traversal(ui.onNodeWithTag("today-section").fetchSemanticsNode()).map { it.config.getOrNull(SemanticsProperties.TestTag) }
        val i = listOf("today-date", "today-progress", "today-next-time", "today-take-now", "today-later", "today-log", "today-appts").map { t ->
            order.indexOf(t).also { assertTrue("$t missing", it >= 0) } }
        assertEquals(i.sorted(), i)
    }

    private fun traversal(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::traversal)
}
