package net.plainnotes.app.reminder

import java.time.LocalDate
import java.time.ZoneId
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic current-behavior baseline; does not attach the shadow protocol to notification code. */
class Ansm03b0ForecastBoundaryTest {
    @Test fun calendarDaysAndSevenDayReminderAgreeAcrossBothParisDstTransitions() {
        val zone = ZoneId.of("Europe/Paris")
        val med = MedicationEntity(1, "Synthetic", "OTHER", null, "TABLET", 1.0, 2.0, null, 0, 60, false, null, true, true, 0)
        assertEquals(366L, StockForecasts.WINDOW_DAYS)
        assertEquals(7, StockAlerts.LOW_STOCK_DAYS)
        for (today in listOf(LocalDate.parse("2026-03-27"), LocalDate.parse("2026-10-23"))) {
            val doses = (0..8).map { day ->
                val t = today.plusDays(day.toLong()).atTime(9, 0).atZone(zone).toInstant()
                TimelineEntry(Slot("SYNTH-$day", 1, 1, t, t, zone, 1.0, 0, 60, t), SlotState.PENDING)
            }
            for (capacity in listOf(6.0, 7.0)) {
                val boxes = listOf(ContainerEntity(1, 1, capacity, 0.0, 0.0, null, "SEALED"))
                val forecast = StockForecasts.forecast(1, boxes, doses)
                assertEquals(capacity.toInt(), forecast.daysUntilShort(today, zone))
                assertEquals(today.plusDays(capacity.toLong()), forecast.firstShort!!.atZone(zone).toLocalDate())
                val due = StockAlerts.due(listOf(med), boxes, mapOf(1L to forecast), today, zone)
                assertEquals(capacity < 7, due.any { it.kind == StockAlerts.Kind.LOW })
            }
            val unknown = StockForecasts.forecast(1, emptyList(), doses)
            assertEquals(StockForecast.Status.UNTRACKED, unknown.status)
            assertNull(unknown.available); assertNull(unknown.daysUntilShort(today, zone))
            assertTrue(StockAlerts.due(listOf(med), emptyList(), mapOf(1L to unknown), today, zone).isEmpty())
        }
    }
}
