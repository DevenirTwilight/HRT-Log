package net.plainnotes.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import net.plainnotes.app.data.*
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
import java.time.*

/**
 * UI-02 screenshot evidence (no pixel assertions), synthetic data: settings overview and its six pages, the home stock
 * card with both actions, the timeline with the "Labs" filter. 4 languages × 320/411dp × font 1/2 × light/dark.
 * Off by default; run with UI02_SHOTS=<label> (output build/ui02-shots/<label>/).
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35])
class Ui02ScreenshotTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr-rFR").flatMap { l -> listOf(320, 411).flatMap { w -> listOf(1f, 2f).flatMap { f -> listOf(false, true).map { arrayOf<Any>(l, w, f, it) } } } }
    }
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now()

    @Test fun screens() {
        val label = System.getenv("UI02_SHOTS")
        assumeTrue("set UI02_SHOTS to render", !label.isNullOrBlank())
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-xxhdpi"); RuntimeEnvironment.setFontScale(font)
        val med = MedicationEntity(1, "Synthetic estradiol valerate", "E2", "ORAL", "MG", 2.0, 28.0, null, 15, 120, false, null, true, true, 0)
        fun at(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(zone).toInstant()
        val forecasts = mapOf(1L to StockForecast(1, 6.0, 30, at(today.plusDays(2), 20), at(today.plusDays(3), 8)))
        val state = NotesState(listOf(med), loading = false, calendarStart = today,
            appointments = listOf(AppointmentEntity(3, "ENDO", at(today.minusDays(10), 10).toEpochMilli(), zone.id, "Synthetic doctor", null, null, 0)))
        val extra = NotesViewModel.ExtraState(stockForecasts = forecasts,
            labs = listOf(LabValueEntity(8, "E2", 420.0, "pmol/L", at(today.minusDays(20), 9).toEpochMilli(), zone.id)),
            milestones = listOf(MilestoneEntity(5, today.minusDays(60).toString(), kind = "STARTED")))
        val sums = SettingsSummaries("Synthetic region", "App lock: synthetic", "Backup: synthetic", "3")
        var page by mutableIntStateOf(0)
        val pages = SettingsPage.entries
        val names = pages.map { "settings_" + it.name.lowercase() } + listOf("home_stock", "timeline_filter_labs")
        rule.setContent { NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            when {
                page < pages.size -> SettingsScreen(Appearance(ThemeMode.SYSTEM, false), {}, false, {}, {}, {}, PaddingValues(), page = pages[page], summaries = sums,
                    wellbeingSlot = { RegionSection("FR") {} }, privacySlot = { PrivacySection(1, false) {} },
                    dataSlot = { androidx.compose.material3.Text("Data section (needs the app model; shown in the app)") },
                    binSlot = { RecycleBinSection(state, extra, {}, {}, {}) })
                page == pages.size -> CalendarScreen(state, today, {}, {}, {}, {}, PaddingValues(), extra = extra)
                else -> LongitudinalScreen(state, extra, {}, {}, {}, PaddingValues())
            }
        } } }
        val dir = File("build/ui02-shots/$label").apply { mkdirs() }
        names.forEachIndexed { i, name ->
            rule.runOnIdle { page = i }; rule.waitForIdle()
            if (name == "home_stock") runCatching { rule.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToNode(androidx.compose.ui.test.hasTestTag("home-stock")) }
            if (name == "timeline_filter_labs") { rule.onNodeWithTag("timeline-filter:LAB").performClick(); rule.waitForIdle() }
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(dir, "${name}_${locale}_${width}dp_${if (font == 1f) "1x" else "2x"}_${if (dark) "dark" else "light"}.png").outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

}
