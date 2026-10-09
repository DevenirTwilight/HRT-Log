package net.plainnotes.app.conc
import net.plainnotes.app.data.*
import net.plainnotes.app.pk.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
/** Actual Android calculator / JSON snapshot runtime. Synthetic facts, no user database opened. */
class CausalTimeAndroidTest {
 private val now=Instant.parse("2026-10-25T01:10:00Z");private val query=now.minusSeconds(1800)
 private val med=MedicationEntity(1,"Synthetic E2","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=true,active=true,sort_order=0)
 private val p=ProfileEntity(1,"E2","sublingual",sl_tier=2)
 private val r=RecordEntity(1,1,taken_utc=query.minusSeconds(2760).toEpochMilli(),taken_zone="Europe/Paris",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=MedicationSnapshot.encode(med,p))
 private fun run(labs:List<LabValueEntity>)=ConcentrationCalculator.compute(listOf(med),mapOf(1L to p),listOf(r),emptyList(),labs,80.0,now,true,CalibrationMode.CAUSAL,historyRead=HistoryRead(true,ConcentrationCalculator.hours(now)),evaluationTime=query)
 private fun lab(v:Double)=LabValueEntity(1,"E2",v,"pg/mL",query.plusSeconds(300).toEpochMilli(),"Europe/Paris")
 @Test fun qualifiedLaterLabCannotChangeHistoricReadoutOrAnyBand(){
  val a=run(emptyList()).currentEvaluation!!;val b=run(listOf(lab(400.0)));val c=b.currentEvaluation!!
  assertTrue(b.labEligibility.single().eligible);assertEquals(1,b.labs.size);assertNull(b.calibration)
  assertArrayEquals(doubleArrayOf(a.center,a.p5,a.p25,a.p75,a.p95),doubleArrayOf(c.center,c.p5,c.p25,c.p75,c.p95),1e-7)
 }
 @Test fun exactSamplingMillisecondBoundaryAndForecastUseOnlyAvailableLabs(){
  val b=run(listOf(lab(400.0)));val t=ConcentrationCalculator.hours(query.plusSeconds(300))
  assertNull(b.evaluateAt(t-1.0/3600000)!!.calibration);assertEquals(1,b.evaluateAt(t)!!.calibration!!.labCount)
  assertEquals(1,b.evaluateAt(t+1.0/3600000)!!.calibration!!.labCount);assertEquals(0.0,b.evaluateAt(t)!!.calibration!!.model.logRate,0.0)
 }
 @Test fun backwardsRepeatedQueriesAndFutureModificationPreserveHistoricState(){
  val a=run(listOf(lab(400.0)));val b=run(listOf(lab(800.0)));val t=ConcentrationCalculator.hours(query)
  a.evaluateAt(t+1);assertEquals(a.evaluateAt(t)!!.center,b.evaluateAt(t)!!.center,1e-7);assertNull(a.evaluateAt(t)!!.calibration)
  assertEquals(2,ConcentrationCalculator.VERSION)
 }
 @Test fun newlyGeneratedPdfChartDoesNotBridgeTheCausalFitBoundary(){
  val r=run(listOf(lab(400.0)));val boundary=r.calibrationBreaks.single()
  val i=r.timeH.indexOfFirst{it>=boundary};assertTrue(i>0)
  val start=r.timeH[i-1]-.001;val end=r.timeH[i]+.001
  val bitmap=android.graphics.Bitmap.createBitmap(360,200,android.graphics.Bitmap.Config.ARGB_8888)
  val canvas=android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.WHITE)
  val draw=net.plainnotes.app.export.PdfReport::class.java.declaredMethods.single{it.name=="drawChart"};draw.isAccessible=true
  draw.invoke(net.plainnotes.app.export.PdfReport,canvas,r,0f,0f,360f,180f,start,end)
  var bridged=0
  for(y in 10..170){val c=bitmap.getPixel(252,y);val red=android.graphics.Color.red(c)
   if(red<230&&android.graphics.Color.green(c)>red&&android.graphics.Color.blue(c)>red)bridged++}
  assertEquals("No model line or uncertainty fill may cross this gap",0,bridged)
  bitmap.recycle()
 }

}
