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
    private fun version(payload:JSONObject):Int {
        val value=payload.get("version");require(value is Int && value in 1..2);return value
    }
    private fun containerUuid(db:SupportSQLiteDatabase,id:Long,medication:Long):String? = db.query(
        "SELECT c.uuid FROM reference_identity c JOIN reference_identity m ON m.uuid=c.owner_uuid WHERE c.kind='CONTAINER' AND c.live_handle=? AND m.kind='MEDICATION' AND m.live_handle=? AND m.dataset_uuid=c.dataset_uuid",
        arrayOf(id,medication)).use {c->if(c.moveToFirst())c.getString(0) else null}
    private fun sameContainer(db:SupportSQLiteDatabase,box:ContainerEntity,payload:JSONObject,current:JSONObject):Boolean {
        val before=payload.getJSONObject("container")
        if(version(payload)==1) {
            if(box.inventory_encoding!=InventoryEncoding.LEGACY)return false
            if(!before.has("inventory_encoding"))current.remove("inventory_encoding")
        } else {
            if(!before.has("inventory_encoding") || before.getString("inventory_encoding")!=box.inventory_encoding)return false
            val uuid=payload.get("container_uuid")
            if(uuid !is String || !ReferencePayload.uuid(uuid) || uuid!=containerUuid(db,box.id,box.medication_id))return false
        }
        return before.toString()==current.toString()
    }
    private fun context(db:SupportSQLiteDatabase,box:ContainerEntity,payload:JSONObject?=null):String {
        val containers=Trash.rows(db,"supply_container","medication_id=? AND id!=? ORDER BY id",arrayOf(box.medication_id,box.id))
        if(payload!=null && version(payload)==1 && !payload.getJSONObject("container").has("inventory_encoding")) {
            for(i in 0 until containers.length()) {
                val row=containers.getJSONObject(i);require(row.getString("inventory_encoding")==InventoryEncoding.LEGACY)
                row.remove("inventory_encoding") // Published v1 hash, never a new-mode downgrade.
            }
        }
        val records=rows(db,"dose_record","medication_id=?",box.medication_id)
        val entries=rows(db,"supply_transaction","container_id IN (SELECT id FROM supply_container WHERE medication_id=?)",box.medication_id)
        val identities=if(payload!=null && version(payload)==1)"" else {
            val owner=db.query("SELECT uuid FROM reference_identity WHERE kind='MEDICATION' AND live_handle=?",arrayOf(box.medication_id)).use{c->require(c.moveToFirst());c.getString(0)}
            Trash.rows(db,"reference_identity","(uuid=? OR owner_uuid=?) AND live_handle IS NOT NULL ORDER BY uuid",arrayOf(owner,owner)).toString()
        }
        return hash(rows(db,"medication","id=?",box.medication_id).toString()+containers.toString()+records.toString()+entries.toString()+identities)
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
        val scope=ReferenceStore.scope(db)
        val medication=ReferenceStore.identity(db,scope,"MEDICATION",box.medication_id,null)
        val subject=ReferenceStore.identity(db,scope,"CONTAINER",id,medication)
        val before=rows(db,"supply_container","id=?",id).getJSONObject(0)
        val payload=JSONObject().put("version",2).put("container_uuid",subject).put("container",before)
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
        val matches=try {sameContainer(db,box,payload,current) && payload.getString("context_sha256")==context(db,box,payload)}
            catch(_:Exception){false}
        if(!event.first.startsWith("package-void:") || !matches ||
            before.getString("state") !in realStates || dao.supplyForContainer(id).any{it.kind!="ADJUST"} ||
            payload.getString("ledger_sha256")!=ledger(db,id))throw PackageRestoreConflict()
        append(db,id,"restore",JSONObject(payload.toString()).put("restores",event.first),now)
        dao.setContainerState(id,before.getString("state"),box.opened_on)
    }
    /** Used after backup restore, while raw maintenance is still in the caller's atomic transaction. */
    fun validate(db:SupportSQLiteDatabase) {
        val last=mutableMapOf<Long,Pair<String,JSONObject>>()
        db.query("SELECT ref,payload_json,state FROM trash_item WHERE ref LIKE 'package-void:%' OR ref LIKE 'package-restore:%' ORDER BY id").use { c -> while(c.moveToNext()) {
            val ref=c.getString(0);val parts=ref.split(':');require(parts.size==3);UUID.fromString(parts[2])
            val id=parts[1].toLong();val p=JSONObject(c.getString(1));val before=p.getJSONObject("container")
            require(c.getString(2)==Trash.HIDDEN && version(p) in 1..2 && p.getString("operation_id")==parts[2] &&
                before.getLong("id")==id && before.getString("state") in realStates)
            require(p.getString("ledger_sha256").matches(Regex("[0-9a-f]{64}")) && p.getString("context_sha256").matches(Regex("[0-9a-f]{64}")))
            val action=if(parts[0]=="package-void")"void" else "restore";require(p.getString("action")==action)
            require(if(action=="void")last[id]?.first?.startsWith("package-void:")!=true else p.getString("restores")==last[id]?.first)
            val marker=if(InventoryEncoding.present(db))"inventory_encoding" else {require(db.version<12 && version(p)==1);"'LEGACY_SCALAR'"}
            val encoding=db.query("SELECT $marker FROM supply_container WHERE id=? AND medication_id=?",arrayOf(id,before.getLong("medication_id"))).use{require(it.moveToFirst());it.getString(0)}
            if(version(p)==1)require(encoding==InventoryEncoding.LEGACY)
            else {
                require(before.get("inventory_encoding") is String && before.getString("inventory_encoding")==encoding)
                val uuid=p.get("container_uuid");require(uuid is String && ReferencePayload.uuid(uuid) && uuid==containerUuid(db,id,before.getLong("medication_id")))
            }
            last[id]=ref to p
        }}
        db.query("SELECT id,state FROM supply_container").use { c -> while(c.moveToNext()) {
            val id=c.getLong(0);val state=c.getString(1);val event=last[id]
            require((state=="VOIDED")== (event?.first?.startsWith("package-void:")==true)){"Missing or inconsistent package correction"}
            if(state=="VOIDED") {
                val current=rows(db,"supply_container","id=?",id).getJSONObject(0).put("state",event!!.second.getJSONObject("container").getString("state"))
                val encoding=if(InventoryEncoding.present(db))"inventory_encoding" else {require(db.version<12 && version(event.second)==1);"'LEGACY_SCALAR'"}
                val box=db.query("SELECT medication_id,$encoding FROM supply_container WHERE id=?",arrayOf(id)).use{q->require(q.moveToFirst());ContainerEntity(id=id,medication_id=q.getLong(0),capacity=current.getDouble("capacity"),initial_used_amount=current.getDouble("initial_used_amount"),used_amount=current.getDouble("used_amount"),state=state,inventory_encoding=q.getString(1))}
                require(sameContainer(db,box,event.second,current))
                require(ledger(db,id)==event.second.getString("ledger_sha256"))
                db.query("SELECT 1 FROM supply_transaction WHERE container_id=? AND kind!='ADJUST' LIMIT 1",arrayOf(id)).use{require(!it.moveToFirst())}
            }
        }}
    }
}
