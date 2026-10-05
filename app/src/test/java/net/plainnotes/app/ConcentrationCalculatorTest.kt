package net.plainnotes.app

import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.conc.MissingInput
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ConcentrationCalculatorTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val now = Instant.parse("2026-03-10T12:00:00Z")
    private fun med(id: Long, route: String, unit: String = "MG") = MedicationEntity(id, "m$id", "E2", route, unit, 2.0, 30.0, null, 15, 120, false, null, true, true, 0)
    private fun rec(id: Long, med: Long, hoursAgo: Long, dose: Double? = 2.0) = RecordEntity(id, med, taken_utc = now.minusSeconds(hoursAgo * 3600).toEpochMilli(), taken_zone = zone.id,
        actual_dose = dose, status = "ON_TIME", origin = if (dose == null) "IMPORT_TM" else "APP", revision = 1, config_snapshot = "{}")
    private fun plan(med: Long, hoursAhead: Long, dose: Double = 2.0) = TimelineEntry(Slot("wall:$med@$hoursAhead", med, med, now.plusSeconds(hoursAhead * 3600),
        now.plusSeconds(hoursAhead * 3600), zone, dose, 15, 120, now.minusSeconds(86400)), SlotState.PENDING)

    @Test fun missingInputsAreReportedNotDefaulted() {
        val gel = med(1, "GEL"); val patch = med(2, "PATCH", "PATCH"); val sl = med(3, "SUBLINGUAL", "TABLET")
        val profiles = mapOf(1L to ProfileEntity(1, "E2", "gel", gel_product_id = 1), 2L to ProfileEntity(2, "E2", "patchApply"), 3L to ProfileEntity(3, "EV", "sublingual"))
        val r = ConcentrationCalculator.compute(listOf(gel, patch, sl), profiles, listOf(rec(1, 1, 10)), emptyList(), emptyList(), null, now)
        val m = r.missing.map { it.medicationId to it.input }.toSet()
        assertTrue((null to MissingInput.WEIGHT) in m)
        assertTrue((1L to MissingInput.GEL_SITE) in m && (1L to MissingInput.GEL_AREA) in m)
        assertTrue((2L to MissingInput.PATCH_RELEASE) in m)
        assertTrue((3L to MissingInput.SL_TIER) in m && (3L to MissingInput.UNIT_NOT_MG) in m)
        assertEquals(0, r.timeH.size)
    }

    @Test fun recordsAndForecastProduceCurveAndImportedDosesWithoutAmountAreSkipped() {
        val oral = med(1, "ORAL")
        val r = ConcentrationCalculator.compute(listOf(oral), mapOf(1L to ProfileEntity(1, "E2", "oral")),
            listOf(rec(1, 1, 36), rec(2, 1, 24), rec(3, 1, 12), rec(4, 1, 6, null)), listOf(plan(1, 12), plan(1, 24)), emptyList(), 60.0, now)
        assertTrue(r.missing.isEmpty())
        assertEquals(3, r.usedDoses); assertEquals(1, r.skippedDoses)
        assertTrue(r.currentPgMl!! > 0)
        assertTrue(r.timeH.last() >= ConcentrationCalculator.hours(now) + ConcentrationCalculator.FORECAST_DAYS * 24 - 1e-6)
        assertNull(r.ci95)
    }

    @Test fun changingWeightRescalesWholeCurve() {
        val oral = med(1, "ORAL"); val p = mapOf(1L to ProfileEntity(1, "E2", "oral")); val recs = listOf(rec(1, 1, 30), rec(2, 1, 6))
        val a = ConcentrationCalculator.compute(listOf(oral), p, recs, emptyList(), emptyList(), 60.0, now)
        val b = ConcentrationCalculator.compute(listOf(oral), p, recs, emptyList(), emptyList(), 80.0, now)
        assertEquals(a.currentPgMl!! * 60 / 80, b.currentPgMl!!, 1e-9)
    }

    @Test fun postDoseLabCalibratesAndAddsBands() {
        val oral = med(1, "ORAL"); val p = mapOf(1L to ProfileEntity(1, "E2", "oral"))
        val recs = (1..20L).map { rec(it, 1, it * 12) }
        val lab = LabValueEntity(1, "E2", 400.0, "pg/mL", now.minusSeconds(5 * 3600).toEpochMilli(), zone.id)
        val pop = ConcentrationCalculator.compute(listOf(oral), p, recs, emptyList(), emptyList(), 60.0, now)
        val cal = ConcentrationCalculator.compute(listOf(oral), p, recs, emptyList(), listOf(lab), 60.0, now)
        assertNotNull(cal.ci95); assertEquals(1, cal.calibration!!.labCount)
        assertTrue("calibration pulls the estimate toward the higher lab", cal.currentPgMl!! > pop.currentPgMl!!)
        val off = ConcentrationCalculator.compute(listOf(oral), p, recs, emptyList(), listOf(lab), 60.0, now, calibrate = false)
        assertEquals(pop.currentPgMl!!, off.currentPgMl!!, 1e-9)
    }

    @Test fun patchesAreRemovedWhenTheNextOneIsApplied() {
        val patch = med(1, "PATCH", "PATCH"); val p = mapOf(1L to ProfileEntity(1, "E2", "patchApply", patch_release_ug_day = 50.0))
        val one = ConcentrationCalculator.compute(listOf(patch), p, listOf(rec(1, 1, 200, 1.0)), emptyList(), emptyList(), 60.0, now)
        val replaced = ConcentrationCalculator.compute(listOf(patch), p, listOf(rec(1, 1, 200, 1.0), rec(2, 1, 116, 1.0)), emptyList(), emptyList(), 60.0, now)
        // With a single patch it is worn indefinitely; after replacement only one patch contributes, not two.
        assertEquals(one.currentPgMl!!, replaced.currentPgMl!!, one.currentPgMl!! * 0.01)
        val two = ConcentrationCalculator.compute(listOf(patch), p, listOf(rec(1, 1, 10, 2.0)), emptyList(), emptyList(), 60.0, now)
        val single = ConcentrationCalculator.compute(listOf(patch), p, listOf(rec(1, 1, 10, 1.0)), emptyList(), emptyList(), 60.0, now)
        assertEquals(single.currentPgMl!! * 2, two.currentPgMl!!, 1e-6)
    }
}
