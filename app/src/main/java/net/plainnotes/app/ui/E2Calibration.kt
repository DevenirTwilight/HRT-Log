package net.plainnotes.app.ui

import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.conc.ConcentrationResult
import net.plainnotes.app.conc.LabEligibility
import net.plainnotes.app.conc.TotalE2Calibration
import net.plainnotes.app.data.LabValueEntity
import net.plainnotes.app.experimental.ConcentrationPreview
import net.plainnotes.app.pk.LabFit
import net.plainnotes.app.pk.LabUnit
import java.time.Instant
import java.time.ZoneId

/**
 * SL-CAL-02 step 4 (REQUIREMENTS §166): the display-layer adapter between the page's data and [TotalE2Calibration].
 *
 * It only reads: the frozen M2 curve (above its one background), the non-sublingual compatibility curve, the E2 lab rows and the
 * existing history gate ([ConcentrationResult.labEligibility]). It never edits a record, a model, a parameter or an export.
 */
object E2Calibration {
    /** The page must not depend on repository/list ordering to find the beginning of recorded exposure. */
    fun firstDoseH(doses: List<Instant>): Double? = doses.minOfOrNull { ConcentrationCalculator.hours(it) }

    /** A lab row as the engine needs it, plus the display facts the page lists. */
    fun labsFor(labs: List<LabValueEntity>, eligibility: List<LabEligibility>, preview: ConcentrationPreview, firstDoseH: Double?, nowH: Double): List<TotalE2Calibration.Lab> {
        val gate = eligibility.associateBy { it.labId }
        return labs.filter { it.analyte_code == ConcentrationCalculator.E2_CODE }.map { lab ->
            val timeH = ConcentrationCalculator.hours(lab.sampled_utc)
            // The existing gate (P1-B) plus at least 24 h of recorded doses before the sample.
            val historyOk = gate[lab.id]?.eligible == true && firstDoseH != null && firstDoseH <= timeH - 24.0 && timeH <= nowH
            val last = preview.sublingualDoseH.filter { it.isFinite() && it <= timeH }.maxOrNull()
            val value = when (lab.unit) {
                "pg/mL" -> lab.value
                "pmol/L" -> LabFit.toPgMl(lab.value, LabUnit.PMOL_L)
                else -> Double.NaN // Keep the source row in the report; never guess an unsupported unit.
            }
            TotalE2Calibration.Lab(lab.id, timeH, value, historyOk,
                recordedClockTime(lab), last?.let { timeH - it })
        }
    }

    /**
     * Known limit (E1): a lab saved with a date only is stored at local midnight, so "00:00:00" is treated as "no clock time recorded".
     * A genuine midnight sample is therefore refused, never guessed; a stored "estimated time" flag would need a schema change.
     */
    fun recordedClockTime(lab: LabValueEntity): Boolean {
        val zone = runCatching { ZoneId.of(lab.sampled_zone) }.getOrNull() ?: return false
        return Instant.ofEpochMilli(lab.sampled_utc).atZone(zone).toLocalTime().toNanoOfDay() != 0L
    }

    /** Null when there is nothing to calibrate (no sublingual curve or no E2 lab). */
    fun run(preview: ConcentrationPreview?, nonSublingual: ConcentrationResult?, labs: List<LabValueEntity>, result: ConcentrationResult?,
            firstDoseH: Double?, nowH: Double, checkCancelled: () -> Unit): TotalE2Calibration.Outcome? {
        val m2 = preview?.takeIf { it.timeH.isNotEmpty() } ?: return null
        val list = labsFor(labs, result?.labEligibility.orEmpty(), m2, firstDoseH, nowH)
        if (list.isEmpty()) return null
        val background = m2.anchor.background
        val curves = TotalE2Calibration.Curves(background,
            sublingual = { h -> m2.valueAt(h)?.let { (it - background).coerceAtLeast(0.0) } ?: Double.NaN },
            other = { h -> if (nonSublingual == null) 0.0 else checkNotNull(nonSublingual.evaluateAt(h, checkCancelled)) { "Other-route model input unavailable" }.center })
        // A missing/non-finite model is a calculation failure, not evidence that the samples contain too little information.
        for (lab in list.filter { TotalE2Calibration.assess(it).use != TotalE2Calibration.Use.REJECT }) {
            for (hour in listOf(lab.timeH - TotalE2Calibration.DERIVATIVE_STEP_H, lab.timeH, lab.timeH + TotalE2Calibration.DERIVATIVE_STEP_H)) {
                checkCancelled()
                val sl = curves.sublingual(hour); val other = curves.other(hour)
                check(sl.isFinite() && sl >= 0.0 && other.isFinite() && other >= 0.0) { "Calibration curve input unavailable" }
            }
        }
        return TotalE2Calibration.calibrate(list, curves, checkCancelled)
    }
}
