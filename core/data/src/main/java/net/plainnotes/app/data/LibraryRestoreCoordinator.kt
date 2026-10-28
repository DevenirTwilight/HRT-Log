package net.plainnotes.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import net.plainnotes.app.domain.AttachmentSession
import java.security.MessageDigest
import java.util.UUID

/** Internal OFF full-library restore. Never overwrite/delete the original root or adopt an
 * interrupted candidate. The ONLY visibility commit is library_authority in the encrypted root. */
internal class LibraryRestoreCoordinator(private val access:DatabaseAccess,
    private val policy:LibraryRestorePolicy=LibraryRestorePolicy(),private val checkpoint:(Boundary)->Unit={}) {
    enum class Boundary { PREPARED, DATABASE_COMMITTED, OBJECTS_COMMITTED, REOPEN_VERIFIED, READY, IN_SWITCH, SWITCHED }
    data class Committed(val operation:UUID,val generation:UUID,val dataset:UUID,val epoch:UUID,
        val requiresNewSession:Boolean=true)
    private val context get()=access.applicationContext
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
    fun restore(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,operation:UUID,
        archive:MedicalArchiveReader.Archive):Committed=access.holdGeneration().use {restoreHeld(scope,cap,operation,archive)}
    private fun restoreHeld(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,operation:UUID,
        archive:MedicalArchiveReader.Archive):Committed {
        check(Looper.myLooper()!=Looper.getMainLooper())
        val current=access.get(Space.PRIMARY);val root=access.legacyRoot()
        check(!root.openHelper.writableDatabase.inTransaction() && !current.openHelper.writableDatabase.inTransaction())
        root.openHelper.writableDatabase.execSQL("PRAGMA synchronous=FULL")
        val base=DatabaseAccess.handleIdentity(current)
        val owner=cap.fork()
        var reservedByThisCall=false
        fun checkCurrent() {
            owner.check();DatabaseAccess.assertActiveHandle(current)
            check(current.isOpen && AttachmentIdentity.current(current.openHelper.writableDatabase)==scope) {"Restore session dataset changed"}
            if(reservedByThisCall)root.openHelper.writableDatabase.query("SELECT state FROM library_candidate WHERE operation_uuid=?",arrayOf(operation.toString())).use {
                check(it.moveToFirst() && it.getString(0) in setOf("PREPARED","READY")) {"Restore was aborted or superseded"}
            }
        }
        fun <T> rootTransaction(retiring:Boolean=false,block:()->T):T {
            root.beginTransaction()
            try {
                root.openHelper.writableDatabase.query("PRAGMA synchronous").use {check(it.moveToFirst() && it.getInt(0)>=2)}
                checkCurrent();val result=block() // Full preflight/IO never holds the session monitor.
                return owner.publishFor(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch)) {
                    if(!retiring)checkCurrent()
                    root.setTransactionSuccessful();root.endTransaction()
                    result
                }
            } finally {if(root.isOpen && root.openHelper.writableDatabase.inTransaction())root.endTransaction()}
        }
        try {
            checkCurrent();require(archive.wholeArchiveAuthenticated)
            val manifest=archive.manifest()
            val provenance=archive.retainedManifest {it.copyOf().also {copy->owner.ownBytes(copy)}}
            val records=archive.records {LibraryRestoreAdmission.memory(it.size,provenance.size);it.copyOf().also {copy->owner.ownBytes(copy)}}
            val digest=MessageDigest.getInstance("SHA-256").also {it.update(archive.header.bytes());it.update(provenance);it.update(hash(records))}.digest()
            rootTransaction {
                root.openHelper.writableDatabase.query("SELECT archive_sha256,state,generation_uuid,dataset_uuid,epoch_uuid FROM library_candidate WHERE operation_uuid=?",arrayOf(operation.toString())).use {c->
                    if(c.moveToFirst()) {
                        require(MessageDigest.isEqual(c.getBlob(0),digest)) {"Conflicting restore UUID payload"}
                        check(c.getString(1)=="ACTIVE" && LibraryAuthority.identity(root.openHelper.writableDatabase)==c.getString(2)) {"Interrupted or superseded restore cannot be resumed"}
                        return@rootTransaction Committed(operation,UUID.fromString(c.getString(2)),UUID.fromString(c.getString(3)),UUID.fromString(c.getString(4)))
                    }
                    null
                }
            }?.let {return it}
            val ns=AttachmentRestorePreflight.Namespace(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID())
            val slots=LibraryRestoreObjects.fresh(manifest);val aliasPlan=LibraryRestoreObjects.encode(slots);owner.ownBytes(aliasPlan)
            val targets=manifest.records.map {AttachmentBackupManifest.Target(it.uuid,it.kind,if(it.state=="PURGED")null else it.sourceId)}
            val rows=manifest.records.filter {it.state!="PURGED"}.map {AttachmentRestorePreflight.TargetRow(it.uuid,it.kind,it.sourceId,it.state)}
            // MED-03: independent files are valid saved content. The archive boundary (MedicalArchiveManifest) already refuses a v1 manifest
            // with an unlinked document and any LAB_PANEL / independent file outside v2; nothing is invented for them here.
            val plan=rootTransaction {
                val raw=root.openHelper.writableDatabase
                raw.execSQL("PRAGMA defer_foreign_keys=ON")
                LibraryAuthority.requireNoPending(raw)
                LibraryCollectionSchema.requireNoWrites(raw)
                val available=policy.restrict(LibraryRestoreAdmission.inspect(context,raw,::checkCurrent))
                val retained=raw.query("SELECT COALESCE(SUM(length(manifest)+length(structured_source)),0) FROM library_candidate").use {it.moveToFirst();it.getLong(0)}
                val reserved=LibraryRestoreAdmission.admit(available,manifest.versions,records.size,provenance.size,retained,policy.minimumFreeBytes)
                val forbidden=hashSetOf(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch),UUID.fromString(scope.logicalVault))
                raw.query("SELECT generation_uuid,dataset_uuid,epoch_uuid FROM library_candidate").use {c->while(c.moveToNext())repeat(3) {forbidden+=UUID.fromString(c.getString(it))}}
                val p=AttachmentRestorePreflight.inspect(manifest,records.copyOf(),archive.members(),ns,targets,rows,forbidden,
                    AttachmentRestorePreflight.Resources(available.free,available.unit,available.aliases-1,available.aliases-1),
                    AttachmentRestorePreflight.Limits(maximumCiphertextBytes=available.ciphertext),::checkCurrent)
                raw.query("SELECT 1 FROM library_authority LIMIT 1").use {if(!it.moveToFirst())raw.execSQL("INSERT INTO library_authority VALUES(1,NULL,0)")}
                val values=ContentValues().apply {
                    put("operation_uuid",operation.toString());put("generation_uuid",ns.generation.toString());put("dataset_uuid",ns.dataset.toString());put("epoch_uuid",ns.epoch.toString());put("state","PREPARED")
                    put("archive_sha256",digest);put("manifest",provenance);put("structured_sha256",hash(records));put("structured_source",records);put("alias_plan",aliasPlan)
                    put("database_alias","notes.gen.db.${ns.generation}");put("reserved_aliases",slots.size*2L+1);put("reserved_ciphertext",reserved);put("created_utc",System.currentTimeMillis())
                }
                check(raw.insert("library_candidate",SQLiteDatabase.CONFLICT_ABORT,values)!=-1L)
                raw.execSQL("INSERT INTO library_resource_owner VALUES(?,?,?,?,?,0,0,NULL)",arrayOf(operation.toString(),ns.generation.toString(),base,scope.dataset,scope.epoch));p
            }
            reservedByThisCall=true
            checkpoint(Boundary.PREPARED);checkCurrent()
            // Durable irreversible marker BEFORE the first directory/key/database primitive.
            rootTransaction {root.openHelper.writableDatabase.execSQL("UPDATE library_resource_owner SET started=1 WHERE operation_uuid=? AND released=0",arrayOf(operation.toString()))}
            LibraryRestoreCandidate.materialize(context,ns,plan,slots,records,archive,owner,::checkCurrent,{owned->
                rootTransaction {root.openHelper.writableDatabase.execSQL("UPDATE library_candidate SET directory_device=?,directory_inode=? WHERE operation_uuid=? AND state='PREPARED'",arrayOf(owned.device,owned.inode,operation.toString()))}
            },checkpoint)
            checkpoint(Boundary.REOPEN_VERIFIED);checkCurrent()
            rootTransaction {
                val raw=root.openHelper.writableDatabase
                val free=android.system.Os.statvfs(context.noBackupFilesDir.path)
                require(Math.multiplyExact(free.f_bavail,free.f_frsize)>=policy.minimumFreeBytes) {"Storage headroom lost"}
                raw.compileStatement("UPDATE library_candidate SET state='READY' WHERE operation_uuid=? AND state='PREPARED'").use {
                    it.bindString(1,operation.toString());check(it.executeUpdateDelete()==1) {"Restore reservation changed"}
                }
            }
            checkpoint(Boundary.READY);checkCurrent()
            rootTransaction(retiring=true) {
                val raw=root.openHelper.writableDatabase
                check(DatabaseAccess.handleIdentity(access.get(Space.PRIMARY))==base)
                val previous=LibraryAuthority.current(raw)?.generation?.toString()
                // The one visibility decision and candidate states share ONE native root COMMIT.
                raw.execSQL("UPDATE library_authority SET active_generation=?,revision=revision+1 WHERE singleton=1",arrayOf(ns.generation.toString()))
                if(previous!=null)raw.execSQL("UPDATE library_candidate SET state='RETIRED' WHERE generation_uuid=? AND state='ACTIVE'",arrayOf(previous))
                raw.execSQL("UPDATE library_candidate SET state='ACTIVE' WHERE operation_uuid=? AND state='READY'",arrayOf(operation.toString()))
                check(LibraryAuthority.identity(raw)==ns.generation.toString())
                checkpoint(Boundary.IN_SWITCH)
            }
            checkpoint(Boundary.SWITCHED)
            return Committed(operation,ns.generation,ns.dataset,ns.epoch)
        } catch(failure:Throwable) {
            if(reservedByThisCall)try {abort(scope,cap,operation)}catch(cleanup:Throwable) {failure.addSuppressed(cleanup)}
            throw failure
        } finally {
            owner.close()
        }
    }
    data class OperationState(val phase:String,val generation:UUID,val currentlyActive:Boolean)
    /** Reconcile a lost reply after restart without a password/KDF, new aliases or another restore. */
    fun state(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,operation:UUID):OperationState? {
        check(Looper.myLooper()!=Looper.getMainLooper())
        val current=access.get(Space.PRIMARY);val root=access.legacyRoot()
        check(!current.openHelper.writableDatabase.inTransaction())
        return DatabaseAccess.withActiveHandle(current) {
            cap.publishFor(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch)) {
                check(AttachmentIdentity.current(current.openHelper.writableDatabase)==scope)
                root.openHelper.writableDatabase.query("SELECT state,generation_uuid FROM library_candidate WHERE operation_uuid=?",arrayOf(operation.toString())).use {c->
                    if(!c.moveToFirst())null else OperationState(c.getString(0),UUID.fromString(c.getString(1)),LibraryAuthority.identity(root.openHelper.writableDatabase)==c.getString(1))
                }
            }
        }
    }
    /** Authorized logical abort only: retains every encrypted object/key/reservation. No resume,
     * auto-adoption, budget refund or permanent deletion. A committed request cannot be cancelled. */
    fun abort(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,operation:UUID):Boolean=
        access.holdGeneration().use {abortHeld(scope,cap,operation)}
    private fun abortHeld(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,operation:UUID):Boolean {
        check(Looper.myLooper()!=Looper.getMainLooper())
        val current=access.get(Space.PRIMARY);val root=access.legacyRoot()
        check(!root.openHelper.writableDatabase.inTransaction());root.openHelper.writableDatabase.execSQL("PRAGMA synchronous=FULL")
        root.beginTransaction()
        try {
            return cap.publishFor(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch)) {
                DatabaseAccess.assertActiveHandle(current)
                check(AttachmentIdentity.current(current.openHelper.writableDatabase)==scope)
                val raw=root.openHelper.writableDatabase
                raw.query("SELECT 1 FROM library_collection_member WHERE operation_uuid=? LIMIT 1",arrayOf(operation.toString())).use {check(!it.moveToFirst()) {"Use collection-level reconciliation"}}
                val phase=raw.query("SELECT state FROM library_candidate WHERE operation_uuid=?",arrayOf(operation.toString())).use {check(it.moveToFirst());it.getString(0)}
                val aborted=phase in setOf("PREPARED","READY","ABORTED")
                if(phase in setOf("PREPARED","READY"))raw.execSQL("UPDATE library_candidate SET state='ABORTED' WHERE operation_uuid=?",arrayOf(operation.toString()))
                root.setTransactionSuccessful();root.endTransaction();aborted
            }
        } finally {if(root.isOpen && root.openHelper.writableDatabase.inTransaction())root.endTransaction()}
    }
}
