package net.plainnotes.app.data

import net.plainnotes.app.domain.SlotState
import net.plainnotes.app.domain.TimelineEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * One stock forecast for one medication (B4, roadmap R-09/D-3), shared by the stock page, the calendar and stock
 * reminders. It walks the medication's open doses (already produced by the domain Timeline, so skips, reschedules,
 * dose overrides, plan versions and taken records are applied there) in time order and subtracts each planned dose
 * from the tracked stock. Taken doses are not subtracted again: they were already allocated to the packages.
 */
enum class StockUnknownReason { UNKNOWN_UNIT, UNKNOWN_SOURCE, MIXED_UNITS, UNALLOCATED, STOCK_EFFECT_UNKNOWN, INVALID_NEEDS_REVIEW, EXPIRY_NOT_FROZEN }

data class StockForecast(
    val medicationId: Long,
    /** Remaining amount in open and sealed packages, or null when no package is tracked. */
    val available: Double?,
    /** Open doses inside the forecast window. */
    val plannedDoses: Int,
    /** Time of the last dose the stock still covers. */
    val lastCovered: Instant?,
    /** Time of the first dose the stock cannot cover. */
    val firstShort: Instant?,
    val uncertainty:Set<StockUnknownReason> = emptySet(),
    val physicalBalances:List<ConfirmedPhysicalStock> = emptyList(),
    /** Same immutable projection version is consumed by pages and reminders; not a claim of medical verification. */
    val snapshotSha256:String?=null,
) {
    enum class Status {
        UNKNOWN,
        /** No open or sealed package: nothing to compare against. */
        UNTRACKED,
        /** Stock is tracked but no dose is planned in the window: no estimate. */
        NO_PLAN,
        /** A planned dose in the window is not covered. */
        SHORT,
        /** Every planned dose in the window is covered; known only up to [lastCovered]. */
        COVERED_THROUGH_WINDOW,
    }

    val status: Status get() = when {
        uncertainty.isNotEmpty() -> Status.UNKNOWN
        available == null -> Status.UNTRACKED
        firstShort != null -> Status.SHORT
        plannedDoses == 0 -> Status.NO_PLAN
        else -> Status.COVERED_THROUGH_WINDOW
    }

    /**
     * Calendar days from [today] to the local date of the first uncovered dose: 0 means a dose today is not covered,
     * 1 tomorrow. Calendar dates (not 24-hour blocks) so the number matches the date shown next to it.
     */
    fun daysUntilShort(today: LocalDate, zone: ZoneId): Int? =
        firstShort?.takeIf {uncertainty.isEmpty()}?.let { ChronoUnit.DAYS.between(today, it.atZone(zone).toLocalDate()).coerceAtLeast(0).toInt() }

    /** Low stock: a planned dose from today through the next [StockForecasts.LOW_STOCK_DAYS] - 1 days is not covered. */
    fun isLow(today: LocalDate, zone: ZoneId): Boolean = daysUntilShort(today, zone)?.let { it < StockForecasts.LOW_STOCK_DAYS } == true
}

object StockForecasts {
    /** A dose not covered within today and the following six days is low stock (page label, calendar and reminder). */
    const val LOW_STOCK_DAYS = 7
    /** Open doses the forecast walks; same window as the calendar's planned doses. */
    const val WINDOW_DAYS = 366L
    private val OPEN = setOf(SlotState.PENDING, SlotState.SOON, SlotState.OVERDUE)
    private const val EPSILON = 1e-9

    /** Remaining amount across open (capacity minus used) and sealed (full) packages; null when none is tracked. */
    fun available(medicationId: Long, containers: List<ContainerEntity>): Double? {
        if(containers.any {it.medication_id==medicationId && it.state in setOf("SEALED","IN_USE","EMPTY") && it.inventory_encoding!=InventoryEncoding.LEGACY})return null
        val mine = containers.filter { it.medication_id == medicationId && it.state in setOf("IN_USE", "SEALED") }
        if (mine.isEmpty()) return null
        return mine.sumOf { if (it.state == "IN_USE") it.capacity - it.used_amount else it.capacity }.coerceAtLeast(0.0)
    }

    fun forecast(medicationId: Long, containers: List<ContainerEntity>, doses: List<TimelineEntry>): StockForecast {
        val start = available(medicationId, containers)
        val mine = doses.filter { it.slot.medicationId == medicationId && it.state in OPEN && !it.slot.skipped }
            .distinctBy { it.slot.key }.sortedWith(compareBy<TimelineEntry> { it.slot.at }.thenBy { it.slot.key })
        val tracked=containers.filter{it.medication_id==medicationId && it.state in setOf("SEALED","IN_USE","EMPTY")}
        if(tracked.any{it.inventory_encoding!=InventoryEncoding.LEGACY}) {
            val reasons=setOf(StockUnknownReason.UNKNOWN_UNIT,StockUnknownReason.UNKNOWN_SOURCE,StockUnknownReason.EXPIRY_NOT_FROZEN)+
                if(tracked.any{it.inventory_encoding==InventoryEncoding.LEGACY})setOf(StockUnknownReason.MIXED_UNITS) else emptySet()
            return StockForecast(medicationId,null,mine.size,null,null,reasons)
        }
        if (start == null) return StockForecast(medicationId, null, mine.size, null, null)
        var left = start; var last: Instant? = null; var short: Instant? = null
        for (e in mine) {
            if (left + EPSILON >= e.slot.dose) { left -= e.slot.dose; last = e.slot.at } else { short = e.slot.at; break }
        }
        return StockForecast(medicationId, start, mine.size, last, short)
    }

    /** Forecasts for active medications, keyed by medication id. */
    fun forecastAll(meds: List<MedicationEntity>, containers: List<ContainerEntity>, doses: List<TimelineEntry>): Map<Long, StockForecast> =
        meds.filter { it.active }.associate { it.id to forecast(it.id, containers, doses) }
}
