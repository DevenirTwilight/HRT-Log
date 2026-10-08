package net.plainnotes.app.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** REQUIREMENTS §35a: O1 with 14 sustained days, 30-day gaps, and the reduction rule. */
class SustainedPatternsTest {
    private val d0 = LocalDate.of(2026, 6, 10)
    private fun days(range: IntRange, doses: (Int) -> List<Double>) = range.map { LoggedDay(d0.plusDays(it.toLong()), doses(it)) }
    private fun seg(days: List<LoggedDay>) = SustainedPatterns.segment(days)

    @Test fun shortIncreaseThenBackStaysOneSegment() {
        val s = seg(days(0..60) { if (it in 20..22) listOf(1.0, 1.0, 1.0) else listOf(1.0, 1.0) })
        assertEquals(1, s.size); assertEquals(listOf(1.0, 1.0), s.single().doses); assertEquals(1, s.single().interval)
    }
    @Test fun thirteenDaysIsShortFourteenIsSustained() {
        fun run(len: Int) = seg(days(0..60) { if (it in 20 until 20 + len) listOf(2.0, 2.0) else if (it < 20) listOf(1.0, 1.0) else listOf(1.0, 1.0) })
        // A temporary change that returns to the old pattern is broken by the old days.
        assertEquals(1, run(13).size)
        val sustained = seg(days(0..60) { if (it >= 20) listOf(2.0, 2.0) else listOf(1.0, 1.0) })
        assertEquals(listOf(listOf(1.0, 1.0), listOf(2.0, 2.0)), sustained.map { it.doses })
        assertEquals(d0.plusDays(20), sustained[1].first)
    }
    @Test fun aChangeNearTheEndIsNotYetSustained() {
        val s = seg(days(0..30) { if (it >= 25) listOf(2.0, 2.0) else listOf(1.0, 1.0) })
        assertEquals(1, s.size)
    }
    @Test fun missedLogsDoNotSplitAndAreNotAReduction() {
        val s = seg(days(0..60) { if (it % 3 == 0) listOf(1.0) else listOf(1.0, 1.0) })
        assertEquals(1, s.size); assertEquals(listOf(1.0, 1.0), s.single().doses)
    }
    @Test fun reductionNeedsFourteenDaysWithoutReachingTheOldCount() {
        // Once daily from day 30, but day 35 still has two doses: not yet sustained at day 30.
        val withReturn = seg(days(0..80) { if (it >= 30 && it != 35) listOf(1.0) else listOf(1.0, 1.0) })
        assertEquals(d0.plusDays(36), withReturn.last().first)
        val clean = seg(days(0..80) { if (it >= 30) listOf(1.0) else listOf(1.0, 1.0) })
        assertEquals(listOf(listOf(1.0, 1.0), listOf(1.0)), clean.map { it.doses }); assertEquals(d0.plusDays(30), clean[1].first)
    }
    @Test fun gradualTaperShorterThanFourteenDaysPerStepIsNotSplit() {
        val s = seg(days(0..60) { when { it < 20 -> listOf(2.0); it < 25 -> listOf(1.5); it < 30 -> listOf(1.0); it < 35 -> listOf(0.5); else -> listOf(0.5) } })
        // Only the final step, held for 14+ days, can become a new segment.
        assertTrue(s.none { it.doses == listOf(1.5) || it.doses == listOf(1.0) })
    }
    @Test fun gapOfThirtyDaysSeparatesButTwentyNineDoesNot() {
        val a = days(0..20) { listOf(1.0, 1.0) }
        assertEquals(1, seg(a + days(50..70) { listOf(1.0, 1.0) }).size) // 29 empty days
        assertEquals(2, seg(a + days(51..70) { listOf(1.0, 1.0) }).size) // 30 empty days
    }
    @Test fun sparseRegularScheduleKeepsItsInterval() {
        val weekly = (0..12).map { LoggedDay(d0.plusDays(7L * it), listOf(5.0)) }
        val s = seg(weekly)
        assertEquals(1, s.size); assertEquals(7, s.single().interval)
    }
    @Test fun irregularHistoryKeepsDosesButNoFrequency() {
        val s = seg(listOf(0, 3, 4, 9, 11, 17).map { LoggedDay(d0.plusDays(it.toLong()), listOf(2.0)) })
        assertEquals(1, s.size); assertFalse(s.single().frequencyKnown)
    }
    @Test fun dailyToEveryOtherDayIsAFrequencyChangeOnlyWhenSustained() {
        val daily = (0..19).map { LoggedDay(d0.plusDays(it.toLong()), listOf(2.0)) }
        val alternate = (20..60 step 2).map { LoggedDay(d0.plusDays(it.toLong()), listOf(2.0)) }
        assertEquals(listOf(1, 2), seg(daily + alternate).map { it.interval })
        // A week of alternate days followed by daily again is missed logging, not a new frequency.
        val back = (20..26 step 2).map { LoggedDay(d0.plusDays(it.toLong()), listOf(2.0)) } + (27..60).map { LoggedDay(d0.plusDays(it.toLong()), listOf(2.0)) }
        assertEquals(listOf(1), seg(daily + back).map { it.interval })
    }
    @Test fun twelveHourlyAndTwiceDailyAreTheSameDailyPattern() {
        // Grouping is by the record's local day, so 12-hour spacing gives the same two-dose day.
        assertEquals(listOf(1.0, 1.0), seg(days(0..20) { listOf(1.0, 1.0) }).single().doses)
    }
}
