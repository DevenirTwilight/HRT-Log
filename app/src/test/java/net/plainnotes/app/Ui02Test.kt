package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.timeline.EventKind
import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.*

/** UI-02 A1: timeline type filter (front-end only) and the home stock card's two actions. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "w411dp-h891dp")
class TimelineFilterAndHomeStockTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.systemDefault()
    private val now = Instant.now()
    private fun extra() = NotesViewModel.ExtraState(labs = listOf(LabValueEntity(8, "E2", 420.0, "pmol/L", now.minusSeconds(20 * 86400).toEpochMilli(), zone.id)),
        milestones = listOf(MilestoneEntity(5, LocalDate.now().minusDays(60).toString(), kind = "STARTED")))
    private val appt = AppointmentEntity(3, "ENDO", now.minusSeconds(10 * 86400).toEpochMilli(), zone.id, "Synthetic doctor", null, null, 0)
    private fun rows() = ui.onAllNodes(SemanticsMatcher("timeline row") { it.config.getOrNull(SemanticsProperties.TestTag)?.let { t -> t.startsWith("timeline:") && !t.startsWith("timeline:imported") } == true })
        .fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
    private fun scrollAll() { val list = ui.onNodeWithTag("period-timeline"); listOf("timeline:lab:8", "timeline:milestone:5").forEach { runCatching { list.performScrollToNode(hasTestTag(it)) } }; list.performScrollToIndex(0) }

    @Test fun eachFilterShowsOnlyItsKindAndSurvivesRecreation() {
        val restore = StateRestorationTester(ui)
        restore.setContent { MaterialTheme { LongitudinalScreen(NotesState(loading = false, appointments = listOf(appt)), extra(), {}, {}, {}, PaddingValues()) } }
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag("timeline:lab:8"))
        ui.onNodeWithTag("period-timeline").performScrollToIndex(0)
        for (f in TimelineFilter.entries) {
            ui.onNodeWithTag("timeline-filter:${f.name}").assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f)).performClick(); ui.waitForIdle()
            ui.onNodeWithTag("timeline-filter:${f.name}").assertIsSelected()
            (TimelineFilter.entries - f).forEach { ui.onNodeWithTag("timeline-filter:${it.name}").assertIsNotSelected() }
            scrollAll()
            val keys = rows()
            when (f) {
                TimelineFilter.LAB -> assertEquals(listOf("timeline:lab:8"), keys)
                TimelineFilter.MILESTONE -> assertEquals(listOf("timeline:milestone:5"), keys)
                TimelineFilter.VISIT -> assertTrue(keys.toString(), keys.isNotEmpty() && keys.all { it.contains("appointment") })
                TimelineFilter.OTHER -> assertTrue(keys.toString(), keys.none { it.contains("lab") || it.contains("milestone") || it.contains("appointment") })
                TimelineFilter.ALL -> assertTrue(keys.toString(), keys.containsAll(listOf("timeline:lab:8", "timeline:milestone:5")) && keys.any { it.contains("appointment") })
            }
        }
        ui.onNodeWithTag("timeline-filter:LAB").performClick()
        restore.emulateSavedInstanceStateRestore(); ui.waitForIdle()
        ui.onNodeWithTag("timeline-filter:LAB").assertIsSelected()
        assertTrue(TimelineFilter.LAB.matches(EventKind.LAB) && !TimelineFilter.LAB.matches(EventKind.MILESTONE))
    }

    @Test fun homeStockRowHasViewAndAddWhichCallTheirCallbacks() {
        val m = MedicationEntity(1, "Synthetic E2", "E2", "ORAL", "MG", 2.0, 30.0, null, 15, 120, false, null, true, true, 0)
        val today = LocalDate.of(2026, 3, 10)
        fun day(n: Long) = today.plusDays(n).atTime(8, 0).atZone(zone).toInstant()
        var viewed = 0; var added: MedicationEntity? = null
        ui.setContent { MaterialTheme { CalendarScreen(NotesState(listOf(m), loading = false, calendarStart = today), today, {}, {}, {}, {}, PaddingValues(),
            extra = NotesViewModel.ExtraState(stockForecasts = mapOf(1L to StockForecast(1, 6.0, 8, day(2), day(3)))), onStock = { viewed++ }, onAddStock = { added = it }) } }
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("home-stock-add:1"))
        ui.onNodeWithTag("home-stock-add:1").assertTextEquals(ui.activity.getString(R.string.stock_add)).performClick()
        assertEquals(m, added)
        ui.onNodeWithTag("home-stock-view").performClick(); assertEquals(1, viewed)
    }
}

/** UI-02 A1: WCAG AA for the today card, low-stock card, event badges and calendar marks in every contrast mode, light and dark. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class Ui02ContrastTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private fun ratio(a: Color, b: Color): Double { val l1 = a.luminance() + 0.05; val l2 = b.luminance() + 0.05; return (maxOf(l1, l2) / minOf(l1, l2)).toDouble() }
    @Test fun keyPairsMeetAa() {
        val failures = mutableListOf<String>()
        var mode by mutableStateOf(ThemeMode.LIGHT); var contrast by mutableStateOf(Contrast.STANDARD)
        val seen = mutableMapOf<String, List<Pair<String, Double>>>()
        ui.setContent { NotesTheme(mode, contrast = contrast) {
            val c = MaterialTheme.colorScheme
            // (name, foreground, background, minimum): text 4.5; large/bold text and graphical marks 3.0.
            val pairs = listOf(
                Triple("today text", c.onPrimary to c.primary, 4.5), Triple("today button", c.primary to c.onPrimary, 4.5),
                Triple("today overdue", c.onErrorContainer to c.errorContainer, 4.5),
                Triple("low-stock text", c.onTertiaryContainer to c.tertiaryContainer, 4.5), Triple("low-stock add", c.onTertiary to c.tertiary, 4.5),
                Triple("low-stock view", c.onTertiaryContainer to c.tertiaryContainer, 4.5),
                Triple("badge default", c.onPrimaryContainer to c.primaryContainer, 3.0), Triple("badge lab", c.onTertiaryContainer to c.tertiaryContainer, 3.0),
                Triple("badge visit", c.onSecondaryContainer to c.secondaryContainer, 3.0), Triple("badge missed", c.onErrorContainer to c.errorContainer, 3.0),
                Triple("mark taken", c.onPrimaryContainer to c.primaryContainer, 3.0), Triple("mark partial", c.onTertiaryContainer to c.tertiaryContainer, 3.0),
                Triple("mark missed", c.error to c.errorContainer, 3.0), Triple("today date", c.onPrimary to c.primary, 3.0),
                Triple("settings attention", c.tertiary to c.surfaceContainerLow, 4.5))
            seen["$mode/$contrast"] = pairs.map { (n, fb, min) -> n to ratio(fb.first, fb.second).also { r -> if (r < min) failures += "$mode/$contrast $n ${"%.2f".format(r)} < $min" } }
            Text("x")
        } }
        for (m in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) for (k in Contrast.entries) { mode = m; contrast = k; ui.waitForIdle() }
        assertEquals(6, seen.size)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}

/** UI-02 A2: settings overview rows, summaries from the same state, and every moved section's callbacks. */
@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk = [35])
class SettingsOverviewTest(private val locale: String) {
    companion object { @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}") fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr-rFR").map { arrayOf<Any>(it) } }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private fun s(id: Int, vararg a: Any) = ui.activity.getString(id, *a)

    @Test fun overviewRowsAreReachableTallAndSpeakTitleWithSummary() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h640dp-xxhdpi"); RuntimeEnvironment.setFontScale(2f)
        var page by mutableStateOf(SettingsPage.OVERVIEW)
        val sums = SettingsSummaries("Synthetic region", "Lock summary", "Backup summary", "Bin summary")
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            SettingsScreen(Appearance(ThemeMode.SYSTEM, false), {}, false, {}, {}, {}, PaddingValues(), page = page, onPage = { page = it }, summaries = sums)
        } } }
        val d = ui.activity.resources.displayMetrics.density
        val expected = mapOf(SettingsPage.WELLBEING to "Synthetic region", SettingsPage.PRIVACY to "Lock summary", SettingsPage.DATA to "Backup summary", SettingsPage.BIN to "Bin summary")
        for (p in SettingsPage.entries - SettingsPage.OVERVIEW) {
            ui.onNodeWithTag("settings-page:OVERVIEW").performScrollToNode(hasTestTag("settings-row:${p.name}"))
            val row = ui.onNodeWithTag("settings-row:${p.name}").assertIsDisplayed().assertHasClickAction()
            val n = row.fetchSemanticsNode()
            assertTrue("${p.name} ${n.size.height / d}dp", n.size.height / d >= 56f - 0.5f)
            val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") ?: ""
            assertTrue("${p.name}: '$text'", text.contains(s(p.title)))
            expected[p]?.let { assertTrue("${p.name}: '$text'", text.contains(it)) }
        }
        // In Robolectric every reminder check reads as fine, so the summary is "all OK" — the same values the page shows.
        // The overview summary and the reminders page read the same checks: "n need attention" equals the page's Fix buttons.
        ui.onNodeWithTag("settings-page:OVERVIEW").performScrollToNode(hasTestTag("settings-row:REMINDERS"))
        val summary = ui.onNodeWithTag("settings-row:REMINDERS").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
        ui.onNodeWithTag("settings-row:REMINDERS").performClick(); ui.waitForIdle()
        assertEquals(SettingsPage.REMINDERS, page)
        val problems = ui.onAllNodesWithText(s(R.string.fix)).fetchSemanticsNodes().size
        assertEquals(3, problems + ui.onAllNodesWithText(s(R.string.check_ok)).fetchSemanticsNodes().size)
        assertTrue(summary, summary.contains(if (problems == 0) s(R.string.settings_all_ok) else s(R.string.settings_n_need_action, problems)))
    }

    @Test fun movedSectionsKeepTheirCallbacks() {
        RuntimeEnvironment.setQualifiers("$locale-w411dp-h891dp")
        var page by mutableStateOf(SettingsPage.REMINDERS)
        var tests = 0; val reliability = mutableListOf<Boolean>(); val prompt = mutableListOf<Boolean>(); val slots = mutableListOf<String>()
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            SettingsScreen(Appearance(ThemeMode.SYSTEM, false), {}, false, { reliability += it }, {}, { tests++ }, PaddingValues(), true, { prompt += it }, page = page,
                wellbeingSlot = { Text("wellbeing-slot"); slots += "W" }, privacySlot = { Text("privacy-slot") }, dataSlot = { Text("data-slot") }, binSlot = { Text("bin-slot") })
        } } }
        ui.onNodeWithText(s(R.string.test_reminder)).performScrollTo().performClick(); assertEquals(1, tests)
        ui.onNode(isToggleable() and hasAnyAncestor(hasTestTag("settings-page:REMINDERS"))).performScrollTo().performClick(); assertEquals(listOf(true), reliability)
        ui.onNodeWithText(s(R.string.reliability_limits_title)).performScrollTo().performClick()
        ui.onNodeWithText(s(R.string.reliability_limits)).assertExists()
        page = SettingsPage.WELLBEING; ui.waitForIdle()
        ui.onNode(isToggleable()).performClick(); assertEquals(listOf(false), prompt)
        ui.onNodeWithText("wellbeing-slot").assertExists()
        for ((p, t) in listOf(SettingsPage.PRIVACY to "privacy-slot", SettingsPage.DATA to "data-slot", SettingsPage.BIN to "bin-slot")) {
            page = p; ui.waitForIdle(); ui.onNodeWithText(t).assertExists()
            (listOf("privacy-slot", "data-slot", "bin-slot", "wellbeing-slot") - t).forEach { ui.onNodeWithText(it).assertDoesNotExist() }
        }
        page = SettingsPage.APPEARANCE; ui.waitForIdle()
        ui.onNodeWithText(s(R.string.theme)).assertExists(); ui.onAllNodesWithText(s(R.string.language), useUnmergedTree = true).onFirst().assertExists()
    }
}
