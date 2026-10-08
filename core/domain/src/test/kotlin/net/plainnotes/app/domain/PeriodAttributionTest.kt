package net.plainnotes.app.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class PeriodAttributionTest {
    private val twice = TherapyStandard("E2", "E2", "SUBLINGUAL", "MG", null, "EVERY_N_DAYS", 1, 0, listOf(1.0, 1.0))
    private val day = LocalDate.of(2026, 7, 1)
    private fun r(id: Long, hour: Int, dose: Double = 1.0, scheduled: Boolean = false, standard: TherapyStandard? = twice, pending: Boolean = false, extra: Boolean = false) =
        AttributionInput(id, Instant.parse("2026-07-01T%02d:00:00Z".format(hour)), day, dose, scheduled, if (standard != null) 7L else null, standard, pending, extra)

    @Test fun matchingRecordsHaveNoLabelAndTheThirdIsExtra() {
        val l = PeriodAttribution.labels(listOf(r(1, 8), r(3, 21), r(2, 20)))
        assertNull(l[1]); assertNull(l[2]); assertEquals(setOf(RecordLabel.EXTRA_INFERRED), l[3])
    }
    @Test fun userMarkedExtraIsLeftOutOfTheCount() {
        val l = PeriodAttribution.labels(listOf(r(1, 8), r(2, 12, extra = true), r(3, 20)))
        assertEquals(setOf(RecordLabel.EXTRA_USER), l[2]); assertNull(l[1]); assertNull(l[3])
    }
    @Test fun scheduledRecordsFillTheDayFirst() {
        val l = PeriodAttribution.labels(listOf(r(1, 7), r(2, 8, scheduled = true), r(3, 20, scheduled = true)))
        assertEquals(setOf(RecordLabel.EXTRA_INFERRED), l[1]); assertNull(l[2]); assertNull(l[3])
    }
    @Test fun differentDoseIsMarkedButFewerRecordsAreNot() {
        val l = PeriodAttribution.labels(listOf(r(1, 8, dose = 1.5)))
        assertEquals(setOf(RecordLabel.DOSE_DIFFERS), l[1])
        assertTrue(PeriodAttribution.labels(listOf(r(2, 8))).isEmpty())
    }
    @Test fun unknownAndPendingNeverSayUnplanned() {
        val l = PeriodAttribution.labels(listOf(r(1, 8, standard = null), r(2, 9, standard = null, pending = true)))
        assertEquals(setOf(RecordLabel.REGIMEN_UNKNOWN), l[1]); assertEquals(setOf(RecordLabel.PENDING_PERIOD), l[2])
    }
    @Test fun unevenDosesOnlyCheckMembershipAndEveryNDaysOnlySameDay() {
        val uneven = twice.copy(doses = listOf(1.0, 2.0))
        assertTrue(PeriodAttribution.labels(listOf(r(1, 8, 2.0, standard = uneven), r(2, 20, 2.0, standard = uneven))).isEmpty())
        val every3 = twice.copy(interval = 3, doses = listOf(5.0))
        assertEquals(setOf(RecordLabel.EXTRA_INFERRED), PeriodAttribution.labels(listOf(r(1, 8, 5.0, standard = every3), r(2, 20, 5.0, standard = every3)))[2])
    }
}
