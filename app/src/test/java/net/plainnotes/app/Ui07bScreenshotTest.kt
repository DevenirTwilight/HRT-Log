package net.plainnotes.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
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
 * UI-07b screenshot evidence (no pixel assertions), synthetic data with placeholder names 药物 A/B/C: home not yet taken,
 * home just logged, home with the "no entry yet" card, the month calendar, intake history. zh-CN and en × 320/411dp ×
 * font 1/2 × light/dark, plus high contrast and a hi-res zh-CN set at 411 dp. Off by default; run with UI07B_SHOTS=<label>
 * (output build/ui07b-shots/<label>/). The same file renders the "before" (UI-07) set on the base commit.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35])
class Ui07bScreenshotTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean, private val variant: String) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}/{4}")
        fun params() = listOf("en", "zh-rCN").flatMap { l -> listOf(320, 411).flatMap { w -> listOf(1f, 2f).flatMap { f -> listOf(false, true).map { arrayOf<Any>(l, w, f, it, "std") } } } } +
            listOf("en", "zh-rCN").flatMap { l -> listOf(false, true).map { arrayOf<Any>(l, 411, 1f, it, "hc") } } +
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
    // Past days with every state, then three variants of today.
    private val past = listOf(
        record(medA, at(today.minusDays(5), 8), "ON_TIME"), record(medB, at(today.minusDays(5), 8), "ON_TIME"),
        record(medA, at(today.minusDays(4), 8), "LATE"), record(medB, at(today.minusDays(4), 8), "MISSED"),
        record(medA, at(today.minusDays(3), 8), "MISSED"), record(medB, at(today.minusDays(3), 8), "MISSED"),
        record(medA, at(today.minusDays(2), 8), "ON_TIME"), record(medB, at(today.minusDays(2), 8), "SKIPPED"))
    private val unconfirmed = listOf(record(medA, now.minus(Duration.ofHours(30)), "MISSED", "AUTO_MISSED"), record(medB, now.minus(Duration.ofHours(26)), "MISSED", "AUTO_MISSED"))
    private val nextAt = now.plus(Duration.ofMinutes(50)).atZone(zone).withSecond(0).withNano(0).toInstant()
    private val open = listOf(entry(medA, nextAt, SlotState.SOON), entry(medB, nextAt.plus(Duration.ofHours(3)), SlotState.PENDING), entry(medC, nextAt.plus(Duration.ofHours(5)), SlotState.PENDING))
    private val logged = record(medC, now.minus(Duration.ofMinutes(5)), "ON_TIME")
    private fun day(n: Long) = today.plusDays(n).atTime(8, 0).atZone(zone).toInstant()
    private val forecasts = mapOf(1L to StockForecast(1, 6.0, 3, day(2), day(3)), 2L to StockForecast(2, 300.0, 30, day(20), day(21)))

    @Test fun pages() {
        val label = System.getenv("UI07B_SHOTS")
        assumeTrue("set UI07B_SHOTS to render", !label.isNullOrBlank())
        val dpi = if (variant == "hires") "xxxhdpi" else "xxhdpi"
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-$dpi"); RuntimeEnvironment.setFontScale(font)
        val contrast = if (variant == "hc") Contrast.HIGH else Contrast.STANDARD
        var page by mutableIntStateOf(0); var homeRecords by mutableStateOf(past)
        rule.setContent { NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, contrast = contrast) { androidx.compose.material3.Surface {
            androidx.compose.runtime.key(page) { when (page) {
                0, 1, 2 -> CalendarScreen(NotesState(meds, if (page == 1) open.drop(1) else open, emptyList(), loading = false, calendarStart = today), today, {}, {}, {}, {}, PaddingValues(),
                    extra = NotesViewModel.ExtraState(records = homeRecords, upcoming = if (page == 1) open.drop(1) else open, stockForecasts = forecasts))
                else -> HistoryScreen(NotesState(meds, loading = false), past + unconfirmed + logged, {}, {}, PaddingValues())
            } }
        } } }
        val dir = File("build/ui07b-shots/$label").apply { mkdirs() }
        val suffix = "${locale}_${width}dp_${if (font == 1f) "1x" else "2x"}_${if (dark) "dark" else "light"}${if (variant == "std") "" else "_$variant"}"
        fun shot(name: String) {
            rule.waitForIdle()
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(dir, "${name}_$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        // 1: not yet taken today.
        rule.runOnIdle { page = 0; homeRecords = past }; shot("1_home_untaken_top")
        runCatching { rule.onNode(hasScrollToIndexAction()).performScrollToIndex(1) }; shot("1_home_untaken_scroll")
        // 2: a dose just logged while the home page is shown (the today log grows; "Logged · Undo" where it exists).
        rule.runOnIdle { page = 1; homeRecords = past }; rule.waitForIdle()
        rule.runOnIdle { homeRecords = past + logged }; rule.mainClock.advanceTimeBy(1_000); shot("2_home_logged_top")
        runCatching { rule.onNode(hasScrollToIndexAction()).performScrollToIndex(1) }; shot("2_home_logged_scroll")
        // 3: unconfirmed doses in the last 48 hours: the "no entry yet" card.
        rule.runOnIdle { page = 2; homeRecords = past + unconfirmed }; shot("3_home_confirm_top")
        runCatching { rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("unconfirmed-card")) }; shot("3_home_confirm_card")
        // 4: the month calendar ("Full calendar" from the home strip).
        rule.runOnIdle { page = 0; homeRecords = past + unconfirmed }; rule.waitForIdle()
        runCatching { rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("calendar-full")); rule.onNodeWithTag("calendar-full").performClick() }
        rule.waitForIdle(); runCatching { rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("calendar-card")) }; shot("4_month")
        rule.runOnIdle { page = 3 }; shot("5_history_top")
    }
}

