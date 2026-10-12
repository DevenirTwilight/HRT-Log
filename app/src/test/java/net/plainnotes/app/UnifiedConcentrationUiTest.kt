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
    /** The scenario value at the snapshot's own time, read from the preview the screen was given (D-1: no on-screen number). */
    private fun scenarioNow():Double { val p=ui.activity.lastPreview!!; return p.valueAt(p.asOfH!!)!! }
    private fun openBasis() { ui.onNodeWithTag("m2-research-basis-toggle").performScrollTo().performClick(); ui.onNodeWithTag("m2-research-basis").assertExists() }
    /** Polls without throwing, so a condition that never holds fails as an assertion with a message. */
    private fun eventually(timeoutMs:Long=30_000,condition:()->Boolean):Boolean {
        val end=System.currentTimeMillis()+timeoutMs
        while(System.currentTimeMillis()<end) { ui.waitForIdle(); if(condition()) return true; Thread.sleep(50) }
        return condition()
    }
    private fun noSelector() { for(model in ResearchModel.entries) ui.onAllNodesWithTag("model-${model.name}").assertCountEquals(0) }

    @Test fun originalCardsAndCalendarControlsRemainWithoutModelSelector() {
        awaitChart();noSelector()
        ui.onNodeWithText(text(R.string.m2_scenario_title)).assertExists()
        ui.onNodeWithText(text(R.string.m2_chart_title)).assertExists()
        for(days in listOf(7,14,60)) ui.onNodeWithText(ui.activity.getString(R.string.days_short,days)).assertExists()
        for(id in listOf(R.string.calib_title,R.string.pk_model_title,R.string.pk_chart_hint)) ui.onNodeWithText(text(id)).assertExists()
        for(tag in listOf("model-overlay","research-settings-card","scenario-concentration-card","display-STUDY","amplitude")) ui.onAllNodesWithTag(tag).assertCountEquals(0)
        ui.onNodeWithTag("research-not-personal").assertExists();ui.onAllNodesWithTag("research-peaks").assertCountEquals(0)
        openBasis();ui.onNodeWithTag("research-peaks").assertExists()
        // Whole pg/mL only (A = 434.836 in the kernel): no false precision.
        ui.onNode(hasText("≈ 435",substring=true)).assertExists();ui.onAllNodes(hasText("434.8",substring=true)).assertCountEquals(0)
        ui.onNodeWithText(text(R.string.uc_original_assumptions)).assertExists()
        ui.onNodeWithText(text(R.string.legend_band_outer)).assertDoesNotExist()
        ui.onNodeWithText(text(R.string.calib_enable)).assertDoesNotExist()
        ui.onNodeWithTag("m2-calibration-unavailable").assertExists()
    }
    @Test fun m2ShowsNoCurrentNumberAndItsCurveIsTheFixedKernel() {
        // D-1: no prominent personal "current concentration"; the curve is still exactly the frozen M2 scenario.
        awaitChart();noSelector();ui.onNodeWithTag("m2-scenario-card").assertExists()
        ui.onAllNodesWithText(expected()).assertCountEquals(0)
        assertEquals(expected(),kotlin.math.round(scenarioNow()).toInt().toString())
        // One top-level explanation: the Legacy population caveat is not stacked on the M2 page.
        ui.onNodeWithText(text(R.string.pk_disclaimer_short)).assertDoesNotExist()
        ui.onNodeWithText(text(R.string.pk_current)).assertDoesNotExist()
        ui.onNodeWithTag("concentration-legacy").assertDoesNotExist()
    }
    @Test fun originalUnitSwitchConvertsResearchValueAndEveryReadoutIsMarkedScenario() {
        awaitChart();ui.onNodeWithText("pmol/L").performScrollTo().performClick()
        ui.onNodeWithTag("unified-chart").performScrollTo().performTouchInput{click(center)}
        val marked=ui.activity.getString(R.string.m2_scenario_unit,"pmol/L")
        ui.waitUntil(10_000){ui.onAllNodes(hasText("  ·  ",substring=true)).fetchSemanticsNodes().isNotEmpty()}
        val bubble=ui.onNode(hasText("  ·  ",substring=true)).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString{it.text}
        assertTrue("Readout must be marked as a scenario value: $bubble",bubble.endsWith(marked))
        assertEquals(expected(3.671),kotlin.math.round(scenarioNow()*net.plainnotes.app.pk.Pk.PMOL_PER_PG).toInt().toString())
    }
    @Test fun personalLabsCannotScaleM2AndOldCalibrationIsNotPresentedAsM2() {
        awaitChart();val before=ui.activity.lastPreview!!.values.copyOf();assertEquals(expected(),kotlin.math.round(scenarioNow()).toInt().toString())
        ui.runOnUiThread { ui.activity.syntheticLabPgMl=9000.0;ui.activity.syntheticHistoryRevision++ }
        awaitChart();ui.waitForIdle()
        // Adding/changing a lab leaves every scenario value exactly unchanged.
        assertArrayEquals(before,ui.activity.lastPreview!!.values,0.0);assertEquals(expected(),kotlin.math.round(scenarioNow()).toInt().toString())
        // Labs are a separate list, not dots on the scenario curve.
        ui.onNodeWithTag("m2-labs-separate").performScrollTo().assertExists()
        ui.onNode(hasText("9000",substring=true)).assertExists()
        ui.onNodeWithText(text(R.string.legend_labs)).assertDoesNotExist()
        ui.onNodeWithText(text(R.string.m2_observed_labs)).assertExists()
        ui.onNodeWithText(text(R.string.calib_enable)).assertDoesNotExist()
        ui.onNodeWithTag("m2-calibration-unavailable").assertExists()
        ui.onNodeWithText(text(R.string.model_e2_sl)).assertDoesNotExist()
        val note=ui.onNodeWithTag("research-not-personal").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
        assertFalse("M2 note cannot claim removed Legacy calibration cards",note.contains("Legacy",ignoreCase=true))
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
        // The population caveat now sits with the other-medicine estimates it describes.
        ui.onNodeWithText(text(R.string.pk_disclaimer_short)).assertExists()
        ui.runOnUiThread { ui.activity.previewDelayMillis=1000L;ui.activity.previewFails=true;ui.activity.syntheticHistoryRevision++ }
        awaitTag("research-preview-loading");ui.onNodeWithText(text(R.string.pk_no_doses_title)).assertDoesNotExist()
        awaitTag("research-preview-error");ui.onNodeWithText(text(R.string.pk_no_doses_title)).assertDoesNotExist()
        ui.onNodeWithText(text(R.string.pk_other_title)).assertExists()
        ui.onAllNodesWithTag("research-peaks").assertCountEquals(0)
        ui.runOnUiThread { ui.activity.previewDelayMillis=0L;ui.activity.previewFails=false }
        ui.onNodeWithTag("m2-retry").performScrollTo().performClick();awaitChart()
        ui.onNodeWithText(text(R.string.m2_scenario_title)).assertExists()
    }
    @Test fun completedHistoryRefreshRetainsOriginalSelectedRange() {
        awaitChart();val seven=ui.activity.getString(R.string.days_short,7)
        ui.onNodeWithText(seven).performScrollTo().performClick();ui.onNodeWithText(seven).assertIsSelected()
        ui.runOnUiThread { ui.activity.previewDelayMillis=1000L;ui.activity.syntheticHistoryRevision++ }
        awaitTag("research-preview-loading");awaitChart()
        ui.onNodeWithText(seven).assertIsSelected();openBasis();ui.onNodeWithTag("research-peaks").assertExists()
    }
    @Test fun fixedM2AndOriginalUnitSurviveRecreation() {
        awaitChart();ui.onNodeWithText("pmol/L").performScrollTo().performClick()
        ui.activityRule.scenario.recreate();awaitChart();noSelector()
        ui.onNodeWithText(text(R.string.m2_scenario_title)).assertExists()
        // D-1 removed the hero unit label; the selected unit itself must survive recreation.
        ui.onNode(hasText("pmol/L") and isSelected()).assertExists()
    }
    @Test fun mixedRoutesAreExplicitlyPartialAndNeverTotalE2() {
        awaitChart()
        for(route in listOf("ORAL","GEL","PATCH","EV")) {
            ui.runOnUiThread { ui.activity.syntheticExtraRoute=route }
            awaitTag("m2-partial-scope");awaitChart();ui.waitForIdle()
            assertEquals(expected(),kotlin.math.round(scenarioNow()).toInt().toString())
            ui.onAllNodesWithText(expected()).assertCountEquals(0)
            ui.onNodeWithText(text(R.string.m2_partial_scope)).assertExists()
            ui.onNodeWithText(text(R.string.pk_current)).assertDoesNotExist()
            ui.onNodeWithText(text(R.string.calib_enable)).assertDoesNotExist()
        }
    }
    @Test fun otherRouteOnlyRetainsCompatibilityCurveAndItsScopedCalibration() {
        awaitChart();ui.runOnUiThread { ui.activity.syntheticSublingualEnabled=false;ui.activity.syntheticExtraRoute="ORAL" }
        awaitTag("e2-compatibility-scope");ui.waitUntil(30_000){ui.onAllNodesWithText(text(R.string.m2_compatibility_current)).fetchSemanticsNodes().isNotEmpty()}
        noSelector();ui.onNodeWithText(text(R.string.m2_scenario_title)).assertDoesNotExist()
        ui.onNodeWithText(text(R.string.calib_enable)).assertExists()
        ui.onNodeWithText(text(R.string.m2_calibration_compatibility_scope)).assertExists()
    }
    @Test fun unknownHistoryNeverFallsBackToOldSublingualPrediction() {
        awaitChart();ui.runOnUiThread { ui.activity.syntheticSublingualEnabled=false;ui.activity.syntheticUnknownContext=true }
        awaitTag("m2-no-qualified-history");noSelector()
        ui.onNodeWithText(text(R.string.m2_scenario_title)).assertDoesNotExist()
        ui.onNodeWithText(text(R.string.pk_current)).assertDoesNotExist()
    }
    @Test fun wrongModelCannotBeRelabelledM2() {
        awaitChart();ui.runOnUiThread { ui.activity.syntheticWrongPreview=true;ui.activity.syntheticHistoryRevision++ }
        awaitTag("research-preview-error");ui.onNodeWithText(text(R.string.m2_scenario_title)).assertDoesNotExist()
        ui.onNodeWithText(text(R.string.pk_current)).assertDoesNotExist();noSelector()
    }
    @Test fun unsupportedSublingualEvIsNotM2OrEmptyRecordedHistory() {
        awaitChart();ui.runOnUiThread { ui.activity.syntheticSublingualEnabled=false;ui.activity.syntheticExtraRoute="EV" }
        ui.waitUntil(30_000){ui.onAllNodesWithText(text(R.string.unsupported_sl_ev)).fetchSemanticsNodes().isNotEmpty()}
        ui.waitForIdle();ui.onNodeWithText(text(R.string.m2_scenario_title)).assertDoesNotExist()
        ui.onNodeWithText(text(R.string.pk_no_doses_title)).assertDoesNotExist();noSelector()
    }

    @Test fun mixedE2WithoutScopeCannotAppearAsTotal() {
        awaitChart();ui.runOnUiThread { ui.activity.syntheticExtraRoute="ORAL" }
        awaitChart();ui.waitForIdle()
        ui.onNodeWithText(text(R.string.m2_partial_scope)).assertExists()
        openBasis();ui.onNodeWithText(text(R.string.uc_original_assumptions)).assertExists()
        ui.onNodeWithText("Total E2").assertDoesNotExist()
    }

    @Test fun timeBaseStaysConsistentWhileThePageStaysOpen() {
        // D-1 / REQUIREMENTS §67: staying on the page past a minute refreshes the snapshot; curve, "now", readouts and
        // the shown calculation time come from one asOf, and the refresh never flashes the loading overlay.
        awaitChart();val first=ui.activity.lastPreview!!.asOfH!!
        val shownFirst=ui.onNodeWithTag("m2-scenario-as-of").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString{it.text}
        ui.activity.clockShiftMillis=3*60_000L
        ui.mainClock.advanceTimeBy(61_000)
        assertTrue("Snapshot must refresh after a minute on the page",eventually{ui.activity.lastPreview?.asOfH?.let{it>first}==true})
        val second=ui.activity.lastPreview!!.asOfH!!
        assertEquals(first+3/60.0,second,1e-9)
        ui.onAllNodesWithTag("research-preview-loading").assertCountEquals(0)
        assertTrue("Shown calculation time must follow the snapshot",eventually(10_000){ui.onNodeWithTag("m2-scenario-as-of").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString{it.text}!=shownFirst})
        // The scenario at the new snapshot time is the kernel's value at that time, not the old one.
        assertEquals(ui.activity.lastPreview!!.valueAt(second)!!,scenarioNow(),0.0)
    }

}
