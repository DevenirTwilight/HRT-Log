package net.plainnotes.app.reminder
import android.app.AlarmManager
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
@RunWith(RobolectricTestRunner::class) @Config(sdk=[31]) class ExactAlarmTest {
    private lateinit var context:Context
    private lateinit var coordinator:ReminderCoordinator
    private lateinit var manager:AlarmManager
    @Before fun setup(){context=ApplicationProvider.getApplicationContext();manager=context.getSystemService(AlarmManager::class.java)
        shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(false)
        coordinator=ReminderCoordinator(context,NotesRepository(DatabaseAccess(context)))
    }
    private fun cache(mode:String){CacheStore(context).write(AlarmCache(UUID.randomUUID().toString(),System.currentTimeMillis()+48*3600000L,
        listOf(CachedAlarm(UUID.randomUUID().toString(),System.currentTimeMillis()+60000,mode))))}
    @Test fun deniedExactPermissionStillSchedulesFallback(){org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false);cache("NORMAL");runBlocking{coordinator.sync()};assertFalse(coordinator.canExact());assertNotNull(shadowOf(manager).nextScheduledAlarm)}
    @Test fun highModeHasSystemAlarmClockWhenAllowed(){org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true);cache("HIGH");runBlocking{coordinator.sync()};assertNotNull(manager.nextAlarmClock)}
    @Test fun revocationIsDetectedOnNextSync(){org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true);cache("NORMAL");runBlocking{coordinator.sync()};org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false);runBlocking{coordinator.sync()};assertFalse(coordinator.canExact());assertNotNull(shadowOf(manager).nextScheduledAlarm)}
}
