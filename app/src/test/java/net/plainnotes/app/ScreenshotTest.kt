package net.plainnotes.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import net.plainnotes.app.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.*

/** Renders key screens with synthetic data to build/screenshots for visual review (no assertions on pixels). */
@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.systemDefault()
    private val now = Instant.now()
    private val today = LocalDate.now()

    private fun med(id: Long, name: String, molecule: String, route: String?, unit: String, dose: Double) =
        MedicationEntity(id, name, molecule, route, unit, dose, 30.0, null, 15, 120, false, null, true, true, id.toInt())
    private val meds = listOf(med(1, "Progynova", "E2", "SUBLINGUAL", "MG", 2.0), med(2, "Androcur", "CPA", null, "MG", 12.5), med(3, "Gynokadin", "E2", "GEL", "MG", 1.5))
    private val profiles = mapOf(1L to ProfileEntity(1, "EV", "sublingual", 2), 3L to ProfileEntity(3, "E2", "gel", null, 1, "ARM", 750.0))

    private fun slot(med: Long, at: Instant, dose: Double, state: SlotState, original: Instant = at) =
        TimelineEntry(Slot("wall:$med@$at", med, med, original, at, zone, dose, 15, 120, at.minusSeconds(86400 * 30)), state)

    private fun state(): NotesState {
        val d = today.atStartOfDay(zone).toInstant()
        val slots = listOf(
            slot(1, d.plusSeconds(8 * 3600), 2.0, SlotState.ON_TIME), slot(2, d.plusSeconds(8 * 3600 + 60), 12.5, SlotState.LATE),
            slot(3, now.minusSeconds(3 * 3600), 1.5, SlotState.OVERDUE), slot(1, now.plusSeconds(600), 2.0, SlotState.SOON),
            slot(1, d.plusSeconds(86400 + 8 * 3600), 2.0, SlotState.PENDING), slot(2, d.plusSeconds(86400 + 8 * 3600 + 60), 12.5, SlotState.PENDING),
            slot(1, d.plusSeconds(86400 + 20 * 3600), 2.0, SlotState.PENDING, d.plusSeconds(86400 + 21 * 3600)),
            slot(3, d.plusSeconds(3 * 86400 + 9 * 3600), 1.5, SlotState.PENDING))
        val appt = AppointmentEntity(1, "ENDO", d.plusSeconds(2 * 86400 + 10 * 3600).toEpochMilli(), zone.id, "Hôpital Saint-Louis", "Dr Martin", null, 60)
        return NotesState(meds, slots, listOf(appt), null, false,
            mapOf(1L to net.plainnotes.app.ScheduleSummary(RuleKind.EVERY_N_DAYS, 1, emptySet(), listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))),
                2L to net.plainnotes.app.ScheduleSummary(RuleKind.EVERY_N_DAYS, 1, emptySet(), listOf(LocalTime.of(8, 0))),
                3L to net.plainnotes.app.ScheduleSummary(RuleKind.WEEKLY, 1, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), listOf(LocalTime.of(9, 0)))), profiles, today)
    }

    private fun concResult(): net.plainnotes.app.conc.ConcentrationResult {
        val records = (0 until 40).flatMap { i ->
            val t = now.minusSeconds(((40 - i) * 12L) * 3600)
            listOf(RecordEntity(i.toLong() + 1, 1, taken_utc = t.toEpochMilli(), taken_zone = zone.id, actual_dose = 2.0, status = "ON_TIME", origin = "APP", revision = 1, config_snapshot = "{}"))
        }
        val planned = (1..20).map { slot(1, now.plusSeconds(it * 12L * 3600), 2.0, SlotState.PENDING) }
        val labs = listOf(LabValueEntity(1, "E2", 160.0, "pg/mL", now.minusSeconds(9 * 86400).toEpochMilli(), zone.id), LabValueEntity(2, "E2", 520.0, "pmol/L", now.minusSeconds(3 * 86400 + 5 * 3600).toEpochMilli(), zone.id, 0.0, 600.0, "pmol/L"))
        return ConcentrationCalculator.compute(meds.take(1), profiles, records, planned, labs, 62.0, now)
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        rule.setContent { NotesTheme(ThemeMode.LIGHT) { androidx.compose.material3.Surface { content() } } }
        rule.waitForIdle()
        val dir = File("build/screenshots").apply { mkdirs() }
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun calendar() = shoot("calendar") { CalendarScreen(state(), today, {}, {}, {}, {}, PaddingValues()) }
    @Test fun medications() = shoot("medications") { MedicationsScreen(state(), {}, {}, {}, PaddingValues()) }
    @Test fun concentration() { val r = concResult(); shoot("concentration") { ConcentrationScreen(state(), r, false, 62.0, ConcSettings(false, true, net.plainnotes.app.pk.CalibrationMode.RETROSPECTIVE), {}, {}, {}, {}, PaddingValues()) } }
    @Test fun labs() { val r = concResult(); shoot("labs") { LabsScreen(listOf(LabValueEntity(1, "E2", 160.0, "pg/mL", now.minusSeconds(40 * 86400).toEpochMilli(), zone.id, 100.0, 300.0, "pg/mL"),
        LabValueEntity(2, "E2", 190.0, "pg/mL", now.minusSeconds(9 * 86400).toEpochMilli(), zone.id, 100.0, 300.0, "pg/mL"), LabValueEntity(3, "E2", 142.0, "pg/mL", now.minusSeconds(86400).toEpochMilli(), zone.id, 100.0, 300.0, "pg/mL"),
        LabValueEntity(4, "T", 25.0, "ng/dL", now.minusSeconds(86400).toEpochMilli(), zone.id)), listOf(now.minusSeconds(86400 + 5 * 3600)), {}, {}, PaddingValues()) } }
    @Test fun editor() = shoot("editor") { MedicationEditor(EditMedication(meds[2], profiles[3L], null, emptyList()), {}, inDialog = false) {} }
    @Test @Config(qualifiers = "fr-rFR-w411dp-h891dp-xxhdpi") fun concentrationFrench() { val r = concResult(); shoot("concentration_fr") { ConcentrationScreen(state(), r, false, 62.5, ConcSettings(false, true, net.plainnotes.app.pk.CalibrationMode.RETROSPECTIVE), {}, {}, {}, {}, PaddingValues()) } }
    @Test @Config(qualifiers = "zh-rTW-w411dp-h891dp-xxhdpi") fun calendarTraditional() = shoot("calendar_zh_tw") { CalendarScreen(state(), today, {}, {}, {}, {}, PaddingValues()) }
    private fun history(): List<RecordEntity> = (0 until 12).map { i ->
        val sched = now.minusSeconds((i + 1) * 12L * 3600)
        val status = when (i) { 2 -> "LATE"; 5 -> "MISSED"; 8 -> "SKIPPED"; else -> "ON_TIME" }
        RecordEntity(i + 1L, if (i % 3 == 0) 2 else 1, 1, "wall:1@$i", sched.toEpochMilli(), zone.id, 2.0, 120,
            if (status in listOf("ON_TIME", "LATE")) sched.plusSeconds(if (status == "LATE") 3 * 3600L else 600).toEpochMilli() else null,
            if (status in listOf("ON_TIME", "LATE")) zone.id else null, if (status in listOf("ON_TIME", "LATE")) 2.0 else null, null, status, origin = "APP", revision = 1, config_snapshot = "{}")
    }
    @Test fun historyScreen() = shoot("history") { HistoryScreen(state(), history(), {}, {}, PaddingValues()) }
    @Test fun stockScreen() = shoot("stock") { StockScreen(state(), listOf(
        ContainerEntity(1, 1, 30.0, 0.0, 22.0, today.minusDays(10).toString(), "IN_USE"), ContainerEntity(2, 1, 30.0, 0.0, 0.0, null, "SEALED"),
        ContainerEntity(3, 3, 80.0, 0.0, 74.0, today.minusDays(27).toString(), "IN_USE")), history(), {}, { _, _ -> }, {}, PaddingValues()) }
    @Test fun wellbeingScreen() {
        val items = net.plainnotes.app.data.CHECKIN_DEFAULTS.mapIndexed { i, k -> CheckinItemEntity(i + 1L, k, null, i < 6, i) }
        val scores = (0 until 20).flatMap { d -> (1..3).map { it -> CheckinScoreEntity(today.minusDays(d.toLong()).toString(), it.toLong(), 1 + (d * it + 2) % 5) } }
        shoot("wellbeing") { WellbeingScreen(items, scores, listOf(DayNoteEntity(today.toString(), "Synthetic note")), { _, _, _ -> }, { _, _ -> }, {}, PaddingValues()) }
    }
    @Test fun settings() = shoot("settings") { SettingsScreen(Appearance(ThemeMode.SYSTEM, false), {}, false, {}, {}, {}, PaddingValues()) }
}
