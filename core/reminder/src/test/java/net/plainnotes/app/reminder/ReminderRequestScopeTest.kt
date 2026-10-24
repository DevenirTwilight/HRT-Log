package net.plainnotes.app.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import net.plainnotes.app.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class ReminderRequestScopeTest {
    @Test fun creatingNewActionCannotRetagAnAlreadyIssuedPendingIntent() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val coordinator=ReminderCoordinator(context,NotesRepository(DatabaseAccess(context)))
        val method=ReminderCoordinator::class.java.getDeclaredMethod("scoped",Intent::class.java,LibraryRequestScope::class.java).apply {isAccessible=true}
        val oldScope=LibraryRequestScope("LEGACY",UUID.randomUUID().toString(),UUID.randomUUID().toString())
        val newScope=LibraryRequestScope(UUID.randomUUID().toString(),UUID.randomUUID().toString(),UUID.randomUUID().toString())
        fun request(scope:LibraryRequestScope):Intent {
            val intent=Intent(context,AlarmReceiver::class.java).setAction("net.plainnotes.app.COMPLETE")
                .putExtra("generation",UUID.randomUUID().toString()).putExtra("id",UUID.randomUUID().toString())
            return method.invoke(coordinator,intent,scope) as Intent
        }
        val first=request(oldScope)
        val old=PendingIntent.getBroadcast(context,2,first,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val second=request(newScope)
        val next=PendingIntent.getBroadcast(context,2,second,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        assertNotEquals("New notification replaced the old action token",old,next)
        assertEquals(oldScope.encoded(),shadowOf(old).savedIntent.getStringArrayListExtra(ReminderCoordinator.SCOPE_EXTRA))
        assertEquals(newScope.encoded(),shadowOf(next).savedIntent.getStringArrayListExtra(ReminderCoordinator.SCOPE_EXTRA))
        old.cancel();next.cancel()
    }
}
