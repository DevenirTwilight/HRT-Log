package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.reminder.ReminderCoordinator
import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

/** UI-01 G: the medical records home, synthetic visits only. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "w411dp-h891dp")
class MedicalHomeTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val now = Instant.parse("2026-03-10T12:00:00Z")
    private fun visit(id: Long, at: Instant, done: Boolean) = AppointmentEntity(id = id, type = "ENDO", at_utc = at.toEpochMilli(), at_zone = "UTC",
        practitioner = "Synthetic doctor $id", location = "Synthetic clinic", note = null, remind_minutes_before = 0, completed_utc = if (done) at.toEpochMilli() else null)
    private fun s(id: Int, vararg a: Any) = ui.activity.getString(id, *a)
    private val list get() = ui.onNodeWithTag("medical-list")

    @Test fun nextVisitIsOnlyTheNearestFutureOne() {
        val later = visit(3, now.plusSeconds(20 * 86400), false); val sooner = visit(2, now.plusSeconds(3 * 86400), false)
        var opened: AppointmentEntity? = null
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { MedicalRecordsScreen(listOf(later, sooner, visit(1, now.minusSeconds(86400), true)), { opened = it }, {}, {}, {}, {}, PaddingValues(), now = now) } }
        ui.onNodeWithTag("medical-next:2").assertExists()
        ui.onNodeWithTag("medical-next:3").assertDoesNotExist()
        ui.onNodeWithText("Synthetic doctor 3").assertDoesNotExist()       // not listed anywhere else either
        ui.onNodeWithTag("medical-next-open").performClick()
        assertEquals(sooner, opened)
    }

    @Test fun noFutureVisitMeansNoNextCard() {
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { MedicalRecordsScreen(listOf(visit(1, now.minusSeconds(86400), true)), {}, {}, {}, {}, {}, PaddingValues(), now = now) } }
        ui.onAllNodes(SemanticsMatcher("next") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("medical-next") == true }).assertCountEquals(0)
    }

    @Test fun unconfirmedVisitsOfferConfirmAndEditConfirmedAreGroupedByYear() {
        val pending = visit(5, now.minusSeconds(2 * 86400), false)
        var confirmed: AppointmentEntity? = null; var opened: AppointmentEntity? = null
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { MedicalRecordsScreen(listOf(pending, visit(6, Instant.parse("2025-05-01T10:00:00Z"), true),
            visit(7, Instant.parse("2026-01-05T10:00:00Z"), true)), { opened = it }, {}, {}, {}, {}, PaddingValues(), onConfirm = { confirmed = it }, now = now) } }
        ui.onNodeWithText(s(R.string.medical_were_you_there)).assertExists()
        ui.onNodeWithTag("medical-confirm:5").performClick(); assertEquals(pending, confirmed); assertNull(opened)
        ui.onNodeWithTag("medical-open:5").performClick(); assertEquals(pending, opened)
        // Already-confirmed visits have no confirm button.
        ui.onNodeWithTag("medical-confirm:6").assertDoesNotExist()
        list.performScrollToNode(hasTestTag("medical-year:2026")); list.performScrollToNode(hasTestTag("medical-year:2025"))
        val y26 = ui.onNodeWithTag("medical-year:2026").fetchSemanticsNode().positionInRoot.y
        val y25 = ui.onNodeWithTag("medical-year:2025").fetchSemanticsNode().positionInRoot.y
        assertTrue("newest year first", y26 < y25)
    }

    @Test fun noFileOrAttachmentEntry() {
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { MedicalRecordsScreen(listOf(visit(1, now.minusSeconds(86400), true), visit(2, now.plusSeconds(86400), false)),
            {}, {}, {}, {}, {}, PaddingValues(), now = now) } }
        list.performScrollToNode(hasTestTag("medical-bin"))
        val tags = ui.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.TestTag), useUnmergedTree = true).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
        assertTrue(tags.toString(), tags.none { it.contains("attach", true) || it.contains("file", true) || it.contains("document", true) })
        for (word in listOf("attach", "file", "pdf", "upload", "附件", "文件")) ui.onAllNodes(hasText(word, substring = true, ignoreCase = true)).assertCountEquals(0)
    }
}

/** UI-01 G: "Confirm visit" on the home is the detail's existing completed_utc write (saveMedicalVisit) and is undone there with Retract. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class MedicalHomeConfirmFlowTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    @Test fun confirmThenUndoInDetail() {
        val context = ui.activity
        val db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        val repo = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = db })
        val model = NotesViewModel(repo, ReminderCoordinator(context, repo), context)
        UiPrefs(context).disclaimerAccepted = true
        val at = Instant.now().minusSeconds(3 * 86400)
        runBlocking { repo.appointment(AppointmentEntity(type = "ENDO", at_utc = at.toEpochMilli(), at_zone = ZoneId.systemDefault().id, practitioner = "Synthetic doctor",
            location = null, note = null, remind_minutes_before = 0)) }
        model.sync()
        val id = runBlocking { db.dao().appointments().single().id }
        try {
            val a = UiPrefs(context).appearance
            ui.setContent { NotesTheme(ThemeMode.LIGHT) { NotesApp(model, a, {}) } }
            ui.onNodeWithContentDescription(context.getString(R.string.menu)).performClick()
            ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(context.getString(R.string.medical_records)))
            ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(context.getString(R.string.medical_records))).performClick(); ui.waitForIdle()
            ui.waitUntil(10_000) { ui.onAllNodesWithTag("medical-confirm:$id").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("medical-confirm:$id").performClick()
            ui.waitUntil(10_000) { runBlocking { db.dao().appointments().single().completed_utc } != null }
            ui.waitUntil(10_000) { ui.onAllNodesWithTag("medical-confirm:$id").fetchSemanticsNodes().isEmpty() }
            assertEquals(at.toEpochMilli(), runBlocking { db.dao().appointments().single().at_utc })   // nothing else changed
            ui.onNodeWithTag("medical-list").performScrollToNode(hasTestTag("medical-visit:$id"))
            ui.onNodeWithTag("medical-visit:$id").performClick(); ui.waitForIdle()
            // The medical detail's own undo: Retract, then its explicit confirmation.
            ui.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(context.getString(R.string.medical_retract)) and hasClickAction())
            ui.onNode(hasText(context.getString(R.string.medical_retract)) and hasClickAction()).performClick()
            ui.onNodeWithTag("medical-correction-confirm").performClick()
            ui.waitUntil(10_000) { runBlocking { db.dao().appointments().single().completed_utc } == null }
        } finally { androidx.lifecycle.ViewModelStore().apply { put("model", model); clear() }; db.close() }
    }
}
