package net.plainnotes.app.ui

import net.plainnotes.app.data.AppointmentEntity
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.data.StockForecast
import net.plainnotes.app.data.unconfirmed
import net.plainnotes.app.domain.SlotState
import net.plainnotes.app.domain.TimelineEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Colour class of a calendar day. Taken/missed come from records, planned from open slots. */
enum class DayKind { NONE, TAKEN, PARTIAL, MISSED, PLANNED, UNCONFIRMED }

class DayInfo(val date: LocalDate, val taken: Int, val missed: Int, val skipped: Int, val open: Int, val appointments: Int,
              /** Medications whose last covered dose falls on this day. */ val runOut: Set<Long>,
              /** Open doses on this day that current stock no longer covers. */ val short: Int,val unconfirmed:Int=0) {
    val kind get() = when {
        unconfirmed > 0 -> DayKind.UNCONFIRMED
        missed > 0 -> if (taken > 0) DayKind.PARTIAL else DayKind.MISSED
        open > 0 -> DayKind.PLANNED
        taken > 0 -> DayKind.TAKEN
        else -> DayKind.NONE
    }
}

val OPEN_STATES = setOf(SlotState.PENDING, SlotState.SOON, SlotState.OVERDUE)

fun dayInfos(records: List<RecordEntity>, upcoming: List<TimelineEntry>, appointments: List<AppointmentEntity>, runOut: Map<Long, StockForecast>,
             zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, DayInfo> {
    class Acc { var taken = 0; var missed = 0; var skipped = 0; var open = 0; var unconfirmed = 0; var appts = 0; var short = 0; val out = HashSet<Long>() }
    val acc = HashMap<LocalDate, Acc>()
    fun at(i: Instant) = acc.getOrPut(i.atZone(zone).toLocalDate()) { Acc() }
    records.filter { it.deleted_at_utc == null }.forEach { r ->
        val t = Instant.ofEpochMilli(r.taken_utc ?: r.scheduled_utc ?: return@forEach)
        when (r.status) { "ON_TIME", "LATE" -> at(t).taken++; "MISSED" -> if(r.unconfirmed)at(t).unconfirmed++ else at(t).missed++; "SKIPPED" -> at(t).skipped++ }
    }
    upcoming.filter { it.state in OPEN_STATES }.distinctBy { it.slot.key }.forEach { e ->
        val a = at(e.slot.at); a.open++
        val f = runOut[e.slot.medicationId]; if (f?.firstShort != null && !e.slot.at.isBefore(f.firstShort)) a.short++
    }
    appointments.forEach { at(Instant.ofEpochMilli(it.at_utc)).appts++ }
    runOut.values.forEach { f -> val last = f.lastCovered; if (f.firstShort != null && last != null) at(last).out += f.medicationId }
    return acc.mapValues { (d, a) -> DayInfo(d, a.taken, a.missed, a.skipped, a.open, a.appts, a.out, a.short,a.unconfirmed) }
}
