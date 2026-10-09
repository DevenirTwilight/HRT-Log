package net.plainnotes.app
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.conc.*
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.CalibrationEligibilitySection
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],application=android.app.Application::class)
class CalibrationEligibilityUiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private fun check() {
        val now=Instant.parse("2026-03-10T12:00:00Z")
        val m=MedicationEntity(1,"Synthetic E2","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=true,active=true,sort_order=0)
        val p=ProfileEntity(1,"E2","sublingual",sl_tier=2)
        fun record(id:Long,h:Long,json:String)=RecordEntity(id,1,taken_utc=now.minusSeconds(h*3600).toEpochMilli(),taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=json)
        fun lab(id:Long,h:Long,value:Double)=LabValueEntity(id,"E2",value,"pg/mL",now.minusSeconds(h*3600).toEpochMilli(),"UTC")
        val result=ConcentrationCalculator.compute(listOf(m),mapOf(1L to p),listOf(record(1,12,MedicationSnapshot.encode(m,p)),record(2,6,"{}")),emptyList(),listOf(lab(1,8,120.0),lab(2,0,220.0)),80.0,now,historyRead=HistoryRead(true,ConcentrationCalculator.hours(now)))
        ui.setContent { val d=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(d.density,2f)){MaterialTheme{Column{CalibrationEligibilitySection(result,{it.toInt().toString()},"pg/mL")}}} }
        ui.onNodeWithText(ui.activity.getString(R.string.calib_history_counts,1,1)).assertExists()
        ui.onNode(hasText(ui.activity.getString(R.string.calib_history_details)) and hasClickAction()).performClick()
        ui.onNodeWithText("120 pg/mL").assertExists()
        ui.onNodeWithText(ui.activity.getString(R.string.calib_history_eligible)).assertExists()
        ui.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        ui.onNodeWithText("220 pg/mL").assertExists()
        ui.onNodeWithText(ui.activity.getString(R.string.calib_history_excluded)).assertExists()
        ui.onNodeWithText(ui.activity.getString(R.string.calib_history_context)).assertExists()
        ui.onNodeWithText(ui.activity.getString(R.string.calib_history_done)).performClick()
        ui.onNode(isDialog()).assertDoesNotExist()
        Assert.assertEquals(2,result.labs.size);Assert.assertEquals(1,result.calibration!!.labCount)
    }
    @Test @Config(qualifiers="en-rUS-w320dp-h891dp-xxhdpi") fun english()=check()
    @Test @Config(qualifiers="zh-rCN-w320dp-h891dp-xxhdpi") fun simplified()=check()
    @Test @Config(qualifiers="b+zh+Hant-w320dp-h891dp-xxhdpi") fun traditional()=check()
    @Test @Config(qualifiers="fr-rFR-w320dp-h891dp-xxhdpi") fun french()=check()
    @Test @Config(qualifiers="en-rUS-w320dp-h891dp-xxhdpi") fun rejectedLabsKeepCalibrationSwitchModesAndManageButton() {
        val now=Instant.parse("2026-03-10T12:00:00Z")
        val m=MedicationEntity(1,"Synthetic E2","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=true,active=true,sort_order=0)
        val p=ProfileEntity(1,"E2","sublingual",sl_tier=2)
        fun record(id:Long,h:Long,json:String)=RecordEntity(id,1,taken_utc=now.minusSeconds(h*3600).toEpochMilli(),taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=json)
        val l=LabValueEntity(1,"E2",220.0,"pg/mL",now.toEpochMilli(),"UTC")
        val r=ConcentrationCalculator.compute(listOf(m),mapOf(1L to p),listOf(record(1,12,"{}"),record(2,1,MedicationSnapshot.encode(m,p))),emptyList(),listOf(l),80.0,now,historyRead=HistoryRead(true,ConcentrationCalculator.hours(now)))
        var settings:net.plainnotes.app.ui.ConcSettings?=null;var opened=0
        ui.setContent{MaterialTheme{Column(androidx.compose.ui.Modifier.verticalScroll(rememberScrollState())){
            net.plainnotes.app.ui.CalibrationCard(r,net.plainnotes.app.ui.ConcSettings(false,true,net.plainnotes.app.pk.CalibrationMode.RETROSPECTIVE),{settings=it},{opened++},{it.toInt().toString()},"pg/mL")
        }}}
        ui.onNodeWithText(ui.activity.getString(R.string.calib_history_population)).assertExists()
        ui.onNodeWithText(ui.activity.getString(R.string.calib_causal)).performScrollTo().performClick()
        ui.runOnIdle{Assert.assertEquals(net.plainnotes.app.pk.CalibrationMode.CAUSAL,settings!!.mode)}
        ui.onNodeWithText(ui.activity.getString(R.string.calib_manage_labs)).performScrollTo().performClick()
        ui.runOnIdle{Assert.assertEquals(1,opened)}
        Assert.assertNull(r.calibration);Assert.assertEquals(1,r.labs.size)
    }

}
