package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.timeline.*
import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.*

/** LAB-04 rules on the grouping itself (no screen). */
class Lab04GroupingTest {
    private val f = Lab04Fixture
    private fun ids(d: TimelineLabDraw) = d.draw.labs.map { it.id }.toSet()

    @Test fun sameGroupsAsTheLabsPage() {
        val t = PeriodTimelineProjection.build(f.extra(), emptyList())
        val merged = TimelineLabDraws.mergeable(t.events, t.upcoming, listOf(f.panel)).values.distinct()
        // The labs page's own groups of two or more, one by one.
        val page = labDraws(f.labs, listOf(f.panel)).filter { it.labs.size > 1 }
        assertEquals(page.map { d -> d.labs.map { it.id } }.toSet(), merged.map { d -> d.draw.labs.map { it.id } }.toSet())
        merged.forEach { d -> assertEquals(page.single { it.labs.map { l -> l.id } == d.draw.labs.map { l -> l.id } }, d.draw) }
        assertEquals(setOf(setOf(1L, 2, 3, 4, 5, 6), setOf(30L, 31)), merged.map(::ids).toSet())
    }

    @Test fun panelSixToOneLooseByTimeAndLaboratory() {
        val t = PeriodTimelineProjection.build(f.extra(), emptyList())
        val period = t.projection.periods.single { p -> t.eventsIn(p).any { it.kind == EventKind.LAB } }
        val rows = TimelineLabDraws.fold(t.eventsIn(period), TimelineLabDraws.mergeable(t.events, t.upcoming, listOf(f.panel))).filter {
            it is TimelineRow.Draw || (it as TimelineRow.One).event.kind == EventKind.LAB }
        // Panel (6 → 1), the single PRL, 30+31 (same time and laboratory), 32 (other laboratory), 33 (same day, other time).
        assertEquals(5, rows.size)
        val draws = rows.filterIsInstance<TimelineRow.Draw>().map { it.draw }
        assertEquals(setOf("lab-draw:panel:1", "lab-draw:group:30"), draws.map { it.key }.toSet())
        assertEquals(setOf("lab:20", "lab:32", "lab:33"), rows.filterIsInstance<TimelineRow.One>().map { it.event.key }.toSet())
        // Every result is still reachable: merged ones through their draw, the rest as before.
        assertEquals(f.labs.map { it.id }.toSet(), draws.flatMap { d -> d.events.map { (it.source as EventSource.Lab).value.id } }.toSet() +
            rows.filterIsInstance<TimelineRow.One>().map { (it.event.source as EventSource.Lab).value.id })
        // Each merged row belongs to the period its results belonged to.
        draws.forEach { d -> assertTrue(d.events.all { it.displayPeriodKey == period.key }) }
    }

    @Test fun oneResultStaysASingleRow() {
        val e = f.event(f.single, "p")
        val panelOfOne = LabPanelEntity(7, "u7", f.single.sampled_utc, "UTC", null, null, 0)
        val inPanel = f.single.copy(panel_id = 7)
        for ((event, panels) in listOf(e to emptyList(), f.event(inPanel, "p") to listOf(panelOfOne))) {
            val m = TimelineLabDraws.mergeable(listOf(event), emptyList(), panels)
            assertTrue(m.isEmpty()); assertEquals(listOf(TimelineRow.One(event)), TimelineLabDraws.fold(listOf(event), m))
        }
    }

    @Test fun membersInDifferentPeriodsAreNotMerged() {
        // Inconsistent synthetic data: one panel whose results sit in two periods (or one upcoming, one past).
        val a = f.event(f.panelLabs[0], "period-a"); val b = f.event(f.panelLabs[1], "period-b")
        val labs = f.panelLabs.take(2)
        assertTrue(TimelineLabDraws.mergeable(listOf(a, b), emptyList(), listOf(f.panel)).isEmpty())
        assertTrue(TimelineLabDraws.mergeable(listOf(f.event(labs[0], "p")), listOf(f.event(labs[1], "p")), listOf(f.panel)).isEmpty())
        assertEquals(listOf(TimelineRow.One(a)), TimelineLabDraws.fold(listOf(a), emptyMap()))
        // The same two in one period do merge.
        assertEquals(1, TimelineLabDraws.mergeable(listOf(f.event(labs[0], "p"), f.event(labs[1], "p")), emptyList(), listOf(f.panel)).values.distinct().size)
    }

    @Test fun outOfRangeCountIsLabFlag() {
        val t = PeriodTimelineProjection.build(f.extra(), emptyList())
        val panel = TimelineLabDraws.mergeable(t.events, t.upcoming, listOf(f.panel)).values.first { it.draw.panel != null }
        assertEquals(f.panelLabs.count { labFlag(it) != null }, panel.outOfRange); assertEquals(2, panel.outOfRange)
        assertEquals(0, TimelineLabDraws.mergeable(t.events, t.upcoming, listOf(f.panel)).values.first { it.draw.panel == null }.outOfRange)
    }

    @Test fun noRedInTheMergedRow() {
        val src = File("src/main/java/net/plainnotes/app/ui/TimelineLabDraws.kt").readText()
        assertFalse(Regex("""colorScheme\.(error|errorContainer|onError|onErrorContainer)\b""").containsMatchIn(src))
        assertTrue(src.contains("NotesTheme.semantic.attention"))
    }
}

/** LAB-04 on the timeline screen: one row per draw, the filter, taps. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h2400dp")
class Lab04TimelineUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val f = Lab04Fixture
    private var panelOpened: Long? = null
    private val opened = mutableListOf<EventKind>()

    private fun show() {
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { LongitudinalScreen(NotesState(loading = false), f.extra(), {}, {}, { opened += it }, PaddingValues(), onLabPanel = { panelOpened = it }) } }
        ui.waitForIdle()
    }
    private fun scrollTo(tag: String) = ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag(tag))
    private fun labRows() = ui.onAllNodes(SemanticsMatcher("timeline lab row") { n ->
        n.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.let { it.startsWith("timeline:lab:") || it.startsWith("timeline:lab-draw:") } == true }).fetchSemanticsNodes()

    @Test fun oneRowPerDrawAndTheLabFilterCountsMergedRows() {
        show()
        ui.onNode(hasText(ui.activity.getString(R.string.timeline_filter_labs)) and hasClickAction()).performClick(); ui.waitForIdle()
        val tags = labRows().map { it.config[SemanticsProperties.TestTag] }.toSet()
        assertEquals(setOf("timeline:lab-draw:panel:1", "timeline:lab-draw:group:30", "timeline:lab:20", "timeline:lab:32", "timeline:lab:33"), tags)
        (1L..6L).forEach { ui.onNodeWithTag("timeline:lab:$it").assertDoesNotExist() }
        // "2 out of range" on the panel row only (it is part of the row's one spoken sentence).
        val out = ui.activity.getString(R.string.timeline_lab_draw_spoken_out, 2)
        fun said(tag: String) = ui.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertTrue(said("timeline:lab-draw:panel:1").endsWith(out)); assertFalse(said("timeline:lab-draw:group:30").contains(out))
    }

    @Test fun tapsOpenPanelLabsPageOrTheSingleDetail() {
        show()
        scrollTo("timeline:lab-draw:panel:1"); ui.onNodeWithTag("timeline:lab-draw:panel:1").performClick()
        assertEquals(1L, panelOpened); assertTrue(opened.isEmpty())
        scrollTo("timeline:lab-draw:group:30"); ui.onNodeWithTag("timeline:lab-draw:group:30").performClick()
        assertEquals(listOf(EventKind.LAB), opened)
        // A single result opens its own detail on the timeline, as before (no navigation).
        scrollTo("timeline:lab:20"); ui.onNodeWithTag("timeline:lab:20").performClick()
        ui.onNodeWithTag("timeline-event-time").assertExists(); assertEquals(listOf(EventKind.LAB), opened)
    }
}

/** LAB-04 TalkBack: the merged row reads as one sentence, in four languages. */
@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class)
class Lab04SpokenTest(private val locale: String) {
    companion object { @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}") fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr").map { arrayOf<Any>(it) } }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val f = Lab04Fixture

    @Test fun mergedRowSpeaksTypeCountDatePlaceNamesAndRange() {
        RuntimeEnvironment.setQualifiers("$locale-w411dp-h2400dp")
        var date = ""; var names = emptyList<String>()
        ui.setContent { NotesTheme(ThemeMode.LIGHT) {
            date = formatDate(LocalDate.ofInstant(Instant.ofEpochMilli(f.t1), f.zone)); names = labDraws(f.panelLabs, listOf(f.panel)).single().labs.map { analyteLabel(it.analyte_code) }
            LongitudinalScreen(NotesState(loading = false), f.extra(), {}, {}, {}, PaddingValues()) } }
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag("timeline:lab-draw:panel:1"))
        val c = ui.activity; val join = c.getString(R.string.calendar_a11y_join)
        val expected = listOf(c.getString(R.string.timeline_lab_draw_spoken_title, 6), date, "Synthetic Lab A", names.joinToString(c.getString(R.string.lab_names_join)),
            c.getString(R.string.timeline_lab_draw_spoken_out, 2)).joinToString(join)
        val node = ui.onNodeWithTag("timeline:lab-draw:panel:1").fetchSemanticsNode()
        assertEquals(listOf(expected), node.config[SemanticsProperties.ContentDescription])
        assertEquals(androidx.compose.ui.semantics.Role.Button, node.config[SemanticsProperties.Role])
        // A draw with nothing out of range does not add the range words.
        val loose = ui.onNodeWithTag("timeline:lab-draw:group:30").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertFalse(loose.contains(c.getString(R.string.timeline_lab_draw_spoken_out, 0).trim()))
        assertTrue(loose.startsWith(c.getString(R.string.timeline_lab_draw_spoken_title, 2)))
    }
}
