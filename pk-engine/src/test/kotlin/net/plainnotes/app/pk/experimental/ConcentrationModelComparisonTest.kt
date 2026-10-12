package net.plainnotes.app.pk.experimental

import net.plainnotes.app.pk.Curve
import net.plainnotes.app.pk.DoseEvent
import net.plainnotes.app.pk.DoseExtras
import net.plainnotes.app.pk.Engine
import net.plainnotes.app.pk.Ester
import net.plainnotes.app.pk.Route
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs

/** Quantitative Legacy-vs-M2 comparison: one dose set, one grid, real kernels, verified metrics. */
class ConcentrationModelComparisonTest {
    private val t0 = 600_000.0
    private fun sl(id: String, at: Double, mg: Double = 1.0, tier: Double = 2.0) =
        DoseEvent(id, Route.SUBLINGUAL, at, mg, Ester.E2, 70.0, DoseExtras(sublingualTier = tier))
    private val first = ExperimentalSlModelView.candidates.first().id
    private val history = listOf(sl("a", t0 - 30.0, 1.0), sl("b", t0 - 18.5, 0.5), sl("c", t0 - 6.25, 2.0), sl("d", t0, 1.0), sl("e", t0 + 7.0, 0.5))
    private fun run(events: List<DoseEvent> = history, start: Double = t0, end: Double = t0 + 24, origin: Double = t0, id: String = first) =
        ConcentrationModelComparison.compute(events, start, end, origin, id)!!

    @Test fun identicalInputsGiveIdenticalOutputs() {
        val a = run(); val b = run(history.shuffled(java.util.Random(3)))
        assertArrayEquals(a.timeHours, b.timeHours); assertArrayEquals(a.legacy, b.legacy); assertArrayEquals(a.m2, b.m2)
        assertArrayEquals(a.m2Minimum, b.m2Minimum); assertArrayEquals(a.m2Maximum, b.m2Maximum)
        assertEquals(a.m2Metrics.auc0to24, b.m2Metrics.auc0to24, 0.0)
    }

    @Test fun bothModelsUseTheSameDosesOnTheSameGrid() {
        val others = listOf(DoseEvent("oral", Route.ORAL, t0 - 1, 2.0, Ester.E2, 70.0), DoseEvent("ev", Route.SUBLINGUAL, t0 - 2, 1.0, Ester.EV, 70.0))
        val r = run(history + others)
        assertEquals(history.map { it.timeH to it.doseMG }, r.doses)
        val grid = r.timeHours
        assertEquals(t0, grid.first(), 0.0); assertEquals(t0 + 24, grid.last(), 1e-9)
        val step = grid[1] - grid[0]
        grid.toList().zipWithNext().forEach { (x, y) -> assertEquals(step, y - x, 1e-9) }
        // Legacy = production Engine on exactly these doses / its own 1 mg, 1 h reference (latest dose's tier).
        val engine = Engine.simulate(history, grid = grid)!!.curves.getValue(Curve.E2)
        val reference = Engine.simulate(listOf(history.last().copy(id = "r", timeH = 0.0, doseMG = 1.0)), grid = doubleArrayOf(1.0))!!.curves.getValue(Curve.E2)[0]
        grid.indices.forEach { assertEquals(engine[it] / reference, r.legacy[it], 1e-12) }
        // M2 = frozen kernel on the same doses.
        val doses = history.map { ExperimentalDose(it.timeH, it.doseMG) }
        grid.indices.forEach { assertEquals(ResearchSublingualV01.relativeHistory(grid[it], doses, r.candidate), r.m2[it], 1e-12) }
    }

    @Test fun bothCurvesEqualOneHourAfterAnIsolatedOneMilligramDose() {
        val r = run(listOf(sl("one", t0)))
        val i = r.timeHours.indexOfFirst { abs(it - (t0 + 1.0)) < 1e-9 }
        assertTrue(i > 0)
        assertEquals(1.0, r.legacy[i], 1e-9); assertEquals(1.0, r.m2[i], 1e-9)
        assertEquals(0.0, r.legacy[0], 1e-12); assertEquals(0.0, r.m2[0], 1e-12)
    }

    @Test fun singleDosePeakEqualsModelTmaxAndOverlapShiftsThePeak() {
        val single = run(listOf(sl("one", t0)))
        for (m in listOf(single.legacyMetrics, single.m2Metrics)) assertEquals(m.singleDoseTmaxHours, m.peakHoursAfterOrigin, 1e-9)
        assertTrue(single.m2Metrics.singleDoseTmaxHours in 0.8..1.2, "M2 single-dose Tmax ${single.m2Metrics.singleDoseTmaxHours}")
        // A second, larger dose 3 h later: the window peak is no longer the single-dose Tmax.
        val overlap = run(listOf(sl("one", t0), sl("two", t0 + 3.0, 2.0)))
        assertTrue(overlap.m2Metrics.peakHoursAfterOrigin > 3.0)
        assertEquals(single.m2Metrics.singleDoseTmaxHours, overlap.m2Metrics.singleDoseTmaxHours, 0.0)
    }

    @Test fun aucIsTheTrapezoidOfTheCurveAndMatchesTheFrozenP2xColumn() {
        // For one 1 mg dose, M2 AUC 0–24 equals the frozen P2-X normalized AUC (1201-point trapezoid) within grid error.
        val rows = File("../docs/pk-research/p2/p2x-frozen-40-candidates.csv").readLines()
        val header = rows.first().split(","); val col = header.indexOf("AUC_normalized_0_24")
        for (c in ExperimentalSlModelView.candidates) {
            val frozen = rows[1 + c.id.removePrefix("p2x-").toInt()].split(",")[col].toDouble()
            val r = run(listOf(sl("one", t0)), id = c.id)
            assertEquals(frozen, r.m2Metrics.auc0to24, 2e-4 * frozen, c.id)
            assertTrue(r.m2Metrics.auc0to24 < ResearchSublingualV01.aucInfinityRelativeHours(c))
        }
    }

    @Test fun metricsConvergeAndHalfHourSamplingMissesThePeak() {
        val c = ExperimentalSlModelView.candidates.first()
        fun peakAndAuc(step: Double): Pair<Double, Double> {
            val n = Math.round(24.0 / step).toInt(); val g = DoubleArray(n + 1) { 24.0 * it / n }
            val y = DoubleArray(g.size) { ResearchSublingualV01.relativeIncrement(g[it], c) }
            var auc = 0.0; for (i in 1 until g.size) auc += (g[i] - g[i - 1]) * (y[i] + y[i - 1]) / 2
            return y.max() to auc
        }
        val oneMinute = peakAndAuc(1.0 / 60); val fine = peakAndAuc(1.0 / 600); val halfHour = peakAndAuc(0.5)
        assertEquals(fine.first, oneMinute.first, 1e-4 * fine.first)
        assertEquals(fine.second, oneMinute.second, 1e-4 * fine.second)
        val r = run(listOf(sl("one", 0.0)), start = 0.0, end = 24.0, origin = 0.0, id = c.id)
        assertEquals(oneMinute.first, r.m2Metrics.peak, 1e-12); assertEquals(oneMinute.second, r.m2Metrics.auc0to24, 1e-9)
        // The old 0.5 h grid under-reads the sublingual peak; documented, not hidden.
        assertTrue(halfHour.first < fine.first)
    }

    @Test fun responsesAt8h12h24hAreKernelValuesAfterTheOrigin() {
        val r = run()
        val doses = history.map { ExperimentalDose(it.timeH, it.doseMG) }
        assertEquals(ResearchSublingualV01.relativeHistory(t0 + 8, doses, r.candidate), r.m2Metrics.at8h, 1e-12)
        assertEquals(ResearchSublingualV01.relativeHistory(t0 + 12, doses, r.candidate), r.m2Metrics.at12h, 1e-12)
        assertEquals(ResearchSublingualV01.relativeHistory(t0 + 24, doses, r.candidate), r.m2Metrics.at24h, 1e-12)
        assertTrue(r.legacyMetrics.auc0to4 <= r.legacyMetrics.auc0to8 && r.legacyMetrics.auc0to8 <= r.legacyMetrics.auc0to24)
    }

    @Test fun longTailFollowsTheActualDoseTimes() {
        val r = run(start = t0 - 40, end = t0 + 24)
        val times = history.map { it.timeH }
        r.timeHours.forEachIndexed { i, t ->
            val latest = times.filter { it <= t }.maxOrNull()
            assertEquals(latest == null || t - latest > 8.0, r.longTail[i], "t=$t")
        }
    }

    @Test fun candidateRangeContainsEveryCoherentCandidate() {
        val base = run()
        for (c in ExperimentalSlModelView.candidates) {
            val r = run(id = c.id)
            assertArrayEquals(base.legacy, r.legacy, "legacy must not depend on the M2 candidate")
            r.timeHours.indices.forEach { assertTrue(base.m2Minimum[it] <= r.m2[it] + 1e-15 && r.m2[it] <= base.m2Maximum[it] + 1e-15) }
        }
    }

    @Test fun m2IsLinearInDose() {
        val a = run(); val b = run(history.map { it.copy(doseMG = it.doseMG * 3) })
        a.m2.indices.forEach { assertEquals(3 * a.m2[it], b.m2[it], 1e-12 * maxOf(1.0, b.m2[it])) }
        assertEquals(3 * a.m2Metrics.auc0to24, b.m2Metrics.auc0to24, 1e-9 * b.m2Metrics.auc0to24)
        assertEquals(a.m2Metrics.peakHoursAfterOrigin, b.m2Metrics.peakHoursAfterOrigin, 0.0)
    }

    @Test fun tapReadOutIsTheExactKernelValueOnTheSameInputs() {
        val r = run(start = t0 - 12, end = t0 + 36)
        for (i in listOf(0, 37, 500, r.timeHours.lastIndex)) {
            val v = ConcentrationModelComparison.valuesAt(history, r, r.timeHours[i])!!
            assertEquals(r.legacy[i], v.legacy, 1e-12 * maxOf(1.0, v.legacy))
            assertEquals(r.m2[i], v.m2, 1e-12 * maxOf(1.0, v.m2))
            assertEquals(r.m2Minimum[i], v.m2Minimum, 1e-12); assertEquals(r.m2Maximum[i], v.m2Maximum, 1e-12)
            assertEquals(r.longTail[i], v.longTail)
        }
        // Between grid points the read-out is computed, not interpolated.
        val between = (r.timeHours[10] + r.timeHours[11]) / 2
        assertEquals(ResearchSublingualV01.relativeHistory(between, history.map { ExperimentalDose(it.timeH, it.doseMG) }, r.candidate),
            ConcentrationModelComparison.valuesAt(history, r, between)!!.m2, 1e-12)
    }

    @Test fun ratioOnlyWithAPositiveDenominator() {
        assertNull(ConcentrationModelComparison.ratio(1.0, 0.0)); assertNull(ConcentrationModelComparison.ratio(1.0, -2.0))
        assertNull(ConcentrationModelComparison.ratio(Double.NaN, 1.0)); assertEquals(2.0, ConcentrationModelComparison.ratio(4.0, 2.0)!!, 0.0)
    }

    @Test fun invalidOrUnsupportedInputs() {
        assertNull(ConcentrationModelComparison.compute(listOf(DoseEvent("oral", Route.ORAL, t0, 2.0, Ester.E2, 70.0)), t0, t0 + 24, t0, first))
        assertNull(ConcentrationModelComparison.compute(emptyList(), t0, t0 + 24, t0, first))
        assertThrows(IllegalArgumentException::class.java) { ConcentrationModelComparison.compute(history, t0, t0 + 24, t0, "best") }
        assertThrows(IllegalArgumentException::class.java) { ConcentrationModelComparison.compute(history, t0 + 1, t0, t0, first) }
        assertThrows(IllegalArgumentException::class.java) { ConcentrationModelComparison.compute(history, Double.NaN, t0, t0, first) }
    }

    @Test fun longHistoryIsFastEnough() {
        val many = (0 until 180 * 4).map { sl("m$it", t0 - it * 6.0, 0.5) }
        val start = System.nanoTime()
        assertNotNull(ConcentrationModelComparison.compute(many, t0 - 48, t0 + 24, t0, first))
        assertTrue((System.nanoTime() - start) / 1e9 < 20.0)
    }
}
