package net.plainnotes.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

/** Final integrated real home with synthetic general-health results, never an imported personal report. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35])
class LabUi03CatalogScreenshotsTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr-rFR").flatMap {
            listOf(arrayOf<Any>(it, 411, 1f, false), arrayOf<Any>(it, 320, 2f, true))
        }
    }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    @Test fun expandedCatalogOnActualHome() {
        val phase = System.getenv("LABUI03_SHOTS")
        assumeTrue("Set LABUI03_SHOTS for screenshot capture", !phase.isNullOrBlank())
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-xxhdpi")
        RuntimeEnvironment.setFontScale(font)
        val at = Instant.parse("2026-03-10T08:30:00Z").toEpochMilli()
        val panel = LabPanelEntity(50, "00000000-0000-4000-8000-000000000050", at, "UTC", "Synthetic laboratory", null, 1)
        val data = listOf(Triple("TG", 3.05, "g/L"), Triple("HDL_C", 50.0, "mg/dL"),
            Triple("GLU_FASTING", 0.95, "g/L"), Triple("RBC", 4.43, "millions/mm³"),
            Triple("HB", 13.8, "g/dL"), Triple("NEUT_ABS", 3.2, "10³/mm³"), Triple("NEUT_PCT", 55.0, "%"))
        val values = data.mapIndexed { index, (code, value, unit) ->
            LabValueEntity(index + 1L, code, value, unit, at, "UTC", laboratory = panel.laboratory, panel_id = panel.id)
        }
        ui.setContent { NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { Surface {
            LabsScreen(values, emptyList(), {}, {}, PaddingValues(), panels = listOf(panel), onPanel = {})
        } } }
        val folder = File("build/labui03-shots/$phase").apply { mkdirs() }
        fun capture(state: String) {
            ui.waitForIdle()
            val view = ui.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(folder, "catalog_${state}_${locale}_${width}dp_${font}x_${if (dark) "dark" else "light"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        capture("all")
        listOf("LIPIDS", "METABOLISM", "CBC").forEach { category ->
            ui.onNodeWithTag("labs-home").performScrollToIndex(0)
            ui.onNodeWithTag("lab-category:$category").performScrollTo().assertIsDisplayed().performClick()
            ui.onNodeWithTag("labs-home-no-results").assertDoesNotExist()
            ui.onNodeWithTag("labs-home").performScrollToNode(hasTestTag("lab-panel:50"))
            if (category == "CBC") {
                ui.onNodeWithTag("labs-home").performScrollToNode(hasTestTag("lab-home-expand:4"))
                ui.onNodeWithTag("lab-home-expand:4").performClick()
                ui.onNodeWithTag("labs-home").performScrollToNode(hasTestTag("lab-group-row:7"))
                ui.onNodeWithTag("lab-group-row:7").assertExists()
            }
            capture(category.lowercase())
        }
    }
}
