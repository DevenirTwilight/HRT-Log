package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.reminder.ReminderCoordinator
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * B3-A (roadmap R-06), synthetic data on the real AppShell: system Back from a top-level page returns to the calendar;
 * dialogs, the drawer and the visit detail close first; on the calendar, in disguise mode and behind the app lock Back
 * reaches the activity fallback unchanged (MainActivity finishes or exits to the disguise shell).
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class)
class AppShellBackNavigationTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ui.activity
    private lateinit var db: NotesDatabase
    private lateinit var repo: NotesRepository
    private lateinit var model: NotesViewModel
    private var activityBacks = 0

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        repo = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = db })
        model = NotesViewModel(repo, ReminderCoordinator(context, repo), context)
        UiPrefs(context).disclaimerAccepted = true
        // Stands in for MainActivity's callback (finish, or exit to the disguise shell); registered before any Compose handler.
        ui.runOnUiThread { context.onBackPressedDispatcher.addCallback(context, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { activityBacks++ } }) }
    }
    @After fun close() { androidx.lifecycle.ViewModelStore().apply { put("model", model); clear() }; db.close() }

    private fun show(backToCalendar: Boolean = true) {
        val appearance = UiPrefs(context).appearance
        ui.setContent { MaterialTheme { NotesApp(model, appearance, {}, backToCalendar = backToCalendar) } }
    }
    private fun back() { ui.runOnUiThread { context.onBackPressedDispatcher.onBackPressed() }; ui.waitForIdle() }
    private fun openDrawer() { ui.onNodeWithContentDescription(context.getString(R.string.menu)).performClick(); ui.waitForIdle() }
    private fun open(destination: Destination) {
        openDrawer()
        ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(context.getString(destination.title)))
        ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(context.getString(destination.title))).performClick()
        ui.waitForIdle()
    }
    private fun assertCalendar() = ui.onNodeWithContentDescription(context.getString(R.string.calendar_pick_start)).assertExists()
    private fun assertNotCalendar() = ui.onNodeWithContentDescription(context.getString(R.string.calendar_pick_start)).assertDoesNotExist()

    @Test fun backFromEveryTopLevelPageReturnsToTheCalendarThenLeaves() {
        show()
        for (d in Destination.entries - Destination.CALENDAR) {
            open(d); assertNotCalendar()
            back()
            assertCalendar(); assertEquals("Back from $d must not leave the app", 0, activityBacks)
        }
        back()
        assertEquals("On the calendar Back follows the normal system behaviour", 1, activityBacks)
        assertCalendar()
    }

    @Test fun openDrawerClosesBeforeNavigating() {
        show(); open(Destination.STOCK); openDrawer()
        back()
        ui.onNodeWithTag("drawer-content").assertIsNotDisplayed()
        assertNotCalendar(); ui.onAllNodesWithText(context.getString(R.string.stock)).onFirst().assertIsDisplayed()
        assertEquals(0, activityBacks)
        back(); assertCalendar()
    }

    @Test fun openDialogClosesBeforeNavigating() {
        show(); open(Destination.LABS)
        ui.onNodeWithText(context.getString(R.string.lab_add)).performClick(); ui.waitForIdle()
        ui.onNode(isDialog()).assertExists()
        ui.runOnUiThread { org.robolectric.shadows.ShadowDialog.getLatestDialog().onBackPressed() }; ui.waitForIdle()
        ui.onNode(isDialog()).assertDoesNotExist()
        assertNotCalendar(); ui.onAllNodesWithText(context.getString(R.string.lab_add), useUnmergedTree = true).onFirst().assertExists()
        assertEquals(0, activityBacks)
        assertEquals(emptyList<LabValueEntity>(), runBlocking { db.dao().labs() })
    }

    @Test fun visitDetailReturnsToTheListFirst() {
        runBlocking { repo.appointment(AppointmentEntity(at_utc = Instant.now().plus(3, ChronoUnit.DAYS).toEpochMilli(), at_zone = "UTC", type = "CHECKUP", practitioner = "Synthetic clinic", remind_minutes_before = 0)) }
        model.sync()
        show(); open(Destination.VISITS)
        ui.onNodeWithText(context.getString(R.string.appointment), useUnmergedTree = true).assertExists()  // list FAB
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Synthetic clinic", substring = true).fetchSemanticsNodes().isNotEmpty() }
        ui.onAllNodesWithText("Synthetic clinic", substring = true).onFirst().performClick(); ui.waitForIdle()
        ui.onNodeWithText(context.getString(R.string.appointment), useUnmergedTree = true).assertDoesNotExist()  // list FAB hidden in the detail
        back()
        ui.onNodeWithText(context.getString(R.string.appointment), useUnmergedTree = true).assertExists(); assertNotCalendar()
        back()
        assertCalendar(); assertEquals(0, activityBacks)
    }

    @Test fun disguiseModeKeepsTheImmediateExitToTheShell() {
        show(backToCalendar = false); open(Destination.STOCK)
        back()
        assertEquals("Disguise mode leaves to the shell at once", 1, activityBacks)
        assertNotCalendar()
    }

    @Test fun appLockScreenDoesNotNavigateTheHiddenApp() {
        var locked by mutableStateOf(false)
        val appearance = UiPrefs(context).appearance
        ui.setContent { MaterialTheme { if (locked) LockScreen({ false }, { 0L }, null) else NotesApp(model, appearance, {}) } }
        open(Destination.STOCK)
        locked = true; ui.waitForIdle()
        back()
        assertEquals("Behind the lock Back reaches MainActivity, which finishes as before", 1, activityBacks)
        ui.onNodeWithContentDescription(context.getString(R.string.menu)).assertDoesNotExist()
    }

    @Test fun backNeverWritesRecords() {
        show()
        for (d in listOf(Destination.HISTORY, Destination.STOCK, Destination.CONCENTRATION)) { open(d); back(); back() }
        assertEquals(emptyList<RecordEntity>(), runBlocking { db.dao().records() })
    }
}
