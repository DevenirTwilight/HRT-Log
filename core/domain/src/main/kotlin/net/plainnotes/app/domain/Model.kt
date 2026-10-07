package net.plainnotes.app.domain

import java.time.*

enum class RuleKind { EVERY_N_DAYS, EVERY_N_HOURS, WEEKLY }
enum class DoseStatus { ON_TIME, LATE, MISSED, SKIPPED, UNCONFIRMED }
enum class SlotState { PENDING, SOON, OVERDUE, ON_TIME, LATE, MISSED, SKIPPED, UNCONFIRMED }
data class RuleTime(val time: LocalTime, val dose: Double? = null) {
    init { require(time.nano == 0); require(dose == null || dose.isFinite() && dose > 0) }
}
data class ScheduleRule(
    val id: Long, val medicationId: Long, val kind: RuleKind, val interval: Int,
    val anchorDate: LocalDate?, val anchorInstant: Instant?, val zone: ZoneId,
    val from: Instant, val until: Instant? = null, val trackingFrom: Instant = from,
    val times: List<RuleTime> = emptyList(), val weekdays: Set<DayOfWeek> = emptySet(),
    val dose: Double, val soonMinutes: Int, val lateMinutes: Int,
) {
    init {
        require(id > 0 && medicationId > 0 && interval > 0 && interval <= 36500)
        require(until == null || until > from); require(trackingFrom >= from)
        require(dose.isFinite() && dose > 0 && soonMinutes >= 0 && lateMinutes >= 0)
        require(times.map { it.time }.distinct().size == times.size)
        if (kind == RuleKind.EVERY_N_HOURS) require(anchorInstant != null && anchorDate == null && times.isEmpty())
        else require(anchorDate != null && anchorInstant == null && times.isNotEmpty())
        if (kind == RuleKind.WEEKLY) require(weekdays.isNotEmpty())
    }
}
data class Slot(
    val key: String, val ruleId: Long, val medicationId: Long,
    val original: Instant, val at: Instant, val zone: ZoneId, val dose: Double,
    val soonMinutes: Int, val lateMinutes: Int, val trackingFrom: Instant, val skipped: Boolean = false,
    val originalZone: ZoneId = zone, val originalDose: Double = dose,
)
data class SlotOverride(val key: String, val rescheduled: Instant? = null, val zone: ZoneId? = null,
                        val dose: Double? = null, val skipped: Boolean = false) {
    init { require((rescheduled == null) == (zone == null)); require(dose == null || dose.isFinite() && dose > 0) }
    val isDefault get() = rescheduled == null && dose == null && !skipped
    fun apply(slot: Slot): Slot { require(key == slot.key); return slot.copy(at = rescheduled ?: slot.original, zone = zone ?: slot.originalZone, dose = dose ?: slot.originalDose, skipped = skipped) }
}
data class DoseRecord(val key: String?, val medicationId: Long, val status: DoseStatus,
                      val taken: Instant? = null, val takenZone: ZoneId? = null,
                      val actualDose: Double? = null, val site: String? = null,
                      val imported: Boolean = false, val deleted: Boolean = false,
                      val scheduled: Instant? = null, val scheduledZone: ZoneId? = null,
                      val plannedDose: Double? = null, val lateSnapshot: Int? = null, val ruleVersionId: Long? = null) {
    init {
        require((taken == null) == (takenZone == null))
        require((scheduled == null) == (scheduledZone == null))
        if (status == DoseStatus.MISSED || status == DoseStatus.SKIPPED || status == DoseStatus.UNCONFIRMED)
            require(taken == null && actualDose == null && site == null)
        else { require(taken != null); require(actualDose != null || imported) }
        require(actualDose == null || actualDose.isFinite() && actualDose > 0)
    }
}
data class TimelineEntry(val slot: Slot, val state: SlotState)
