package net.plainnotes.app
import net.plainnotes.app.conc.*
import net.plainnotes.app.data.*
import net.plainnotes.app.pk.*
import org.junit.Test
import org.junit.Assert.*
import org.json.*
import java.io.File
import java.time.Instant
object DispositionFixture {
 val now=Instant.parse("2026-10-25T01:10:00Z")
 val med=MedicationEntity(1,"Synthetic E2","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=true,active=true,sort_order=0)
 val p=ProfileEntity(1,"E2","sublingual",sl_tier=2)
 val record=RecordEntity(1,1,taken_utc=now.minusSeconds(101*3600).toEpochMilli(),taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=MedicationSnapshot.encode(med,p))
 fun lab(id:Long,t:Double,v:Double)=LabValueEntity(id,"E2",v,"pg/mL",record.taken_utc!!+(t*3600000).toLong(),"UTC")
 fun pop(t:Double)=Engine.simulate(listOf(DoseEvent("r1",Route.SUBLINGUAL,0.0,2.0,Ester.E2,80.0,DoseExtras(sublingualTier=2.0))),grid=doubleArrayOf(t))!!.curves.getValue(Curve.E2)[0]
 val normals get()=listOf(.5,1.0,2.0).mapIndexed{i,t->lab(i+1L,t,pop(t))}
 val single get()=listOf(lab(4,100.0,200.0))
 val partial get()=normals+lab(4,3.0,pop(3.0)*30)
 fun run(ls:List<LabValueEntity>,mode:CalibrationMode=CalibrationMode.CAUSAL,at:Instant=now,records:List<RecordEntity> = listOf(record),calibrate:Boolean=true)=ConcentrationCalculator.compute(listOf(med),mapOf(1L to p),records,emptyList(),ls,80.0,now,calibrate,mode,historyRead=HistoryRead(true,ConcentrationCalculator.hours(now)),evaluationTime=at)
}
class OutlierDispositionTest {
 private fun values(r:ConcentrationResult)=r.currentEvaluation!!.let{doubleArrayOf(it.center,it.p5,it.p25,it.p75,it.p95)}
 @Test fun actualAllWarningFallbackHasOneUsedNoExcludedAndRawPoint(){val r=DispositionFixture.run(DispositionFixture.single);assertTrue(r.labEligibility.single().eligible);val m=r.calibration!!.model;assertEquals(setOf("l4"),m.usedLabIds);assertEquals(setOf("l4"),m.warningLabIds);assertTrue(m.excludedLabIds.isEmpty());assertEquals(1,m.postDoseObservationCount);assertEquals(1,r.labs.size);assertTrue(r.calibration!!.diagnostics!!.usedInFit)}
 @Test fun actualPartialFitsSubsetAndCountsAreDifferentFromQualified(){val r=DispositionFixture.run(DispositionFixture.partial);val subset=DispositionFixture.run(DispositionFixture.normals);assertEquals(4,r.calibration!!.labCount);assertEquals(3,r.calibration!!.model.postDoseObservationCount);assertEquals(setOf("l4"),r.calibration!!.model.excludedLabIds);assertArrayEquals(values(subset),values(r),1e-7);assertArrayEquals(subset.calibration!!.model.cov,r.calibration!!.model.cov,1e-12);assertEquals(4,r.labs.size)}
 @Test fun futureWarningStatesAndBackwardQueriesCannotLeak(){val before=DispositionFixture.now.minusSeconds(7200);val a=DispositionFixture.run(emptyList(),at=before);val b=DispositionFixture.run(DispositionFixture.single,at=before);assertArrayEquals(values(a),values(b),1e-7);assertNull(b.calibration);assertTrue(b.currentEvaluation!!.fitDisposition!!.warningLabIds.isEmpty());b.evaluateAt(ConcentrationCalculator.hours(DispositionFixture.now));assertTrue(b.evaluateAt(ConcentrationCalculator.hours(before))!!.fitDisposition!!.usedLabIds.isEmpty());val retro=DispositionFixture.run(DispositionFixture.single,CalibrationMode.RETROSPECTIVE,before);assertEquals(1,retro.calibration!!.model.postDoseObservationCount)}
 @Test fun historicalIneligibilityIsIndependentOfResidualDisposition(){val old=DispositionFixture.record.copy(id=2,taken_utc=DispositionFixture.now.minusSeconds(201*86400L).toEpochMilli());val oldLab=DispositionFixture.lab(8,100.0,500.0).copy(sampled_utc=DispositionFixture.now.minusSeconds(200*86400L).toEpochMilli());val r=DispositionFixture.run(DispositionFixture.partial+oldLab,records=listOf(old,DispositionFixture.record));assertFalse(r.labEligibility.last().eligible);assertEquals(5,r.labs.size);assertEquals(setOf("l1","l2","l3"),r.calibration!!.model.usedLabIds);assertEquals(setOf("l4"),r.calibration!!.model.excludedLabIds);assertNull(r.calibration!!.model.baselinePGmL)
  val unknown=DispositionFixture.record.copy(config_snapshot="{}");val rejected=DispositionFixture.run(DispositionFixture.single,records=listOf(unknown));assertNull(rejected.calibration);assertFalse(rejected.labEligibility.single().eligible);assertEquals(1,rejected.labs.size)}
 @Test fun invalidConcentrationDoesNotInventPersonalFitOrCounts(){for(v in listOf(0.0,-1.0,Double.NaN,Double.POSITIVE_INFINITY)){val r=DispositionFixture.run(listOf(DispositionFixture.lab(1,100.0,v)));assertNull(r.calibration);assertTrue(r.currentEvaluation!!.fitDisposition!!.usedLabIds.isEmpty());assertEquals(LabNotFittedReason.INVALID_CONCENTRATION,r.currentEvaluation!!.fitDisposition!!.ignoredLabReasons["l1"]);assertTrue(r.currentPgMl!!.isFinite())}}
 @Test fun actualAppOutputs(){val rows=JSONArray();for((id,ls)in listOf("single_warning" to DispositionFixture.single,"partial" to DispositionFixture.partial,"normal" to DispositionFixture.normals,"empty" to emptyList())){val r=DispositionFixture.run(ls);val m=r.currentEvaluation!!.fitDisposition!!;rows.put(JSONObject().put("id",id).put("raw_count",r.labs.size).put("eligible_count",r.labEligibility.count{it.eligible}).put("available_count",r.calibration?.labCount ?: 0).put("actual_count",m.postDoseObservationCount).put("used",JSONArray(m.usedLabIds.sorted())).put("excluded",JSONArray(m.excludedLabIds.sorted())).put("warnings",JSONArray(m.warningLabIds.sorted())).put("amplitude_log",m.logAmplitude).put("covariance",JSONArray(m.cov.toList())).put("values",JSONArray(values(r).toList())))}
 val f=File("build/reports/pk-p1c2/disposition-app.json");f.parentFile.mkdirs();f.writeText(JSONObject().put("synthetic_only",true).put("cases",rows).toString(2)+"\n")}
}
