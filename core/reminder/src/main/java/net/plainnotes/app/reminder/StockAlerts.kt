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
import net.plainnotes.app.data.StockForecast
import net.plainnotes.app.data.StockForecasts
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Refill reminders: low stock by the shared per-dose forecast, and open packages close to their after-opening expiry. */
object StockAlerts {
    const val LOW_STOCK_DAYS = StockForecasts.LOW_STOCK_DAYS
    const val EXPIRY_WARNING_DAYS = 3
    enum class Kind { LOW, EXPIRY }
    data class Alert(val medication: MedicationEntity, val kind: Kind)
    /** Once-a-day markers, `"<medication id>:<kind>"` to the last ISO date an alert was posted. */
    const val PREFS = "stock_alerts"

    /**
     * Removes every marker, including keys left by older versions. Part of "Delete all data": medication ids restart
     * after a wipe, so a stale marker would both reveal which ids alerted on which dates and silence a new medication's
     * first alert that day. Returns false if the store could not be written; [isClear] then reports the residue.
     */
    fun clear(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    fun isClear(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.isEmpty()

    /**
     * Which alerts are due. Low stock is exactly the shared forecast's [StockForecast.isLow] (B4), the same judgement the
     * stock page and calendar show; whether a notification is sent is decided here and in [post] (per-medication
     * notification switch, review state, permission, once a day), never by changing the forecast. Untracked stock never
     * alerts. The after-opening expiry warning stays independent of quantity.
     */
    fun due(meds: List<MedicationEntity>, containers: List<ContainerEntity>, forecasts: Map<Long, StockForecast>, today: LocalDate,
            zone: ZoneId = ZoneId.systemDefault()): List<Alert> =
        meds.filter { it.active && it.notifications_on && it.needs_review == null }.flatMap { m ->
            val open = containers.filter { it.medication_id == m.id && it.state == "IN_USE" && it.inventory_encoding=="LEGACY_SCALAR" }
            val out = ArrayList<Alert>()
            if (StockForecasts.available(m.id,containers)!=null && forecasts[m.id]?.isLow(today, zone) == true) out += Alert(m, Kind.LOW)
            val days = m.expiry_days_after_open
            if (days != null && open.any { c -> c.opened_on?.let { ChronoUnit.DAYS.between(today, LocalDate.parse(it).plusDays(days.toLong())) <= EXPIRY_WARNING_DAYS } == true })
                out += Alert(m, Kind.EXPIRY)
            out
        }

    /** Posts each alert at most once per day; wording follows the neutral-notification settings. */
    fun post(context: Context, alerts: List<Alert>, today: LocalDate) {
        val sent = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
