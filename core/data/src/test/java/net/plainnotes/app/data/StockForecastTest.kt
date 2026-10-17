package net.plainnotes.app.data

import net.plainnotes.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

/**
 * B4-A: the shared per-dose stock forecast over doses produced by the real domain Timeline (synthetic data, fixed
 * clock and zones; no Android).
 */
class StockForecastTest {
    private val utc = ZoneId.of("UTC")
    private val paris = ZoneId.of("Europe/Paris")
    private val day0 = LocalDate.of(2026, 3, 10)  // a Tuesday
    private fun at(d: LocalDate, h: Int, m: Int = 0, zone: ZoneId = utc) = d.atTime(h, m).atZone(zone).toInstant()
    private val now = at(day0, 6)

    private fun daily(vararg times: Pair<Int, Double?>, interval: Int = 1, dose: Double = 2.0, id: Long = 1, zone: ZoneId = utc,
                      from: Instant = at(day0.minusDays(30), 0), until: Instant? = null) =
        ScheduleRule(id, 1, RuleKind.EVERY_N_DAYS, interval, day0, null, zone, from, until, from,
            times.map { (h, d) -> RuleTime(LocalTime.of(h, 0), d) }, emptySet(), dose, 15, 60)
    private fun weekly(days: Set<DayOfWeek>, dose: Double = 2.0) =
        ScheduleRule(1, 1, RuleKind.WEEKLY, 1, day0, null, utc, at(day0.minusDays(30), 0), null, at(day0.minusDays(30), 0),
            listOf(RuleTime(LocalTime.of(8, 0))), days, dose, 15, 60)
    private fun hourly(hours: Int, dose: Double = 1.0) =
        ScheduleRule(1, 1, RuleKind.EVERY_N_HOURS, hours, null, at(day0, 2), utc, at(day0.minusDays(30), 0), null, at(day0.minusDays(30), 0),
            emptyList(), emptySet(), dose, 15, 60)

    private fun doses(rules: List<ScheduleRule>, overrides: List<SlotOverride> = emptyList(), records: List<DoseRecord> = emptyList(),
                      clock: Instant = now, zone: ZoneId = utc, days: Long = StockForecasts.WINDOW_DAYS) =
        Timeline.build(rules, overrides, records, clock, clock.plus(Duration.ofDays(days)), clock, zone)
    private fun open(amount: Double) = ContainerEntity(1, 1, 100.0, 100.0 - amount, 100.0 - amount, "2026-03-01", "IN_USE")
    private fun sealed(id: Long, capacity: Double) = ContainerEntity(id, 1, capacity, 0.0, 0.0, null, "SEALED")
    private fun f(containers: List<ContainerEntity>, ds: List<TimelineEntry>) = StockForecasts.forecast(1, containers, ds)

    @Test fun dailyFixedDose() {
        val r = f(listOf(open(10.0)), doses(listOf(daily(8 to null))))  // 2 mg at 08:00: covers 5 doses, day0..day4
        assertEquals(at(day0.plusDays(4), 8), r.lastCovered); assertEquals(at(day0.plusDays(5), 8), r.firstShort)
        assertEquals(5, r.daysUntilShort(day0, utc)); assertTrue(r.isLow(day0, utc)); assertEquals(StockForecast.Status.SHORT, r.status)
    }

    @Test fun severalDosesPerDayWithDifferentAmounts() {
        val r = f(listOf(open(7.0)), doses(listOf(daily(8 to 1.0, 20 to 3.0))))  // 1 + 3 per day
        assertEquals(at(day0.plusDays(1), 8), r.lastCovered)   // 1,3,1 = 5; next 3 needs 3 > 2 left
        assertEquals(at(day0.plusDays(1), 20), r.firstShort)
    }

    @Test fun everyOtherDayAndWeekdaysAndEveryNHours() {
        val other = f(listOf(open(6.0)), doses(listOf(daily(8 to null, interval = 2))))
        assertEquals(at(day0.plusDays(4), 8), other.lastCovered); assertEquals(at(day0.plusDays(6), 8), other.firstShort)  // day0, 2, 4
        val twiceWeekly = f(listOf(open(4.0)), doses(listOf(weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)))))
        assertEquals(at(LocalDate.of(2026, 3, 16), 8), twiceWeekly.lastCovered)   // Thu 12th, Mon 16th covered
        assertEquals(at(LocalDate.of(2026, 3, 19), 8), twiceWeekly.firstShort)    // Thu 19th not
        val hours = f(listOf(open(3.0)), doses(listOf(hourly(8))))                 // 1 mg every 8 h from 02:00; now 06:00
        assertEquals(at(day0.plusDays(1), 2), hours.lastCovered)                  // 10:00, 18:00, 02:00 covered
        assertEquals(at(day0.plusDays(1), 10), hours.firstShort)
    }

    @Test fun skipRescheduleAndDoseOverrideComeFromTheTimeline() {
        val rule = daily(8 to null); val base = doses(listOf(rule)).filter { it.slot.medicationId == 1L }
        val second = base[1].slot; val third = base[2].slot
        val changed = doses(listOf(rule), overrides = listOf(
            SlotOverride(second.key, skipped = true),                                   // skipped: needs nothing
            SlotOverride(third.key, rescheduled = at(day0.plusDays(2), 21), zone = utc, dose = 4.0)))  // later and larger
        val r = f(listOf(open(8.0)), changed)
        assertEquals(at(day0.plusDays(3), 8), r.lastCovered)  // 2 (day0) + 4 (day2 21:00) + 2 (day3) = 8
        assertEquals(at(day0.plusDays(4), 8), r.firstShort)
    }

    @Test fun takenDosesAreNotSubtractedTwice() {
        val rule = daily(8 to null); val first = doses(listOf(rule)).first().slot
        val taken = DoseRecord(first.key, 1, DoseStatus.ON_TIME, at(day0, 8), utc, 2.0, scheduled = first.original, scheduledZone = utc, plannedDose = 2.0)
        val later = at(day0, 9)
        // The taken dose already reduced the package (allocation); the forecast walks only what is still open.
        val r = f(listOf(open(4.0)), doses(listOf(rule), records = listOf(taken), clock = later))
        assertEquals(at(day0.plusDays(2), 8), r.lastCovered); assertEquals(at(day0.plusDays(3), 8), r.firstShort)
    }

    @Test fun planChangeUsesTheNewVersionFromItsStart() {
        val switch = at(day0.plusDays(2), 0)
        val old = daily(8 to null, dose = 2.0, id = 1, until = switch)
        val new = daily(8 to null, dose = 5.0, id = 2, from = switch)
        val r = f(listOf(open(12.0)), doses(listOf(old, new)))
        assertEquals(at(day0.plusDays(2), 8), r.lastCovered)  // 2 + 2 + 5 = 9, then 5 > 3
        assertEquals(at(day0.plusDays(3), 8), r.firstShort)
    }

    @Test fun openAndSealedPackagesAndEdgeAmounts() {
        val ds = doses(listOf(daily(8 to null)))
        assertEquals(14.0, StockForecasts.available(1, listOf(open(4.0), sealed(2, 10.0))))
        assertEquals(at(day0.plusDays(6), 8), f(listOf(open(4.0), sealed(2, 10.0)), ds).lastCovered)
        val exact = f(listOf(open(10.0)), ds); assertEquals(at(day0.plusDays(4), 8), exact.lastCovered)       // exactly the 5th dose
        val tiny = f(listOf(open(9.999)), ds); assertEquals(at(day0.plusDays(4), 8), tiny.firstShort)          // 0.001 short of the 5th
        val zero = f(listOf(open(0.0)), ds)
        assertNull(zero.lastCovered); assertEquals(ds.first().slot.at, zero.firstShort); assertEquals(0, zero.daysUntilShort(day0, utc)); assertTrue(zero.isLow(day0, utc))
        assertEquals(listOf(StockForecast.Status.UNTRACKED), listOf(f(emptyList(), ds).status))
        assertEquals(StockForecast.Status.UNTRACKED, f(listOf(open(5.0).copy(state = "EMPTY")), ds).status)  // finished packages are not stock
        assertEquals(StockForecast.Status.NO_PLAN, f(listOf(open(5.0)), emptyList()).status)
    }

    @Test fun sevenDayBoundaryUsesCalendarDays() {
        val ds = doses(listOf(daily(8 to null)))
        val inside = f(listOf(open(12.0)), ds)   // covers day0..day5, first short day6 → within today + 6
        assertEquals(6, inside.daysUntilShort(day0, utc)); assertTrue(inside.isLow(day0, utc))
        val outside = f(listOf(open(14.0)), ds)  // first short day7 → not low
        assertEquals(7, outside.daysUntilShort(day0, utc)); assertFalse(outside.isLow(day0, utc))
        // At 22:00 today's dose is past: day1..day6 are covered and day7 08:00 is short, only 6 days 10 h away. It is
        // still the 7th calendar day, so not low (a 7×24 h window would have called it low).
        val late = at(day0, 22)
        val evening = f(listOf(open(12.0)), doses(listOf(daily(8 to null)), clock = late))
        assertEquals(at(day0.plusDays(7), 8), evening.firstShort)
        assertTrue(Duration.between(late, evening.firstShort) < Duration.ofDays(7))
        assertEquals(7, evening.daysUntilShort(day0, utc)); assertFalse(evening.isLow(day0, utc))
    }

    @Test fun coveredThroughTheWholeWindowIsNotInfinite() {
        val r = f(listOf(open(100.0), sealed(2, 1000.0)), doses(listOf(daily(8 to null))))
        assertEquals(StockForecast.Status.COVERED_THROUGH_WINDOW, r.status); assertNull(r.firstShort); assertFalse(r.isLow(day0, utc))
        assertTrue("known only up to the window end", r.lastCovered!! < now.plus(Duration.ofDays(StockForecasts.WINDOW_DAYS)))
    }

    @Test fun daylightSavingSwitchKeepsLocalDates() {
        // Paris switches to summer time on 29 March 2026; doses stay at 08:00 local.
        val rule = daily(8 to null, zone = paris)
        val clock = at(LocalDate.of(2026, 3, 27), 6, zone = paris)
        val r = f(listOf(open(6.0)), doses(listOf(rule), clock = clock, zone = paris))
        assertEquals(at(LocalDate.of(2026, 3, 30), 8, zone = paris), r.firstShort)   // 27, 28, 29 covered
        assertEquals(3, r.daysUntilShort(LocalDate.of(2026, 3, 27), paris))
        // The same instant seen from another zone falls on the same date here, so the count is zone-explicit.
        assertEquals(3, r.daysUntilShort(LocalDate.of(2026, 3, 27), utc))
    }
    @Test fun integerQuantaCannotBeTreatedAsLegacyMedicationDoseUnits() {
        val exact=open(10.0).copy(inventory_encoding="INTEGER_QUANTA_V1")
        val result=f(listOf(exact),doses(listOf(daily(8 to null))))
        assertNull("Integer quanta were presented as mg stock",result.available)
        assertNull(result.firstShort);assertNull(result.lastCovered)
        assertFalse(result.isLow(day0,utc));assertNull(result.daysUntilShort(day0,utc))
    }

}
