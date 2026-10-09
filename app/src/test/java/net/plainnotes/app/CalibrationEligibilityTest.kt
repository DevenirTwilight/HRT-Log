package net.plainnotes.app

import net.plainnotes.app.conc.*
import net.plainnotes.app.data.*
import net.plainnotes.app.pk.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CancellationException

/** All inputs synthetic; COMPLETE means complete saved model inputs, never a life-long medical history. */
class CalibrationEligibilityTest {
    private val now=Instant.parse("2026-10-25T01:10:00Z")
    private val med=MedicationEntity(1,"Synthetic E2","E2","SUBLINGUAL","MG",2.0,40.0,null,15,60,false,null,true,true,0)
    private val profile=ProfileEntity(1,"E2","sublingual",sl_tier=2)
    private fun record(id:Long,secondsAgo:Long, snapshot:String=MedicationSnapshot.encode(med,profile),dose:Double?=2.0)=
        RecordEntity(id,1,taken_utc=now.minusSeconds(secondsAgo).toEpochMilli(),taken_zone="Europe/Paris",actual_dose=dose,status="ON_TIME",origin="APP",revision=1,config_snapshot=snapshot)
    private fun lab(id:Long,secondsAgo:Long,value:Double=400.0,unit:String="pg/mL")=LabValueEntity(id,"E2",value,unit,now.minusSeconds(secondsAgo).toEpochMilli(),"Europe/Paris")
    private fun compute(records:List<RecordEntity>,labs:List<LabValueEntity>,on:Boolean=true,mode:CalibrationMode=CalibrationMode.RETROSPECTIVE,
                        read:HistoryRead=HistoryRead(true,ConcentrationCalculator.hours(now)),cancel:()->Unit={}):ConcentrationResult =
        ConcentrationCalculator.compute(listOf(med),mapOf(1L to profile),records,emptyList(),labs,80.0,now,on,mode,historyRead=read,checkCancelled=cancel)
    private fun samePopulation(a:ConcentrationResult,b:ConcentrationResult) {
        assertEquals(a.currentPgMl!!,b.currentPgMl!!,1e-9);assertArrayEquals(a.e2,b.e2,0.0)
        assertArrayEquals(a.bandOuter!!.first,b.bandOuter!!.first,0.0);assertArrayEquals(a.bandOuter!!.second,b.bandOuter!!.second,0.0)
    }

    @Test fun completeSingleDoseAndRepeatedMixedRoutesActuallyCalibrate() {
        val sl=listOf(record(1,46*60));val a=compute(sl,listOf(lab(1,0)))
        assertTrue(a.labEligibility.single().eligible);assertNull(a.calibration!!.model.baselinePGmL)
        assertEquals(0.0,a.calibration!!.model.logRate,0.0);assertFalse(a.calibration!!.model.rateAdjustable)
        assertTrue(a.currentPgMl!!>compute(sl,emptyList()).currentPgMl!!+50)
        val oralMed=med.copy(route="ORAL");val oral=ProfileEntity(1,"EV","oral")
        val mixed=sl+record(2,24*3600,MedicationSnapshot.encode(oralMed,oral))
        val b=compute(mixed,listOf(lab(1,0)))
        assertTrue(b.labEligibility.single().eligible);assertTrue(b.calibration!!.model.rateAdjustable)
        assertTrue(b.calibration!!.model.cov[3]>0);assertTrue(b.currentPgMl!!>compute(mixed,emptyList()).currentPgMl!!+1)
    }
    @Test fun onlyEligibleSubsetDrivesBandsSummaryAndDiagnosticsAndRawPointsRemain() {
        val records=listOf(record(1,12*3600),record(2,6*3600,snapshot="{}"),record(3,46*60))
        val good=lab(1,8*3600,120.0);val bad=lab(2,0,400.0)
        val subset=compute(records,listOf(good));val both=compute(records,listOf(good,bad))
        assertEquals(2,both.labs.size);assertEquals(listOf(true,false),both.labEligibility.map{it.eligible})
        assertEquals(1,both.calibration!!.labCount);assertEquals(1,both.calibration!!.model.postDoseObservationCount)
        assertEquals(subset.currentPgMl!!,both.currentPgMl!!,1e-9);assertArrayEquals(subset.bandOuter!!.second,both.bandOuter!!.second,0.0)
        assertEquals(subset.calibration!!.diagnostics!!.observedPGmL,both.calibration!!.diagnostics!!.observedPGmL,0.0)
    }
    @Test fun unknownOrTruncatedSourceNeverMasqueradesAsComplete() {
        val a=compute(listOf(record(1,46*60)),listOf(lab(1,0)),read=HistoryRead())
        assertNull(a.calibration);assertEquals(HistoryCoverage.UNKNOWN,a.labEligibility.single().coverage)
        assertTrue(EligibilityReason.READ_SCOPE_UNKNOWN in a.labEligibility.single().reasons)
        val future=compute(listOf(record(1,46*60)),listOf(lab(1,-300)))
        assertNull(future.calibration);assertTrue(EligibilityReason.READ_END_BEFORE_SAMPLE in future.labEligibility.single().reasons)
    }
    @Test fun noRecordOrOnlyPostSampleRecordsNeverProvePreTreatment() {
        for(records in listOf(emptyList(),listOf(record(1,46*60)))) {
            val a=compute(records,listOf(lab(1,6*3600,220.0)))
            assertNull(a.calibration);assertEquals(1,a.labs.size)
            assertEquals(BaselineEligibility.UNKNOWN,a.labEligibility.single().baseline)
            assertTrue(EligibilityReason.NO_TREATMENT_EVIDENCE in a.labEligibility.single().reasons)
        }
    }
    @Test fun unknownDoseTimeFormulationAndMixedExposureBlockOnlyRelatedLabs() {
        val base=record(1,12*3600)
        val variants=listOf(record(2,6*3600,dose=null),record(2,6*3600).copy(taken_utc=null),
            record(2,6*3600,snapshot="{}"),record(2,6*3600,snapshot="{\"molecule\":\"E2\",\"unit\":\"MG\"}").copy(origin="IMPORT_HT"),
            record(2,6*3600,snapshot=MedicationSnapshot.encode(med.copy(route="ORAL"),profile)),
            record(2,6*3600,snapshot=MedicationSnapshot.encode(med,profile.copy(ester="EV"))))
        for(r in variants) {
            val a=compute(listOf(base,r),listOf(lab(1,0)))
            assertNull(a.calibration);assertFalse(a.labEligibility.single().eligible)
            samePopulation(a,compute(listOf(base,r),listOf(lab(1,0)),on=false))
        }
        val unrelated=record(2,6*3600,snapshot="{\"molecule\":\"CPA\",\"unit\":\"MG\"}",dose=null)
        assertTrue(compute(listOf(base,unrelated),listOf(lab(1,0))).labEligibility.single().eligible)
    }
    @Test fun excludedAllLabsAndSwitchOffPreservePopulationAndObservationUnits() {
        val rows=listOf(record(1,12*3600,snapshot="{}"),record(2,46*60))
        val a=compute(rows,listOf(lab(1,6*3600,220.0)));val b=compute(rows,listOf(lab(1,6*3600,220*Pk.PMOL_PER_PG,"pmol/L")),on=false)
        samePopulation(a,b);assertEquals(a.labs.single().second,b.labs.single().second,1e-9)
        assertNull(a.calibration);assertNull(b.calibration)
        val goodRows=listOf(record(1,46*60))
        val pg=compute(goodRows,listOf(lab(1,0)));val pm=compute(goodRows,listOf(lab(1,0,400*Pk.PMOL_PER_PG,"pmol/L")))
        assertEquals(pg.currentPgMl!!,pm.currentPgMl!!,1e-9)
    }
    @Test fun fixedSlOmittedTailCanBeBoundedButLongActingAndUnknownOldExposureCannot() {
        val recent=record(2,46*60)
        val old=record(1,201*86400L)
        val a=compute(listOf(old,recent),listOf(lab(1,0)))
        assertTrue(a.labEligibility.single().eligible)
        assertTrue(a.labEligibility.single().omittedLogFractionBound!!<kotlin.math.ln(CalibrationEligibility.MAX_OMITTED_FRACTION))
        val oldUnknown=old.copy(config_snapshot="{}")
        assertFalse(compute(listOf(oldUnknown,recent),listOf(lab(1,0))).labEligibility.single().eligible)
        val oldIm=old.copy(config_snapshot=MedicationSnapshot.encode(med.copy(route="INJECTION"),ProfileEntity(1,"EV","injection")),actual_dose=5.0)
        val b=compute(listOf(oldIm,recent),listOf(lab(1,0)))
        assertFalse(b.labEligibility.single().eligible);assertTrue(EligibilityReason.OMITTED_EXPOSURE in b.labEligibility.single().reasons)
    }
    @Test fun windowBoundaryIsNotABaselineBoundaryAndNearBoundaryOmissionIsDetected() {
        val start=180*86400L;val rows=listOf(record(1,start+3600),record(2,46*60))
        for(seconds in listOf(start+1,start,start-1)) {
            val a=compute(rows,listOf(lab(1,seconds)))
            assertFalse(a.labEligibility.single().eligible);assertNull(a.calibration)
            assertEquals(BaselineEligibility.NOT_PRE_TREATMENT,a.labEligibility.single().baseline)
        }
    }
    @Test fun skippedMissedDeletedAndFutureInputsDoNotTurnIntoPastExposureEvidence() {
        val actual=record(1,46*60)
        val ignored=listOf(record(2,3600,snapshot="{}").copy(status="SKIPPED"),record(3,3600,snapshot="{}").copy(status="MISSED",origin="AUTO_MISSED"),
            record(4,3600,snapshot="{}").copy(deleted_at_utc=now.toEpochMilli()),record(5,-3600,snapshot="{}"))
        val a=compute(listOf(actual)+ignored,listOf(lab(1,0)))
        assertTrue(a.labEligibility.single().eligible);assertEquals(BaselineEligibility.NOT_PRE_TREATMENT,a.labEligibility.single().baseline)
        val noActual=compute(ignored,listOf(lab(1,0)))
        assertFalse(noActual.labEligibility.single().eligible);assertEquals(BaselineEligibility.UNKNOWN,noActual.labEligibility.single().baseline)
    }
    @Test fun actualTimeCorrectionAndFrozenRouteRemainAuthoritativeAcrossDstAndMutableProfile() {
        val r=record(1,46*60).copy(scheduled_utc=now.minusSeconds(10*3600).toEpochMilli(),scheduled_zone="America/New_York",taken_zone="Pacific/Auckland",revision=2)
        val observation=lab(1,0).copy(sampled_zone="America/New_York")
        val a=compute(listOf(r),listOf(observation))
        assertTrue(a.labEligibility.single().eligible)
        assertEquals(46/60.0,ConcentrationCalculator.hours(now)-ConcentrationCalculator.hours(r.taken_utc!!),1e-8)
        val b=ConcentrationCalculator.compute(listOf(med.copy(route="ORAL")),mapOf(1L to profile.copy(ester="EV",pk_route="oral")),listOf(r),emptyList(),listOf(observation),80.0,now,historyRead=HistoryRead(true,ConcentrationCalculator.hours(now)))
        assertEquals(a.currentPgMl!!,b.currentPgMl!!,0.0)
        val lower=compute(listOf(r.copy(actual_dose=1.0)),emptyList())
        assertEquals(compute(listOf(r),emptyList()).currentPgMl!!/2,lower.currentPgMl!!,1e-8)
    }
    @Test fun largeSavedHistoryDoesNotDoPerLabQueriesAndCancellationStopsCpuWork() {
        val rows=(1..3000L).map{record(it,it*1800)}
        val observations=(1..100L).map{lab(it,it*60)}
        val a=compute(rows,observations,on=false)
        assertEquals(3000,a.usedDoses);assertEquals(100,a.labEligibility.count{it.eligible});assertEquals(100,a.labs.size)
        var checks=0
        assertThrows(CancellationException::class.java){compute(rows,observations,cancel={if(++checks==100)throw CancellationException("Synthetic cancellation")})}
        assertEquals(100,checks)
    }
    @Test fun residualResourceBudgetReturnsReviewRatherThanSilentlyDroppingHistory() {
        val facts=(0 until 3000).map{ i-> val t=if(i<1500)-5000.0 else -1.0
            ExposureEvidence(i.toLong(),t,"E2",DoseEvent("$i",Route.SUBLINGUAL,t,2.0,Ester.E2,80.0),i>=1500) }
        val results=CalibrationEligibility.evaluate((0 until 100).map{it.toLong() to 0.0},facts,-4320.0,HistoryRead(true,0.0))
        assertTrue(results.all{EligibilityReason.RESOURCE_LIMIT in it.reasons && !it.eligible && it.fit==FitEligibility.NEEDS_REVIEW})
    }
    @Test fun exportActualP1bQualificationAndNumericalCases() {
        val cases=linkedMapOf(
            "old_treatment_lab" to (listOf(record(1,201*86400L),record(2,46*60)) to listOf(lab(1,200*86400L,500.0))),
            "unknown_context_lab" to (listOf(record(1,12*3600,snapshot="{}"),record(2,46*60)) to listOf(lab(1,6*3600,220.0))),
            "eligible_single_sl" to (listOf(record(1,46*60)) to listOf(lab(1,0))),
            "eligible_mixed" to (listOf(record(1,46*60),record(2,24*3600,MedicationSnapshot.encode(med.copy(route="ORAL"),ProfileEntity(1,"EV","oral")))) to listOf(lab(1,0))),
            "partial_eligibility" to (listOf(record(1,12*3600),record(2,6*3600,snapshot="{}"),record(3,46*60)) to listOf(lab(1,8*3600,120.0),lab(2,0))),
            "bounded_old_sl" to (listOf(record(1,201*86400L),record(2,46*60)) to listOf(lab(1,0))))
        val rows=org.json.JSONArray()
        cases.forEach{(name,input)->
            val a=compute(input.first,input.second);val off=compute(input.first,input.second,on=false)
            val decisions=org.json.JSONArray(a.labEligibility.map { row -> org.json.JSONObject()
                .put("lab_id",row.labId).put("coverage",row.coverage.name).put("frozen",row.frozen.name)
                .put("baseline_eligibility",row.baseline.name).put("fit",row.fit.name)
                .put("reasons",org.json.JSONArray(row.reasons.map{it.name})).put("evidence_record_ids",org.json.JSONArray(row.evidenceRecordIds.toList()))
                .put("omitted_log_fraction_bound",row.omittedLogFractionBound?.takeIf{it.isFinite()} ?: org.json.JSONObject.NULL) })
            rows.put(org.json.JSONObject().put("id",name).put("population_pg_ml",off.currentPgMl)
                .put("gated_pg_ml",a.currentPgMl).put("original_observations",a.labs.size)
                .put("eligible_count",a.labEligibility.count{it.eligible}).put("fit_count",a.calibration?.model?.postDoseObservationCount ?: 0)
                .put("baseline_pg_ml",a.calibration?.model?.baselinePGmL ?: org.json.JSONObject.NULL)
                .put("amplitude",a.calibration?.model?.logAmplitude?.let{kotlin.math.exp(it)} ?: org.json.JSONObject.NULL)
                .put("rate",a.calibration?.model?.logRate?.let{kotlin.math.exp(it)} ?: org.json.JSONObject.NULL)
                .put("decisions",decisions))
        }
        val facts=(0 until 10_000).map{ i->ExposureEvidence(i.toLong(),-i*.25,"E2",DoseEvent("$i",Route.SUBLINGUAL,-i*.25,2.0,Ester.E2,80.0),true) }
        val samples=(0 until 1000).map{it.toLong() to -it*.01}
        val start=System.nanoTime();val outcomes=CalibrationEligibility.evaluate(samples,facts,-4320.0,HistoryRead(true,0.0));val seconds=(System.nanoTime()-start)/1e9
        assertEquals(1000,outcomes.count{it.eligible})
        val out=org.json.JSONObject().put("synthetic_only",true).put("calculator_version",2).put("labfit_algorithm_version",2)
            .put("cases",rows).put("resource_test",org.json.JSONObject().put("facts",10000).put("labs",1000).put("seconds",seconds).put("eligible",1000))
        val file=java.io.File("build/reports/pk-p1b/eligibility-audit.json");file.parentFile!!.mkdirs();file.writeText(out.toString(2)+"\n")
    }

}
