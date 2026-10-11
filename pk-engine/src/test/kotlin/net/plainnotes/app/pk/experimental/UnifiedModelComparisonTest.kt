package net.plainnotes.app.pk.experimental

import net.plainnotes.app.pk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class UnifiedModelComparisonTest {
    private val t = 600_000.0
    private fun dose(at: Double = t, mg: Double = 1.0) = DoseEvent("dose-$at", Route.SUBLINGUAL, at, mg, Ester.E2, 70.0, DoseExtras(sublingualTier = 2.0))
    private fun run(display: ResearchDisplay = ResearchDisplay.CONDITIONAL, events: List<DoseEvent> = listOf(dose()),
                    c: CommonCalibration = CommonCalibration(25.0, 100.0)) = UnifiedModelComparison.compute(events, t, t+24, t,
        ResearchSublingualV01.candidates.first().id, ResearchM1Catalog.candidates.first().id, display, c)!!
    private fun sha(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    @Test fun all32M1CandidatesMatchIndependentPythonReferenceAndLockedInputs() {
        val reference = JSONObject(javaClass.getResource("/unified-m1-python-reference.json")!!.readText())
        assertEquals(ResearchM1Catalog.parameterSha256, reference.getString("parameter_sha256"))
        assertEquals(ResearchM1Catalog.parameterSha256, sha(File("../docs/design/unified-model-inputs/m1-candidates.json")))
        assertEquals(reference.getString("source_sha256"), sha(File("../tools/pk-research/p2aw_sampling_design.py")))
        val times = reference.getJSONArray("times"); val rows = reference.getJSONArray("reference")
        assertEquals(32, rows.length()); assertEquals(32, ResearchM1Catalog.candidates.size)
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i); val c = ResearchM1Catalog.candidates[i]
            assertEquals(c.id, row.getString("id")); assertEquals(0.0, c.slowWeight)
            for (j in 0 until times.length()) {
                val expected = row.getJSONArray("values").getDouble(j)
                assertEquals(expected, ResearchSublingualV01.relativeIncrement(times.getDouble(j), c), maxOf(1e-13, expected * 1e-9), "${c.id} t=${times.getDouble(j)}")
            }
        }
    }
    @Test fun commonCalibrationAtOneHourAndBeforeDoseHasExactlyOneBackground() {
        val r = run()
        UnifiedModelComparison.valuesAt(r, t+1).values.forEach { assertEquals(125.0, it, 1e-8) }
        UnifiedModelComparison.valuesAt(r, t-1).values.forEach { assertEquals(25.0, it, 1e-12) }
        val relative = run(ResearchDisplay.RELATIVE)
        UnifiedModelComparison.valuesAt(relative, t-1).values.forEach { assertEquals(0.0, it, 1e-12) }
    }
    @Test fun repeatedDosesSuperposeIncrementNotBackground() {
        val one = run(); val repeated = run(events = listOf(dose(t), dose(t-6, 2.0)))
        val first = UnifiedModelComparison.valuesAt(one, t+1)
        val older = UnifiedModelComparison.valuesAt(run(events=listOf(dose(t-6,2.0))), t+1)
        val total = UnifiedModelComparison.valuesAt(repeated,t+1)
        ResearchModel.entries.forEach { assertEquals(first.getValue(it) + older.getValue(it) - 25.0, total.getValue(it), 1e-8) }
    }
    @Test fun legacyShapeUsesUnchangedProductionEngineOnIdenticalInputs() {
        val events=listOf(dose(t-6, 2.0),dose())
        val r=run(ResearchDisplay.RELATIVE,events)
        val actual=Engine.simulate(events,grid=r.base.timeHours)!!.curves.getValue(Curve.E2)
        val ref=Engine.simulate(listOf(dose(0.0)),grid=doubleArrayOf(1.0))!!.curves.getValue(Curve.E2)[0]
        assertArrayEquals(DoubleArray(actual.size){actual[it]/ref},r.series.getValue(ResearchModel.LEGACY),1e-12)
    }
    @Test fun exactReadoutMatchesAllGridSamplesAndFractionalTimeKernels() {
        val r=run(events=listOf(dose(t-6),dose()))
        for(i in r.base.timeHours.indices step 53) {
            val v=UnifiedModelComparison.valuesAt(r,r.base.timeHours[i])
            ResearchModel.entries.forEach { assertEquals(r.series.getValue(it)[i],v.getValue(it),1e-8) }
        }
        val at=t+1.234567
        val expected=25+100*ResearchSublingualV01.relativeHistory(at,r.base.doses.map {ExperimentalDose(it.first,it.second)},r.m1Candidate)
        assertEquals(expected,UnifiedModelComparison.valuesAt(r,at).getValue(ResearchModel.M1),1e-12)
    }
    @Test fun aucAddsBackgroundOnceAndUnitConversionIsSharedForAllCurves() {
        val rel=run(ResearchDisplay.RELATIVE);val abs=run()
        ResearchModel.entries.forEach { m ->
            val a=abs.metrics.getValue(m);val b=rel.metrics.getValue(m)
            assertEquals(25*4+100*b.auc0to4,a.auc0to4,1e-8)
            assertEquals(25*8+100*b.auc0to8,a.auc0to8,1e-8)
            assertEquals(25*24+100*b.auc0to24,a.auc0to24,1e-8)
            assertEquals(b.peakHoursAfterOrigin,a.peakHoursAfterOrigin,0.0)
        }
        assertEquals(3.671,abs.factor(true));assertEquals(1.0,abs.factor(false));assertEquals(1.0,rel.factor(true))
    }
    @Test fun priceScenarioIgnoresPersonalHistoryAndSharesOneStudyAnchor() {
        val a=run(ResearchDisplay.STUDY);val b=run(ResearchDisplay.STUDY,events=listOf(dose(t-100,1000.0)))
        assertEquals(listOf(0.0 to 1.0),a.base.doses)
        assertEquals(0.0,a.base.originHour)
        ResearchModel.entries.forEach { assertArrayEquals(a.series.getValue(it),b.series.getValue(it));assertEquals(450.0,UnifiedModelComparison.valuesAt(a,1.0).getValue(it),1e-9) }
        assertEquals(PriceFigure1997.means[0]-a.calibration.backgroundPgMl,a.calibration.amplitudePgMl)
        assertNotEquals(PriceFigure1997.TABLE1_AUC_0_24,PriceFigure1997.figureAuc0to24AssumingZeroPredose())
    }
    @Test fun invalidCalibrationTimeAndVersionFailClosed() {
        for (v in listOf(Double.NaN,Double.POSITIVE_INFINITY,-1.0)) {
            assertThrows(IllegalArgumentException::class.java){CommonCalibration(v,100.0)}
            assertThrows(IllegalArgumentException::class.java){CommonCalibration(25.0,v)}
            if (!v.isFinite()) assertThrows(IllegalArgumentException::class.java){UnifiedModelComparison.valuesAt(run(),v)}
        }
        assertThrows(IllegalArgumentException::class.java){CommonCalibration(25.0,0.0)}
        assertThrows(IllegalArgumentException::class.java){UnifiedModelComparison.compute(listOf(dose()),t,t+24,t,"unknown","unknown",ResearchDisplay.RELATIVE)}
    }
}
