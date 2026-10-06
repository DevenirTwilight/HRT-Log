package net.plainnotes.app

import net.plainnotes.app.ui.MAX_TIMES_PER_DAY
import net.plainnotes.app.ui.evenTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class TimesPerDayTest {
    private fun t(s: String) = LocalTime.parse(s)

    @Test fun commonCounts() {
        assertEquals(listOf(t("09:00")), evenTimes(1))
        assertEquals(listOf(t("08:00"), t("20:00")), evenTimes(2))
        assertEquals(listOf(t("08:00"), t("14:00"), t("20:00")), evenTimes(3))
        assertEquals(listOf(t("08:00"), t("12:00"), t("16:00"), t("20:00")), evenTimes(4))
    }

    @Test fun everyCountGivesDistinctSortedTimesWithinTheDay() {
        for (n in 1..MAX_TIMES_PER_DAY) {
            val times = evenTimes(n)
            assertEquals(n, times.size)
            assertEquals(times.sorted(), times)
            assertTrue(times.all { it.minute % 30 == 0 && it >= t("08:00") && it <= t("20:00") })
        }
    }
}
