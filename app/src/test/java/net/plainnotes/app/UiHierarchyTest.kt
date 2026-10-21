package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
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
import java.time.format.DateTimeFormatter

/** UI-01 C: each dose state on the month grid has a shape as well as a colour; the spoken description is unchanged. */
@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk = [35])
class CalendarDayMarkTest(private val locale: String) {
    companion object { @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}") fun locales() = listOf("en", "zh-rCN", "zh-rTW", "fr").map { arrayOf<Any>(it) } }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 3, 10)
    private fun at(d: LocalDate) = d.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
    private fun record(id: Long, d: LocalDate, status: String) = RecordEntity(id, 1, scheduled_utc = at(d), taken_utc = if (status == "MISSED") null else at(d),
        taken_zone = zone.id, actual_dose = 2.0, status = status, origin = "APP", revision = 1, config_snapshot = "{}")

    @Test fun everyStateHasAShape() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h1800dp-xxhdpi"); RuntimeEnvironment.setFontScale(2f)
        val med = MedicationEntity(1, "Synthetic E2", "E2", "ORAL", "MG", 2.0, 30.0, null, 15, 120, false, null, true, true, 0)
        val records = listOf(record(1, today.minusDays(1), "ON_TIME"), record(2, today.minusDays(1), "MISSED"), record(3, today.minusDays(2), "MISSED"), record(4, today.minusDays(3), "ON_TIME"))
        ui.setContent { NotesTheme(ThemeMode.DARK) { androidx.compose.material3.Surface {
            CalendarScreen(NotesState(listOf(med), calendarStart = today), today, {}, {}, {}, {}, PaddingValues(), extra = NotesViewModel.ExtraState(records = records),
                initialFull = true)  // UI-07 F: month grid = full calendar, opened explicitly
        } } }
        val l = ui.activity.resources.configuration.locales[0]
        fun node(d: LocalDate) = ui.onNode(hasContentDescription(d.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(l, "yMMMMdEEEE"), l)), substring = true) and hasClickAction()).fetchSemanticsNode()
        fun mark(d: LocalDate) = node(d).config.getOrNull(DayMarkKey)
        assertEquals(DayMark.CHECK, mark(today.minusDays(3)))
        assertEquals(DayMark.HALF, mark(today.minusDays(1)))
        assertEquals(DayMark.CROSS, mark(today.minusDays(2)))
        assertNull(mark(today.plusDays(5)))
        // Words are still spoken; the shape is an extra, not a replacement.
        assertTrue(node(today.minusDays(2)).config[SemanticsProperties.ContentDescription].joinToString().contains(ui.activity.getString(R.string.legend_missed)))
        // UI-06: unconfirmed days now have their own ? mark.
        assertEquals(mapOf(DayKind.TAKEN to DayMark.CHECK, DayKind.PARTIAL to DayMark.HALF, DayKind.MISSED to DayMark.CROSS, DayKind.UNCONFIRMED to DayMark.QUESTION),
            DayKind.entries.mapNotNull { k -> dayMark(k)?.let { k to it } }.toMap())
    }
}

/** UI-01 E and F. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "w320dp-h640dp")
class TimelineAndDrawerHierarchyTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private fun s(id: Int, vararg a: Any) = ui.activity.getString(id, *a)

    @Test fun everyEventKindHasAnIconAndAWrittenType() {
        assertEquals(EventKind.entries.size, EventKind.entries.map(::eventKindIcon).toSet().size)
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { Column { EventKind.entries.forEach { k -> EventTypeBadge(k); Text(eventKindLabel(k)) } } } }
        EventKind.entries.forEach { k -> ui.onNodeWithTag("event-badge:${k.name}").assertExists() }
        assertEquals(EventKind.entries.size, ui.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).fetchSemanticsNodes().count { n ->
            n.config[SemanticsProperties.Text].joinToString().isNotBlank() })
    }

    @Test fun zoneLineOnlyWhenItDiffersFromThePhone() {
        val phone = ZoneId.of("Europe/Paris")
        var zone by androidx.compose.runtime.mutableStateOf(phone)
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { TimelineZoneNote(zone, phone) } }
        ui.onNodeWithTag("timeline-zone").assertDoesNotExist()
        zone = ZoneId.of("Asia/Tokyo"); ui.waitForIdle()
        ui.onNodeWithTag("timeline-zone").assertExists()
        ui.onAllNodesWithText("Asia/Tokyo", substring = true).assertCountEquals(0)        // a name, never the ID
    }

    @Test @Config(qualifiers = "w320dp-h640dp") fun drawerKeepsEveryOriginalEntryOnceUnderHeadings() {
        RuntimeEnvironment.setFontScale(2f)
        assertEquals(Destination.entries.toList().sortedBy { it.ordinal }, DRAWER_GROUPS.flatMap { it.items }.sortedBy { it.ordinal })
        val original=setOf(Destination.CALENDAR,Destination.TIMELINE,Destination.HISTORY,Destination.WELLBEING,Destination.MEDICATIONS,Destination.STOCK,Destination.CONCENTRATION,Destination.MEDICAL,Destination.VISITS,Destination.LABS,Destination.SETTINGS,Destination.ABOUT)
        val actual=DRAWER_GROUPS.flatMap {it.items}
        assertTrue(actual.containsAll(original))
        assertEquals(Destination.entries.size,actual.size)
        assertEquals(actual.size,actual.distinct().size)
        var picked: Destination? = null
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { DrawerContent(Destination.CALENDAR) { picked = it } } }
        val headings = ui.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).fetchSemanticsNodes()
        assertEquals(3, headings.size)                                            // UI-03: journal, medication, records
        for (d in Destination.entries) {
            ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(s(d.title)))
            ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(s(d.title))).assertIsDisplayed().performClick()
            assertEquals(d, picked)
        }
    }
}
