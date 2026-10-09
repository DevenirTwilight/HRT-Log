package net.plainnotes.app

import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.conc.ConcentrationResult
import net.plainnotes.app.conc.MissingInput
import net.plainnotes.app.data.*
import net.plainnotes.app.pk.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.AfterClass
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.math.abs

/** P0 characterization: green assertions document existing defects, not approval of those behaviors. Synthetic data only. */
class SublingualCalibrationAuditTest {
    companion object {
        private val findings = JSONObject()
        @JvmStatic @AfterClass fun report() {
            val file = File("build/reports/pk-p1c2/calculator-audit.json")
            file.parentFile!!.mkdirs(); file.writeText(findings.toString(2) + "\n")
        }
    }
    private val now = Instant.parse("2026-10-25T01:10:00Z") // Europe/Paris autumn clock fold
    private val m = MedicationEntity(1, "Synthetic E2", "E2", "SUBLINGUAL", "MG", 2.0, 40.0, null, 15, 60, false, null, true, true, 0)
    private val p = ProfileEntity(1, "E2", "sublingual", sl_tier = 2)
    private fun record(id: Long, secondsAgo: Long, snapshot: String = MedicationSnapshot.encode(m, p), dose: Double = 2.0) =
        RecordEntity(id, 1, taken_utc = now.minusSeconds(secondsAgo).toEpochMilli(), taken_zone = "Europe/Paris", actual_dose = dose,
            status = "ON_TIME", origin = "APP", revision = 1, config_snapshot = snapshot)
    private fun lab(id: Long, secondsAgo: Long, value: Double = 500.0, unit: String = "pg/mL") =
        LabValueEntity(id, "E2", value, unit, now.minusSeconds(secondsAgo).toEpochMilli(), "Europe/Paris")
    private fun compute(records: List<RecordEntity>, labs: List<LabValueEntity> = emptyList(), calibrate: Boolean = true,
                        mode: CalibrationMode = CalibrationMode.RETROSPECTIVE, currentM: MedicationEntity = m, currentP: ProfileEntity = p): ConcentrationResult =
        ConcentrationCalculator.compute(listOf(currentM), mapOf(1L to currentP), records, emptyList(), labs, 80.0, now, calibrate, mode,historyRead=net.plainnotes.app.conc.HistoryRead(true,ConcentrationCalculator.hours(now)))

    @Test fun actualCalculatorSingleDoseAndCalibrationSwitch() {
        val records = listOf(record(1, 46 * 60))
        val population = compute(records)
        assertEquals(277.767987, population.currentPgMl!!, .002)
        assertEquals(setOf("E2_SL", "E2_ORAL"), population.models.getValue(Curve.E2).map { it.key }.toSet())
        assertNull(population.calibration)
        val labs = listOf(lab(1, 0, 400.0))
        val off = compute(records, labs, calibrate = false)
        val on = compute(records, labs)
        assertEquals(population.currentPgMl!!, off.currentPgMl!!, 1e-8)
        assertTrue(abs(on.currentPgMl!! - off.currentPgMl!!) > 1)
        assertEquals(1, on.calibration!!.model.postDoseObservationCount)
        assertEquals(0.0, on.calibration!!.model.baselinePGmL ?: 0.0, 0.0)
        findings.put("single_dose", JSONObject().put("direct_engine_46min_pg_ml", 278.86477960569)
            .put("actual_calculator_off_pg_ml", off.currentPgMl).put("synthetic_400_pg_ml_lab_on", on.currentPgMl)
            .put("calibrated_amplitude", kotlin.math.exp(on.calibration!!.model.logAmplitude)).put("calibrated_rate", kotlin.math.exp(on.calibration!!.model.logRate)))
    }

    @Test fun oldOnTreatmentLabIsExcludedWithoutFabricatingBaseline() {
        val oldRecord = record(1, 201 * 86400L)
        val recent = record(2, 46 * 60)
        val oldLab = lab(1, 200 * 86400L, 500.0)
        val result = compute(listOf(oldRecord, recent), listOf(oldLab))
        val population = compute(listOf(oldRecord, recent), calibrate = false)
        assertEquals(1, result.usedDoses) // oldRecord was supplied but discarded in the calculator
        assertNull(result.calibration)
        assertFalse(result.labEligibility.single().eligible)
        assertEquals(net.plainnotes.app.conc.BaselineEligibility.NOT_PRE_TREATMENT,result.labEligibility.single().baseline)
        assertEquals(1,result.labs.size)
        assertEquals(population.currentPgMl!!, result.currentPgMl!!, 1e-7)
        val allEvents = listOf(oldRecord, recent).map { DoseEvent("r${it.id}", Route.SUBLINGUAL, ConcentrationCalculator.hours(it.taken_utc!!), 2.0, Ester.E2, 80.0) }
        val untruncated = LabFit.fit(allEvents, listOf(LabResult("old", ConcentrationCalculator.hours(oldLab.sampled_utc), 500.0, LabUnit.PG_ML)))
        assertNull(untruncated.baselinePGmL)
        findings.put("history_window_false_baseline", JSONObject().put("history_days", 180).put("lab_age_days", 200)
            .put("provided_record_age_days", 201).put("used_doses", result.usedDoses).put("baseline_pg_ml", JSONObject.NULL)
            .put("population_current_pg_ml", population.currentPgMl).put("gated_current_pg_ml", result.currentPgMl)
            .put("untruncated_baseline", JSONObject.NULL))
    }

    @Test fun missingHistoricalContextExcludesAffectedLabWithoutBaseline() {
        val recent = record(2, 46 * 60)
        val unknown = record(1, 12 * 3600, snapshot = "{}")
        val result = compute(listOf(unknown, recent), listOf(lab(1, 6 * 3600, 220.0)))
        assertTrue(result.missing.any { it.input == MissingInput.HISTORICAL_CONTEXT })
        assertEquals(1, result.skippedDoses)
        assertNull(result.calibration)
        assertFalse(result.labEligibility.single().eligible)
        assertEquals(compute(listOf(unknown,recent),calibrate=false).currentPgMl!!,result.currentPgMl!!,1e-9)
        findings.put("missing_context_false_baseline", JSONObject().put("skipped", 1).put("warning_present", true).put("baseline_pg_ml", JSONObject.NULL))
    }

    @Test fun causalHistoricalBoundaryUsesQueryPrefix() {
        val records=listOf(record(1,46*60))
        val observed=lab(1,25*60,400.0)
        val population=compute(records,mode=CalibrationMode.CAUSAL)
        val causal=compute(records,listOf(observed),mode=CalibrationMode.CAUSAL)
        val beforeSample=ConcentrationCalculator.hours(now.minusSeconds(30*60))
        val actual=causal.evaluateAt(beforeSample)!!.center
        val original=population.evaluateAt(beforeSample)!!.center
        assertEquals(original,actual,1e-7)
        assertNull(causal.evaluateAt(beforeSample)!!.calibration)
        assertEquals(1,causal.calibration!!.model.postDoseObservationCount)
        findings.put("causal_interpolation_leak",JSONObject().put("sample_age_min",25).put("evaluation_age_min",30)
            .put("population_historical_pg_ml",original).put("with_later_lab_historical_pg_ml",actual).put("still_unfixed",false))
    }

    @Test fun sampledUtcFrozenRouteAndActualDoseWinAcrossDstAndCurrentConfigurationChanges() {
        val actual = record(1, 46 * 60)
        val nominal = now.minusSeconds(10 * 3600).toEpochMilli()
        val changed = actual.copy(scheduled_utc = nominal, scheduled_zone = "America/New_York", taken_zone = "Pacific/Auckland", revision = 2)
        val old = compute(listOf(actual), listOf(lab(1, 0, 350.0)))
        val updated = compute(listOf(changed), listOf(lab(1, 0, 350.0).copy(sampled_zone = "America/New_York")),
            currentM = m.copy(route = "ORAL"), currentP = ProfileEntity(1, "EV", "oral"))
        assertEquals(old.currentPgMl!!, updated.currentPgMl!!, 1e-8)
        assertTrue(updated.models.getValue(Curve.E2).any { it.key == "E2_SL" })
        val revisedDose = compute(listOf(actual.copy(actual_dose = 1.0, revision = 2)), calibrate = false)
        assertEquals(compute(listOf(actual), calibrate = false).currentPgMl!! / 2, revisedDose.currentPgMl!!, 1e-7)
        findings.put("time_and_frozen_route", JSONObject().put("dst_fold_actual_elapsed_min", 46).put("uses_taken_not_scheduled", true)
            .put("sample_zone_label_does_not_shift_epoch", true).put("current_oral_EV_does_not_overwrite_historical_E2_SL", true).put("actual_dose_revision_applied", true))
    }

    @Test fun skippedDeletedAndMissedRecordsDoNotContributeAndLabUnitsAgree() {
        val actual = record(1, 46 * 60)
        val ignored = listOf(record(2, 1800, dose = 100.0).copy(status = "SKIPPED"), record(3, 1800, dose = 100.0).copy(status = "MISSED"),
            record(4, 1800, dose = 100.0).copy(deleted_at_utc = now.toEpochMilli()))
        val a = compute(listOf(actual), listOf(lab(1, 0, 300.0)))
        val b = compute(listOf(actual) + ignored, listOf(lab(1, 0, 300 * Pk.PMOL_PER_PG, "pmol/L")))
        assertEquals(1, b.usedDoses); assertEquals(a.currentPgMl!!, b.currentPgMl!!, 1e-7)
        assertEquals(a.labs.single().second, b.labs.single().second, 1e-9)
        findings.put("filters_and_units", JSONObject().put("skipped_deleted_missed_contribute", false).put("pg_ml_pmol_l_equivalent", true))
    }
}
