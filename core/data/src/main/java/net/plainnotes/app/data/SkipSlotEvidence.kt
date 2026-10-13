package net.plainnotes.app.data

import net.plainnotes.app.domain.Slot
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** Optional evidence inside the existing JSON snapshot; clinical fields and PNBAK1/table formats stay unchanged. */
internal object SkipSlotEvidence {
    private const val KEY="skip_original_slot_v1"
    fun capture(snapshot:String,slot:Slot,previous:RecordEntity?=null):String {
        val context=JSONObject(snapshot)
        val evidence=if(previous==null)JSONObject().put("key",slot.key).put("original_utc",slot.original.toEpochMilli()).put("zone",slot.originalZone.id)
            else JSONObject(previous.config_snapshot).optJSONObject(KEY)
        if(evidence!=null)context.put(KEY,evidence)
        return context.toString()
    }
    data class Clock(val key:String,val at:Instant,val zone:ZoneId)
    fun read(record:RecordEntity):Clock? = try {
        val context=JSONObject(record.config_snapshot)
        if(!context.has(KEY))null else context.getJSONObject(KEY).let{Clock(it.getString("key"),Instant.ofEpochMilli(it.getLong("original_utc")),ZoneId.of(it.getString("zone")))}
    } catch(_:Exception) { throw SkipUndoUnavailable() }
}
