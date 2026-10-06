package net.plainnotes.app.conc

import net.plainnotes.app.data.LabValueEntity
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.data.ProfileEntity
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.domain.SlotState
import net.plainnotes.app.domain.TimelineEntry
import net.plainnotes.app.pk.BandedCurve
import net.plainnotes.app.pk.CalibrationMode
import net.plainnotes.app.pk.Curve
import net.plainnotes.app.pk.CurveFlag
import net.plainnotes.app.pk.DoseEvent
import net.plainnotes.app.pk.DoseExtras
import net.plainnotes.app.pk.Engine
import net.plainnotes.app.pk.Ester
import net.plainnotes.app.pk.FittedModel
import net.plainnotes.app.pk.LabDiagnostics
import net.plainnotes.app.pk.LabFit
import net.plainnotes.app.pk.LabFitModel
import net.plainnotes.app.pk.LabResult
import net.plainnotes.app.pk.LabUnit
import net.plainnotes.app.pk.Pk
import net.plainnotes.app.pk.Route
import net.plainnotes.app.pk.Unsupported
import java.time.Instant

/** Why a medication (or the whole page) cannot be simulated; never silently defaulted. */
enum class MissingInput { WEIGHT, ROUTE_OR_ESTER, UNIT_NOT_MG, PATCH_RELEASE, PATCH_UNIT, GEL_PRODUCT, SL_TIER, ROUTE_NOT_MODELLED }

data class Missing(val medicationId: Long?, val input: MissingInput)

class CalibrationSummary(val model: LabFitModel, val diagnostics: LabDiagnostics?, val labCount: Int)

class ConcentrationResult(
    val missing: List<Missing>,
    val timeH: DoubleArray,
    /** Estradiol (pg/mL): central curve, 25–75 % and 5–95 % Monte Carlo bands. */
    val e2: DoubleArray,
    val bandInner: Pair<DoubleArray, DoubleArray>?,
    val bandOuter: Pair<DoubleArray, DoubleArray>?,
    val nowH: Double,
    val currentPgMl: Double?,
    /** E2 labs as (hours, pg/mL). */
    val labs: List<Pair<Double, Double>>,
    val calibration: CalibrationSummary?,
    val usedDoses: Int,
    val skippedDoses: Int,
    val simulatedMedications: Set<Long>,
    /** Other compounds (CPA, spironolactone, canrenone, progesterone), each with its own unit and band. */
    val others: Map<Curve, BandedCurve> = emptyMap(),
    val flags: Map<Curve, Set<CurveFlag>> = emptyMap(),
    /** Fitted models behind each curve (parameters and literature for the interface). */
    val models: Map<Curve, Set<FittedModel>> = emptyMap(),
    /** Medications with doses but no curve, and why (no reliable literature). */
    val unsupported: Map<Long, Unsupported> = emptyMap(),
)

object ConcentrationCalculator {
    const val FORECAST_DAYS = 30L
    const val HISTORY_DAYS = 180L
    const val E2_CODE = "E2"
    /** Molecules with a literature model (oral only for the non-estradiol ones). */
    private val OTHER_MOLECULES = mapOf("CPA" to Ester.CPA, "SPI" to Ester.SPI, "P4" to Ester.P4)

    fun hours(instant: Instant) = instant.toEpochMilli() / 3_600_000.0
    fun hours(epochMillis: Long) = epochMillis / 3_600_000.0

    /** Missing inputs for one medication; empty means it can be simulated. */
    fun missingFor(m: MedicationEntity, p: ProfileEntity?): List<MissingInput> {
        OTHER_MOLECULES[m.molecule]?.let {
            return buildList { if (m.unit != "MG") add(MissingInput.UNIT_NOT_MG); if (m.route != null && m.route != "ORAL") add(MissingInput.ROUTE_NOT_MODELLED) }
        }
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
        if (p.pk_route == "gel" && p.gel_product_id == null) out += MissingInput.GEL_PRODUCT
        return out
    }

    private fun extras(p: ProfileEntity?, count: Double, instance: String?): DoseExtras = when (p?.pk_route) {
        "sublingual" -> DoseExtras(sublingualTier = p.sl_tier?.toDouble())
        "gel" -> DoseExtras(gelProductId = p.gel_product_id?.toDouble())
        "patchApply" -> DoseExtras(releaseRateUGPerDay = p.patch_release_ug_day!! * count, patchInstanceId = instance)
        else -> DoseExtras()
    }

    private fun simulated(m: MedicationEntity) = m.molecule == "E2" || m.molecule in OTHER_MOLECULES

    fun compute(
        medications: List<MedicationEntity>, profiles: Map<Long, ProfileEntity>, records: List<RecordEntity>, planned: List<TimelineEntry>,
        labs: List<LabValueEntity>, weightKg: Double?, now: Instant, calibrate: Boolean = true, mode: CalibrationMode = CalibrationMode.RETROSPECTIVE,
    ): ConcentrationResult {
        val nowH = hours(now)
        val missing = mutableListOf<Missing>()
        val usable = medications.filter(::simulated).filter { m ->
            val reasons = missingFor(m, profiles[m.id]); reasons.forEach { missing += Missing(m.id, it) }; reasons.isEmpty()
        }.associateBy { it.id }
        // Weight only enters the cyproterone model (clearance per kg).
        val needsWeight = usable.values.any { it.molecule == "CPA" }
        if (weightKg == null && needsWeight) missing += Missing(null, MissingInput.WEIGHT)
        val e2Labs = labs.filter { it.analyte_code == E2_CODE && (it.unit == "pg/mL" || it.unit == "pmol/L") }
        val labPoints = e2Labs.map { hours(it.sampled_utc) to LabFit.toPgMl(it.value, if (it.unit == "pmol/L") LabUnit.PMOL_L else LabUnit.PG_ML) }
        val active = if (weightKg == null) usable.filterValues { it.molecule != "CPA" } else usable
        if (active.isEmpty())
            return ConcentrationResult(missing, DoubleArray(0), DoubleArray(0), null, null, nowH, null, labPoints, null, 0, 0, emptySet())

        data class Raw(val med: Long, val timeH: Double, val dose: Double, val id: String)
        val raw = mutableListOf<Raw>()
        var skipped = 0
        val historyStart = nowH - HISTORY_DAYS * 24
        for (r in records) {
            if (r.medication_id !in active || r.status !in listOf("ON_TIME", "LATE") || r.deleted_at_utc != null) continue
            val t = r.taken_utc ?: continue
            if (hours(t) < historyStart) continue
            val dose = r.actual_dose
            if (dose == null || !dose.isFinite() || dose <= 0) { skipped++; continue }
            raw += Raw(r.medication_id, hours(t), dose, "r${r.id}")
        }
        val used = raw.size
        val horizon = nowH + FORECAST_DAYS * 24
        for (e in planned) {
            if (e.slot.medicationId !in active || e.slot.skipped || e.state !in listOf(SlotState.PENDING, SlotState.SOON)) continue
            val t = hours(e.slot.at)
            if (t <= nowH || t > horizon) continue
            raw += Raw(e.slot.medicationId, t, e.slot.dose, "f${e.slot.key}")
        }
        val events = mutableListOf<DoseEvent>(); val medOfEvent = HashMap<String, Long>()
        val w = weightKg ?: 70.0   // unused by the estradiol, spironolactone and progesterone models
        for ((med, list) in raw.groupBy { it.med }) {
            val m = active.getValue(med); val p = profiles[med]
            val other = OTHER_MOLECULES[m.molecule]
            val route = if (other != null) Route.ORAL else Route.of(p!!.pk_route)
            val ester = other ?: Ester.valueOf(p!!.ester)
            val sorted = list.sortedBy { it.timeH }
            sorted.forEachIndexed { i, x ->
                medOfEvent[x.id] = med
                if (route == Route.PATCH_APPLY) {
                    events += DoseEvent(x.id, route, x.timeH, x.dose, ester, w, extras(p, x.dose, x.id))
                    // Assumption shown in the UI: each new patch replaces the previous one.
                    sorted.getOrNull(i + 1)?.let { next -> events += DoseEvent("${x.id}-off", Route.PATCH_REMOVE, next.timeH, 0.0, ester, w, DoseExtras(patchRemovalFor = x.id)) }
                } else events += DoseEvent(x.id, route, x.timeH, x.dose, ester, w, extras(p, 1.0, null))
            }
        }
        if (events.isEmpty())
            return ConcentrationResult(missing, DoubleArray(0), DoubleArray(0), null, null, nowH, null, labPoints, null, used, skipped, active.keys)
        val grid = Engine.gridFor(events, horizon)
        val base = Engine.simulate(events, grid = grid)!!
        val unsupported = base.unsupported.mapNotNull { (id, why) -> medOfEvent[id]?.let { it to why } }.toMap()
        val labResults = if (calibrate) e2Labs.map { LabResult("l${it.id}", hours(it.sampled_utc), it.value, if (it.unit == "pmol/L") LabUnit.PMOL_L else LabUnit.PG_ML) } else emptyList()
        val bands = LabFit.bands(events, grid, labResults, mode)
        val summary = if (labResults.isNotEmpty() && base.curves.containsKey(Curve.E2))
            CalibrationSummary(LabFit.fit(events, labResults), LabFit.lastDiagnostics(events, labResults), labResults.size) else null
        val e2 = bands[Curve.E2]
        return ConcentrationResult(missing, grid, e2?.center ?: DoubleArray(grid.size), e2?.let { it.p25 to it.p75 }, e2?.let { it.p5 to it.p95 },
            nowH, e2?.let { Pk.interpolate(grid, it.center, nowH) }, labPoints, summary, used, skipped, active.keys,
            bands.filterKeys { it != Curve.E2 }, base.flags, base.models, unsupported)
    }
}
