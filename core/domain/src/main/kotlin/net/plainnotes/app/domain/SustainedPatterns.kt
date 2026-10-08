package net.plainnotes.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Doses actually logged on one local day (the record's own zone), sorted; duplicates of one record removed by the caller. */
data class LoggedDay(val date: LocalDate, val doses: List<Double>)

/** A stretch of logged days sharing one standard. `interval` is 0 when the frequency is not known. */
data class PatternSegment(val days: List<LoggedDay>, val interval: Int, val doses: List<Double>) {
    val first get() = days.first().date
    val last get() = days.last().date
    val frequencyKnown get() = interval > 0
}

/**
 * REQUIREMENTS §35a (O1). A new pattern becomes a new segment only when it holds for [SUSTAIN_DAYS] calendar days:
 * missing records do not interrupt it, any day of the old pattern does, and for a reduction no day may reach the old
 * daily count. Short deviations stay inside the current segment. A run of [GAP_DAYS] or more days without records
 * separates segments even when the standard is the same; nothing is inferred about the gap itself.
 */
object SustainedPatterns {
    const val SUSTAIN_DAYS = 14
    const val GAP_DAYS = 30
    private const val MIN_EVIDENCE = 3

    fun subset(part: List<Double>, whole: List<Double>): Boolean {
        val remaining = whole.toMutableList()
        return part.all { amount -> val i = remaining.indexOf(amount); if (i < 0) false else { remaining.removeAt(i); true } }
    }

    /** Most frequent exact daily pattern; a frequent superset wins because missing records hide doses, extra doses rarely recur. */
    internal fun baseline(days: List<LoggedDay>): List<Double>? {
        if (days.isEmpty()) return null
        val counts = days.groupingBy { it.doses }.eachCount()
        val mode = counts.entries.maxWith(compareBy<Map.Entry<List<Double>, Int>>({ it.value }, { it.key.size }, { -days.indexOfFirst { d -> d.doses == it.key } })).key
        val superset = counts.filter { (p, c) -> p != mode && p.size > mode.size && subset(mode, p) && c >= MIN_EVIDENCE && c * 2 >= counts.getValue(mode) }
            .maxWithOrNull(compareBy({ it.value }, { it.key.size }))?.key
        return superset ?: mode
    }

    fun segment(days: List<LoggedDay>, sustainDays: Int = SUSTAIN_DAYS, gapDays: Int = GAP_DAYS): List<PatternSegment> {
        val sorted = days.sortedBy { it.date }
        require(sorted.zipWithNext().all { (a, b) -> a.date < b.date }) { "One entry per day" }
        val blocks = mutableListOf<MutableList<LoggedDay>>()
        sorted.forEach { d -> val prev = blocks.lastOrNull()
            if (prev == null || ChronoUnit.DAYS.between(prev.last().date, d.date) - 1 >= gapDays) blocks += mutableListOf(d) else prev += d }
        return blocks.flatMap { block ->
            if (block.size < MIN_EVIDENCE) return@flatMap emptyList()
            cadenceParts(block, sustainDays).flatMap { (part, cadence) ->
                val window = if (cadence <= 1) sustainDays else maxOf(sustainDays, 3 * cadence)
                split(part, window).mapNotNull { describe(it, cadence == 1) }
            }
        }
    }

    private fun gaps(days: List<LoggedDay>) = days.zipWithNext().map { (a, b) -> ChronoUnit.DAYS.between(a.date, b.date).toInt() }
    /** 1 when most logged days are consecutive, a regular spacing when one dominates, 0 when the spacing is irregular. */
    private fun cadence(days: List<LoggedDay>): Int {
        val g = gaps(days); if (g.isEmpty()) return 1
        if (g.sorted()[g.size / 2] == 1) return 1
        val (spacing, count) = g.groupingBy { it }.eachCount().maxWith(compareBy({ it.value }, { -it.key })).toPair()
        return if (count >= 2 && count * 2 >= g.size) spacing else 0
    }

    /**
     * Frequency changes under the same O1 rule: the new spacing must hold for the sustain window. When the spacing
     * widens (fewer doses), no gap in the window may return to the closer spacing; missed logs alone never widen it.
     */
    private fun cadenceParts(block: List<LoggedDay>, sustainDays: Int): List<Pair<List<LoggedDay>, Int>> {
        val parts = mutableListOf<Pair<MutableList<LoggedDay>, Int>>()
        var c = cadence(block.filter { ChronoUnit.DAYS.between(block.first().date, it.date) < sustainDays })
        var current = mutableListOf<LoggedDay>()
        for ((i, day) in block.withIndex()) {
            val gap = block.getOrNull(i + 1)?.let { ChronoUnit.DAYS.between(day.date, it.date).toInt() }
            if (current.isNotEmpty() && gap != null && gap != c) {
                val probe = block.subList(i, block.size).filter { ChronoUnit.DAYS.between(day.date, it.date) < maxOf(sustainDays, 3 * gap) }
                val next = cadence(probe)
                val w = maxOf(sustainDays, 3 * maxOf(next, 1))
                val window = block.subList(i, block.size).filter { ChronoUnit.DAYS.between(day.date, it.date) < w }
                val wg = gaps(window)
                val held = block.last().date >= day.date.plusDays(w - 1L)
                val ok = next != c && held && wg.size >= MIN_EVIDENCE && cadence(window) == next &&
                    if (next > c) wg.all { it % next == 0 } else wg.count { it == next } * 3 >= wg.size * 2
                if (ok) { parts += current to c; current = mutableListOf(); c = next }
            }
            current += day
        }
        if (current.isNotEmpty()) parts += current to c
        return parts
    }

    private fun split(block: List<LoggedDay>, window: Int): List<List<LoggedDay>> {
        val parts = mutableListOf<MutableList<LoggedDay>>()
        var current = mutableListOf<LoggedDay>()
        var pattern = baseline(block.filter { ChronoUnit.DAYS.between(block.first().date, it.date) < window }) ?: return emptyList()
        for ((i, day) in block.withIndex()) {
            if (current.isNotEmpty() && day.doses != pattern) {
                val end = day.date.plusDays(window - 1L)
                val ahead = block.subList(i, block.size).filter { it.date <= end }
                val next = baseline(ahead)
                val held = block.last().date >= end
                if (held && next != null && next != pattern && accepts(ahead, pattern, next)) {
                    parts += current; current = mutableListOf(); pattern = next
                }
            }
            current += day
        }
        if (current.isNotEmpty()) parts += current
        return parts
    }

    /** The new pattern is established, no day returns to the old one, and it explains most logged days of the window. */
    private fun accepts(window: List<LoggedDay>, old: List<Double>, new: List<Double>): Boolean {
        if (window.count { it.doses == new } < MIN_EVIDENCE) return false
        val reduction = new.size < old.size
        val returnsToOld = window.any { d -> (subset(d.doses, old) && !subset(d.doses, new)) || (reduction && d.doses.size >= old.size) }
        return !returnsToOld && window.count { subset(it.doses, new) } * 3 >= window.size * 2
    }

    private fun describe(part: List<LoggedDay>, daily: Boolean): PatternSegment? {
        if (part.size < MIN_EVIDENCE) return null
        val pattern = baseline(part)!!
        if (daily) return if (part.count { it.doses == pattern } >= MIN_EVIDENCE) PatternSegment(part, 1, pattern)
            else PatternSegment(part, 0, part.flatMap { it.doses }.distinct().sorted())
        // Sparse regular schedules: at least four occurrences, every gap a multiple of the usual one.
        val gaps = part.zipWithNext().map { (a, b) -> ChronoUnit.DAYS.between(a.date, b.date).toInt() }
        val g = gaps.groupingBy { it }.eachCount().maxWith(compareBy({ it.value }, { -it.key })).key
        val regular = part.size >= 4 && g in 2..365 && gaps.all { it % g == 0 } && gaps.count { it == g } * 3 >= gaps.size * 2 &&
            part.count { it.doses == pattern } >= MIN_EVIDENCE
        return if (regular) PatternSegment(part, g, pattern) else PatternSegment(part, 0, part.flatMap { it.doses }.distinct().sorted())
    }
}
