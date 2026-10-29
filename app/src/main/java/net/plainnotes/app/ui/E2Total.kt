package net.plainnotes.app.ui

import net.plainnotes.app.conc.ConcentrationResult
import net.plainnotes.app.conc.TotalE2Calibration
import net.plainnotes.app.data.HistoricalContext
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.data.MedicationSnapshot
import net.plainnotes.app.data.ProfileEntity
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.domain.TimelineEntry
import net.plainnotes.app.experimental.ConcentrationPreview
import net.plainnotes.app.pk.Pk

/**
 * UI-12 (REQUIREMENTS §162): the single E2 total shown on the concentration page. Display layer only.
 *
 * The sublingual part is the existing M2 [ConcentrationPreview] (values unchanged); the other routes are the existing
 * compatibility calculation run a second time on non-sublingual estradiol only, so no sublingual dose is counted twice.
 * M2's `C = B + A*H` carries the one background; the compatibility part has none unless a lab calibration adds one,
 * and (SL-CAL-02) a total with sublingual doses is calibrated only by the common coefficient of [TotalE2Calibration]. Nothing here changes a model, a parameter,
 * a candidate or an export.
 */
object E2Total {
    enum class Route { SUBLINGUAL, ORAL, GEL, PATCH, INJECTION }

    /** Inputs for the extra compatibility run: estradiol that is not sublingual, nothing else. */
    class Inputs(val medications: List<MedicationEntity>, val profiles: Map<Long, ProfileEntity>, val records: List<RecordEntity>, val planned: List<TimelineEntry>)

    /** Same reading of a record's immutable context as the calculator and the M2 adapter (resolved snapshot first). */
    private fun contextOf(json: String?, medicationId: Long): Pair<MedicationEntity, ProfileEntity?>? {
        val snapshot = MedicationSnapshot.decode(json ?: return null, medicationId) ?: return null
        return snapshot.medication(medicationId)?.let { it to snapshot.profile }
    }

    private fun isSublingualE2(context: Pair<MedicationEntity, ProfileEntity?>?): Boolean =
        context != null && context.first.molecule == "E2" && (context.first.route == "SUBLINGUAL" || context.second?.pk_route == "sublingual")

    private fun isOtherE2(context: Pair<MedicationEntity, ProfileEntity?>?): Boolean = context != null && context.first.molecule == "E2" && !isSublingualE2(context)

    fun nonSublingualInputs(medications: List<MedicationEntity>, profiles: Map<Long, ProfileEntity>, records: List<RecordEntity>,
                            planned: List<TimelineEntry>, ruleSnapshots: Map<Long, String>): Inputs {
        val meds = medications.filter { it.molecule == "E2" && it.route != "SUBLINGUAL" && profiles[it.id]?.pk_route != "sublingual" }
        val kept = records.filter { r -> isOtherE2(contextOf(HistoricalContext.resolved(r, ruleSnapshots), r.medication_id)) }
        val keptPlanned = planned.filter { e ->
            val context = ruleSnapshots[e.slot.ruleId]?.let { contextOf(it, e.slot.medicationId) }
                ?: if (ruleSnapshots.isEmpty()) medications.firstOrNull { it.id == e.slot.medicationId }?.let { it to profiles[it.id] } else null
            isOtherE2(context)
        }
        return Inputs(meds, profiles.filterKeys { id -> meds.any { it.id == id } }, kept, keptPlanned)
    }

    /**
     * @property values pg/mL on [timeH]; the chart multiplies by the unit factor.
     * @property nowValue the total at the page's "now" (pg/mL), null when no part can be read there.
     * @property readAt exact tap readout in pg/mL: the sum of the parts' own exact readings.
     * @property breaks calibration refit boundaries; only a single compatibility route (calibrated) has any.
     */
    class Curve(val timeH: DoubleArray, val values: DoubleArray, val nowH: Double, val nowValue: Double?, val breaks: DoubleArray,
                val routes: List<Route>, val doseCount: Int, val calibrated: Boolean,
                /** SL-CAL-02: the common-coefficient calibration this total was scaled with (null = uncalibrated or the legacy single-route calibration). */
                val calibration: TotalE2Calibration.Calibrated? = null,
                val readAt: (Double, () -> Unit) -> Double?)

    private fun routesOf(result: ConcentrationResult): List<Route> {
        val keys = result.models[net.plainnotes.app.pk.Curve.E2].orEmpty().map { it.key }
        return buildList {
            if (keys.any { it == "E2_ORAL" || it == "EV_ORAL" }) add(Route.ORAL)
            if (keys.any { it.startsWith("E2_GEL") }) add(Route.GEL)
            if (keys.any { it == "E2_PATCH" }) add(Route.PATCH)
            if (keys.any { it == "EV_IM" }) add(Route.INJECTION)
        }
    }

    /**
     * @param preview the M2 snapshot (empty/pending counts as absent)
     * @param compat the original full compatibility result of the page (labs, calibration, missing inputs, other medicines)
     * @param nonSublingual the compatibility result for non-sublingual estradiol only; null when there is none
     * @param calibration SL-CAL-02: the fitted common coefficient; the total becomes `B + k·((M2 − B) + N)`, the background B still counted once
     * @return null when there is nothing to total, including: sublingual exposure exists but M2 cannot cover it (never undercounted)
     */
    fun curve(preview: ConcentrationPreview?, compat: ConcentrationResult?, nonSublingual: ConcentrationResult?, calibration: TotalE2Calibration.Calibrated? = null): Curve? {
        val m2 = preview?.takeIf { it.timeH.isNotEmpty() }
        val compatHasSl = compat?.models?.get(net.plainnotes.app.pk.Curve.E2)?.any { it.key == "E2_SL" } == true
        if (m2 == null) {
            if (compat == null || compatHasSl || compat.timeH.isEmpty() || !compat.models.containsKey(net.plainnotes.app.pk.Curve.E2)) return null
            return Curve(compat.timeH, compat.e2, compat.nowH, compat.currentPgMl, compat.calibrationBreaks, routesOf(compat), compat.usedDoses, compat.calibration != null) { h, cancel ->
                cancel(); compat.evaluateAt(h, cancel)?.center
            }
        }
        val other = nonSublingual?.takeIf { it.timeH.isNotEmpty() && it.models.containsKey(net.plainnotes.app.pk.Curve.E2) }
        val nowH = m2.asOfH ?: compat?.nowH ?: other?.nowH ?: 0.0
        val background = m2.anchor.background
        // Uncalibrated: plain sum (M2 already holds the one background). Calibrated: B + k·((M2 − B) + N); the background is never scaled.
        fun combine(sublingual: Double, others: Double): Double =
            if (calibration == null) sublingual + others else background + calibration.factor * ((sublingual - background).coerceAtLeast(0.0) + others)
        if (other == null) {
            return Curve(m2.timeH, DoubleArray(m2.values.size) { combine(m2.values[it], 0.0) }, nowH, m2.currentPgMl?.let { combine(it, 0.0) }, doubleArrayOf(),
                listOf(Route.SUBLINGUAL), m2.doseCount, calibration != null, calibration) { h, cancel -> cancel(); m2.valueAt(h)?.let { combine(it, 0.0) } }
        }
        // Union time grid: the exact M2 value plus the compatibility curve on its own grid (display interpolation only).
        val times = (m2.timeH.asList() + other.timeH.asList()).distinct().sorted().toDoubleArray()
        val values = DoubleArray(times.size) { i ->
            val h = times[i]
            combine(m2.valueAt(h) ?: 0.0, Pk.interpolate(other.timeH, other.e2, h) ?: 0.0)
        }
        fun total(h: Double, cancel: () -> Unit): Double? {
            cancel()
            val a = m2.valueAt(h) ?: return null
            val b = other.evaluateAt(h, cancel)?.center ?: return null
            return combine(a, b)
        }
        return Curve(times, values, nowH, total(nowH) {}, doubleArrayOf(), listOf(Route.SUBLINGUAL) + routesOf(other), m2.doseCount + other.usedDoses, calibration != null, calibration, ::total)
    }

    /** Every E2 lab is a dot on the total curve (all routes, UI-12), in the displayed unit; nothing is filtered or rescaled beyond the unit factor. */
    fun labPoints(labs: List<Pair<Double, Double>>, factor: Double): List<Pair<Double, Double>> = labs.map { it.first to it.second * factor }

    /** Hours of the doses actually taken, only those up to "now". */
    fun doseMarks(doseTimes: List<java.time.Instant>, nowH: Double): DoubleArray =
        doseTimes.map { it.toEpochMilli() / 3_600_000.0 }.filter { it <= nowH }.sorted().toDoubleArray()

    /** The chart's data for the page: the total in the displayed unit, labs, dose ticks, day axis and the line width of the chosen range. */
    fun chartData(total: Curve, labs: List<Pair<Double, Double>>, factor: Double, doseTimes: List<java.time.Instant>, rangeDays: Int,
                  unit: String, noonLabel: String, unitSpoken: String?): ChartData =
        ChartData(total.timeH, DoubleArray(total.values.size) { total.values[it] * factor }, null, null, total.nowH, labPoints(labs, factor), unit = unit,
            breaks = total.breaks, readAt = { hour, cancel -> cancel(); total.readAt(hour, cancel)?.times(factor) },
            marks = doseMarks(doseTimes, total.nowH), axisDays = true, noonLabel = noonLabel, lineWidthDp = if (rangeDays == 3) 2.6f else 2f, unitSpoken = unitSpoken)
}
