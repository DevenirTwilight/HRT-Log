package net.plainnotes.app.shell

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.reminder.ReminderCoordinator
import net.plainnotes.app.reminder.ReminderCoordinator.Companion.TEST_NOTIFICATION
import net.plainnotes.app.reminder.ReminderEntryPoint
import net.plainnotes.app.reminder.TestOutcome
import org.junit.*
import org.junit.Assert.*

/**
 * China beta P0-A on a real device (API 26/29/35, no health data): the scheduled test is delivered by the real
 * AlarmManager to the real AlarmReceiver (Hilt → dispatch) and ends as an active notification. Each stage is read back
 * separately. A real vendor ROM, Doze and a killed process stay NOT_RUN here.
 */
class ReminderTestDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val coordinator: ReminderCoordinator get() = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java).coordinator()
    private val notifications get() = context.getSystemService(NotificationManager::class.java)

    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).close()
    private fun highReliability(on: Boolean) = context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putBoolean("high_reliability", on).commit()

    @Before fun allow() {
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 31) shell("appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
        notifications.cancelAll(); highReliability(false)
    }
    @After fun clean() { notifications.cancelAll(); highReliability(false) }

    private fun shown() = notifications.activeNotifications.any { it.id == TEST_NOTIFICATION }
    /** Waits for the real alarm: the receiver records the stage, and the notification must be active in the system. */
    private fun awaitDelivery(scheduledAt: Long) {
        val deadline = System.currentTimeMillis() + 90_000
        while (System.currentTimeMillis() < deadline && !(coordinator.testStatus().receivedAt >= scheduledAt && shown())) Thread.sleep(500)
        val status = coordinator.testStatus()
        assertTrue("the alarm broadcast reached the app", status.receivedAt >= scheduledAt)
        assertEquals(TestOutcome.SHOWN, status.outcome)
        assertTrue("the notification is active in the system", shown())
    }

    @Test fun scheduledTestTravelsTheRealAlarmChain() {
        val status = runBlocking { coordinator.testReminder(delayMillis = 5_000) }
        assertEquals("NORMAL", status.mode); assertTrue(status.exact); assertNull("nothing blocks notifications", status.blockedAtSchedule)
        assertFalse("nothing shown before the alarm", shown())
        awaitDelivery(status.scheduledAt)
    }

    @Test fun highReliabilityTestUsesTheSystemAlarmClockAndArrives() {
        highReliability(true)
        val status = runBlocking { coordinator.testReminder(delayMillis = 5_000) }
        assertEquals("HIGH", status.mode)
        assertNotNull("set as a system alarm clock", context.getSystemService(AlarmManager::class.java).nextAlarmClock)
        awaitDelivery(status.scheduledAt)
    }

    /** The owner's case: after tapping test, returning from system settings (onResume → sync) must not drop it. */
    @Test fun scheduledTestSurvivesReturnsToTheApp() {
        val status = runBlocking { coordinator.testReminder(delayMillis = 8_000) }
        runBlocking { coordinator.sync(); coordinator.sync() }
        awaitDelivery(status.scheduledAt)
    }
}
