package net.plainnotes.app

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.reminder.CacheStore
import net.plainnotes.app.reminder.ReminderCoordinator
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * China beta P0-A (owner report 2026-10-18): “Send a test reminder in 1 minute” never arrived. Every return to the app
 * (MainActivity.onResume → sync), every data change (mutate) and every boot/time change (SystemReceiver → sync) rebuilt
 * the alarm cache from the database, where the test reminder has no row, so a test was silently dropped before it fired.
 * Uses only the APIs that existed before the fix, so it runs against both versions.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class)
class TestReminderSurvivalTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: NotesDatabase
    private lateinit var repo: NotesRepository
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        repo = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = db })
    }
    @After fun close() = db.close()

    /** The pending test: in the cache, not consumed, about one minute ahead, and the alarm actually set with AlarmManager. */
    private fun assertTestPending(step: String) {
        val now = System.currentTimeMillis()
        val pending = CacheStore(context).read()?.alarms.orEmpty().filter { !it.consumed && it.triggerMillis in (now + 30_000)..(now + 61_000) }
        assertEquals("test reminder still pending after $step", 1, pending.size)
        val scheduled = alarms.peekNextScheduledAlarm()
        assertNotNull("an alarm is set with AlarmManager after $step", scheduled)
        assertEquals("the AlarmManager alarm is the test after $step", pending.single().triggerMillis, scheduled!!.triggerAtMs)
        assertEquals(pending.single().opaqueId, shadowOf(scheduled.operation).savedIntent.getStringExtra("id"))
    }

    @Test fun returningToTheAppKeepsTheTest() {
        val coordinator = ReminderCoordinator(context, repo)
        runBlocking { coordinator.testReminder() }; assertTestPending("tap")
        runBlocking { coordinator.sync() }; assertTestPending("returning to the app (onResume sync)")
        runBlocking { coordinator.sync() }; assertTestPending("a second return, e.g. from system settings")
    }

    @Test fun aDataChangeKeepsTheTest() {
        val coordinator = ReminderCoordinator(context, repo)
        runBlocking { coordinator.testReminder() }
        runBlocking { coordinator.mutate { } }; assertTestPending("a data change while waiting")
    }

    @Test fun processRecreationBootAndTimeChangeKeepTheTest() {
        runBlocking { ReminderCoordinator(context, repo).testReminder() }
        // A new process has a new coordinator; boot, time and time zone changes all arrive as SystemReceiver → sync().
        runBlocking { ReminderCoordinator(context, repo).sync() }; assertTestPending("process recreation / boot / time change")
    }
}
