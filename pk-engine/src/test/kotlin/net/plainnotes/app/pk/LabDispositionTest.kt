package net.plainnotes.app.pk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.json.*
import java.io.File
import java.util.Random
import java.util.concurrent.CancellationException
import kotlin.math.*
class LabDispositionTest {
 private fun event(id:String="dose",t:Double=0.0,d:Double=2.0)=DoseEvent(id,Route.SUBLINGUAL,t,d,Ester.E2,80.0,DoseExtras(sublingualTier=2.0))
 private val es=listOf(event())
 private val grid=doubleArrayOf(.25,.5,.75,46.0/60,1.0,1.5,2.0,3.0,4.0,6.0,8.0,12.0,24.0,100.0,101.0)
 private fun pop(t:Double,events:List<DoseEvent> = es)=Engine.simulate(events,grid=doubleArrayOf(t))!!.curves.getValue(Curve.E2)[0]
 private fun l(id:String,t:Double,v:Double)=LabResult(id,t,v,LabUnit.PG_ML)
 private val normal get()=listOf(l("a",.5,pop(.5)),l("b",1.0,pop(1.0)),l("c",2.0,pop(2.0)))
 private val partialTail get()=l("tail",3.0,pop(3.0)*30)
 private val tail get()=l("tail",100.0,200.0)
 private fun invariant(m:LabFitModel){assertEquals(m.candidateLabIds,m.usedLabIds+m.excludedLabIds);assertTrue(m.usedLabIds.intersect(m.excludedLabIds).isEmpty());assertEquals(m.usedLabIds.size,m.postDoseObservationCount);assertTrue(m.candidateLabIds.containsAll(m.warningLabIds));assertTrue(m.cov.all{it.isFinite()});assertTrue(m.logAmplitude.isFinite());assertTrue(m.logRate.isFinite())}
 private fun equalFit(a:LabFitModel,b:LabFitModel){assertEquals(a.logAmplitude,b.logAmplitude,1e-12);assertEquals(a.logRate,b.logRate,1e-12);assertArrayEquals(a.cov,b.cov,1e-12)}
 @Test fun singleAndAllWarningsAreKeptNeverExcluded(){for(ls in listOf(listOf(tail),listOf(tail,l("second",100.0,1e-20)))){val m=LabFit.fit(es,ls);invariant(m);assertEquals(ls.map{it.id}.toSet(),m.warningLabIds);assertEquals(m.candidateLabIds,m.usedLabIds);assertTrue(m.excludedLabIds.isEmpty());assertEquals(0.0,m.logRate,0.0);assertEquals(0.0,m.cov[3],0.0)}}
 @Test fun partialRemovalMatchesActualSubsetMapCovarianceAndEveryQuantile(){
  val ls=normal+partialTail;val m=LabFit.fit(es,ls);invariant(m);assertEquals(setOf("tail"),m.excludedLabIds);assertEquals(setOf("a","b","c"),m.usedLabIds);equalFit(LabFit.fit(es,normal),m)
  for(n in listOf(1,2,40,200)){val all=LabFit.bands(es,grid,ls,CalibrationMode.RETROSPECTIVE,n).getValue(Curve.E2);val subset=LabFit.bands(es,grid,normal,CalibrationMode.RETROSPECTIVE,n).getValue(Curve.E2);for((a,b)in listOf(all.center to subset.center,all.p5 to subset.p5,all.p25 to subset.p25,all.p75 to subset.p75,all.p95 to subset.p95))assertArrayEquals(a,b,1e-12)}
 }
 @Test fun noWarningAndNoCandidatesPreservePopulationPrior(){val m=LabFit.fit(es,normal);invariant(m);assertTrue(m.warningLabIds.isEmpty());assertTrue(m.excludedLabIds.isEmpty());val empty=LabFit.fit(es,emptyList());invariant(empty);assertEquals(0.0,empty.logAmplitude,0.0);assertTrue(empty.usedLabIds.isEmpty())}
 @Test fun independentPureSlLogAmplitudeAndFixedSeedOracle(){
  val ls=normal.map{it.copy(concValue=it.concValue*1.5)};val m=LabFit.fit(es,ls)
  val noise=LabFit.SIGMA_LAB.pow(2);val prior=m.priorSdAmplitude.pow(2);val u=3*ln(1.5)/noise/(3/noise+1/prior);val variance=1/(3/noise+1/prior)
  assertEquals(u,m.logAmplitude,1e-7);assertEquals(variance,m.cov[0],1e-10)
  val t=.8;val v=LabFit.evaluateAt(es,grid,ls,t,CalibrationMode.RETROSPECTIVE,200.0)!!;val points=LabFit.queryBracket(grid,t);val center=Pk.interpolate(points,Engine.simulate(es,grid=points)!!.curves.getValue(Curve.E2),t)!!*exp(u)
  assertEquals(center,v.center,1e-5);val r=Random(20261006L);val draws=DoubleArray(200){val z=DoubleArray(4){r.nextGaussian()};center*exp(sqrt(variance)*z[0])}.sorted();assertEquals(draws[9],v.p5,1e-5);assertEquals(draws[189],v.p95,1e-5)
 }
 @Test fun futureStateCannotChangePastAndSamplingEqualityWorks(){
  val past=normal.take(1);val future=l("future",1.5,1e8);fun at(t:Double,ls:List<LabResult>)=LabFit.evaluateAt(es,grid,ls,t,CalibrationMode.CAUSAL,200.0)!!
  val a=at(.8,past);for(ls in listOf(past+future,past+future.copy(concValue=1.0),past)){val b=at(.8,ls);assertEquals(a.center,b.center,0.0);assertEquals(a.p95,b.p95,0.0);assertEquals(a.model.warningLabIds,b.model.warningLabIds);assertEquals(a.model.usedLabIds,b.model.usedLabIds);assertEquals(a.diagnostics,b.diagnostics)}
  assertFalse("future" in at(1.5-1e-8,past+future).model.candidateLabIds);assertTrue("future" in at(1.5,past+future).model.candidateLabIds)
 }
 @Test fun diagnosticLeavesOutSelfAndAllSameTimeAndReportsActualDisposition(){
  val ls=normal+partialTail+partialTail.copy(id="z",concValue=partialTail.concValue*1.25);val m=LabFit.fit(es,ls);val d=LabFit.lastDiagnostics(es,ls,currentFit=m)!!
  assertEquals("z",d.labId);assertTrue(d.isOutlier);assertTrue(d.excludedFromFit);assertFalse(d.usedInFit);assertEquals(LabFit.lastDiagnostics(es,normal+partialTail)!!.predictedPGmL,d.predictedPGmL,0.0)
  val single=LabFit.lastDiagnostics(es,listOf(tail),currentFit=LabFit.fit(es,listOf(tail)))!!;assertTrue(single.isOutlier);assertTrue(single.usedInFit);assertFalse(single.excludedFromFit)
  for(seed in 1..5){val shuffled=ls.shuffled(kotlin.random.Random(seed));val f=LabFit.fit(es,shuffled);equalFit(m,f);assertEquals(m.usedLabIds,f.usedLabIds);assertEquals(d,LabFit.lastDiagnostics(es,shuffled,currentFit=f))}
 }
 @Test fun invalidValuesBaselineAndDuplicateIdsHaveSeparateReasons(){
  val invalid=listOf(0.0,-1.0,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY).mapIndexed{i,v->l("bad$i",1.0,v)}
  val ls=invalid+l("before",-1.0,20.0)+l("badTime",Double.NaN,200.0)+l("dupe",1.0,200.0)+l("dupe",2.0,300.0)
  val m=LabFit.fit(es,ls);invariant(m);assertTrue(m.candidateLabIds.isEmpty());assertNull(m.baselinePGmL);assertEquals(LabNotFittedReason.INVALID_CONCENTRATION,m.ignoredLabReasons["bad0"]);assertEquals(LabNotFittedReason.INVALID_TIME,m.ignoredLabReasons["badTime"]);assertEquals(LabNotFittedReason.DUPLICATE_ID,m.ignoredLabReasons["dupe"])
  val confirmed=LabFit.fit(es,ls,confirmedBaselineLabIds=setOf("before"));assertEquals(setOf("before"),confirmed.baselineLabIds);assertEquals(20.0,confirmed.baselinePGmL!!,0.0)
  val pg=LabFit.fit(es,normal);val pmol=LabFit.fit(es,normal.map{it.copy(concValue=it.concValue*Pk.PMOL_PER_PG,unit=LabUnit.PMOL_L)});equalFit(pg,pmol)
 }
 @Test fun residualThresholdHasStrictEqualityAndFiniteLogFloor(){val p=1.0-1e-9;assertFalse(LabFit.residualWarning(4.0,p));assertTrue(LabFit.residualWarning(4.0*(1+1e-12),p));assertFalse(LabFit.residualWarning(4.0*(1-1e-12),p));assertTrue(LabFit.residualWarning(200.0,0.0));assertTrue(LabFit.residualWarning(1e300,1.0));assertThrows(IllegalArgumentException::class.java){LabFit.residualWarning(Double.NaN,p)}}
 @Test fun dosesRepeatedHistorySamplesAndCancellationStayStable(){
  for(d in listOf(1.0,2.0,4.0))for(interval in listOf(6.0,12.0,24.0)){
   val events=(0 until (30*24/interval).toInt()).map{event("d$it",-it*interval,d)};val ls=listOf(l("ok",1.0,pop(1.0,events)),l("tail",100.0,200.0));val m=LabFit.fit(events,ls);invariant(m);assertEquals(0.0,m.logRate,0.0)
   val b=LabFit.bands(events,grid,ls,CalibrationMode.CAUSAL,10).getValue(Curve.E2);for(i in grid.indices){assertTrue(b.center[i].isFinite()&&b.center[i]>=0);assertTrue(b.p5[i]>=0&&b.p5[i]<=b.p25[i]&&b.p25[i]<=b.p75[i]&&b.p75[i]<=b.p95[i]&&b.p95[i].isFinite())}}
  var count=0;assertThrows(CancellationException::class.java){LabFit.bands(es,grid,normal,CalibrationMode.CAUSAL,200,checkCancelled={if(++count>10)throw CancellationException()})}
  invariant(LabFit.fit(es,listOf(l("large",1.0,1e300))))
 }
 @Test fun actualOutputsAndImmutablePopulationReport(){
  val cases=linkedMapOf("single_warning" to listOf(tail),"all_warning" to listOf(tail,l("second",100.0,1e-20)),"partial" to (normal+partialTail),"normal" to normal,"empty" to emptyList())
  val rows=JSONArray();for((id,ls)in cases){val m=LabFit.fit(es,ls);invariant(m);val b=LabFit.bands(es,grid,ls,CalibrationMode.RETROSPECTIVE,40).getValue(Curve.E2)
   rows.put(JSONObject().put("id",id).put("input",JSONArray(ls.map{JSONObject().put("id",it.id).put("time_h",it.timeH).put("pg_ml",it.concValue)})).put("candidates",JSONArray(m.candidateLabIds.sorted())).put("used",JSONArray(m.usedLabIds.sorted())).put("excluded",JSONArray(m.excludedLabIds.sorted())).put("warnings",JSONArray(m.warningLabIds.sorted())).put("count",m.postDoseObservationCount).put("amplitude_log",m.logAmplitude).put("rate_log",m.logRate).put("covariance",JSONArray(m.cov.toList())).put("center",JSONArray(b.center.toList())).put("p5",JSONArray(b.p5.toList())).put("p25",JSONArray(b.p25.toList())).put("p75",JSONArray(b.p75.toList())).put("p95",JSONArray(b.p95.toList())))}
  val file=File("build/reports/pk-p1c2/disposition-engine.json");file.parentFile.mkdirs();file.writeText(JSONObject().put("synthetic_only",true).put("time_h",JSONArray(grid.toList())).put("cases",rows).toString(2)+"\n")
 }
}
