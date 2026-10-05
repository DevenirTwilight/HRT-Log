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
    companion object { private val lock=Mutex() }
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
    private fun invalidate() {
        store.write(AlarmCache(UUID.randomUUID().toString(),System.currentTimeMillis(),emptyList()));cancel()
    }
    private suspend fun rebuild() {
        if(!unlocked())return
        val now=Instant.now();val expires=now.plusSeconds(48*3600).toEpochMilli();val generation=UUID.randomUUID().toString()
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
        val cache=AlarmCache(generation,expires,alarms)
        store.write(cache)
        // Keep sent identities until their trigger ages out, including across repeated replans.
        repo.transaction { dao -> dao.pruneMappings(now.minusSeconds(48*3600).toEpochMilli()) }
        scheduleCached()
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
        val consumed=current.consume(id,generation) ?: return@withLock
        val batch=consumed.copy(alarms=consumed.alarms.map{if(!it.consumed&&it.triggerMillis<=System.currentTimeMillis()+1000)it.copy(consumed=true)else it})
        // Persist before sending: after a crash we prefer no duplicate to an uncertain resend.
        store.write(batch)
        notifyNeutral(generation,id,if(unlocked())detailText(id,generation) else null)
        if(unlocked()) { reconcileConsumed();invalidate();rebuild() } else scheduleCached()
    } }
    /** "Name · dose" for the notification body, only when the user opted in and the device is unlocked. */
    private suspend fun detailText(id:String,generation:String):String? {
        if(!NotificationPrefs(context).details)return null
        return runCatching { val slot=repo.reminderSlot(id,generation) ?: return null
            val m=repo.medications().firstOrNull{it.id==slot.medicationId} ?: return null
            "${m.name} · ${java.text.NumberFormat.getNumberInstance().apply{maximumFractionDigits=3}.format(slot.dose)} ${m.unit.lowercase()}" }.getOrNull()
    }
    private fun notifyNeutral(generation:String,id:String,detail:String?=null) {
        val text=NotificationPrefs(context)
        val nm=context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("reminders",context.getString(R.string.reminder_channel),NotificationManager.IMPORTANCE_HIGH))
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val launch=context.packageManager.getLaunchIntentForPackage(context.packageName)
        val open=launch?.let{PendingIntent.getActivity(context,1,it,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)}
        val complete=PendingIntent.getBroadcast(context,2,Intent(context,AlarmReceiver::class.java).setAction("net.plainnotes.app.COMPLETE").putExtra("generation",generation).putExtra("id",id),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val snooze=PendingIntent.getBroadcast(context,3,Intent(context,AlarmReceiver::class.java).setAction("net.plainnotes.app.SNOOZE").putExtra("generation",generation).putExtra("id",id),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder=NotificationCompat.Builder(context,"reminders").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(text.title ?: context.getString(R.string.neutral_reminder)).setContentText(detail ?: text.body ?: context.getString(R.string.neutral_open))
            .setVisibility(NotificationCompat.VISIBILITY_SECRET).setContentIntent(open).setAutoCancel(true)
            .addAction(0,context.getString(R.string.snooze_action),snooze)
        if(unlocked())builder.addAction(0,context.getString(R.string.record_action),complete)
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
    suspend fun testReminder()=withContext(Dispatchers.IO){lock.withLock {
        if(unlocked())reconcileConsumed()
        val current=store.read() ?: AlarmCache(UUID.randomUUID().toString(),System.currentTimeMillis()+48*3600000L,emptyList())
        val a=CachedAlarm(UUID.randomUUID().toString(),System.currentTimeMillis()+60000,"NORMAL")
        store.write(current.copy(expiresMillis=maxOf(current.expiresMillis,a.triggerMillis),alarms=current.alarms+a));scheduleCached()
    }}
}
