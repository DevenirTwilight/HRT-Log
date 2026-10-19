package net.plainnotes.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.LabValueEntity
import net.plainnotes.app.ui.*
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.*

/**
 * LAB-01: in 4 languages × 320/411dp × font 1/2 × light/dark, the batch form with all three presets keeps Save on screen and
 * scrolls to its last row and to the estimate choice. With LAB01_SHOTS=<label> it also saves screenshots of the form and of
 * the labs page (draw cards and single rows), synthetic data only, to build/lab01-shots/<label>/.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35])
class Lab01LayoutTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr-rFR").flatMap { l -> listOf(320, 411).flatMap { w -> listOf(1f, 2f).flatMap { f -> listOf(false, true).map { arrayOf<Any>(l, w, f, it) } } } }
    }
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val label = System.getenv("LAB01_SHOTS")
    private fun shot(name: String) {
        if (label.isNullOrBlank()) return
        rule.waitForIdle()
        val dir = File("build/lab01-shots/$label").apply { mkdirs() }
        val root = rule.onAllNodes(isRoot()).fetchSemanticsNodes().size
        // The form is a dialog window; draw the topmost window.
        val view = if (root > 1) org.robolectric.shadows.ShadowDialog.getLatestDialog()?.window?.decorView ?: rule.activity.window.decorView else rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        File(dir, "${name}_${locale}_${width}dp_${if (font == 1f) "1x" else "2x"}_${if (dark) "dark" else "light"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun qualify() { RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-xxhdpi"); RuntimeEnvironment.setFontScale(font) }

    @Test fun formScrollsToEveryRowWithSaveFixed() {
        qualify()
        rule.setContent { NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { LabBatchDialog({}, { _, _ -> }) } }
        shot("2_form_empty")
        LabPreset.entries.forEach { rule.onNodeWithTag("lab-preset:${it.name}").performScrollTo().performClick() }
        rule.onNodeWithTag("lab-batch-value:0").performScrollTo().performTextInput("4.5")
        rule.onNodeWithTag("lab-batch-scroll").performTouchInput { swipeDown() }
        shot("3_form_presets")
        rule.onNodeWithTag("lab-batch-save").assertIsDisplayed().assertIsEnabled()
        rule.onNodeWithTag("lab-batch-cancel").assertIsDisplayed()
        rule.onNodeWithTag("lab-batch-value:10").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("lab-batch-estimate").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("lab-batch-save").assertIsDisplayed()
    }

    @Test fun labsPageWithDrawCardsAndSingleRows() {
        qualify()
        val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
        fun at(d: Long, h: Int) = today.minusDays(d).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
        fun lab(id: Long, code: String, v: Double, t: Long, place: String?, lo: Double? = null, hi: Double? = null) = analyte(code).units.first().first.let { u ->
            LabValueEntity(id, code, v, u, t, zone.id, lo, hi, if (lo != null || hi != null) u else null, place) }
        val labs = listOf(lab(1, "FSH", 1.2, at(3, 9), "Lab placeholder", 1.5, 12.4), lab(2, "LH", 0.8, at(3, 9), "Lab placeholder"), lab(3, "E2", 245.0, at(3, 9), "Lab placeholder", 30.0, 200.0),
            lab(4, "T", 25.0, at(3, 9), "Lab placeholder"), lab(5, "PRL", 14.0, at(3, 9), "Lab placeholder", 4.0, 25.0), lab(6, "E2", 160.0, at(40, 10), null))
        rule.setContent { NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { androidx.compose.material3.Surface { LabsScreen(labs, emptyList(), {}, {}, PaddingValues()) } } }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("lab-home-expand:3"))
        rule.onNodeWithTag("lab-home-expand:3").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("lab-group-row:1"))
        rule.onNodeWithTag("lab-group-row:1").assertExists()
        shot("1_labs")
    }
}
