package net.plainnotes.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
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

/**
 * LAB-04 screenshot evidence (no pixel assertions): the treatment timeline with a six-result panel (two out of range), a
 * one-result lab, a display-only group of old results and two results that stay apart, all synthetic (Lab04Fixture).
 * 4 languages × 320/411dp × font 1/2 × light/dark. Off by default; run with LAB04_SHOTS=<label> (output
 * build/lab04-shots/<label>/). The same file renders the "before" set on the base commit (rows are found by their old keys).
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35], application = android.app.Application::class)
class Lab04ScreenshotTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}")
        fun params() = listOf("zh-rCN", "en", "fr", "zh-rTW").flatMap { l -> listOf(320, 411).flatMap { w -> listOf(1f, 2f).flatMap { f -> listOf(false, true).map { arrayOf<Any>(l, w, f, it) } } } }
    }
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun timeline() {
        val label = System.getenv("LAB04_SHOTS")
        assumeTrue("set LAB04_SHOTS to render", !label.isNullOrBlank())
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-xxhdpi"); RuntimeEnvironment.setFontScale(font)
        rule.setContent { NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            LongitudinalScreen(NotesState(loading = false), Lab04Fixture.extra(), {}, {}, {}, PaddingValues())
        } } }
        val dir = File("build/lab04-shots/$label").apply { mkdirs() }
        val suffix = "${locale}_${width}dp_${if (font == 1f) "1x" else "2x"}_${if (dark) "dark" else "light"}"
        fun shot(name: String) {
            rule.waitForIdle()
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(dir, "${name}_$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        fun scroll(vararg tags: String) { tags.firstNotNullOfOrNull { t -> runCatching { rule.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag(t)) }.getOrNull() } }
        // The newest lab rows first (the display group at t3), then the panel (after: one row; before: the first of six).
        scroll("timeline:lab-draw:group:30", "timeline:lab:30"); shot("1_timeline_group")
        scroll("timeline:lab:20"); shot("2_timeline_single")
        scroll("timeline:lab-draw:panel:1", "timeline:lab:1"); shot("3_timeline_panel")
    }
}
