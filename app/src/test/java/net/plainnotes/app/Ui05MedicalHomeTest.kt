package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

/**
 * UI-05 F, synthetic visits only: "Record a past visit" moves to the top bar (same onAdd), the next-visit card has a date
 * tile, "next visit · in n days" and two inset rows (questions with their count, visit pack) that open the same visit,
 * and confirmed visits are an inset list per year. MedicalHomeTest keeps covering confirm/edit and "no file entry".
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "w411dp-h891dp")
class Ui05MedicalHomeTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val now = Instant.parse("2026-03-10T12:00:00Z")
    private fun visit(id: Long, at: Instant, done: Boolean, note: String? = null) = AppointmentEntity(id = id, type = "ENDO", at_utc = at.toEpochMilli(), at_zone = "UTC",
        practitioner = "Synthetic doctor $id", location = "Synthetic clinic", note = note, remind_minutes_before = 0, completed_utc = if (done) at.toEpochMilli() else null)
    private fun s(id: Int, vararg a: Any) = ui.activity.getString(id, *a)

    @Test fun addIsATopBarActionNotAListButton() {
        var added = 0
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { androidx.compose.foundation.layout.Column {
            MedicalAddAction { added++ }
            MedicalRecordsScreen(listOf(visit(1, now.minusSeconds(86400), true)), {}, {}, {}, {}, {}, PaddingValues(), now = now)
        } } }
        ui.onAllNodes(hasTestTag("medical-add") and hasAnyAncestor(hasTestTag("medical-list"))).assertCountEquals(0)
        ui.onNodeWithTag("medical-add").assertContentDescriptionEquals(s(R.string.medical_add_visit)).performClick()
        assertEquals(1, added)
    }

    @Test fun nextVisitCardHasADateTileCountdownAndTwoRowsToTheSameVisit() {
        val next = visit(2, now.plusSeconds(3 * 86400), false)
        val opened = mutableListOf<AppointmentEntity>()
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { MedicalRecordsScreen(listOf(next), { opened += it }, {}, {}, {}, {}, PaddingValues(),
            questionCounts = mapOf(2L to 3), now = now) } }
        ui.onNode(hasTestTag("medical-next-date") and hasAnyDescendant(hasText("13"))).assertExists()
        ui.onNode(hasText(s(R.string.medical_in_days, 3), substring = true) and hasAnyAncestor(hasTestTag("medical-next:2"))).assertExists()
        ui.onNodeWithTag("medical-next-questions").assertTextContains(s(R.string.medical_next_questions, 3)).performClick()
        ui.onNodeWithTag("medical-next-pack").assertTextContains(s(R.string.medical_next_pack)).performClick()
        assertEquals(listOf(next, next), opened)
    }

    @Test fun confirmedVisitsAreAnInsetListPerYearAndOpenTheVisit() {
        val v26 = visit(7, Instant.parse("2026-01-05T10:00:00Z"), true, note = "Synthetic note"); val v25 = visit(6, Instant.parse("2025-05-01T10:00:00Z"), true)
        var opened: AppointmentEntity? = null
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { MedicalRecordsScreen(listOf(v26, v25), { opened = it }, {}, {}, {}, {}, PaddingValues(),
            questionCounts = mapOf(7L to 2), now = now) } }
        ui.onNode(hasTestTag("medical-visit:7") and hasAnyAncestor(hasTestTag("medical-year-list:2026"))).assertExists()
        ui.onNode(hasTestTag("medical-visit:6") and hasAnyAncestor(hasTestTag("medical-year-list:2025"))).assertExists()
        ui.onNode(hasTestTag("medical-visit:7") and hasText(s(R.string.medical_has_note) + " · " + s(R.string.medical_question_count, 2), substring = true)).assertExists()
        ui.onNodeWithTag("medical-visit:7").performClick()
        assertEquals(v26, opened)
    }
}
