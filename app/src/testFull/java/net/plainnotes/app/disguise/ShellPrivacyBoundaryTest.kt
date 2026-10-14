package net.plainnotes.app.disguise

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** PRIVACY-P0: the privacy notice (which names HRT Log and medical data) lives only in the unlocked app's About
 * screen. The disguise shells never render it, its entry point or its wording. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class ShellPrivacyBoundaryTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val noticeTexts get() = listOf(R.string.privacy_notice_title, R.string.privacy_notice_open, R.string.privacy_notice_backup_title)
        .map { ui.activity.getString(it) } + ui.activity.getString(R.string.app_name)

    private fun assertNoNotice() {
        for (tag in listOf("privacy-notice", "privacy-notice-open")) ui.onAllNodesWithTag(tag, useUnmergedTree = true).assertCountEquals(0)
        for (text in noticeTexts) ui.onAllNodesWithText(text, substring = true, useUnmergedTree = true).assertCountEquals(0)
    }
    @Test fun calculatorShellShowsNoPrivacyNoticeOrRealName() { ui.setContent { ShellTheme { CalculatorScreen {} } }; assertNoNotice() }
    @Test fun notesShellShowsNoPrivacyNoticeOrRealName() { ui.setContent { ShellTheme { NotesShellScreen("", {}) { _, _ -> } } }; assertNoNotice() }
    @Test fun shellSourcesNeverReferenceTheNotice() {
        val shells = File("src/full/java/net/plainnotes/app/disguise/Shells.kt").readText()
        assertFalse(shells.contains("PrivacyNotice") || shells.contains("privacy_notice") || shells.contains("AboutScreen"))
    }
}
