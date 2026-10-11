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

/** The real original ConcentrationScreen and ConcChart, not a placeholder Legacy host. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class UnifiedConcentrationUiTest {
    @get:Rule val ui=createAndroidComposeRule<UnifiedConcentrationTestActivity>()
    private fun awaitChart()=ui.waitUntil(30_000){ui.onAllNodesWithTag("unified-chart").fetchSemanticsNodes().isNotEmpty()}
    private fun text(id:Int)=ui.activity.getString(id)
    @Test fun originalCardsAndCalendarControlsRemainWithOnlyModelSelectorAdded() {
        awaitChart();ui.onNodeWithTag("model-M2").assertIsSelected()
        ui.onNodeWithText(text(R.string.uc_original_scenario)).assertExists()
        ui.onNodeWithText(text(R.string.pk_chart_title)).assertExists()
        for(days in listOf(7,14,60)) ui.onNodeWithText(ui.activity.getString(R.string.days_short,days)).assertExists()
        for(id in listOf(R.string.calib_title,R.string.pk_model_title,R.string.chart_full_band,R.string.pk_chart_hint)) ui.onNodeWithText(text(id)).assertExists()
        for(tag in listOf("model-overlay","research-settings-card","scenario-concentration-card","display-STUDY","amplitude")) ui.onAllNodesWithTag(tag).assertCountEquals(0)
        ui.onNodeWithTag("research-not-personal").assertExists()
        ui.onNodeWithText(text(R.string.uc_original_assumptions)).assertExists()
        ui.onNodeWithText(text(R.string.legend_band_outer)).assertDoesNotExist()
    }
    @Test fun selectedKernelChangesOriginalHeroAndLegacyRestoresOriginalLabels() {
        awaitChart()
        // Synthetic history: doses at -30/-20/-3 h; compute against the same frozen candidates.
        val doses=listOf(ExperimentalDose(-30.0,1.0),ExperimentalDose(-20.0,1.0),ExperimentalDose(-3.0,1.0))
        fun expected(c:ExperimentalCandidate)=(25.0+100.0*ResearchSublingualV01.relativeHistory(0.0,doses,c)).let{kotlin.math.round(it).toInt()}.toString()
        ui.onNodeWithText(expected(ResearchSublingualV01.candidates.first())).assertExists()
        ui.onNodeWithTag("model-M1").performScrollTo().performClick();awaitChart()
        ui.waitUntil(10_000){ui.onAllNodesWithText(expected(ResearchM1Catalog.candidates.first())).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("model-LEGACY").performScrollTo().performClick()
        ui.onNodeWithText(text(R.string.pk_current)).assertExists()
        ui.onNodeWithText(text(R.string.uc_original_scenario)).assertDoesNotExist()
        ui.onNodeWithText(text(R.string.legend_band_outer)).assertExists()
        ui.onNodeWithTag("concentration-legacy").assertExists()
    }
    @Test fun originalUnitSwitchConvertsResearchValue() {
        awaitChart();ui.onNodeWithText("pmol/L").performScrollTo().performClick()
        val doses=listOf(ExperimentalDose(-30.0,1.0),ExperimentalDose(-20.0,1.0),ExperimentalDose(-3.0,1.0))
        val expected=kotlin.math.round((25.0+100.0*ResearchSublingualV01.relativeHistory(0.0,doses,ResearchSublingualV01.candidates.first()))*3.671).toInt().toString()
        ui.onNodeWithText(expected).assertExists()
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
        chart.performTouchInput{click(center)}
        ui.onNode(hasText("  ·  ",substring=true)).assertExists()
    }
    @Test fun selectedModelAndOriginalUnitSurviveRecreation() {
        awaitChart();ui.onNodeWithTag("model-M1").performScrollTo().performClick();awaitChart()
        ui.onNodeWithText("pmol/L").performScrollTo().performClick()
        ui.activityRule.scenario.recreate();awaitChart()
        ui.onNodeWithTag("model-M1").assertIsSelected()
        ui.onNodeWithText(text(R.string.uc_original_assumptions)).assertExists()
        assertTrue(ui.onAllNodesWithText("pmol/L").fetchSemanticsNodes().size>=2)
    }
}
