package net.plainnotes.app

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.AlarmCache
import net.plainnotes.app.domain.CachedAlarm
import net.plainnotes.app.reminder.CacheStore
import net.plainnotes.app.reminder.ReminderCoordinator
import net.plainnotes.app.reminder.ReminderCoordinator.Companion.TEST_NOTIFICATION
import net.plainnotes.app.reminder.TestOutcome
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.ZoneId
import java.util.UUID

/**
 * China beta P0-A: the one test reminder goes through AlarmManager → dispatch → NotificationManager in the user's mode;
 * tapping only reads what would block notifications (posts nothing), and every stage is recorded separately: scheduled
 * with that check, broadcast received, notification outcome. Synthetic data only. The receiver → Hilt → dispatch hop and real firing run on devices
 * (ReminderTestDeviceTest).
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = Application::class)
class ReminderTestChainTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: NotesDatabase
    private lateinit var repo: NotesRepository
    private lateinit var coordinator: ReminderCoordinator
    private val manager get() = context.getSystemService(AlarmManager::class.java)
    private val notifications get() = context.getSystemService(NotificationManager::class.java)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        repo = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = db })
        coordinator = ReminderCoordinator(context, repo)
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }
    @After fun close() = db.close()

    private fun highReliability(on: Boolean) = context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putBoolean("high_reliability", on).commit()
    private fun pendingTests() = CacheStore(context).read()?.alarms.orEmpty().filter { it.test && !it.consumed }
    /** What AlarmReceiver does with the alarm the system delivers: the extras of the PendingIntent actually set. */
    private fun fireNext(): Pair<String, String> {
        val alarm = requireNotNull(shadowOf(manager).peekNextScheduledAlarm()) { "nothing set with AlarmManager" }
        val intent = shadowOf(alarm.operation).savedIntent
        assertEquals("net.plainnotes.app.REMINDER", intent.action)
        val delivery = intent.getStringExtra("generation")!! to intent.getStringExtra("id")!!
        runBlocking { coordinator.dispatch(delivery.first, delivery.second) }
        return delivery
    }
    private fun active(id: Int) = notifications.activeNotifications.filter { it.id == id }

    @Test fun scheduledTestRunsTheWholeChainAndRecordsEachStage() {
        val scheduled = runBlocking { coordinator.testReminder(delayMillis = 0) }
        assertEquals("NORMAL", scheduled.mode); assertTrue(scheduled.exact); assertTrue(scheduled.triggerAt >= scheduled.scheduledAt)
        assertEquals("not yet received", 0L, scheduled.receivedAt); assertNull(scheduled.outcome)
        val delivery = fireNext()
        val status = coordinator.testStatus()
        assertTrue("broadcast reached dispatch", status.receivedAt > 0)
        assertEquals(TestOutcome.SHOWN, status.outcome)
        assertEquals(1, active(TEST_NOTIFICATION).size)
        assertEquals(context.getString(net.plainnotes.app.reminder.R.string.test_notification_body),
            active(TEST_NOTIFICATION).single().notification.extras.getString("android.text"))
        assertTrue("no real reminder was invented", active(100).isEmpty())
        assertTrue("the test is consumed once", pendingTests().isEmpty())
        notifications.cancelAll()
        runBlocking { coordinator.dispatch(delivery.first, delivery.second) }
        assertTrue("a repeated delivery of the same alarm posts nothing", notifications.activeNotifications.isEmpty())
    }

    @Test fun highReliabilityTestUsesTheSystemAlarmClock() {
        highReliability(true)
        assertEquals("HIGH", runBlocking { coordinator.testReminder() }.mode)
        assertNotNull("HIGH is tested through setAlarmClock", manager.nextAlarmClock)
        highReliability(false)
    }

    @Test fun deniedExactAlarmsAreReportedButTheTestIsStillSet() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(runBlocking { coordinator.testReminder() }.exact)
        assertTrue(shadowOf(manager).peekNextScheduledAlarm()!!.isAllowWhileIdle)
    }

    @Test fun repeatedTapsKeepOnePendingTest() {
        runBlocking { repeat(3) { coordinator.testReminder() } }
        assertEquals(1, pendingTests().size)
    }

    @Test fun tappingPostsNothingAndChecksTheNotificationSettings() {
        val status = runBlocking { coordinator.testReminder() }
        assertNull("nothing blocks notifications", status.blockedAtSchedule)
        assertTrue("only the one-minute test: nothing is posted at the tap", notifications.activeNotifications.isEmpty())
        assertEquals(0L, status.receivedAt); assertNull(status.outcome)
    }

    @Test fun deniedNotificationPermissionIsReportedAtEachStage() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals("seen at the tap", TestOutcome.BLOCKED_PERMISSION, runBlocking { coordinator.testReminder(delayMillis = 0) }.blockedAtSchedule)
        fireNext()
        val status = coordinator.testStatus()
        assertTrue("the alarm still arrived", status.receivedAt > 0)
        assertEquals(TestOutcome.BLOCKED_PERMISSION, status.outcome)
        assertTrue(notifications.activeNotifications.isEmpty())
    }

    @Test fun blockedAppNotificationsAreReported() {
        shadowOf(notifications).setNotificationsEnabled(false)
        assertEquals(TestOutcome.BLOCKED_APP, runBlocking { coordinator.testReminder() }.blockedAtSchedule)
        shadowOf(notifications).setNotificationsEnabled(true)
    }

    @Test fun disabledReminderChannelIsReported() {
        // The user switched the channel off; an app cannot raise a channel's importance again.
        notifications.createNotificationChannel(NotificationChannel("reminders", "Reminders", NotificationManager.IMPORTANCE_NONE))
        assertEquals(TestOutcome.BLOCKED_CHANNEL, runBlocking { coordinator.testReminder(delayMillis = 0) }.blockedAtSchedule)
        fireNext(); assertEquals(TestOutcome.BLOCKED_CHANNEL, coordinator.testStatus().outcome)
    }

    @Test fun aRealReminderDueInTheSameBatchIsNotSwallowedByTheTest() {
        val now = System.currentTimeMillis()
        runBlocking { repo.saveAppointment(AppointmentEntity(type = "VISIT", at_utc = now + 30_000, at_zone = ZoneId.systemDefault().id, remind_minutes_before = 1)) }
        runBlocking { coordinator.sync(); coordinator.testReminder(delayMillis = 0) }
        fireNext()
        assertEquals("real reminder posted", 1, active(100).size)
        assertEquals("test posted separately", 1, active(TEST_NOTIFICATION).size)
    }

    @Test fun anEarlierRealReminderLeavesTheTestScheduledAfterIt() {
        val now = System.currentTimeMillis()
        runBlocking { repo.saveAppointment(AppointmentEntity(type = "VISIT", at_utc = now + 30_000, at_zone = ZoneId.systemDefault().id, remind_minutes_before = 1)) }
        runBlocking { coordinator.sync(); coordinator.testReminder() }
        fireNext()
        assertEquals("the real reminder fired first", 1, active(100).size)
        assertTrue("the test did not fire early", active(TEST_NOTIFICATION).isEmpty())
        val test = pendingTests().single()
        assertEquals("the test is the next alarm set with AlarmManager", test.triggerMillis, shadowOf(manager).peekNextScheduledAlarm()!!.triggerAtMs)
    }

    @Test fun aTestThatCouldNotFireForLongIsDroppedNotDeliveredHoursLate() {
        val stale = CachedAlarm(UUID.randomUUID().toString(), System.currentTimeMillis() - 20 * 60_000, "NORMAL", test = true)
        CacheStore(context).write(AlarmCache(UUID.randomUUID().toString(), System.currentTimeMillis() + 3_600_000, listOf(stale)))
        runBlocking { coordinator.sync() }
        assertTrue(pendingTests().isEmpty()); assertNull(shadowOf(manager).peekNextScheduledAlarm())
    }

    @Test fun oldCachesWithoutTheTestFlagStillRead() {
        val file = java.io.File(context.createDeviceProtectedStorageContext().filesDir, "reminders.cache")
        val id = UUID.randomUUID().toString(); val generation = UUID.randomUUID().toString()
        file.writeText("""{"generation":"$generation","expires":${System.currentTimeMillis() + 60_000},"alarms":[{"id":"$id","trigger":1,"mode":"NORMAL","consumed":false}]}""")
        val alarm = CacheStore(context).read()!!.alarms.single()
        assertEquals(id, alarm.opaqueId); assertFalse(alarm.test)
    }
}
