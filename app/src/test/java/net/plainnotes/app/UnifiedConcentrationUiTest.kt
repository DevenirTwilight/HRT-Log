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
    private fun awaitChart()=ui.waitUntil(30_000){ui.onAllNodesWithTag("e2-chart").fetchSemanticsNodes().isNotEmpty()}
    private fun awaitTag(tag:String)=ui.waitUntil(30_000){ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun text(id:Int)=ui.activity.resources.getString(id)
    private fun expected(factor:Double=1.0)=kotlin.math.round(ResearchSublingualV01.studyScenario(0.0,
        listOf(-30.0,-20.0,-3.0).map{ExperimentalDose(it,1.0)},ResearchDisplayProtocol.candidate(ResearchModel.M2),ResearchDisplayProtocol.anchor(ResearchModel.M2))*factor).toInt().toString()
    /** The scenario value at the snapshot's own time, read from the preview the screen was given (D-1: no on-screen number). */
    private fun scenarioNow():Double { val p=ui.activity.lastPreview!!; return p.valueAt(p.asOfH!!)!! }
    /** UI-12: every explanation lives under "Model and literature"; open that window first. */
    private fun openWindow() { if(ui.onAllNodesWithTag("models-literature-window").fetchSemanticsNodes().isEmpty()) ui.onNodeWithTag("models-literature-open").performScrollTo().performClick(); ui.onNodeWithTag("models-literature-window").assertExists() }
    private fun closeWindow() { ui.onNodeWithTag("models-literature-back").performClick(); ui.waitForIdle() }
    private fun openBasis() { openWindow(); ui.onNodeWithTag("m2-research-basis-toggle").performScrollTo().performClick(); ui.onNodeWithTag("m2-research-basis").assertExists() }
    private fun readout():String=ui.onNodeWithTag("chart-readout").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].joinToString(" ")
    private fun awaitReadout()=ui.waitUntil(10_000){ui.onAllNodesWithTag("chart-readout").fetchSemanticsNodes().isNotEmpty()}
    /** Polls without throwing, so a condition that never holds fails as an assertion with a message. */
    private fun eventually(timeoutMs:Long=30_000,condition:()->Boolean):Boolean {
        val end=System.currentTimeMillis()+timeoutMs
        while(System.currentTimeMillis()<end) { ui.waitForIdle(); if(condition()) return true; Thread.sleep(50) }
        return condition()
    }
    private fun noSelector() { for(model in ResearchModel.entries) ui.onAllNodesWithTag("model-${model.name}").assertCountEquals(0) }

    // UI-12 migration of originalCardsAndCalendarControlsRemainWithoutModelSelector:
    //  kept: no model selector or overlay, no calibration switch you can use with sublingual doses, the model card, whole pg/mL (no false precision),
    //        the assumptions and the research peaks (now reachable under "Model and literature");
    //  changed: ranges are 3 and 7 days only; the chart title is "E2 curve"; the scenario card, the research note on the page, the hint and the band legend are gone;
    //  the unavailable-calibration note moved from m2-calibration-unavailable to calibration-unavailable-sl.
    @Test fun pageKeepsItsCardsWithoutModelSelectorAndOffersThreeAndSevenDays() {
        awaitChart();noSelector()
        ui.onNodeWithTag("e2-estimate-card").assertExists()
        ui.onNodeWithText(text(R.string.ui12_curve_title)).assertExists()
        for(days in listOf(3,7)) ui.onNodeWithText(ui.activity.getString(R.string.days_short,days)).assertExists()
        for(days in listOf(14,60)) ui.onAllNodesWithText(ui.activity.getString(R.string.days_short,days)).assertCountEquals(0)
        ui.onNodeWithText(ui.activity.getString(R.string.days_short,3)).assertIsSelected()
        for(id in listOf(R.string.calib_title,R.string.pk_model_title)) ui.onNodeWithText(text(id)).assertExists()
        ui.onAllNodesWithText(text(R.string.pk_chart_hint)).assertCountEquals(0)
        for(tag in listOf("model-overlay","research-settings-card","scenario-concentration-card","display-STUDY","amplitude","m2-scenario-card","research-not-personal","m2-labs-separate","m2-calibration-unavailable")) ui.onAllNodesWithTag(tag).assertCountEquals(0)
        ui.onAllNodesWithTag("research-peaks").assertCountEquals(0)
        openBasis();ui.onNodeWithTag("research-not-personal").assertExists();ui.onNodeWithTag("research-peaks").assertExists()
        // Whole pg/mL only (A = 434.836 in the kernel): no false precision.
        ui.onNode(hasText("≈ 435",substring=true)).assertExists();ui.onAllNodes(hasText("434.8",substring=true)).assertCountEquals(0)
        ui.onNodeWithText(text(R.string.uc_original_assumptions)).assertExists()
        closeWindow()
        ui.onNodeWithText(text(R.string.legend_band_outer)).assertDoesNotExist()
        // SL-CAL-02 step 4: the switch is usable with sublingual doses (it starts off in this harness); the old "unavailable" note is gone.
        ui.onNodeWithTag("calibration-switch").performScrollTo().assertIsEnabled().assertIsOff()
        ui.onAllNodesWithTag("calibration-unavailable-sl").assertCountEquals(0)
    }
    // UI-12 migration of m2ShowsNoCurrentNumberAndItsCurveIsTheFixedKernel:
    //  kept: the curve is exactly the frozen M2 scenario value; no selector; no Legacy card; no second caveat stacked on the page;
    //  superseded (D-1 item "no prominent personal current number"): the estimate card now shows the total at now, which equals the kernel value for sublingual only.
    @Test fun totalShowsTheResultFirstAndItsCurveIsTheFixedKernel() {
        awaitChart();noSelector();ui.onNodeWithTag("e2-estimate-card").assertExists()
        ui.onNodeWithTag("m2-scenario-card").assertDoesNotExist()
        assertEquals(expected(),kotlin.math.round(scenarioNow()).toInt().toString())
        assertEquals(expected(),ui.onNodeWithTag("e2-now-value",useUnmergedTree=true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString(""))
        ui.onNodeWithText(text(R.string.pk_disclaimer_short)).assertDoesNotExist()
        ui.onNodeWithText(text(R.string.pk_current)).assertDoesNotExist()
        ui.onNodeWithTag("concentration-legacy").assertDoesNotExist()
        ui.onAllNodesWithText(text(R.string.ui12_reminder)).assertCountEquals(1)
    }
    // UI-12 migration of originalUnitSwitchConvertsResearchValueAndEveryReadoutIsMarkedScenario:
    //  kept: the unit switch converts exactly by 3.671 and the value is the kernel's;
    //  superseded: the readout no longer carries a "scenario" marker; it names date, number and unit (spoken in words for a screen reader).
    @Test fun unitSwitchConvertsTheTotalAndTheReadoutNamesDateNumberAndUnit() {
        awaitChart();ui.onNodeWithText("pmol/L").performScrollTo().performClick()
        ui.onNodeWithTag("e2-chart").performScrollTo().performTouchInput{click(center)}
        awaitReadout()
        val spoken=readout()
        assertTrue("Readout must say the unit in words: $spoken",spoken.contains(text(R.string.ui12_unit_pmol_spoken)))
        assertFalse("Readout no longer carries the scenario marker",spoken.contains(ui.activity.getString(R.string.m2_scenario_unit,"pmol/L")))
        assertEquals(expected(3.671),kotlin.math.round(scenarioNow()*net.plainnotes.app.pk.Pk.PMOL_PER_PG).toInt().toString())
        assertEquals(expected(3.671),ui.onNodeWithTag("e2-now-value",useUnmergedTree=true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString(""))
    }
    // UI-12 migration of personalLabsCannotScaleM2AndOldCalibrationIsNotPresentedAsM2:
    //  kept: a lab never changes a single M2 value; the retired Legacy sublingual card is never shown; calibration stays unavailable with sublingual doses;
    //        the research note makes no Legacy-calibration claim (now in "Model and literature");
    //  superseded (D-1 "labs are listed separately, never drawn on the curve"): labs are dots on the total, so the separate list is gone and the legend names them.
    @Test fun labsAreDotsOnTheTotalAndNeverChangeTheM2Values() {
        awaitChart();val before=ui.activity.lastPreview!!.values.copyOf();assertEquals(expected(),kotlin.math.round(scenarioNow()).toInt().toString())
        ui.runOnUiThread { ui.activity.syntheticLabPgMl=9000.0;ui.activity.syntheticHistoryRevision++ }
        awaitChart();ui.waitForIdle()
        assertArrayEquals(before,ui.activity.lastPreview!!.values,0.0);assertEquals(expected(),kotlin.math.round(scenarioNow()).toInt().toString())
        ui.onNodeWithText(text(R.string.legend_labs)).performScrollTo().assertExists()
        ui.onAllNodesWithTag("m2-labs-separate").assertCountEquals(0)
        ui.onNodeWithText(text(R.string.m2_observed_labs)).assertDoesNotExist()
        ui.onNodeWithTag("calibration-switch").performScrollTo().assertIsEnabled().assertIsOff()
        ui.onAllNodesWithTag("calibration-unavailable-sl").assertCountEquals(0)
        // SL-CAL-02: turning the calibration on may scale the displayed total, but never the frozen M2 kernel values.
        ui.onNodeWithTag("calibration-switch").performClick()
        ui.waitUntil(30_000){ui.onAllNodesWithTag("calibration-switch").fetchSemanticsNodes().isNotEmpty()};awaitChart();ui.waitForIdle()
        assertArrayEquals(before,ui.activity.lastPreview!!.values,0.0)
        ui.onNodeWithTag("calibration-switch").performScrollTo().performClick()
        awaitChart();ui.waitForIdle()
        ui.onNodeWithText(text(R.string.model_e2_sl)).assertDoesNotExist()
        openWindow()
        ui.onNodeWithText(text(R.string.model_e2_sl)).assertDoesNotExist()
        val note=ui.onNodeWithTag("research-not-personal").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
        assertFalse("M2 note cannot claim removed Legacy calibration cards",note.contains("Legacy",ignoreCase=true))
        closeWindow()
    }
    // UI-12 migration of originalChartTapZoomAndPanRemainOperable: same gestures; the readout is the floating card (chart-readout) instead of the "  ·  " bubble.
    @Test fun chartTapZoomAndPanRemainOperable() {
        awaitChart();val chart=ui.onNodeWithTag("e2-chart").performScrollTo()
        chart.performTouchInput{click(center)}
        awaitReadout()
        val before=readout()
        chart.performTouchInput{swipeLeft(startX=centerX+width*.25f,endX=centerX-width*.25f)}
        chart.performTouchInput{click(center)}
        ui.waitUntil(10_000){ui.onAllNodesWithTag("chart-readout").fetchSemanticsNodes().isNotEmpty() && readout()!=before}
        chart.performTouchInput{ val small=androidx.compose.ui.geometry.Offset(width*.05f,0f);val far=androidx.compose.ui.geometry.Offset(width*.3f,0f);pinch(center-small,center-far,center+small,center+far) }
        ui.waitForIdle()
        chart.performTouchInput{click(androidx.compose.ui.geometry.Offset(width*.37f,height*.5f))}
        awaitReadout()
        ui.onNodeWithTag("chart-readout").assertExists()
    }

    @Test fun failedM2NeverMasqueradesAsEmptyHistoryAndOtherMedicinesRemainAvailable() {
        awaitChart()
        ui.runOnUiThread { ui.activity.syntheticExtraRoute="SPI" }
        awaitChart();ui.onNodeWithText(text(R.string.pk_other_title)).assertExists()
        // UI-12: the population caveat moved from the other-medicine card to "Model and literature".
        ui.onNodeWithText(text(R.string.pk_disclaimer_short)).assertDoesNotExist()
        openWindow();ui.onNodeWithText(text(R.string.pk_disclaimer_short)).assertExists();closeWindow()
        ui.runOnUiThread { ui.activity.previewDelayMillis=1000L;ui.activity.previewFails=true;ui.activity.syntheticHistoryRevision++ }
        awaitTag("research-preview-loading");ui.onNodeWithText(text(R.string.pk_no_doses_title)).assertDoesNotExist()
        awaitTag("research-preview-error");ui.onNodeWithText(text(R.string.pk_no_doses_title)).assertDoesNotExist()
        ui.onNodeWithText(text(R.string.pk_other_title)).assertExists()
        ui.onAllNodesWithTag("research-peaks").assertCountEquals(0)
        ui.runOnUiThread { ui.activity.previewDelayMillis=0L;ui.activity.previewFails=false }
        ui.onNodeWithTag("m2-retry").performScrollTo().performClick();awaitChart()
        ui.onNodeWithTag("e2-estimate-card").assertExists()
    }
    @Test fun completedHistoryRefreshRetainsOriginalSelectedRange() {
        awaitChart();val seven=ui.activity.getString(R.string.days_short,7)
        ui.onNodeWithText(seven).performScrollTo().performClick();ui.onNodeWithText(seven).assertIsSelected()
        ui.runOnUiThread { ui.activity.previewDelayMillis=1000L;ui.activity.syntheticHistoryRevision++ }
        awaitTag("research-preview-loading");awaitChart()
        ui.onNodeWithText(seven).assertIsSelected();openBasis();ui.onNodeWithTag("research-peaks").assertExists();closeWindow()
    }
    @Test fun fixedM2AndOriginalUnitSurviveRecreation() {
        awaitChart();ui.onNodeWithText("pmol/L").performScrollTo().performClick()
        ui.activityRule.scenario.recreate();awaitChart();noSelector()
        ui.onNodeWithTag("e2-estimate-card").assertExists()
        // The selected unit itself must survive recreation.
        ui.onNode(hasText("pmol/L") and isSelected()).assertExists()
    }
    // UI-12 migration of mixedRoutesAreExplicitlyPartialAndNeverTotalE2 (the owner's decision supersedes "a combined total is unavailable"):
    //  kept: the sublingual part is still exactly the M2 kernel value at every moment, the retired Legacy calibration is not offered;
    //  superseded: with an oral, gel or patch route the page now shows ONE total (label "E2 total") that is larger than the sublingual part alone and no "partial" notice;
    //  unchanged: sublingual estradiol valerate is unsupported, stays an unsupported card, and adds nothing to the total.
    @Test fun mixedRoutesAreAddedIntoOneTotal() {
        awaitChart()
        for(route in listOf("ORAL","GEL","PATCH")) {
            ui.runOnUiThread { ui.activity.syntheticExtraRoute=route }
            ui.waitUntil(30_000){ui.onAllNodesWithTag("e2-label",useUnmergedTree=true).fetchSemanticsNodes().any{it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("")==text(R.string.ui12_label_total)}}
            awaitChart();ui.waitForIdle()
            assertEquals("the M2 part is untouched for $route",expected(),kotlin.math.round(scenarioNow()).toInt().toString())
            val shown=ui.onNodeWithTag("e2-now-value",useUnmergedTree=true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("").toInt()
            assertTrue("total ($shown) must exceed the sublingual part ($route)",shown>expected().toInt())
            ui.onAllNodesWithText(text(R.string.m2_partial_scope)).assertCountEquals(0)
            ui.onNodeWithText(text(R.string.pk_current)).assertDoesNotExist()
            ui.onNodeWithTag("calibration-switch").performScrollTo().assertIsEnabled()
        }
        ui.runOnUiThread { ui.activity.syntheticExtraRoute="EV" }
        ui.waitUntil(30_000){ui.onAllNodesWithText(text(R.string.unsupported_sl_ev)).fetchSemanticsNodes().isNotEmpty()}
        awaitChart();ui.waitForIdle()
        assertEquals(expected(),ui.onNodeWithTag("e2-now-value",useUnmergedTree=true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString(""))
    }
    @Test fun otherRouteOnlyRetainsCompatibilityCurveAndItsCalibration() {
        awaitChart();ui.runOnUiThread { ui.activity.syntheticSublingualEnabled=false;ui.activity.syntheticExtraRoute="ORAL" }
        ui.waitUntil(30_000){ui.onAllNodesWithTag("e2-label",useUnmergedTree=true).fetchSemanticsNodes().any{it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("")==ui.activity.getString(R.string.ui12_label_single,text(R.string.ui12_route_oral).replaceFirstChar{c->c.titlecase()})}}
        noSelector();ui.onNodeWithTag("m2-scenario-card").assertDoesNotExist()
        ui.onNodeWithText(text(R.string.calib_enable)).performScrollTo().assertExists()
        ui.onAllNodesWithTag("calibration-switch").assertCountEquals(0)
        openWindow();ui.onNodeWithTag("e2-compatibility-scope").assertExists();closeWindow()
    }
    @Test fun unknownHistoryNeverFallsBackToOldSublingualPrediction() {
        awaitChart();ui.runOnUiThread { ui.activity.syntheticSublingualEnabled=false;ui.activity.syntheticUnknownContext=true }
        awaitTag("m2-no-qualified-history");noSelector()
        ui.onNodeWithTag("e2-estimate-card").assertDoesNotExist()
        ui.onNodeWithText(text(R.string.pk_current)).assertDoesNotExist()
    }
    @Test fun wrongModelCannotBeRelabelledM2() {
        awaitChart();ui.runOnUiThread { ui.activity.syntheticWrongPreview=true;ui.activity.syntheticHistoryRevision++ }
        awaitTag("research-preview-error");ui.onNodeWithTag("e2-estimate-card").assertDoesNotExist()
        ui.onNodeWithText(text(R.string.pk_current)).assertDoesNotExist();noSelector()
    }
    @Test fun unsupportedSublingualEvIsNotM2OrEmptyRecordedHistory() {
        awaitChart();ui.runOnUiThread { ui.activity.syntheticSublingualEnabled=false;ui.activity.syntheticExtraRoute="EV" }
        ui.waitUntil(30_000){ui.onAllNodesWithText(text(R.string.unsupported_sl_ev)).fetchSemanticsNodes().isNotEmpty()}
        ui.waitForIdle();ui.onNodeWithTag("e2-estimate-card").assertDoesNotExist()
        ui.onNodeWithText(text(R.string.pk_no_doses_title)).assertDoesNotExist();noSelector()
    }

    // UI-12 migration of mixedE2WithoutScopeCannotAppearAsTotal: the "partial scope / no total" notice is gone with the decision to add routes up;
    // what remains true is that records with an unknown route, dose or time are named on the page and are not counted.
    @Test fun unknownRecordsAreNamedAndNotCounted() {
        awaitChart();ui.runOnUiThread { ui.activity.syntheticUnknownContext=true }
        awaitChart();ui.waitForIdle()
        ui.onNodeWithTag("e2-unknown-records").performScrollTo().assertExists()
        assertEquals(expected(),ui.onNodeWithTag("e2-now-value",useUnmergedTree=true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString(""))
        openBasis();ui.onNodeWithText(text(R.string.uc_original_assumptions)).assertExists();closeWindow()
    }

    @Test fun timeBaseStaysConsistentWhileThePageStaysOpen() {
        // D-1 / REQUIREMENTS §67: staying on the page past a minute refreshes the snapshot; curve, "now", readouts and
        // the shown calculation time come from one asOf, and the refresh never flashes the loading overlay.
        awaitChart();val first=ui.activity.lastPreview!!.asOfH!!
        openWindow() // UI-12: the calculation time is shown under "Model and literature"
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
