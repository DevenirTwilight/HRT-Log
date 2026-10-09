package net.plainnotes.app
import net.plainnotes.app.conc.*
import net.plainnotes.app.data.*
import net.plainnotes.app.pk.*
import net.plainnotes.app.ui.ChartViewport
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.io.File
import org.json.*
import kotlin.math.abs

/** Production calculator, actual P1-B gate, query API and display geometry. Synthetic data only. */
class CausalTimeBoundaryTest {
 private val now=Instant.parse("2026-10-25T01:10:00Z")
 private val t0=now.minusSeconds(30*60)
 private val m=MedicationEntity(1,"Synthetic E2","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=true,active=true,sort_order=0)
 private val p=ProfileEntity(1,"E2","sublingual",sl_tier=2)
 private fun record(id:Long,at:Instant=t0.minusSeconds(46*60),json:String=MedicationSnapshot.encode(m,p))=RecordEntity(id,1,taken_utc=at.toEpochMilli(),taken_zone="Europe/Paris",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=json)
 private val records=listOf(record(1))
 private fun lab(id:Long=1,at:Instant=t0.plusSeconds(5*60),v:Double=400.0,unit:String="pg/mL")=LabValueEntity(id,"E2",v,unit,at.toEpochMilli(),"Europe/Paris")
 private fun compute(labs:List<LabValueEntity>,on:Boolean=true,mode:CalibrationMode=CalibrationMode.CAUSAL,rows:List<RecordEntity> = records,query:Instant=now)=ConcentrationCalculator.compute(listOf(m),mapOf(1L to p),rows,emptyList(),labs,80.0,now,on,mode,historyRead=HistoryRead(true,ConcentrationCalculator.hours(now)),evaluationTime=query)
 private fun at(r:ConcentrationResult,t:Instant=t0)=r.evaluateAt(ConcentrationCalculator.hours(t))!!
 private fun values(v:ConcentrationEvaluation)=doubleArrayOf(v.center,v.p5,v.p25,v.p75,v.p95)
 private fun same(a:ConcentrationEvaluation,b:ConcentrationEvaluation){
  assertArrayEquals(values(a),values(b),1e-7)
  assertEquals(a.calibration?.labCount,b.calibration?.labCount)
  assertEquals(a.calibration?.model?.excludedLabIds,b.calibration?.model?.excludedLabIds)
  assertEquals(a.calibration?.model?.postDoseObservationCount,b.calibration?.model?.postDoseObservationCount)
  assertEquals(a.calibration?.diagnostics,b.calibration?.diagnostics)
  if(a.calibration==null)assertNull(b.calibration) else {
   assertEquals(a.calibration!!.model.logAmplitude,b.calibration!!.model.logAmplitude,0.0)
   assertEquals(a.calibration!!.model.logRate,b.calibration!!.model.logRate,0.0)
   assertArrayEquals(a.calibration!!.model.cov,b.calibration!!.model.cov,0.0)
  }
 }
 @Test fun laterQualifiedLabCannotChangeHistoricalReadingIntervalsOrSummary() {
  val a=at(compute(emptyList()))
  for(ls in listOf(listOf(lab()),listOf(lab(v=800.0)),listOf(lab(),lab(2,v=350.0)),emptyList())) {
   val r=compute(ls);assertTrue(r.labEligibility.all{it.eligible});assertEquals(ls.size,r.labs.size)
   same(a,at(r));assertNull(at(r).calibration)
  }
 }
 @Test fun productionHistoricalQueryCurrentReadoutUsesSamePrefix() {
  val a=compute(emptyList(),query=t0);val b=compute(listOf(lab()),query=t0)
  assertTrue(b.labEligibility.single().eligible);assertEquals(a.currentPgMl!!,b.currentPgMl!!,1e-7)
  assertEquals(b.currentPgMl!!,at(b).center,0.0);assertNull(b.calibration)
  assertEquals(ConcentrationCalculator.hours(now),b.nowH,0.0)
 }
 @Test fun boundaryMinusEqualPlusMillisecondAndPositiveAfterSampling() {
  val sample=t0.plusSeconds(300);val r=compute(listOf(lab()))
  val before=at(r,sample.minusMillis(1));val exact=at(r,sample);val after=at(r,sample.plusMillis(1))
  assertNull(before.calibration);assertEquals(1,exact.calibration!!.labCount);assertEquals(1,after.calibration!!.labCount)
  assertTrue(abs(exact.center-at(compute(emptyList()),sample).center)>1)
  assertEquals(0.0,exact.calibration!!.model.logRate,0.0)
  assertTrue(abs(after.center-exact.center)<.1)
 }
 @Test fun earlierQualifiedAndRejectedAndLaterQualifiedRemainSeparate() {
  val earlier=lab(1,t0.minusSeconds(300),250.0);val rejected=lab(2,now.minusSeconds(200*86400L),500.0);val later=lab(3)
  val a=compute(listOf(earlier));val b=compute(listOf(later,rejected,earlier))
  assertEquals(3,b.labs.size);assertEquals(2,b.labEligibility.count{it.eligible});assertFalse(b.labEligibility.first{it.labId==2L}.eligible)
  same(at(a),at(b));assertEquals(1,at(b).calibration!!.labCount);assertEquals(250.0,at(b).calibration!!.diagnostics!!.observedPGmL,0.0)
 }
 @Test fun sameTimeOrderAndRepeatedBackwardsQueriesDoNotLeakState() {
  val ls=listOf(lab(1),lab(2,v=350.0),lab(3,t0.plusSeconds(600),300.0));val a=compute(ls);val b=compute(ls.reversed())
  for(t in listOf(now,t0,t0.plusSeconds(301),t0.minusMillis(1),now,t0))same(at(a,t),at(b,t))
  assertEquals(350.0,at(a,t0.plusSeconds(301)).calibration!!.diagnostics!!.observedPGmL,0.0)
 }
 @Test fun retrospectiveStillUsesLaterQualifiedObservationsWithoutBypassingGate() {
  val a=at(compute(emptyList(),mode=CalibrationMode.RETROSPECTIVE));val r=compute(listOf(lab(),lab(2,now.minusSeconds(200*86400L))),mode=CalibrationMode.RETROSPECTIVE)
  assertTrue(abs(a.center-at(r).center)>1);assertEquals(1,at(r).calibration!!.labCount);assertEquals(2,r.labs.size);assertTrue(r.calibrationBreaks.isEmpty())
 }
 @Test fun futureWallNowLabNeverCalibratesForecastAndUnitsAgree() {
  val a=compute(listOf(lab()));val b=compute(listOf(lab(),lab(2,now.plusSeconds(300),800.0)))
  assertFalse(b.labEligibility.last().eligible);same(at(a,now.plusSeconds(86400)),at(b,now.plusSeconds(86400)))
  val units=compute(listOf(lab(v=400*Pk.PMOL_PER_PG,unit="pmol/L")))
  same(at(a,now),at(units,now));assertEquals(400.0,units.labs.single().second,1e-9)
 }
 @Test fun switchOffPopulationAndFrozenInputAndMissingContextCompatibility() {
  val a=compute(emptyList(),on=false);val b=compute(listOf(lab(v=800.0)),on=false)
  same(at(a),at(b));assertNull(b.calibration);assertTrue(b.calibrationBreaks.isEmpty());assertEquals(2,ConcentrationCalculator.VERSION)
  val changed=records.map{it.copy(scheduled_utc=now.minusSeconds(80000).toEpochMilli(),taken_zone="Pacific/Auckland")}
  same(at(compute(listOf(lab()))),at(compute(listOf(lab().copy(sampled_zone="America/New_York")),rows=changed)))
  val missing=compute(listOf(lab()),rows=records+record(2,t0.minusSeconds(7200),"{}"));assertFalse(missing.labEligibility.single().eligible);assertNull(missing.calibration);assertEquals(1,missing.labs.size)
 }
 @Test fun plotPathsAndViewportCutsNeverBridgeFitBoundaries() {
  val r=compute(listOf(lab(),lab(2,t0.plusSeconds(301),350.0)))
  for(range in ChartViewport.segments(r.timeH,r.calibrationBreaks))for(cut in r.calibrationBreaks)assertFalse(r.timeH[range.first]<cut &&r.timeH[range.last]>=cut)
  val segments=ChartViewport.segmentSamples(r.timeH,r.e2,ConcentrationCalculator.hours(t0.minusSeconds(300)),ConcentrationCalculator.hours(now),ConcentrationCalculator.hours(t0),r.calibrationBreaks)
  for(s in segments)for(cut in r.calibrationBreaks)assertFalse(s.any{it.first<cut}&&s.any{it.first>=cut})
  assertTrue(segments.any{it.isNotEmpty()});same(at(compute(emptyList())),at(r))
 }
 @Test fun exactGridEndpointsOutsideRangeAndEmptyRecords() {
  val r=compute(listOf(lab()))
  for(i in listOf(0,100,r.timeH.lastIndex)){val v=r.evaluateAt(r.timeH[i])!!;assertEquals(r.e2[i],v.center,1e-7);assertEquals(r.bandOuter!!.first[i],v.p5,1e-7)}
  assertEquals(0.0,r.evaluateAt(r.timeH.first()-1)!!.center,0.0);assertTrue(r.evaluateAt(r.timeH.last()+1)!!.center.isFinite())
  val empty=compute(emptyList(),rows=emptyList());assertNull(empty.currentPgMl);assertNull(empty.evaluateAt(ConcentrationCalculator.hours(t0)))
 }
 @Test fun cancellationAndLongRepeatedActualHistoryAreFinite() {
  val r=compute(listOf(lab()),rows=(0 until 120).map{record(it.toLong()+1,t0.minusSeconds(it*6*3600L+46*60))})
  val v=at(r,now);assertTrue(values(v).all{it.isFinite()&&it>=0});assertTrue(v.p5<=v.p25 &&v.p25<=v.p75&&v.p75<=v.p95)
  assertThrows(java.util.concurrent.CancellationException::class.java){r.evaluateAt(ConcentrationCalculator.hours(now)){throw java.util.concurrent.CancellationException("synthetic cancel")}}
 }
 @Test fun futureLabsCannotConsumeEarlierEligibilityBudget() {
  val facts=(0 until 3000).map{ i -> val t=if(i<1500)-5000.0 else -1.0
   ExposureEvidence(i.toLong(),t,"E2",DoseEvent("$i",Route.SUBLINGUAL,t,2.0,Ester.E2,80.0),i>=1500) }
  val one=CalibrationEligibility.evaluate(listOf(0L to 0.0),facts,-4320.0,HistoryRead(true,100.0)).single()
  val many=CalibrationEligibility.evaluate(listOf(0L to 0.0)+(1..100).map{it.toLong() to it.toDouble()},facts,-4320.0,HistoryRead(true,100.0))
  assertTrue(one.eligible);assertEquals(one,many.first());assertTrue(many.any{EligibilityReason.RESOURCE_LIMIT in it.reasons})
 }
 @Test fun exportActualBeforeAfterInputsAndOutputs() {
  val rows=JSONArray();val start=System.nanoTime()
  for((id,ls) in listOf("none" to emptyList(),"future400" to listOf(lab()),"future800" to listOf(lab(v=800.0)),"same_time_two" to listOf(lab(),lab(2,v=350.0)))){
   val r=compute(ls);val a=at(r);val post=at(r,t0.plusSeconds(301))
   rows.put(JSONObject().put("id",id).put("values_center_p5_p25_p75_p95",JSONArray(values(a).toList())).put("qualified_count",r.labEligibility.count{it.eligible}).put("raw_count",r.labs.size).put("summary_count",a.calibration?.labCount ?:0).put("post_count",a.calibration?.model?.postDoseObservationCount ?:0).put("diagnostic_observed",a.calibration?.diagnostics?.observedPGmL ?:JSONObject.NULL).put("after_sample_values",JSONArray(values(post).toList())).put("after_sample_count",post.calibration?.labCount ?:0))
  }
  val file=File("build/reports/pk-p1c2/causal-after.json");file.parentFile!!.mkdirs();file.writeText(JSONObject().put("synthetic_only",true).put("now",now.toString()).put("query",t0.toString()).put("dose",t0.minusSeconds(46*60).toString()).put("sample",t0.plusSeconds(300).toString()).put("cases",rows).put("seconds",(System.nanoTime()-start)/1e9).toString(2)+"\n")
 }
}
