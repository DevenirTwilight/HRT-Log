package net.plainnotes.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.security.MessageDigest
import java.util.UUID

/** Local, append-retained operation evidence. Portable medical facts remain attachment_link.
 * Caller supplies the existing scoped native transaction; this class never commits files or keys. */
internal object AttachmentRelations {
    data class Intent(val operation:UUID,val document:UUID,val documentRevision:Long,val target:MedicalAttachmentOperations.Target,
        val record:String,val unlink:MedicalAttachmentOperations.Relationship?) {
        fun digest():ByteArray {
            val fields=listOf("notes.attachment.relation.v1",operation.toString(),document.toString(),documentRevision.toString(),target.kind.name,target.id.toString(),record,
                unlink?.link?.toString().orEmpty(),unlink?.revision?.toString().orEmpty())
            val bytes=java.io.ByteArrayOutputStream().also {b->java.io.DataOutputStream(b).use {out->fields.forEach {v->val text=v.toByteArray(Charsets.UTF_8);out.writeInt(text.size);out.write(text)}}}.toByteArray()
            return try {MessageDigest.getInstance("SHA-256").digest(bytes)}finally {bytes.fill(0)}
        }
    }
    data class Fact(val operation:UUID,val phase:String,val link:UUID?,val revision:Long?)
    fun status(raw:SupportSQLiteDatabase,s:AttachmentIdentity.Scope,operation:UUID):Fact? = raw.query(
        "SELECT phase,result_link_uuid,result_revision FROM attachment_relation_operation WHERE store_uuid=? AND record_epoch=? AND operation_uuid=?",arrayOf(s.dataset,s.epoch,operation.toString())).use {
        if(!it.moveToFirst())null else Fact(operation,it.getString(0),if(it.isNull(1))null else UUID.fromString(it.getString(1)),if(it.isNull(2))null else it.getLong(2))
    }
    /** PREPARED is durable before authentication. Existing UUIDs never resume an uncertain write. */
    fun prepare(raw:SupportSQLiteDatabase,s:AttachmentIdentity.Scope,i:Intent):Fact? {
        check(raw.inTransaction())
        status(raw,s,i.operation)?.let {fact->
            raw.query("SELECT payload_sha256 FROM attachment_relation_operation WHERE store_uuid=? AND operation_uuid=?",arrayOf(s.dataset,i.operation.toString())).use {
                check(it.moveToFirst() && MessageDigest.isEqual(it.getBlob(0),i.digest())) {"Conflicting association operation UUID"}
            }
            return fact
        }
        raw.insert("attachment_relation_operation",SQLiteDatabase.CONFLICT_ABORT,ContentValues().apply {
            put("store_uuid",s.dataset);put("record_epoch",s.epoch);put("operation_uuid",i.operation.toString())
            put("kind",if(i.unlink==null)"LINK" else "UNLINK");put("target_kind",i.target.kind.name);put("legacy_id",i.target.id)
            put("record_uuid",i.record);put("document_uuid",i.document.toString());put("expected_document_revision",i.documentRevision)
            put("expected_link_uuid",i.unlink?.link?.toString());put("expected_link_revision",i.unlink?.revision)
            put("payload_sha256",i.digest());put("phase","PREPARED")
        }).also {check(it!=-1L)}
        return null
    }
    fun commit(raw:SupportSQLiteDatabase,s:AttachmentIdentity.Scope,i:Intent):Fact {
        check(raw.inTransaction());check(status(raw,s,i.operation)?.phase=="PREPARED") {"Operation needs read-only reconciliation"}
        raw.query("SELECT 1 FROM attachment_document WHERE store_uuid=? AND document_uuid=? AND state='ACTIVE' AND revision=?",arrayOf(s.dataset,i.document.toString(),i.documentRevision)).use {check(it.moveToFirst()) {"Document changed during association"}}
        var link:UUID;var revision:Long
        if(i.unlink!=null) {
            link=i.unlink.link;revision=Math.addExact(i.unlink.revision,1)
            raw.compileStatement("UPDATE attachment_link SET state='RETRACTED',revision=revision+1 WHERE store_uuid=? AND record_epoch=? AND link_uuid=? AND record_uuid=? AND document_uuid=? AND state='ACTIVE' AND revision=?").use {
                it.bindString(1,s.dataset);it.bindString(2,s.epoch);it.bindString(3,link.toString());it.bindString(4,i.record);it.bindString(5,i.document.toString());it.bindLong(6,i.unlink.revision)
                check(it.executeUpdateDelete()==1) {"Selected relationship changed"}
            }
        } else {
            val existing=raw.query("SELECT link_uuid,revision FROM attachment_link WHERE store_uuid=? AND record_epoch=? AND record_uuid=? AND document_uuid=? AND state='ACTIVE'",arrayOf(s.dataset,s.epoch,i.record,i.document.toString())).use {
                if(it.moveToFirst())UUID.fromString(it.getString(0)) to it.getLong(1) else null
            }
            link=existing?.first ?: i.operation;revision=existing?.second ?: 1
            if(existing==null)raw.execSQL("INSERT INTO attachment_link(store_uuid,link_uuid,record_epoch,record_uuid,document_uuid,revision,state) VALUES(?,?,?,?,?,1,'ACTIVE')",arrayOf(s.dataset,link.toString(),s.epoch,i.record,i.document.toString()))
        }
        raw.execSQL("UPDATE attachment_relation_operation SET phase='COMMITTED',result_link_uuid=?,result_revision=? WHERE store_uuid=? AND record_epoch=? AND operation_uuid=? AND phase='PREPARED'",arrayOf(link.toString(),revision,s.dataset,s.epoch,i.operation.toString()))
        return requireNotNull(status(raw,s,i.operation)).also {check(it.phase=="COMMITTED")}
    }
    fun abort(raw:SupportSQLiteDatabase,s:AttachmentIdentity.Scope,operation:UUID):Fact? {
        check(raw.inTransaction())
        raw.execSQL("UPDATE attachment_relation_operation SET phase='ABORTED' WHERE store_uuid=? AND record_epoch=? AND operation_uuid=? AND phase='PREPARED'",arrayOf(s.dataset,s.epoch,operation.toString()))
        return status(raw,s,operation)
    }
}
