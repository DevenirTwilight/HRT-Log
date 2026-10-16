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
import java.time.LocalDate
import java.time.ZoneId

/**
 * UI-04 screenshot evidence (no pixel assertions), synthetic data, the calendar page in three cases: one dose today; several
 * doses today with a missed dose and an appointment; another date selected. Each case is shot at the top and scrolled to
 * the area below the calendar (where today's day list used to repeat the card). 4 languages × 320/411dp × font 1/2 ×
 * light/dark. Off by default; run with UI04_SHOTS=<label> (output build/ui04-shots/<label>/).
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35])
class Ui04ScreenshotTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr-rFR").flatMap { l -> listOf(320, 411).flatMap { w -> listOf(1f, 2f).flatMap { f -> listOf(false, true).map { arrayOf<Any>(l, w, f, it) } } } }
    }
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 3, 10)
    private fun at(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(zone).toInstant()
    private val med = MedicationEntity(1, "Synthetic estradiol valerate", "E2", "ORAL", "MG", 2.0, 28.0, null, 15, 120, false, null, true, true, 0)
    private val spiro = MedicationEntity(2, "Synthetic spironolactone", "SPIRO", "ORAL", "MG", 50.0, 30.0, null, 15, 120, false, null, true, true, 1)
    private fun entry(m: MedicationEntity, d: LocalDate, h: Int, st: SlotState = SlotState.PENDING) =
        TimelineEntry(Slot("wall:${m.id}@${at(d, h)}", m.id, m.id, at(d, h), at(d, h), zone, m.dose_per_intake, 15, 120, at(d, h).minusSeconds(86400 * 30)), st)
    private fun record(id: Long, m: MedicationEntity, d: LocalDate, h: Int, status: String) = RecordEntity(id, m.id, scheduled_utc = at(d, h).toEpochMilli(),
        taken_utc = if (status == "MISSED") null else at(d, h).toEpochMilli(), taken_zone = zone.id, actual_dose = m.dose_per_intake, status = status, origin = "APP",
        revision = 1, config_snapshot = MedicationSnapshot.encode(m, null))

    @Test fun calendar() {
        val label = System.getenv("UI04_SHOTS")
        assumeTrue("set UI04_SHOTS to render", !label.isNullOrBlank())
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-xxhdpi"); RuntimeEnvironment.setFontScale(font)
        val tomorrow = today.plusDays(1)
        val cases = listOf(
            Triple("one_dose", listOf(entry(med, today, 20)), Pair(listOf(record(1, med, today, 8, "ON_TIME")), emptyList<AppointmentEntity>())) to today,
            Triple("several", listOf(entry(med, today, 12, SlotState.OVERDUE), entry(spiro, today, 12, SlotState.OVERDUE), entry(med, today, 20), entry(spiro, today, 21)),
                Pair(listOf(record(2, med, today, 8, "ON_TIME"), record(3, spiro, today, 8, "MISSED")),
                    listOf(AppointmentEntity(4, "ENDO", at(today, 15).toEpochMilli(), zone.id, "Synthetic doctor", "Synthetic clinic", null, 0)))) to today,
            Triple("other_date", listOf(entry(med, today, 20), entry(med, tomorrow, 8), entry(med, tomorrow, 20)),
                Pair(emptyList<RecordEntity>(), emptyList<AppointmentEntity>())) to tomorrow)
        var case by mutableIntStateOf(0)
        rule.setContent { NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            val (data, start) = cases[case]
            val (_, open, rest) = data
            androidx.compose.runtime.key(case) {
                CalendarScreen(NotesState(listOf(med, spiro), open, rest.second, loading = false, calendarStart = start), today, {}, {}, {}, {}, PaddingValues(),
                    extra = NotesViewModel.ExtraState(records = rest.first, upcoming = open))
            }
        } } }
        val dir = File("build/ui04-shots/$label").apply { mkdirs() }
        fun shot(name: String) {
            rule.waitForIdle()
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(dir, "${name}_${locale}_${width}dp_${if (font == 1f) "1x" else "2x"}_${if (dark) "dark" else "light"}.png").outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        cases.forEachIndexed { i, (data, _) ->
            rule.runOnIdle { case = i }; rule.waitForIdle()
            shot("${i}a_${data.first}_top")
            // Item 2 is the first one below the calendar: the day list (or, for today after UI-04, the stock card / end).
            runCatching { rule.onNode(hasScrollToIndexAction()).performScrollToIndex(2) }
            shot("${i}b_${data.first}_below")
        }
    }
}
