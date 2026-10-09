package net.plainnotes.app.pk

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.exp

/** Lab calibration and Monte Carlo bands, on synthetic data. */
class LabFitTest {
    private val events = (0 until 60).map { DoseEvent("d$it", Route.ORAL, 1000.0 + it * 12.0, 2.0, Ester.EV, 70.0) }
    private fun truth(amp: Double, rate: Double, hour: Double) =
        Engine.simulate(events, grid = doubleArrayOf(hour)) { c, _ -> if (c == Curve.E2) Scale(amp, rate) else Scale() }!!.curves.getValue(Curve.E2)[0]
    private fun lab(id: String, hour: Double, value: Double) = LabResult(id, hour, value, LabUnit.PG_ML)

    @Test fun recoversAKnownPersonalFactor() {
        val labs = listOf(1200.0, 1330.0, 1500.0, 1650.0).mapIndexed { i, h -> lab("l$i", h, truth(1.6, 0.8, h)) }
        val m = LabFit.fit(events, labs)
        assertEquals(1.6, exp(m.logAmplitude), 0.25)
        assertEquals(4, m.postDoseObservationCount)
        assertTrue(m.convergenceScore > 0.5)
    }

    @Test fun noLabsKeepsPriorAndBaselineRequiresExplicitConfirmation() {
        val none = LabFit.fit(events, emptyList())
        assertEquals(0.0, none.logAmplitude); assertEquals(0.0, none.convergenceScore, 1e-9)
        val m = LabFit.fit(events, listOf(lab("b1", 900.0, 20.0), lab("b2", 950.0, 30.0), lab("p", 1300.0, truth(1.0, 1.0, 1300.0) + 25.0)), confirmedBaselineLabIds=setOf("b1","b2"))
        assertEquals(25.0, m.baselinePGmL!!, 1e-9); assertEquals(1, m.postDoseObservationCount)
    }

    @Test fun tenFoldLabIsExcluded() {
        val labs = listOf(1200.0, 1330.0, 1500.0).mapIndexed { i, h -> lab("l$i", h, truth(1.0, 1.0, h)) } + lab("x", 1600.0, 10 * truth(1.0, 1.0, 1600.0))
        val m = LabFit.fit(events, labs)
        assertEquals(setOf("x"), m.excludedLabIds)
        assertEquals(1.0, exp(m.logAmplitude), 0.15)
        assertTrue(LabFit.lastDiagnostics(events, labs)!!.isOutlier)
    }

    @Test fun bandsContainTheCurveAndNarrowWithLabs() {
        val grid = DoubleArray(400) { 1000.0 + it * 2.0 }
        val pop = LabFit.bands(events, grid, emptyList(), CalibrationMode.RETROSPECTIVE).getValue(Curve.E2)
        val labs = listOf(1200.0, 1330.0, 1500.0, 1650.0).mapIndexed { i, h -> lab("l$i", h, truth(1.0, 1.0, h)) }
        val cal = LabFit.bands(events, grid, labs, CalibrationMode.RETROSPECTIVE).getValue(Curve.E2)
        for (i in 50 until 400) {
            assertTrue(pop.p5[i] <= pop.center[i] * 1.0001 && pop.center[i] <= pop.p95[i] * 1.0001, "center inside band at $i")
            assertTrue(pop.p25[i] <= pop.p75[i])
        }
        val width = { b: BandedCurve -> (300 until 400).sumOf { b.p95[it] - b.p5[it] } }
        assertTrue(width(cal) < 0.7 * width(pop), "calibration narrows the band")
    }

    @Test fun causalModeUsesOnlyEarlierLabs() {
        val grid = doubleArrayOf(1100.0, 1250.0, 1700.0)
        val labs = listOf(lab("l", 1200.0, 3 * truth(1.0, 1.0, 1200.0)))
        val causal = LabFit.bands(events, grid, labs, CalibrationMode.CAUSAL).getValue(Curve.E2)
        val pop = Engine.simulate(events, grid = grid)!!.curves.getValue(Curve.E2)
        assertEquals(pop[0], causal.center[0], 1e-9)          // before the lab: population curve
        assertTrue(causal.center[1] > 1.5 * pop[1])            // after it: raised
    }

    @Test fun halfAYearOfTwiceDailyDosesIsFast() {
        val many = (0 until 420).map { DoseEvent("d$it", Route.ORAL, it * 12.0, 2.0, Ester.EV, 70.0) } +
            (0 until 210).map { DoseEvent("c$it", Route.ORAL, it * 24.0, 10.0, Ester.CPA, 70.0) }
        val grid = Engine.gridFor(many, 5400.0)
        val labs = listOf(lab("a", 2000.0, 80.0), lab("b", 4000.0, 90.0))
        val start = System.nanoTime()
        val bands = LabFit.bands(many, grid, labs, CalibrationMode.RETROSPECTIVE)
        val seconds = (System.nanoTime() - start) / 1e9
        assertEquals(setOf(Curve.E2, Curve.CPA), bands.keys)
        assertTrue(seconds < 20, "took %.1f s".format(seconds))
        println("bands for %d grid points, %d events: %.2f s".format(grid.size, many.size, seconds))
    }
}
