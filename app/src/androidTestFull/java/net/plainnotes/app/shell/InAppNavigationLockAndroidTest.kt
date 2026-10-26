package net.plainnotes.app.shell

import android.content.Intent
import android.app.Activity
import android.app.Instrumentation
import java.util.concurrent.atomic.AtomicInteger
import androidx.test.espresso.Espresso
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import net.plainnotes.app.MainActivity
import net.plainnotes.app.R
import net.plainnotes.app.disguise.CalculatorActivity
import net.plainnotes.app.disguise.Disguise
import net.plainnotes.app.disguise.NotesShellActivity
import net.plainnotes.app.security.AppLock
import net.plainnotes.app.security.Session
import net.plainnotes.app.security.UnlockTarget
import net.plainnotes.app.ui.Destination
import net.plainnotes.app.ui.UiPrefs
import org.junit.*
import org.junit.Assert.*

/**
 * China beta P0-B (owner report 2026-10-18): after unlocking, moving between pages inside the app must not ask for the PIN
 * again, while every real leave/expiry still locks. Real MainActivity, real Keystore PIN, no health data (empty database).
 */
class InAppNavigationLockAndroidTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val pin = "2468"
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun setup() {
        Disguise.disable(context); Session.reset(); AppLock(context).setPin(pin)
        UiPrefs(context).lockAfterMillis = 30_000L; UiPrefs(context).disclaimerAccepted = true
    }
    @After fun cleanup() {
        scenario?.close()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach { it.finish() } }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Disguise.disable(context); AppLock(context).disable(); UiPrefs(context).lockAfterMillis = 30_000L; Session.reset()
    }

    private fun launch() = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).also { scenario = it; ui.waitForIdle() }
    private fun lockShown() = ui.onAllNodesWithText(context.getString(R.string.lock_unlock)).fetchSemanticsNodes().isNotEmpty()
    private fun assertUnlocked(step: String) { ui.waitForIdle(); assertFalse("PIN asked again after: $step", lockShown()) }
    private fun assertLocked(step: String) { ui.waitUntil(10_000) { lockShown() }; assertTrue(step, lockShown()) }
    private fun unlock() {
        assertLocked("cold start")
        pin.forEach { d -> ui.onNode(hasText(d.toString()) and hasClickAction()).performClick() }
        ui.onNodeWithText(context.getString(R.string.lock_unlock)).performClick()
        ui.waitUntil(10_000) { !lockShown() }
    }
    private fun open(destination: Destination) {
        ui.onNodeWithContentDescription(context.getString(R.string.menu)).performClick(); ui.waitForIdle()
        ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(context.getString(destination.title)))
        ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(context.getString(destination.title))).performClick()
        ui.waitForIdle()
    }
    private fun title() = ui.onNodeWithTag("top-title")

    /** The owner's exact path: unlock → calendar → stock → settings → history → calendar, without leaving the app. */
    @Test fun internalNavigationNeverAsksForThePinAgain() {
        launch(); unlock()
        listOf(Destination.STOCK, Destination.SETTINGS, Destination.HISTORY, Destination.CALENDAR).forEach { d ->
            open(d); assertUnlocked("open ${d.name}")
        }
        title().assertTextEquals(context.getString(R.string.app_name))
    }

    /** A configuration change (rotation, dark mode, font size, split screen) recreates the activity inside one session. */
    @Test fun configurationRecreationKeepsTheSessionAndPage() {
        launch(); unlock(); open(Destination.STOCK)
        scenario!!.recreate(); assertUnlocked("recreate on the stock page")
        title().assertTextEquals(context.getString(R.string.stock))
        open(Destination.SETTINGS); scenario!!.recreate(); assertUnlocked("recreate on settings")
        open(Destination.HISTORY); assertUnlocked("open history after recreation")
    }

    /** With auto-lock set to “immediately”, a configuration change is not leaving the app. */
    @Test fun immediateAutoLockDoesNotTreatRecreationAsLeaving() {
        UiPrefs(context).lockAfterMillis = 0L
        launch(); unlock(); open(Destination.STOCK)
        scenario!!.recreate(); assertUnlocked("recreate with immediate auto-lock")
    }

    /** Security boundary kept: leaving the screen with immediate auto-lock locks on return. */
    @Test fun leavingWithImmediateAutoLockStillLocks() {
        UiPrefs(context).lockAfterMillis = 0L
        launch(); unlock(); open(Destination.STOCK)
        scenario!!.moveToState(Lifecycle.State.CREATED); scenario!!.moveToState(Lifecycle.State.RESUMED)
        assertLocked("return after leaving with immediate auto-lock")
    }

    /** Security boundary kept: a recreation while in the background cannot reset the background timer. */
    @Test fun recreationWhileInBackgroundDoesNotSkipAutoLock() {
        UiPrefs(context).lockAfterMillis = 0L
        launch(); unlock()
        scenario!!.moveToState(Lifecycle.State.CREATED); scenario!!.recreate(); scenario!!.moveToState(Lifecycle.State.RESUMED)
        assertLocked("recreated in background, then returned")
    }

    /** LAB-03: populated real batch form survives activity recreation, but never crosses the lock boundary. */
    @Test fun populatedLabDraftSurvivesRotationAndIsDiscardedOnActualLock() {
        UiPrefs(context).lockAfterMillis = 0L
        launch(); unlock(); open(Destination.LABS)
        ui.onNodeWithTag("lab-open-batch").performClick()
        ui.onNodeWithTag("lab-batch-lab").performTextInput("SYNTHETIC rotation lab")
        Espresso.closeSoftKeyboard()
        ui.onNodeWithTag("lab-preset:CBC").performScrollTo().performClick()
        ui.onNodeWithTag("lab-preset:LIVER_EXTENDED").performScrollTo().performClick()
        assertEquals(24, ui.onAllNodes(SemanticsMatcher("lab rows") {
            it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith("lab-batch-row:") == true
        }).fetchSemanticsNodes().size)
        repeat(24) { i ->
            ui.onNodeWithTag("lab-batch-value:$i").performScrollTo().performTextInput((i + 1).toString())
        }
        Espresso.closeSoftKeyboard()
        ui.onNodeWithTag("lab-batch-save").assertIsEnabled()
        ui.onNodeWithTag("lab-batch-estimate").performScrollTo().assertIsNotSelected()
        scenario!!.recreate(); assertUnlocked("rotate populated lab draft")
        ui.onNodeWithTag("lab-batch").assertExists()
        ui.onNodeWithTag("lab-batch-lab").performScrollTo().assertTextContains("SYNTHETIC rotation lab")
        repeat(24) { i ->
            ui.onNodeWithTag("lab-batch-value:$i").performScrollTo().assert(SemanticsMatcher.expectValue(
                SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString((i + 1).toString())))
        }
        ui.onNodeWithTag("lab-batch-estimate").performScrollTo().assertIsNotSelected()
        // Actual orientation changes, separate from the explicit recreation above.
        listOf(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to android.content.res.Configuration.ORIENTATION_LANDSCAPE,
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to android.content.res.Configuration.ORIENTATION_PORTRAIT).forEach { (requested, expected) ->
            scenario!!.onActivity { it.requestedOrientation = requested }
            ui.waitUntil(15_000) { resumedMain()?.resources?.configuration?.orientation == expected }
            assertUnlocked("orientation $expected with populated lab draft")
            ui.onNodeWithTag("lab-batch").assertExists()
            ui.onNodeWithTag("lab-batch-value:23").performScrollTo().assert(SemanticsMatcher.expectValue(
                SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("24")))
        }
        scenario!!.moveToState(Lifecycle.State.CREATED)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        assertLocked("actual leave discards unsaved lab input")
        ui.onNodeWithTag("lab-batch").assertDoesNotExist()
        unlock(); open(Destination.LABS)
        ui.onNodeWithTag("lab-open-batch").performClick()
        ui.onNodeWithTag("lab-batch-lab").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        ui.onNodeWithTag("lab-batch-value:0").performScrollTo().assert(SemanticsMatcher.expectValue(
            SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        ui.onNodeWithTag("lab-batch-value:1").assertDoesNotExist()
        ui.onNodeWithTag("lab-batch-estimate").performScrollTo().assertIsNotSelected()
        ui.onNodeWithTag("lab-batch-cancel").performClick()
        ui.onNodeWithTag("lab-batch").assertDoesNotExist()
    }

    private fun back(dialogTag: String? = null) {
        // Composition does not prove that THIS dialog owns input focus: nested roots can
        // leave Espresso seeing the preceding focused window on API26. Observe the exact
        // tagged window before injecting one Back. Never retry a key or waive dismissal.
        if (dialogTag != null) {
            val view = (ui.onNodeWithTag(dialogTag).fetchSemanticsNode().root as ViewRootForTest).view
            ui.waitUntil(10_000) {
                var focused = false
                ui.runOnUiThread { focused = view.isAttachedToWindow && view.hasWindowFocus() }
                focused
            }
            // A visible IME consumes Back before a dialog does. This test checks dialog
            // dismissal, with the keyboard closed, rather than issuing a second Back.
            Espresso.closeSoftKeyboard()
        }
        Espresso.pressBack()
        ui.waitForIdle()
        if (dialogTag != null) {
            ui.waitUntil(10_000) { ui.onAllNodesWithTag(dialogTag).fetchSemanticsNodes().isEmpty() }
        }
    }

    /** Inspect the raw action: IntentFilter monitors also match actionless explicit launches and miss MIME types. */
    private class FileActivityMonitor : Instrumentation.ActivityMonitor() {
        val blocked = AtomicInteger(0)
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action !in actions) return null
            blocked.incrementAndGet()
            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }
        companion object {
            val actions = listOf(Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_GET_CONTENT, Intent.ACTION_CREATE_DOCUMENT)
        }
    }

    /** Positive control: real MIME-bearing file intents are intercepted, while explicit MainActivity still launches. */
    @Test fun fileIntentMonitorBlocksDocumentActionsWithoutBlockingTheApp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = FileActivityMonitor()
        instrumentation.addMonitor(monitor)
        try {
            launch(); unlock()
            FileActivityMonitor.actions.forEachIndexed { index, action ->
                scenario!!.onActivity { activity ->
                    @Suppress("DEPRECATION")
                    activity.startActivityForResult(Intent(action).setType("application/pdf")
                        .addCategory(Intent.CATEGORY_OPENABLE), 300 + index)
                }
                ui.waitUntil(5_000) { monitor.blocked.get() == index + 1 }
                assertUnlocked("blocked synthetic file intent $action")
            }
            assertEquals(3, monitor.blocked.get())
            assertNotNull("No external file activity replaces MainActivity", resumedMain())
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    /** Real dialog windows: Back dismisses only the top window, never launches an importer or asks for PIN. */
    @Test fun centerDialogsBackNavigationKeepsSessionAndNeverLaunchesFiles() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = FileActivityMonitor()
        instrumentation.addMonitor(monitor)
        try {
            launch(); unlock(); open(Destination.MEDICAL)
            listOf("files", "originals", "practitioners", "visit-links", "lab-links", "organize").forEach { suffix ->
                val tag = "medical-future-$suffix"
                ui.onNodeWithTag("medical-list").performScrollToNode(hasTestTag(tag))
                ui.onNodeWithTag(tag).performClick()
                ui.onNodeWithTag("center-unavailable-dialog").assertExists()
                back("center-unavailable-dialog")
                ui.onNodeWithTag("center-unavailable-dialog").assertDoesNotExist()
                ui.onNodeWithTag("medical-list").assertExists()
                assertUnlocked("Back from $tag")
            }
            open(Destination.MEDICATIONS)
            ui.onNodeWithTag("drug-center-open").performClick()
            ui.onNodeWithTag("drug-center-list").performScrollToNode(hasTestTag("drug-center-catalogue"))
            ui.onNodeWithTag("drug-center-catalogue").performClick()
            ui.onNodeWithTag("reference-window").assertExists()
            back("reference-window")
            ui.onNodeWithTag("reference-window").assertDoesNotExist()
            ui.onNodeWithTag("drug-center-window").assertExists()
            assertUnlocked("Back from offline catalogue")
            listOf("rcp", "notice", "safety", "supply", "recalls").forEach { suffix ->
                val tag = "drug-center-$suffix"
                ui.onNodeWithTag("drug-center-list").performScrollToNode(hasTestTag(tag))
                ui.onNodeWithTag(tag).performClick()
                ui.onNodeWithTag("center-unavailable-dialog").assertExists()
                back("center-unavailable-dialog")
                ui.onNodeWithTag("center-unavailable-dialog").assertDoesNotExist()
                ui.onNodeWithTag("drug-center-window").assertExists()
                assertUnlocked("Back from $tag")
            }
            ui.onNodeWithTag("drug-center-list").performScrollToNode(hasTestTag("drug-center-browse"))
            ui.onNodeWithTag("drug-center-browse").performClick()
            ui.onNodeWithTag("reference-screen").assertExists()
            back("reference-window")
            ui.onNodeWithTag("reference-window").assertDoesNotExist()
            ui.onNodeWithTag("drug-center-window").assertExists()
            back("drug-center-window")
            ui.onNodeWithTag("drug-center-window").assertDoesNotExist()
            title().assertTextEquals(context.getString(R.string.medications))
            assertUnlocked("Back to medication plans")
            assertEquals("Unavailable modules must never launch a file activity", 0, monitor.blocked.get())
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    /** Saved UI state survives rotation inside the unlocked session; it must not be shown over the lock screen. */
    @Test fun centerRecreationKeepsOpenWindowButActualLeaveStillLocks() {
        launch(); unlock(); open(Destination.MEDICATIONS)
        ui.onNodeWithTag("drug-center-open").performClick()
        scenario!!.recreate()
        assertUnlocked("recreate inside drug center")
        ui.onNodeWithTag("drug-center-window").assertExists()
        back("drug-center-window")
        ui.onNodeWithTag("drug-center-window").assertDoesNotExist()
        title().assertTextEquals(context.getString(R.string.medications))
        ui.onNodeWithTag("drug-center-open").performClick()
        UiPrefs(context).lockAfterMillis = 0L
        scenario!!.moveToState(Lifecycle.State.CREATED)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        assertLocked("actual leave from drug center with immediate auto-lock")
        ui.onNodeWithTag("drug-center-window").assertDoesNotExist()
    }

    /** LAB-UI-03: every real entry selects the same production homepage and retains the unlocked session. */
    @Test fun labsDrawerMedicalAndConcentrationShareNewHomeAndExistingBatchForm() {
        launch(); unlock(); open(Destination.LABS)
        ui.onNodeWithTag("labs-home").assertExists()
        ui.onNodeWithTag("labs-home-add").performClick()
        ui.onNodeWithTag("lab-batch").assertExists()
        ui.onNodeWithTag("lab-batch-estimate").assertIsNotSelected()
        back("lab-batch")
        ui.onNodeWithTag("lab-batch").assertDoesNotExist()
        ui.onNodeWithTag("labs-home").assertExists()
        assertUnlocked("cancel new home batch")
        open(Destination.MEDICAL)
        ui.onNodeWithTag("medical-list").performScrollToNode(hasTestTag("medical-labs"))
        ui.onNodeWithTag("medical-labs").performClick()
        ui.onNodeWithTag("labs-home").assertExists()
        assertUnlocked("medical center labs")
        back()
        ui.onNodeWithTag("medical-list").assertExists()
        open(Destination.CONCENTRATION)
        ui.onNodeWithText(context.getString(R.string.calib_manage_labs)).performScrollTo().performClick()
        ui.onNodeWithTag("labs-home").assertExists()
        scenario!!.recreate()
        ui.onNodeWithTag("labs-home").assertExists()
        assertUnlocked("labs rotation after concentration entry")
        open(Destination.CALENDAR)
        assertUnlocked("return to calendar from labs")
    }

    private fun resumedMain(): MainActivity? {
        var found: MainActivity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            found = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
        }
        return found
    }
    private fun resumedShell() = ActivityLifecycleMonitorRegistry.getInstance().let { m ->
        var found = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync { found = m.getActivitiesInStage(Stage.RESUMED).any { it is CalculatorActivity || it is NotesShellActivity } }
        found
    }
    /** Disguise: the shell code opens the app once; page changes and configuration changes keep that session. */
    private fun disguisedNavigation(shell: Disguise.Shell) {
        AppLock(context).disable(); Disguise.enable(context, shell, pin, null); Session.reset()
        val type = if (shell == Disguise.Shell.CALCULATOR) CalculatorActivity::class.java else NotesShellActivity::class.java
        ActivityScenario.launch<android.app.Activity>(Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        ui.waitUntil(20_000) { resumedShell() }; ui.waitForIdle()
        if (shell == Disguise.Shell.CALCULATOR) {
            pin.forEach { d -> ui.onNode(hasText(d.toString()) and hasClickAction()).performClick() }
            ui.onNodeWithText("=", useUnmergedTree = true).performClick()
        } else {
            ui.onNodeWithContentDescription(context.getString(R.string.shell_search)).performClick()
            val search = ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ImeAction, ImeAction.Search))
            search.performTextInput(pin); search.performImeAction()
        }
        ui.waitUntil(20_000) { resumedMain() != null }; ui.waitForIdle()
        listOf(Destination.STOCK, Destination.SETTINGS, Destination.HISTORY, Destination.CALENDAR).forEach { d ->
            open(d); assertNotNull("disguise session ended after opening ${d.name}", resumedMain()); assertEquals(UnlockTarget.PRIMARY, Session.target)
        }
        open(Destination.STOCK)
        val main = resumedMain()!!; InstrumentationRegistry.getInstrumentation().runOnMainSync { main.recreate() }
        ui.waitUntil(20_000) { resumedMain() != null }; ui.waitForIdle()
        assertEquals(UnlockTarget.PRIMARY, Session.target); title().assertTextEquals(context.getString(R.string.stock))
        // Leaving the screen still ends the disguise session and returns only to the shell.
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ui.waitUntil(10_000) { Session.target == null && resumedMain() == null }
        Disguise.disable(context)
    }
    @Test fun calculatorDisguiseNavigationAndRecreationKeepTheSession() = disguisedNavigation(Disguise.Shell.CALCULATOR)
    @Test fun notesDisguiseNavigationAndRecreationKeepTheSession() = disguisedNavigation(Disguise.Shell.NOTES)

    /** Security boundary kept: leaving within the 30 s grace keeps the session, finishing the activity does not. */
    @Test fun shortLeaveKeepsSessionButFinishingLocks() {
        launch(); unlock(); open(Destination.STOCK)
        scenario!!.moveToState(Lifecycle.State.CREATED); scenario!!.moveToState(Lifecycle.State.RESUMED); assertUnlocked("short leave within 30 s")
        scenario!!.close(); scenario = null
        launch(); assertLocked("new launch after the activity finished")
    }
    @Test fun realActivityPauseAndRecreationHideSensitivePresentation() {
        val activity=launch();unlock()
        val hidden=AtomicInteger()
        var registration:AutoCloseable?=null
        activity.onActivity {registration=androidx.lifecycle.ViewModelProvider(it)[net.plainnotes.app.NotesViewModel::class.java].libraryRuntime.observePresentationHidden {hidden.incrementAndGet()}}
        try {
            activity.moveToState(Lifecycle.State.CREATED)
            assertTrue(hidden.get()>0)
            activity.moveToState(Lifecycle.State.RESUMED)
            val before=hidden.get();activity.recreate()
            assertTrue(hidden.get()>before)
        } finally {registration?.close()}
    }

}
