package net.plainnotes.app.experimental

import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.conc.experimental.ResearchHistoricalSlAdapter
import net.plainnotes.app.conc.experimental.SlAdapterAudit
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.pk.experimental.*
import java.time.Instant

enum class ComparisonWindow(val hours: Double, val relative: Boolean) {
    H4(4.0, true), H8(8.0, true), H24(24.0, true), H48(48.0, true), CALENDAR_7D(168.0, false)
}
class ComparisonSnapshot(val audit: SlAdapterAudit, val originIndex: Int, val result: UnifiedComparison)

/** Reuses PR3's read-only historical adapter. One snapshot for chart, metrics and exact tap readouts. */
object ExperimentalConcentrationComparison {
    fun audit(records: List<RecordEntity>, rules: Map<Long, String>, now: Instant) =
        ResearchHistoricalSlAdapter.audit(records, rules, ConcentrationCalculator.hours(now))
    fun compute(audit: SlAdapterAudit, now: Instant, window: ComparisonWindow, originIndex: Int?, m2Id: String,
                m1Id: String, display: ResearchDisplay, calibration: CommonCalibration, singleDose: Boolean): ComparisonSnapshot? {
        if (audit.events.isEmpty() && display != ResearchDisplay.STUDY) return null
        val index = (originIndex ?: audit.events.lastIndex).coerceIn(0, maxOf(0, audit.events.lastIndex))
        val origin = audit.events.getOrNull(index)?.timeH ?: 0.0
        val nowHour = ConcentrationCalculator.hours(now)
        val start = if (window.relative || display == ResearchDisplay.STUDY) origin else nowHour - window.hours
        val end = start + window.hours
        val events = if (singleDose) listOfNotNull(audit.events.getOrNull(index)) else audit.events
        val result = UnifiedModelComparison.compute(events, start, end, origin, m2Id, m1Id, display, calibration) ?: return null
        return ComparisonSnapshot(audit, index, result)
    }
}

/** Personal observations are a separate layer, never calibration inputs or study-cohort points. */
fun researchObservationPoints(labs: List<net.plainnotes.app.data.LabValueEntity>, display: ResearchDisplay, pmol: Boolean): List<Triple<Double, Double, Double>> {
    if (display != ResearchDisplay.CONDITIONAL) return emptyList()
    val factor = if (pmol) net.plainnotes.app.pk.Pk.PMOL_PER_PG else 1.0
    return labs.filter { it.analyte_code == ConcentrationCalculator.E2_CODE && it.unit in listOf("pg/mL", "pmol/L") && it.value.isFinite() && it.value >= 0 }.mapNotNull {
        val concentration = it.value * (if (it.unit == "pmol/L") 1.0 / net.plainnotes.app.pk.Pk.PMOL_PER_PG else 1.0) * factor
        // Keep axis rounding/headroom finite even for corrupt, extreme finite lab values.
        // This filters only the display layer; the original observation is never rewritten.
        if (concentration.isFinite() && concentration <= Double.MAX_VALUE / 4.0)
            Triple(ConcentrationCalculator.hours(it.sampled_utc), concentration, 0.0) else null
    }
}
