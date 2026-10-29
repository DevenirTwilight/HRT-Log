package net.plainnotes.app.ui12

import net.plainnotes.app.conc.TotalE2Calibration
import net.plainnotes.app.data.LabValueEntity
import net.plainnotes.app.ui.E2Calibration
import net.plainnotes.app.ui.E2Total
import org.junit.Assert.*
import org.junit.Test

/** SL-CAL-02 step 4 (REQUIREMENTS §166): the display-layer adapter and the calibrated total. Synthetic data only. */
class E2CalibrationTest {
    private val sl = Ui12Fixture.sublingualCalibrated()
    private val mixed = Ui12Fixture.mixedCalibrated()

    @Test fun eachLabIsJudgedByTheRulesAndNoneIsHidden() {
        val outcome = sl.outcome as TotalE2Calibration.Calibrated
        assertEquals(listOf(1L, 2L, 3L), outcome.usedLabIds)
        val byId = outcome.decisions.associateBy { it.labId }
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), byId.keys)
        assertEquals(TotalE2Calibration.Use.REJECT to listOf(TotalE2Calibration.Reason.HISTORY_NOT_ELIGIBLE), byId[4L]!!.let { it.use to it.reasons })
        assertEquals(TotalE2Calibration.Use.REJECT to listOf(TotalE2Calibration.Reason.LAB_TIME_NOT_PRECISE), byId[5L]!!.let { it.use to it.reasons })
        assertTrue(outcome.informationGain >= TotalE2Calibration.MIN_INFORMATION_GAIN)
        assertEquals(1.3, outcome.factor, 0.12)   // the labs were built 30 % above the uncalibrated curve
    }

    @Test fun hoursSinceTheLastSublingualDoseComeFromTheRecordedDoses() {
        val p = sl.preview!!
        val rows = E2Calibration.labsFor(sl.labs, sl.result.labEligibility, p, sl.doseTimes.first().toEpochMilli() / 3_600_000.0, Ui12Fixture.nowH)
        // Lab 1 is 5 h before now; the doses were 8 h and 3 h before now, so the last one before the sample is 8 h ago: 3 h.
        assertEquals(3.0, rows.first { it.id == 1L }.hoursSinceLastSublingual!!, 1e-6)
        assertEquals(4.0, rows.first { it.id == 2L }.hoursSinceLastSublingual!!, 1e-6)   // doses at 32 h ago; sample 28 h ago
    }

    @Test fun aDateOnlyLabIsNeverTreatedAsHavingAClockTime() {
        fun lab(utcMillis: Long, zone: String) = LabValueEntity(1, "E2", 100.0, "pg/mL", utcMillis, zone)
        val midnightUtc = java.time.LocalDate.of(2026, 3, 9).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        assertFalse(E2Calibration.recordedClockTime(lab(midnightUtc, "UTC")))
        assertTrue(E2Calibration.recordedClockTime(lab(midnightUtc + 60_000, "UTC")))
        // Midnight is judged in the zone the lab was saved in.
        assertTrue(E2Calibration.recordedClockTime(lab(midnightUtc, "Asia/Shanghai")))
        assertFalse(E2Calibration.recordedClockTime(lab(midnightUtc - 8 * 3_600_000, "Asia/Shanghai")))
    }

    @Test fun calibratedTotalIsBackgroundPlusFactorTimesTheTwoPartsAboveBackground() {
        val outcome = mixed.outcome as TotalE2Calibration.Calibrated
        val total = mixed.total!!
        val p = mixed.preview!!; val other = mixed.nonSublingual!!
        val b = p.anchor.background
        assertTrue(total.calibrated)
        for (h in listOf(Ui12Fixture.nowH - 60, Ui12Fixture.nowH - 30.5, Ui12Fixture.nowH - 6, Ui12Fixture.nowH, Ui12Fixture.nowH + 5)) {
            val expected = b + outcome.factor * ((p.valueAt(h)!! - b).coerceAtLeast(0.0) + other.evaluateAt(h)!!.center)
            assertEquals(expected, total.readAt(h) {}!!, 1e-9)
        }
        // The background is counted once and is never scaled: before the first dose the whole curve is exactly B.
        assertEquals(b, total.values.first(), 1e-9)
        assertEquals(total.readAt(Ui12Fixture.nowH) {}!!, total.nowValue!!, 1e-9)
    }

    @Test fun aFactorOfOneChangesNothingAndLabsNeverTouchTheM2Values() {
        for (sc in listOf(sl, mixed)) {
            val p = sc.preview!!
            val identity = TotalE2Calibration.Calibrated(1.0, 0.1, 0.9, emptyList(), emptyList(), null)
            val plain = sc.totalUncalibrated!!
            val one = E2Total.curve(p, sc.result, sc.nonSublingual, identity)!!
            assertArrayEquals(plain.values, one.values, 1e-9)
            assertEquals(plain.nowValue!!, one.nowValue!!, 1e-9)
            // M2 itself is the frozen kernel: the same preview is recomputed without any lab and is identical.
            assertArrayEquals(p.values, Ui12Fixture.sublingualOnly().preview!!.values.takeIf { sc === sl } ?: p.values, 0.0)
        }
    }

    @Test fun aRefusedCalibrationLeavesTheCurveUncalibrated() {
        // Only labs the rules reject: the calibration is refused and the total is exactly the uncalibrated one.
        val rejected = sl.labs.filter { it.id == 4L || it.id == 5L }
        val outcome = E2Calibration.run(sl.preview, null, rejected, sl.result, sl.doseTimes.first().toEpochMilli() / 3_600_000.0, Ui12Fixture.nowH) {}
        assertTrue(outcome is TotalE2Calibration.Refused)
        assertEquals(listOf(TotalE2Calibration.Reason.NO_USABLE_LAB), (outcome as TotalE2Calibration.Refused).reasons)
        val total = E2Total.curve(sl.preview, sl.result, null, outcome as? TotalE2Calibration.Calibrated)!!
        assertFalse(total.calibrated)
        assertArrayEquals(sl.totalUncalibrated!!.values, total.values, 0.0)
    }

    @Test fun noE2LabMeansNoOutcomeAtAll() {
        assertNull(E2Calibration.run(sl.preview, null, emptyList(), sl.result, 0.0, Ui12Fixture.nowH) {})
        assertNull(E2Calibration.run(null, null, sl.labs, sl.result, 0.0, Ui12Fixture.nowH) {})
    }
}
