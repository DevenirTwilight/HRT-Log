package net.plainnotes.app.debug

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Real Android UI instrumentation of the shared production component; synthetic fixtures only. */
class UnifiedConcentrationAndroidTest {
    @get:Rule val ui=createAndroidComposeRule<UnifiedConcentrationTestActivity>()
    private fun await(tag:String)=ui.waitUntil(30_000){ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    @Test fun defaultM2OverlayUnitAndStudyAreDistinct() {
        ui.onNodeWithTag("model-M2").assertIsSelected();await("unified-chart")
        ui.onNodeWithTag("research-not-personal").assertExists()
        ui.onNodeWithTag("model-overlay").performClick()
        ui.onNodeWithTag("unified-chart").performScrollTo().performTouchInput { click(center) }
        await("readout-M1");ui.onNodeWithTag("readout-LEGACY").assertExists();ui.onNodeWithTag("difference-M1").assertExists()
        ui.onNodeWithTag("unit-pmol").performScrollTo().performClick();ui.onNodeWithTag("chart-unit").assertTextEquals("pmol/L")
        ui.onNodeWithTag("display-STUDY").performScrollTo().performClick();await("study-not-personal")
        ui.onNodeWithTag("tail-warning").assertExists()
    }
    @Test fun modelAndAssumptionsSurviveActivityRecreation() {
        await("unified-chart");ui.onNodeWithTag("model-M1").performClick()
        ui.onNodeWithTag("amplitude").performScrollTo().performTextReplacement("200")
        ui.onNodeWithTag("unit-pmol").performScrollTo().performClick()
        ui.activityRule.scenario.recreate();await("unified-chart")
        ui.onNodeWithTag("model-M1").assertIsSelected();ui.onNodeWithTag("unit-pmol").assertIsSelected()
        ui.onNodeWithTag("amplitude").assertTextEquals("200")
    }
}
