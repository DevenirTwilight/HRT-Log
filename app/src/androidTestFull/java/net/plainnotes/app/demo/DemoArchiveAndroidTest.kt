package net.plainnotes.app.demo

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.app.R
import net.plainnotes.app.debug.demo.MedicalArchiveDemoActivity
import org.junit.Rule
import org.junit.Test
import java.io.File

/** MED-02B6 / PHR-DEMO-UI on a native emulator: the synthetic document library opens, Inbox → categories → search →
 * detail (versions, explicit links) → system back → bin, with the synthetic banner always visible. Each step saves a
 * real screenshot of the device (scripts/run_demo_capture.py pulls them into a CI artifact). Synthetic data only. */
class DemoArchiveAndroidTest {
    @get:Rule val ui = createAndroidComposeRule<MedicalArchiveDemoActivity>()
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private fun s(id: Int, vararg a: Any) = ui.activity.getString(id, *a)
    private fun inList(list: String, m: SemanticsMatcher): SemanticsNodeInteraction { ui.onNodeWithTag(list).performScrollToNode(m); return ui.onNode(m) }
    private fun shot(name: String) {
        ui.waitForIdle()
        val dir = File(ui.activity.filesDir, "demo-screens").apply { mkdirs() }
        val bitmap = instrument.uiAutomation.takeScreenshot() ?: error("No screenshot")
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun banner() = ui.onNodeWithTag("demo-banner").assertIsDisplayed().assert(hasText(s(R.string.demo_banner)))

    @Test fun inboxCategoriesSearchDetailBackAndBin() {
        banner(); ui.onNodeWithTag("demo-tab-INBOX").assertIsSelected()
        inList("demo-list", hasTestTag("demo-doc:00000000-0000-4000-8000-000000000002")).assertIsDisplayed()
        shot("01-inbox")
        ui.onNodeWithTag("demo-tab-CATEGORIES").performClick(); inList("demo-list", hasTestTag("demo-category:LAB_REPORT")).assertIsDisplayed(); shot("02-categories")
        ui.onNodeWithTag("demo-category:LAB_REPORT").performClick(); ui.onNodeWithTag("demo-category-clear").assertIsDisplayed(); shot("03-category-lab-reports")
        ui.onNodeWithTag("demo-tab-ALL").performClick()
        ui.onNodeWithTag("demo-search").performTextInput("Blood test")
        inList("demo-list", hasTestTag("demo-doc:00000000-0000-4000-8000-000000000001")).assertIsDisplayed(); shot("04-search")
        ui.onNodeWithTag("demo-doc:00000000-0000-4000-8000-000000000001").performClick()
        ui.onNodeWithTag("demo-detail").assertExists(); banner(); shot("05-detail")
        inList("demo-detail", hasText(s(R.string.demo_version_retained, 1))).assertIsDisplayed()
        inList("demo-detail", hasTestTag("demo-link:LAB:epoch-2026:13")).assertIsDisplayed(); shot("06-versions-and-links")
        // System Back through the activity's dispatcher (the demo's BackHandler). A hardware BACK key is not used: after typing
        // in the search field the first BACK may only close the soft keyboard (CI push run 38739412867), which is the IME's
        // behaviour, not the demo's.
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("demo-detail").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithTag("demo-detail").assertDoesNotExist(); ui.onNodeWithTag("demo-list").assertExists()
        ui.onNodeWithTag("demo-tab-TRASH").performClick()
        inList("demo-list", hasTestTag("demo-doc:00000000-0000-4000-8000-000000000004")).assertIsDisplayed(); shot("07-bin")
        ui.onNodeWithTag("demo-tab-INBOX").performClick()
        inList("demo-list", hasTestTag("demo-doc:00000000-0000-4000-8000-000000000007")).performClick()
        inList("demo-detail", hasTestTag("demo-status")).assert(hasAnyDescendant(hasText(s(R.string.demo_status_quarantined)))); shot("08-quarantined")
    }

    /** Re-run by the capture script with the system in night mode, for dark-theme screenshots. */
    @Test fun overviewScreens() {
        banner(); shot("90-overview-inbox")
        ui.onNodeWithTag("demo-tab-ALL").performClick(); inList("demo-list", hasTestTag("demo-doc:00000000-0000-4000-8000-000000000001")).performClick()
        shot("91-overview-detail")
    }
}
