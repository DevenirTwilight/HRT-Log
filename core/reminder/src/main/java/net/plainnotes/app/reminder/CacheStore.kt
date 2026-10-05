package net.plainnotes.app.reminder

import android.content.Context
import android.util.AtomicFile
import net.plainnotes.app.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** This codec deliberately has no API that accepts domain identities or notification content. */
class CacheStore(context:Context) {
    private val file=AtomicFile(File(context.createDeviceProtectedStorageContext().filesDir,"reminders.cache"))
    @Synchronized fun read():AlarmCache?=runCatching {
        val j=JSONObject(String(file.readFully(),Charsets.UTF_8));val array=j.getJSONArray("alarms")
        AlarmCache(j.getString("generation"),j.getLong("expires"),(0 until array.length()).map { index ->
            val a=array.getJSONObject(index);CachedAlarm(a.getString("id"),a.getLong("trigger"),a.getString("mode"),a.getBoolean("consumed"))
        })
    }.getOrNull()
    @Synchronized fun write(cache:AlarmCache) {
        require(cache.generation.matches(Regex("[a-f0-9-]{36}")))
        val j=JSONObject().put("generation",cache.generation).put("expires",cache.expiresMillis)
        val alarms=JSONArray();cache.alarms.forEach { a ->
            require(a.mode in listOf("NORMAL","HIGH"));require(a.opaqueId.matches(Regex("[a-f0-9-]{36}")))
            alarms.put(JSONObject().put("id",a.opaqueId).put("trigger",a.triggerMillis).put("mode",a.mode).put("consumed",a.consumed))
        };j.put("alarms",alarms)
        val output=file.startWrite();try{output.write(j.toString().toByteArray());file.finishWrite(output)}catch(e:Exception){file.failWrite(output);throw e}
    }
}
