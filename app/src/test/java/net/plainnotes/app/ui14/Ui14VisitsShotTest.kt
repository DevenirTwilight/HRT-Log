package net.plainnotes.app.ui14

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.room.Room
import net.plainnotes.app.NotesState
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.data.*
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
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * UI-14 screenshot evidence (no pixel assertions), synthetic visits with neutral placeholder names. Off by default; run with
 * UI14_SHOTS=<label> (output build/ui14-shots/<label>/). Only APIs that exist both before and after UI-14 are used, so the same file renders
 * the "before" set on the base commit: [VisitsScreen] with its long-standing parameters.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35], application = android.app.Application::class)
class Ui14VisitsShotTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}")
        fun params() = listOf("zh-rCN", "fr-rFR").flatMap { l -> listOf(320 to 2f, 411 to 1f).flatMap { (w, f) -> listOf(false, true).map { arrayOf<Any>(l, w, f, it) } } }
        const val LONG_PLACE = "Synthetic Outpatient Building A, Floor 3, East Wing, Room 305, Synthetic Medical Centre, Placeholder Street 123, Placeholder City"
    }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.systemDefault()
    private val now = Instant.now()
    private fun at(days: Long) = now.plus(Duration.ofDays(days)).toEpochMilli()
    private fun visit(id: Long, days: Long, done: Boolean, type: String = "ENDO", doctor: String? = "Doctor Placeholder", place: String? = "Clinic Placeholder") =
        AppointmentEntity(id, type, at(days), zone.id, place, doctor, null, 0, if (done) at(days) else null)
    private val questions = listOf(VisitQuestionEntity(1, 2, 0, "Synthetic question", "OPEN", null), VisitQuestionEntity(2, 2, 1, "Synthetic question two", "OPEN", null))
    private val packs = listOf(VisitPackEntity(1, 2, now.minus(Duration.ofDays(2)).toEpochMilli(), zone.id, "2026-10-01", "2026-10-30", "FACTS", "en", 1, "d", "{}"))
    private val scenarios = listOf(
        "1_past-two" to listOf(visit(1, -3, true), visit(2, -30, true, "LAB")),
        "2_future-and-past" to listOf(visit(1, 5, false), visit(2, -3, true), visit(3, -20, false, "LAB", doctor = null)),
        "3_unconfirmed-only" to listOf(visit(1, -9, false)),
        "4_long-location" to listOf(visit(1, 2, false, place = LONG_PLACE), visit(2, -3, true, place = LONG_PLACE)),
    )

    @Test fun pages() {
        val label = System.getenv("UI14_SHOTS")
        assumeTrue("set UI14_SHOTS to render", !label.isNullOrBlank())
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-xxhdpi"); RuntimeEnvironment.setFontScale(font)
        val db = Room.inMemoryDatabaseBuilder(ui.activity, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        db.openHelper.writableDatabase
        val repo = NotesRepository(object : DatabaseAccess(ui.activity) { override fun get(space: Space) = net.plainnotes.app.syntheticLibrary(db) })
        val model = NotesViewModel(repo, ReminderCoordinator(ui.activity, repo), ui.activity).also { net.plainnotes.app.awaitRuntimeLibrary(it) }
        var page by mutableIntStateOf(0)
        val extra = NotesViewModel.ExtraState(visitQuestions = questions, visitPacks = packs)
        ui.setContent { NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            key(page) { VisitsScreen(NotesState(emptyList(), loading = false, appointments = scenarios[page].second), extra, model, null, {}, {}, PaddingValues()) }
        } } }
        val dir = File("build/ui14-shots/$label").apply { mkdirs() }
        val suffix = "${locale}_${width}dp_${if (font == 1f) "1x" else "2x"}_${if (dark) "dark" else "light"}"
        fun shot(name: String) {
            ui.waitForIdle()
            val view = ui.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            ui.runOnUiThread { view.draw(android.graphics.Canvas(bitmap)) }
            File(dir, "${name}_$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        scenarios.forEachIndexed { i, (name, _) ->
            ui.runOnIdle { page = i }; ui.waitForIdle()
            shot("${name}_top")
            if (i == 1 || i == 3) { runCatching { ui.onNode(hasScrollToIndexAction()).performScrollToIndex(4) }; shot("${name}_scrolled") }
        }
    }
}
