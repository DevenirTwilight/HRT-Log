package net.plainnotes.app
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.conc.*
import net.plainnotes.app.data.*
import net.plainnotes.app.pk.*
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],application=android.app.Application::class)
class CausalTimeUiTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 private fun check(){
  val now=Instant.parse("2026-10-25T01:10:00Z");val t0=now.minusSeconds(1800);val h=ConcentrationCalculator.hours(t0)
  val m=MedicationEntity(1,"Synthetic E2","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=true,active=true,sort_order=0)
  val p=ProfileEntity(1,"E2","sublingual",sl_tier=2)
  val r=RecordEntity(1,1,taken_utc=t0.minusSeconds(2760).toEpochMilli(),taken_zone="Europe/Paris",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=MedicationSnapshot.encode(m,p))
  val lab=LabValueEntity(1,"E2",400.0,"pg/mL",t0.plusSeconds(300).toEpochMilli(),"Europe/Paris")
  val a=ConcentrationCalculator.compute(listOf(m),mapOf(1L to p),listOf(r),emptyList(),listOf(lab),80.0,now,true,CalibrationMode.CAUSAL,historyRead=HistoryRead(true,ConcentrationCalculator.hours(now)),evaluationTime=t0)
  val tapped=AtomicReference<Double?>();val predicted=AtomicReference<Double?>()
  val data=ChartData(a.timeH,a.e2,a.bandInner,a.bandOuter,a.nowH,a.labs,unit="pg/mL",breaks=a.calibrationBreaks,readAt={t,cancel->a.evaluateAt(t,cancel)?.center?.also{tapped.set(t);predicted.set(it)}})
  var density=1f
  ui.setContent{val d=LocalDensity.current;density=d.density;CompositionLocalProvider(LocalDensity provides Density(d.density,2f)){MaterialTheme{Column(Modifier.verticalScroll(rememberScrollState())){
   ConcChart(data,h-.2,h+.3,Modifier.fillMaxWidth().height(260.dp).testTag("causal_chart")){"%.3f".format(java.util.Locale.US,it)}
   CalibrationCard(a,ConcSettings(false,true,CalibrationMode.CAUSAL),{}, {},{"%.0f".format(java.util.Locale.US,it)},"pg/mL")
  }}}}
  ui.onNodeWithTag("causal_chart").performTouchInput{val pad=44*density;click(androidx.compose.ui.geometry.Offset(pad+(width-pad)*.4f,height*.5f))}
  ui.mainClock.advanceTimeBy(100)
  ui.waitForIdle()
  ui.waitUntil(10000){predicted.get()!=null}
  val actual=tapped.get()!!;Assert.assertTrue(actual<ConcentrationCalculator.hours(t0.plusSeconds(300)))
  val formatted="%.3f".format(java.util.Locale.US,predicted.get()!!)
  ui.onNodeWithText(formatted,substring=true).assertExists()
  Assert.assertNull(a.evaluateAt(actual)!!.calibration)
  ui.onNodeWithText(ui.activity.getString(R.string.calib_history_counts,0,0)).assertExists()
  ui.onNodeWithText(ui.activity.getString(R.string.calib_history_details)).performScrollTo().performClick()
  ui.onNodeWithText(ui.activity.getString(R.string.calib_time_pending)).assertExists()
  ui.onNodeWithText("400 pg/mL").assertExists()
  Assert.assertTrue(a.labEligibility.single().eligible);Assert.assertEquals(1,a.labs.size)
  // Exercise the actual new-PDF renderer too; historical Visit Pack/PDF bytes are not touched.
  val boundary=a.calibrationBreaks.single();val i=a.timeH.indexOfFirst{it>=boundary}
  val bitmap=android.graphics.Bitmap.createBitmap(360,200,android.graphics.Bitmap.Config.ARGB_8888)
  val canvas=android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.WHITE)
  val draw=net.plainnotes.app.export.PdfReport::class.java.declaredMethods.single{it.name=="drawChart"};draw.isAccessible=true
  draw.invoke(net.plainnotes.app.export.PdfReport,canvas,a,0f,0f,360f,180f,a.timeH[i-1]-.001,a.timeH[i]+.001)
  var bridges=0
  for(y in 10..170){val c=bitmap.getPixel(252,y);val red=android.graphics.Color.red(c)
   if(red<230&&android.graphics.Color.green(c)>red&&android.graphics.Color.blue(c)>red)bridges++}
  Assert.assertEquals(0,bridges);bitmap.recycle()
 }
 @Test @Config(qualifiers="en-rUS-w320dp-h891dp-xxhdpi") fun english()=check()
 @Test @Config(qualifiers="zh-rCN-w320dp-h891dp-xxhdpi") fun simplified()=check()
 @Test @Config(qualifiers="b+zh+Hant-w320dp-h891dp-xxhdpi") fun traditional()=check()
 @Test @Config(qualifiers="fr-rFR-w320dp-h891dp-xxhdpi") fun french()=check()
}
