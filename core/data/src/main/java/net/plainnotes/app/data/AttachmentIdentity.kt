package net.plainnotes.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** Transaction-only identity bridge. Never searches by date, filename or reused integer ID. */
internal object AttachmentIdentity {
    data class Scope(val dataset:String, val logicalVault:String, val epoch:String)
    private fun uuid()=UUID.randomUUID().toString()
    private fun exists(db:SupportSQLiteDatabase)=db.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name='attachment_store'").use{it.moveToFirst()}
    fun current(db:SupportSQLiteDatabase):Scope? {
        if(!exists(db))return null
        return db.query("SELECT store_uuid,logical_vault_uuid,record_epoch FROM attachment_store WHERE store_state='CURRENT' AND space='PRIMARY'").use {
            if(!it.moveToFirst())null else Scope(it.getString(0),it.getString(1),it.getString(2)).also{_ -> check(!it.moveToNext())}
        }
    }
    /** Explicit PRIMARY pin is required. DECOY has empty attachment tables and no namespace/key/path. */
    fun initialize(db:SupportSQLiteDatabase, primary:Boolean):Scope? {
        check(db.inTransaction())
        if(!primary || !exists(db))return null
        current(db)?.let{return it}
        return fresh(db)
    }
    private fun fresh(db:SupportSQLiteDatabase):Scope {
        val s=Scope(uuid(),uuid(),uuid())
        db.execSQL("INSERT INTO attachment_store(store_uuid,logical_vault_uuid,store_state,space,record_epoch,generation,active_generation_uuid,feature_enabled) VALUES (?,?,'CURRENT','PRIMARY',?,1,NULL,0)",arrayOf(s.dataset,s.logicalVault,s.epoch))
        val physical=uuid()
        db.execSQL("INSERT INTO attachment_generation(store_uuid,generation_uuid,state,manifest_digest,created_utc) VALUES (?,?,'STAGING',NULL,?)",arrayOf(s.dataset,physical,System.currentTimeMillis()))
        check(physical!=s.dataset && physical!=s.logicalVault && physical!=s.epoch)
        check(current(db)==s);return s
    }
    /** Called inside the SAME transaction as structured replacement, never after commit. */
    fun replaceStructuredDataset(db:SupportSQLiteDatabase) {
        check(db.inTransaction());val old=current(db) ?: return
        db.execSQL("UPDATE attachment_link SET state='QUARANTINED',revision=revision+1 WHERE store_uuid=? AND state!='QUARANTINED'",arrayOf(old.dataset))
        db.execSQL("UPDATE attachment_operation SET phase='ABORTED',staged_wrapped_dek=NULL WHERE store_uuid=? AND phase IN('PREPARED','WRITING')",arrayOf(old.dataset))
        db.execSQL("UPDATE attachment_store SET store_state='RETIRED' WHERE store_uuid=? AND store_state='CURRENT'",arrayOf(old.dataset))
        fresh(db)
    }
    fun bind(db:SupportSQLiteDatabase,expected:Scope,kind:String,id:Long):String {
        check(db.inTransaction());check(current(db)==expected){"Stale dataset"}
        val table=when(kind){"APPOINTMENT"->"appointment";"LAB"->"lab_value";"DOSE_RECORD"->"dose_record";else->error("Unsupported target")}
        val where=if(kind=="DOSE_RECORD") "id=? AND deleted_at_utc IS NULL AND status IN('ON_TIME','LATE') AND taken_utc IS NOT NULL AND actual_dose>0" else "id=?"
        val rows=Trash.rows(db,table,where,arrayOf(id));require(rows.length()==1){"Missing real typed record"}
        db.query("SELECT record_uuid,state FROM attachment_record_identity WHERE store_uuid=? AND record_epoch=? AND target_kind=? AND legacy_id=? AND state!='PURGED'",arrayOf(expected.dataset,expected.epoch,kind,id)).use {
            if(it.moveToFirst()){check(it.getString(1)=="ACTIVE");return it.getString(0)}
        }
        val record=uuid()
        db.insert("attachment_record_identity",SQLiteDatabase.CONFLICT_ABORT,ContentValues().apply {
            put("store_uuid",expected.dataset);put("record_epoch",expected.epoch);put("record_uuid",record);put("target_kind",kind);put("legacy_id",id);put("state","ACTIVE");put("evidence_digest",digest(rows.toString()))
        })
        return record
    }
    private fun kind(trashKind:String)=when(trashKind){Trash.APPOINTMENT->"APPOINTMENT";Trash.LAB,Trash.LAB_PANEL->"LAB";Trash.RECORD->"DOSE_RECORD";else->null}
    private fun digest(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    fun trashed(db:SupportSQLiteDatabase,trashKind:String,ref:String,trashId:Long,payload:String) {
        val type=kind(trashKind) ?: return;val s=current(db) ?: return
        // LAB-02: a whole lab panel carries the identities of every result in it (its ref is the panel uuid, not a row id).
        val ids=if(trashKind==Trash.LAB_PANEL)org.json.JSONObject(payload).getJSONObject("tables").getJSONArray("lab_value").let{a->(0 until a.length()).map{a.getJSONObject(it).getLong("id")}}
            else listOf(ref.toLongOrNull() ?: error("Invalid record ref"))
        ids.forEach{id->db.execSQL("UPDATE attachment_record_identity SET state='TRASHED',trash_id=?,evidence_digest=? WHERE store_uuid=? AND record_epoch=? AND target_kind=? AND legacy_id=? AND state='ACTIVE'",arrayOf(trashId,digest(payload),s.dataset,s.epoch,type,id))}
        db.execSQL("UPDATE attachment_link SET state='QUARANTINED',revision=revision+1 WHERE store_uuid=? AND record_epoch=? AND record_uuid IN(SELECT record_uuid FROM attachment_record_identity WHERE store_uuid=? AND trash_id=?) AND state='ACTIVE'",arrayOf(s.dataset,s.epoch,s.dataset,trashId))
    }
    /** Only exact retained trash ownership proves restoration, including payload commitment. */
    fun restored(db:SupportSQLiteDatabase,item:TrashItemEntity) {
        val s=current(db) ?: return;kind(item.kind) ?: return
        db.query("SELECT evidence_digest FROM attachment_record_identity WHERE store_uuid=? AND record_epoch=? AND trash_id=? AND state='TRASHED'",arrayOf(s.dataset,s.epoch,item.id)).use {
            while(it.moveToNext())check(MessageDigest.isEqual(it.getBlob(0),digest(item.payload_json))){"Trash identity evidence changed"}
        }
        db.execSQL("UPDATE attachment_record_identity SET state='ACTIVE',trash_id=NULL WHERE store_uuid=? AND record_epoch=? AND trash_id=? AND state='TRASHED'",arrayOf(s.dataset,s.epoch,item.id))
        // Links remain quarantined; future B4 must authenticate actual files before reassociation.
    }
    fun purged(db:SupportSQLiteDatabase,item:TrashItemEntity) {
        val s=current(db) ?: return;kind(item.kind) ?: return
        db.execSQL("UPDATE attachment_record_identity SET state='PURGED',trash_id=NULL WHERE store_uuid=? AND record_epoch=? AND trash_id=? AND state='TRASHED'",arrayOf(s.dataset,s.epoch,item.id))
    }
}
