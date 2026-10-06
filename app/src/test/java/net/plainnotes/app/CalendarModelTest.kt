package net.plainnotes.app

import net.plainnotes.app.data.*
import net.plainnotes.app.domain.Slot
import net.plainnotes.app.domain.SlotState
import net.plainnotes.app.domain.TimelineEntry
import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CalendarModelTest {
    private val zone = ZoneId.of("UTC")
    private val t0 = Instant.parse("2026-03-10T08:00:00Z")
    private val med = MedicationEntity(1, "Synthetic", "E2", "SUBLINGUAL", "MG", 2.0, 30.0, null, 0, 60, false, null, true, true, 0)
    private fun open(i: Int, dose: Double = 2.0) = t0.plusSeconds(i * 12L * 3600).let { TimelineEntry(Slot("wall:1@$it", 1, 1, it, it, zone, dose, 0, 60, t0), SlotState.PENDING) }
    private val upcoming = (0 until 20).map { open(it) } // twice a day for 10 days

    @Test fun forecastWalksScheduledDoses() {
        val boxes = listOf(ContainerEntity(1, 1, 10.0, 4.0, 4.0, "2026-03-01", "IN_USE"), ContainerEntity(2, 1, 10.0, 0.0, 0.0, null, "SEALED"))
        val f = forecast(listOf(med), boxes, upcoming).getValue(1)
        assertEquals(16.0, f.remaining, 1e-9) // 6 left in the open box + 10 sealed = 8 doses
        assertEquals(open(7).slot.at, f.lastCovered); assertEquals(open(8).slot.at, f.firstShort)
        assertTrue(forecast(listOf(med), listOf(ContainerEntity(3, 1, 100.0, 0.0, 0.0, null, "SEALED")), upcoming).getValue(1).beyondHorizon)
        assertNull(forecast(listOf(med), emptyList(), upcoming)[1]) // stock not tracked: no guess
        assertNull(forecast(listOf(med.copy(active = false)), boxes, upcoming)[1])
    }

    @Test fun eightyFourMilligramsCoversExactlyFortyTwoTwiceDailyDoses() {
        val boxes=listOf(ContainerEntity(1,1,84.0,0.0,0.0,null,"SEALED"))
        val f=forecast(listOf(med),boxes,(0 until 732).map{open(it)}).getValue(1)
        assertEquals(open(41).slot.at,f.lastCovered)
        assertEquals(21,f.daysLeft(t0.minusSeconds(3600)))
        assertEquals(t0.atZone(zone).toLocalDate().plusDays(21),f.firstShort!!.atZone(zone).toLocalDate())
        // Already completed slots cannot consume stock again.
        val completed=upcoming.take(2).map{TimelineEntry(it.slot,SlotState.ON_TIME)}
        assertEquals(open(41).slot.at,forecast(listOf(med),boxes,completed+(0 until 732).map{open(it)}.drop(2)).getValue(1).lastCovered!!.minusSeconds(86400))
    }

    @Test fun dayKindsFromRecordsAndOpenSlots() {
        fun rec(day: String, status: String) = RecordEntity(medication_id = 1, taken_utc = if (status in listOf("ON_TIME", "LATE")) Instant.parse("${day}T08:00:00Z").toEpochMilli() else null,
            taken_zone = if (status in listOf("ON_TIME", "LATE")) "UTC" else null, actual_dose = if (status in listOf("ON_TIME", "LATE")) 2.0 else null,
            scheduled_utc = Instant.parse("${day}T08:00:00Z").toEpochMilli(), scheduled_zone = "UTC", status = status, origin = "APP", revision = 1, config_snapshot = "{}")
        val records = listOf(rec("2026-03-01", "ON_TIME"), rec("2026-03-01", "LATE"), rec("2026-03-02", "ON_TIME"), rec("2026-03-02", "MISSED"),
            rec("2026-03-03", "MISSED"), rec("2026-03-04", "ON_TIME").copy(deleted_at_utc = 1))
        val boxes = listOf(ContainerEntity(1, 1, 10.0, 4.0, 4.0, "2026-03-01", "IN_USE"))
        val f = forecast(listOf(med), boxes, upcoming)
        val days = dayInfos(records, upcoming, emptyList(), f, zone)
        fun kind(d: String) = days[LocalDate.parse(d)]?.kind ?: DayKind.NONE
        assertEquals(DayKind.TAKEN, kind("2026-03-01")); assertEquals(DayKind.PARTIAL, kind("2026-03-02"))
        assertEquals(DayKind.MISSED, kind("2026-03-03")); assertEquals(DayKind.NONE, kind("2026-03-04")) // deleted record ignored
        assertEquals(DayKind.PLANNED, kind("2026-03-10"))
        // 6 mg left covers three 2 mg doses: the last one is 11 March 08:00, the 20:00 dose that day is short.
        assertEquals(setOf(1L), days.getValue(LocalDate.parse("2026-03-11")).runOut)
        assertEquals(1, days.getValue(LocalDate.parse("2026-03-11")).short); assertEquals(2, days.getValue(LocalDate.parse("2026-03-12")).short)
        assertEquals(0, days.getValue(LocalDate.parse("2026-03-10")).short)
    }
}
