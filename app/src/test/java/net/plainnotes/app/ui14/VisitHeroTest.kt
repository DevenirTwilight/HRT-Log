package net.plainnotes.app.ui14

import net.plainnotes.app.data.AppointmentEntity
import net.plainnotes.app.ui.VisitDays
import net.plainnotes.app.ui.VisitHero
import net.plainnotes.app.ui.VisitHeroKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** UI-14 (REQUIREMENTS §174): which visit headlines the page, and how many calendar days away it is. Pure functions, synthetic data. */
class VisitHeroTest {
    private val utc = ZoneId.of("UTC")
    private fun ms(z: ZonedDateTime) = z.toInstant().toEpochMilli()
    private fun visit(id: Long, at: Long, done: Boolean = false, zone: String = "UTC") =
        AppointmentEntity(id, "ENDO", at, zone, null, null, null, 0, if (done) at else null)
    private val now = ms(ZonedDateTime.of(2026, 11, 10, 12, 0, 0, 0, utc))
    private val hour = 3_600_000L; private val day = 24 * hour

    @Test fun theEarliestFutureVisitWins() {
        val pick = VisitHero.pick(listOf(visit(1, now + 5 * day), visit(2, now + 2 * day), visit(3, now - day, done = true)), now)!!
        assertEquals(2L, pick.visit.id); assertEquals(VisitHeroKind.NEXT, pick.kind)
    }

    @Test fun aVisitExactlyNowIsStillTheNextOne() {
        val pick = VisitHero.pick(listOf(visit(1, now), visit(2, now - day, done = true)), now)!!
        assertEquals(1L, pick.visit.id); assertEquals(VisitHeroKind.NEXT, pick.kind)
    }

    @Test fun withNothingAheadTheMostRecentConfirmedVisitHeadlines() {
        val pick = VisitHero.pick(listOf(visit(1, now - 30 * day, done = true), visit(2, now - 3 * day, done = true), visit(3, now - day)), now)!!
        assertEquals(2L, pick.visit.id); assertEquals(VisitHeroKind.LAST, pick.kind)
    }

    @Test fun anUnconfirmedPastVisitNeverHeadlines() {
        assertNull(VisitHero.pick(listOf(visit(1, now - day), visit(2, now - 9 * day)), now))
        // ...even when a confirmed one is older: the confirmed one is chosen, not the newer unconfirmed one.
        assertEquals(3L, VisitHero.pick(listOf(visit(1, now - day), visit(3, now - 20 * day, done = true)), now)!!.visit.id)
    }

    @Test fun noVisitsGivesNoPick() = assertNull(VisitHero.pick(emptyList(), now))

    @Test fun todayIsZeroAndTomorrowAndYesterdayAreOneApart() {
        assertEquals(0L, VisitHero.dayOffset(now + 3 * hour, now, utc))
        assertEquals(0L, VisitHero.dayOffset(now - 11 * hour, now, utc)) // 01:00 the same day
        assertEquals(1L, VisitHero.dayOffset(now + day, now, utc))
        assertEquals(-1L, VisitHero.dayOffset(now - day, now, utc))
        assertEquals(VisitDays.Today, VisitHero.days(0)); assertEquals(VisitDays.Ahead(4), VisitHero.days(4)); assertEquals(VisitDays.Ago(9), VisitHero.days(-9))
    }

    @Test fun midnightIsTheBoundaryNotTwentyFourHours() {
        val late = ms(ZonedDateTime.of(2026, 11, 10, 23, 59, 0, 0, utc))
        assertEquals(1L, VisitHero.dayOffset(late + 2 * 60_000, late, utc)) // two minutes later is already tomorrow
        assertEquals(0L, VisitHero.dayOffset(late + 30_000, late, utc))
    }

    @Test fun monthAndYearEndsCountCalendarDays() {
        val jan31 = ms(ZonedDateTime.of(2027, 1, 31, 10, 0, 0, 0, utc)); val feb1 = ms(ZonedDateTime.of(2027, 2, 1, 9, 0, 0, 0, utc))
        assertEquals(1L, VisitHero.dayOffset(feb1, jan31, utc))
        val dec31 = ms(ZonedDateTime.of(2026, 12, 31, 23, 0, 0, 0, utc)); val jan2 = ms(ZonedDateTime.of(2027, 1, 2, 1, 0, 0, 0, utc))
        assertEquals(2L, VisitHero.dayOffset(jan2, dec31, utc)); assertEquals(-2L, VisitHero.dayOffset(dec31, jan2, utc))
        assertEquals(365L, VisitHero.dayOffset(ms(ZonedDateTime.of(2027, 11, 10, 8, 0, 0, 0, utc)), now, utc))
    }

    @Test fun daylightSavingDaysCountDatesNotHours() {
        val ny = ZoneId.of("America/New_York") // clocks go forward on 2026-03-08: that day has 23 hours
        val before = ms(ZonedDateTime.of(2026, 3, 7, 12, 0, 0, 0, ny)); val after = ms(ZonedDateTime.of(2026, 3, 9, 12, 0, 0, 0, ny))
        assertEquals(47L, (after - before) / hour)
        assertEquals(2L, VisitHero.dayOffset(after, before, ny))
        val paris = ZoneId.of("Europe/Paris") // clocks go back on 2026-10-25: that day has 25 hours
        val a = ms(ZonedDateTime.of(2026, 10, 24, 23, 30, 0, 0, paris)); val b = ms(ZonedDateTime.of(2026, 10, 26, 0, 30, 0, 0, paris))
        assertEquals(2L, VisitHero.dayOffset(b, a, paris))
        assertEquals(1L, VisitHero.dayOffset(ms(ZonedDateTime.of(2026, 10, 25, 0, 30, 0, 0, paris)), a, paris))
        assertEquals(0L, VisitHero.dayOffset(ms(ZonedDateTime.of(2026, 10, 25, 23, 0, 0, 0, paris)), ms(ZonedDateTime.of(2026, 10, 25, 1, 0, 0, 0, paris)), paris))
    }

    @Test fun theZoneDecidesWhichDayItIs() {
        val auckland = ZoneId.of("Pacific/Auckland") // UTC+13 in November
        val visitAt = ms(ZonedDateTime.of(2026, 11, 10, 23, 30, 0, 0, utc)); val nowAt = ms(ZonedDateTime.of(2026, 11, 10, 10, 0, 0, 0, utc))
        assertEquals(0L, VisitHero.dayOffset(visitAt, nowAt, utc))        // the same day in UTC
        assertEquals(1L, VisitHero.dayOffset(visitAt, nowAt, auckland))   // already tomorrow in Auckland (12:30 on the 11th vs 23:00 on the 10th)
    }

    @Test fun displayZoneIsTheVisitsOwnOnTheMedicalRouteAndTheSystemsOtherwise() {
        val paris = ZoneId.of("Europe/Paris"); val system = ZoneId.of("Asia/Tokyo")
        assertEquals(paris, VisitHero.displayZone(visit(1, now, zone = "Europe/Paris"), medical = true, system = system))
        assertEquals(system, VisitHero.displayZone(visit(1, now, zone = "Europe/Paris"), medical = false, system = system))
        assertEquals(system, VisitHero.displayZone(visit(1, now, zone = "Not/AZone"), medical = true, system = system))
    }
}
