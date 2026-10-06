package net.plainnotes.app.domain

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object ScheduleEngine {
    private val format = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")
    fun wallInstant(local: LocalDateTime, zone: ZoneId): Instant {
        val offsets = zone.rules.getValidOffsets(local)
        return if (offsets.isEmpty()) zone.rules.getTransition(local).dateTimeAfter.atZone(zone).toInstant()
        else local.toInstant(offsets.first())
    }
    fun validateVersions(rules: List<ScheduleRule>) {
        rules.groupBy { it.medicationId }.values.forEach { group ->
            group.sortedBy { it.from }.zipWithNext().forEach { (a,b) -> require(a.until != null && a.until <= b.from) { "Overlapping rule versions" } }
        }
    }
    fun expand(rule: ScheduleRule, from: Instant, to: Instant, zone: ZoneId): List<Slot> {
        require(to >= from)
        val lo = maxOf(from, rule.from); val hi = minOf(to, rule.until ?: to)
        if (hi <= lo) return emptyList()
        val out = mutableListOf<Slot>()
        if (rule.kind == RuleKind.EVERY_N_HOURS) {
            val anchor = rule.anchorInstant!!; val step = rule.interval.toLong() * 3600
            var k = maxOf(0L, Math.floorDiv(Duration.between(anchor, lo).seconds, step))
            while (true) {
                val at = anchor.plusSeconds(Math.multiplyExact(k,step))
                if (at >= hi) break
                if (at >= lo) out += slot(rule, "elapsed:${rule.id}#$k", at, zone, rule.dose)
                k++
            }
        } else {
            var date = lo.atZone(zone).toLocalDate().minusDays(1)
            val last = hi.atZone(zone).toLocalDate().plusDays(1)
            while (date <= last) {
                if (matches(rule,date)) rule.times.forEach { rt ->
                    val local = date.atTime(rt.time); val at = wallInstant(local,zone)
                    if (at >= lo && at < hi) out += slot(rule,"wall:${rule.id}@${local.format(format)}",at,zone,rt.dose ?: rule.dose)
                }
                date = date.plusDays(1)
            }
        }
        return out.sortedWith(compareBy<Slot> { it.at }.thenBy { it.key })
    }
    fun resolve(rule: ScheduleRule, key: String, zone: ZoneId): Slot? = runCatching {
        val at: Instant; val dose: Double
        if (rule.kind == RuleKind.EVERY_N_HOURS) {
            require(key.startsWith("elapsed:${rule.id}#"))
            val k = key.substringAfter('#').toLong(); require(k >= 0)
            at = rule.anchorInstant!!.plusSeconds(Math.multiplyExact(k,rule.interval.toLong()*3600)); dose=rule.dose
        } else {
            require(key.startsWith("wall:${rule.id}@"))
            val local = LocalDateTime.parse(key.substringAfter('@'),format); require(matches(rule,local.toLocalDate()))
            val time = rule.times.single { it.time == local.toLocalTime() }
            at=wallInstant(local,zone); dose=time.dose ?: rule.dose
        }
        require(at >= rule.from && (rule.until == null || at < rule.until))
        slot(rule,key,at,zone,dose)
    }.getOrNull()
    private fun matches(r: ScheduleRule,d: LocalDate): Boolean {
        val anchor=r.anchorDate!!; if(d<anchor) return false
        return when(r.kind) {
            RuleKind.EVERY_N_DAYS -> ChronoUnit.DAYS.between(anchor,d)%r.interval==0L
            RuleKind.WEEKLY -> {
                val a=anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val b=d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                ChronoUnit.WEEKS.between(a,b)%r.interval==0L && d.dayOfWeek in r.weekdays
            }
            else -> false
        }
    }
    private fun slot(r: ScheduleRule,key:String,at:Instant,zone:ZoneId,dose:Double)=Slot(key,r.id,r.medicationId,at,at,zone,dose,r.soonMinutes,r.lateMinutes,r.trackingFrom)
    fun complete(slot: Slot, taken: Instant, zone: ZoneId, dose: Double): DoseRecord = DoseRecord(slot.key,slot.medicationId,
        if(taken <= slot.at.plusSeconds(slot.lateMinutes.toLong()*60)) DoseStatus.ON_TIME else DoseStatus.LATE,taken,zone,dose)
}

object Timeline {
    /**
     * Editing a medication ends its rule version and starts a new one, so the same planned dose (same medication,
     * original time, effective time and dose) can appear once per version, e.g. a dose completed early under the old
     * version and again as open under the new one. Only such cross-version copies are merged: a copy with a record
     * always stays and makes the unrecorded copies disappear; otherwise one copy is kept (an overridden one first,
     * then the newest version). Slots of one version, or with different times or doses, are never merged.
     */
    fun dedupeVersions(slots: Collection<Slot>, recorded: Set<String?>, overridden: Set<String>): List<Slot> =
        slots.groupBy { listOf(it.medicationId, it.original, it.at, it.dose) }.values.flatMap { group ->
            if (group.size < 2 || group.map { it.ruleId }.distinct().size < 2) return@flatMap group
            val withRecord = group.filter { it.key in recorded }
            if (withRecord.isNotEmpty()) withRecord
            else listOf(group.maxWith(compareBy<Slot>({ it.key in overridden }, { it.ruleId })))
        }

    fun build(rules: List<ScheduleRule>, overrides: List<SlotOverride>, records: List<DoseRecord>,
              from: Instant, to: Instant, now: Instant, zone: ZoneId, retained: List<Slot> = emptyList()): List<TimelineEntry> {
        ScheduleEngine.validateVersions(rules)
        require(overrides.map { it.key }.distinct().size == overrides.size)
        val active=records.filterNot { it.deleted }.filter { it.key != null }
        require(active.map { it.key }.distinct().size == active.size)
        val byKey = active.associateBy { it.key }; val changes=overrides.associateBy { it.key }
        // A successor outside the requested display window still decides MISSED.
        val horizon = rules.maxOfOrNull { if(it.kind==RuleKind.EVERY_N_HOURS) it.interval.toLong()*3600 else it.interval.toLong()*7*86400 } ?: 86400L
        val scanTo=maxOf(to,now).plusSeconds(horizon+172800)
        val scanFrom=minOf(from,now)
        val slots=rules.flatMap { ScheduleEngine.expand(it,scanFrom,scanTo,zone) }.associateBy { it.key }.toMutableMap()
        retained.forEach { slots[it.key]=it }
        overrides.forEach { o -> if(o.key !in slots) rules.firstNotNullOfOrNull { ScheduleEngine.resolve(it,o.key,zone) }?.let { slots[o.key]=it } }
        // Recorded UTC/zone/dose snapshots win over a later timezone or configuration change.
        active.filter{it.scheduled!=null && it.ruleVersionId!=null}.forEach { record ->
            val r=rules.firstOrNull{it.id==record.ruleVersionId} ?: return@forEach
            slots[record.key!!]=Slot(record.key,r.id,record.medicationId,record.scheduled!!,record.scheduled,record.scheduledZone!!,
                record.plannedDose ?: r.dose,r.soonMinutes,record.lateSnapshot ?: r.lateMinutes,r.trackingFrom)
        }
        val effective=dedupeVersions(slots.values.map { if(byKey[it.key]?.scheduled!=null) it else changes[it.key]?.apply(it) ?: it }, byKey.keys, changes.keys)
            .sortedWith(compareBy<Slot> { it.at }.thenBy { it.key })
        val successors=effective.groupBy { it.medicationId }.mapValues { (_,group) -> group.map { it.at }.distinct().sorted() }
        return effective.filter { it.at >= from && it.at < to }.map { s ->
            val record=byKey[s.key]; val next=successors[s.medicationId]!!.firstOrNull { it>s.at }
            val state=when {
                record != null -> SlotState.valueOf(record.status.name)
                s.skipped -> SlotState.SKIPPED
                s.original>=s.trackingFrom && next!=null && now>=next -> SlotState.MISSED
                now>s.at.plusSeconds(s.lateMinutes.toLong()*60) -> SlotState.OVERDUE
                now>=s.at.minusSeconds(s.soonMinutes.toLong()*60) && now<s.at -> SlotState.SOON
                else -> SlotState.PENDING
            }
            TimelineEntry(s,state)
        }
    }
}
