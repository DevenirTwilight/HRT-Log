package net.plainnotes.app.debug

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/**
 * Installs/launches ONLY the fullDebug research Activity.
 * Uses its built-in SYNTHETIC dose examples, never production HRT records.
 */
class ExperimentalSlComparisonAndroidTest {
    @get:Rule val ui = createAndroidComposeRule<ExperimentalSlComparisonActivity>()

    @Test fun standaloneResearchActivityRendersTwoRelativeCurvesFromSyntheticEvents() {
        ui.onNodeWithText("HRT Log · 实验药代对比 / PK Research Sandbox").assertExists()
        ui.onNodeWithText("计算虚构情景 · Render").performClick()
        ui.waitUntil(timeoutMillis = 20_000L) {
            ui.onAllNodes(hasText("● 旧版 SL 曲线 / Legacy relative")).fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithText("● 新版 M2 曲线 / Experimental relative").assertExists()
        ui.onNodeWithText("候选：", substring = true).assertExists()
    }
}
