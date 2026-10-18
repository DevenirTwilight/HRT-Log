package net.plainnotes.app.reminder

import android.content.Context

/** Where a test notification ended up; each value is a separate observed fact, never inferred from an earlier stage. */
enum class TestOutcome { SHOWN, POSTED_NOT_SHOWN, BLOCKED_PERMISSION, BLOCKED_APP, BLOCKED_CHANNEL }

/**
 * Stages of the last test reminder, kept apart: scheduled with AlarmManager, broadcast received, notification outcome.
 * [blockedAtSchedule] is what already blocked notifications when the test was set (read, nothing posted). Only times, modes and outcomes are stored
 * (device-protected, because the alarm can fire before the first unlock); no health data.
 */
data class ReminderTestStatus(val scheduledAt: Long = 0, val triggerAt: Long = 0, val mode: String = "", val exact: Boolean = false,
                              val receivedAt: Long = 0, val outcome: TestOutcome? = null, val blockedAtSchedule: TestOutcome? = null)

class ReminderTestLog(context: Context) {
    private val p = context.createDeviceProtectedStorageContext().getSharedPreferences("reminder_test", Context.MODE_PRIVATE)
    fun read() = ReminderTestStatus(p.getLong("scheduled", 0), p.getLong("trigger", 0), p.getString("mode", "")!!, p.getBoolean("exact", false),
        p.getLong("received", 0), p.getString("outcome", null)?.let(TestOutcome::valueOf), p.getString("blocked", null)?.let(TestOutcome::valueOf))
    fun scheduled(at: Long, trigger: Long, mode: String, exact: Boolean, blocked: TestOutcome?) = p.edit().putLong("scheduled", at).putLong("trigger", trigger)
        .putString("mode", mode).putBoolean("exact", exact).putString("blocked", blocked?.name).remove("received").remove("outcome").commit()
    fun received(at: Long, outcome: TestOutcome) = p.edit().putLong("received", at).putString("outcome", outcome.name).commit()
    fun clear() = p.edit().clear().commit()
}
