package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/** B1 metadata journal only. No import, attestation, publication or filesystem cleanup is enabled. */
internal object AttachmentOperations {
    fun prepare(db:SupportSQLiteDatabase, scope:AttachmentIdentity.Scope, idempotency:UUID,
                kind:String, reservedBytes:Long=0, reservedObjects:Long=0):String {
        check(db.inTransaction());check(AttachmentIdentity.current(db)==scope)
        require(kind in setOf("IMPORT","REPLACE","TRASH","RESTORE","PURGE","BACKUP","FULL_RESTORE","OLD_RESTORE"))
        require(reservedBytes>=0 && reservedObjects>=0)
        db.query("SELECT operation_uuid,kind,reserved_bytes,reserved_objects FROM attachment_operation WHERE store_uuid=? AND idempotency_uuid=?",
            arrayOf(scope.dataset,idempotency.toString())).use {
            if(it.moveToFirst()) {
                check(it.getString(1)==kind && it.getLong(2)==reservedBytes && it.getLong(3)==reservedObjects){"Conflicting replay"}
                return it.getString(0)
            }
        }
        val generation=db.query("SELECT generation FROM attachment_store WHERE store_uuid=? AND store_state='CURRENT'",arrayOf(scope.dataset))
            .use {check(it.moveToFirst());it.getLong(0)}
        val operation=UUID.randomUUID().toString()
        db.execSQL("INSERT INTO attachment_operation(store_uuid,operation_uuid,idempotency_uuid,kind,phase,expected_generation,reserved_bytes,reserved_objects,updated_utc) VALUES (?,?,?,?,'PREPARED',?,?,?,?)",
            arrayOf(scope.dataset,operation,idempotency.toString(),kind,generation,reservedBytes,reservedObjects,System.currentTimeMillis()))
        return operation
    }
    /** Repeated cancel is harmless. No automatic reactivation of a terminal operation. */
    fun cancel(db:SupportSQLiteDatabase,scope:AttachmentIdentity.Scope,operation:String) {
        check(db.inTransaction());check(AttachmentIdentity.current(db)==scope)
        db.execSQL("UPDATE attachment_operation SET phase='ABORTED',staged_wrapped_dek=NULL,updated_utc=? WHERE store_uuid=? AND operation_uuid=? AND phase IN('PREPARED','WRITING')",
            arrayOf(System.currentTimeMillis(),scope.dataset,operation))
    }
}
