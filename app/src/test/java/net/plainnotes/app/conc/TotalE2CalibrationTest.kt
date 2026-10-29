package net.plainnotes.app.conc

import net.plainnotes.app.conc.TotalE2Calibration.Calibrated
import net.plainnotes.app.conc.TotalE2Calibration.Curves
import net.plainnotes.app.conc.TotalE2Calibration.Lab
import net.plainnotes.app.conc.TotalE2Calibration.Reason
import net.plainnotes.app.conc.TotalE2Calibration.Refused
import net.plainnotes.app.conc.TotalE2Calibration.Use
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.exp
import kotlin.math.ln

/** SL-CAL-02 engine rules on synthetic curves: fail-closed inputs, honest refusals, one background, no cap. The numbers against the reference are in the parity test. */
class TotalE2CalibrationTest {
    /** A made-up daily sublingual curve: 300 pg/mL above background right after a dose at hour 8 of each day, falling with a 3 h time constant. */
    private val daily = Curves(24.0, { t -> val h = ((t % 24.0) + 24.0) % 24.0; if (h >= 8.0) 300.0 * exp(-(h - 8.0) / 3.0) else 300.0 * exp(-(h + 16.0) / 3.0) })
    private fun lab(id: Long, t: Double, y: Double, hours: Double? = 5.0, history: Boolean = true, minute: Boolean = true) = Lab(id, t, y, history, minute, hours)
    private fun calibrated(o: TotalE2Calibration.Outcome) = o as? Calibrated ?: fail("expected a calibration, got $o").let { throw AssertionError() }

    @Test fun noLabsMeansNoCalibration() {
        val o = TotalE2Calibration.calibrate(emptyList(), daily)
        assertTrue(o is Refused); assertEquals(listOf(Reason.NO_USABLE_LAB), (o as Refused).reasons)
    }

    @Test fun everyRejectedLabSaysWhyAndNothingIsFitted() {
        val labs = listOf(lab(1, 100.0, 80.0, history = false), lab(2, 101.0, 80.0, minute = false), lab(3, 102.0, 80.0, hours = null), lab(4, 103.0, 0.0), lab(5, 104.0, Double.NaN))
        val o = TotalE2Calibration.calibrate(labs, daily)
        assertEquals(listOf(Reason.NO_USABLE_LAB), (o as Refused).reasons)
        assertEquals(listOf(Reason.HISTORY_NOT_ELIGIBLE, Reason.LAB_TIME_NOT_PRECISE, Reason.SL_LAST_DOSE_UNKNOWN, Reason.INVALID_VALUE, Reason.INVALID_VALUE),
            o.decisions.map { it.reasons.single() })
        assertTrue(o.decisions.all { it.use == Use.REJECT })
    }

    @Test fun aLabNearTheDoseIsDownweightedNotRejected() {
        val d = TotalE2Calibration.assess(lab(1, 129.0, 200.0, hours = 1.0))
        assertEquals(Use.DOWNWEIGHT, d.use); assertEquals(listOf(Reason.PEAK_WINDOW_DOWNWEIGHTED), d.reasons)
        assertEquals(Use.USE, TotalE2Calibration.assess(lab(1, 129.0, 200.0, hours = 2.5)).use)
    }

    @Test fun curvesThatCannotDescribeTheLabsRefuseInsteadOfFitting() {
        val zero = Curves(24.0, { 0.0 })
        assertEquals(listOf(Reason.INSUFFICIENT_INFORMATION), (TotalE2Calibration.calibrate(listOf(lab(1, 100.0, 80.0)), zero) as Refused).reasons)
        val bad = Curves(24.0, { if (it > 100.0) Double.NaN else 5.0 })
        assertTrue(TotalE2Calibration.calibrate(listOf(lab(1, 100.05, 80.0)), bad) is Refused)
        val negative = Curves(24.0, { -1.0 })
        assertTrue(TotalE2Calibration.calibrate(listOf(lab(1, 100.0, 80.0)), negative) is Refused)
        assertTrue(TotalE2Calibration.calibrate(listOf(lab(1, 100.0, 80.0)), Curves(0.0, { 5.0 })) is Refused)
    }

    @Test fun theBackgroundIsCountedOnceAndNeverScaled() {
        val curves = Curves(24.0, { 100.0 }, { 40.0 })
        assertEquals(24.0 + 2.0 * 140.0, TotalE2Calibration.total(curves, 2.0, 5.0), 1e-12)
        assertEquals(24.0, TotalE2Calibration.total(Curves(24.0, { 0.0 }), 7.0, 5.0), 0.0)
    }

    @Test fun aLabFarAboveTheCurveRaisesTheWholeCurveAndNothingCapsIt() {
        // Two peak-side labs well above the population curve: k is large, finite, and the calibrated curve follows without a concentration limit.
        val labs = listOf(lab(1, 8.0 + 24 * 10 + 1.0, 2500.0, hours = 1.0), lab(2, 8.0 + 24 * 11 + 1.5, 2400.0, hours = 1.5))
        val o = calibrated(TotalE2Calibration.calibrate(labs, daily))
        assertTrue(o.factor > 3.0 && o.factor.isFinite())
        assertTrue(TotalE2Calibration.total(daily, o.factor, 8.0 + 24 * 12) > 1000.0)
    }

    @Test fun theFitDoesNotDependOnTheOrderOfTheLabs() {
        val labs = listOf(lab(1, 9.0 + 24 * 10, 260.0, hours = 1.0), lab(2, 13.0 + 24 * 11, 70.0, hours = 5.0), lab(3, 20.0 + 24 * 12, 40.0, hours = 12.0))
        val a = calibrated(TotalE2Calibration.calibrate(labs, daily)); val b = calibrated(TotalE2Calibration.calibrate(labs.reversed(), daily))
        assertEquals(a.factor, b.factor, 1e-9); assertEquals(a.informationGain, b.informationGain, 1e-12)
        assertEquals(TotalE2Calibration.calibrate(labs, daily), TotalE2Calibration.calibrate(labs, daily))
    }

    @Test fun leaveOneOutNeedsAtLeastTwoLabs() {
        val one = calibrated(TotalE2Calibration.calibrate(listOf(lab(1, 9.0 + 24 * 10, 260.0, hours = 1.0)), daily))
        assertNull(one.leaveOneOut)
        val two = calibrated(TotalE2Calibration.calibrate(listOf(lab(1, 9.0 + 24 * 10, 260.0, hours = 1.0), lab(2, 10.0 + 24 * 11, 150.0, hours = 2.0)), daily))
        val loo = requireNotNull(two.leaveOneOut); assertEquals(2, loo.labs); assertTrue(loo.medianAbsLn >= 0.0 && loo.rmsLn >= loo.medianAbsLn - 1e-12)
        assertEquals((exp(loo.medianAbsLn) - 1.0) * 100.0, loo.medianPercent, 1e-9)
    }

    @Test fun onlyLowPointLabsGiveTooLittleInformationAndAreRefusedWithTheCurveLeftAlone() {
        // Labs just before the next dose: the sublingual curve there is almost gone, so the coefficient cannot be learned.
        val labs = listOf(lab(1, 7.9 + 24 * 10, 40.0, hours = 23.9), lab(2, 7.8 + 24 * 11, 30.0, hours = 23.8))
        val o = TotalE2Calibration.calibrate(labs, Curves(24.0, { t -> val h = ((t % 24.0) + 24.0) % 24.0; if (h >= 8.0) 40.0 * exp(-(h - 8.0) / 3.0) else 40.0 * exp(-(h + 16.0) / 3.0) }))
        assertTrue("$o", o is Refused && o.reasons == listOf(Reason.INSUFFICIENT_INFORMATION))
        assertTrue((o as Refused).informationGain!! < TotalE2Calibration.MIN_INFORMATION_GAIN)
    }

    @Test fun theTierComesFromTheInformationGainOnly() {
        assertEquals(TotalE2Calibration.Tier.LIMITED, TotalE2Calibration.tier(0.5)); assertEquals(TotalE2Calibration.Tier.LIMITED, TotalE2Calibration.tier(0.7499))
        assertEquals(TotalE2Calibration.Tier.SUFFICIENT, TotalE2Calibration.tier(0.75))
        assertEquals(ln(1 + 0.299 * 0.299).let { Math.sqrt(it) }, TotalE2Calibration.SIGMA_LAB, 0.0)
    }
}
