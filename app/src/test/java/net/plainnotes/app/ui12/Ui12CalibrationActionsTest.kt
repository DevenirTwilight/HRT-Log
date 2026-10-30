package net.plainnotes.app.ui12

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import net.plainnotes.app.pk.CalibrationMode
import net.plainnotes.app.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Owner feedback 2026-10-30 (REQUIREMENTS §175): the calibration card's actions ("check eligibility", "history", "manage lab results") used to
 * stand one above the other. They share one wrapping row: side by side when the screen is wide enough, wrapped when it is not.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35], application = android.app.Application::class)
class Ui12CalibrationActionsTest(private val locale: String) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr-rFR")
    }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private var pmol by mutableStateOf(false)

    private fun show(width: Int, font: Float) {
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-xxhdpi"); RuntimeEnvironment.setFontScale(font)
        val sc = Ui12Fixture.sublingualCalibrated()
        ui.setContent { NotesTheme(ThemeMode.LIGHT) {
            ConcentrationScreen(sc.state, sc.result, false, 70.0, ConcSettings(pmol, true, CalibrationMode.RETROSPECTIVE), { pmol = it.pmol }, {}, {}, {}, PaddingValues(),
                sc.records, sc.state.profiles, preview = sc.preview, e2NonSublingual = sc.nonSublingual, doseTimes = sc.doseTimes, labs = sc.labs)
        } }
        ui.waitUntil(30_000) { ui.onAllNodesWithTag("calibration-switch").fetchSemanticsNodes().isNotEmpty() }
        ui.waitForIdle()
    }

    private val tags = listOf("calibration-details-open", "calibration-open-history", "calibration-manage-labs")
    private fun present() = tags.filter { ui.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }

    @Test fun theActionsShareARowWheneverTheyFitAndAreAllStillThere() {
        show(411, 1f)
        ui.onNodeWithTag("calibration-manage-labs").performScrollTo()
        val shown = present(); assertTrue("details and manage are always there: $shown", "calibration-details-open" in shown && "calibration-manage-labs" in shown)
        val boxes = shown.map { it to ui.onNodeWithTag(it).also { n -> n.assertHasClickAction() }.getUnclippedBoundsInRoot() }
        val left = boxes.first().second.left.value; val rightLimit = 411f - left // the card's content ends as far from the right edge as it starts from the left
        // A button may start a new row only when it would not have fitted after its predecessor; otherwise it stands next to it.
        boxes.zipWithNext().forEach { (a, b) ->
            val width = b.second.right.value - b.second.left.value
            if (b.second.top == a.second.top) assertTrue("${a.first} then ${b.first} side by side: ${a.second} ${b.second}", b.second.left >= a.second.right)
            else assertTrue("${b.first} wrapped although it fits: ${a.second} ${b.second}", a.second.right.value + 8f + width > rightLimit && b.second.top >= a.second.bottom)
        }
        // Chinese labels are short: the first two (and the third when it is the only other one) really are on one row.
        if (locale.startsWith("zh")) assertEquals("zh: the first two buttons share a row", boxes[0].second.top, boxes[1].second.top)
    }

    @Test fun theyWrapInsteadOfBeingCutOffAtTheSmallestWidthAndLargestFont() {
        show(320, 2f)
        ui.onNodeWithTag("calibration-manage-labs").performScrollTo()
        val a = ui.onNodeWithTag("calibration-details-open").getUnclippedBoundsInRoot(); val b = ui.onNodeWithTag("calibration-manage-labs").getUnclippedBoundsInRoot()
        val widthDp = 320f
        assertTrue("inside the window: $a $b", a.left.value >= 0f && a.right.value <= widthDp && b.left.value >= 0f && b.right.value <= widthDp)
        assertTrue("never overlapping: $a $b", b.top >= a.bottom || b.left >= a.right)
    }
}
