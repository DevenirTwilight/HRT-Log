package net.plainnotes.app.privacy

import android.app.Activity
import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import net.plainnotes.app.MainActivity
import net.plainnotes.app.R
import net.plainnotes.app.debug.AboutTestActivity
import net.plainnotes.app.disguise.CalculatorActivity
import net.plainnotes.app.disguise.Disguise
import net.plainnotes.app.security.AppLock
import net.plainnotes.app.security.Session
import net.plainnotes.app.ui.PRIVACY_NOTICE_SECTIONS
import org.junit.*
import org.junit.Assert.*

/** PRIVACY-P1, native emulator (CI device-tests, API 35): the offline privacy notice opens from the production About
 * screen, every section is reachable, it survives activity recreation (rotation) and closes with the system back key. */
class PrivacyNoticeDeviceAndroidTest {
    @get:Rule val ui = createAndroidComposeRule<AboutTestActivity>()
    private fun text(id: Int) = ui.activity.getString(id)
    private val inNotice = hasAnyAncestor(hasTestTag("privacy-notice"))

    @Test fun noticeOpensReadsSurvivesRecreationAndClosesWithBack() {
        ui.onNodeWithTag("privacy-notice-open").performScrollTo().assertIsDisplayed().performClick()
        ui.onNodeWithTag("privacy-notice").assertExists()
        assertEquals(1 + PRIVACY_NOTICE_SECTIONS.size,
            ui.onAllNodes(inNotice and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).fetchSemanticsNodes().size)
        PRIVACY_NOTICE_SECTIONS.forEachIndexed { i, (title, _) ->
            ui.onNodeWithTag("privacy-section-$i").performScrollTo().assertIsDisplayed().assert(hasAnyDescendant(hasText(text(title))))
        }
        val density = ui.activity.resources.displayMetrics.density
        assertTrue(ui.onNodeWithTag("privacy-notice-close").fetchSemanticsNode().boundsInRoot.height >= 48 * density - 1)

        ui.activityRule.scenario.recreate(); ui.waitForIdle()
        ui.onNodeWithTag("privacy-notice").assertExists()                                    // rememberSaveable across recreation

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); ui.waitForIdle()
        ui.onNodeWithTag("privacy-notice").assertDoesNotExist()
        ui.onNodeWithTag("privacy-notice-open").assertExists()                               // back closed the notice, not the screen

        ui.onNodeWithTag("privacy-notice-open").performScrollTo().performClick()
        ui.onNodeWithTag("privacy-notice-close").performClick(); ui.waitForIdle()
        ui.onNodeWithTag("privacy-notice").assertDoesNotExist()
    }
}

/** PRIVACY-P1, native emulator: neither the PIN lock screen nor a disguise shell shows the privacy notice, its entry point
 * or the real app name; only the unlocked real app reaches About. */
class PrivacyNoticeLockAndDisguiseAndroidTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrument.targetContext
    @Before fun setup() { Disguise.disable(context); AppLock(context).disable(); Session.reset() }
    @After fun cleanup() {
        instrument.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach { it.finish() } }
        instrument.waitForIdleSync(); Disguise.disable(context); AppLock(context).disable(); Session.reset()
    }
    private fun launch(type: Class<out Activity>) = ActivityScenario.launch<Activity>(Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    private fun resumed(type: Class<out Activity>): Boolean {
        var found = false
        instrument.runOnMainSync { found = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { type.isInstance(it) } }
        return found
    }
    private fun await(type: Class<out Activity>) { ui.waitUntil(20_000) { resumed(type) }; ui.waitForIdle() }
    private fun assertNoNotice() {
        listOf("privacy-notice", "privacy-notice-open").forEach { ui.onAllNodesWithTag(it, useUnmergedTree = true).assertCountEquals(0) }
        listOf(R.string.privacy_notice_title, R.string.privacy_notice_open).forEach {
            ui.onAllNodesWithText(context.getString(it), substring = true, useUnmergedTree = true).assertCountEquals(0) }
    }

    @Test fun lockScreenShowsNoPrivacyNotice() {
        AppLock(context).setPin("8642")
        launch(MainActivity::class.java); await(MainActivity::class.java)
        ui.onNodeWithText(context.getString(R.string.lock_title)).assertIsDisplayed()
        assertNoNotice()
    }

    @Test fun disguiseShellShowsNoPrivacyNoticeOrRealName() {
        Disguise.enable(context, Disguise.Shell.CALCULATOR, "2468", null); Session.reset()
        launch(MainActivity::class.java); await(CalculatorActivity::class.java)
        assertNoNotice()
        ui.onAllNodesWithText(context.getString(R.string.app_name), substring = true, useUnmergedTree = true).assertCountEquals(0)
    }
}
