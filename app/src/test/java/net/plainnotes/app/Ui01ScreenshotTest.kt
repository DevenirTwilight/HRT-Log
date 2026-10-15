package net.plainnotes.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
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
 * UI-01 screenshot evidence (no pixel assertions): calendar, stock, timeline, drawer and medical records with synthetic
 * data, 4 languages × 320/411dp × font 1/2 × light/dark. Uses only screen APIs that also exist before UI-01, so the same
 * file renders the "before" set on the base commit. Off by default; run with UI01_SHOTS=<label> (output
 * build/ui01-shots/<label>/).
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35])
class Ui01ScreenshotTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr-rFR").flatMap { l -> listOf(320, 411).flatMap { w -> listOf(1f, 2f).flatMap { f -> listOf(false, true).map { arrayOf<Any>(l, w, f, it) } } } }
    }
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now()
    private fun at(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).atZone(zone).toInstant()

    @Test fun screens() {
        val label = System.getenv("UI01_SHOTS")
        assumeTrue("set UI01_SHOTS to render", !label.isNullOrBlank())
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-xxhdpi"); RuntimeEnvironment.setFontScale(font)
        val e2 = MedicationEntity(1, "Synthetic estradiol valerate", "E2", "ORAL", "MG", 2.0, 28.0, null, 15, 120, false, null, true, true, 0)
        val cpa = MedicationEntity(2, "Synthetic CPA", "CPA", "ORAL", "MG", 10.0, 50.0, null, 15, 120, false, null, true, true, 1)
        val meds = listOf(e2, cpa)
        fun slot(m: MedicationEntity, t: Instant, st: SlotState) = TimelineEntry(Slot("wall:${m.id}@$t", m.id, m.id, t, t, zone, m.dose_per_intake, 15, 120, t.minusSeconds(86400 * 60)), st)
        val open = listOf(slot(e2, at(today, 20), SlotState.PENDING), slot(cpa, at(today, 20), SlotState.PENDING), slot(e2, at(today.plusDays(1), 8), SlotState.PENDING))
        val records = listOf(RecordEntity(1, 1, scheduled_utc = at(today, 8).toEpochMilli(), taken_utc = at(today, 8, 5).toEpochMilli(), taken_zone = zone.id, actual_dose = 2.0,
            status = "ON_TIME", origin = "APP", revision = 1, config_snapshot = MedicationSnapshot.encode(e2, null)),
            RecordEntity(2, 1, scheduled_utc = at(today.minusDays(2), 8).toEpochMilli(), taken_utc = null, taken_zone = zone.id, actual_dose = 2.0,
                status = "MISSED", origin = "APP", revision = 1, config_snapshot = MedicationSnapshot.encode(e2, null)))
        val forecasts = mapOf(1L to StockForecast(1, 6.0, 30, at(today.plusDays(2), 20), at(today.plusDays(3), 8)), 2L to StockForecast(2, 40.0, 30, at(today.plusDays(60), 8), null))
        val boxes = listOf(ContainerEntity(1, 1, 28.0, 0.0, 22.0, today.minusDays(11).toString(), "IN_USE", source_note = "Synthetic pharmacy", batch = "B-1"),
            ContainerEntity(2, 2, 50.0, 0.0, 10.0, today.minusDays(5).toString(), "IN_USE"), ContainerEntity(3, 2, 50.0, 0.0, 0.0, null, "SEALED"))
        val past = AppointmentEntity(1, "ENDO", at(today.minusDays(30), 10).toEpochMilli(), zone.id, "Synthetic doctor", "Synthetic clinic", "Synthetic note", 0, completed_utc = at(today.minusDays(30), 11).toEpochMilli())
        val pending = AppointmentEntity(2, "BLOOD", at(today.minusDays(2), 9).toEpochMilli(), zone.id, null, "Synthetic lab", null, 0)
        val next = AppointmentEntity(3, "ENDO", at(today.plusDays(12), 14).toEpochMilli(), zone.id, "Synthetic doctor", "Synthetic clinic", null, 0)
        val older = AppointmentEntity(4, "GP", at(today.minusDays(400), 10).toEpochMilli(), zone.id, "Synthetic GP", null, null, 0, completed_utc = at(today.minusDays(400), 11).toEpochMilli())
        val state = NotesState(meds, open, listOf(past, pending, next, older), loading = false, calendarStart = today)
        val def = RegimenDefinition(MedicationSnapshot.encode(e2, ProfileEntity(1, "EV", "oral")), "EVERY_N_DAYS", 1, 0, 2.0, zone.id, today.minusDays(90).toString(), null, listOf("08:00:00" to null))
        val from = at(today.minusDays(90), 0).toEpochMilli()
        val extra = NotesViewModel.ExtraState(records = records, containers = boxes, upcoming = open, stockForecasts = forecasts,
            regimens = listOf(RegimenVersionEntity(1, 1, from, null, zone.id, def.json(), def.signature(), "APP", from)),
            labs = listOf(LabValueEntity(8, "E2", 420.0, "pmol/L", at(today.minusDays(20), 9).toEpochMilli(), zone.id)),
            milestones = listOf(MilestoneEntity(5, today.minusDays(60).toString(), kind = "STARTED")))
        var page by mutableIntStateOf(0)
        val names = listOf("calendar", "stock", "timeline", "drawer", "medical")
        // UI-02 A1: UI01_CONTRAST=MEDIUM|HIGH renders the same set in another contrast mode.
        val contrast = System.getenv("UI01_CONTRAST")?.let { runCatching { Contrast.valueOf(it) }.getOrNull() } ?: Contrast.STANDARD
        // The contrast sets cover en and zh-CN at 411dp, font 1 (light and dark), as UI-02 asks.
        if (contrast != Contrast.STANDARD) assumeTrue(locale in listOf("en", "zh-rCN") && width == 411 && font == 1f)
        rule.setContent { NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, contrast = contrast) { androidx.compose.material3.Surface {
            when (page) {
                0 -> CalendarScreen(state, today, {}, {}, {}, {}, PaddingValues(), extra = extra)
                1 -> StockScreen(state, boxes, records, {}, {}, {}, PaddingValues(), forecasts = forecasts, today = today, zone = zone)
                2 -> LongitudinalScreen(state, extra, {}, {}, {}, PaddingValues())
                3 -> DrawerContent(Destination.CALENDAR) {}
                else -> MedicalRecordsScreen(state.appointments, {}, {}, {}, {}, {}, PaddingValues())
            }
        } } }
        val dir = File("build/ui01-shots/$label").apply { mkdirs() }
        names.forEachIndexed { i, name ->
            rule.runOnIdle { page = i }; rule.waitForIdle()
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(dir, "${name}_${locale}_${width}dp_${if (font == 1f) "1x" else "2x"}_${if (dark) "dark" else "light"}${if (contrast == Contrast.STANDARD) "" else "_" + contrast.name.lowercase()}.png").outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
