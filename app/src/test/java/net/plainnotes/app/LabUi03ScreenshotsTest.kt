package net.plainnotes.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import net.plainnotes.app.data.LabPanelEntity
import net.plainnotes.app.data.LabValueEntity
import net.plainnotes.app.ui.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

/** Actual Destination.LABS Composable; identical synthetic fixture for baseline/new screenshots, no real health data. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35])
class LabUi03ScreenshotsTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr-rFR").flatMap { l -> listOf(320, 411).flatMap { w -> listOf(1f, 2f).flatMap { f -> listOf(false, true).map { arrayOf<Any>(l, w, f, it) } } } }
    }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    @Test fun actualLabsHome() {
        val phase = System.getenv("LABUI03_SHOTS")
        assumeTrue("Set LABUI03_SHOTS for screenshot capture", !phase.isNullOrBlank())
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-xxhdpi")
        RuntimeEnvironment.setFontScale(font)
        val at = Instant.parse("2026-03-10T08:30:00Z").toEpochMilli()
        val panel = LabPanelEntity(5, "00000000-0000-4000-8000-000000000005", at, "UTC", "Laboratory A", null, 1)
        val values = listOf(
            LabValueEntity(1, "E2", 210.0, "pg/mL", at, "UTC", laboratory = "Laboratory A", panel_id = 5),
            LabValueEntity(2, "T", 9.0, "ng/dL", at, "UTC", laboratory = "Laboratory A", panel_id = 5),
            LabValueEntity(3, "ALT", 54.0, "U/L", at, "UTC", reference_upper = 40.0, reference_unit = "U/L", laboratory = "Laboratory A", panel_id = 5),
            LabValueEntity(4, "LH", 0.4, "IU/L", at - 86400000, "UTC"),
            LabValueEntity(5, "FSH", 0.2, "IU/L", at - 86400000, "UTC"),
            LabValueEntity(6, "E2", 150.0, "pg/mL", at - 172800000, "UTC"))
        ui.setContent { NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { Surface { Column {
            TopAppBar(title = { Text(stringResource(R.string.labs)) })
            LabsScreen(values, emptyList(), {}, {}, PaddingValues(), panels = listOf(panel), onPanel = {}, onSaveAsPanel = {})
        } } } }
        val folder = File("build/labui03-shots/$phase").apply { mkdirs() }
        fun capture(state: String) {
            ui.waitForIdle()
            val view = ui.activity.window.decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            File(folder, "${state}_${locale}_${width}dp_${font}x_${if (dark) "dark" else "light"}.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
        capture("top")
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("lab-group:${at - 86400000}:"))
        capture("history")
    }
}
