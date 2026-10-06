package net.plainnotes.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import net.plainnotes.app.data.ContainerEntity
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.domain.SlotState
import net.plainnotes.app.domain.TimelineEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Refill reminders: low stock for the coming days' planned doses, and open packages close to their after-opening expiry. */
object StockAlerts {
    const val LOW_STOCK_DAYS = 7
    const val EXPIRY_WARNING_DAYS = 3
    enum class Kind { LOW, EXPIRY }
    data class Alert(val medication: MedicationEntity, val kind: Kind)

    /**
     * [upcoming] holds the planned doses of the next [LOW_STOCK_DAYS] days. Untracked stock (no open or sealed package)
     * never alerts: there is nothing to compare against.
     */
    fun due(meds: List<MedicationEntity>, containers: List<ContainerEntity>, upcoming: List<TimelineEntry>, today: LocalDate): List<Alert> =
        meds.filter { it.active && it.notifications_on && it.needs_review == null }.flatMap { m ->
            val mine = containers.filter { it.medication_id == m.id }
            val open = mine.filter { it.state == "IN_USE" }; val sealed = mine.filter { it.state == "SEALED" }
            val out = ArrayList<Alert>()
            if (open.isNotEmpty() || sealed.isNotEmpty()) {
                val remaining = open.sumOf { it.capacity - it.used_amount } + sealed.sumOf { it.capacity }
                val need = upcoming.filter { it.slot.medicationId == m.id && it.state in setOf(SlotState.PENDING, SlotState.SOON, SlotState.OVERDUE) }.sumOf { it.slot.dose }
                if (need > 0 && remaining + 1e-9 < need) out += Alert(m, Kind.LOW)
            }
            val days = m.expiry_days_after_open
            if (days != null && open.any { c -> c.opened_on?.let { ChronoUnit.DAYS.between(today, LocalDate.parse(it).plusDays(days.toLong())) <= EXPIRY_WARNING_DAYS } == true })
                out += Alert(m, Kind.EXPIRY)
            out
        }

    /** Posts each alert at most once per day; wording follows the neutral-notification settings. */
    fun post(context: Context, alerts: List<Alert>, today: LocalDate) {
        val sent = context.getSharedPreferences("stock_alerts", Context.MODE_PRIVATE)
        val text = NotificationPrefs(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("reminders", context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH))
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val open = launch?.let { PendingIntent.getActivity(context, 4, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        alerts.forEach { a ->
            val key = "${a.medication.id}:${a.kind}"
            if (sent.getString(key, null) == today.toString()) return@forEach
            val detail = if (text.details) context.getString(if (a.kind == Kind.LOW) R.string.stock_low_detail else R.string.stock_expiry_detail, a.medication.name) else null
            val n = NotificationCompat.Builder(context, "reminders").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(text.title ?: context.getString(R.string.neutral_reminder)).setContentText(detail ?: text.body ?: context.getString(R.string.neutral_open))
                .setVisibility(NotificationCompat.VISIBILITY_SECRET).setContentIntent(open).setAutoCancel(true).build()
            NotificationManagerCompat.from(context).notify(1000 + (a.medication.id * 2 + a.kind.ordinal).toInt(), n)
            sent.edit().putString(key, today.toString()).apply()
        }
    }
}
