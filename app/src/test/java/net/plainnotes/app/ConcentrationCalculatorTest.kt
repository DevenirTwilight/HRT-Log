package net.plainnotes.app

import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.conc.MissingInput
import net.plainnotes.app.pk.Curve
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ConcentrationCalculatorTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val now = Instant.parse("2026-03-10T12:00:00Z")
    private fun med(id: Long, route: String, unit: String = "MG", molecule: String = "E2") = MedicationEntity(id, "m$id", molecule, route, unit, 2.0, 30.0, null, 15, 120, false, null, true, true, 0)
    private fun rec(id: Long, med: Long, hoursAgo: Long, dose: Double? = 2.0) = RecordEntity(id, med, taken_utc = now.minusSeconds(hoursAgo * 3600).toEpochMilli(), taken_zone = zone.id,
        actual_dose = dose, status = "ON_TIME", origin = if (dose == null) "IMPORT_TM" else "APP", revision = 1, config_snapshot = "{}")
    private fun plan(med: Long, hoursAhead: Long, dose: Double = 2.0) = TimelineEntry(Slot("wall:$med@$hoursAhead", med, med, now.plusSeconds(hoursAhead * 3600),
        now.plusSeconds(hoursAhead * 3600), zone, dose, 15, 120, now.minusSeconds(86400)), SlotState.PENDING)

    @Test fun missingInputsAreReportedNotDefaulted() {
        val gel = med(1, "GEL"); val patch = med(2, "PATCH", "PATCH"); val sl = med(3, "SUBLINGUAL", "TABLET")
        val profiles = mapOf(1L to ProfileEntity(1, "E2", "gel", gel_product_id = 1), 2L to ProfileEntity(2, "E2", "patchApply"), 3L to ProfileEntity(3, "EV", "sublingual"))
        val r = ConcentrationCalculator.compute(listOf(gel, patch, sl), profiles, listOf(rec(1, 1, 10)), emptyList(), emptyList(), null, now)
        val m = r.missing.map { it.medicationId to it.input }.toSet()
        // Weight only enters the cyproterone model.
        assertFalse((null to MissingInput.WEIGHT) in m)
        assertTrue((2L to MissingInput.PATCH_RELEASE) in m)
        assertTrue((3L to MissingInput.SL_TIER) in m && (3L to MissingInput.UNIT_NOT_MG) in m)
        assertEquals("only the complete gel is simulated", setOf(1L), r.simulatedMedications)
    }

    @Test fun recordsAndForecastProduceCurveAndImportedDosesWithoutAmountAreSkipped() {
        val oral = med(1, "ORAL")
        val r = ConcentrationCalculator.compute(listOf(oral), mapOf(1L to ProfileEntity(1, "E2", "oral")),
            listOf(rec(1, 1, 36), rec(2, 1, 24), rec(3, 1, 12), rec(4, 1, 6, null)), listOf(plan(1, 12), plan(1, 24)), emptyList(), 60.0, now)
        assertTrue(r.missing.isEmpty())
        assertEquals(3, r.usedDoses); assertEquals(1, r.skippedDoses)
        assertTrue(r.currentPgMl!! > 0)
        assertTrue(r.timeH.last() >= ConcentrationCalculator.hours(now) + ConcentrationCalculator.FORECAST_DAYS * 24 - 1e-6)
        assertNotNull("population bands are always shown", r.bandOuter); assertNull(r.calibration)
        val i = r.timeH.indexOfFirst { it >= r.nowH }
        assertTrue(r.bandOuter!!.first[i] <= r.bandInner!!.first[i] && r.bandInner!!.second[i] <= r.bandOuter!!.second[i])
        assertTrue(r.models[net.plainnotes.app.pk.Curve.E2]!!.any { it.key == "E2_ORAL" })
    }

    @Test fun weightOnlyScalesCyproterone() {
        val oral = med(1, "ORAL"); val cpa = med(2, "ORAL", molecule = "CPA"); val p = mapOf(1L to ProfileEntity(1, "E2", "oral"))
        val recs = listOf(rec(1, 1, 30), rec(2, 1, 6), rec(3, 2, 30), rec(4, 2, 6))
        assertTrue((null to MissingInput.WEIGHT) in ConcentrationCalculator.compute(listOf(oral, cpa), p, recs, emptyList(), emptyList(), null, now).missing.map { it.medicationId to it.input })
        val a = ConcentrationCalculator.compute(listOf(oral, cpa), p, recs, emptyList(), emptyList(), 60.0, now)
        val b = ConcentrationCalculator.compute(listOf(oral, cpa), p, recs, emptyList(), emptyList(), 80.0, now)
        assertEquals(a.currentPgMl!!, b.currentPgMl!!, 1e-9)
        val c = net.plainnotes.app.pk.Curve.CPA
        val i = a.timeH.indexOfFirst { it >= a.nowH }
        assertEquals(a.others[c]!!.center[i] * 60 / 80, b.others[c]!!.center[i], 1e-9)
    }

    @Test fun otherCompoundsGetTheirOwnCurvesAndUnsupportedRoutesAreExplained() {
        val spi = med(1, "ORAL", molecule = "SPI"); val p4 = med(2, "ORAL", molecule = "P4"); val slEv = med(3, "SUBLINGUAL")
        val p = mapOf(3L to ProfileEntity(3, "EV", "sublingual", sl_tier = 2))
        val r = ConcentrationCalculator.compute(listOf(spi, p4, slEv), p, listOf(rec(1, 1, 5), rec(2, 2, 5), rec(3, 3, 5)), emptyList(), emptyList(), null, now)
        assertEquals(setOf(Curve.SPIRONOLACTONE, Curve.CANRENONE, Curve.PROGESTERONE), r.others.keys)
        assertTrue(net.plainnotes.app.pk.CurveFlag.ILLUSTRATIVE in r.flags[Curve.PROGESTERONE].orEmpty())
        assertEquals(net.plainnotes.app.pk.Unsupported.SUBLINGUAL_EV, r.unsupported[3L])
        assertFalse(r.models.containsKey(Curve.E2))
    }

    @Test fun postDoseLabCalibratesAndAddsBands() {
        val oral = med(1, "ORAL"); val p = mapOf(1L to ProfileEntity(1, "E2", "oral"))
        val recs = (1..20L).map { rec(it, 1, it * 12) }
        val lab = LabValueEntity(1, "E2", 150.0, "pg/mL", now.minusSeconds(5 * 3600).toEpochMilli(), zone.id)
        val pop = ConcentrationCalculator.compute(listOf(oral), p, recs, emptyList(), emptyList(), 60.0, now)
        val cal = ConcentrationCalculator.compute(listOf(oral), p, recs, emptyList(), listOf(lab), 60.0, now)
        assertNotNull(cal.bandOuter); assertEquals(1, cal.calibration!!.labCount)
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

class StockSummaryTest {
    private val m = MedicationEntity(1, "m", "E2", "ORAL", "MG", 2.0, 30.0, 30, 15, 120, false, null, true, true, 0)
    private val twiceDaily = ScheduleSummary(RuleKind.EVERY_N_DAYS, 1, emptySet(), listOf(java.time.LocalTime.of(8, 0), java.time.LocalTime.of(20, 0)))
    @Test fun eightyFourMilligramsAtFourADayLastsTwentyOneDays() {
        val boxes=listOf(ContainerEntity(1,1,84.0,0.0,0.0,null,"SEALED"))
        assertEquals(21,net.plainnotes.app.ui.stockSummary(m,boxes,twiceDaily).daysLeft)
        // The current schedule's snapshot/overrides, not a stale medication default, determine use.
        val overridden=twiceDaily.copy(dose=0.178, timeDoses=listOf(2.0,2.0))
        assertEquals(21,net.plainnotes.app.ui.stockSummary(m.copy(dose_per_intake=0.178),boxes,overridden).daysLeft)
        assertEquals(21,net.plainnotes.app.ui.stockSummary(m.copy(dose_per_intake=4.0),boxes,
            ScheduleSummary(RuleKind.EVERY_N_DAYS,1,emptySet(),listOf(java.time.LocalTime.NOON),dose=4.0)).daysLeft)
    }
    @Test fun untrackedIsNeverLow() {
        val s = net.plainnotes.app.ui.stockSummary(m, emptyList(), twiceDaily)
        assertFalse(s.tracked); assertFalse(s.low)
    }
    @Test fun daysLeftCountsOpenAndSealed() {
        val open = ContainerEntity(1, 1, 30.0, 0.0, 22.0, java.time.LocalDate.now().minusDays(28).toString(), "IN_USE")
        val sealed = ContainerEntity(2, 1, 30.0, 0.0, 0.0, null, "SEALED")
        val s = net.plainnotes.app.ui.stockSummary(m, listOf(open, sealed), twiceDaily)
        assertEquals(38.0, s.remaining, 1e-9); assertEquals(9, s.daysLeft); assertTrue(!s.low)
        assertEquals(java.time.LocalDate.now().plusDays(2), s.expiresOn); assertTrue(s.expiringSoon(java.time.LocalDate.now()))
        assertTrue(net.plainnotes.app.ui.stockSummary(m, listOf(open), twiceDaily).low)
    }
    @Test fun dailyUseFollowsScheduleKind() {
        assertEquals(4.0, net.plainnotes.app.ui.dailyUse(m, twiceDaily)!!, 1e-9)
        assertEquals(2.0 * 3 / 14, net.plainnotes.app.ui.dailyUse(m, ScheduleSummary(RuleKind.WEEKLY, 2, setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.FRIDAY), listOf(java.time.LocalTime.NOON)))!!, 1e-9)
        assertEquals(6.0, net.plainnotes.app.ui.dailyUse(m, ScheduleSummary(RuleKind.EVERY_N_HOURS, 8, emptySet(), emptyList()))!!, 1e-9)
    }
}
