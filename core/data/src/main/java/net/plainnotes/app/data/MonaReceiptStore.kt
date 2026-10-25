package net.plainnotes.app.data

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import net.plainnotes.app.importer.Mona
import net.plainnotes.app.importer.MonaMigration
import org.json.JSONArray
import org.json.JSONObject

/** Transaction-local receipt reads and ordinary clinical trash bookkeeping. No retained cache,
 * file, key, source-provider handle, or inventory write. A null FK is never automatically rebound. */
internal object MonaReceiptStore {
    data class Key(val kind:String,val id:String,val part:String="")
    data class Head(val key:Key,val sourceDigest:String,val targetDigest:String,val record:Long?,val lab:Long?,val panel:String?,val retention:String)
    private val realColumns=mapOf("dose_record" to setOf("planned_dose","actual_dose","unallocated_supply_amount"),
        "lab_value" to setOf("value","reference_lower","reference_upper"),"lab_panel" to emptySet<String>())
    private val rowColumns=mapOf(
        "dose_record" to listOf("id","medication_id","rule_version_id","slot_key","scheduled_utc","scheduled_zone","planned_dose","late_after_minutes_snapshot","taken_utc","taken_zone","actual_dose","unallocated_supply_amount","status","site","note","origin","source_record_key","revision","deleted_at_utc","config_snapshot"),
        "lab_value" to listOf("id","analyte_code","value","unit","sampled_utc","sampled_zone","reference_lower","reference_upper","reference_unit","laboratory","note","panel_id"))
    fun fingerprint(table:String,row:JSONObject):String {
        val normalized=JSONObject()
        for(key in rowColumns.getValue(table)) {
            val value=row.opt(key) ?: JSONObject.NULL
            normalized.put(key,if(value!=JSONObject.NULL && key in realColumns.getValue(table))
                "ieee754:"+java.lang.Long.toHexString(java.lang.Double.doubleToRawLongBits((value as Number).toDouble())) else value)
        }
        return MonaMigration.digest(Mona.canonical(normalized))
    }
    fun row(c:Cursor)=JSONObject().also {o->for(i in 0 until c.columnCount)o.put(c.getColumnName(i),when(c.getType(i)) {
        Cursor.FIELD_TYPE_NULL->JSONObject.NULL;Cursor.FIELD_TYPE_INTEGER->c.getLong(i);Cursor.FIELD_TYPE_FLOAT->c.getDouble(i)
        Cursor.FIELD_TYPE_STRING->c.getString(i);else->error("Unexpected clinical column type")
    })}
    fun heads(db:SupportSQLiteDatabase,ids:Collection<String>):Map<Key,Head> {
        val result=linkedMapOf<Key,Head>()
        for(chunk in ids.distinct().chunked(400)) {
            val query="SELECT source_kind,source_id,source_part,source_digest,target_digest,record_id,lab_id,panel_uuid,retention FROM external_import_item WHERE source_app='MONA' AND source_id IN (${chunk.joinToString(","){"?"}})"
            db.query(query,chunk.toTypedArray()).use{c->while(c.moveToNext()) {
                val key=Key(c.getString(0),c.getString(1),c.getString(2))
                result[key]=Head(key,c.getString(3),c.getString(4),if(c.isNull(5))null else c.getLong(5),if(c.isNull(6))null else c.getLong(6),if(c.isNull(7))null else c.getString(7),c.getString(8))
            }}
        }
        return result
    }
    /** Bounded batches, with one live row object at a time rather than an unbounded JSONArray. */
    fun rows(db:SupportSQLiteDatabase,table:String,ids:Collection<Long>,consume:(JSONObject)->Unit) {
        require(table in rowColumns)
        for(chunk in ids.distinct().chunked(400))db.query("SELECT * FROM $table WHERE id IN (${chunk.joinToString(","){"?"}})",chunk.toTypedArray()).use {c->while(c.moveToNext())consume(row(c))}
    }
    private fun refs(payload:JSONObject):JSONArray {
        if(!payload.has("mona_sources"))return JSONArray()
        val rows=payload.get("mona_sources");require(rows is JSONArray && rows.length()<=BackupLimits.MAX_ROWS){"Unknown source retention metadata"}
        for(i in 0 until rows.length()) {
            val r=rows.getJSONObject(i)
            require(r.keys().asSequence().toSet()==setOf("kind","id","part","digest","target","target_id","target_digest"))
            for(key in listOf("kind","id","part","digest","target","target_digest"))require(r.opt(key) is String)
            require(r.opt("target_id") is Int || r.opt("target_id") is Long)
        }
        return rows
    }
    private fun key(r:JSONObject)=Key(r.getString("kind"),r.getString("id"),r.getString("part"))
    private fun predicate(@Suppress("UNUSED_PARAMETER") key:Key)="source_app='MONA' AND source_kind=? AND source_id=? AND source_part=?"
    private fun args(k:Key)=arrayOf<Any?>(k.kind,k.id,k.part)
    fun capture(db:SupportSQLiteDatabase,kind:String,ref:String,payload:JSONObject) {
        if(!ExternalImportSchema.exists(db))return
        val targets=when(kind) {
            Trash.RECORD->listOf("DOSE" to requireNotNull(ref.toLongOrNull()))
            Trash.LAB,Trash.LAB_PANEL->payload.getJSONObject("tables").optJSONArray("lab_value")?.let{a->(0 until a.length()).map{"LAB" to a.getJSONObject(it).getLong("id")}}.orEmpty()
            else->emptyList()
        }
        val captured=JSONArray()
        for((target,id) in targets) {
            val column=if(target=="DOSE")"record_id" else "lab_id"
            db.query("SELECT source_kind,source_id,source_part,source_digest FROM external_import_item WHERE $column=? AND retention='COMMITTED'",arrayOf(id)).use {c->while(c.moveToNext()) {
                val table=if(target=="DOSE")"dose_record" else "lab_value"
                val actual=Trash.rows(db,table,"id=?",arrayOf(id)).getJSONObject(0)
                captured.put(JSONObject().put("kind",c.getString(0)).put("id",c.getString(1)).put("part",c.getString(2)).put("digest",c.getString(3))
                    .put("target",target).put("target_id",id).put("target_digest",fingerprint(table,actual)))
            }}
        }
        if(captured.length()>0)payload.put("mona_sources",captured)
    }
    /** A receipt-owned trash member is evidence of deletion, not an absent/zero target. */
    fun retained(db:SupportSQLiteDatabase):Set<Key> {
        val found=hashSetOf<Key>()
        db.query("SELECT kind,ref,payload_json FROM trash_item WHERE kind IN ('RECORD','LAB','LAB_PANEL')").use {c->while(c.moveToNext()) {
            val payload=JSONObject(c.getString(2));val members=refs(payload)
            for(i in 0 until members.length()) {
                val r=members.getJSONObject(i);val k=key(r);require(k.kind in setOf("INTAKE","BLOOD_TEST"))
                val target=r.getString("target");require(target==if(k.kind=="INTAKE")"DOSE" else "LAB")
                val id=r.getLong("target_id");require(id>0)
                if(target=="DOSE")require(c.getString(0)==Trash.RECORD && c.getString(1)==id.toString())
                else {
                    val values=payload.getJSONObject("tables").getJSONArray("lab_value")
                    val matches=(0 until values.length()).map(values::getJSONObject).filter{it.getLong("id")==id};require(matches.size==1)
                    require(fingerprint("lab_value",matches.single())==r.getString("target_digest"))
                }
                db.query("SELECT source_digest FROM external_import_item WHERE ${predicate(k)}",args(k)).use {e->require(e.moveToFirst() && e.getString(0)==r.getString("digest")){"Unknown retained source identity"}}
                require(found.add(k)){"Duplicate retained source ownership"}
            }
        }}
        return found
    }
    fun restore(db:SupportSQLiteDatabase,payload:JSONObject) {
        val r=refs(payload)
        for(i in 0 until r.length()) {
            val ref=r.getJSONObject(i);val k=key(ref);require(ref.getString("target")=="LAB")
            val id=ref.getLong("target_id");val current=Trash.rows(db,"lab_value","id=?",arrayOf(id)).getJSONObject(0)
            // LAB-02 may deliberately restore a member ungrouped when its old panel changed.
            // Its data must still be the exact snapshot, except for that explicit panel handling.
            val original=payload.getJSONObject("tables").getJSONArray("lab_value")
            val old=(0 until original.length()).map(original::getJSONObject).single{it.getLong("id")==id}
            require(fingerprint("lab_value",current)==fingerprint("lab_value",old))
            db.query("SELECT lab_id,source_digest,retention FROM external_import_item WHERE ${predicate(k)}",args(k)).use {e->
                require(e.moveToFirst() && e.isNull(0) && e.getString(1)==ref.getString("digest") && e.getString(2)=="COMMITTED"){"Stale source restore"}
            }
            db.execSQL("UPDATE external_import_item SET lab_id=? WHERE ${predicate(k)}",arrayOf<Any?>(id,*args(k)))
        }
    }
    fun purge(db:SupportSQLiteDatabase,item:TrashItemEntity) {
        val r=refs(JSONObject(item.payload_json))
        for(i in 0 until r.length()) {
            val ref=r.getJSONObject(i);val k=key(ref)
            db.query("SELECT source_digest,retention FROM external_import_item WHERE ${predicate(k)}",args(k)).use {e->require(e.moveToFirst() && e.getString(0)==ref.getString("digest") && e.getString(1)=="COMMITTED")}
            db.execSQL("UPDATE external_import_item SET source_json=NULL,retention='REMOVED',record_id=NULL,lab_id=NULL WHERE ${predicate(k)}",args(k))
        }
    }
}
