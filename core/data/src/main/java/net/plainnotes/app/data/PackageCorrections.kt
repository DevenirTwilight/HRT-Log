package net.plainnotes.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class PackageRestoreConflict : IllegalStateException("Stock or intake history changed after voiding this package")
class PackageConsumptionConflict : IllegalArgumentException("Package has actual consumption history")

/** Append-only package correction evidence in existing hidden CORRECTION rows; no new schema or ledger kind. */
internal object PackageCorrections {
    private val realStates=setOf("SEALED","IN_USE","EMPTY","DISCARDED")
    fun isAudit(ref:String)=ref.startsWith("package-void:") || ref.startsWith("package-restore:")
    private fun rows(db:SupportSQLiteDatabase,table:String,where:String,id:Long)=Trash.rows(db,table,"$where ORDER BY id",arrayOf(id))
    private fun hash(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun ledger(db:SupportSQLiteDatabase,id:Long)=hash(rows(db,"supply_transaction","container_id=?",id).toString())
    private fun context(db:SupportSQLiteDatabase,box:ContainerEntity):String {
        val containers=Trash.rows(db,"supply_container","medication_id=? AND id!=? ORDER BY id",arrayOf(box.medication_id,box.id))
        val records=rows(db,"dose_record","medication_id=?",box.medication_id)
        val entries=rows(db,"supply_transaction","container_id IN (SELECT id FROM supply_container WHERE medication_id=?)",box.medication_id)
        return hash(rows(db,"medication","id=?",box.medication_id).toString()+containers.toString()+records.toString()+entries.toString())
    }
    private fun latest(db:SupportSQLiteDatabase,id:Long):Pair<String,JSONObject>? = db.query(
        "SELECT ref,payload_json FROM trash_item WHERE kind='CORRECTION' AND (ref LIKE ? OR ref LIKE ?) ORDER BY id DESC LIMIT 1",
        arrayOf("package-void:$id:%","package-restore:$id:%")).use { c -> if(c.moveToFirst()) c.getString(0) to JSONObject(c.getString(1)) else null }
    private fun append(db:SupportSQLiteDatabase,id:Long,action:String,payload:JSONObject,now:Instant) {
        val operation=UUID.randomUUID().toString()
        db.insert("trash_item",SQLiteDatabase.CONFLICT_ABORT,ContentValues().apply{
            put("kind",Trash.CORRECTION);put("ref","package-$action:$id:$operation")
            put("item_date",now.atZone(ZoneOffset.UTC).toLocalDate().toString());put("deleted_utc",now.toEpochMilli())
            put("payload_json",payload.put("operation_id",operation).put("action",action).toString());put("state",Trash.HIDDEN)
        })
    }
    suspend fun void(db:SupportSQLiteDatabase,dao:NotesDao,id:Long,now:Instant) {
        val box=dao.container(id)
        if(box.state=="VOIDED")return // No second evidence row or second inventory change.
        require(box.state in realStates)
        if(dao.supplyForContainer(id).any{it.kind!="ADJUST"})throw PackageConsumptionConflict()
        val before=rows(db,"supply_container","id=?",id).getJSONObject(0)
        val payload=JSONObject().put("version",1).put("container",before)
            .put("ledger_sha256",ledger(db,id)).put("context_sha256",context(db,box))
        append(db,id,"void",payload,now)
        dao.setContainerState(id,"VOIDED",box.opened_on)
    }
    suspend fun restore(db:SupportSQLiteDatabase,dao:NotesDao,id:Long,now:Instant) {
        val box=dao.container(id)
        if(box.state!="VOIDED")return
        val event=latest(db,id) ?: throw PackageRestoreConflict()
        val payload=event.second
        val before=payload.getJSONObject("container")
        val current=rows(db,"supply_container","id=?",id).getJSONObject(0).put("state",before.getString("state"))
        if(!event.first.startsWith("package-void:") || before.toString()!=current.toString() ||
            before.getString("state") !in realStates || dao.supplyForContainer(id).any{it.kind!="ADJUST"} ||
            payload.getString("ledger_sha256")!=ledger(db,id) || payload.getString("context_sha256")!=context(db,box))throw PackageRestoreConflict()
        append(db,id,"restore",JSONObject(payload.toString()).put("restores",event.first),now)
        dao.setContainerState(id,before.getString("state"),box.opened_on)
    }
    /** Used after backup restore, while raw maintenance is still in the caller's atomic transaction. */
    fun validate(db:SupportSQLiteDatabase) {
        val last=mutableMapOf<Long,Pair<String,JSONObject>>()
        db.query("SELECT ref,payload_json,state FROM trash_item WHERE ref LIKE 'package-void:%' OR ref LIKE 'package-restore:%' ORDER BY id").use { c -> while(c.moveToNext()) {
            val ref=c.getString(0);val parts=ref.split(':');require(parts.size==3);UUID.fromString(parts[2])
            val id=parts[1].toLong();val p=JSONObject(c.getString(1));val before=p.getJSONObject("container")
            require(c.getString(2)==Trash.HIDDEN && p.getInt("version")==1 && p.getString("operation_id")==parts[2] &&
                before.getLong("id")==id && before.getString("state") in realStates)
            require(p.getString("ledger_sha256").matches(Regex("[0-9a-f]{64}")) && p.getString("context_sha256").matches(Regex("[0-9a-f]{64}")))
            val action=if(parts[0]=="package-void")"void" else "restore";require(p.getString("action")==action)
            require(if(action=="void")last[id]?.first?.startsWith("package-void:")!=true else p.getString("restores")==last[id]?.first)
            db.query("SELECT 1 FROM supply_container WHERE id=? AND medication_id=?",arrayOf(id,before.getLong("medication_id"))).use{require(it.moveToFirst())}
            last[id]=ref to p
        }}
        db.query("SELECT id,state FROM supply_container").use { c -> while(c.moveToNext()) {
            val id=c.getLong(0);val state=c.getString(1);val event=last[id]
            require((state=="VOIDED")== (event?.first?.startsWith("package-void:")==true)){"Missing or inconsistent package correction"}
            if(state=="VOIDED") {
                val current=rows(db,"supply_container","id=?",id).getJSONObject(0).put("state",event!!.second.getJSONObject("container").getString("state"))
                require(current.toString()==event.second.getJSONObject("container").toString())
                require(ledger(db,id)==event.second.getString("ledger_sha256"))
                db.query("SELECT 1 FROM supply_transaction WHERE container_id=? AND kind!='ADJUST' LIMIT 1",arrayOf(id)).use{require(!it.moveToFirst())}
            }
        }}
    }
}
