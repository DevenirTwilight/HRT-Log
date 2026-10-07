package net.plainnotes.app.data

import org.json.JSONObject
import java.time.Instant

/** Fill missing historical facts, never overwrite an already saved formulation. */
object HistoricalContext {
    fun incomplete(record:RecordEntity):Boolean {
        val s=MedicationSnapshot.decode(record.config_snapshot,record.medication_id) ?: return true
        if(s.molecule==null || s.unit==null || s.route==null)return true
        if(s.molecule!="E2")return false
        val p=s.profile ?: return true
        return when(p.pk_route){"gel"->p.gel_product_id==null;"sublingual"->p.sl_tier==null;"patchApply"->p.patch_release_ug_day==null;else->false}
    }
    fun fill(original:String,candidate:String):String? = runCatching {
        val o=JSONObject(original);val n=JSONObject(candidate)
        fun known(j:JSONObject,k:String)=j.has(k) && !j.isNull(k)
        // Names are labels; all saved chemical, route and dimensional facts must agree.
        for(k in listOf("molecule","route","unit","ester"))if(known(o,k) && known(n,k))require(o.get(k).toString()==n.get(k).toString())
        val oldProfile=o.optJSONObject("pk_profile");val newProfile=n.optJSONObject("pk_profile")
        if(oldProfile!=null && newProfile!=null)oldProfile.keys().forEach{k->if(known(oldProfile,k) && known(newProfile,k))require(oldProfile.get(k).toString()==newProfile.get(k).toString())}
        val filled=JSONObject(o.toString())
        n.keys().forEach{k->if(k=="snapshot_version" || !known(filled,k))filled.put(k,n.get(k))}
        if(oldProfile!=null && newProfile!=null) {
            val p=JSONObject(oldProfile.toString());newProfile.keys().forEach{k->if(!known(p,k))p.put(k,newProfile.get(k))};filled.put("pk_profile",p)
        }
        filled.toString()
    }.getOrNull()
    fun resolved(record:RecordEntity,ruleSnapshots:Map<Long,String>):String {
        if(!incomplete(record) || record.origin.startsWith("IMPORT_"))return record.config_snapshot
        val rule=record.rule_version_id?.let(ruleSnapshots::get) ?: return record.config_snapshot
        return fill(record.config_snapshot,rule) ?: record.config_snapshot
    }
    fun confirmed(original:String,candidate:String,kind:String,now:Instant,knownContext:String=original):String {
        val result=JSONObject(requireNotNull(fill(knownContext,candidate)))
        result.put("historical_context_confirmation",JSONObject().put("kind",kind).put("confirmed_at",now.toString()).put("original_snapshot",JSONObject(original)))
        return result.toString()
    }
}
