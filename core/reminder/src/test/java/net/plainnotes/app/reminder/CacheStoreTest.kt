package net.plainnotes.app.reminder
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.plainnotes.app.domain.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28]) class CacheStoreTest {
    @Test fun roundTripContainsOnlyAllowlistedSchedulingKeys(){
        val context=ApplicationProvider.getApplicationContext<Context>();val store=CacheStore(context)
        val cache=AlarmCache(UUID.randomUUID().toString(),5000,listOf(CachedAlarm(UUID.randomUUID().toString(),4000,"NORMAL")))
        store.write(cache);assertEquals(cache,store.read())
        val text=java.io.File(context.createDeviceProtectedStorageContext().filesDir,"reminders.cache").readText()
        val j=org.json.JSONObject(text);assertEquals(setOf("generation","expires","alarms"),j.keys().asSequence().toSet())
        val a=j.getJSONArray("alarms").getJSONObject(0);assertEquals(setOf("id","trigger","mode","consumed"),a.keys().asSequence().toSet())
        assertFalse(text.contains("slot_key"));assertFalse(text.contains("dose"))
    }
    @Test fun corruptCacheFailsClosed(){val context=ApplicationProvider.getApplicationContext<Context>();java.io.File(context.createDeviceProtectedStorageContext().filesDir,"reminders.cache").writeText("corrupt");assertNull(CacheStore(context).read())}
}
