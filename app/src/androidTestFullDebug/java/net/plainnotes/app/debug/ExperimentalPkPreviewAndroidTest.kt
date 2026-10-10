package net.plainnotes.app.debug

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import net.plainnotes.app.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** fullDebug only: the real experimental page on the emulator, fed with synthetic records. */
class ExperimentalPkPreviewAndroidTest {
    @get:Rule val ui = createAndroidComposeRule<ExperimentalPkPreviewActivity>()

    @Test fun realPageSwitchesModesAndIgnoresOralEstradiol() {
        ui.waitUntil(20_000) { ui.onAllNodes(hasTestTag("xpk-chart")).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("xpk-mode-COMPARISON").performClick()
        ui.onNodeWithTag("xpk-legend-legacy").assertExists()
        ui.onNodeWithTag("xpk-legend-m2").assertExists()
        val used = ui.activity.getString(R.string.xpk_inputs_used, 6, 0)
        ui.onNodeWithTag("experimental-pk").performScrollToNode(hasText(used))
        val excluded = ui.activity.getString(R.string.xpk_inputs_excluded, ui.activity.getString(R.string.xpk_excl_not_sl_e2, 1))
        ui.onNodeWithTag("experimental-pk").performScrollToNode(hasText(excluded))
        assertEquals(0, ui.onAllNodes(hasText("pg/mL", substring = true, ignoreCase = true)).fetchSemanticsNodes().size)
        ui.onNodeWithTag("experimental-pk").performScrollToNode(hasTestTag("xpk-back"))
        ui.onNodeWithTag("xpk-back").performClick()
        ui.waitUntil(10_000) { ui.activity.isFinishing }
    }
}
