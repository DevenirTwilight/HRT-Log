package net.plainnotes.app.reminder
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28]) class LockedBootAlarmTest {
    private lateinit var context:Context
    private lateinit var coordinator:ReminderCoordinator
    private lateinit var store:CacheStore
    private lateinit var cache:AlarmCache
    @Before fun setup(){
        context=ApplicationProvider.getApplicationContext();shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(false)
        coordinator=ReminderCoordinator(context,NotesRepository(DatabaseAccess(context)));store=CacheStore(context)
        cache=AlarmCache(UUID.randomUUID().toString(),System.currentTimeMillis()+48*3600000L,
            listOf(CachedAlarm(UUID.randomUUID().toString(),System.currentTimeMillis()-1,"NORMAL")))
        store.write(cache)
    }
    @Test fun lockedBootReschedulesWithoutDatabase(){runBlocking{coordinator.sync()};assertNotNull(shadowOf(context.getSystemService(AlarmManager::class.java)).nextScheduledAlarm);assertFalse(context.getDatabasePath("notes.db").exists())}
    @Test fun oldGenerationCannotSend(){runBlocking{coordinator.dispatch("old",cache.alarms.single().opaqueId)};assertFalse(store.read()!!.alarms.single().consumed);assertEquals(0,context.getSystemService(NotificationManager::class.java).activeNotifications.size)}
    @Test fun dispatchConsumesOnceAndKeepsDatabaseClosed(){val id=cache.alarms.single().opaqueId;runBlocking{coordinator.dispatch(cache.generation,id);coordinator.dispatch(cache.generation,id)};assertTrue(store.read()!!.alarms.single().consumed);assertEquals(1,context.getSystemService(NotificationManager::class.java).activeNotifications.size);assertFalse(context.getDatabasePath("notes.db").exists())}
    @Test fun simultaneousRemindersAreConsumedAsOneBatch(){
        val second=CachedAlarm(UUID.randomUUID().toString(),cache.alarms.single().triggerMillis,"NORMAL")
        store.write(cache.copy(alarms=cache.alarms+second));runBlocking{coordinator.dispatch(cache.generation,cache.alarms.single().opaqueId)}
        assertTrue(store.read()!!.alarms.all{it.consumed});assertEquals(1,context.getSystemService(NotificationManager::class.java).activeNotifications.size)
    }
    @Test fun canceledAndRescheduledOldAlarmCannotResurrect(){store.write(cache.copy(generation=UUID.randomUUID().toString(),alarms=emptyList()));runBlocking{coordinator.dispatch(cache.generation,cache.alarms.single().opaqueId)};assertEquals(0,context.getSystemService(NotificationManager::class.java).activeNotifications.size)}
    @Test fun lockedCompletionDoesNotOpenDatabaseOrInvalidateAlarms(){
        runBlocking{coordinator.complete(cache.generation,cache.alarms.single().opaqueId)}
        assertEquals(cache,store.read());assertFalse(context.getDatabasePath("notes.db").exists())
    }

}
