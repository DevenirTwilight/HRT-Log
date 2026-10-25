package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import net.plainnotes.app.domain.AttachmentSession
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** Existing per-generation engine shared by v1 and v2. Never changes the routing authority. */
internal object LibraryRestoreCandidate {
    fun materialize(context:Context,ns:AttachmentRestorePreflight.Namespace,plan:AttachmentRestorePreflight.Plan,
        slots:List<LibraryRestoreObjects.Slot>,records:ByteArray,source:AttachmentRestoreStaging.CiphertextSource,
        owner:AttachmentSession.Capability,checkCurrent:()->Unit,onDirectory:(LibraryGenerationContext)->Unit,
        checkpoint:(LibraryRestoreCoordinator.Boundary)->Unit) {
        var candidateDb:NotesDatabase?=null;var directory:LibraryGenerationContext?=null;var password:ByteArray?=null
        try {
            val owned=LibraryGenerationContext.open(context,ns.generation,true).also {directory=it}
            val key=LocalDatabaseKey(owned).open("notes.db","key.wrap",owned.databaseAlias,freshAlias=true).also {password=it}
            val db=open(owned,key).also {candidateDb=it}
            onDirectory(owned)
            BackupJsonReader.indexed(records.copyOf(),checkCurrent).use {source->db.runInTransaction {
                RawData.reconstructGeneration(db.openHelper.writableDatabase,source.tables,owned,checkCurrent)
                AttachmentCandidateMetadata.compareBusiness(db.openHelper.writableDatabase,source.tables,checkCurrent)
            }}
            checkpoint(LibraryRestoreCoordinator.Boundary.DATABASE_COMMITTED);checkCurrent()
            val targetSession=AttachmentSession(ns.dataset,ns.epoch)
            val sessionLease=owner.own(AutoCloseable {targetSession.revoke()})
            try {LibraryRestoreObjects(owned,db,plan,targetSession.issue(),slots,checkCurrent).install(source)}finally {sessionLease.close()}
            checkpoint(LibraryRestoreCoordinator.Boundary.OBJECTS_COMMITTED);checkCurrent()
            db.close();candidateDb=null;key.fill(0);password=null;owned.sync();owned.close();directory=null
            // Reopen with the actual persisted local envelope and fresh connections, not old RAM.
            val reopened=LibraryGenerationContext.open(context,ns.generation,false,owned.device to owned.inode).also {directory=it}
            val persistedKey=LocalDatabaseKey(reopened).open("notes.db","key.wrap",reopened.databaseAlias,allowCreate=false).also {password=it}
            val verified=open(reopened,persistedKey).also {candidateDb=it}
            verify(reopened,verified,plan,records,owner,checkCurrent)
            verified.close();candidateDb=null;persistedKey.fill(0);password=null;reopened.sync();reopened.close();directory=null
        }finally {try {candidateDb?.close()}finally {password?.fill(0);directory?.close()}}
    }
    private fun open(context:LibraryGenerationContext,password:ByteArray):NotesDatabase {
        val db=Room.databaseBuilder(context,NotesDatabase::class.java,"notes.db")
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .openHelperFactory(SupportOpenHelperFactory(password)).addMigrations(*allMigrations()).addCallback(SchemaGuards).build()
        try {
            val raw=db.openHelper.writableDatabase
            raw.execSQL("PRAGMA synchronous=FULL");raw.execSQL("PRAGMA temp_store=MEMORY")
            raw.execSQL("PRAGMA cache_size=-2048");raw.query("PRAGMA mmap_size=0").use {check(it.moveToFirst() && it.getLong(0)==0L)}
            raw.query("PRAGMA page_size").use {check(it.moveToFirst());val pages=LibraryRestoreAdmission.DATABASE_BYTES/it.getLong(0);raw.query("PRAGMA max_page_count=$pages").use {c->check(c.moveToFirst() && c.getLong(0)<=pages)}}
            return db
        } catch(e:Throwable) {db.close();throw e}
    }
    private fun verify(context:LibraryGenerationContext,db:NotesDatabase,p:AttachmentRestorePreflight.Plan,
        records:ByteArray,parent:AttachmentSession.Capability,checkActive:()->Unit) {
        BackupJsonReader.indexed(records.copyOf(),checkActive).use {source->db.runInTransaction {
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
