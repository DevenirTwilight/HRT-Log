package net.plainnotes.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
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
 * UI-05 screenshot evidence (no pixel assertions), synthetic data with neutral placeholder names: home (today card and
 * low-stock strips), stock, timeline (top and scrolled), drawer and medical records. 4 languages × 320/411dp × font 1/2 ×
 * light/dark. Off by default; run with UI05_SHOTS=<label> (output build/ui05-shots/<label>/). The same file, minus the
 * UI-05-only arguments marked "UI-05:", renders the "before" set on the base commit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35])
class Ui05ScreenshotTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr-rFR").flatMap { l -> listOf(320, 411).flatMap { w -> listOf(1f, 2f).flatMap { f -> listOf(false, true).map { arrayOf<Any>(l, w, f, it) } } } }
    }
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val now = Instant.now()
    private fun at(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).atZone(zone).toInstant()
    private val medA = MedicationEntity(1, "Medication A", "E2", "ORAL", "MG", 2.0, 28.0, null, 15, 120, false, null, true, true, 0)
    private val medB = MedicationEntity(2, "Medication B", "SPIRO", "ORAL", "MG", 50.0, 30.0, null, 15, 120, false, null, true, true, 1)
    private val schedules = mapOf(1L to ScheduleSummary(net.plainnotes.app.domain.RuleKind.EVERY_N_DAYS, 1, emptySet(), listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))),
        2L to ScheduleSummary(net.plainnotes.app.domain.RuleKind.EVERY_N_DAYS, 1, emptySet(), listOf(LocalTime.of(8, 0))))
    // The next dose about two hours from now (so the countdown has something to show), then one later today.
    private val nextAt = now.plus(Duration.ofHours(2)).atZone(zone).withMinute(0).withSecond(0).withNano(0).toInstant()
    private fun entry(m: MedicationEntity, t: Instant, st: SlotState = SlotState.PENDING) =
        TimelineEntry(Slot("wall:${m.id}@$t", m.id, m.id, t, t, zone, m.dose_per_intake, 15, 120, t.minusSeconds(86400 * 30)), st)
    private fun record(id: Long, m: MedicationEntity, t: Instant, status: String) = RecordEntity(id, m.id, scheduled_utc = t.toEpochMilli(),
        taken_utc = if (status == "MISSED") null else t.toEpochMilli(), taken_zone = zone.id, actual_dose = m.dose_per_intake, status = status, origin = "APP",
        revision = 1, config_snapshot = MedicationSnapshot.encode(m, null))
    private fun day(n: Long) = today.plusDays(n).atTime(8, 0).atZone(zone).toInstant()
    private val forecasts = mapOf(1L to StockForecast(1, 6.0, 8, day(2), day(3)), 2L to StockForecast(2, 300.0, 30, day(20), day(21)))
    private val boxes = listOf(ContainerEntity(1, 1, 28.0, 0.0, 22.0, today.minusDays(11).toString(), "IN_USE"), ContainerEntity(2, 2, 30.0, 0.0, 0.0, today.minusDays(3).toString(), "IN_USE"),
        ContainerEntity(3, 2, 30.0, 0.0, 0.0, null, "SEALED"))
    private val json = MedicationSnapshot.encode(medA, ProfileEntity(1, "E2", "oral"))
    private fun version(id: Long, from: LocalDate, until: LocalDate?, dose: Double): RegimenVersionEntity {
        val d = RegimenDefinition(json, "EVERY_N_DAYS", 1, 0, dose, zone.id, from.toString(), null, listOf("08:00:00" to null, "20:00:00" to null))
        val ms = { x: LocalDate -> x.atStartOfDay(zone).toInstant().toEpochMilli() }
        return RegimenVersionEntity(id, 1, ms(from), until?.let(ms), zone.id, d.json(), d.signature(), "APP", ms(from))
    }
    private fun lab(id: Long, d: LocalDate, code: String, v: Double, unit: String) = LabValueEntity(id, code, v, unit, d.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(), zone.id)
    private val extraTimeline = NotesViewModel.ExtraState(regimens = listOf(version(1, today.minusDays(120), today.minusDays(40), 2.0), version(2, today.minusDays(40), null, 4.0)),
        labs = listOf(lab(1, today.minusDays(100), "E2", 95.0, "PG_ML"), lab(2, today.minusDays(20), "E2", 160.0, "PG_ML"), lab(3, today.minusDays(20), "T", 0.6, "NG_ML")),
        milestones = listOf(MilestoneEntity(7, today.minusDays(40).toString(), kind = "ROUTE")))
    private fun visit(id: Long, t: Instant, done: Boolean, type: String = "ENDO", note: String? = null) = AppointmentEntity(id, type, t.toEpochMilli(), zone.id,
        "Doctor placeholder", "Clinic placeholder", note, 0, if (done) t.toEpochMilli() else null)
    private val visits = listOf(visit(10, now.plus(Duration.ofDays(5)), false), visit(11, now.minus(Duration.ofDays(3)), false, "LAB"),
        visit(12, now.minus(Duration.ofDays(30)), true, note = "Note"), visit(13, now.minus(Duration.ofDays(400)), true, "OTHER"))

    @Test fun pages() {
        val label = System.getenv("UI05_SHOTS")
        assumeTrue("set UI05_SHOTS to render", !label.isNullOrBlank())
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-xxhdpi"); RuntimeEnvironment.setFontScale(font)
        var page by mutableIntStateOf(0)
        val open = listOf(entry(medA, nextAt), entry(medB, nextAt.plus(Duration.ofHours(1))))
        val todayRecords = listOf(record(1, medA, at(today, 8), "ON_TIME"), record(2, medB, at(today, 8), "MISSED"))
        rule.setContent { NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            androidx.compose.runtime.key(page) { when (page) {
                0 -> CalendarScreen(NotesState(listOf(medA, medB), open, emptyList(), loading = false, calendarStart = today), today, {}, {}, {}, {}, PaddingValues(),
                    extra = NotesViewModel.ExtraState(records = todayRecords, upcoming = open, stockForecasts = forecasts))
                1 -> StockScreen(NotesState(listOf(medA, medB), loading = false, schedules = schedules), boxes, emptyList(), {}, {}, {}, PaddingValues(),
                    forecasts = forecasts, today = today, zone = zone)
                2 -> Column {
                    val host = remember { TimelineAddHost() } // UI-05:
                    TopAppBar(title = { Text(stringResource(R.string.timeline)) }, actions = { TimelineAddMenu(host) /* UI-05: */ })
                    LongitudinalScreen(NotesState(listOf(medA), appointments = listOf(visits[0]), loading = false), extraTimeline, {}, {}, {}, PaddingValues(),
                        addHost = host /* UI-05: */)
                }
                3 -> DrawerContent(Destination.CALENDAR, stockLow = 1 /* UI-05: */) {}
                else -> Column {
                    TopAppBar(title = { Text(stringResource(R.string.medical_records)) }, actions = { MedicalAddAction {} /* UI-05: */ })
                    MedicalRecordsScreen(visits, {}, {}, {}, {}, {}, PaddingValues(), questionCounts = mapOf(10L to 3, 12L to 2), now = now)
                }
            } }
        } } }
        val dir = File("build/ui05-shots/$label").apply { mkdirs() }
        fun shot(name: String) {
            rule.waitForIdle()
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(dir, "${name}_${locale}_${width}dp_${if (font == 1f) "1x" else "2x"}_${if (dark) "dark" else "light"}.png").outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        listOf("1_home", "2_stock", "3_timeline", "4_drawer", "5_medical").forEachIndexed { i, name ->
            rule.runOnIdle { page = i }; rule.waitForIdle()
            shot("${name}_top")
            if (i == 2) { runCatching { rule.onNode(hasScrollToIndexAction()).performScrollToIndex(4) }; shot("${name}_scrolled") }
        }
    }
}
