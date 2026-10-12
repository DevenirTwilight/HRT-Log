package net.plainnotes.app

import net.plainnotes.app.conc.experimental.SlAdapterAudit
import net.plainnotes.app.experimental.ConcentrationPreview
import net.plainnotes.app.pk.*
import net.plainnotes.app.pk.experimental.*
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConcentrationPreviewTest {
    private val now=600000.0
    private fun event(t:Double,mg:Double=1.0)=DoseEvent("synthetic-$t",Route.SUBLINGUAL,t,mg,Ester.E2,70.0,DoseExtras(sublingualTier=2.0))
    private val events=listOf(-30.0,-20.0,-3.0).map{event(now+it)}
    private val audit=SlAdapterAudit(events,emptyMap())
    private val reference=JSONObject(File("../docs/design/pr19-calibration-evidence/reference.json").readText())
    private fun row(id:String)=reference.getJSONArray("rows").let{r -> (0 until r.length()).map{r.getJSONObject(it)}.single{it.getString("id")==id}}
    @Test fun defaultProductPreviewUsesRealLiteratureFitInHeroChartAndExactReadout() {
        for(model in listOf(ResearchModel.M2,ResearchModel.M1)) {
            val c=ResearchDisplayProtocol.candidate(model);val r=row(c.id)
            val p=ConcentrationPreview.compute(SlAdapterAudit(listOf(event(now)),emptyMap()),model,now)
            assertEquals(reference.getString("default_${model.name.lowercase()}"),p.candidateId)
            assertEquals(PriceFigure1997.STUDY_ID,p.anchor.studyId)
            assertEquals(r.getDouble("anchor_b"),p.currentPgMl!!,0.0)
            for(i in 0 until reference.getJSONArray("times_h").length()) assertEquals(r.getJSONArray("values").getDouble(i),p.valueAt(now+reference.getJSONArray("times_h").getDouble(i))!!,1e-7)
            assertEquals(r.getJSONObject("price").getDouble("cmax_pgml"),p.values.max(),1e-5)
            assertEquals(p.singleDosePeak!!.pgMl,p.values.max(),1e-7)
            assertEquals(p.singleDosePeak!!.hour,p.historyWindowPeak!!.hour-now,2e-6)
            assertNotEquals(125.0,p.valueAt(now+1.0)!!,1.0)
            for(i in p.timeH.indices) assertEquals(p.valueAt(p.timeH[i])!!,p.values[i],0.0)
        }
    }
    @Test fun originalChartViewportRetainsTheRefinedModelPeakInBothUnits() {
        for(model in listOf(ResearchModel.M1,ResearchModel.M2)) {
            val p=ConcentrationPreview.compute(SlAdapterAudit(listOf(event(now,2.0)),emptyMap()),model,now)
            for(factor in listOf(1.0,Pk.PMOL_PER_PG)) {
                val shown=net.plainnotes.app.ui.ChartViewport.samples(p.timeH,DoubleArray(p.values.size){p.values[it]*factor},now,now+4.123,now)
                assertEquals(p.historyWindowPeak!!.pgMl*factor,shown.maxOf{it.second},1e-5*factor)
            }
        }
    }
    @Test fun doseScalingRepeatedHistoryAndBackgroundOnceDoNotAlterActualEvents() {
        for(model in listOf(ResearchModel.M1,ResearchModel.M2)) {
            val full=ConcentrationPreview.compute(audit,model,now);val b=full.anchor.background
            val sums=events.sumOf{ConcentrationPreview.compute(SlAdapterAudit(listOf(it),emptyMap()),model,now).currentPgMl!!-b}
            assertEquals(b+sums,full.currentPgMl!!,1e-8)
            val one=ConcentrationPreview.compute(SlAdapterAudit(listOf(event(now)),emptyMap()),model,now)
            val two=ConcentrationPreview.compute(SlAdapterAudit(listOf(event(now,2.0)),emptyMap()),model,now)
            assertEquals(b+2*(one.singleDosePeak!!.pgMl-b),two.historyWindowPeak!!.pgMl,1e-7)
            assertEquals(one.historyWindowPeak!!.hour,two.historyWindowPeak!!.hour,2e-6)
            val expected=full.anchor.background+full.anchor.oneMgIncrementAtOneHour*ResearchSublingualV01.relativeHistory(now,events.map{ExperimentalDose(it.timeH,it.doseMG)},ResearchDisplayProtocol.candidate(model))
            assertEquals(expected,full.currentPgMl!!,1e-8)
            assertEquals(expected*Pk.PMOL_PER_PG,full.currentPgMl!!*Pk.PMOL_PER_PG,1e-7)
            assertEquals(b,full.valueAt(now-31)!!,0.0)
        }
        assertEquals(events,audit.events)
    }
    @Test fun offGridPeaksOfDifferentCandidatesAndCloseRepeatedDosesAreRepresented() {
        for(model in listOf(ResearchModel.M1,ResearchModel.M2)) {
            val candidates=if(model==ResearchModel.M2) listOf(ResearchSublingualV01.candidates.single{it.id=="p2x-14"},ResearchDisplayProtocol.candidate(model)) else listOf(ResearchM1Catalog.candidates.single{it.id=="p2az-m1-00"},ResearchDisplayProtocol.candidate(model))
            for(c in candidates) {
                val doses=listOf(event(now-1.37),event(now-.73,.99999),event(now-.01,2.0))
                val p=ConcentrationPreview.compute(SlAdapterAudit(doses,emptyMap()),model,now,c)
                val exact=ResearchExtrema.maximum(now-1.37,now+24,1.0/120){p.valueAt(it)!!}
                assertEquals(exact.pgMl,p.values.max(),1e-5)
                assertEquals(exact.pgMl,p.valueAt(exact.hour)!!,0.0)
                assertTrue(p.timeH.toList().zipWithNext().all{it.first<it.second})
            }
        }
    }
    @Test fun twiceDailySeveralMonthsOfQualifiedHistoryProducesBothResearchPreviews() {
        val instant=java.time.Instant.parse("2026-10-12T12:00:00Z")
        val hour=net.plainnotes.app.conc.ConcentrationCalculator.hours(instant)
        val med=net.plainnotes.app.data.MedicationEntity(1,"Synthetic SL E2","E2","SUBLINGUAL","MG",1.0,30.0,null,15,120,false,null,true,true,0)
        val profile=net.plainnotes.app.data.ProfileEntity(1,"E2","sublingual",sl_tier=2)
        val snapshot=net.plainnotes.app.data.MedicationSnapshot.encode(med,profile)
        val records=(0 until 360).map { i -> net.plainnotes.app.data.RecordEntity((i+1).toLong(),1,
            taken_utc=instant.minusSeconds((3L+12L*i)*3600).toEpochMilli(),taken_zone="UTC",actual_dose=1.0,
            status="ON_TIME",origin="APP",revision=1,config_snapshot=snapshot) }
        val history=net.plainnotes.app.conc.experimental.ResearchHistoricalSlAdapter.audit(records,emptyMap(),hour)
        assertEquals(360,history.events.size)
        for(model in listOf(ResearchModel.M1,ResearchModel.M2)) {
            val start=System.nanoTime()
            val p=ConcentrationPreview.compute(history,model,hour)
            val elapsedMs=(System.nanoTime()-start)/1e6
            println("COMPLETE_PREVIEW_BENCHMARK model=$model doses=360 points=${p.timeH.size} compute_ms=$elapsedMs")
            assertNotNull(p.currentPgMl)
            assertTrue(p.currentPgMl!!.isFinite())
            assertTrue(p.timeH.isNotEmpty())
            assertEquals(p.currentPgMl!!,p.valueAt(hour)!!,0.0)
        }
    }
    @Test fun cancelledResearchConstructionStopsBeforeCompletingLongHistory() {
        var calls=0
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            ConcentrationPreview.compute(audit,ResearchModel.M2,now,checkCancelled={
                if(++calls==20) throw java.util.concurrent.CancellationException("synthetic cancellation")
            })
        }
        assertEquals(20,calls)
    }
    @Test fun exactReadoutRemainsUsableAfterConstructionCoroutineHasCompleted() {
        var active=true
        val p=ConcentrationPreview.compute(audit,ResearchModel.M2,now,checkCancelled={
            if(!active) throw java.util.concurrent.CancellationException("synthetic completed job")
        })
        active=false
        assertEquals(p.currentPgMl!!,p.valueAt(now)!!,0.0)
    }
    @Test fun emptyPendingInvalidTimesDosesAndMixedRoutesCannotInventPersonalConcentrations() {
        for(model in listOf(ResearchModel.M1,ResearchModel.M2)) {
            val p=ConcentrationPreview.compute(SlAdapterAudit(emptyList(),emptyMap()),model,now)
            assertTrue(p.values.isEmpty());assertNull(p.currentPgMl);assertNull(p.valueAt(now))
        }
        assertThrows(IllegalStateException::class.java){ConcentrationPreview.pending(ResearchModel.LEGACY)}
        for(t in listOf(Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java){ConcentrationPreview.compute(audit,ResearchModel.M2,t)}
            assertThrows(IllegalArgumentException::class.java){ConcentrationPreview.compute(audit,ResearchModel.M2,now).valueAt(t)}
        }
        for(e in listOf(event(now,0.0),event(now,-1.0),event(now,Double.NaN),event(now,Double.POSITIVE_INFINITY),event(now+1),event(now).copy(route=Route.ORAL))) assertThrows(IllegalArgumentException::class.java){ConcentrationPreview.compute(SlAdapterAudit(listOf(e),emptyMap()),ResearchModel.M2,now)}
    }
}
