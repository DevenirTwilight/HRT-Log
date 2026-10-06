package net.plainnotes.app.reminder

import net.plainnotes.app.data.ContainerEntity
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.domain.Slot
import net.plainnotes.app.domain.SlotState
import net.plainnotes.app.domain.TimelineEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StockAlertsTest {
    private val today = LocalDate.parse("2026-03-10")
    private val med = MedicationEntity(1, "Synthetic", "E2", "ORAL", "MG", 2.0, 28.0, 30, 0, 60, false, null, true, true, 0)
    private val week = (0 until 14).map { i -> val t = Instant.parse("2026-03-10T08:00:00Z").plusSeconds(i * 12L * 3600)
        TimelineEntry(Slot("wall:1@$t", 1, 1, t, t, ZoneId.of("UTC"), 2.0, 0, 60, t), SlotState.PENDING) } // 28 mg over 7 days
    private fun box(used: Double, opened: String? = "2026-03-01", state: String = "IN_USE") = ContainerEntity(1, 1, 28.0, used, used, opened, state)
    private fun kinds(meds: List<MedicationEntity>, boxes: List<ContainerEntity>) = StockAlerts.due(meds, boxes, week, today).map { it.kind }

    @Test fun lowWhenStockDoesNotCoverTheWeek() {
        assertEquals(emptyList<StockAlerts.Kind>(), kinds(listOf(med), listOf(box(0.0))))          // 28 left, 28 needed
        assertEquals(listOf(StockAlerts.Kind.LOW), kinds(listOf(med), listOf(box(2.0))))           // 26 left
        assertEquals(emptyList<StockAlerts.Kind>(), kinds(listOf(med), listOf(box(2.0), box(0.0, null, "SEALED")))) // sealed box counts
        assertEquals(emptyList<StockAlerts.Kind>(), kinds(listOf(med), emptyList()))               // untracked: no guess
        assertEquals(emptyList<StockAlerts.Kind>(), kinds(listOf(med.copy(notifications_on = false)), listOf(box(20.0))))
    }

    @Test fun expiryWithinThreeDaysOfOpeningLimit() {
        assertEquals(listOf(StockAlerts.Kind.EXPIRY), kinds(listOf(med), listOf(box(0.0, "2026-02-11"))))  // expires 13 March
        assertEquals(emptyList<StockAlerts.Kind>(), kinds(listOf(med), listOf(box(0.0, "2026-02-08").copy(state = "SEALED", opened_on = null), box(0.0, "2026-02-20"))))
    }
}
