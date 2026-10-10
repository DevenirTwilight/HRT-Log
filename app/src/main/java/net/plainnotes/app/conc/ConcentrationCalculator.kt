package net.plainnotes.app.conc

import net.plainnotes.app.data.LabValueEntity
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.data.ProfileEntity
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.data.MedicationSnapshot
import net.plainnotes.app.data.HistoricalContext
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
enum class MissingInput { WEIGHT, ROUTE_OR_ESTER, UNIT_NOT_MG, PATCH_RELEASE, PATCH_UNIT, GEL_PRODUCT, SL_TIER, ROUTE_NOT_MODELLED, HISTORICAL_CONTEXT, ACTUAL_DOSE }

data class Missing(val medicationId: Long?, val input: MissingInput)

class CalibrationSummary(val model: LabFitModel, val diagnostics: LabDiagnostics?, val labCount: Int)

data class ConcentrationEvaluation(val timeH: Double, val center: Double, val p5: Double, val p25: Double, val p75: Double, val p95: Double, val calibration: CalibrationSummary?, val fitDisposition: LabFitModel? = calibration?.model)

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
    val labEligibility: List<LabEligibility> = emptyList(),
    val evaluationH: Double = nowH,
    val mode: CalibrationMode = CalibrationMode.RETROSPECTIVE,
    /** Graph must never connect points across these fit-change boundaries. */
    val calibrationBreaks: DoubleArray = doubleArrayOf(),
    val currentEvaluation: ConcentrationEvaluation? = null,
    private val evaluator: ((Double, () -> Unit) -> ConcentrationEvaluation?)? = null,
    /** Validated historical E2 SL events only. Research preview reads these in memory; no DB change. */
    val researchSublingualHistory: List<DoseEvent> = emptyList(),
) {
    fun evaluateAt(hour:Double, checkCancelled:()->Unit = {}):ConcentrationEvaluation? = evaluator?.invoke(hour,checkCancelled)
}

object ConcentrationCalculator {
    /** Existing snapshot metadata: v2 changes SL calibration/parameter bands, not population parameters. */
    const val VERSION = 2
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
        labs: List<LabValueEntity>, weightKg: Double?, now: Instant, calibrate: Boolean = true, mode: CalibrationMode = CalibrationMode.RETROSPECTIVE, plannedSnapshots: Map<Long,String> = emptyMap(), historyRead: HistoryRead = HistoryRead(), checkCancelled: () -> Unit = {}, evaluationTime: Instant = now,
    ): ConcentrationResult {
        val nowH = hours(now)
        val missing = mutableListOf<Missing>()
        val usable = medications.filter(::simulated).filter { m ->
            val reasons = missingFor(m, profiles[m.id]); reasons.forEach { missing += Missing(m.id, it) }; reasons.isEmpty()
        }.associateBy { it.id }
        val e2Labs = labs.filter { it.analyte_code == E2_CODE && (it.unit == "pg/mL" || it.unit == "pmol/L") }
        val labPoints = e2Labs.map { hours(it.sampled_utc) to LabFit.toPgMl(it.value, if (it.unit == "pmol/L") LabUnit.PMOL_L else LabUnit.PG_ML) }
        data class Raw(val med: MedicationEntity, val profile: ProfileEntity?, val timeH: Double, val dose: Double, val id: String)
        val raw = mutableListOf<Raw>()
        val evidence = mutableListOf<ExposureEvidence>()
        var skipped = 0
        val historyStart = nowH - HISTORY_DAYS * 24
        fun context(json:String,id:Long):Pair<MedicationEntity,ProfileEntity?>? {
            val snap=MedicationSnapshot.decode(json,id) ?: return null
            return snap.medication(id)?.let{it to snap.profile}
        }
        fun add(c:Pair<MedicationEntity,ProfileEntity?>?,t:Double,dose:Double?,id:String,medId:Long,historical:Boolean) {
            if(c==null) {
                if(historical){skipped++;missing+=Missing(medId,MissingInput.HISTORICAL_CONTEXT)}
                return
            }
            val (m,p)=c
            if(!simulated(m))return
            val reasons=missingFor(m,p)
            if(reasons.isNotEmpty()) {
                reasons.forEach{missing+=Missing(m.id,it)}
                if(historical)skipped++
                return
            }
            if(m.molecule=="CPA" && weightKg==null){missing+=Missing(null,MissingInput.WEIGHT);if(historical)skipped++;return}
            if(dose==null || !dose.isFinite() || dose<=0){skipped++;if(historical)missing+=Missing(medId,MissingInput.ACTUAL_DOSE);return}
            raw+=Raw(m,p,t,dose,id)
        }
        for (r in records) {
            checkCancelled()
            if(r.status !in listOf("ON_TIME","LATE") || r.deleted_at_utc!=null)continue
            val t=r.taken_utc
            val saved=context(HistoricalContext.resolved(r,plannedSnapshots),r.medication_id)
            // Import sources without route data cannot inherit the app's oral-model assumption.
            val c=if(r.origin.startsWith("IMPORT_") && saved!=null && simulated(saved.first) && saved.first.route==null) null else saved
            val actualDose=r.actual_dose
            val ingredient=saved?.first?.molecule ?: MedicationSnapshot.decode(r.config_snapshot,r.medication_id)?.molecule
            val timestamp=t?.let(::hours)
            val included=timestamp!=null && timestamp>=historyStart
            val problem=when {
                timestamp==null -> EligibilityReason.UNKNOWN_TIME
                c==null -> EligibilityReason.UNKNOWN_CONTEXT
                ingredient!="E2" -> null
                actualDose==null || !actualDose.isFinite() || actualDose<=0 -> EligibilityReason.UNKNOWN_DOSE
                missingFor(c.first,c.second).isNotEmpty() || c.first.route != when(c.second?.pk_route){"oral"->"ORAL";"sublingual"->"SUBLINGUAL";"gel"->"GEL";"injection"->"INJECTION";"patchApply"->"PATCH";else->null} -> EligibilityReason.UNKNOWN_CONTEXT
                else -> null
            }
            val historicalEvent=if(problem==null && ingredient=="E2") runCatching {
                val route=Route.of(c!!.second!!.pk_route);val ester=Ester.valueOf(c.second!!.ester)
                DoseEvent("r${r.id}",route,timestamp!!,actualDose!!,ester,weightKg ?: 70.0,extras(c.second,if(route==Route.PATCH_APPLY)actualDose else 1.0,"r${r.id}"))
            }.getOrNull() else null
            val supported=historicalEvent?.let { (Engine.choose(it) as? net.plainnotes.app.pk.ModelChoice.Use)?.parts?.any { part -> part.first==Curve.E2 } == true } == true
            evidence+=ExposureEvidence(r.id,timestamp,ingredient,historicalEvent.takeIf{supported},included,
                problem ?: if(ingredient=="E2" && !supported)EligibilityReason.UNMODELLED_EXPOSURE else null)
            if(included) add(c,timestamp!!,r.actual_dose,"r${r.id}",r.medication_id,true)
        }
        val used = raw.size
        val horizon = nowH + FORECAST_DAYS * 24
        for (e in planned) {
            if (e.slot.skipped || e.state !in listOf(SlotState.PENDING, SlotState.SOON)) continue
            val t = hours(e.slot.at)
            if (t <= nowH || t > horizon) continue
            // Production passes every rule's immutable context, including a retained old slot.
            val c=plannedSnapshots[e.slot.ruleId]?.let{context(it,e.slot.medicationId)}
                ?: if(plannedSnapshots.isEmpty())usable[e.slot.medicationId]?.let{it to profiles[it.id]} else null
            add(c,t,e.slot.dose,"f${e.slot.key}",e.slot.medicationId,false)
        }
        val active=raw.map{it.med.id}.toSet()
        val events = mutableListOf<DoseEvent>(); val medOfEvent = HashMap<String, Long>()
        val w = weightKg ?: 70.0   // unused by the estradiol, spironolactone and progesterone models
        for ((medId, list) in raw.groupBy { it.med.id }) {
            val sorted=list.sortedBy{it.timeH}
            sorted.forEach { x ->
                val other=OTHER_MOLECULES[x.med.molecule]
                val route=if(other!=null)Route.ORAL else runCatching{Route.of(x.profile!!.pk_route)}.getOrNull()
                val ester=other ?: runCatching{Ester.valueOf(x.profile!!.ester)}.getOrNull()
                if(route==null || ester==null){skipped++;missing+=Missing(medId,MissingInput.HISTORICAL_CONTEXT);return@forEach}
                medOfEvent[x.id]=medId
                events+=DoseEvent(x.id,route,x.timeH,x.dose,ester,w,extras(x.profile,if(route==Route.PATCH_APPLY)x.dose else 1.0,x.id))
                if(route==Route.PATCH_APPLY) {
                    // Assumption shown in the UI: the next recorded patch replaces this one.
                    sorted.firstOrNull{it.timeH>x.timeH && it.profile?.pk_route=="patchApply"}?.let{next->
                        events+=DoseEvent("${x.id}-off",Route.PATCH_REMOVE,next.timeH,0.0,ester,w,DoseExtras(patchRemovalFor=x.id))
                    }
                }
            }
        }
        val eligibility=CalibrationEligibility.evaluate(e2Labs.map { it.id to hours(it.sampled_utc) },evidence,historyStart,historyRead,checkCancelled)
        if (events.isEmpty())
            return ConcentrationResult(missing.distinct(), DoubleArray(0), DoubleArray(0), null, null, nowH, null, labPoints, null, used, skipped, active, labEligibility=eligibility,evaluationH=hours(evaluationTime),mode=mode)
        val grid = Engine.gridFor(events, horizon)
        val base = Engine.simulate(events, grid = grid)!!
        val unsupported = base.unsupported.mapNotNull { (id, why) -> medOfEvent[id]?.let { it to why } }.toMap()
        val qualifiedIds=eligibility.filter { it.eligible }.map { it.labId }.toSet()
        val labResults = if (calibrate) e2Labs.filter { it.id in qualifiedIds }.map { LabResult("l${it.id}", hours(it.sampled_utc), it.value, if (it.unit == "pmol/L") LabUnit.PMOL_L else LabUnit.PG_ML) } else emptyList()
        val availableThrough = minOf(nowH,historyRead.throughH.takeIf{it.isFinite()} ?: nowH)
        val usableLabs = labResults.filter { it.timeH <= availableThrough }
        val immutableEvents = events.toList()
        val bands = LabFit.bands(immutableEvents, grid, usableLabs, mode,checkCancelled=checkCancelled)
        val evaluationH = hours(evaluationTime)
        val evaluator: (Double,()->Unit)->ConcentrationEvaluation? = { time,cancel ->
            if (mode == CalibrationMode.CAUSAL && calibrate) {
                LabFit.evaluateAt(immutableEvents,grid,usableLabs,time,mode,availableThrough,checkCancelled=cancel)?.let { v ->
                    ConcentrationEvaluation(time,v.center,v.p5,v.p25,v.p75,v.p95,
                        if(v.model.usedLabIds.isEmpty() && v.model.baselineLabIds.isEmpty())null else CalibrationSummary(v.model,v.diagnostics,v.labCount),v.model)
                }
            } else {
                cancel()
                bands[Curve.E2]?.let { b ->
                    fun v(a:DoubleArray)=Pk.interpolate(grid,a,time)!!
                    val fit=if(usableLabs.isEmpty())null else LabFit.fit(immutableEvents,usableLabs)
                    val summary=fit?.takeIf { it.usedLabIds.isNotEmpty() || it.baselineLabIds.isNotEmpty() }?.let {
                        CalibrationSummary(it,LabFit.lastDiagnostics(immutableEvents,usableLabs,currentFit=it),usableLabs.size) }
                    ConcentrationEvaluation(time,v(b.center),v(b.p5),v(b.p25),v(b.p75),v(b.p95),summary,fit)
                }
            }
        }
        val current = evaluator(evaluationH,checkCancelled)
        val e2 = bands[Curve.E2]
        return ConcentrationResult(missing.distinct(), grid, e2?.center ?: DoubleArray(grid.size), e2?.let { it.p25 to it.p75 }, e2?.let { it.p5 to it.p95 },
            nowH, current?.center, labPoints, current?.calibration, used, skipped, active,
            bands.filterKeys { it != Curve.E2 }, base.flags, base.models, unsupported, eligibility,evaluationH,mode,
            if(mode==CalibrationMode.CAUSAL)usableLabs.map{it.timeH}.distinct().sorted().toDoubleArray() else doubleArrayOf(),current,evaluator,
            events.filter { it.id.startsWith("r") && it.route == Route.SUBLINGUAL && it.ester == Ester.E2 && it.timeH <= nowH })
    }
}
