package net.plainnotes.app.reminder
import android.content.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*

@EntryPoint @InstallIn(SingletonComponent::class) interface ReminderEntryPoint { fun coordinator(): ReminderCoordinator }
private fun coordinator(context:Context)=EntryPointAccessors.fromApplication(context.applicationContext,ReminderEntryPoint::class.java).coordinator()
class AlarmReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action !in setOf("net.plainnotes.app.REMINDER","net.plainnotes.app.SNOOZE","net.plainnotes.app.COMPLETE"))return
        val generation=intent.getStringExtra("generation") ?: return;val id=intent.getStringExtra("id") ?: return
        val pending=goAsync();CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try { when(intent.action){"net.plainnotes.app.SNOOZE"->coordinator(context).snooze(generation,id);"net.plainnotes.app.COMPLETE"->coordinator(context).complete(generation,id);else->coordinator(context).dispatch(generation,id)} } catch(_:Exception) { /* no health data in logs */ } finally { pending.finish() }
        }
    }
}
class SystemReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_LOCKED_BOOT_COMPLETED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED,android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))return
        val pending=goAsync();CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try{coordinator(context).sync()}catch(_:Exception){}finally{pending.finish()}
        }
    }
}
