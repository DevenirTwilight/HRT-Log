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

    /** PR-B: the whole synthetic flow on a native emulator, each step with a real screenshot. */
    @Test fun fullScriptedFlow() {
        fun action(tag: String) = inList("demo-detail", hasTestTag(tag))
        fun uuid(n: Int) = "00000000-0000-4000-8000-%012d".format(n)
        // Link a visit and two labs to the uncategorised inbox photo; the retired dataset cannot be linked.
        inList("demo-list", hasTestTag("demo-doc:${uuid(2)}")).performClick()
        action("demo-link-manage").performClick()
        for (k in listOf("VISIT:epoch-2026:7", "LAB:epoch-2026:12", "LAB:epoch-2026:13")) inList("demo-link-list", hasTestTag("demo-link-toggle:$k")).performClick()
        inList("demo-link-list", hasTestTag("demo-link-blocked:LAB:epoch-2025-retired:12")).assertIsDisplayed(); shot("10-link-dialog")
        ui.onNodeWithTag("demo-link-close").performClick()
        action("demo-link:LAB:epoch-2026:13").assertIsDisplayed(); shot("11-linked")
        action("demo-unlink:LAB:epoch-2026:13").performClick()
        ui.onAllNodesWithTag("demo-link:LAB:epoch-2026:13").assertCountEquals(0)
        // Edit with validation: an impossible date is refused and the draft is kept; then saved.
        action("demo-edit").performClick()
        ui.onNodeWithTag("demo-edit-title").performTextReplacement("Clinic handout, triaged (synthetic)")
        ui.onNodeWithTag("demo-edit-prec:DAY").performScrollTo().performClick()
        ui.onNodeWithTag("demo-edit-year").performScrollTo().performTextReplacement("2026")
        ui.onNodeWithTag("demo-edit-month").performScrollTo().performTextReplacement("2")
        ui.onNodeWithTag("demo-edit-day").performScrollTo().performTextReplacement("30")
        ui.onNodeWithTag("demo-edit-save").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        // The soft keyboard from the day field can cover the top of the dialog (CI run 38752825365), so check the error by
        // its text rather than its on-screen visibility; the form stays open and nothing was saved.
        ui.onNodeWithTag("demo-edit-error").assertTextEquals(s(R.string.demo_err_day)); ui.onNodeWithTag("demo-edit-form").assertExists(); shot("12-edit-error")
        ui.onNodeWithTag("demo-edit-day").performScrollTo().performTextReplacement("27")
        ui.onNodeWithTag("demo-edit-cat:LETTER").performScrollTo().performClick()
        ui.onNodeWithTag("demo-edit-save").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("demo-edit-form").fetchSemanticsNodes().isEmpty() }
        // Linking already took it out of the inbox, so there is nothing left to file.
        ui.onAllNodesWithTag("demo-file").assertCountEquals(0); shot("13-edited")
        // A correction adds v2 and keeps v1.
        action("demo-correct").performClick()
        ui.onNodeWithTag("demo-correct-note").performTextInput("Clearer photo of the same handout")
        ui.onNodeWithTag("demo-correct-save").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        action("demo-version:${uuid(202)}").assertIsDisplayed(); shot("14-correction")
        // Bin → restore → bin → permanent delete (two confirmations).
        action("demo-trash").performClick(); action("demo-restore").assertIsDisplayed(); shot("15-in-bin")
        action("demo-restore").performClick(); action("demo-trash").performClick()
        action("demo-delete").performClick(); ui.onNodeWithTag("demo-delete-confirm1").performClick(); shot("16-delete-second-confirm")
        ui.onNodeWithTag("demo-delete-confirm2").performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("demo-detail").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithTag("demo-tab-TRASH").performClick()
        ui.onAllNodesWithTag("demo-doc:${uuid(2)}").assertCountEquals(0)
        // Failure states stay visible and never open.
        ui.onNodeWithTag("demo-tab-ALL").performClick()
        inList("demo-list", hasTestTag("demo-doc:${uuid(5)}")).performClick()
        action("demo-status").assert(hasAnyDescendant(hasText(s(R.string.demo_status_corrupt)))); shot("17-corrupt")
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        // Reset Demo brings the fixture back.
        inList("demo-list", hasTestTag("demo-reset")).performClick(); shot("18-reset-confirm")
        ui.onNodeWithTag("demo-reset-confirm").performClick()
        ui.onNodeWithTag("demo-tab-INBOX").performClick()
        inList("demo-list", hasTestTag("demo-doc:${uuid(2)}")).assertIsDisplayed(); shot("19-after-reset")
    }

    /** Re-run by the capture script with the system in night mode, for dark-theme screenshots. */
    @Test fun overviewScreens() {
        banner(); shot("90-overview-inbox")
        ui.onNodeWithTag("demo-tab-ALL").performClick(); inList("demo-list", hasTestTag("demo-doc:00000000-0000-4000-8000-000000000001")).performClick()
        shot("91-overview-detail")
    }
}
