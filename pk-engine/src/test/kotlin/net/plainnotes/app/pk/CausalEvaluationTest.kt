package net.plainnotes.app.pk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.CancellationException
import java.util.Random
import kotlin.math.*
class CausalEvaluationTest {
 private val events=listOf(DoseEvent("r1",Route.SUBLINGUAL,0.0,2.0,Ester.E2,80.0,DoseExtras(sublingualTier=2.0)))
 private val grid=doubleArrayOf(0.0,.5,1.0,1.5,2.0,4.0)
 private val lab=LabResult("l1",.85,400.0,LabUnit.PG_ML)
 private fun at(t:Double,ls:List<LabResult>,samples:Int=200,es:List<DoseEvent> = events)=LabFit.evaluateAt(es,grid,ls,t,CalibrationMode.CAUSAL,2.0,samples)!!
 private fun vals(v:LabEvaluation)=doubleArrayOf(v.center,v.p5,v.p25,v.p75,v.p95)
 @Test fun allDrawCountsRespectFutureNoninterferenceAndInclusiveBoundary(){
  for(n in listOf(1,2,40,200)){
   val a=at(.8,emptyList(),n);val b=at(.8,listOf(lab),n);assertArrayEquals(vals(a),vals(b),0.0);assertEquals(0,b.labCount)
   val c=at(.85,listOf(lab),n);assertEquals(1,c.labCount);assertTrue(abs(c.center-at(.85,emptyList(),n).center)>1)
   assertArrayEquals(vals(c),vals(at(.85,listOf(lab),n)),0.0);assertTrue(c.p5<=c.p25&&c.p25<=c.p75&&c.p75<=c.p95)
  }
 }
 @Test fun independentFixedSlAmplitudeAndRandomQuantileOracle(){
  val t=.95;val v=at(t,listOf(lab));val model=LabFit.fit(events,listOf(lab));val pop=Engine.simulate(events,grid=grid)!!.curves.getValue(Curve.E2)
  val c=Pk.interpolate(grid,pop,t)!!;assertEquals(c*exp(model.logAmplitude),v.center,1e-8)
  val rng=Random(20261006L);val draws=DoubleArray(200){val z=DoubleArray(4){rng.nextGaussian()};c*exp(model.logAmplitude+sqrt(model.cov[0])*z[0])}.sorted()
  assertEquals(draws[9],v.p5,1e-8);assertEquals(draws[49],v.p25,1e-8);assertEquals(draws[149],v.p75,1e-8);assertEquals(draws[189],v.p95,1e-8)
 }
 @Test fun exactGridValuesAllBandsAgreeWithSegmentCurves(){
  val b=LabFit.bands(events,grid,listOf(lab),CalibrationMode.CAUSAL).getValue(Curve.E2)
  for(i in grid.indices){val v=at(grid[i],listOf(lab));assertEquals(b.center[i],v.center,1e-8);assertEquals(b.p5[i],v.p5,1e-8);assertEquals(b.p25[i],v.p25,1e-8);assertEquals(b.p75[i],v.p75,1e-8);assertEquals(b.p95[i],v.p95,1e-8)}
 }
 @Test fun deterministicSameTimeAndDiagnosticCannotUseSameTimeOrFuture(){
  val ls=listOf(lab.copy(id="b",concValue=350.0),lab.copy(id="a"),lab.copy(id="c",timeH=1.9))
  assertArrayEquals(vals(at(1.0,ls)),vals(at(1.0,ls.reversed())),0.0)
  val v=at(1.0,ls);assertEquals(2,v.labCount);assertEquals(350.0,v.diagnostics!!.observedPGmL,0.0)
  val direct=Engine.simulate(events,grid=doubleArrayOf(.85))!!.curves.getValue(Curve.E2)[0]
  assertEquals(direct,v.diagnostics!!.predictedPGmL,1e-8)
  val earlier=lab.copy(id="earlier",timeH=.85-1.0/3600000)
  val precise=at(1.0,listOf(earlier,lab))
  assertEquals(direct*exp(LabFit.fit(events,listOf(earlier)).logAmplitude),precise.diagnostics!!.predictedPGmL,1e-8)
 }
 @Test fun futureForecastStopsAtSessionAvailabilityRetrospectiveCanUseLater(){
  val future=lab.copy(id="future",timeH=2.1,concValue=800.0)
  assertArrayEquals(vals(at(4.0,listOf(lab))),vals(at(4.0,listOf(lab,future))),0.0)
  val retro=LabFit.evaluateAt(events,grid,listOf(lab),.8,CalibrationMode.RETROSPECTIVE,2.0)!!
  assertTrue(abs(retro.center-at(.8,listOf(lab)).center)>1);assertEquals(1,retro.labCount)
 }
 @Test fun mixedRouteRetainsNonSlRateAndSlFixedPopulationGolden(){
  val mixed=events+DoseEvent("oral",Route.ORAL,-24.0,2.0,Ester.EV,80.0)
  val a=at(1.0,listOf(lab),es=mixed);assertTrue(a.model.rateAdjustable);assertTrue(abs(a.model.logRate)>1e-6)
  assertEquals(0.0,at(1.0,listOf(lab)).model.logRate,0.0)
  assertEquals(278.86477960569,Engine.simulate(events,grid=doubleArrayOf(46.0/60))!!.curves.getValue(Curve.E2)[0],1e-7)
 }
 @Test fun emptySinglePointOutOfRangeAndCancelledInputs(){
  assertNull(LabFit.evaluateAt(emptyList(),doubleArrayOf(),emptyList(),0.0,CalibrationMode.CAUSAL,2.0))
  for(g in listOf(doubleArrayOf(),doubleArrayOf(1.0),grid))assertTrue(LabFit.evaluateAt(events,g,emptyList(),8.0,CalibrationMode.CAUSAL,2.0)!!.center.isFinite())
  assertEquals(0.0,at(-1.0,listOf(lab)).center,0.0)
  assertThrows(CancellationException::class.java){LabFit.evaluateAt(events,grid,listOf(lab),1.0,CalibrationMode.CAUSAL,2.0,checkCancelled={throw CancellationException()})}
 }
 @Test fun cancellationDuringMonteCarloAndNoLabPriorUnchanged(){
  var checks=0
  assertThrows(CancellationException::class.java){LabFit.evaluateAt(events,grid,listOf(lab),1.0,CalibrationMode.CAUSAL,2.0,checkCancelled={if(++checks==10)throw CancellationException()})}
  assertEquals(10,checks)
  val b=LabFit.bands(events,grid,emptyList(),CalibrationMode.CAUSAL).getValue(Curve.E2);val v=at(.8,emptyList())
  assertEquals(Pk.interpolate(grid,b.center,.8)!!,v.center,1e-8);assertEquals(Pk.interpolate(grid,b.p95,.8)!!,v.p95,1e-8)
 }
}
