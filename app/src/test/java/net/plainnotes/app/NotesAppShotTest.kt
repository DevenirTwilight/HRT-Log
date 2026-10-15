package net.plainnotes.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RuleKind
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
 * UI-02B pixel check of the real NotesApp shell (top bar, FAB, drawer, every destination) with synthetic data, rendered
 * before and after the AppShell state split and compared image by image. Off by default; UI02B_SHOTS=<label>
 * (output build/ui02b-shots/<label>/).
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class NotesAppShotTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}")
        fun params() = listOf("en", "zh-rCN").flatMap { l -> listOf(320, 411).flatMap { w -> listOf(1f, 2f).flatMap { f -> listOf(false, true).map { arrayOf<Any>(l, w, f, it) } } } }
    }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    @Test fun shell() {
        val label = System.getenv("UI02B_SHOTS")
        assumeTrue("set UI02B_SHOTS to render", !label.isNullOrBlank())
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-xxhdpi"); RuntimeEnvironment.setFontScale(font)
        val context = ui.activity
        val db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        val repo = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = db })
        val model = NotesViewModel(repo, ReminderCoordinator(context, repo), context)
        UiPrefs(context).disclaimerAccepted = true
        try {
            runBlocking {
                val m = MedicationEntity(name = "Synthetic E2", molecule = "E2", route = "ORAL", unit = "MG", dose_per_intake = 2.0, container_capacity = 28.0,
                    soon_alert_minutes = 15, late_after_minutes = 60, site_rotation = false, notifications_on = false, active = true, sort_order = 0)
                repo.saveMedication(m, "E2", RuleKind.EVERY_N_DAYS, 1, listOf(LocalTime.of(8, 0)), emptySet(), Instant.parse("2026-09-01T00:00:00Z"))
                repo.addContainers(db.dao().medications().single().id, 28.0, 1, true, "Synthetic pharmacy", "B-1")
                repo.appointment(AppointmentEntity(type = "ENDO", at_utc = Instant.parse("2026-09-20T09:00:00Z").toEpochMilli(), at_zone = "UTC",
                    practitioner = "Synthetic doctor", location = "Synthetic clinic", note = null, remind_minutes_before = 0))
            }
            model.sync()
            val a = Appearance(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, false)
            ui.setContent { NotesTheme(a.mode) { NotesApp(model, a, {}) } }
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Synthetic E2", substring = true).fetchSemanticsNodes().isNotEmpty() }
            val dir = File("build/ui02b-shots/$label").apply { mkdirs() }
            fun shot(name: String) {
                ui.waitForIdle()
                val view = ui.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                File(dir, "${name}_${locale}_${width}dp_${if (font == 1f) "1x" else "2x"}_${if (dark) "dark" else "light"}.png").outputStream()
                    .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            shot("00_calendar")
            ui.onNodeWithContentDescription(context.getString(R.string.menu)).performClick(); shot("01_drawer")
            ui.runOnUiThread { context.onBackPressedDispatcher.onBackPressed() }
            for (d in Destination.entries - Destination.CALENDAR - Destination.CONCENTRATION) {
                ui.onNodeWithContentDescription(context.getString(R.string.menu)).performClick(); ui.waitForIdle()
                ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(context.getString(d.title)))
                ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(context.getString(d.title))).performClick()
                ui.mainClock.advanceTimeBy(1_000); shot("10_" + d.name.lowercase())
            }
        } finally { androidx.lifecycle.ViewModelStore().apply { put("model", model); clear() }; db.close() }
    }
}
