package net.plainnotes.app
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.debug.UnifiedConcentrationTestActivity
import net.plainnotes.app.pk.experimental.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class UnifiedConcentrationUiTest {
    @get:Rule val ui=createAndroidComposeRule<UnifiedConcentrationTestActivity>()
    private fun awaitChart()=ui.waitUntil(30_000){ui.onAllNodesWithTag("unified-chart").fetchSemanticsNodes().isNotEmpty()}
    private fun awaitTag(tag:String)=ui.waitUntil(30_000){ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun text(id:Int)=ui.activity.getString(id)
    private fun expected(factor:Double=1.0)=kotlin.math.round(ResearchSublingualV01.studyScenario(0.0,
        listOf(-30.0,-20.0,-3.0).map{ExperimentalDose(it,1.0)},ResearchDisplayProtocol.candidate(ResearchModel.M2),ResearchDisplayProtocol.anchor(ResearchModel.M2))*factor).toInt().toString()
    private fun noSelector() { for(model in ResearchModel.entries) ui.onAllNodesWithTag("model-${model.name}").assertCountEquals(0) }

    @Test fun originalCardsAndCalendarControlsRemainWithoutModelSelector() {
        awaitChart();noSelector()
        ui.onNodeWithText(text(R.string.m2_reference_title)).assertExists()
        ui.onNodeWithText(text(R.string.m2_chart_title)).assertExists()
        for(days in listOf(7,14,60)) ui.onNodeWithText(ui.activity.getString(R.string.days_short,days)).assertExists()
        for(id in listOf(R.string.calib_title,R.string.pk_model_title,R.string.pk_chart_hint)) ui.onNodeWithText(text(id)).assertExists()
        for(tag in listOf("model-overlay","research-settings-card","scenario-concentration-card","display-STUDY","amplitude")) ui.onAllNodesWithTag(tag).assertCountEquals(0)
        ui.onNodeWithTag("research-not-personal").assertExists();ui.onNodeWithTag("research-peaks").assertExists()
        ui.onNode(hasText("434.836",substring=true)).assertExists()
        ui.onNodeWithText(text(R.string.uc_original_assumptions)).assertExists()
        ui.onNodeWithText(text(R.string.legend_band_outer)).assertDoesNotExist()
        ui.onNodeWithText(text(R.string.calib_enable)).assertDoesNotExist()
        ui.onNodeWithTag("m2-calibration-unavailable").assertExists()
    }
    @Test fun fixedM2KernelOwnsHeroWithoutOldPredictionFallback() {
        awaitChart();ui.onNodeWithText(expected()).assertExists();noSelector()
        ui.onNodeWithText(text(R.string.pk_current)).assertDoesNotExist()
        ui.onNodeWithTag("concentration-legacy").assertDoesNotExist()
    }
    @Test fun originalUnitSwitchConvertsResearchValue() {
        awaitChart();ui.onNodeWithText("pmol/L").performScrollTo().performClick()
        ui.onNodeWithText(expected(3.671)).assertExists()
    }
    @Test fun personalLabsCannotScaleM2AndOldCalibrationIsNotPresentedAsM2() {
        awaitChart();ui.onNodeWithText(expected()).assertExists()
        ui.runOnUiThread { ui.activity.syntheticLabPgMl=9000.0;ui.activity.syntheticHistoryRevision++ }
        awaitChart();ui.waitForIdle();ui.onNodeWithText(expected()).assertExists()
        ui.onNodeWithText(text(R.string.m2_observed_labs)).assertExists()
        ui.onNodeWithText(text(R.string.calib_enable)).assertDoesNotExist()
        ui.onNodeWithTag("m2-calibration-unavailable").assertExists()
        ui.onNodeWithText(text(R.string.model_e2_sl)).assertDoesNotExist()
    }
    @Test fun originalChartTapZoomAndPanRemainOperable() {
        awaitChart();val chart=ui.onNodeWithTag("unified-chart").performScrollTo()
        chart.performTouchInput{click(center)}
        ui.waitUntil(10_000){ui.onAllNodes(hasText("  ·  ",substring=true)).fetchSemanticsNodes().isNotEmpty()}
        val before=ui.onNode(hasText("  ·  ",substring=true)).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text
        chart.performTouchInput{swipeLeft(startX=centerX+width*.25f,endX=centerX-width*.25f)}
        chart.performTouchInput{click(center)}
        ui.waitUntil(10_000){ui.onAllNodes(hasText("  ·  ",substring=true)).fetchSemanticsNodes().any { it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text!=before }}
        chart.performTouchInput{ val small=androidx.compose.ui.geometry.Offset(width*.05f,0f);val far=androidx.compose.ui.geometry.Offset(width*.3f,0f);pinch(center-small,center-far,center+small,center+far) }
        ui.waitForIdle()
        chart.performTouchInput{click(center)}
        ui.waitUntil(10_000){ui.onAllNodes(hasText("  ·  ",substring=true)).fetchSemanticsNodes().isNotEmpty()}
        ui.onNode(hasText("  ·  ",substring=true)).assertExists()
    }

    @Test fun failedM2NeverMasqueradesAsEmptyHistoryAndOtherMedicinesRemainAvailable() {
        awaitChart()
        ui.runOnUiThread { ui.activity.syntheticExtraRoute="SPI" }
        awaitChart();ui.onNodeWithText(text(R.string.pk_other_title)).assertExists()
        ui.runOnUiThread { ui.activity.previewDelayMillis=1000L;ui.activity.previewFails=true;ui.activity.syntheticHistoryRevision++ }
        awaitTag("research-preview-loading");ui.onNodeWithText(text(R.string.pk_no_doses_title)).assertDoesNotExist()
        awaitTag("research-preview-error");ui.onNodeWithText(text(R.string.pk_no_doses_title)).assertDoesNotExist()
        ui.onNodeWithText(text(R.string.pk_other_title)).assertExists()
        ui.onAllNodesWithTag("research-peaks").assertCountEquals(0)
        ui.runOnUiThread { ui.activity.previewDelayMillis=0L;ui.activity.previewFails=false }
        ui.onNodeWithTag("m2-retry").performScrollTo().performClick();awaitChart()
        ui.onNodeWithText(text(R.string.m2_reference_title)).assertExists()
    }
    @Test fun completedHistoryRefreshRetainsOriginalSelectedRange() {
        awaitChart();val seven=ui.activity.getString(R.string.days_short,7)
        ui.onNodeWithText(seven).performScrollTo().performClick();ui.onNodeWithText(seven).assertIsSelected()
        ui.runOnUiThread { ui.activity.previewDelayMillis=1000L;ui.activity.syntheticHistoryRevision++ }
        awaitTag("research-preview-loading");awaitChart()
        ui.onNodeWithText(seven).assertIsSelected();ui.onNodeWithTag("research-peaks").assertExists()
    }
    @Test fun fixedM2AndOriginalUnitSurviveRecreation() {
        awaitChart();ui.onNodeWithText("pmol/L").performScrollTo().performClick()
        ui.activityRule.scenario.recreate();awaitChart();noSelector()
        ui.onNodeWithText(text(R.string.m2_reference_title)).assertExists()
        assertTrue(ui.onAllNodesWithText("pmol/L").fetchSemanticsNodes().size>=2)
    }
    @Test fun mixedRoutesAreExplicitlyPartialAndNeverTotalE2() {
        awaitChart()
        for(route in listOf("ORAL","GEL","PATCH","EV")) {
            ui.runOnUiThread { ui.activity.syntheticExtraRoute=route }
            awaitTag("m2-partial-scope");awaitChart();ui.waitForIdle()
            ui.onNodeWithText(expected()).assertExists()
            ui.onNodeWithText(text(R.string.m2_partial_scope)).assertExists()
            ui.onNodeWithText(text(R.string.pk_current)).assertDoesNotExist()
            ui.onNodeWithText(text(R.string.calib_enable)).assertDoesNotExist()
        }
    }
    @Test fun otherRouteOnlyRetainsCompatibilityCurveAndItsScopedCalibration() {
        awaitChart();ui.runOnUiThread { ui.activity.syntheticSublingualEnabled=false;ui.activity.syntheticExtraRoute="ORAL" }
        awaitTag("e2-compatibility-scope");ui.waitUntil(30_000){ui.onAllNodesWithText(text(R.string.m2_compatibility_current)).fetchSemanticsNodes().isNotEmpty()}
        noSelector();ui.onNodeWithText(text(R.string.m2_reference_title)).assertDoesNotExist()
        ui.onNodeWithText(text(R.string.calib_enable)).assertExists()
        ui.onNodeWithText(text(R.string.m2_calibration_compatibility_scope)).assertExists()
    }
    @Test fun unknownHistoryNeverFallsBackToOldSublingualPrediction() {
        awaitChart();ui.runOnUiThread { ui.activity.syntheticSublingualEnabled=false;ui.activity.syntheticUnknownContext=true }
        awaitTag("m2-no-qualified-history");noSelector()
        ui.onNodeWithText(text(R.string.m2_reference_title)).assertDoesNotExist()
        ui.onNodeWithText(text(R.string.pk_current)).assertDoesNotExist()
    }
    @Test fun wrongModelCannotBeRelabelledM2() {
        awaitChart();ui.runOnUiThread { ui.activity.syntheticWrongPreview=true;ui.activity.syntheticHistoryRevision++ }
        awaitTag("research-preview-error");ui.onNodeWithText(text(R.string.m2_reference_title)).assertDoesNotExist()
        ui.onNodeWithText(text(R.string.pk_current)).assertDoesNotExist();noSelector()
    }
}
