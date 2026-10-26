package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/** Keeps source identity through trash, full backup, and explicit purge without retaining source text after purge. */
internal object ChrysalideReceiptStore {
    private fun refs(p:JSONObject):JSONArray { val a=p.opt("chrysalide_sources") as? JSONArray ?: return JSONArray();require(a.length()<=BackupLimits.MAX_ROWS)
        for(i in 0 until a.length()){val r=a.getJSONObject(i);require(r.keys().asSequence().toSet()==setOf("source_digest","source_id","target_id","target_digest"));require(r.getString("source_digest").matches(Regex("[0-9a-f]{64}")));require(r.getString("target_digest").matches(Regex("[0-9a-f]{64}")))};return a }
    fun capture(db:SupportSQLiteDatabase,kind:String,ref:String,payload:JSONObject){if(kind!=Trash.RECORD||!ChrysalideImportSchema.exists(db))return
        val id=ref.toLongOrNull()?:return;val a=JSONArray()
        db.query("SELECT source_digest,source_id,target_digest FROM chrysalide_import_item WHERE record_id=? AND retention='COMMITTED'",arrayOf(id)).use{c->while(c.moveToNext())a.put(JSONObject().put("source_digest",c.getString(0)).put("source_id",c.getString(1)).put("target_id",id).put("target_digest",c.getString(2)))}
        if(a.length()>0)payload.put("chrysalide_sources",a)
    }
    fun restore(db:SupportSQLiteDatabase,payload:JSONObject){val a=refs(payload);for(i in 0 until a.length()){val r=a.getJSONObject(i);db.query("SELECT source_id,target_digest,record_id,retention FROM chrysalide_import_item WHERE source_digest=?",arrayOf(r.getString("source_digest"))).use{c->require(c.moveToFirst()&&c.getString(0)==r.getString("source_id")&&c.getString(1)==r.getString("target_digest")&&c.getLong(2)==r.getLong("target_id")&&c.getString(3)=="COMMITTED")}}}
    fun purge(db:SupportSQLiteDatabase,item:TrashItemEntity){val a=refs(JSONObject(item.payload_json));for(i in 0 until a.length()){val r=a.getJSONObject(i);db.query("SELECT source_id,target_digest,retention FROM chrysalide_import_item WHERE source_digest=?",arrayOf(r.getString("source_digest"))).use{c->require(c.moveToFirst()&&c.getString(0)==r.getString("source_id")&&c.getString(1)==r.getString("target_digest")&&c.getString(2)=="COMMITTED")};db.execSQL("UPDATE chrysalide_import_item SET source_json=NULL,record_id=NULL,retention='REMOVED' WHERE source_digest=?",arrayOf(r.getString("source_digest")))} }
    fun validateTrash(db:SupportSQLiteDatabase){val seen=hashSetOf<String>();db.query("SELECT kind,ref,payload_json FROM trash_item WHERE payload_json LIKE '%chrysalide_sources%' ").use{c->while(c.moveToNext()){require(c.getString(0)==Trash.RECORD);val a=refs(JSONObject(c.getString(2)));for(i in 0 until a.length()){val r=a.getJSONObject(i);require(r.getLong("target_id")==c.getString(1).toLong());require(seen.add(r.getString("source_digest")));db.query("SELECT source_id,target_digest,retention FROM chrysalide_import_item WHERE source_digest=?",arrayOf(r.getString("source_digest"))).use{e->require(e.moveToFirst()&&e.getString(0)==r.getString("source_id")&&e.getString(1)==r.getString("target_digest")&&e.getString(2)=="COMMITTED")}}}}
    }
}
