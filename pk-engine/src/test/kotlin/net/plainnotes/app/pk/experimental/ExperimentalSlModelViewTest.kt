package net.plainnotes.app.pk.experimental

import net.plainnotes.app.pk.Curve
import net.plainnotes.app.pk.DoseEvent
import net.plainnotes.app.pk.DoseExtras
import net.plainnotes.app.pk.Engine
import net.plainnotes.app.pk.Ester
import net.plainnotes.app.pk.Route
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** The opt-in page's read-only computation entry; dimensionless shapes only. */
class ExperimentalSlModelViewTest {
    private val now = 500_000.0
    private fun sl(id: String, at: Double, mg: Double = 1.0, tier: Double = 2.0) =
        DoseEvent(id, Route.SUBLINGUAL, at, mg, Ester.E2, 70.0, DoseExtras(sublingualTier = tier))
    private val first = ExperimentalSlModelView.candidates.first().id
    // Irregular: gaps of 5.5, 7.25, 13, 2 and 20 h, varying doses.
    private val irregular = listOf(sl("a", now - 47.75, 1.0), sl("b", now - 42.25, 0.5), sl("c", now - 35.0, 2.0),
        sl("d", now - 22.0, 0.25), sl("e", now - 20.0, 1.5), sl("f", now - 0.5, 1.0))

    private fun close(expected: DoubleArray, actual: DoubleArray, tolerance: Double) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { assertEquals(expected[it], actual[it], tolerance * maxOf(1.0, abs(expected[it]))) }
    }

    @Test fun identicalInputsGiveIdenticalOutput() {
        val a = ExperimentalSlModelView.compute(irregular, now, first)!!
        val b = ExperimentalSlModelView.compute(irregular.shuffled(java.util.Random(7)), now, first)!!
        assertArrayEquals(a.timeHours, b.timeHours)
        assertArrayEquals(a.legacyRelative, b.legacyRelative)
        assertArrayEquals(a.m2Relative, b.m2Relative)
        assertArrayEquals(a.m2Minimum, b.m2Minimum)
        assertArrayEquals(a.m2Maximum, b.m2Maximum)
    }

    @Test fun reusesResearchComparatorExactlyForItsOwnCandidate() {
        for (baseline in ResearchShapeComparisonV01.assumedPriceBaselines) {
            val research = ResearchShapeComparisonV01.compare(irregular, now, baseline)!!
            val view = ExperimentalSlModelView.compute(irregular, now, research.candidateId)!!
            assertArrayEquals(research.timeHours, view.timeHours)
            assertArrayEquals(research.legacyRelative, view.legacyRelative)
            close(research.experimentalRelative, view.m2Relative, 1e-12)
        }
    }

    @Test fun irregularRepeatedDosesSuperposeLinearlyInM2() {
        val whole = ExperimentalSlModelView.compute(irregular, now, first)!!
        val sum = DoubleArray(whole.timeHours.size)
        for (e in irregular) {
            val one = ExperimentalSlModelView.compute(listOf(e), now, first)!!
            one.m2Relative.forEachIndexed { i, v -> sum[i] += v }
        }
        close(sum, whole.m2Relative, 1e-12)
        val doubled = ExperimentalSlModelView.compute(irregular.map { it.copy(doseMG = it.doseMG * 2) }, now, first)!!
        close(whole.m2Relative.map { it * 2 }.toDoubleArray(), doubled.m2Relative, 1e-12)
    }

    @Test fun eachCandidateIsCoherentAndInsideTheExploredRange() {
        val legacy = ExperimentalSlModelView.compute(irregular, now, first)!!.legacyRelative
        val distinct = mutableSetOf<List<Double>>()
        for (c in ExperimentalSlModelView.candidates) {
            val v = ExperimentalSlModelView.compute(irregular, now, c.id)!!
            assertEquals(c, v.candidate)
            assertArrayEquals(legacy, v.legacyRelative, "legacy must not depend on the M2 candidate")
            v.timeHours.indices.forEach { assertTrue(v.m2Minimum[it] <= v.m2Relative[it] && v.m2Relative[it] <= v.m2Maximum[it]) }
            distinct += v.m2Relative.toList()
            val single = ResearchSublingualV01.coherentCandidateSeries(v.timeHours.toList(), irregular.map { ExperimentalDose(it.timeH, it.doseMG) })
                .single { it.candidateId == c.id }.relativeValues
            close(single.toDoubleArray(), v.m2Relative, 0.0)
        }
        assertEquals(15, distinct.size)
    }

    @Test fun everyOutputIsFiniteAndNonNegative() {
        for (c in ExperimentalSlModelView.candidates) {
            val v = ExperimentalSlModelView.compute(irregular, now, c.id)!!
            for (arr in listOf(v.legacyRelative, v.m2Relative, v.m2Minimum, v.m2Maximum)) assertTrue(arr.all { it.isFinite() && it >= 0.0 })
        }
    }

    @Test fun windowEndsAtEvaluationTimeWithoutForecastOrFutureDoses() {
        val withFuture = irregular + sl("planned", now + 1.0, 5.0)
        val v = ExperimentalSlModelView.compute(withFuture, now, first)!!
        assertEquals(now, v.timeHours.last(), 0.0)
        assertEquals(now - ExperimentalSlModelView.WINDOW_HOURS, v.timeHours.first(), 0.0)
        assertArrayEquals(ExperimentalSlModelView.compute(irregular, now, first)!!.m2Relative, v.m2Relative)
        assertEquals(irregular.size, v.consideredDoses)
    }

    @Test fun nonSublingualEstradiolInputsAreIgnoredOrYieldNoView() {
        val others = listOf(DoseEvent("oral", Route.ORAL, now - 2, 2.0, Ester.E2, 70.0),
            DoseEvent("ev", Route.SUBLINGUAL, now - 3, 1.0, Ester.EV, 70.0),
            DoseEvent("inj", Route.INJECTION, now - 4, 5.0, Ester.EV, 70.0))
        assertNull(ExperimentalSlModelView.compute(others, now, first))
        assertNull(ExperimentalSlModelView.compute(emptyList(), now, first))
        assertArrayEquals(ExperimentalSlModelView.compute(irregular, now, first)!!.m2Relative,
            ExperimentalSlModelView.compute(irregular + others, now, first)!!.m2Relative)
    }

    @Test fun truncatingOldHistoryChangesNeitherCurveMeasurably() {
        // 120 days of twice-daily doses; the view keeps only 30 days before its 48-hour window.
        val history = (0 until 240).map { sl("h$it", now - 0.25 - it * 12.0, 1.0) }
        val v = ExperimentalSlModelView.compute(history, now, first)!!
        assertTrue(v.omittedOlderDoses > 100)
        val all = history.map { ExperimentalDose(it.timeH, it.doseMG) }
        val full = DoubleArray(v.timeHours.size) { ResearchSublingualV01.relativeHistory(v.timeHours[it], all, v.candidate) }
        close(full, v.m2Relative, 1e-12)
        // Legacy model on the full history, same reference as the comparator.
        val engine = Engine.simulate(history, grid = v.timeHours)!!.curves.getValue(Curve.E2)
        val reference = Engine.simulate(listOf(history.maxBy { it.timeH }.copy(id = "ref", timeH = 0.0)), grid = doubleArrayOf(1.0))!!.curves.getValue(Curve.E2)[0]
        close(engine.map { it / reference }.toDoubleArray(), v.legacyRelative, 1e-9)
    }

    @Test fun longTailIsFlaggedAfterEightHoursWithoutADose() {
        val v = ExperimentalSlModelView.compute(listOf(sl("one", now - 20.0)), now, first)!!
        v.timeHours.forEachIndexed { i, t ->
            val since = t - (now - 20.0)
            assertEquals(since < 0 || since > ExperimentalSlModelView.TAIL_AFTER_HOURS, v.longTail[i], "t-dose=$since")
        }
        assertEquals(now - 20.0, v.latestDoseHour, 0.0)
    }

    @Test fun veryCloseAndVerySmallDosesStayFiniteAndContinuous() {
        val split = listOf(sl("x", now - 10.0, 0.5), sl("y", now - 10.0 + 1.0 / 3600, 0.5))
        val merged = listOf(sl("z", now - 10.0, 1.0))
        val a = ExperimentalSlModelView.compute(split, now, first)!!
        val b = ExperimentalSlModelView.compute(merged, now, first)!!
        val peak = b.m2Relative.max()
        a.m2Relative.indices.forEach { assertEquals(b.m2Relative[it], a.m2Relative[it], 1e-3 * peak) }
        val tiny = ExperimentalSlModelView.compute(listOf(sl("t", now - 3.0, 1e-9)), now, first)!!
        assertTrue(tiny.m2Relative.all { it.isFinite() && it >= 0.0 } && tiny.m2Relative.max() > 0.0)
    }

    @Test fun longHistoryStaysFastEnough() {
        val history = (0 until 180 * 6).map { sl("l$it", now - 0.1 - it * 4.0, 0.5) }
        val start = System.nanoTime()
        ExperimentalSlModelView.candidates.forEach { assertNotNull(ExperimentalSlModelView.compute(history, now, it.id)) }
        assertTrue((System.nanoTime() - start) / 1e9 < 20.0, "15 candidates over 180 days must finish well within 20 s on the JVM")
    }

    @Test fun rejectsUnknownCandidateAndInvalidTime() {
        assertThrows(IllegalArgumentException::class.java) { ExperimentalSlModelView.compute(irregular, now, "best") }
        assertThrows(IllegalArgumentException::class.java) { ExperimentalSlModelView.compute(irregular, Double.NaN, first) }
        assertThrows(IllegalArgumentException::class.java) { ExperimentalSlModelView.compute(irregular, Double.POSITIVE_INFINITY, first) }
        assertEquals(ResearchSublingualV01.candidates.map { it.id }, ExperimentalSlModelView.candidates.map { it.id })
    }
}
