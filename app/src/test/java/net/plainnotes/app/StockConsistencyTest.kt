package net.plainnotes.app

import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import net.plainnotes.app.reminder.StockAlerts
import net.plainnotes.app.ui.dayInfos
import net.plainnotes.app.ui.stockSummary
import org.junit.Assert.*
import org.junit.Test
import java.time.*

/**
 * B4-B: for the same synthetic medication, packages and plan, the stock page, the calendar and the stock reminder reach
 * the same conclusion because all three read one shared forecast. The scenarios are the ones where the three old
 * paths disagreed (report 24).
 */
class StockConsistencyTest {
    private val utc = ZoneId.of("UTC")
    private val day0 = LocalDate.of(2026, 3, 10)
    private fun at(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(utc).toInstant()
    private val med = MedicationEntity(1, "Synthetic", "E2", "ORAL", "MG", 2.0, 100.0, null, 15, 60, false, null, true, true, 0)
    private fun box(left: Double) = ContainerEntity(1, 1, 100.0, 100.0 - left, 100.0 - left, "2026-03-01", "IN_USE")
    private fun rule(kind: RuleKind, interval: Int, times: List<RuleTime>, days: Set<DayOfWeek> = emptySet(), dose: Double = 2.0) =
        if (kind == RuleKind.EVERY_N_HOURS) ScheduleRule(1, 1, kind, interval, null, at(day0, 2), utc, at(day0.minusDays(30), 0), null, at(day0.minusDays(30), 0), emptyList(), emptySet(), dose, 15, 60)
        else ScheduleRule(1, 1, kind, interval, day0, null, utc, at(day0.minusDays(30), 0), null, at(day0.minusDays(30), 0), times, days, dose, 15, 60)
    private val t8 = listOf(RuleTime(LocalTime.of(8, 0)))

    private data class Case(val name: String, val rule: ScheduleRule, val left: Double, val now: Instant, val skips: Int = 0,
                            val expectedShort: Instant, val expectedDays: Int, val expectedLow: Boolean)

    private val cases = listOf(
        Case("weekly Mon+Thu", rule(RuleKind.WEEKLY, 1, t8, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)), 4.0, at(day0, 6),
            expectedShort = at(LocalDate.of(2026, 3, 19), 8), expectedDays = 9, expectedLow = false),
        Case("daily, checked at 22:00", rule(RuleKind.EVERY_N_DAYS, 1, t8), 12.0, at(day0, 22),
            expectedShort = at(day0.plusDays(7), 8), expectedDays = 7, expectedLow = false),
        Case("1 mg + 3 mg a day", rule(RuleKind.EVERY_N_DAYS, 1, listOf(RuleTime(LocalTime.of(8, 0), 1.0), RuleTime(LocalTime.of(20, 0), 3.0))), 9.0, at(day0, 6),
            expectedShort = at(day0.plusDays(2), 20), expectedDays = 2, expectedLow = true),
        Case("two doses skipped", rule(RuleKind.EVERY_N_DAYS, 1, t8), 12.0, at(day0, 6), skips = 2,
            expectedShort = at(day0.plusDays(8), 8), expectedDays = 8, expectedLow = false),
        Case("every 8 h", rule(RuleKind.EVERY_N_HOURS, 8, emptyList(), dose = 1.0), 20.0, at(day0, 6),
            expectedShort = at(day0.plusDays(7), 2), expectedDays = 7, expectedLow = false),
        Case("nothing left", rule(RuleKind.EVERY_N_DAYS, 1, t8), 0.0, at(day0, 6),
            expectedShort = at(day0, 8), expectedDays = 0, expectedLow = true),
    )

    @Test fun pageCalendarAndReminderAgreeForEveryScenario() {
        for (c in cases) {
            val window = c.now.plus(Duration.ofDays(StockForecasts.WINDOW_DAYS))
            val plain = Timeline.build(listOf(c.rule), emptyList(), emptyList(), c.now, window, c.now, utc)
            val overrides = plain.take(c.skips).map { SlotOverride(it.slot.key, skipped = true) }
            val doses = Timeline.build(listOf(c.rule), overrides, emptyList(), c.now, window, c.now, utc)
            val boxes = listOf(box(c.left)); val today = c.now.atZone(utc).toLocalDate()
            val forecasts = StockForecasts.forecastAll(listOf(med), boxes, doses)
            val shared = forecasts.getValue(1)
            assertEquals(c.name, c.expectedShort, shared.firstShort)
            assertEquals(c.name, c.expectedDays, shared.daysUntilShort(today, utc))
            assertEquals(c.name, c.expectedLow, shared.isLow(today, utc))

            val page = stockSummary(med, boxes, forecasts[1], today, utc)
            assertEquals("${c.name}: page days", c.expectedDays, page.daysLeft)
            assertEquals("${c.name}: page low", c.expectedLow, page.low)

            val reminderLow = StockAlerts.due(listOf(med), boxes, forecasts, today, utc).any { it.kind == StockAlerts.Kind.LOW }
            assertEquals("${c.name}: reminder", c.expectedLow, reminderLow)

            val days = dayInfos(emptyList(), doses.filter { it.state in net.plainnotes.app.ui.OPEN_STATES }, emptyList(), forecasts, utc)
            val firstShortDay = c.expectedShort.atZone(utc).toLocalDate()
            assertTrue("${c.name}: calendar marks the first uncovered dose", (days[firstShortDay]?.short ?: 0) > 0)
            assertEquals("${c.name}: no uncovered dose before it", 0, days.filterKeys { it < firstShortDay }.values.sumOf { it.short })
            shared.lastCovered?.let { assertEquals("${c.name}: run-out marker on the last covered day", setOf(1L), days.getValue(it.atZone(utc).toLocalDate()).runOut) }
        }
    }
}
