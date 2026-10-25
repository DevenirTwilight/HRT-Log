package net.plainnotes.app.data

import net.plainnotes.app.domain.AttachmentSession
import java.security.MessageDigest
import java.util.UUID

/** OFF joint admission for the existing object transaction. A permit is never a cached scan:
 * immutable request identity is durable BEFORE IO, and checked inside each real root/child commit.
 * Interrupted reservations retain their entire allowance; there is no guessed refund or deletion. */
internal class LibraryAttachmentAdmission(private val access:DatabaseAccess) {
    class Permit private constructor(private val access:DatabaseAccess,private val db:NotesDatabase,private val scope:AttachmentIdentity.Scope,
        private val request:AttachmentPersistentStore.Request) {
        private val root get()=DatabaseAccess.authorityRoot(db).openHelper.writableDatabase
        fun request(value:AttachmentPersistentStore.Request) {require(MessageDigest.isEqual(value.digest(scope),request.digest(scope)))}
        fun check() {
            check(root.inTransaction())
            DatabaseAccess.assertActiveHandle(db)
            check(AttachmentIdentity.current(db.openHelper.writableDatabase)==scope)
            root.query("SELECT generation,dataset,epoch,request_sha256,state FROM library_write_reservation WHERE operation_uuid=?",arrayOf(request.operation.toString())).use {c->
                check(c.moveToFirst() && c.getString(0)==DatabaseAccess.handleIdentity(db) && c.getString(1)==scope.dataset && c.getString(2)==scope.epoch)
                check(MessageDigest.isEqual(c.getBlob(3),request.digest(scope)) && c.getString(4) in setOf("STARTED","COMMITTED"))
            }
        }
        fun finish(cap:AttachmentSession.Capability)=AttachmentTransaction.commit(db,scope,cap) {
            committedIfProven()
            root.query("SELECT state FROM library_write_reservation WHERE operation_uuid=?",arrayOf(request.operation.toString())).use {check(it.moveToFirst() && it.getString(0)=="COMMITTED")}
        }
        private fun committedIfProven() {
            check()
            db.openHelper.writableDatabase.query("""SELECT j.request_sha256,j.proof,o.phase FROM attachment_journal j
                JOIN attachment_operation o USING(store_uuid,operation_uuid) WHERE j.store_uuid=? AND j.record_epoch=? AND j.operation_uuid=?""",
                arrayOf(scope.dataset,scope.epoch,request.operation.toString())).use {c->
                if(!c.moveToFirst())return
                check(MessageDigest.isEqual(c.getBlob(0),request.digest(scope)))
                if(c.getString(1)=="COMMITTED" && c.getString(2)=="COMMITTED") {
                    val lifecycle=LibraryLifecycle(access)
                    val report=lifecycle.persist(DatabaseAccess.authorityRoot(db),db,scope,request.operation,lifecycle.fresh(DatabaseAccess.authorityRoot(db),db,::check),collection=true)
                    check(report.reason=="FULL_SNAPSHOT_AUTHENTICATION_REQUIRED") {"Joint retained set changed before commit"}
                    LibraryRestoreAdmission.inspect(access.applicationContext,root,::check)
                    root.execSQL("UPDATE library_write_reservation SET state='COMMITTED' WHERE operation_uuid=? AND state='STARTED'",arrayOf(request.operation.toString()))
                }
            }
        }
        companion object {
            internal fun reserved(access:DatabaseAccess,db:NotesDatabase,scope:AttachmentIdentity.Scope,request:AttachmentPersistentStore.Request)=Permit(access,db,scope,request)
        }
    }
    private fun available(db:NotesDatabase,scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability) {
        val root=access.legacyRoot();val raw=root.openHelper.writableDatabase
        check(raw.inTransaction());LibraryAuthority.requireNoPending(raw);LibraryCollectionSchema.requireNoWrites(raw)
        fun active() {cap.check();DatabaseAccess.assertActiveHandle(db);check(AttachmentIdentity.current(db.openHelper.writableDatabase)==scope)}
        active()
        val lifecycle=LibraryLifecycle(access)
        val inventory=lifecycle.persist(root,db,scope,UUID.randomUUID(),lifecycle.fresh(root,db,::active),collection=true)
        check(inventory.reason=="FULL_SNAPSHOT_AUTHENTICATION_REQUIRED") {"Joint resources not proven: ${inventory.reason}"}
        val libraries=inventory.items.count {it.category in setOf("CURRENT_BUSINESS","RETAINED_BUSINESS")}
        require(libraries in 1..8)
        val a=LibraryRestoreAdmission.inspect(access.applicationContext,raw,::active)
        val reserve=reserve(a.unit)
        require(a.aliases>=2 && a.ciphertext>=reserve && a.free>=Math.addExact(reserve,32L*1024*1024)) {"Joint attachment budget exhausted"}
    }
    /** Early refusal before provider read/typed binding; never authorizes later IO by itself. */
    fun preflight(db:NotesDatabase,scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability)=
        AttachmentTransaction.commit(db,scope,cap) {available(db,scope,cap)}

    fun prepare(db:NotesDatabase,scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,
        request:()->AttachmentPersistentStore.Request):Pair<AttachmentPersistentStore.Request,Permit> =
        AttachmentTransaction.commit(db,scope,cap) {
            available(db,scope,cap)
            // Binding and reservation share this native root gate. An ENOSPC/root commit failure
            // may retain a harmless typed identity in the child, but never authorizes object IO.
            val value=request();val raw=access.legacyRoot().openHelper.writableDatabase
            raw.query("SELECT 1 FROM attachment_operation WHERE operation_uuid=? OR idempotency_uuid=? LIMIT 1",arrayOf(value.operation.toString(),value.operation.toString())).use {check(!it.moveToFirst()) {"Operation UUID retained in the legacy library"}}
            raw.query("SELECT 1 FROM library_write_reservation WHERE operation_uuid=?",arrayOf(value.operation.toString())).use {check(!it.moveToFirst()) {"Reconcile existing operation; never reopen changed input"}}
            val fs=android.system.Os.statvfs(access.applicationContext.noBackupFilesDir.path)
            raw.execSQL("INSERT INTO library_write_reservation VALUES(?,?,?,?,?,?,'STARTED',?)",arrayOf(value.operation.toString(),DatabaseAccess.handleIdentity(db),scope.dataset,scope.epoch,value.digest(scope),reserve(fs.f_frsize),System.currentTimeMillis()))
            value to Permit.reserved(access,db,scope,value)
        }
    /** Read-only child reconciliation after a lost root response. Never opens the provider or
     * repeats encryption. A started reservation without an exact committed journal stays charged. */
    fun reconcile(db:NotesDatabase,scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,operation:UUID):String?=
        AttachmentTransaction.commit(db,scope,cap) {
            val raw=access.legacyRoot().openHelper.writableDatabase
            val reservation=raw.query("SELECT generation,dataset,epoch,request_sha256,state FROM library_write_reservation WHERE operation_uuid=?",arrayOf(operation.toString())).use {c->
                if(!c.moveToFirst())null else {
                    check(c.getString(0)==DatabaseAccess.handleIdentity(db) && c.getString(1)==scope.dataset && c.getString(2)==scope.epoch)
                    c.getBlob(3) to c.getString(4)
                }
            } ?: return@commit null
            val committed=db.openHelper.writableDatabase.query("""SELECT j.request_sha256,j.proof,o.phase FROM attachment_journal j
                JOIN attachment_operation o USING(store_uuid,operation_uuid) WHERE j.store_uuid=? AND j.record_epoch=? AND j.operation_uuid=?""",arrayOf(scope.dataset,scope.epoch,operation.toString())).use {c->
                if(!c.moveToFirst())false else {check(MessageDigest.isEqual(c.getBlob(0),reservation.first));c.getString(1)=="COMMITTED" && c.getString(2)=="COMMITTED"}
            }
            if(reservation.second=="COMMITTED")check(committed) {"Committed reservation lacks its journal"}
            if(committed && reservation.second=="STARTED") {
                val thread=Thread.currentThread()
                fun active() {check(Thread.currentThread()===thread && raw.inTransaction());cap.check();DatabaseAccess.assertActiveHandle(db)}
                AttachmentBackupPreparation.fenced(DatabaseAccess.storageContext(db,access.applicationContext),db.openHelper.writableDatabase,::active)
                    .capture(scope,cap).use {it.manifest()}
                val lifecycle=LibraryLifecycle(access)
                val inventory=lifecycle.persist(access.legacyRoot(),db,scope,operation,lifecycle.fresh(access.legacyRoot(),db,::active),collection=true)
                check(inventory.reason=="FULL_SNAPSHOT_AUTHENTICATION_REQUIRED")
                LibraryRestoreAdmission.inspect(access.applicationContext,raw,::active)
                raw.execSQL("UPDATE library_write_reservation SET state='COMMITTED' WHERE operation_uuid=? AND state='STARTED'",arrayOf(operation.toString()))
                "COMMITTED"
            } else reservation.second
        }
    companion object {
        private fun reserve(unit:Long):Long {
            require(unit>0)
            val maximum=AttachmentObjectStreams.MAXIMUM_BYTES+65536
            return Math.addExact(Math.multiplyExact((maximum+unit-1)/unit,unit),Math.multiplyExact(2,unit))
        }
    }
}
