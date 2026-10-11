package net.plainnotes.app.pk.experimental

import java.io.File
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import net.plainnotes.app.pk.*

class ResearchDisplayProtocolTest {
    private val root=File("../docs/design/pr19-calibration-evidence")
    private val reference=JSONObject(File(root,"reference.json").readText())
    private val lock=JSONObject(File(root,"product-lock.json").readText())
    private fun row(id:String):JSONObject = reference.getJSONArray("rows").let { rows ->
        (0 until rows.length()).map{rows.getJSONObject(it)}.single{it.getString("id")==id} }
    @Test fun frozenProductChoiceIsExplicitTraceableAndReproducesTheSelectionRule() {
        assertEquals(reference.getString("default_m2"),ResearchDisplayProtocol.m2Id)
        assertEquals(reference.getString("default_m1"),ResearchDisplayProtocol.m1Id)
        assertEquals(lock.getString("protocol"),ResearchDisplayProtocol.version)
        assertEquals(lock.getString("m1_projection"),ResearchDisplayProtocol.m1ProjectionVersion)
        for(model in listOf(ResearchModel.M1,ResearchModel.M2)) {
            val c=ResearchDisplayProtocol.candidate(model);val expected=lock.getJSONObject(model.name)
            assertEquals(expected.getString("id"),c.id)
            assertEquals(expected.getString("runtime_signature"),ResearchDisplayProtocol.signature(c))
            assertEquals(expected.getString("runtime_parameter_sha256"),ResearchDisplayProtocol.parameterHash(c))
        }
        assertEquals(ResearchSublingualV01.candidates.minBy{it.exposedDataPseudoLoss}.id,ResearchDisplayProtocol.m2Id)
        assertEquals(28,row(ResearchDisplayProtocol.m1Id).getInt("modal_count"))
        assertFalse(ResearchSublingualV01.productionEnabled)
    }
    @Test fun priceWeightedAmplitudeAndTrueExtremaAgreeWithIndependentScipyForAllCandidates() {
        for(model in listOf(ResearchModel.M1,ResearchModel.M2)) {
            val candidates=if(model==ResearchModel.M2) ResearchSublingualV01.candidates else ResearchM1Catalog.candidates
            for(c in candidates) {
                val r=row(c.id);val anchor=ResearchDisplayProtocol.anchor(model,c)
                assertEquals(PriceFigure1997.STUDY_ID,anchor.studyId)
                assertEquals(r.getDouble("anchor_b"),anchor.background,0.0)
                assertEquals(r.getDouble("anchor_a"),anchor.oneMgIncrementAtOneHour,1e-8)
                fun exact(t:Double)=ResearchSublingualV01.studyScenario(t,listOf(ExperimentalDose(0.0,1.0)),c,anchor)
                val peak=ResearchExtrema.maximum(0.0,24.0,value=::exact)
                assertEquals(r.getJSONObject("price").getDouble("cmax_pgml"),peak.pgMl,1e-7)
                assertEquals(r.getJSONObject("price").getDouble("tmax_h"),peak.hour,2e-6)
                val coarse=ResearchExtrema.maximum(0.0,24.0,1.0/12,::exact)
                assertEquals(peak.pgMl,coarse.pgMl,1e-7)
                assertEquals(peak.hour,coarse.hour,2e-6)
                for(i in 0 until reference.getJSONArray("times_h").length()) assertEquals(r.getJSONArray("values").getDouble(i),exact(reference.getJSONArray("times_h").getDouble(i)),1e-8)
                // All supported shapes decrease after +8h: only recent dose intervals need dense chart peak searches.
                var prev=exact(8.0)
                for(i in 1..96) {val v=exact(8.0+i/6.0);assertTrue(v<=prev+1e-10,c.id);prev=v}
                var auc=0.0;var prevY=exact(0.0)-anchor.background
                for(i in 1..48000) {val y=exact(i/2000.0)-anchor.background;auc+=(prevY+y)/4000;prevY=y}
                assertEquals(r.getJSONObject("price").getDouble("auc_increment_0_24"),auc,0.002)
            }
        }
    }
    @Test fun legacyOriginalAbsoluteKernelAgreesWithIndependentParameterReference() {
        val e=DoseEvent("synthetic",Route.SUBLINGUAL,0.0,1.0,Ester.E2,70.0,DoseExtras(sublingualTier=2.0))
        fun exact(t:Double)=Engine.simulate(listOf(e),grid=doubleArrayOf(t))!!.curves.getValue(Curve.E2)[0]
        val peak=ResearchExtrema.maximum(0.0,24.0,value=::exact)
        val expected=reference.getJSONObject("legacy").getJSONObject("metrics")
        assertEquals(expected.getDouble("cmax_pgml"),peak.pgMl,1e-6)
        assertEquals(expected.getDouble("tmax_h"),peak.hour,3e-6)
        var auc=0.0;var previous=exact(0.0)
        for(i in 1..48000){val y=exact(i/2000.0);auc+=(previous+y)/4000;previous=y}
        assertEquals(expected.getDouble("auc_total_0_24"),auc,0.002)
    }
    @Test fun extremaHandleNearTiesFlatBackgroundInvalidValuesAndEndpoints() {
        val p=ResearchExtrema.maximum(0.0,4.0){maxOf(10.0-(it-.92345)*(it-.92345),10.000001-(it-2.6789)*(it-2.6789))}
        assertEquals(2.6789,p.hour,1e-6)
        assertEquals(0.0,ResearchExtrema.maximum(0.0,1.0){24.0}.hour,0.0)
        assertEquals(2.0,ResearchExtrema.maximum(0.0,2.0){it}.hour,0.0)
        for(v in listOf(Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-1.0)) assertThrows(IllegalArgumentException::class.java){ResearchExtrema.maximum(0.0,1.0){v}}
    }
}
