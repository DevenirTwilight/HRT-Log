package net.plainnotes.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import org.junit.Assume.assumeTrue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.swipeUp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * UI-08 screenshot evidence (no pixel assertions), synthetic data only: the daily tab with nothing, some and all noted
 * (top and scrolled to the recent entries), the symptoms tab's top, the "Choose items" dialog. 4 languages × 320/411 dp ×
 * font 1/2 × light/dark, plus high contrast and a hi-res zh-CN set. Off by default; run with UI08_SHOTS=<label> (output
 * build/ui08-shots/<label>/). The "before" set renders the same pages on the base commit through [page].
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35], application = android.app.Application::class)
class Ui08ScreenshotTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean, private val variant: String) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}/{4}")
        fun params() = listOf("zh-rCN", "en", "fr", "zh-rTW").flatMap { l -> listOf(320, 411).flatMap { w -> listOf(1f, 2f).flatMap { f -> listOf(false, true).map { arrayOf<Any>(l, w, f, it, "std") } } } } +
            listOf("zh-rCN", "en").flatMap { l -> listOf(false, true).map { arrayOf<Any>(l, 411, 1f, it, "hc") } } +
            listOf(false, true).map { arrayOf<Any>("zh-rCN", 411, 1f, it, "hires") }
    }
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val today = LocalDate.now()
    private val items = listOf(CheckinItemEntity(1, "DAY_MOOD", null, true, 0), CheckinItemEntity(2, "DAY_ENERGY", null, true, 1),
        CheckinItemEntity(3, "DAY_SLEEP", null, true, 2), CheckinItemEntity(4, "DAY_BODY", null, true, 3))
    private fun scores(n: Int) = items.take(n).mapIndexed { i, it -> CheckinScoreEntity(today.toString(), it.id, listOf(4, 2, 3, 5)[i]) } +
        (1L..40L).filter { it % 3L != 0L && it != 7L }.flatMap { d -> items.take(2).map { CheckinScoreEntity(today.minusDays(d).toString(), it.id, ((d + it.id) % 5 + 1).toInt()) } }

    @Test fun pages() {
        val label = System.getenv("UI08_SHOTS")
        assumeTrue("set UI08_SHOTS to render", !label.isNullOrBlank())
        val dpi = if (variant == "hires") "xxxhdpi" else "xxhdpi"
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-$dpi"); RuntimeEnvironment.setFontScale(font)
        var page by mutableIntStateOf(0)
        rule.setContent { NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, contrast = if (variant == "hc") Contrast.HIGH else Contrast.STANDARD) {
            androidx.compose.material3.Surface { key(page) { page(page) } } } }
        val dir = File("build/ui08-shots/$label").apply { mkdirs() }
        val suffix = "${locale}_${width}dp_${if (font == 1f) "1x" else "2x"}_${if (dark) "dark" else "light"}${if (variant == "std") "" else "_$variant"}"
        fun shot(name: String) {
            rule.mainClock.advanceTimeBy(2_000); rule.waitForIdle()
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(dir, "${name}_$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        fun scrollDown() = runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction()).onFirst().performTouchInput { swipeUp(startY = bottom - 10f, endY = top + 10f, durationMillis = 300) } }
        rule.runOnIdle { page = 0 }; shot("1_daily_none")
        rule.runOnIdle { page = 1 }; shot("2_daily_some")
        rule.runOnIdle { page = 2 }; shot("3_daily_all_top"); scrollDown(); scrollDown(); shot("3_daily_all_recent")
        rule.runOnIdle { page = 3 }; rule.waitForIdle(); runCatching { rule.onNodeWithTag("wb-tab:1").performClick() }; shot("4_symptoms_top")
        rule.runOnIdle { page = 4 }; shot("5_manage_items")
    }

    @Composable private fun page(p: Int) {
        val med = MedicationEntity(1, "药物 A", "E2", "ORAL", "MG", 2.0, 30.0, null, 15, 120, false, null, true, true, 0)
        when (p) {
            0, 1, 2 -> daily(items, scores(listOf(0, 2, 4)[p]))
            3 -> symptoms(med, items)
            else -> ManageCheckinItemsDialog(items + CheckinItemEntity(5, null, "Synthetic custom", false, 4), {}, {})
        }
    }
    /** After UI-08: the hub's daily tab. (The before set calls WellbeingScreen with its old onManage argument here.) */
    @Composable private fun daily(items: List<CheckinItemEntity>, scores: List<CheckinScoreEntity>) =
        WellbeingHubContent(NotesState(loading = false), NotesViewModel.ExtraState(items = items, scores = scores), "FR", PaddingValues(), { _, _, _ -> }, { _, _ -> }, { _, _, _, _ -> }, {}, {})
    @Composable private fun symptoms(med: MedicationEntity, items: List<CheckinItemEntity>) =
        WellbeingHubContent(NotesState(listOf(med), loading = false), NotesViewModel.ExtraState(items = items), "FR", PaddingValues(), { _, _, _ -> }, { _, _ -> }, { _, _, _, _ -> }, {}, {})
}
