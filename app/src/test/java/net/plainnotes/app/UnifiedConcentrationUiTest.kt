package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import net.plainnotes.app.data.*
import net.plainnotes.app.experimental.*
import net.plainnotes.app.pk.experimental.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Locale

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class UnifiedConcentrationUiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val now=Instant.parse("2026-03-10T12:00:00Z")
    private val med=MedicationEntity(1,"Synthetic SL","E2","SUBLINGUAL","MG",1.0,30.0,null,15,120,false,null,true,true,0)
    private val profile=ProfileEntity(1,"E2","sublingual",sl_tier=2)
    private val history=listOf(30L,20L,3L).mapIndexed { i,h -> RecordEntity((i+1).toLong(),1,taken_utc=now.toEpochMilli()-h*3600000,
        taken_zone="UTC",actual_dose=1.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=MedicationSnapshot.encode(med,profile)) }
    @androidx.compose.runtime.Composable private fun content() { MaterialTheme { UnifiedConcentrationScreen(history,emptyMap(),emptyList(),PaddingValues(),{Text("LEGACY-CONTENT",Modifier.testTag("official"))},{now}) } }
    private fun show()=ui.setContent { content() }
    private fun click(tag:String) { ui.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }
    private fun await(tag:String)=ui.waitUntil(30_000){ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun read(tag:String)=ui.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text }
    private fun tap() { ui.onNodeWithTag("unified-chart").performScrollTo().performTouchInput { click(center) }; await("readout-M2") }
    private fun expected()=ExperimentalConcentrationComparison.compute(ExperimentalConcentrationComparison.audit(history,emptyMap(),now),now,ComparisonWindow.H24,null,
        ResearchSublingualV01.candidates.first().id,ResearchM1Catalog.candidates.first().id,ResearchDisplay.CONDITIONAL,CommonCalibration(25.0,100.0),false)!!

    @Test fun m2DefaultIsConditionalAndLegacyIsAvailableWithoutChangingHistory() {
        show();ui.onNodeWithTag("model-M2").assertIsSelected();ui.onNodeWithTag("display-CONDITIONAL").assertIsSelected()
        await("unified-chart");ui.onNodeWithTag("research-not-personal").assertExists();ui.onNodeWithTag("calibration-source").assertExists()
        click("model-M1");ui.onNodeWithTag("model-M1").assertIsSelected()
        click("model-LEGACY");ui.onNodeWithTag("official").assertTextEquals("LEGACY-CONTENT");ui.onNodeWithTag("legacy-unverified").assertExists()
        click("model-M2");await("unified-chart");assertEquals(3,history.size)
    }
    @Test fun overlayExactReadoutAndSharedPmolUnit() {
        show();await("unified-chart");click("model-overlay");tap()
        await("readout-M1");await("readout-LEGACY");ui.onNodeWithTag("difference-M1").assertExists()
        val offset=read("readout-time").removeSuffix(" h").replace(',','.').toDouble()
        val exact=UnifiedModelComparison.valuesAt(expected().result,expected().result.base.originHour+offset)
        val shown=read("readout-M2").substringAfter(": ").substringBefore(" pg/mL").replace(',','.').toDouble()
        // The UI rounds the selected time to .001 h; this tolerance bounds that rounding, not kernel interpolation.
        assertEquals(exact.getValue(ResearchModel.M2),shown,0.1)
        click("unit-pmol");ui.onNodeWithTag("chart-unit").assertTextEquals("pmol/L")
        ui.waitUntil(10_000){read("readout-M2").endsWith("pmol/L")}
        val converted=read("readout-M2").substringAfter(": ").substringBefore(" pmol/L").replace(',','.').toDouble()
        assertEquals(shown*3.671,converted,0.004)
        ui.onNodeWithTag("tail-warning").assertExists()
    }
    @Test fun zoomAndPanChangeTapCoordinateAndRestoreSelection() {
        val restore=StateRestorationTester(ui);restore.setContent { content() };await("unified-chart")
        ui.onNodeWithTag("unified-chart").performScrollTo().performTouchInput {
            val small=androidx.compose.ui.geometry.Offset(width*.05f,0f);val far=androidx.compose.ui.geometry.Offset(width*.35f,0f)
            pinch(center-small,center-far,center+small,center+far)
        }
        tap();val before=read("readout-time")
        ui.onNodeWithTag("unified-chart").performTouchInput { swipeLeft(startX=centerX+width*.3f,endX=centerX-width*.3f) }
        tap();ui.waitUntil(10_000){read("readout-time")!=before}
        val panned=read("readout-time");click("unit-pmol")
        restore.emulateSavedInstanceStateRestore();await("unified-chart")
        ui.onNodeWithTag("unit-pmol").assertIsSelected();await("readout-M2");assertEquals(panned,read("readout-time"))
    }
    @Test fun relativeAndPriceKeepUnitsAndCohortSeparate() {
        show();await("unified-chart");click("display-RELATIVE");await("unified-chart")
        ui.waitUntil(10_000){read("chart-unit")==ui.activity.getString(R.string.uc_relative_unit)}
        ui.onAllNodesWithTag("common-calibration").assertCountEquals(0)
        click("display-STUDY");await("study-not-personal");ui.onNodeWithTag("chart-unit").assertTextEquals("pg/mL")
        ui.onAllNodesWithText(ui.activity.getString(R.string.uc_lab_points)).assertCountEquals(0)
        ui.onNodeWithTag("common-calibration").assertTextEquals(ui.activity.getString(R.string.uc_calibration,"18.000","432.000"))
    }
    @Test fun invalidCalibrationDoesNotLeaveOldValidCurveVisible() {
        show();await("unified-chart");ui.onNodeWithTag("amplitude").performScrollTo().performTextReplacement("NaN")
        ui.onNodeWithTag("invalid-calibration").assertExists();ui.onAllNodesWithTag("unified-chart").assertCountEquals(0)
        ui.onNodeWithTag("amplitude").performTextReplacement("0");ui.onAllNodesWithTag("unified-chart").assertCountEquals(0)
        ui.onNodeWithTag("amplitude").performTextReplacement("200");await("unified-chart")
    }
}
