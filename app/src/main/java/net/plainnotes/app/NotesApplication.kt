package net.plainnotes.app
import android.app.Application
import android.content.*
import android.os.UserManager
import androidx.core.content.ContextCompat
import androidx.work.*
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import net.plainnotes.app.reminder.ReminderEntryPoint
import java.util.concurrent.TimeUnit

@HiltAndroidApp class NotesApplication:Application(), Configuration.Provider {
    override val workManagerConfiguration:Configuration get()=Configuration.Builder().build()
    override fun onCreate() {
        super.onCreate()
        val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){if(i.action==Intent.ACTION_USER_UNLOCKED) startMaintenance()}}
        ContextCompat.registerReceiver(this,receiver,IntentFilter(Intent.ACTION_USER_UNLOCKED),ContextCompat.RECEIVER_NOT_EXPORTED)
        if(getSystemService(UserManager::class.java).isUserUnlocked)startMaintenance()
    }
    private fun startMaintenance() {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("reminder-maintenance",ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MaintenanceWorker>(6,TimeUnit.HOURS).build())
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch { runCatching{EntryPointAccessors.fromApplication(this@NotesApplication,ReminderEntryPoint::class.java).coordinator().sync()} }
    }
}
class MaintenanceWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result=try{
        EntryPointAccessors.fromApplication(applicationContext,ReminderEntryPoint::class.java).coordinator().sync();Result.success()
    }catch(_:Exception){Result.retry()}
}
