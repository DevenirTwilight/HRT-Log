package net.plainnotes.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import net.plainnotes.app.domain.AttachmentSession
import java.security.MessageDigest
import java.util.UUID

/** OFF v2 coordinator. All members reserve together and use the existing per-generation engine.
 * One encrypted root COMMIT publishes the selected member, retained members and collection state.
 * No current file/handle/key is replaced or removed. Interrupted candidates are never adopted. */
internal class LibraryCollectionRestore(private val access:DatabaseAccess,
    private val policy:LibraryRestorePolicy=LibraryRestorePolicy(),
    private val checkpoint:(LibraryRestoreCoordinator.Boundary)->Unit={}) {
    data class Committed(val operation:UUID,val activeGeneration:UUID,val libraries:Int,val requiresNewSession:Boolean=true)
    private data class Work(val source:MedicalCollectionIndex.Library,val operation:UUID,
        val namespace:AttachmentRestorePreflight.Namespace,val slots:List<LibraryRestoreObjects.Slot>,
        val records:ByteArray,val provenance:ByteArray,var plan:AttachmentRestorePreflight.Plan?=null,var reserved:Long=0)
    data class State(val phase:String,val generation:UUID,val currentlyActive:Boolean)
    /** Lost-response reconciliation never decrypts an archive, allocates keys or repeats writes. */
    fun state(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,operation:UUID):State?=
        LibraryLifecycle(access).rootWork(scope,cap) {_,root,_->
            val raw=root.openHelper.writableDatabase
            raw.query("SELECT state,selected_generation FROM library_collection WHERE operation_uuid=?",arrayOf(operation.toString())).use {c->
                if(!c.moveToFirst())null else State(c.getString(0),UUID.fromString(c.getString(1)),LibraryAuthority.identity(raw)==c.getString(1))
            }
        }
    /** Logical group abort retains all materialized resources and allowances. No IO, refund,
     * automatic adoption or deletion. A committed collection cannot be cancelled. */
    fun abort(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,operation:UUID):Boolean=
        LibraryLifecycle(access).rootWork(scope,cap) {_,root,_->
            val raw=root.openHelper.writableDatabase
            val phase=raw.query("SELECT state,origin_dataset,origin_epoch FROM library_collection WHERE operation_uuid=?",arrayOf(operation.toString())).use {c->
                check(c.moveToFirst());check(c.getString(1)==scope.dataset && c.getString(2)==scope.epoch);c.getString(0)
            }
            if(phase=="COMMITTED")false else {
                raw.execSQL("UPDATE library_candidate SET state='ABORTED' WHERE operation_uuid IN(SELECT operation_uuid FROM library_collection_member WHERE collection_uuid=?) AND state IN('PREPARED','READY')",arrayOf(operation.toString()))
                raw.execSQL("UPDATE library_collection SET state='ABORTED' WHERE operation_uuid=? AND state IN('PREPARED','READY')",arrayOf(operation.toString()));true
            }
        }
    fun restore(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,operation:UUID,
        archive:MedicalCollectionReader.Archive):Committed=access.holdGeneration().use {
        check(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())
        val current=access.get(Space.PRIMARY);val root=access.legacyRoot();val raw=root.openHelper.writableDatabase
        check(!raw.inTransaction() && !current.openHelper.writableDatabase.inTransaction());raw.execSQL("PRAGMA synchronous=FULL")
        val owner=cap.fork();val base=DatabaseAccess.handleIdentity(current);var reserved=false
        fun active() {
            owner.check();DatabaseAccess.assertActiveHandle(current)
            check(AttachmentIdentity.current(current.openHelper.writableDatabase)==scope)
            if(reserved) {
                val count=raw.query("SELECT state,members FROM library_collection WHERE operation_uuid=?",arrayOf(operation.toString())).use {check(it.moveToFirst() && it.getString(0) in setOf("PREPARED","READY"));it.getInt(1)}
                raw.query("SELECT c.state,o.released FROM library_collection_member m JOIN library_candidate c USING(operation_uuid) JOIN library_resource_owner o USING(operation_uuid) WHERE m.collection_uuid=?",arrayOf(operation.toString())).use {c->
                    check(c.count==count);while(c.moveToNext())check(c.getString(0) in setOf("PREPARED","READY") && c.getInt(1)==0) {"Collection member aborted or released"}
                }
            }
        }
        fun <T> transaction(retiring:Boolean=false,block:()->T):T {
            root.beginTransaction()
            try {
                active();val result=block()
                return owner.publishFor(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch)) {
                    if(!retiring)active()
                    root.setTransactionSuccessful();root.endTransaction();result
                }
            }finally {if(raw.inTransaction())root.endTransaction()}
        }
        try {
            active();require(archive.wholeArchiveAuthenticated)
            val index=archive.index()
            val works=index.libraries.map {l->
                val records=archive.records(l.id) {it.copyOf().also(owner::ownBytes)}
                val provenance=archive.provenance(l.id).also(owner::ownBytes)
                Work(l,UUID.randomUUID(),AttachmentRestorePreflight.Namespace(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()),
                    LibraryRestoreObjects.fresh(archive.manifest(l.id)),records,provenance)
            }
            LibraryRestoreAdmission.memory(works.sumOf {it.records.size},works.sumOf {it.provenance.size})
            val digest=MessageDigest.getInstance("SHA-256").apply {
                update(archive.header.bytes());update(index.encode())
                works.forEach {update(MessageDigest.getInstance("SHA-256").digest(it.records));update(MessageDigest.getInstance("SHA-256").digest(it.provenance))}
            }.digest()
            transaction {
                raw.query("SELECT request_sha256,state,selected_generation,members FROM library_collection WHERE operation_uuid=?",arrayOf(operation.toString())).use {c->
                    if(!c.moveToFirst())null else {
                        require(MessageDigest.isEqual(c.getBlob(0),digest)) {"Conflicting collection UUID"}
                        check(c.getString(1)=="COMMITTED") {"Interrupted collection requires reconciliation; never resume"}
                        Committed(operation,UUID.fromString(c.getString(2)),c.getInt(3))
                    }
                }
            }?.let {return@use it}
            val selected=works.single {it.source.id==index.active}.namespace.generation
            transaction {
                LibraryAuthority.requireNoPending(raw);LibraryCollectionSchema.requireNoWrites(raw)
                val lifecycle=LibraryLifecycle(access)
                val inventory=lifecycle.persist(root,current,scope,operation,lifecycle.fresh(root,current,::active),collection=true)
                check(inventory.reason=="FULL_SNAPSHOT_AUTHENTICATION_REQUIRED") {"Retained set not proven: ${inventory.reason}"}
                val existing=inventory.items.count {it.category in setOf("CURRENT_BUSINESS","RETAINED_BUSINESS")}
                require(existing+works.size<=8) {"Retaining existing libraries would exceed eight"}
                val available=policy.restrict(LibraryRestoreAdmission.inspect(access.applicationContext,raw,::active))
                val metadata=raw.query("SELECT COALESCE(SUM(length(manifest)+length(structured_source)),0) FROM library_candidate").use {it.moveToFirst();it.getLong(0)}
                require(metadata+works.sumOf {it.records.size.toLong()+it.provenance.size}<=LibraryRestoreAdmission.METADATA_BYTES)
                require(works.sumOf {it.slots.size*2+1}<=available.aliases)
                var cipher=0L
                for(w in works) {
                    w.reserved=w.source.members.fold(Math.multiplyExact(2,available.unit)) {n,m->
                        Math.addExact(n,Math.multiplyExact((m.bytes+available.unit-1)/available.unit,available.unit))}
                    cipher=Math.addExact(cipher,w.reserved)
                }
                require(cipher<=available.ciphertext)
                val needed=Math.addExact(cipher,Math.addExact(LibraryRestoreAdmission.DATABASE_BYTES*3*works.size,
                    Math.addExact(works.sumOf {it.records.size.toLong()+it.provenance.size}*3,policy.minimumFreeBytes)))
                require(needed<=available.free) {"Insufficient joint restore space"}
                val forbidden=hashSetOf(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch),UUID.fromString(scope.logicalVault))
                raw.query("SELECT generation_uuid,dataset_uuid,epoch_uuid FROM library_candidate").use {c->while(c.moveToNext())repeat(3) {forbidden+=UUID.fromString(c.getString(it))}}
                index.libraries.forEach {forbidden+=it.dataset;forbidden+=it.epoch;forbidden+=it.logicalVault}
                works.forEach {w->
                    val m=archive.manifest(w.source.id)
                    require(m.documents.all {d->m.links.any {it.document==d.uuid}}) {"Unlinked document provenance unavailable"}
                    w.plan=AttachmentRestorePreflight.inspect(m,w.records.copyOf(),archive.source(w.source.id).members(),w.namespace,
                        m.records.map {AttachmentBackupManifest.Target(it.uuid,it.kind,if(it.state=="PURGED")null else it.sourceId)},
                        m.records.filter {it.state!="PURGED"}.map {AttachmentRestorePreflight.TargetRow(it.uuid,it.kind,it.sourceId,it.state)},forbidden,
                        AttachmentRestorePreflight.Resources(available.free,available.unit,available.aliases-works.size,available.aliases-works.size),
                        AttachmentRestorePreflight.Limits(maximumCiphertextBytes=available.ciphertext),::active)
                    forbidden+=w.namespace.dataset;forbidden+=w.namespace.epoch;forbidden+=w.namespace.generation
                }
                raw.query("SELECT 1 FROM library_authority LIMIT 1").use {if(!it.moveToFirst())raw.execSQL("INSERT INTO library_authority VALUES(1,NULL,0)")}
                raw.execSQL("INSERT INTO library_collection VALUES(?,?,?,?,?,?,?,'PREPARED',?)",arrayOf(operation.toString(),digest,base,scope.dataset,scope.epoch,selected.toString(),works.size,System.currentTimeMillis()))
                for(w in works) {
                    val values=ContentValues().apply {
                        put("operation_uuid",w.operation.toString());put("generation_uuid",w.namespace.generation.toString());put("dataset_uuid",w.namespace.dataset.toString());put("epoch_uuid",w.namespace.epoch.toString());put("state","PREPARED")
                        put("archive_sha256",digest);put("manifest",w.provenance);put("structured_sha256",MessageDigest.getInstance("SHA-256").digest(w.records));put("structured_source",w.records);put("alias_plan",LibraryRestoreObjects.encode(w.slots))
                        put("database_alias","notes.gen.db.${w.namespace.generation}");put("reserved_aliases",w.slots.size*2L+1);put("reserved_ciphertext",w.reserved);put("created_utc",System.currentTimeMillis())
                    }
                    check(raw.insert("library_candidate",SQLiteDatabase.CONFLICT_ABORT,values)!=-1L)
                    raw.execSQL("INSERT INTO library_resource_owner VALUES(?,?,?,?,?,0,0,NULL)",arrayOf(w.operation.toString(),w.namespace.generation.toString(),base,scope.dataset,scope.epoch))
                    raw.execSQL("INSERT INTO library_collection_member VALUES(?,?,?,?)",arrayOf(operation.toString(),w.source.id.toString(),w.operation.toString(),w.source.role.name))
                }
            }
            reserved=true;checkpoint(LibraryRestoreCoordinator.Boundary.PREPARED);active()
            for(w in works) {
                transaction {raw.execSQL("UPDATE library_resource_owner SET started=1 WHERE operation_uuid=? AND released=0",arrayOf(w.operation.toString()))}
                LibraryRestoreCandidate.materialize(access.applicationContext,w.namespace,requireNotNull(w.plan),w.slots,w.records,archive.source(w.source.id),owner,::active,{directory->
                    transaction {raw.execSQL("UPDATE library_candidate SET directory_device=?,directory_inode=? WHERE operation_uuid=? AND state='PREPARED'",arrayOf(directory.device,directory.inode,w.operation.toString()))}
                },checkpoint)
                checkpoint(LibraryRestoreCoordinator.Boundary.REOPEN_VERIFIED)
                transaction {raw.execSQL("UPDATE library_candidate SET state='READY' WHERE operation_uuid=? AND state='PREPARED'",arrayOf(w.operation.toString()))}
            }
            transaction {
                val fs=android.system.Os.statvfs(access.applicationContext.noBackupFilesDir.path)
                require(Math.multiplyExact(fs.f_bavail,fs.f_frsize)>=policy.minimumFreeBytes)
                raw.execSQL("UPDATE library_collection SET state='READY' WHERE operation_uuid=? AND state='PREPARED'",arrayOf(operation.toString()))
            }
            checkpoint(LibraryRestoreCoordinator.Boundary.READY)
            transaction(retiring=true) {
                val previous=LibraryAuthority.current(raw)?.generation?.toString()
                raw.execSQL("UPDATE library_authority SET active_generation=?,revision=revision+1 WHERE singleton=1",arrayOf(selected.toString()))
                if(previous!=null)raw.execSQL("UPDATE library_candidate SET state='RETIRED' WHERE generation_uuid=? AND state='ACTIVE'",arrayOf(previous))
                for(w in works) {
                    raw.execSQL("UPDATE library_candidate SET state='ACTIVE' WHERE operation_uuid=? AND state='READY'",arrayOf(w.operation.toString()))
                    if(w.source.role==MedicalCollectionIndex.Role.RETAINED)raw.execSQL("UPDATE library_candidate SET state='RETIRED' WHERE operation_uuid=? AND state='ACTIVE'",arrayOf(w.operation.toString()))
                }
                raw.execSQL("UPDATE library_collection SET state='COMMITTED' WHERE operation_uuid=? AND state='READY'",arrayOf(operation.toString()))
                checkpoint(LibraryRestoreCoordinator.Boundary.IN_SWITCH)
            }
            checkpoint(LibraryRestoreCoordinator.Boundary.SWITCHED)
            Committed(operation,selected,works.size)
        }catch(failure:Throwable) {
            if(reserved)try {abort(scope,cap,operation)}catch(cleanup:Throwable) {failure.addSuppressed(cleanup)}
            throw failure
        }finally {owner.close()}
    }
}
