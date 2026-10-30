package net.plainnotes.app.ui

import net.plainnotes.app.data.AppointmentEntity
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** UI-14 (REQUIREMENTS §174): which visit the list's header card shows, and how many calendar days away it is. */
enum class VisitHeroKind { NEXT, LAST }
data class VisitHeroPick(val visit: AppointmentEntity, val kind: VisitHeroKind)

/** What the header card's day tag says: today, N days ahead, or N days ago. */
sealed interface VisitDays {
    data object Today : VisitDays
    data class Ahead(val days: Int) : VisitDays
    data class Ago(val days: Int) : VisitDays
}

object VisitHero {
    /**
     * The earliest visit that has not happened yet (`at_utc >= now`) is the next visit. With none, it is the most recent visit the user
     * has confirmed took place (`completed_utc != null`); a past visit that was never confirmed is listed below but never headlines the page.
     * Null when there is neither (the page then shows its empty state).
     */
    fun pick(appointments: List<AppointmentEntity>, nowMs: Long): VisitHeroPick? {
        appointments.filter { it.at_utc >= nowMs }.minByOrNull { it.at_utc }?.let { return VisitHeroPick(it, VisitHeroKind.NEXT) }
        return appointments.filter { it.at_utc < nowMs && it.completed_utc != null }.maxByOrNull { it.at_utc }?.let { VisitHeroPick(it, VisitHeroKind.LAST) }
    }

    /** The zone a visit is shown in: its own zone on the medical-record route (like the detail page), the system zone otherwise. */
    fun displayZone(visit: AppointmentEntity, medical: Boolean, system: ZoneId): ZoneId =
        if (medical) runCatching { ZoneId.of(visit.at_zone) }.getOrDefault(system) else system

    /**
     * Whole calendar days from today to the visit's day, both read in [zone] (so the tag never counts a different day than the card shows).
     * 0 is today, positive is ahead, negative is past. Daylight-saving days and year or month ends need no special case because the
     * difference is between local dates, not between 24-hour spans.
     */
    fun dayOffset(visitUtc: Long, nowMs: Long, zone: ZoneId): Long =
        ChronoUnit.DAYS.between(Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate(), Instant.ofEpochMilli(visitUtc).atZone(zone).toLocalDate())

    fun days(offset: Long): VisitDays = when {
        offset == 0L -> VisitDays.Today
        offset > 0 -> VisitDays.Ahead(offset.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        else -> VisitDays.Ago((-offset).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }
}
