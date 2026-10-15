package net.plainnotes.app.demo

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.R
import net.plainnotes.app.debug.demo.MedicalArchiveDemoActivity
import org.junit.Rule
import org.junit.Test

/** MED-02B6 PR-B basic compatibility (run on API 26 and 29 in the attachment-poc job, and on API 35 with the rest):
 * the demo opens with its banner, a document opens, one explicit link, back, reset. Synthetic data only. */
class DemoCompatAndroidTest {
    @get:Rule val ui = createAndroidComposeRule<MedicalArchiveDemoActivity>()
    private fun inList(list: String, m: SemanticsMatcher): SemanticsNodeInteraction { ui.onNodeWithTag(list).performScrollToNode(m); return ui.onNode(m) }

    @Test fun opensLinksGoesBackAndResets() {
        ui.onNodeWithTag("demo-banner").assertIsDisplayed().assert(hasText(ui.activity.getString(R.string.demo_banner)))
        inList("demo-list", hasTestTag("demo-doc:00000000-0000-4000-8000-000000000002")).performClick()
        inList("demo-detail", hasTestTag("demo-link-manage")).performClick()
        inList("demo-link-list", hasTestTag("demo-link-toggle:VISIT:epoch-2026:7")).performClick()
        ui.onNodeWithTag("demo-link-close").performClick()
        inList("demo-detail", hasTestTag("demo-link:VISIT:epoch-2026:7")).assertIsDisplayed()
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("demo-detail").fetchSemanticsNodes().isEmpty() }
        inList("demo-list", hasTestTag("demo-reset")).performClick(); ui.onNodeWithTag("demo-reset-confirm").performClick()
        ui.onNodeWithTag("demo-list").assertExists()
    }
}
