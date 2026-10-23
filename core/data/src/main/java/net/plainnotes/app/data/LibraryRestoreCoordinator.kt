package net.plainnotes.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import androidx.room.Room
import net.plainnotes.app.domain.AttachmentSession
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Callable

/** Internal OFF full-library restore. Never overwrite/delete the original root or adopt an
 * interrupted candidate. The ONLY visibility commit is library_authority in the encrypted root. */
internal class LibraryRestoreCoordinator(private val access:DatabaseAccess,
    private val checkpoint:(Boundary)->Unit={}) {
    enum class Boundary { PREPARED, DATABASE_COMMITTED, OBJECTS_COMMITTED, REOPEN_VERIFIED, READY, IN_SWITCH, SWITCHED }
    data class Committed(val operation:UUID,val generation:UUID,val dataset:UUID,val epoch:UUID,
        val requiresNewSession:Boolean=true)
    private val context get()=access.applicationContext
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
    fun restore(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,operation:UUID,
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
        var candidateDb:NotesDatabase?=null;var directory:LibraryGenerationContext?=null;var password:ByteArray?=null
        try {
            checkCurrent();require(archive.wholeArchiveAuthenticated)
            val manifest=archive.manifest()
            val provenance=archive.retainedManifest {it.copyOf()};owner.ownBytes(provenance)
            val records=archive.records {LibraryRestoreAdmission.memory(it.size,provenance.size);it.copyOf()};owner.ownBytes(records)
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
            // Explicitly refuse unsupported provenance instead of inventing a record for an orphan.
            require(manifest.documents.all {d->manifest.links.any {it.document==d.uuid}})
            val plan=rootTransaction {
                val raw=root.openHelper.writableDatabase
                raw.execSQL("PRAGMA defer_foreign_keys=ON")
                LibraryAuthority.requireNoPending(raw)
                val available=LibraryRestoreAdmission.inspect(context,raw,::checkCurrent)
                val retained=raw.query("SELECT COALESCE(SUM(length(manifest)+length(structured_source)),0) FROM library_candidate").use {it.moveToFirst();it.getLong(0)}
                val reserved=LibraryRestoreAdmission.admit(available,manifest.versions,records.size,provenance.size,retained)
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
                check(raw.insert("library_candidate",SQLiteDatabase.CONFLICT_ABORT,values)!=-1L);p
            }
            reservedByThisCall=true
            checkpoint(Boundary.PREPARED);checkCurrent()
            val owned=LibraryGenerationContext.open(context,ns.generation,true).also {directory=it}
            val key=LocalDatabaseKey(owned).open("notes.db","key.wrap",owned.databaseAlias,freshAlias=true).also {password=it}
            val db=open(owned,key).also {candidateDb=it}
            rootTransaction {root.openHelper.writableDatabase.execSQL("UPDATE library_candidate SET directory_device=?,directory_inode=? WHERE operation_uuid=? AND state='PREPARED'",arrayOf(owned.device,owned.inode,operation.toString()))}
            BackupJsonReader.indexed(records,::checkCurrent).use {source->db.runInTransaction {
                RawData.reconstructGeneration(db.openHelper.writableDatabase,source.tables,owned,::checkCurrent)
                AttachmentCandidateMetadata.compareBusiness(db.openHelper.writableDatabase,source.tables,::checkCurrent)
            }}
            checkpoint(Boundary.DATABASE_COMMITTED);checkCurrent()
            val targetSession=AttachmentSession(ns.dataset,ns.epoch)
            val sessionLease=owner.own(AutoCloseable {targetSession.revoke()})
            try {LibraryRestoreObjects(owned,db,plan,targetSession.issue(),slots,::checkCurrent).install(archive)}finally {sessionLease.close()}
            checkpoint(Boundary.OBJECTS_COMMITTED);checkCurrent()
            db.close();candidateDb=null;key.fill(0);password=null;owned.sync();owned.close();directory=null
            // Reopen with the actual persisted local envelope and fresh connections, not old RAM.
            val reopened=LibraryGenerationContext.open(context,ns.generation,false,owned.device to owned.inode).also {directory=it}
            val persistedKey=LocalDatabaseKey(reopened).open("notes.db","key.wrap",reopened.databaseAlias,allowCreate=false).also {password=it}
            val verified=open(reopened,persistedKey).also {candidateDb=it}
            verify(reopened,verified,plan,records,owner,::checkCurrent)
            verified.close();candidateDb=null;persistedKey.fill(0);password=null;reopened.sync();reopened.close();directory=null
            checkpoint(Boundary.REOPEN_VERIFIED);checkCurrent()
            rootTransaction {
                val raw=root.openHelper.writableDatabase
                val free=android.system.Os.statvfs(context.noBackupFilesDir.path)
                require(Math.multiplyExact(free.f_bavail,free.f_frsize)>=32L*1024*1024) {"Storage headroom lost"}
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
            try {candidateDb?.close()}finally {password?.fill(0);try {directory?.close()}finally {owner.close()}}
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
    fun abort(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,operation:UUID):Boolean {
        check(Looper.myLooper()!=Looper.getMainLooper())
        val current=access.get(Space.PRIMARY);val root=access.legacyRoot()
        check(!root.openHelper.writableDatabase.inTransaction());root.openHelper.writableDatabase.execSQL("PRAGMA synchronous=FULL")
        root.beginTransaction()
        try {
            return cap.publishFor(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch)) {
                DatabaseAccess.assertActiveHandle(current)
                check(AttachmentIdentity.current(current.openHelper.writableDatabase)==scope)
                val raw=root.openHelper.writableDatabase
                val phase=raw.query("SELECT state FROM library_candidate WHERE operation_uuid=?",arrayOf(operation.toString())).use {check(it.moveToFirst());it.getString(0)}
                val aborted=phase in setOf("PREPARED","READY","ABORTED")
                if(phase in setOf("PREPARED","READY"))raw.execSQL("UPDATE library_candidate SET state='ABORTED' WHERE operation_uuid=?",arrayOf(operation.toString()))
                root.setTransactionSuccessful();root.endTransaction();aborted
            }
        } finally {if(root.isOpen && root.openHelper.writableDatabase.inTransaction())root.endTransaction()}
    }
    private fun open(context:LibraryGenerationContext,password:ByteArray):NotesDatabase {
        val db=Room.databaseBuilder(context,NotesDatabase::class.java,"notes.db")
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .openHelperFactory(SupportOpenHelperFactory(password)).addMigrations(*allMigrations()).addCallback(SchemaGuards).build()
        try {
            val raw=db.openHelper.writableDatabase
            raw.execSQL("PRAGMA synchronous=FULL");raw.execSQL("PRAGMA temp_store=MEMORY")
            raw.execSQL("PRAGMA cache_size=-2048");raw.execSQL("PRAGMA mmap_size=0")
            raw.query("PRAGMA page_size").use {check(it.moveToFirst());val pages=LibraryRestoreAdmission.DATABASE_BYTES/it.getLong(0);raw.query("PRAGMA max_page_count=$pages").use {c->check(c.moveToFirst() && c.getLong(0)<=pages)}}
            return db
        } catch(e:Throwable) {db.close();throw e}
    }
    private fun verify(context:LibraryGenerationContext,db:NotesDatabase,p:AttachmentRestorePreflight.Plan,
        records:ByteArray,parent:AttachmentSession.Capability,checkActive:()->Unit) {
        BackupJsonReader.indexed(records,checkActive).use {source->db.runInTransaction {
            MeasuredConsumptionStore.auditStagedDataset(db.openHelper.writableDatabase,0,0)
            AttachmentCandidateMetadata.compareBusiness(db.openHelper.writableDatabase,source.tables,checkActive)
        }}
        val session=AttachmentSession(p.namespace.dataset,p.namespace.epoch);val lease=parent.own(AutoCloseable {session.revoke()})
        try {
            val scope=AttachmentIdentity.Scope(p.namespace.dataset.toString(),p.manifest.logicalVault.toString(),p.namespace.epoch.toString())
            AttachmentBackupPreparation(context,db).capture(scope,session.issue()).use {snapshot->
                check(AttachmentCandidateMetadata.normalized(snapshot.manifest())==AttachmentCandidateMetadata.normalized(p.manifest.copy(dataset=p.namespace.dataset,epoch=p.namespace.epoch))) {"Persisted attachment facts changed"}
            }
        } finally {lease.close()}
        checkActive();context.checkCurrent()
    }
}
