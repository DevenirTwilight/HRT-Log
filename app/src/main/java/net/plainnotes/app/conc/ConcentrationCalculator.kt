package net.plainnotes.app.conc

import net.plainnotes.app.data.LabValueEntity
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.data.ProfileEntity
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.domain.SlotState
import net.plainnotes.app.domain.TimelineEntry
import net.plainnotes.app.pk.Calibration
import net.plainnotes.app.pk.CalibrationMode
import net.plainnotes.app.pk.DoseEvent
import net.plainnotes.app.pk.DoseExtras
import net.plainnotes.app.pk.EkfDiagnostics
import net.plainnotes.app.pk.Ester
import net.plainnotes.app.pk.LabResult
import net.plainnotes.app.pk.LabUnit
import net.plainnotes.app.pk.Pk
import net.plainnotes.app.pk.PersonalModel
import net.plainnotes.app.pk.Route
import java.time.Instant

/** Why a medication (or the whole page) cannot be simulated; never silently defaulted. */
enum class MissingInput { WEIGHT, ROUTE_OR_ESTER, UNIT_NOT_MG, PATCH_RELEASE, PATCH_UNIT, GEL_PRODUCT, GEL_SITE, GEL_AREA, SL_TIER }

data class Missing(val medicationId: Long?, val input: MissingInput)

class CalibrationSummary(val model: PersonalModel, val diagnostics: EkfDiagnostics?, val labCount: Int)

class ConcentrationResult(
    val missing: List<Missing>,
    val timeH: DoubleArray,
    val e2: DoubleArray,
    /** 68 % / 95 % bands; present only once at least one post-dose lab calibrates the model. */
    val ci68: Pair<DoubleArray, DoubleArray>?,
    val ci95: Pair<DoubleArray, DoubleArray>?,
    val nowH: Double,
    val currentPgMl: Double?,
    /** E2 labs as (hours, pg/mL). */
    val labs: List<Pair<Double, Double>>,
    val calibration: CalibrationSummary?,
    val usedDoses: Int,
    val skippedDoses: Int,
    val simulatedMedications: Set<Long>,
)

object ConcentrationCalculator {
    const val FORECAST_DAYS = 30L
    const val HISTORY_DAYS = 180L
    const val E2_CODE = "E2"
    private val GEL_SITES = listOf("ARM", "THIGH", "SCROTAL", "ABDOMEN")

    fun hours(instant: Instant) = instant.toEpochMilli() / 3_600_000.0
    fun hours(epochMillis: Long) = epochMillis / 3_600_000.0

    /** Missing inputs for one estradiol medication; empty means it can be simulated. */
    fun missingFor(m: MedicationEntity, p: ProfileEntity?): List<MissingInput> {
        if (m.molecule != "E2") return emptyList()
        if (p == null || m.route == null) return listOf(MissingInput.ROUTE_OR_ESTER)
        val out = mutableListOf<MissingInput>()
        when (p.pk_route) {
            "patchApply" -> {
                if (m.unit != "PATCH") out += MissingInput.PATCH_UNIT
                if ((p.patch_release_ug_day ?: 0.0) <= 0) out += MissingInput.PATCH_RELEASE
            }
            else -> if (m.unit != "MG") out += MissingInput.UNIT_NOT_MG
        }
        if (p.pk_route == "sublingual" && p.sl_tier == null) out += MissingInput.SL_TIER
        if (p.pk_route == "gel") {
            if (p.gel_product_id == null) out += MissingInput.GEL_PRODUCT
            if (p.gel_site == null) out += MissingInput.GEL_SITE
            if ((p.gel_area_cm2 ?: 0.0) <= 0) out += MissingInput.GEL_AREA
        }
        return out
    }

    private fun extras(p: ProfileEntity, count: Double, instance: String?): DoseExtras = when (p.pk_route) {
        "sublingual" -> DoseExtras(sublingualTier = p.sl_tier?.toDouble())
        "gel" -> DoseExtras(gelProductId = p.gel_product_id?.toDouble(), gelSite = GEL_SITES.indexOf(p.gel_site).coerceAtLeast(0).toDouble(), areaCM2 = p.gel_area_cm2)
        "patchApply" -> DoseExtras(releaseRateUGPerDay = p.patch_release_ug_day!! * count, patchInstanceId = instance)
        else -> DoseExtras()
    }

    fun compute(
        medications: List<MedicationEntity>, profiles: Map<Long, ProfileEntity>, records: List<RecordEntity>, planned: List<TimelineEntry>,
        labs: List<LabValueEntity>, weightKg: Double?, now: Instant, calibrate: Boolean = true, mode: CalibrationMode = CalibrationMode.RETROSPECTIVE,
    ): ConcentrationResult {
        val nowH = hours(now)
        val missing = mutableListOf<Missing>()
        if (weightKg == null) missing += Missing(null, MissingInput.WEIGHT)
        val usable = medications.filter { it.molecule == "E2" }.filter { m ->
            val reasons = missingFor(m, profiles[m.id]); reasons.forEach { missing += Missing(m.id, it) }; reasons.isEmpty()
        }.associateBy { it.id }
        val e2Labs = labs.filter { it.analyte_code == E2_CODE && (it.unit == "pg/mL" || it.unit == "pmol/L") }
        val labPoints = e2Labs.map { hours(it.sampled_utc) to Calibration.toPgMl(it.value, if (it.unit == "pmol/L") LabUnit.PMOL_L else LabUnit.PG_ML) }
        if (weightKg == null || usable.isEmpty())
            return ConcentrationResult(missing, DoubleArray(0), DoubleArray(0), null, null, nowH, null, labPoints, null, 0, 0, emptySet())

        // (medication, time, amount, isForecast) before patch pairing.
        data class Raw(val med: Long, val timeH: Double, val dose: Double, val id: String)
        val raw = mutableListOf<Raw>()
        var skipped = 0
        val historyStart = nowH - HISTORY_DAYS * 24
        for (r in records) {
            if (r.medication_id !in usable || r.status !in listOf("ON_TIME", "LATE") || r.deleted_at_utc != null) continue
            val t = r.taken_utc ?: continue
            if (hours(t) < historyStart) continue
            val dose = r.actual_dose
            if (dose == null || !dose.isFinite() || dose <= 0) { skipped++; continue }
            raw += Raw(r.medication_id, hours(t), dose, "r${r.id}")
        }
        val used = raw.size
        val horizon = nowH + FORECAST_DAYS * 24
        for (e in planned) {
            if (e.slot.medicationId !in usable || e.slot.skipped || e.state !in listOf(SlotState.PENDING, SlotState.SOON)) continue
            val t = hours(e.slot.at)
            if (t <= nowH || t > horizon) continue
            raw += Raw(e.slot.medicationId, t, e.slot.dose, "f${e.slot.key}")
        }
        val events = mutableListOf<DoseEvent>()
        for ((med, list) in raw.groupBy { it.med }) {
            val p = profiles.getValue(med)
            val route = Route.of(p.pk_route)
            val ester = Ester.valueOf(p.ester)
            val sorted = list.sortedBy { it.timeH }
            sorted.forEachIndexed { i, x ->
                if (route == Route.PATCH_APPLY) {
                    events += DoseEvent(x.id, route, x.timeH, x.dose, ester, weightKg, extras(p, x.dose, x.id))
                    // Assumption shown in the UI: each new patch replaces the previous one.
                    sorted.getOrNull(i + 1)?.let { next -> events += DoseEvent("${x.id}-off", Route.PATCH_REMOVE, next.timeH, 0.0, ester, weightKg, DoseExtras(patchRemovalFor = x.id)) }
                } else events += DoseEvent(x.id, route, x.timeH, x.dose, ester, weightKg, extras(p, 1.0, null))
            }
        }
        if (events.isEmpty())
            return ConcentrationResult(missing, DoubleArray(0), DoubleArray(0), null, null, nowH, null, labPoints, null, used, skipped, usable.keys)
        val sim = Pk.simulate(events, horizon)!!
        val labResults = e2Labs.map { LabResult("l${it.id}", hours(it.sampled_utc), it.value, if (it.unit == "pmol/L") LabUnit.PMOL_L else LabUnit.PG_ML) }
        var e2 = sim.concPGmLE2
        var ci68: Pair<DoubleArray, DoubleArray>? = null; var ci95: Pair<DoubleArray, DoubleArray>? = null
        var summary: CalibrationSummary? = null
        if (calibrate && labResults.isNotEmpty()) {
            val model = Calibration.replay(events, labResults)
            summary = CalibrationSummary(model, Calibration.lastDiagnostics(events, labResults), labResults.size)
            if (model.postDoseObservationCount > 0 || model.baselinePGmL != null) {
                val curve = Calibration.calibrate(sim, events, labResults, mode, applyE2LearningToCpa = false)
                e2 = curve.e2
                if (model.postDoseObservationCount > 0) { ci68 = curve.ci68Low to curve.ci68High; ci95 = curve.ci95Low to curve.ci95High }
            }
        }
        return ConcentrationResult(missing, sim.timeH, e2, ci68, ci95, nowH, Pk.interpolate(sim.timeH, e2, nowH), labPoints, summary, used, skipped, usable.keys)
    }
}
