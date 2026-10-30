package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.timeline.EventKind
import net.plainnotes.app.timeline.PeriodTimelineProjection
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

/**
 * UI-05 D, synthetic data on the real LongitudinalScreen (display only): the two add actions live in the top bar "+" menu
 * and call the original callbacks; every event is drawn in the inset list of exactly the period the unchanged projection
 * gives it (or in the upcoming / unknown group); upcoming events are dashed cards; the current period has a pill and a
 * "since · day n" line; every EventKind keeps an icon and its name; type colours reach 3:1 in every theme and contrast.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class)
class Ui05TimelineTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.systemDefault()
    private val med = MedicationEntity(1, "Synthetic A", "E2", "SUBLINGUAL", "MG", 2.0, 40.0, site_rotation = false, notifications_on = false, active = true, sort_order = 0)
    private val start = LocalDate.now().minusDays(80)
    private fun at(d: Long) = start.plusDays(d).atStartOfDay(zone).toInstant().toEpochMilli()
    private val json = MedicationSnapshot.encode(med, ProfileEntity(1, "E2", "sublingual"))
    private fun version(id: Long, from: Long, until: Long?, dose: Double): RegimenVersionEntity {
        val d = RegimenDefinition(json, "EVERY_N_DAYS", 1, 0, dose, zone.id, start.toString(), null, listOf("08:00:00" to null, "20:00:00" to null))
        return RegimenVersionEntity(id, 1, at(from), until?.let(::at), zone.id, d.json(), d.signature(), "APP", at(from))
    }
    private fun lab(id: Long, day: Long) = LabValueEntity(id, "E2", 100.0 + id, "PG_ML", at(day) + 9 * 3_600_000, zone.id)
    private val extra = NotesViewModel.ExtraState(regimens = listOf(version(1, 0, 30, 2.0), version(2, 30, null, 3.0)),
        labs = listOf(lab(1, 10), lab(2, 45), lab(3, 70)),
        milestones = listOf(MilestoneEntity(7, start.plusDays(60).toString(), kind = "STARTED"), MilestoneEntity(8, start.minusDays(20).toString(), kind = "SURGERY")))
    private val future = AppointmentEntity(5, "FOLLOW_UP", Instant.now().plus(Duration.ofDays(5)).toEpochMilli(), zone.id, remind_minutes_before = 0)
    private val state = NotesState(medications = listOf(med), appointments = listOf(future), loading = false)
    private fun s(id: Int) = ui.activity.getString(id)

    private fun show(host: TimelineAddHost? = null, saved: (MilestoneEntity) -> Unit = {}) {
        ui.setContent { MaterialTheme { Column {
            val h = host ?: remember { TimelineAddHost() }
            TimelineAddMenu(h)
            LongitudinalScreen(state, extra, saved, {}, {}, PaddingValues(), addHost = h)
        } } }
        ui.waitForIdle()
    }

    @Test fun addMenuReplacesTheInlineButtonsAndOpensTheSameDialogs() {
        show()
        // No inline action buttons left in the list.
        ui.onNode(hasText(s(R.string.milestone_add)) and hasAnyAncestor(hasTestTag("period-timeline"))).assertDoesNotExist()
        ui.onNodeWithTag("timeline-add").assertContentDescriptionEquals(s(R.string.add)).performClick()
        ui.onNodeWithTag("timeline-add-milestone").assertTextEquals(s(R.string.milestone_add)).performClick()
        ui.onNode(hasText(s(R.string.milestone_add)) and hasAnyAncestor(isDialog())).assertExists()
        ui.onNode(hasText(s(R.string.cancel)) and hasAnyAncestor(isDialog())).performClick()
        ui.onNodeWithTag("timeline-add").performClick()
        ui.onNodeWithTag("timeline-new-period").assertTextEquals(s(R.string.timeline_new_period)).performClick()
        ui.onNodeWithTag("period-edit-dialog").assertExists()
    }

    /** The menu item calls exactly the callback the timeline registered, and is disabled when that was disabled. */
    @Test fun newPeriodMenuItemCallsTheRegisteredCallback() {
        var calls = 0
        val host = TimelineAddHost().apply { period = { calls++ }; periodEnabled = true }
        ui.setContent { MaterialTheme { TimelineAddMenu(host) } }
        ui.onNodeWithTag("timeline-add").performClick()
        ui.onNodeWithTag("timeline-new-period").assertTextEquals(s(R.string.timeline_new_period)).performClick()
        assertEquals(1, calls)
        ui.runOnIdle { host.periodEnabled = false }
        ui.onNodeWithTag("timeline-add").performClick()
        ui.onNodeWithTag("timeline-new-period").assertIsNotEnabled()
    }

    /** The timeline registers its own add actions, enabled exactly as the former buttons were. */
    @Test fun timelineRegistersItsNewPeriodEditorWithTheFormerEnabledRule() {
        val host = TimelineAddHost()
        show(host)
        ui.runOnIdle { assertNotNull(host.period); assertTrue(host.periodEnabled); assertNotNull(host.milestone); assertTrue(host.milestoneEnabled) }
    }

    @Test fun everyEventIsDrawnInTheGroupTheProjectionGivesIt() {
        show()
        val record = PeriodTimelineProjection.build(extra, state.appointments, ruleSnapshots = state.ruleSnapshots)
        val expected = record.projection.periods.flatMap { p -> record.eventsIn(p).map { it.key to p.key } } +
            record.unknownEvents.map { it.key to "unknown" } + record.upcoming.map { it.key to "upcoming" }
        assertTrue("fixture has events in two periods, unknown and upcoming", expected.map { it.second }.distinct().size >= 4)
        expected.forEach { (event, group) ->
            ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag("timeline:$event"))
            ui.onNode(hasTestTag("timeline:$event") and hasAnyAncestor(hasTestTag("timeline-group:$group"))).assertExists()
        }
    }

    @Test fun upcomingEventsAreDashedCards() {
        show()
        val key = PeriodTimelineProjection.build(extra, state.appointments).upcoming.single().key
        ui.onNode(hasTestTag("timeline:$key") and hasAnyAncestor(hasTestTag("timeline-upcoming-card"))).assertExists()
    }

    @Test fun currentPeriodHasAPillAndASinceDayLine() {
        show()
        val current = PeriodTimelineProjection.build(extra, state.appointments).projection.periods.last()
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag("period-day:${current.key}"))
        ui.onNode(hasText(s(R.string.period_current)) and hasAnyAncestor(hasTestTag("period-pill:${current.key}"))).assertExists()
        ui.onNodeWithTag("period-since:${current.key}").assertExists()
    }

    @Test fun everyEventKindHasAnIconAndAName() {
        ui.setContent { MaterialTheme { Column { EventKind.entries.forEach { k -> EventTypeBadge(k); androidx.compose.material3.Text(eventKindLabel(k)) } } } }
        EventKind.entries.forEach { k ->
            ui.onNodeWithTag("event-badge:${k.name}").assertExists()
        }
        val names = EventKind.entries.map { k -> ui.activity.getString(eventKindLabelRes(k)) }
        assertTrue(names.all { it.isNotBlank() }); assertEquals(names.size, names.distinct().size)
    }

    /**
     * UI-13 (old meaning -> new meaning): a lab event used to be coral (tertiary container), a visit the teal-grey secondary container and a
     * stage review the inverse surface. Now lab is the purple accent, visit blue (owner swapped blue and purple on 2026-10-30) and stage review rose; the milestone stays teal and coral is
     * only the reminder colour.
     */
    @Test fun typeColoursAreTheAccentsAndCoralIsNoLongerATypeColour() {
        for (dark in listOf(false, true)) {
            val c = notesColorScheme(dark); val a = accentColors(c, dark)
            assertEquals(a.purple.container to a.purple.onContainer, eventTypeColors(EventKind.LAB, c))
            assertEquals(a.blue.container to a.blue.onContainer, eventTypeColors(EventKind.APPOINTMENT, c))
            assertEquals(a.rose.container to a.rose.onContainer, eventTypeColors(EventKind.REVIEW, c))
            assertEquals(c.primaryContainer to c.onPrimaryContainer, eventTypeColors(EventKind.MILESTONE, c))
            EventKind.entries.forEach { k -> assertTrue("$k must not use the coral reminder colour", eventTypeColors(k, c).first != c.tertiaryContainer) }
        }
    }

    @Test fun typeColoursReachThreeToOneInEveryThemeAndContrast() {
        val pairs = mutableListOf<Triple<String, Color, Color>>()
        ui.setContent { Column {
            listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode -> Contrast.entries.forEach { contrast ->
                NotesTheme(mode, false, contrast) {
                    val c = MaterialTheme.colorScheme
                    listOf(EventKind.LAB, EventKind.APPOINTMENT, EventKind.MILESTONE, EventKind.REVIEW, EventKind.SYMPTOM, EventKind.MISSED).forEach { k ->
                        val (bg, fg) = eventTypeColors(k, c); pairs += Triple("$mode/$contrast/$k", bg, fg)
                    }
                }
            } }
        } }
        ui.waitForIdle()
        fun ratio(a: Color, b: Color) = (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
        pairs.forEach { (name, bg, fg) -> assertTrue("$name icon contrast ${ratio(bg, fg)}", ratio(bg, fg) >= 3f) }
        // Five type colours (lab, visit, milestone, review, other) are distinct within each theme.
        pairs.groupBy { it.first.substringBeforeLast('/') }.values.forEach { group ->
            assertEquals(5, group.filter { !it.first.endsWith("MISSED") }.map { it.second }.distinct().size)
        }
    }
}
