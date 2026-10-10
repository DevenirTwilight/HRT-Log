package net.plainnotes.app.releaseacceptance

import android.app.NotificationManager
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.releaseacceptance.AcceptanceSupport.app
import net.plainnotes.app.reminder.ReminderEntryPoint
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Phase 1 of the reboot check: schedule the app's test reminder; the orchestrator reboots and watches for the notification. */
class ReminderRebootAcceptanceTest {
    @Before fun guard() = AcceptanceSupport.requireDisposableDevice()
    @Test fun scheduleBeforeReboot() {
        org.junit.Assume.assumeTrue(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("hrtRebootPhase") == "prepare")
        assertTrue(app.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        app.getSystemService(NotificationManager::class.java).cancel(100)
        runBlocking { dagger.hilt.android.EntryPointAccessors.fromApplication(app, ReminderEntryPoint::class.java).coordinator().testReminder() }
    }
}
