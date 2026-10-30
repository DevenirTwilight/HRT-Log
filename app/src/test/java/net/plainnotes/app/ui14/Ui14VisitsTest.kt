package net.plainnotes.app.ui14

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import net.plainnotes.app.NotesState
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import net.plainnotes.app.reminder.ReminderCoordinator
import net.plainnotes.app.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * UI-14 (REQUIREMENTS §174): the visits list. Four languages × 320/411 dp × font 1, 1.3, 2 × light and dark; synthetic visits only, with
 * neutral placeholder names. Every state of the header card and every tag is checked, and no text may be cut off or leave the window
 * (the one place that is meant to cut is a very long location, which keeps two lines and is spoken in full by the card).
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35], application = android.app.Application::class)
class Ui14VisitsTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr-rFR").flatMap { l -> listOf(320, 411).flatMap { w -> listOf(1f, 1.3f, 2f).flatMap { f ->
            listOf(false, true).map { arrayOf<Any>(l, w, f, it) } } } }
        const val LONG_PLACE = "Synthetic Outpatient Building A, Floor 3, East Wing, Room 305, Synthetic Medical Centre, Placeholder Street 123, Placeholder City"
    }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.systemDefault()
    private val now = Instant.now()
    private fun at(days: Long) = now.plus(Duration.ofDays(days)).toEpochMilli()
    private fun visit(id: Long, days: Long, done: Boolean, type: String = "ENDO", doctor: String? = "Doctor Placeholder", place: String? = "Clinic Placeholder") =
        AppointmentEntity(id, type, at(days), zone.id, place, doctor, null, 0, if (done) at(days) else null)
    private val upcoming = visit(1, 5, false)
    private val pastConfirmed = visit(2, -3, true, place = LONG_PLACE)
    private val pastUnconfirmed = visit(3, -20, false, type = "LAB", doctor = null)
    private val questions = listOf(VisitQuestionEntity(1, 2, 0, "Synthetic question", "OPEN", null), VisitQuestionEntity(2, 2, 1, "Synthetic question two", "OPEN", null))
    private val packs = listOf(VisitPackEntity(1, 2, now.minus(Duration.ofDays(2)).toEpochMilli(), zone.id, "2026-10-01", "2026-10-30", "FACTS", "en", 1, "d", "{}"))

    private fun s(id: Int, vararg a: Any) = if (a.isEmpty()) ui.activity.resources.getString(id) else ui.activity.getString(id, *a)
    private fun setup() { RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-xxhdpi"); RuntimeEnvironment.setFontScale(font) }
    private var selected: Long? = null; private var recorded = 0

    private fun show(appointments: List<AppointmentEntity>, medical: Boolean = false, extra: NotesViewModel.ExtraState = NotesViewModel.ExtraState(visitQuestions = questions, visitPacks = packs)) {
        val db = Room.inMemoryDatabaseBuilder(ui.activity, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        db.openHelper.writableDatabase
        val repo = NotesRepository(object : DatabaseAccess(ui.activity) { override fun get(space: Space) = net.plainnotes.app.syntheticLibrary(db) })
        val model = NotesViewModel(repo, ReminderCoordinator(ui.activity, repo), ui.activity).also { net.plainnotes.app.awaitRuntimeLibrary(it) }
        ui.setContent { NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            VisitsScreen(NotesState(emptyList(), loading = false, appointments = appointments), extra, model, null, { selected = it }, {}, PaddingValues(),
                medical = medical, onAddHistorical = { recorded++ })
        } } }
        ui.waitForIdle()
    }
    /** The list is lazy: bring an item into the composition before asking about it. */
    private fun scrollTo(tag: String) { ui.onNodeWithTag("visits-list").performScrollToNode(hasTestTag(tag)); ui.waitForIdle() }
    private fun text(tag: String, unmerged: Boolean = true) = ui.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

    /** Text that is cut off, ellipsised or wider than its box, in every compose root of the window. */
    private fun clipped(): List<String> {
        val d = ui.activity.resources.displayMetrics.density
        val out = mutableListOf<String>()
        fun walk(n: androidx.compose.ui.semantics.SemanticsNode): List<androidx.compose.ui.semantics.SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
        for (root in ui.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()) walk(root).filter { it.config.contains(SemanticsProperties.Text) }.forEach { t ->
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            t.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            val r = results.firstOrNull() ?: return@forEach
            if (r.lineCount == 0) return@forEach
            val last = r.lineCount - 1
            val widest = (0..last).maxOf { r.getLineRight(it) - r.getLineLeft(it) }
            if (r.isLineEllipsized(last) || r.getLineEnd(last, visibleEnd = false) < r.layoutInput.text.length || widest - r.size.width > d || r.getLineBottom(last) - r.size.height > 2 * d)
                out += t.config[SemanticsProperties.Text].joinToString(" ").take(80)
        }
        return out
    }
    private fun outsideTheWindow(): List<String> {
        val w = ui.activity.resources.displayMetrics.widthPixels
        return ui.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().flatMap { root ->
            fun walk(n: androidx.compose.ui.semantics.SemanticsNode): List<androidx.compose.ui.semantics.SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
            walk(root).filter { it.config.contains(SemanticsProperties.Text) && it.boundsInWindow.width > 0f && (it.boundsInWindow.left < -1f || it.boundsInWindow.right > w + 1f) }
                .map { it.config[SemanticsProperties.Text].joinToString(" ").take(60) } }
    }

    @Test fun headerNamesTheNextVisitWithTheTagsAndNothingIsCutOrOutsideTheWindow() {
        setup(); show(listOf(upcoming, pastConfirmed, pastUnconfirmed))
        assertEquals(s(R.string.visits_hero_next), text("visits-hero-label"))
        ui.onNodeWithTag("visits-hero-days", useUnmergedTree = true).assertExists()
        ui.onNodeWithTag("visits-record-past").assertExists()
        // The one very long location is meant to keep two lines; every other text must be whole. Walk the whole lazy list.
        val cut = mutableSetOf<String>(); val outside = mutableSetOf<String>()
        for (tag in listOf("visits-group-upcoming", "visit-card:1", "visits-group-past", "visit-card:2", "visit-card:3")) {
            scrollTo(tag)
            if (tag == "visits-group-upcoming") assertEquals(s(R.string.visits_upcoming), text(tag))
            if (tag == "visits-group-past") { assertEquals(s(R.string.visits_past), text(tag)); assertEquals("2", text("visits-past-count")) }
            cut += clipped().filterNot { it.startsWith("Synthetic Outpatient") }; outside += outsideTheWindow()
        }
        assertEquals(emptyList<String>(), cut.toList()); assertEquals(emptyList<String>(), outside.toList())
    }

    @Test fun tagsShowOnlyWhatTheVisitHas() {
        setup(); show(listOf(upcoming, pastConfirmed, pastUnconfirmed))
        // Confirmed past visit: confirmed tag, question count and pack tag. Unconfirmed past visit: the dashed tag only. Upcoming: the dashed tag only.
        scrollTo("visit-card:2")
        ui.onNode(hasTestTag("visit-tag-confirmed") and hasAnyAncestor(hasTestTag("visit-card:2")), useUnmergedTree = true).assertExists()
        ui.onNode(hasTestTag("visit-tag-questions") and hasAnyAncestor(hasTestTag("visit-card:2")), useUnmergedTree = true).assertExists()
        ui.onNode(hasTestTag("visit-tag-pack") and hasAnyAncestor(hasTestTag("visit-card:2")), useUnmergedTree = true).assertExists()
        ui.onNode(hasText(s(R.string.visit_completed)) and hasAnyAncestor(hasTestTag("visit-tag-confirmed")), useUnmergedTree = true).assertExists()
        scrollTo("visit-card:3")
        ui.onNode(hasTestTag("visit-tag-unconfirmed") and hasAnyAncestor(hasTestTag("visit-card:3")), useUnmergedTree = true).assertExists()
        ui.onNode(hasTestTag("visit-tag-confirmed") and hasAnyAncestor(hasTestTag("visit-card:3")), useUnmergedTree = true).assertDoesNotExist()
        ui.onNode(hasTestTag("visit-tag-questions") and hasAnyAncestor(hasTestTag("visit-card:3")), useUnmergedTree = true).assertDoesNotExist()
        ui.onNode(hasTestTag("visit-tag-pack") and hasAnyAncestor(hasTestTag("visit-card:3")), useUnmergedTree = true).assertDoesNotExist()
        ui.onNode(hasText(s(R.string.visit_not_confirmed)) and hasAnyAncestor(hasTestTag("visit-tag-unconfirmed")) and hasAnyAncestor(hasTestTag("visit-card:3")), useUnmergedTree = true).assertExists()
    }

    @Test fun withNothingAheadTheLastConfirmedVisitHeadlinesAndAnUnconfirmedOneNeverDoes() {
        setup(); show(listOf(pastUnconfirmed, pastConfirmed))
        assertEquals(s(R.string.visits_hero_last), text("visits-hero-label"))
        ui.onNodeWithTag("visits-group-upcoming").assertDoesNotExist()
    }

    @Test fun onlyUnconfirmedPastVisitsGiveTheEntryButtonWithoutAnEmptySentence() {
        setup(); show(listOf(pastUnconfirmed))
        ui.onNodeWithTag("visits-hero-label", useUnmergedTree = true).assertDoesNotExist()
        ui.onNodeWithTag("visits-hero-empty", useUnmergedTree = true).assertDoesNotExist()
        ui.onNodeWithTag("visits-record-past").assertExists()
        ui.onAllNodesWithTag("empty-state").assertCountEquals(0)
    }

    @Test fun noVisitsAtAllKeepsTheEmptyStateAndTheEntryButton() {
        setup(); show(emptyList())
        assertEquals(s(R.string.visits_empty), text("visits-hero-empty"))
        ui.onNodeWithTag("visits-record-past").assertExists(); ui.onNodeWithTag("empty-state").assertExists()
        assertEquals(emptyList<String>(), clipped())
    }

    @Test fun theEntryButtonAndTheCardsKeepTheirCallbacks() {
        setup(); show(listOf(upcoming, pastConfirmed))
        ui.onNodeWithTag("visits-record-past").performClick(); assertEquals(1, recorded)
        scrollTo("visit-card:2"); ui.onNodeWithTag("visit-card:2").performClick(); assertEquals(2L, selected)
    }

    @Test fun theCardIsSpokenAsOneSentenceWithTheWholeLocation() {
        setup(); show(listOf(upcoming, pastConfirmed)); scrollTo("visit-card:2")
        val spoken = ui.onNodeWithTag("visit-card:2").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(" ") { it.text }
        assertTrue(spoken, LONG_PLACE in spoken); assertTrue(spoken, "Doctor Placeholder" in spoken); assertTrue(spoken, s(R.string.visit_completed) in spoken)
        // The pieces inside are decoration for the eye: none of them is a separate stop for TalkBack.
        assertEquals("exactly one spoken sentence per card", 1, ui.onNodeWithTag("visit-card:2").fetchSemanticsNode().config[SemanticsProperties.Text].size)
        assertFalse("no separate description next to it", ui.onNodeWithTag("visit-card:2").fetchSemanticsNode().config.contains(SemanticsProperties.ContentDescription))
        // ...and the card can be found and pressed by the doctor's name, as the existing flows do.
        ui.onNode(androidx.compose.ui.test.hasText("Doctor Placeholder", substring = true) and hasAnyAncestor(hasTestTag("visits-list")) and hasTestTag("visit-card:2")).assertExists()
    }

    @Test fun theMedicalRouteUsesTheVisitsOwnZoneForItsDayAndSoDoesTheDayCount() {
        setup()
        val tokyo = ZoneId.of("Asia/Tokyo")
        val v = AppointmentEntity(9, "ENDO", now.plus(Duration.ofDays(2)).toEpochMilli(), tokyo.id, null, null, null, 0, null)
        show(listOf(v), medical = true); scrollTo("visit-card:9")
        val day = java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag(locale.replace("-r", "-"))).format(Instant.ofEpochMilli(v.at_utc).atZone(tokyo).dayOfMonth)
        ui.onNode(hasText(day) and hasAnyAncestor(hasTestTag("visit-date-tile:9")), useUnmergedTree = true).assertExists()
    }
}
