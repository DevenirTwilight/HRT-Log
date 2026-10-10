package net.plainnotes.app.experimental

import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.conc.experimental.ResearchHistoricalSlAdapter
import net.plainnotes.app.conc.experimental.SlAdapterAudit
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.pk.experimental.ConcentrationModelComparison
import net.plainnotes.app.pk.experimental.ExperimentalDose
import net.plainnotes.app.pk.experimental.ModelComparisonResult
import net.plainnotes.app.pk.experimental.ModelValuesAt
import net.plainnotes.app.pk.experimental.PriceFigure1997
import net.plainnotes.app.pk.experimental.StudyAnchors
import java.time.Instant

/** Display windows: hours after the chosen origin dose, or the calendar week ending now. */
enum class ComparisonWindow(val hours: Double, val relative: Boolean) {
    H4(4.0, true), H8(8.0, true), H24(24.0, true), H48(48.0, true), CALENDAR_7D(7 * 24.0, false)
}

/** Price 1997 study scenario on the same grid as [result]; pg/mL from the verified anchor, never from the relative curve. */
class StudyScenarioCurves(val selected: DoubleArray, val minimum: DoubleArray, val maximum: DoubleArray, val background: Double, val amplitude: Double)

/** Everything the comparison panel shows, computed once off the main thread. Read-only. */
class ComparisonSnapshot(
    val audit: SlAdapterAudit,
    val nowHour: Double,
    val window: ComparisonWindow,
    /** Index into [SlAdapterAudit.events] of the origin dose. */
    val originIndex: Int,
    val result: ModelComparisonResult,
    val study: StudyScenarioCurves,
)

/**
 * Read-only comparison layer (REQUIREMENTS §57): qualified history via the snapshot-audited adapter, both kernels via
 * [ConcentrationModelComparison]. Never writes, never touches the official ConcentrationCalculator result.
 */
object ExperimentalConcentrationComparison {
    fun audit(records: List<RecordEntity>, ruleSnapshots: Map<Long, String>, now: Instant): SlAdapterAudit =
        ResearchHistoricalSlAdapter.audit(records, ruleSnapshots, ConcentrationCalculator.hours(now))

    /** Null when there is no qualifying sublingual E2 dose. [originIndex] defaults to the latest dose. */
    fun compute(audit: SlAdapterAudit, now: Instant, window: ComparisonWindow, originIndex: Int?, candidateId: String): ComparisonSnapshot? {
        if (audit.events.isEmpty()) return null
        val nowHour = ConcentrationCalculator.hours(now)
        val index = (originIndex ?: audit.events.lastIndex).coerceIn(0, audit.events.lastIndex)
        val origin = audit.events[index].timeH
        val (start, end) = if (window.relative) origin to origin + window.hours else nowHour - window.hours to nowHour
        val result = ConcentrationModelComparison.compute(audit.events, start, end, origin, candidateId) ?: return null
        val anchor = PriceFigure1997.anchor(result.candidate)
        val doses = result.doses.map { (t, mg) -> ExperimentalDose(t, mg) }
        val (lo, hi) = StudyAnchors.scenarioRange(PriceFigure1997.STUDY_ID, result.timeHours, doses)!!
        val selected = DoubleArray(result.m2.size) { anchor.background + anchor.oneMgIncrementAtOneHour * result.m2[it] }
        return ComparisonSnapshot(audit, nowHour, window, index, result, StudyScenarioCurves(selected, lo, hi, anchor.background, anchor.oneMgIncrementAtOneHour))
    }

    fun valuesAt(snapshot: ComparisonSnapshot, hour: Double): ModelValuesAt? = ConcentrationModelComparison.valuesAt(snapshot.audit.events, snapshot.result, hour)
}
