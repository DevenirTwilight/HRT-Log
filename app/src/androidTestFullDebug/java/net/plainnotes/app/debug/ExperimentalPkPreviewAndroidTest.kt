package net.plainnotes.app.debug

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import net.plainnotes.app.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** fullDebug only: the real unified comparison on the emulator, fed with synthetic records (one oral dose must be ignored). */
class ExperimentalPkPreviewAndroidTest {
    @get:Rule val ui = createAndroidComposeRule<ExperimentalPkPreviewActivity>()

    @Test fun comparisonReadsBothModelsAndIgnoresOralEstradiol() {
        ui.onNodeWithTag("xpk-mode-COMPARISON").performSemanticsAction(SemanticsActions.OnClick)
        ui.waitUntil(20_000) { ui.onAllNodes(hasTestTag("xpk-chart")).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("xpk-legend-legacy").assertExists()
        ui.onNodeWithTag("xpk-legend-m2").assertExists()
        ui.onNodeWithTag("xpk-chart").performTouchInput { click(center) }
        ui.waitUntil(10_000) { ui.onAllNodes(hasTestTag("xpk-readout-m2")).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("xpk-readout-legacy").assertExists()
        val excluded = ui.activity.getString(R.string.xpk_inputs_excluded, ui.activity.getString(R.string.xpk_excl_not_sl_e2, 1))
        ui.onNodeWithTag("experimental-pk").performScrollToNode(hasText(excluded))
        ui.onNodeWithTag("xpk-coverage-mixed").assertExists()
        assertEquals(0, ui.onAllNodes(hasText("pg/mL", substring = true, ignoreCase = true)).fetchSemanticsNodes().size)
        ui.onNodeWithTag("experimental-pk").performScrollToNode(hasTestTag("xpk-back"))
        ui.onNodeWithTag("xpk-back").performClick()
        ui.waitUntil(10_000) { ui.activity.isFinishing }
    }
}
