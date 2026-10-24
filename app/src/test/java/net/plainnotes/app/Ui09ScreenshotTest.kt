package net.plainnotes.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.room.Room
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.Slot
import net.plainnotes.app.domain.SlotState
import net.plainnotes.app.domain.TimelineEntry
import net.plainnotes.app.reminder.ReminderCoordinator
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
 * UI-09 screenshot evidence (no pixel assertions), synthetic data only: home, stock, treatment timeline, medical records
 * home, visit detail, wellbeing, intake history and the stage review. 4 languages × 320/411 dp × font 1/2 × light/dark.
 * Off by default; run with UI09_SHOTS=<label> (output build/ui09-shots/<label>/). Only existing APIs are called, so the
 * same file renders the "before" set on the base commit.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35], application = android.app.Application::class)
class Ui09ScreenshotTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}")
        fun params() = listOf("zh-rCN", "en", "fr", "zh-rTW").flatMap { l -> listOf(320, 411).flatMap { w -> listOf(1f, 2f).flatMap { f -> listOf(false, true).map { arrayOf<Any>(l, w, f, it) } } } }
    }
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.systemDefault()
    private val now = Instant.now()
    private val today = LocalDate.now(zone)
    private fun at(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(zone).toInstant()
    private val medA = MedicationEntity(1, "药物 A", "E2", "ORAL", "MG", 2.0, 28.0, 30, 15, 120, false, null, true, true, 0)
    private val medB = MedicationEntity(2, "药物 B", "SPIRO", "ORAL", "MG", 50.0, 30.0, null, 15, 120, false, null, true, true, 1)
    private val meds = listOf(medA, medB)
    private var nextId = 1L
    private fun record(m: MedicationEntity, t: Instant, status: String) = RecordEntity(nextId++, m.id, scheduled_utc = t.toEpochMilli(), scheduled_zone = zone.id,
        planned_dose = m.dose_per_intake, taken_utc = if (status == "MISSED" || status == "SKIPPED") null else t.toEpochMilli(), taken_zone = zone.id,
        actual_dose = if (status == "MISSED" || status == "SKIPPED") null else m.dose_per_intake, status = status, origin = "APP", revision = 1, config_snapshot = MedicationSnapshot.encode(m, null))
    private val records = listOf(record(medA, at(today.minusDays(2), 8), "ON_TIME"), record(medB, at(today.minusDays(2), 9), "SKIPPED"),
        record(medA, at(today.minusDays(1), 8), "LATE"), record(medB, at(today.minusDays(1), 9), "MISSED"), record(medA, at(today, 8), "ON_TIME"))
    private fun entry(m: MedicationEntity, t: Instant) = TimelineEntry(Slot("wall:${m.id}@$t", m.id, m.id, t, t, zone, m.dose_per_intake, 15, 120, t.minusSeconds(86400 * 30)), SlotState.PENDING)
    private val open = listOf(entry(medB, now.plus(Duration.ofHours(3))))
    private fun day(n: Long) = today.plusDays(n).atTime(8, 0).atZone(zone).toInstant()
    private val forecasts = mapOf(1L to StockForecast(1, 6.0, 3, day(2), day(3)))
    private val boxes = listOf(ContainerEntity(1, 1, 28.0, 0.0, 22.0, today.minusDays(27).toString(), "IN_USE"), ContainerEntity(2, 1, 28.0, 0.0, 0.0, null, "SEALED"))
    private val visit = AppointmentEntity(1, "ENDO", now.plus(Duration.ofDays(5)).toEpochMilli(), zone.id, "Doctor placeholder", "Clinic placeholder", "Synthetic note", 0, null)
    private val items = listOf(CheckinItemEntity(1, "DAY_MOOD", null, true, 0), CheckinItemEntity(2, "DAY_ENERGY", null, true, 1))

    @Test fun pages() {
        val label = System.getenv("UI09_SHOTS")
        assumeTrue("set UI09_SHOTS to render", !label.isNullOrBlank())
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-xxhdpi"); RuntimeEnvironment.setFontScale(font)
        val context = rule.activity
        val db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        db.openHelper.writableDatabase
        val repo = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = net.plainnotes.app.syntheticLibrary(db) })
        val model = NotesViewModel(repo, ReminderCoordinator(context, repo), context).also {net.plainnotes.app.awaitRuntimeLibrary(it)}
        try {
            // The visit detail reads its rows from the state passed in; the model is only there for callbacks.
            val id = visit.id
            val questions = listOf("Synthetic question one", "Synthetic question two").mapIndexed { i, q -> VisitQuestionEntity(i + 1L, id, i, q, if (i == 0) "ASKED" else "OPEN", if (i == 0) "Synthetic answer" else null) }
            var page by mutableIntStateOf(0)
            rule.setContent { NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { androidx.compose.material3.Surface { key(page) { when (page) {
                0 -> CalendarScreen(NotesState(meds, open, emptyList(), loading = false, calendarStart = today), today, {}, {}, {}, {}, PaddingValues(),
                    extra = NotesViewModel.ExtraState(records = records, upcoming = open, stockForecasts = forecasts))
                1 -> StockScreen(NotesState(meds, loading = false), boxes, emptyList(), {}, {}, {}, PaddingValues(), forecasts = forecasts, today = today, zone = zone)
                2 -> LongitudinalScreen(NotesState(loading = false), NotesViewModel.ExtraState(records = records), {}, {}, {}, PaddingValues())
                3 -> MedicalRecordsScreen(listOf(visit), {}, {}, {}, {}, {}, PaddingValues(), questionCounts = mapOf(1L to 2), now = now)
                4 -> VisitsScreen(NotesState(meds, loading = false, appointments = listOf(visit)), NotesViewModel.ExtraState(visitQuestions = questions), model, id, {}, {}, PaddingValues())
                5 -> WellbeingHubContent(NotesState(loading = false), NotesViewModel.ExtraState(items = items, scores = listOf(CheckinScoreEntity(today.toString(), 1, 4))), "FR",
                    PaddingValues(), { _, _, _ -> }, { _, _ -> }, { _, _, _, _ -> }, {}, {})
                else -> HistoryScreen(NotesState(meds, loading = false), records, {}, {}, PaddingValues())
            } } } } }
            val dir = File("build/ui09-shots/$label").apply { mkdirs() }
            val suffix = "${locale}_${width}dp_${if (font == 1f) "1x" else "2x"}_${if (dark) "dark" else "light"}"
            fun shot(name: String, view: android.view.View = rule.activity.window.decorView) {
                rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                File(dir, "${name}_$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            fun scroll(i: Int) = runCatching { rule.onNode(hasScrollToIndexAction()).performScrollToIndex(i) }
            val names = listOf("1_home", "2_stock", "3_timeline", "4_medical", "5_visit_detail", "6_wellbeing", "7_history")
            names.forEachIndexed { i, n -> rule.runOnIdle { page = i }; shot("${n}_top"); if (i == 4 || i == 6 || i == 0) { scroll(3); shot("${n}_scroll") } }
        } finally { db.close() }
    }

    /** The stage review is a dialog: its own window, drawn from its decor view with a paused clock (as in UI-08). */
    @Test fun stageReview() {
        val label = System.getenv("UI09_SHOTS")
        assumeTrue("set UI09_SHOTS to render", !label.isNullOrBlank())
        // At 411 dp the dialog window never settles under Robolectric (same as UI-08's dialog shots): NOT_RUN, report 75 §9.
        assumeTrue("dialog shots at 320 dp only", width == 320)
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-xxhdpi"); RuntimeEnvironment.setFontScale(font)
        rule.mainClock.autoAdvance = false
        var open by mutableStateOf(true)
        rule.setContent { NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            if (open) StageReviewDialog(StageReviewEntity(id = 1, date = today.toString(), effects_json = "{}", tolerance_note = "Synthetic tolerance"), emptyList(), emptyList(), emptyList(), {}, {}) } } }
        rule.mainClock.advanceTimeBy(2_000)
        val dir = File("build/ui09-shots/$label").apply { mkdirs() }
        val suffix = "${locale}_${width}dp_${if (font == 1f) "1x" else "2x"}_${if (dark) "dark" else "light"}"
        val view = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
        val img = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888); view.draw(android.graphics.Canvas(img))
        File(dir, "8_stage_review_$suffix.png").outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Close the dialog before teardown: left open, Robolectric keeps relayouting its window for minutes.
        open = false; rule.mainClock.advanceTimeBy(1_000)
    }
}
