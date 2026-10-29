package net.plainnotes.app

import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.conc.ConcentrationResult
import net.plainnotes.app.conc.HistoryRead
import net.plainnotes.app.conc.experimental.ResearchHistoricalSlAdapter
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import net.plainnotes.app.experimental.ConcentrationPreview
import net.plainnotes.app.pk.Curve
import net.plainnotes.app.pk.Pk
import net.plainnotes.app.pk.experimental.ResearchModel
import net.plainnotes.app.ui.E2Total
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * UI-12 (REQUIREMENTS §162): the E2 total shown on the concentration page is computed in the display layer only.
 * Numerical contract: a single route is point-for-point the number the page showed before; a mixed total is the M2 part
 * plus the non-sublingual compatibility part, with the background counted once; the compatibility part never contains a
 * sublingual dose; a tap reads the exact sum of the two parts. Everything here is synthetic.
 */
class E2TotalTest {
    private val now = Instant.parse("2026-03-10T12:00:00Z")
    private val nowH = ConcentrationCalculator.hours(now)
    private val zone = ZoneId.of("UTC")
    private val sl = MedicationEntity(1, "Synthetic SL E2", "E2", "SUBLINGUAL", "MG", 1.0, 30.0, null, 15, 120, false, null, true, true, 0)
    private val slProfile = ProfileEntity(1, "E2", "sublingual", sl_tier = 2)
    private val oral = sl.copy(id = 2, name = "Synthetic oral E2", route = "ORAL")
    private val oralProfile = ProfileEntity(2, "E2", "oral")
    private val gel = sl.copy(id = 3, name = "Synthetic gel E2", route = "GEL")
    private val gelProfile = ProfileEntity(3, "E2", "gel", gel_product_id = 1)
    private val cpa = sl.copy(id = 4, name = "Synthetic CPA", molecule = "CPA", route = "ORAL")
    private val profiles = mapOf(1L to slProfile, 2L to oralProfile, 3L to gelProfile)

    private fun record(id: Long, m: MedicationEntity, p: ProfileEntity?, hoursAgo: Long, dose: Double = 1.0) = RecordEntity(id, m.id,
        taken_utc = now.minusSeconds(hoursAgo * 3600).toEpochMilli(), taken_zone = zone.id, actual_dose = dose, status = "ON_TIME", origin = "APP",
        revision = 1, config_snapshot = MedicationSnapshot.encode(m, p))
    private fun plan(m: MedicationEntity, hoursAhead: Long) = TimelineEntry(Slot("wall:${m.id}@$hoursAhead", m.id, m.id, now.plusSeconds(hoursAhead * 3600),
        now.plusSeconds(hoursAhead * 3600), zone, 1.0, 15, 120, now.minusSeconds(86400)), SlotState.PENDING)

    private val slRecords = listOf(record(1, sl, slProfile, 30), record(2, sl, slProfile, 20), record(3, sl, slProfile, 3))
    private val oralRecords = listOf(record(11, oral, oralProfile, 28), record(12, oral, oralProfile, 4))

    private fun full(meds: List<MedicationEntity>, records: List<RecordEntity>, planned: List<TimelineEntry> = emptyList()): ConcentrationResult =
        ConcentrationCalculator.compute(meds, profiles, records, planned, emptyList(), 70.0, now, calibrate = false, historyRead = HistoryRead(true, nowH))
    private fun preview(records: List<RecordEntity>) = ConcentrationPreview.compute(ResearchHistoricalSlAdapter.audit(records, emptyMap(), nowH), ResearchModel.M2, nowH)
    private fun nonSublingual(meds: List<MedicationEntity>, records: List<RecordEntity>, planned: List<TimelineEntry> = emptyList()): ConcentrationResult {
        val inputs = E2Total.nonSublingualInputs(meds, profiles, records, planned, emptyMap())
        return ConcentrationCalculator.compute(inputs.medications, inputs.profiles, inputs.records, inputs.planned, emptyList(), 70.0, now,
            calibrate = false, historyRead = HistoryRead(true, nowH))
    }

    @Test fun onlySublingualIsThePreviousM2CurvePointForPoint() {
        val p = preview(slRecords)
        val total = E2Total.curve(p, full(listOf(sl), slRecords), null)!!
        assertArrayEquals(p.timeH, total.timeH, 0.0)
        assertArrayEquals(p.values, total.values, 0.0)
        for (h in listOf(nowH - 40, nowH - 3.3, nowH, nowH + 5)) assertEquals(p.valueAt(h)!!, total.readAt(h) {}!!, 0.0)
        assertEquals(listOf(E2Total.Route.SUBLINGUAL), total.routes)
        assertEquals(p.doseCount, total.doseCount)
        assertEquals(3, total.doseCount)
        assertFalse(total.calibrated)
    }

    @Test fun onlyOtherRouteIsThePreviousCompatibilityCurvePointForPoint() {
        val r = full(listOf(oral), oralRecords)
        val total = E2Total.curve(null, r, null)!!
        assertArrayEquals(r.timeH, total.timeH, 0.0)
        assertArrayEquals(r.e2, total.values, 0.0)
        for (h in listOf(nowH - 20, nowH, nowH + 12)) assertEquals(r.evaluateAt(h)!!.center, total.readAt(h) {}!!, 0.0)
        assertEquals(r.currentPgMl, total.nowValue)
        assertEquals(listOf(E2Total.Route.ORAL), total.routes)
        assertEquals(r.usedDoses, total.doseCount)
    }

    @Test fun mixedTotalIsM2PlusNonSublingualWithTheBackgroundCountedOnce() {
        val records = slRecords + oralRecords
        val p = preview(records)
        val everything = full(listOf(sl, oral), records)
        assertTrue("the unfiltered compatibility run does contain the sublingual model", everything.models[Curve.E2]!!.any { it.key == "E2_SL" })
        val other = nonSublingual(listOf(sl, oral), records)
        val total = E2Total.curve(p, everything, other)!!
        assertEquals(listOf(E2Total.Route.SUBLINGUAL, E2Total.Route.ORAL), total.routes)
        assertEquals(p.doseCount + other.usedDoses, total.doseCount)
        // Union grid: every point is the exact M2 value plus the compatibility curve on its own grid.
        assertTrue(total.timeH.size >= p.timeH.size)
        for (i in total.timeH.indices step 37) {
            val h = total.timeH[i]
            assertEquals("at hour $h", p.valueAt(h)!! + Pk.interpolate(other.timeH, other.e2, h)!!, total.values[i], 1e-9)
        }
        // The exact readout is the sum of the two exact parts.
        for (h in listOf(nowH - 50.5, nowH - 3.1, nowH, nowH + 7.2)) assertEquals(p.valueAt(h)!! + other.evaluateAt(h)!!.center, total.readAt(h) {}!!, 1e-9)
        // Background once: before the first dose the compatibility part is zero and the whole total is M2's background.
        val early = nowH - 100
        assertEquals(0.0, Pk.interpolate(other.timeH, other.e2, early)!!, 0.0)
        assertEquals(p.anchor.background, total.readAt(early) {}!!, 1e-9)
        assertTrue("the compatibility part carries no background of its own", other.calibration == null && other.e2.first() == 0.0)
    }

    @Test fun compatibilityPartNeverContainsSublingualDosesOrPlannedSublingualDoses() {
        val records = slRecords + oralRecords
        val planned = listOf(plan(sl, 12), plan(oral, 12))
        val inputs = E2Total.nonSublingualInputs(listOf(sl, oral, cpa), profiles, records + record(21, cpa, null, 6), planned, emptyMap())
        assertEquals(listOf(11L, 12L), inputs.records.filter { MedicationSnapshot.decode(it.config_snapshot, it.medication_id)?.molecule == "E2" }.map { it.id })
        assertTrue(inputs.records.none { MedicationSnapshot.decode(it.config_snapshot, it.medication_id)?.medication(it.medication_id)?.route == "SUBLINGUAL" })
        assertEquals(listOf(oral.id), inputs.planned.map { it.slot.medicationId })
        assertEquals("only estradiol that is not sublingual", listOf(oral.id), inputs.medications.map { it.id })
        val other = nonSublingual(listOf(sl, oral, cpa), records, planned)
        assertTrue(other.models[Curve.E2]!!.none { it.key == "E2_SL" })
        assertEquals("the planned sublingual dose adds no future exposure", other.usedDoses, oralRecords.size)
    }

    @Test fun nothingIsShownWhenSublingualExposureExistsButM2CannotCoverIt() {
        val everything = full(listOf(sl), slRecords)
        assertNull("no silent undercount: a Legacy sublingual curve is never the total", E2Total.curve(null, everything, null))
        assertNull(E2Total.curve(ConcentrationPreview.pending(ResearchModel.M2), everything, null))
    }

    @Test fun routesAreListedInTheOrderTheyAreAddedAndOnlyOnceEach() {
        val records = slRecords + oralRecords + record(31, gel, gelProfile, 10)
        val other = nonSublingual(listOf(sl, oral, gel), records)
        val total = E2Total.curve(preview(records), full(listOf(sl, oral, gel), records), other)!!
        assertEquals(listOf(E2Total.Route.SUBLINGUAL, E2Total.Route.ORAL, E2Total.Route.GEL), total.routes)
    }

    @Test fun calibrationOnlyAppliesWhenNoSublingualRecordExists() {
        val records = oralRecords
        val labs = listOf(LabValueEntity(1, "E2", 60.0, "pg/mL", now.minusSeconds(3600).toEpochMilli(), "UTC"))
        val calibrated = ConcentrationCalculator.compute(listOf(oral), profiles, records, emptyList(), labs, 70.0, now, calibrate = true,
            historyRead = HistoryRead(true, nowH))
        assertArrayEquals(calibrated.e2, E2Total.curve(null, calibrated, null)!!.values, 0.0)
        assertEquals(calibrated.calibration != null, E2Total.curve(null, calibrated, null)!!.calibrated)
        val mixed = records + slRecords
        val p = preview(mixed)
        val total = E2Total.curve(p, full(listOf(sl, oral), mixed), nonSublingual(listOf(sl, oral), mixed))!!
        assertFalse("a mixed total is never labelled as calibrated", total.calibrated)
    }
}
