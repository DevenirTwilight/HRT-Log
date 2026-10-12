package net.plainnotes.app

import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import net.plainnotes.app.ui.AboutScreen
import net.plainnotes.app.ui.SourceIdentity
import net.plainnotes.app.ui.SourceIdentitySection
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Audit B1 / owner decision 2026-10-12: About shows the full source commit, its commit time and origin, and can copy them. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class)
class SourceIdentityUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val sha = "0123456789abcdef0123456789abcdef01234567"
    private val clean = SourceIdentity(sha, "2026-10-12T13:37:51Z", "git", false, "0.2.0", 25)
    private fun show(identity: SourceIdentity) = ui.setContent { MaterialTheme { SourceIdentitySection(identity) } }
    private fun text(tag: String) = ui.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
    private fun s(id: Int) = ui.activity.getString(id)

    @Test fun showsFullShaCommitTimeAndTrustedOrigin() {
        show(clean)
        assertEquals(sha, text("about-source-sha"))
        assertEquals("2026-10-12 13:37:51", text("about-source-time"))
        assertEquals(s(R.string.about_source_origin_git), text("about-source-origin"))
        ui.onAllNodesWithTag("about-source-dirty").assertCountEquals(0)
    }

    @Test fun dirtyDeclaredAndUnknownBuildsAreDistinguishable() {
        var current by mutableStateOf(clean.copy(dirty = true))
        ui.setContent { MaterialTheme { SourceIdentitySection(current) } }
        ui.onNodeWithTag("about-source-dirty", useUnmergedTree = true).assertIsDisplayed().assertTextEquals(s(R.string.about_source_dirty))
        current = clean.copy(origin = "declared-unverified"); ui.waitForIdle()
        assertEquals(s(R.string.about_source_origin_declared), text("about-source-origin"))
        current = SourceIdentity("unknown", "unknown", "unknown", false, "0.2.0", 25); ui.waitForIdle()
        assertEquals(s(R.string.about_source_unknown), text("about-source-sha"))
        assertEquals(s(R.string.about_source_unknown), text("about-source-time"))
        assertEquals(s(R.string.about_source_origin_unknown), text("about-source-origin"))
        // An unknown build must never be presented as read from git.
        assertNotEquals(s(R.string.about_source_origin_git), text("about-source-origin"))
    }

    @Test fun copyPutsMachineReadableIdentityOnTheClipboardWithoutTheAppName() {
        show(clean.copy(dirty = true))
        ui.onNode(hasClickAction() and hasText(s(R.string.about_source_copy))).assertIsDisplayed().performClick()
        val clip = ui.activity.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
        assertEquals("version_name=0.2.0\nversion_code=25\nsource_sha=$sha\nsource_commit_time_utc=2026-10-12T13:37:51Z\nsource_origin=git\nsource_dirty=true", clip)
        assertFalse(clip.contains(s(R.string.app_name)))
        ui.onNode(hasClickAction() and hasText(s(R.string.about_source_copied))).assertIsDisplayed()
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE) @Config(qualifiers = "w320dp-h480dp", fontScale = 2.0f) fun fullShaWrapsWithoutClippingOnNarrowLargeText() {
        show(clean)
        assertEquals(sha, text("about-source-sha"))
        val layouts = mutableListOf<TextLayoutResult>()
        ui.onNodeWithTag("about-source-sha", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertFalse("SHA must not be cut off", layouts.single().hasVisualOverflow)
        assertTrue("SHA wraps at 320dp and 2x text", layouts.single().lineCount > 1)
    }

    @Test @Config(qualifiers = "zh") fun labelsAreLocalized() {
        show(clean)
        ui.onNodeWithText("构建来源").assertExists()
        ui.onNodeWithText("源码提交").assertExists()
        ui.onNodeWithText("复制构建来源").assertExists()
    }

    @Test fun aboutScreenShowsTheCompiledIdentity() {
        ui.setContent { MaterialTheme { AboutScreen(PaddingValues()) } }
        ui.onNodeWithTag("about-source").performScrollTo()
        val expected = if (BuildConfig.SOURCE_SHA == "unknown") s(R.string.about_source_unknown) else BuildConfig.SOURCE_SHA
        assertEquals(expected, text("about-source-sha"))
        assertEquals(BuildConfig.SOURCE_DIRTY, ui.onAllNodesWithTag("about-source-dirty", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        val code = ui.activity.packageManager.getPackageInfo(ui.activity.packageName, 0).longVersionCode
        assertEquals(code, SourceIdentity.current(ui.activity).versionCode)
    }
}
