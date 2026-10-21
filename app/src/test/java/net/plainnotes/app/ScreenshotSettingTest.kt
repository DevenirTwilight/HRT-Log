package net.plainnotes.app

import android.content.ComponentName
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.security.ScreenCapture
import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * REQUIREMENTS §135: "Allow screenshots" in Settings → Privacy & security. Off by default (FLAG_SECURE, as before); turning it
 * on clears FLAG_SECURE on the current window at once and is remembered; turning it off sets it again. In disguise mode the
 * switch works the same inside the real app, with a note on the recents preview. Four languages.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk = [35])
class ScreenshotSettingTest(private val locale: String) {
    companion object { @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}") fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr").map { arrayOf<Any>(it) } }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ui.activity
    private fun s(id: Int) = context.getString(id)
    private fun secure() = context.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
    private fun show() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h1800dp-xxhdpi"); RuntimeEnvironment.setFontScale(2f)
        // As MainActivity does on creation: secure unless allowed.
        ui.runOnUiThread { ScreenCapture.apply(context, UiPrefs(context).allowScreenshots, net.plainnotes.app.disguise.Disguise.enabled(context)) }
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { androidx.compose.material3.Surface { Column(Modifier.verticalScroll(rememberScrollState())) { PrivacySection(1, false) {} } } } }
        ui.waitForIdle()
    }
    private fun row() = ui.onNodeWithTag("privacy-screenshots")

    @Test fun offByDefaultThenOnAndOffApplyAtOnceAndAreRemembered() {
        assertFalse("default: not allowed", UiPrefs(context).allowScreenshots)
        show()
        assertTrue("default keeps FLAG_SECURE", secure())
        row().performScrollTo().assertIsDisplayed().assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            .assert(hasText(s(R.string.screenshots_allow), substring = true)).assert(hasText(s(R.string.screenshots_allow_desc), substring = true))
        val b = row().fetchSemanticsNode().boundsInRoot; assertTrue(b.height / context.resources.displayMetrics.density >= 47.5f)
        row().performClick(); ui.waitForIdle()
        assertTrue(UiPrefs(context).allowScreenshots); assertFalse("allowed: FLAG_SECURE cleared at once", secure())
        row().assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        row().performClick(); ui.waitForIdle()
        assertFalse(UiPrefs(context).allowScreenshots); assertTrue("blocked again", secure())
    }

    @Test fun disguiseModeFollowsTheSettingInsideTheApp() {
        context.packageManager.setComponentEnabledSetting(ComponentName(context.packageName, "net.plainnotes.app.CalculatorLauncher"),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        try {
            show()
            assertTrue("disguised, default: blocked", secure())
            // The switch works in disguise mode too; the note says what happens to the recents preview (Android 15 here: hidden).
            row().performScrollTo().assertIsEnabled().assert(hasText(s(R.string.screenshots_allow_desc), substring = true))
            ui.onNodeWithTag("privacy-screenshots-disguise", useUnmergedTree = true).assertExists()
            row().assert(hasText(s(R.string.screenshots_disguise_preview_hidden), substring = true))
            row().performClick(); ui.waitForIdle()
            assertTrue(UiPrefs(context).allowScreenshots); assertFalse("disguised, allowed: FLAG_SECURE cleared", secure())
            row().performClick(); ui.waitForIdle()
            assertTrue("disguised, turned off: blocked", secure())
        } finally {
            context.packageManager.setComponentEnabledSetting(ComponentName(context.packageName, "net.plainnotes.app.CalculatorLauncher"),
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
        }
    }

    @Test fun noDisguiseNoteWithoutDisguise() {
        show(); row().performScrollTo()
        ui.onNodeWithTag("privacy-screenshots-disguise", useUnmergedTree = true).assertDoesNotExist()
    }
}

/** §135: the rule itself. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class ScreenCaptureRuleTest {
    @Test fun blockedUnlessAllowed() {
        assertTrue(ScreenCapture.blocked(allowed = false)); assertFalse(ScreenCapture.blocked(allowed = true))
    }

    @Test fun recentsPreviewInDisguiseMode() {
        // Android 13+: allowed screenshots in disguise mode still hide the recents preview.
        assertTrue(ScreenCapture.hideRecentsPreview(allowed = true, disguised = true, sdk = 33))
        assertFalse(ScreenCapture.hideRecentsPreview(allowed = true, disguised = false, sdk = 35))
        assertFalse("blocked anyway by FLAG_SECURE", ScreenCapture.hideRecentsPreview(allowed = false, disguised = true, sdk = 35))
        // Below 13 there is no separate switch: the preview is exposed only when allowed and disguised.
        assertTrue(ScreenCapture.previewExposed(allowed = true, disguised = true, sdk = 32))
        assertFalse(ScreenCapture.previewExposed(allowed = true, disguised = true, sdk = 33))
        assertFalse(ScreenCapture.previewExposed(allowed = false, disguised = true, sdk = 26))
        assertFalse(ScreenCapture.previewExposed(allowed = true, disguised = false, sdk = 26))
    }
}

/** §135: the disguise's private notes screen follows the same setting (owner, 2026-10-21); secure by default. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class PrivateNotesScreenshotTest {
    private fun secureWith(allowed: Boolean): Boolean {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        UiPrefs(app).allowScreenshots = allowed
        val a = org.robolectric.Robolectric.buildActivity(net.plainnotes.app.disguise.privatenotes.PrivateNotesActivity::class.java).create().get()
        return a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
    }
    @Test fun defaultBlockedAllowedFollows() {
        assertTrue("default: blocked", secureWith(false))
        assertFalse("allowed: FLAG_SECURE cleared", secureWith(true))
        assertTrue("off again: blocked", secureWith(false))
    }
}
