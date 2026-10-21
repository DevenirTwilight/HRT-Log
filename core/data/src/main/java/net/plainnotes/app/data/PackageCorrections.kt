package net.plainnotes.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject
import net.plainnotes.app.domain.measurement.MeasureResult
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
    private fun ledgerHead(db:SupportSQLiteDatabase,id:Long)=db.query("SELECT COALESCE(MAX(id),0) FROM supply_transaction WHERE container_id=?",arrayOf(id)).use{it.moveToFirst();it.getLong(0)}
    private fun exactRow(row:JSONObject):JSONObject {
        if(row.optString("inventory_encoding")==InventoryEncoding.EXACT) {
            for(key in listOf("capacity","initial_used_amount","used_amount"))row.put(key,InventoryEncoding.exactInteger(row.getDouble(key)))
        }
        return row
    }
    private fun sameBody(a:JSONObject,b:JSONObject):Boolean {
        fun body(p:JSONObject)=JSONObject(p.toString()).apply {remove("operation_id");remove("action");remove("restores")}.toString()
        return body(a)==body(b)
    }
    /** Every v2 event retains its own pre-void ledger prefix, even after a later restore/use. */
    private fun validateHistorical(db:SupportSQLiteDatabase,id:Long,p:JSONObject) {
        val before=p.getJSONObject("container")
        val head=p.get("ledger_head_id");require(head is String && Regex("0|[1-9][0-9]{0,18}").matches(head))
        val n=head.toLong();require(n>=0)
        val entries=Trash.rows(db,"supply_transaction","container_id=? AND id<=? ORDER BY id",arrayOf(id,n))
        require((if(entries.length()==0)0L else entries.getJSONObject(entries.length()-1).getLong("id"))==n)
        require(hash(entries.toString())==p.getString("ledger_sha256"))
        for(i in 0 until entries.length())require(entries.getJSONObject(i).getString("kind")=="ADJUST")
        if(before.getString("inventory_encoding")==InventoryEncoding.EXACT) {
            fun integer(key:String):Long {val v=before.get(key);require(v is Int || v is Long);return (v as Number).toLong().also{require(it in 0..InventoryEncoding.MAX_EXACT_INTEGER)}}
            val capacity=integer("capacity");val initial=integer("initial_used_amount");val used=integer("used_amount")
            require(capacity>0 && initial==0L && used<=capacity)
            val frozen=db.query("SELECT s.payload_json FROM reference_event e JOIN reference_snapshot s ON s.uuid=e.snapshot_uuid WHERE e.subject_uuid=? AND e.revision=1 AND s.kind='PRESENTATION'",arrayOf(p.getString("container_uuid"))).use{require(it.moveToFirst());PackageMeasurementPayload.read(it.getString(0))}
            val scale=frozen.stockScale();require(scale is MeasureResult.Success && scale.value.capacityQuanta==capacity)
            val deltas=(0 until entries.length()).map {i->
                val value=entries.getJSONObject(i).getDouble("used_delta");require(value.isFinite() && value!=0.0 && kotlin.math.abs(value)<=InventoryEncoding.MAX_EXACT_INTEGER && value.toLong().toDouble()==value);value.toLong()
            }
            val replay=scale.value.restoredUsed(initial,deltas);require(replay is MeasureResult.Success && replay.value==used)
        }
    }
    private fun version(payload:JSONObject):Int {
        val value=payload.get("version");require(value is Int && value in 1..2);return value
    }
    private fun containerUuid(db:SupportSQLiteDatabase,id:Long,medication:Long):String? = db.query(
        "SELECT c.uuid FROM reference_identity c JOIN reference_identity m ON m.uuid=c.owner_uuid WHERE c.kind='CONTAINER' AND c.live_handle=? AND m.kind='MEDICATION' AND m.live_handle=? AND m.dataset_uuid=c.dataset_uuid",
        arrayOf(id,medication)).use {c->if(c.moveToFirst())c.getString(0) else null}
    private fun sameContainer(db:SupportSQLiteDatabase,box:ContainerEntity,payload:JSONObject,current:JSONObject):Boolean {
        val before=payload.getJSONObject("container")
        if(version(payload)==2)exactRow(current)
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
    private fun append(db:SupportSQLiteDatabase,id:Long,action:String,payload:JSONObject,now:Instant,operationUuid:String?=null) {
        val operation=operationUuid ?: UUID.randomUUID().toString();require(ReferencePayload.uuid(operation))
        db.insert("trash_item",SQLiteDatabase.CONFLICT_ABORT,ContentValues().apply{
            put("kind",Trash.CORRECTION);put("ref","package-$action:$id:$operation")
            put("item_date",now.atZone(ZoneOffset.UTC).toLocalDate().toString());put("deleted_utc",now.toEpochMilli())
            put("payload_json",payload.put("operation_id",operation).put("action",action).toString());put("state",Trash.HIDDEN)
        })
    }
    suspend fun void(db:SupportSQLiteDatabase,dao:NotesDao,id:Long,now:Instant,operationUuid:String?=null) {
        val box=dao.container(id)
        if(box.state=="VOIDED")return // No second evidence row or second inventory change.
        require(box.state in realStates)
        if(dao.supplyForContainer(id).any{it.kind!="ADJUST"})throw PackageConsumptionConflict()
        val scope=ReferenceStore.scope(db)
        val medication=ReferenceStore.identity(db,scope,"MEDICATION",box.medication_id,null)
        val subject=ReferenceStore.identity(db,scope,"CONTAINER",id,medication)
        val before=exactRow(rows(db,"supply_container","id=?",id).getJSONObject(0))
        val payload=JSONObject().put("version",2).put("container_uuid",subject).put("container",before)
            .put("ledger_sha256",ledger(db,id)).put("ledger_head_id",ledgerHead(db,id).toString()).put("context_sha256",context(db,box))
        append(db,id,"void",payload,now,operationUuid)
        dao.setContainerState(id,"VOIDED",box.opened_on)
    }
    suspend fun restore(db:SupportSQLiteDatabase,dao:NotesDao,id:Long,now:Instant,operationUuid:String?=null) {
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
        append(db,id,"restore",JSONObject(payload.toString()).put("restores",event.first),now,operationUuid)
        dao.setContainerState(id,before.getString("state"),box.opened_on)
    }
    /** Used after backup restore, while raw maintenance is still in the caller's atomic transaction. */
    fun validate(db:SupportSQLiteDatabase) {
        val last=mutableMapOf<Long,Pair<String,JSONObject>>();val operations=HashSet<String>()
        db.query("SELECT ref,payload_json,state FROM trash_item WHERE ref LIKE 'package-void:%' OR ref LIKE 'package-restore:%' ORDER BY id").use { c -> while(c.moveToNext()) {
            val ref=c.getString(0);val parts=ref.split(':');require(parts.size==3);UUID.fromString(parts[2])
            require(operations.add(parts[2])) {"Duplicate package operation UUID"}
            if(db.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name='reference_dataset'").use{it.moveToFirst()})for(table in listOf("measured_operation","reference_event"))db.query("SELECT 1 FROM $table WHERE operation_uuid=?",arrayOf(parts[2])).use{require(!it.moveToFirst()) {"Cross-domain package operation collision"}}
            val id=parts[1].toLong();val text=c.getString(1);val legacy=JSONObject(text)
            val p=if(version(legacy)==2)BackupJsonReader.read(text) else legacy;val before=p.getJSONObject("container")
            require(c.getString(2)==Trash.HIDDEN && version(p) in 1..2 && p.getString("operation_id")==parts[2] &&
                before.getLong("id")==id && before.getString("state") in realStates)
            require(p.getString("ledger_sha256").matches(Regex("[0-9a-f]{64}")) && p.getString("context_sha256").matches(Regex("[0-9a-f]{64}")))
            val action=if(parts[0]=="package-void")"void" else "restore";require(p.getString("action")==action)
            require(if(action=="void")last[id]?.first?.startsWith("package-void:")!=true else p.getString("restores")==last[id]?.first)
            if(action=="restore")require(sameBody(last.getValue(id).second,p)) {"Changed restore evidence body"}
            val marker=if(InventoryEncoding.present(db))"inventory_encoding" else {require(db.version<InventoryEncoding.ROOM_FORMAT_VERSION && version(p)==1);"'LEGACY_SCALAR'"}
            val encoding=db.query("SELECT $marker FROM supply_container WHERE id=? AND medication_id=?",arrayOf(id,before.getLong("medication_id"))).use{require(it.moveToFirst());it.getString(0)}
            if(version(p)==1)require(encoding==InventoryEncoding.LEGACY)
            else {
                require(before.get("inventory_encoding") is String && before.getString("inventory_encoding")==encoding)
                val uuid=p.get("container_uuid");require(uuid is String && ReferencePayload.uuid(uuid) && uuid==containerUuid(db,id,before.getLong("medication_id")))
            }
            if(version(p)==2)validateHistorical(db,id,p)
            last[id]=ref to p
        }}
        db.query("SELECT id,state FROM supply_container").use { c -> while(c.moveToNext()) {
            val id=c.getLong(0);val state=c.getString(1);val event=last[id]
            require((state=="VOIDED")== (event?.first?.startsWith("package-void:")==true)){"Missing or inconsistent package correction"}
            if(state=="VOIDED") {
                val current=rows(db,"supply_container","id=?",id).getJSONObject(0).put("state",event!!.second.getJSONObject("container").getString("state"))
                val encoding=if(InventoryEncoding.present(db))"inventory_encoding" else {require(db.version<InventoryEncoding.ROOM_FORMAT_VERSION && version(event.second)==1);"'LEGACY_SCALAR'"}
                val box=db.query("SELECT medication_id,$encoding FROM supply_container WHERE id=?",arrayOf(id)).use{q->require(q.moveToFirst());ContainerEntity(id=id,medication_id=q.getLong(0),capacity=current.getDouble("capacity"),initial_used_amount=current.getDouble("initial_used_amount"),used_amount=current.getDouble("used_amount"),state=state,inventory_encoding=q.getString(1))}
                require(sameContainer(db,box,event.second,current))
                require(ledger(db,id)==event.second.getString("ledger_sha256"))
                db.query("SELECT 1 FROM supply_transaction WHERE container_id=? AND kind!='ADJUST' LIMIT 1",arrayOf(id)).use{require(!it.moveToFirst())}
            }
        }}
    }
}
