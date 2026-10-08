package net.plainnotes.app.domain

import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

/** Labels for a taken record without an original scheduled time (REQUIREMENTS §35/35a). No label means it matches its period. */
enum class RecordLabel { EXTRA_INFERRED, EXTRA_USER, DOSE_DIFFERS, REGIMEN_UNKNOWN, PENDING_PERIOD }

/**
 * One taken record. [standard] is the confirmed or saved standard covering it (null when none), [intervalId] identifies
 * that interval, [pending] is true when only an unconfirmed candidate covers it. [date] is the local day in the record's zone.
 */
data class AttributionInput(val recordId: Long, val at: Instant, val date: LocalDate, val dose: Double?, val scheduled: Boolean,
                            val intervalId: Long?, val standard: TherapyStandard?, val pending: Boolean, val userExtra: Boolean)

object PeriodAttribution {
    private const val EPSILON = 1e-9

    /** Doses expected on one dosing day, or null when the standard has no per-day count (e.g. every 36 hours). */
    fun perDay(s: TherapyStandard): Int? = when (s.kind) { "EVERY_N_DAYS", "WEEKLY" -> s.doses.size.takeIf { it > 0 }; else -> null }

    fun labels(inputs: List<AttributionInput>): Map<Long, Set<RecordLabel>> {
        val result = mutableMapOf<Long, MutableSet<RecordLabel>>()
        fun add(id: Long, label: RecordLabel) { result.getOrPut(id) { mutableSetOf() } += label }
        inputs.forEach { r ->
            when {
                r.userExtra -> add(r.recordId, RecordLabel.EXTRA_USER)
                r.scheduled -> Unit // Scheduled records keep on-time / late / missed.
                r.standard == null -> add(r.recordId, if (r.pending) RecordLabel.PENDING_PERIOD else RecordLabel.REGIMEN_UNKNOWN)
                r.dose != null && r.standard.doses.none { abs(it - r.dose) < EPSILON } -> add(r.recordId, RecordLabel.DOSE_DIFFERS)
            }
        }
        // Extra doses: per interval and day, scheduled records fill the day first, then the rest in time order.
        // Records the user marked as extra are left out of the count, so they never push another record over the limit.
        inputs.filter { it.standard != null && it.intervalId != null && !it.userExtra }.groupBy { it.intervalId to it.date }.values.forEach { day ->
            val limit = perDay(day.first().standard!!) ?: return@forEach
            val free = (limit - day.count { it.scheduled }).coerceAtLeast(0)
            day.filter { !it.scheduled }.sortedWith(compareBy({ it.at }, { it.recordId })).drop(free).forEach { add(it.recordId, RecordLabel.EXTRA_INFERRED) }
        }
        return result
    }
}
