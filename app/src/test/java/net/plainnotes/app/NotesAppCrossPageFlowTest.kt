package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RuleKind
import net.plainnotes.app.reminder.ReminderCoordinator
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

/**
 * UI-02B safety net, written and run before the AppShell state split: cross-page flows owned by NotesApp keep working.
 * (The notification entry opens the text-field intake dialog, which Robolectric cannot idle; it runs on a device in
 * NotificationEntryAndroidTest.)
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class NotesAppCrossPageFlowTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ui.activity
    private lateinit var db: NotesDatabase
    private lateinit var repo: NotesRepository
    private lateinit var model: NotesViewModel
    private fun s(id: Int, vararg a: Any) = context.getString(id, *a)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        repo = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = db })
        model = NotesViewModel(repo, ReminderCoordinator(context, repo), context)
        UiPrefs(context).disclaimerAccepted = true
        runBlocking {
            val m = MedicationEntity(name = "Synthetic E2", molecule = "E2", route = "ORAL", unit = "MG", dose_per_intake = 2.0, container_capacity = 28.0,
                soon_alert_minutes = 15, late_after_minutes = 60, site_rotation = false, notifications_on = false, active = true, sort_order = 0)
            repo.saveMedication(m, "E2", RuleKind.EVERY_N_DAYS, 1, listOf(LocalTime.of(8, 0)), emptySet(), Instant.now().minusSeconds(3 * 86400))
        }
    }
    @After fun close() { androidx.lifecycle.ViewModelStore().apply { put("model", model); clear() }; db.close() }
    private fun show() { model.sync(); val a = UiPrefs(context).appearance; ui.setContent { MaterialTheme { NotesApp(model, a, {}) } }; ui.waitForIdle() }
    private fun open(d: Destination) {
        ui.onNodeWithContentDescription(s(R.string.menu)).performClick()
        ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(s(d.title)))
        ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(s(d.title))).performClick(); ui.waitForIdle()
    }

    @Test fun reviewBannerOpensMedications() {
        runBlocking { val m = db.dao().medications().single(); db.dao().updateMedication(m.copy(needs_review = "IMPORT")) }
        show()
        ui.waitUntil(10_000) { ui.onAllNodesWithText(s(R.string.review_banner, 1)).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(s(R.string.review_banner, 1)).performClick(); ui.waitForIdle()
        ui.onAllNodesWithText(s(R.string.medications)).onFirst().assertExists()
        ui.onNodeWithContentDescription(s(R.string.calendar_pick_start)).assertDoesNotExist()
    }

    @Test fun pkDisclaimerFirstVisitDeclineReturnsToCalendarAcceptRemembers() {
        UiPrefs(context).disclaimerAccepted = false
        show(); open(Destination.CONCENTRATION)
        ui.onNodeWithText(s(R.string.pk_disclaimer_title)).assertExists()
        ui.onNodeWithText(s(R.string.cancel)).performClick(); ui.waitForIdle()
        ui.onNodeWithContentDescription(s(R.string.calendar_pick_start)).assertExists()
        assertFalse(UiPrefs(context).disclaimerAccepted)
        open(Destination.CONCENTRATION)
        ui.onNodeWithText(s(R.string.pk_disclaimer_accept)).performClick(); ui.waitForIdle()
        assertTrue(UiPrefs(context).disclaimerAccepted)
        ui.onNodeWithText(s(R.string.pk_disclaimer_title)).assertDoesNotExist()
    }

    @Test fun medicalSaveFailureOutsideTheEditorShowsASnackbar() {
        val at = Instant.now().minusSeconds(5 * 86400)
        runBlocking { repo.appointment(AppointmentEntity(type = "ENDO", at_utc = at.toEpochMilli(), at_zone = ZoneId.systemDefault().id, practitioner = "Synthetic doctor",
            location = null, note = null, remind_minutes_before = 0)) }
        show(); open(Destination.MEDICAL)
        val a = runBlocking { db.dao().appointments().single() }
        // A stale expected row (someone else changed it): the existing conflict path reports it without an editor open.
        ui.runOnIdle { model.clearMedicalVisitSave(); model.saveMedicalVisit(a.copy(note = "Synthetic"), a.copy(note = "Different")) }
        ui.waitUntil(10_000) { ui.onAllNodesWithText(s(R.string.medical_stale)).fetchSemanticsNodes().isNotEmpty() }
        assertNull(runBlocking { db.dao().appointments().single().note })
    }
}
