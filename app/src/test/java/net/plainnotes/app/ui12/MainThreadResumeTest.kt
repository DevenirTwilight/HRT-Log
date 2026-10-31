package net.plainnotes.app.ui12

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.plainnotes.app.pk.CalibrationMode
import net.plainnotes.app.ui.ConcSettings
import net.plainnotes.app.ui.ConcentrationScreen
import net.plainnotes.app.ui.NotesTheme
import net.plainnotes.app.ui.computeOffMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Collections

/**
 * REQUIREMENTS §176 (2026-10-30): composition coroutines must come back to the main thread after background work. The Compose test
 * environment resumes them on whichever thread finished the work, and its interceptor then sends snapshot apply notifications from
 * there, so recomposition and layout ran on a worker: the intermittent CI failures. These tests show the mechanism, prove the helper
 * fixes it, check the concentration page (the real offender) never applies state off the main thread, and keep bare background hops
 * out of composable source files.
 */
@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class MainThreadResumeTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val main: Thread get() = Looper.getMainLooper().thread

    @Test fun aBareBackgroundHopResumesOffMainInComposeTestsButTheHelperAlwaysReturnsToMain() {
        val after = Collections.synchronizedMap(HashMap<String, Thread>())
        ui.setContent { LaunchedEffect(Unit) {
            withContext(Dispatchers.Default) { Thread.sleep(20) }; after["bare"] = Thread.currentThread()
            computeOffMain { Thread.sleep(20) }; after["helper"] = Thread.currentThread()
        } }
        // waitUntil only advances the frame clock; idle the main looper too so the helper's main-thread continuation can run.
        ui.waitUntil(15_000) { ui.waitForIdle(); after.size == 2 }
        // The mechanism behind the flakes: without the helper the composition coroutine continues on the worker.
        assertNotSame("bare withContext resumed on ${after["bare"]?.name}", main, after["bare"])
        assertSame("computeOffMain resumed on ${after["helper"]?.name}", main, after["helper"])
    }

    @Test fun theConcentrationPageNeverAppliesStateOffTheMainThread() {
        RuntimeEnvironment.setQualifiers("en-w411dp-h891dp-xxhdpi")
        val offMain = Collections.synchronizedList(ArrayList<String>())
        val handle = Snapshot.registerApplyObserver { _, _ -> if (Thread.currentThread() !== main) offMain += Thread.currentThread().name }
        try {
            val sc = Ui12Fixture.sublingualOnly()
            ui.setContent { NotesTheme {
                ConcentrationScreen(sc.state, sc.result, false, 70.0, ConcSettings(false, true, CalibrationMode.RETROSPECTIVE),
                    {}, {}, {}, {}, PaddingValues(), sc.records, sc.state.profiles,
                    preview = sc.preview, e2NonSublingual = sc.nonSublingual, doseTimes = sc.doseTimes, labs = sc.labs)
            } }
            // The calibrated total is built off the main thread; wait for it, then read the curve by tapping (also built off main).
            ui.waitUntil(30_000) { ui.onAllNodesWithTag("calibration-downweighted").fetchSemanticsNodes().isNotEmpty() ||
                ui.onAllNodesWithTag("calibration-refused").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("e2-chart").performScrollTo()
            ui.onNodeWithTag("e2-chart").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.62f, height * 0.4f)) }
            ui.waitUntil(15_000) { ui.onAllNodesWithTag("chart-readout").fetchSemanticsNodes().isNotEmpty() }
            ui.waitForIdle()
            assertEquals("state applied off the main thread: $offMain", emptyList<String>(), offMain.toList())
        } finally { handle.dispose() }
    }

    @Test fun composableSourceFilesDoNotHopToABackgroundDispatcherDirectly() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val bare = Regex("""withContext\(\s*(kotlinx\.coroutines\.)?(Dispatchers\.|NonCancellable\s*\+\s*Dispatchers\.)""")
        val offenders = root.walkTopDown().filter { it.extension == "kt" && it.name != "OffMain.kt" }.filter { "@Composable" in it.readText() }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line -> if (bare.containsMatchIn(line)) "${f.path}:${i + 1}" else null }
        }.toList()
        assertEquals("use computeOffMain in composable files", emptyList<String>(), offenders)
        // Scopes handed to controllers that do IO must continue on main. Only the shell's drawer/snackbar scope (frame-clock animations,
        // no IO) keeps the plain composition scope.
        val plainScopes = root.walkTopDown().filter { it.extension == "kt" && it.name != "OffMain.kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line -> if ("rememberCoroutineScope(" in line) "${f.name}:${i + 1}" else null }
        }.filterNot { it.startsWith("AppShell.kt:") }.toList()
        assertEquals("use rememberMainThreadScope", emptyList<String>(), plainScopes)
        // A StateFlow may be written from any thread; its UI collector must continue on main.
        val collectors = root.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line -> if (Regex("""collectAsState(WithLifecycle)?\(\)""").containsMatchIn(line)) "${f.name}:${i + 1}" else null }
        }.toList()
        assertEquals("collect on Dispatchers.Main.immediate", emptyList<String>(), collectors)
    }
}
