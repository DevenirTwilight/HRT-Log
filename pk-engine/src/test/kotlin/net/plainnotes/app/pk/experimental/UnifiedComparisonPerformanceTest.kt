package net.plainnotes.app.pk.experimental

import net.plainnotes.app.pk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.json.JSONObject
import java.io.File

/** Reproducible synthetic 30-day stress fixture; timing/heap are measurements, not flaky acceptance thresholds. */
class UnifiedComparisonPerformanceTest {
    @Test fun thirtyDayRepeatedHistoryHasBoundedChartAndExactReadout() {
        val origin=600_000.0
        val doses=(0 until 60).map { i -> DoseEvent("synthetic-$i",Route.SUBLINGUAL,origin-i*12.0,1.0,Ester.E2,70.0,DoseExtras(sublingualTier=2.0)) }
        val runtime=Runtime.getRuntime();val before=runtime.totalMemory()-runtime.freeMemory();val start=System.nanoTime()
        val result=UnifiedModelComparison.compute(doses,origin-168.0,origin,origin,ResearchSublingualV01.candidates.first().id,
            ResearchM1Catalog.candidates.first().id,ResearchDisplay.CONDITIONAL,CommonCalibration(25.0,100.0))!!
        val elapsed=(System.nanoTime()-start)/1_000_000.0;val after=runtime.totalMemory()-runtime.freeMemory()
        assertEquals(60,result.base.doses.size)
        assertTrue(result.base.timeHours.size<=ConcentrationModelComparison.MAX_DISPLAY_POINTS)
        assertTrue(result.series.values.all { it.size==result.base.timeHours.size && it.all { y->y.isFinite() && y>=25.0 } })
        val at=origin+1.234567
        val expected=25.0+100.0*ResearchSublingualV01.relativeHistory(at,doses.map{ExperimentalDose(it.timeH,it.doseMG)},result.base.candidate)
        assertEquals(expected,UnifiedModelComparison.valuesAt(result,at).getValue(ResearchModel.M2),1e-9)
        val file=File("build/reports/unified-performance.json");file.parentFile.mkdirs()
        file.writeText(JSONObject().put("fixture","synthetic-60-doses-over-30-days").put("chart_points",result.base.timeHours.size)
            .put("compute_ms",elapsed).put("jvm_used_heap_before_bytes",before).put("jvm_used_heap_after_bytes",after)
            .put("measurement_limit","Single JVM execution, heap includes unrelated allocations and GC; not Android PSS, device frame rate or a clinical claim.").toString(2)+"\n")
    }
}
