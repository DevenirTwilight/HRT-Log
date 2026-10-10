package net.plainnotes.app.releaseacceptance

import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.DatabaseAccess
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.data.NotesRepository
import net.plainnotes.app.domain.RuleKind
import net.plainnotes.app.releaseacceptance.AcceptanceSupport.app
import net.plainnotes.app.releaseacceptance.AcceptanceSupport.evidence
import net.plainnotes.app.reminder.CacheStore
import net.plainnotes.app.reminder.ReminderEntryPoint
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.time.LocalTime

/**
 * A real synthetic medication schedule through the app's repository and ReminderCoordinator (Hilt graph), so the
 * alarms come from the database exactly as after a reboot. The medication is synthetic and is deactivated afterwards;
 * nothing is deleted.
 */
class ReminderLifecycleAcceptanceTest {
    private val coordinator get() = dagger.hilt.android.EntryPointAccessors.fromApplication(app, ReminderEntryPoint::class.java).coordinator()
    private val repo by lazy { NotesRepository(DatabaseAccess(app)) }
    private val nm get() = app.getSystemService(NotificationManager::class.java)
    private val phase get() = InstrumentationRegistry.getArguments().getString("hrtRebootPhase")
    @Before fun guard() = AcceptanceSupport.requireDisposableDevice()

    private fun synthetic(name: String) = MedicationEntity(name = name, molecule = "OTHER", unit = "MG", dose_per_intake = 1.0, container_capacity = 100.0,
        soon_alert_minutes = 1, late_after_minutes = 5, site_rotation = false, notifications_on = true, active = true, sort_order = 98)
    private fun upcoming(windowMinutes: Long = 15): List<Long> { val now = System.currentTimeMillis()
        return CacheStore(app).read()?.alarms.orEmpty().filter { !it.consumed && it.triggerMillis in now..now + windowMinutes * 60_000 }.map { it.triggerMillis } }
    private fun save(value: MedicationEntity, at: LocalTime) = runBlocking { coordinator.mutate { repo.saveMedication(value, null, RuleKind.EVERY_N_DAYS, 1, listOf(at), emptySet()) } }
    private fun stored(id: Long) = runBlocking { repo.medications() }.single { it.id == id }
    private fun awaitNotification(seconds: Int): Boolean { val end = System.currentTimeMillis() + seconds * 1000L
        while (System.currentTimeMillis() < end) { if (nm.activeNotifications.any { it.id == 100 }) return true; Thread.sleep(1000) }; return false }

    @Test fun createCancelRescheduleAndTriggerFromTheDatabase() {
        Assume.assumeTrue(phase == null)
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED, app.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS))
        nm.cancel(100)
        val at = LocalTime.now().plusMinutes(3).withSecond(0).withNano(0)
        val id = save(synthetic("P1 synthetic reminder ${System.nanoTime()}"), at)
        try {
            val created = upcoming(); val generation1 = CacheStore(app).read()!!.generation
            assertTrue("SOON/DUE/LATE alarms must be scheduled from the new schedule: $created", created.size >= 2)
            save(stored(id).copy(notifications_on = false), at)
            val cancelled = upcoming()
            assertTrue("Disabling notifications must remove its alarms: $cancelled", cancelled.isEmpty())
            save(stored(id).copy(notifications_on = true), at)
            val rescheduled = upcoming(); val generation3 = CacheStore(app).read()!!.generation
            assertEquals(created.sorted(), rescheduled.sorted()); assertNotEquals(generation1, generation3)
            val posted = awaitNotification(240)
            evidence("reminder-lifecycle", JSONObject().put("created", created.size).put("cancelled_remaining", cancelled.size)
                .put("rescheduled", rescheduled.size).put("generation_changed", generation1 != generation3).put("notification_posted", posted))
            assertTrue("The scheduled reminder must post its notification", posted)
        } finally { save(stored(id).copy(active = false, notifications_on = false), at); nm.cancel(100) }
    }

    /** Reboot phase 1: a schedule a few minutes ahead. The orchestrator reboots and waits for the notification without opening the app. */
    @Test fun scheduleBeforeReboot() {
        Assume.assumeTrue(phase == "prepare")
        nm.cancel(100)
        save(synthetic("P1 synthetic reboot reminder"), LocalTime.now().plusMinutes(5).withSecond(0).withNano(0))
        val alarms = upcoming()
        evidence("reboot-prepare", JSONObject().put("alarms_before_reboot", alarms.size))
        assertTrue(alarms.isNotEmpty())
    }
}
