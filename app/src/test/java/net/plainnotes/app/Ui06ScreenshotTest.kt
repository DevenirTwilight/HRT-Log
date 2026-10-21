package net.plainnotes.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToIndex
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.Slot
import net.plainnotes.app.domain.SlotState
import net.plainnotes.app.domain.TimelineEntry
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
 * UI-06 screenshot evidence (no pixel assertions), synthetic data with placeholder names 药物 A/B/C: home (today card with a
 * past-time dose, the "no entry yet" card), month calendar and legend, week view, intake history, stock and timeline.
 * 4 languages × 320/411dp × font 1/2 × light/dark, plus high contrast and a hi-res zh-CN set. Off by default; run with
 * UI06_SHOTS=<label> (output build/ui06-shots/<label>/). The same file renders the "before" set on the base commit, where
 * the card does not exist yet.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35])
class Ui06ScreenshotTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean, private val variant: String) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}/{4}")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr-rFR").flatMap { l -> listOf(320, 411).flatMap { w -> listOf(1f, 2f).flatMap { f -> listOf(false, true).map { arrayOf<Any>(l, w, f, it, "std") } } } } +
            listOf("en", "zh-rCN", "zh-rTW", "fr-rFR").flatMap { l -> listOf(false, true).map { arrayOf<Any>(l, 411, 1f, it, "hc") } } +
            listOf(false, true).map { arrayOf<Any>("zh-rCN", 411, 1f, it, "hires") }
    }
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.systemDefault()
    private val now = Instant.now()
    private val today = LocalDate.now(zone)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).atZone(zone).toInstant()
    private val medA = MedicationEntity(1, "药物 A", "E2", "ORAL", "MG", 2.0, 28.0, 30, 15, 120, false, null, true, true, 0)
    private val medB = MedicationEntity(2, "药物 B", "SPIRO", "ORAL", "MG", 50.0, 30.0, null, 15, 120, false, null, true, true, 1)
    private val medC = MedicationEntity(3, "药物 C", "PROG", "ORAL", "MG", 100.0, 30.0, null, 15, 120, false, null, true, true, 2)
    private val meds = listOf(medA, medB, medC)
    private fun entry(m: MedicationEntity, t: Instant, st: SlotState) =
        TimelineEntry(Slot("wall:${m.id}@$t", m.id, m.id, t, t, zone, m.dose_per_intake, 15, 120, t.minusSeconds(86400 * 30)), st)
    private var nextId = 1L
    private fun record(m: MedicationEntity, t: Instant, status: String, origin: String = "APP") = RecordEntity(nextId++, m.id, scheduled_utc = t.toEpochMilli(),
        scheduled_zone = zone.id, planned_dose = m.dose_per_intake, taken_utc = if (status == "MISSED" || status == "SKIPPED") null else t.toEpochMilli(),
        taken_zone = zone.id, actual_dose = if (status == "MISSED" || status == "SKIPPED") null else m.dose_per_intake, status = status, origin = origin,
        revision = 1, config_snapshot = MedicationSnapshot.encode(m, null))
    // The past few days: taken, late, partly done, a confirmed missed day, a skipped dose, and three unconfirmed doses
    // inside the last 48 hours (two shown on the card, one behind "1 more").
    private val records = listOf(
        record(medA, at(today.minusDays(5), 8), "ON_TIME"), record(medB, at(today.minusDays(5), 8), "ON_TIME"),
        record(medA, at(today.minusDays(4), 8), "LATE"), record(medB, at(today.minusDays(4), 8), "MISSED"),
        record(medA, at(today.minusDays(3), 8), "MISSED"), record(medB, at(today.minusDays(3), 8), "MISSED"),
        record(medA, at(today.minusDays(2), 8), "ON_TIME"), record(medB, at(today.minusDays(2), 8), "SKIPPED"),
        record(medA, now.minus(Duration.ofHours(30)), "MISSED", "AUTO_MISSED"), record(medB, now.minus(Duration.ofHours(26)), "MISSED", "AUTO_MISSED"),
        record(medC, now.minus(Duration.ofHours(5)), "MISSED", "AUTO_MISSED"), record(medA, now.minus(Duration.ofHours(3)), "ON_TIME"))
    private val open = listOf(entry(medB, now.minus(Duration.ofHours(1)), SlotState.OVERDUE), entry(medA, now.plus(Duration.ofMinutes(20)), SlotState.SOON),
        entry(medC, now.plus(Duration.ofHours(4)), SlotState.PENDING))
    private fun day(n: Long) = today.plusDays(n).atTime(8, 0).atZone(zone).toInstant()
    private val forecasts = mapOf(1L to StockForecast(1, 6.0, 3, day(2), day(3)), 2L to StockForecast(2, 300.0, 30, day(20), day(21)))
    private val boxes = listOf(ContainerEntity(1, 1, 28.0, 0.0, 22.0, today.minusDays(27).toString(), "IN_USE"), ContainerEntity(2, 2, 30.0, 0.0, 0.0, null, "SEALED"))
    private val extraTimeline = NotesViewModel.ExtraState(records = records, milestones = listOf(MilestoneEntity(7, today.minusDays(10).toString(), kind = "ROUTE")))

    @Test fun pages() {
        val label = System.getenv("UI06_SHOTS")
        assumeTrue("set UI06_SHOTS to render", !label.isNullOrBlank())
        val dpi = if (variant == "hires") "xxxhdpi" else "xxhdpi"
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-$dpi"); RuntimeEnvironment.setFontScale(font)
        val contrast = if (variant == "hc") Contrast.HIGH else Contrast.STANDARD
        var page by mutableIntStateOf(0)
        val state = NotesState(meds, open, emptyList(), loading = false, calendarStart = today.minusDays(40))
        rule.setContent { NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, contrast = contrast) { androidx.compose.material3.Surface {
            androidx.compose.runtime.key(page) { when (page) {
                0 -> CalendarScreen(state, today, {}, {}, {}, {}, PaddingValues(), extra = NotesViewModel.ExtraState(records = records, upcoming = open, stockForecasts = forecasts))
                1 -> CalendarScreen(state, today, {}, {}, {}, {}, PaddingValues(), extra = NotesViewModel.ExtraState(records = records, upcoming = open), initialView = CalView.WEEK)
                2 -> HistoryScreen(NotesState(meds, loading = false), records, {}, {}, PaddingValues())
                3 -> StockScreen(NotesState(meds, loading = false), boxes, records, {}, {}, {}, PaddingValues(), forecasts = forecasts, today = today, zone = zone)
                else -> LongitudinalScreen(NotesState(meds, loading = false), extraTimeline, {}, {}, {}, PaddingValues())
            } }
        } } }
        val dir = File("build/ui06-shots/$label").apply { mkdirs() }
        val suffix = "${locale}_${width}dp_${if (font == 1f) "1x" else "2x"}_${if (dark) "dark" else "light"}${if (variant == "std") "" else "_$variant"}"
        fun shot(name: String) {
            rule.waitForIdle()
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(dir, "${name}_$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        listOf("1_home", "2_week", "3_history", "4_stock", "5_timeline").forEachIndexed { i, name ->
            rule.runOnIdle { page = i }; rule.waitForIdle()
            shot("${name}_top")
            if (i == 0) for (k in 1..3) { runCatching { rule.onNode(hasScrollToIndexAction()).performScrollToIndex(k) }; shot("${name}_scroll$k") }
        }
    }
}
