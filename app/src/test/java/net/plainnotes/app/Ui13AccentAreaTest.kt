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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToIndex
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.Slot
import net.plainnotes.app.domain.SlotState
import net.plainnotes.app.domain.TimelineEntry
import net.plainnotes.app.ui.*
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
 * UI-13 area check (task book 5.4): the blue, purple and rose accents may only mark small elements, so on every page they cover at most
 * [MAX_ACCENT_SHARE] of the pixels, and on pages that get no accent at all (home, stock) they cover none. Real pixels are rendered with
 * native graphics from synthetic data and counted: a pixel is "accent" when it equals, exactly, one of the accent tone colours of the
 * current theme (flat fills, so anti-aliased edges simply do not count and the share is a slight under-estimate of the edge only).
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35])
class Ui13AccentAreaTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
    companion object {
        /** The upper limit written into the UI-13 report; a page above it fails. */
        const val MAX_ACCENT_SHARE = 0.08
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}")
        fun params() = listOf("en", "fr-rFR").flatMap { l -> listOf(320 to 2f, 411 to 1f).flatMap { (w, f) -> listOf(false, true).map { arrayOf<Any>(l, w, f, it) } } }
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

    private val checkinItems = listOf(CheckinItemEntity(1, "DAY_MOOD", null, true, 0), CheckinItemEntity(2, "DAY_ENERGY", null, true, 1), CheckinItemEntity(3, "DAY_SLEEP", null, true, 2))
    private fun accentShare(bitmap: Bitmap, accents: AccentColors): Double {
        val tones = listOf(accents.blue, accents.purple, accents.rose).flatMap { listOf(it.main, it.container, it.onContainer, it.soft) }
            .map { it.toArgb() and 0xFFFFFF }.toHashSet()
        val pixels = IntArray(bitmap.width * bitmap.height); bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { (it and 0xFFFFFF) in tones }.toDouble() / pixels.size
    }
    private fun distinctColours(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height); bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.toHashSet().size
    }

    @Test fun accentsStayASmallShareOfEveryPage() {
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-xxhdpi"); RuntimeEnvironment.setFontScale(font)
        var page by mutableIntStateOf(0)
        val open = listOf(entry(medA, nextAt), entry(medB, nextAt.plus(Duration.ofHours(1))))
        val todayRecords = listOf(record(1, medA, at(today, 8), "ON_TIME"), record(2, medB, at(today, 8), "MISSED"))
        var accents: AccentColors? = null
        rule.setContent { NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            accents = NotesTheme.accent
            androidx.compose.runtime.key(page) { when (page) {
                0 -> CalendarScreen(NotesState(listOf(medA, medB), open, emptyList(), loading = false, calendarStart = today), today, {}, {}, {}, {}, PaddingValues(),
                    extra = NotesViewModel.ExtraState(records = todayRecords, upcoming = open, stockForecasts = forecasts))
                1 -> StockScreen(NotesState(listOf(medA, medB), loading = false, schedules = schedules), boxes, emptyList(), {}, {}, {}, PaddingValues(),
                    forecasts = forecasts, today = today, zone = zone)
                2 -> Column {
                    val host = remember { TimelineAddHost() }
                    TopAppBar(title = { Text(stringResource(R.string.timeline)) }, actions = { TimelineAddMenu(host) })
                    LongitudinalScreen(NotesState(listOf(medA), appointments = listOf(visits[0]), loading = false), extraTimeline, {}, {}, {}, PaddingValues(), addHost = host)
                }
                3 -> DrawerContent(Destination.CALENDAR, stockLow = 1) {}
                4 -> LabsScreen(extraTimeline.labs, emptyList(), {}, {}, PaddingValues())
                5 -> { val sc = net.plainnotes.app.ui12.Ui12Fixture.sublingualCalibrated()
                    ConcentrationScreen(sc.state, sc.result, false, 70.0, ConcSettings(false, true, net.plainnotes.app.pk.CalibrationMode.RETROSPECTIVE), {}, {}, {}, {}, PaddingValues(),
                        sc.records, sc.state.profiles, preview = sc.preview, e2NonSublingual = sc.nonSublingual, doseTimes = sc.doseTimes, labs = sc.labs) }
                6 -> WellbeingHubContent(NotesState(loading = false), NotesViewModel.ExtraState(items = checkinItems, scores = listOf(CheckinScoreEntity(today.toString(), 1, 4))), "FR",
                    PaddingValues(), { _, _, _ -> }, { _, _ -> }, { _, _, _, _ -> }, {}, {})
                else -> MedicalRecordsScreen(visits, {}, {}, {}, {}, {}, PaddingValues(), questionCounts = mapOf(10L to 3, 12L to 2), now = now)
            } }
        } } }
        val names = listOf("home", "stock", "timeline", "drawer", "labs", "concentration", "wellbeing", "medical"); val shares = mutableMapOf<String, Double>()
        names.forEachIndexed { i, name ->
            rule.runOnIdle { page = i }; rule.waitForIdle()
            if (name == "concentration") rule.waitUntil(30_000) { rule.onAllNodesWithTag("calibration-switch").fetchSemanticsNodes().isNotEmpty() }
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            rule.runOnUiThread { view.draw(android.graphics.Canvas(bitmap)) }
            assertTrue("$name rendered real pixels (${distinctColours(bitmap)} distinct colours)", distinctColours(bitmap) > 3)
            shares[name] = accentShare(bitmap, accents!!)
        }
        println("UI13_AREA $locale/${width}dp/${font}x/dark=$dark " + shares.entries.joinToString(" ") { "${it.key}=${"%.4f".format(it.value)}" })
        assertEquals("home must carry no accent", 0.0, shares.getValue("home"), 0.0)
        assertEquals("stock must carry no accent", 0.0, shares.getValue("stock"), 0.0)
        assertTrue("the drawer's icon tiles are drawn in accent colours (${shares["drawer"]})", shares.getValue("drawer") > 0.0)
        // These only prove the test really sees the page's accent tiles; at 320 dp with the largest font the first screen of the mood page is
        // filled by its day card, so its items are below the fold there. The upper limit below holds everywhere, unchanged.
        for (page in listOf("labs", "medical") + if (width >= 411) listOf("wellbeing") else emptyList())
            assertTrue("$page carries its accent (${shares[page]})", shares.getValue(page) > 0.0)
        shares.forEach { (name, share) -> assertTrue("$name accent share $share exceeds $MAX_ACCENT_SHARE", share <= MAX_ACCENT_SHARE) }
    }
}
