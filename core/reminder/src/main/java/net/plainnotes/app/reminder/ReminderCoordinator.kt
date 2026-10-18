package net.plainnotes.app.reminder

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class ReminderCoordinator @Inject constructor(@ApplicationContext private val context:Context,repository:NotesRepository) {
    // Reminders belong to the real data only, never to the decoy space the UI may have open.
    private val repo=repository.pinnedTo(Space.PRIMARY)
    private val store=CacheStore(context)
    private val manager=context.getSystemService(AlarmManager::class.java)
    private val testLog=ReminderTestLog(context)
    companion object {
        private val lock=Mutex()
        const val TEST_NOTIFICATION=101
        /** A test that could not fire within this time after its trigger (device off) is dropped instead of arriving hours late. */
        private const val TEST_GRACE=15*60_000L
    }
    fun canExact()=Build.VERSION.SDK_INT<31 || manager.canScheduleExactAlarms()
    private fun request(generation:String,id:String)=PendingIntent.getBroadcast(context,0,
        Intent(context,AlarmReceiver::class.java).setAction("net.plainnotes.app.REMINDER").putExtra("generation",generation).putExtra("id",id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun cancel() { manager.cancel(request("", "")) }
    suspend fun <T> mutate(operation:suspend()->T):T=withContext(Dispatchers.IO) { lock.withLock {
        reconcileConsumed(); invalidate()
        try { operation() } finally { rebuild() }
    } }
    suspend fun sync()=withContext(Dispatchers.IO) { lock.withLock {
        if(!unlocked())scheduleCached() else { reconcileConsumed();invalidate();rebuild() }
    } }
    private fun unlocked()=context.getSystemService(UserManager::class.java).isUserUnlocked
    private suspend fun reconcileConsumed() {
        if(!unlocked())return
        val old=store.read() ?: return
        repo.reconcileCache(old)
    }
    /** The user's test reminder has no database mapping, so every rebuild carries it over until it fires. */
    private fun pendingTests(now:Long)=store.read()?.alarms.orEmpty().filter{it.test && !it.consumed && it.triggerMillis>=now-TEST_GRACE}
    private fun invalidate() {
        val now=System.currentTimeMillis();val tests=pendingTests(now)
        store.write(AlarmCache(UUID.randomUUID().toString(),maxOf(now,tests.maxOfOrNull{it.triggerMillis} ?: 0L),tests));cancel()
    }
    private suspend fun rebuild() {
        if(!unlocked())return
        val now=Instant.now();val expires=now.plusSeconds(48*3600).toEpochMilli();val generation=UUID.randomUUID().toString()
        val tests=pendingTests(now.toEpochMilli())
        val meds=repo.medications().associateBy{it.id}
        val candidates= mutableListOf<Pair<String,Long>>()
        repo.calendar(now).filter{it.state in listOf(SlotState.PENDING,SlotState.SOON,SlotState.OVERDUE)}.forEach { e ->
            val m=meds[e.slot.medicationId] ?: return@forEach
            if(m.active && m.notifications_on && m.needs_review==null) {
                listOf("SOON" to e.slot.at.minusSeconds(e.slot.soonMinutes.toLong()*60),"DUE" to e.slot.at,"LATE" to e.slot.at.plusSeconds(e.slot.lateMinutes.toLong()*60+1)).forEach{(type,t)->
                    if(t.toEpochMilli() in (now.toEpochMilli()-60000)..expires)candidates+=("${e.slot.key}:$type" to t.toEpochMilli())
                }
            }
        }
        repo.appointments().forEach{a->val t=a.at_utc-a.remind_minutes_before.toLong()*60000;if(t in (now.toEpochMilli()-60000)..expires)candidates+=("appointment:${a.id}" to t)}
        // Keep valid user snoozes across reboot/unlock/replans. Cancel them with the underlying slot.
        val oldMappings=repo.transaction{it.mappings()}
        oldMappings.filter{!it.sent && it.identity.startsWith("snooze:") && it.trigger_utc in now.toEpochMilli()..expires}.forEach { m ->
            val source=oldMappings.firstOrNull{it.opaque_id==m.identity.removePrefix("snooze:")}
            if(source!=null && (source.identity.startsWith("appointment:") || repo.reminderSlot(m.opaque_id)!=null)) candidates+=m.identity to m.trigger_utc
        }
        val mode=if(context.getSharedPreferences("prefs",Context.MODE_PRIVATE).getBoolean("high_reliability",false))"HIGH" else "NORMAL"
        val alarms=repo.transaction { dao ->
            val sent=dao.mappings().filter{it.sent}.map{it.identity}.toSet()
            candidates.distinct().filterNot{it.first in sent}.map{(identity,t)->
                val id=UUID.randomUUID().toString();dao.mapping(ReminderMappingEntity(id,generation,identity,t,false));CachedAlarm(id,t,mode)
            }
        }
        val cache=AlarmCache(generation,maxOf(expires,tests.maxOfOrNull{it.triggerMillis} ?: 0L),alarms+tests)
        store.write(cache)
        // Keep sent identities until their trigger ages out, including across repeated replans.
        repo.transaction { dao -> dao.pruneMappings(now.minusSeconds(48*3600).toEpochMilli()) }
        scheduleCached()
        runCatching {
            val today=java.time.LocalDate.now()
            // Same input and rule as the stock page and calendar (B4): the shared forecast over the open-dose window.
            StockAlerts.post(context,StockAlerts.due(repo.medications(),repo.containers(),repo.stockForecasts(now),today),today)
        }
    }
    private fun scheduleCached() {
        val cache=store.read() ?: return;val next=cache.next(System.currentTimeMillis()) ?: return
        val operation=request(cache.generation,next.opaqueId)
        val trigger=maxOf(next.triggerMillis,System.currentTimeMillis())
        try {
            if(canExact()) {
                if(next.mode=="HIGH") {
                    val launch=context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
                    val show=PendingIntent.getActivity(context,1,launch,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    manager.setAlarmClock(AlarmManager.AlarmClockInfo(trigger,show),operation)
                } else manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,operation)
            } else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,operation)
        } catch(_:SecurityException) { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,operation) }
    }
    suspend fun dispatch(generation:String,id:String)=withContext(Dispatchers.IO) { lock.withLock {
        val current=store.read() ?: return@withLock
        val event=current.alarms.firstOrNull{it.opaqueId==id && !it.consumed} ?: return@withLock
        if(current.generation!=generation || System.currentTimeMillis()>current.expiresMillis || event.triggerMillis>System.currentTimeMillis()+1000)return@withLock
        current.consume(id,generation) ?: return@withLock
        val now=System.currentTimeMillis()
        val due=current.alarms.filter{!it.consumed && (it.opaqueId==id || it.triggerMillis<=now+1000)}
        val batch=current.copy(alarms=current.alarms.map{if(it in due)it.copy(consumed=true)else it})
        // Persist before sending: after a crash we prefer no duplicate to an uncertain resend.
        store.write(batch)
        // A test never replaces or swallows a real reminder that falls due in the same batch, and vice versa.
        due.firstOrNull{!it.test}?.let{real->notifyNeutral(generation,real.opaqueId,if(unlocked())detailText(real.opaqueId,generation) else null)}
        if(due.any{it.test})testLog.received(now,postTest())
        if(unlocked()) { reconcileConsumed();invalidate();rebuild() } else scheduleCached()
    } }
    /** "Name · dose" for the notification body, only when the user opted in and the device is unlocked. */
    private suspend fun detailText(id:String,generation:String):String? {
        if(!NotificationPrefs(context).details)return null
        return runCatching { val slot=repo.reminderSlot(id,generation) ?: return null
            val m=repo.medications().firstOrNull{it.id==slot.medicationId} ?: return null
            "${m.name} · ${java.text.NumberFormat.getNumberInstance().apply{maximumFractionDigits=3}.format(slot.dose)} ${m.unit.lowercase()}" }.getOrNull()
    }
    private fun channel(nm:NotificationManager)=nm.createNotificationChannel(NotificationChannel("reminders",context.getString(R.string.reminder_channel),NotificationManager.IMPORTANCE_HIGH))
    private fun notifyNeutral(generation:String,id:String,detail:String?=null) {
        val text=NotificationPrefs(context)
        val nm=context.getSystemService(NotificationManager::class.java)
        channel(nm)
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val launch=context.packageManager.getLaunchIntentForPackage(context.packageName)
        val open=launch?.let{PendingIntent.getActivity(context,1,it,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)}
        val complete=PendingIntent.getBroadcast(context,2,Intent(context,AlarmReceiver::class.java).setAction("net.plainnotes.app.COMPLETE").putExtra("generation",generation).putExtra("id",id),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val snooze=PendingIntent.getBroadcast(context,3,Intent(context,AlarmReceiver::class.java).setAction("net.plainnotes.app.SNOOZE").putExtra("generation",generation).putExtra("id",id),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder=NotificationCompat.Builder(context,"reminders").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(text.title ?: context.getString(R.string.neutral_reminder)).setContentText(detail ?: text.body ?: context.getString(R.string.neutral_open))
            .setVisibility(NotificationCompat.VISIBILITY_SECRET).setContentIntent(open).setAutoCancel(true)
            .addAction(0,context.getString(R.string.snooze_action),snooze)
        if(unlocked())builder.addAction(0,context.getString(if(text.disguised)R.string.neutral_done else R.string.record_action),complete)
        NotificationManagerCompat.from(context).notify(100,builder.build())
    }
    suspend fun complete(generation:String,id:String) {
        if(!unlocked())return
        mutate {
        val sent=repo.transaction{dao->dao.mappings().any{it.opaque_id==id && it.generation==generation && it.sent}}
        if(!sent)return@mutate
        val slot=repo.reminderSlot(id,generation) ?: return@mutate
        repo.complete(slot,Instant.now(),slot.dose)
        context.getSystemService(NotificationManager::class.java).cancel(100)
        }
    }
    suspend fun snooze(generation:String,id:String)=withContext(Dispatchers.IO) { lock.withLock {
        val current=store.read() ?: return@withLock
        val trigger=System.currentTimeMillis()+10*60000
        if(unlocked()) {
            val source=repo.transaction{dao->dao.mappings().firstOrNull{it.opaque_id==id&&it.generation==generation&&it.sent}} ?: return@withLock
            if(!source.identity.startsWith("appointment:") && repo.reminderSlot(id,generation)==null)return@withLock
            val next=repo.transaction { dao ->
                val source=dao.mappings().firstOrNull{it.opaque_id==id&&it.generation==generation&&it.sent} ?: return@transaction null
                if(dao.mappings().any{it.identity=="snooze:$id"})return@transaction null
                val opaque=UUID.randomUUID().toString();dao.mapping(ReminderMappingEntity(opaque,current.generation,"snooze:$id",trigger,false))
                CachedAlarm(opaque,trigger,"NORMAL")
            } ?: return@withLock
            store.write(current.copy(expiresMillis=maxOf(current.expiresMillis,trigger),alarms=current.alarms+next))
        } else {
            if(current.generation!=generation || current.alarms.none{it.opaqueId==id&&it.consumed})return@withLock
            store.write(current.copy(expiresMillis=maxOf(current.expiresMillis,trigger),alarms=current.alarms.map{if(it.opaqueId==id)it.copy(triggerMillis=trigger,consumed=false)else it}))
        }
        context.getSystemService(NotificationManager::class.java).cancel(100);scheduleCached()
    } }
    /**
     * Scheduled test: the real AlarmManager → AlarmReceiver → dispatch → NotificationManager path, in the mode the user chose
     * (HIGH uses the system alarm clock). A repeated tap replaces the pending test; real reminders are untouched.
     */
    suspend fun testReminder(delayMillis:Long=60_000)=withContext(Dispatchers.IO){lock.withLock {
        if(unlocked())reconcileConsumed()
        val now=System.currentTimeMillis()
        val current=store.read() ?: AlarmCache(UUID.randomUUID().toString(),now+48*3600000L,emptyList())
        val mode=if(context.getSharedPreferences("prefs",Context.MODE_PRIVATE).getBoolean("high_reliability",false))"HIGH" else "NORMAL"
        val a=CachedAlarm(UUID.randomUUID().toString(),now+delayMillis,mode,test=true)
        store.write(current.copy(expiresMillis=maxOf(current.expiresMillis,a.triggerMillis),alarms=current.alarms.filterNot{it.test}+a))
        testLog.scheduled(now,a.triggerMillis,mode,canExact(),blocked(context.getSystemService(NotificationManager::class.java).also(::channel)))
        scheduleCached()
        testLog.read()
    }}
    fun testStatus()=testLog.read()
    /** What would stop a notification right now, read from the system without posting anything (null: nothing blocks it). */
    private fun blocked(nm:NotificationManager):TestOutcome? = when {
        Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED->TestOutcome.BLOCKED_PERMISSION
        !NotificationManagerCompat.from(context).areNotificationsEnabled()->TestOutcome.BLOCKED_APP
        nm.getNotificationChannel("reminders")?.importance==NotificationManager.IMPORTANCE_NONE->TestOutcome.BLOCKED_CHANNEL
        else->null
    }
    private suspend fun postTest():TestOutcome {
        val nm=context.getSystemService(NotificationManager::class.java)
        channel(nm)
        blocked(nm)?.let{return it}
        val open=context.packageManager.getLaunchIntentForPackage(context.packageName)?.let{PendingIntent.getActivity(context,1,it,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)}
        NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION,NotificationCompat.Builder(context,"reminders").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(NotificationPrefs(context).title ?: context.getString(R.string.neutral_reminder)).setContentText(context.getString(R.string.test_notification_body))
            .setVisibility(NotificationCompat.VISIBILITY_SECRET).setContentIntent(open).setAutoCancel(true).build())
        // Posted is not the same as shown: the system may still drop it (e.g. a vendor block); only an active entry counts.
        // notify() is handed to the system asynchronously, so allow it a moment to appear before reporting it missing.
        repeat(30) { if(nm.activeNotifications.any{it.id==TEST_NOTIFICATION})return TestOutcome.SHOWN; kotlinx.coroutines.delay(100) }
        return TestOutcome.POSTED_NOT_SHOWN
    }
}
