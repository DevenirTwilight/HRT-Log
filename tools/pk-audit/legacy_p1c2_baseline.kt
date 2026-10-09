package net.plainnotes.app
import net.plainnotes.app.conc.*
import net.plainnotes.app.data.*
import net.plainnotes.app.pk.*
import org.json.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.time.Instant
/** Run ONLY on starting 95199ab tree, synthetic inputs, old actual code. */
class P1c2BeforeTest {
 @Test fun reproduceActualAllOutlierBookkeeping(){
  val e=DoseEvent("dose",Route.SUBLINGUAL,0.0,2.0,Ester.E2,80.0,DoseExtras(sublingualTier=2.0))
  val l=LabResult("tail",100.0,200.0,LabUnit.PG_ML);val fit=LabFit.fit(listOf(e),listOf(l))
  assertEquals(setOf("tail"),fit.excludedLabIds);assertEquals(1,fit.postDoseObservationCount)
  val now=Instant.parse("2026-10-25T01:10:00Z")
  val med=MedicationEntity(1,"Synthetic E2","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=true,active=true,sort_order=0)
  val p=ProfileEntity(1,"E2","sublingual",sl_tier=2)
  val r=RecordEntity(1,1,taken_utc=now.minusSeconds(101*3600).toEpochMilli(),taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=MedicationSnapshot.encode(med,p))
  val lab=LabValueEntity(1,"E2",200.0,"pg/mL",now.minusSeconds(3600).toEpochMilli(),"UTC")
  val result=ConcentrationCalculator.compute(listOf(med),mapOf(1L to p),listOf(r),emptyList(),listOf(lab),80.0,now,true,CalibrationMode.CAUSAL,historyRead=HistoryRead(true,ConcentrationCalculator.hours(now)))
  assertTrue(result.labEligibility.single().eligible);val model=result.calibration!!.model
  assertEquals(setOf("l1"),model.excludedLabIds);assertEquals(1,model.postDoseObservationCount)
  fun json(m:LabFitModel)=JSONObject().put("actual_post_count",m.postDoseObservationCount).put("claimed_excluded",JSONArray(m.excludedLabIds.sorted())).put("log_amplitude",m.logAmplitude).put("log_rate",m.logRate).put("covariance",JSONArray(m.cov.toList()))
  val out=JSONObject().put("source_commit","95199ab19867a72e93a3ba5a6fae2c7320075577").put("synthetic_only",true).put("engine",json(fit)).put("application",json(model).put("eligible",true).put("candidate_count",result.calibration!!.labCount).put("raw_count",result.labs.size).put("sample_age_h",1).put("dose_age_h",101)).put("input",JSONObject().put("dose_mg",2).put("route","sublingual").put("tier",2).put("weight_kg",80).put("sample_after_dose_h",100).put("sample_pg_ml",200))
  File(System.getenv("P1C2_BEFORE_OUTPUT")!!).writeText(out.toString(2)+"\n")
 }
}
