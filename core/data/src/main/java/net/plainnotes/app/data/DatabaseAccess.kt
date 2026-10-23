package net.plainnotes.app.data

import android.content.Context
import android.os.UserManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.room.withTransaction
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class DataLockedException : IllegalStateException("Credential storage unavailable")
class KeyRecoveryRequired(cause: Throwable? = null) : IllegalStateException("Encrypted data requires recovery",cause)

/**
 * Encrypted HRT storage. DECOY is legacy storage retained for upgrade compatibility/explicit cleanup.
 * No authentication target or new UI routes to it; ordinary private notes use their own full-flavor store.
 */
enum class Space(val file:String,val wrap:String,val alias:String) {
    PRIMARY("notes.db","key.wrap","notes.wrap"), DECOY("notes_b.db","key_b.wrap","notes_b.wrap")
}

@Singleton open class DatabaseAccess internal constructor(private val context:Context,private val deviceNamespace:java.util.UUID?) {
    @Inject constructor(@ApplicationContext context:Context):this(context,null)
    private fun rootAlias(space:Space)=deviceNamespace?.let {"notes.local.$it.${space.alias}"} ?: space.alias
    internal val measuredSaveGate=MeasuredSaveGate()
    private val instances=mutableMapOf<Space,NotesDatabase>()
    private val passphrases=mutableMapOf<Space,ByteArray>()
    private data class GenerationConnection(val db:NotesDatabase,val context:LibraryGenerationContext,val key:ByteArray)
    private val generations=mutableMapOf<String,GenerationConnection>()
    /** The space the UI works on. Background reminders always use PRIMARY. Not persisted: a new process starts in PRIMARY. */
    @Volatile var current=Space.PRIMARY
        private set
    @Synchronized fun select(space:Space) {
        if(space==current)return
        // Switching the UI must not close a database still used by an in-flight transaction.
        current=space
    }
    internal val applicationContext: Context get() = context.applicationContext
    fun get(): NotesDatabase=get(current)
    @Synchronized open fun get(space:Space):NotesDatabase {
        val root=legacyRoot(space)
        if(space!=Space.PRIMARY)return root
        val rawRoot=root.openHelper.writableDatabase
        val selected=LibraryAuthority.current(rawRoot) ?: return root
        if(!rawRoot.inTransaction())rawRoot.execSQL("PRAGMA synchronous=FULL")
        else rawRoot.query("PRAGMA synchronous").use {check(it.moveToFirst() && it.getInt(0)>=2)}
        generations[selected.generation.toString()]?.let {connection->
            if(connection.db.isOpen) {connection.context.checkCurrent();return connection.db}
            connection.context.close();connection.key.fill(0);generations.remove(selected.generation.toString())
        }
        val owned=LibraryGenerationContext.open(context,selected.generation,false,selected.device to selected.inode)
        var key:ByteArray?=null;var database:NotesDatabase?=null
        try {
            check(owned.getDatabasePath("notes.db").isFile) {"Active generation database missing"}
            val password=LocalDatabaseKey(owned).open("notes.db","key.wrap",selected.alias,allowCreate=false).also {key=it}
            val db=Room.databaseBuilder(owned,NotesDatabase::class.java,"notes.db")
                .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .openHelperFactory(SupportOpenHelperFactory(password)).addMigrations(*allMigrations()).addCallback(SchemaGuards).build().also {database=it}
            val scope=AttachmentIdentity.current(db.openHelper.writableDatabase)
            check(scope!=null) {"Missing current database namespace"}
            // The immutable original restore identity must remain retained. Ordinary PNBAK1
            // replacement may legitimately retire it and create a later CURRENT attachment scope.
            db.openHelper.writableDatabase.query("SELECT 1 FROM attachment_store WHERE store_uuid=? AND record_epoch=?",arrayOf(selected.dataset,selected.epoch)).use {check(it.moveToFirst()) {"Active database origin mismatch"}}
            owned.checkCurrent()
            generations[selected.generation.toString()]=GenerationConnection(db,owned,password)
            register(db,root,selected.generation.toString(),owned)
            return db
        } catch(e:Throwable) {try {database?.close()}finally {key?.fill(0);owned.close()};throw e}
    }
    @Synchronized internal fun legacyRoot(space:Space=Space.PRIMARY):NotesDatabase {
        if(!context.getSystemService(UserManager::class.java).isUserUnlocked) throw DataLockedException()
        instances[space]?.let { if (it.isOpen) return it else close(space) }
        val passphrase=loadPassphrase(space)
        try {
            System.loadLibrary("sqlcipher")
            val db=Room.databaseBuilder(context,NotesDatabase::class.java,space.file)
                .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(*allMigrations()).addCallback(SchemaGuards).build()
            try {
                val sql=db.openHelper.writableDatabase
                sql.beginTransaction()
                try { AttachmentIdentity.initialize(sql,space==Space.PRIMARY);sql.setTransactionSuccessful() }
                finally { sql.endTransaction() }
            } catch(e:Exception) { db.close(); throw KeyRecoveryRequired(e) }
            // SQLCipher 4.10 retains this array for every WAL connection. Clearing it here
            // makes later concurrent reads fail to decrypt the same database.
            instances[space]=db;passphrases[space]=passphrase
            if(space==Space.PRIMARY)register(db,db,"LEGACY",context)
            return db
        } catch(e:Throwable) { passphrase.fill(0); throw e }
    }
    /** One root native transaction serializes the final authority switch with the ENTIRE
     * selected database transaction (including its commit). No untrusted callback selects a path. */
    internal suspend fun <T> withGenerationTransaction(space:Space,checkBinding:(NotesDatabase)->Unit,
        action:suspend(NotesDatabase)->T):T {
        val selected=get(space)
        val root=synchronized(this) {instances[space]} ?: selected // overridden in-memory test access
        return root.withTransaction {
            val current=get(space)
            if(space==Space.PRIMARY)LibraryAuthority.requireNoPending(root.openHelper.writableDatabase)
            checkBinding(current)
            if(current===root) action(current) else current.withTransaction {action(current)}
        }
    }
    companion object {
        private data class Handle(val root:NotesDatabase,val identity:String,val context:Context)
        private val handles=java.util.WeakHashMap<NotesDatabase,Handle>()
        private fun register(db:NotesDatabase,root:NotesDatabase,identity:String,context:Context)=synchronized(handles) {handles[db]=Handle(root,identity,context);Unit}
        /** Called inside the capability publication monitor; the native root transaction covers
         * publication and excludes the authority's final COMMIT, including across processes. */
        internal fun <T> withActiveHandle(db:NotesDatabase,block:()->T):T {
            val handle=synchronized(handles) {handles[db]} ?: return block() // private test/candidate DB
            check(handle.root.isOpen && db.isOpen)
            return handle.root.runInTransaction(java.util.concurrent.Callable {
                check(LibraryAuthority.identity(handle.root.openHelper.writableDatabase)==handle.identity) {"Retired database handle"}
                block()
            })
        }
        internal fun storageContext(db:NotesDatabase,fallback:Context):Context=synchronized(handles) {handles[db]?.context ?: fallback}
        internal fun managedGeneration(db:NotesDatabase)=synchronized(handles) {handles[db]?.identity?.let {it!="LEGACY"} ?: false}
        internal fun assertActiveHandle(db:NotesDatabase) {
            val h=synchronized(handles) {handles[db]} ?: return
            check(h.root.isOpen && db.isOpen)
            check(LibraryAuthority.identity(h.root.openHelper.writableDatabase)==h.identity) {"Retired database handle"}
        }
        /** Acquire SQLite writer locks BEFORE the session monitor; otherwise a snapshot that
         * owns a DB transaction and needs to transfer its buffer could deadlock with a writer.
         * Both actual child COMMIT and the root gate release remain under publication authority. */
        internal fun <T> commitActive(db:NotesDatabase,scope:AttachmentIdentity.Scope,
            cap:net.plainnotes.app.domain.AttachmentSession.Capability,block:()->T):T {
            val h=synchronized(handles) {handles[db]};val root=h?.root ?: db
            check(!db.openHelper.writableDatabase.inTransaction() && !root.openHelper.writableDatabase.inTransaction()) {"Own native transactions required"}
            root.beginTransaction()
            try {
                if(db!==root)db.beginTransaction()
                try {
                    return cap.publishFor(java.util.UUID.fromString(scope.dataset),java.util.UUID.fromString(scope.epoch)) {
                        if(h!=null) {
                            check(LibraryAuthority.identity(root.openHelper.writableDatabase)==h.identity)
                            LibraryAuthority.requireNoPending(root.openHelper.writableDatabase)
                        }
                        check(AttachmentIdentity.current(db.openHelper.writableDatabase)==scope)
                        val result=block()
                        if(db!==root) {db.setTransactionSuccessful();db.endTransaction()}
                        root.setTransactionSuccessful();root.endTransaction()
                        result
                    }
                } finally {if(db!==root && db.isOpen && db.openHelper.writableDatabase.inTransaction())db.endTransaction()}
            } finally {if(root.isOpen && root.openHelper.writableDatabase.inTransaction())root.endTransaction()}
        }
        /** Additional ordinary attachment allocations cannot escape the combined-generation budget.
         * Until that writer is integrated, refuse new imports; exact committed replays/read remain. */
        internal fun requireUnpartitionedStorage(db:NotesDatabase) {
            val handle=synchronized(handles) {handles[db]} ?: return
            handle.root.openHelper.writableDatabase.query("SELECT 1 FROM library_candidate LIMIT 1").use {
                check(!it.moveToFirst()) {"Additional attachment writes need joint generation admission"}
            }
        }
        internal fun handleIdentity(db:NotesDatabase):String {
            synchronized(handles) {handles[db]?.let {return it.identity}}

            val path=db.openHelper.writableDatabase.path
            return if(path.isNullOrEmpty() || path==":memory:") "memory:${System.identityHashCode(db)}" else path
        }
    }
    @Synchronized fun close(space:Space=current) {
        measuredSaveGate.synchronousMutation(space) {
            try {
                if(space==Space.PRIMARY) {
                    generations.values.forEach {connection->try {connection.db.close()}finally {connection.key.fill(0);connection.context.close()}}
                    generations.clear()
                }
                val root=instances.remove(space)
                if(root!=null)synchronized(handles) {handles.entries.removeAll {it.value.root===root}}
                root?.close()
                passphrases.remove(space)?.fill(0)
            } finally {
                MeasuredOperationChain.clearPureInputMemo()
            }
        }
    }
    /** Closes and deletes one space's database, wrapped key and Keystore alias ("delete all data"). */
    @Synchronized fun destroy(space:Space=current) {
        measuredSaveGate.synchronousMutation(space) {
            if(space==Space.PRIMARY) {
                val root=legacyRoot(space).openHelper.writableDatabase
                root.query("SELECT 1 FROM library_candidate LIMIT 1").use {check(!it.moveToFirst()) {"Retained generations require an approved deletion policy"}}
            }
            close(space)
            listOf("","-wal","-shm","-journal").forEach { context.getDatabasePath(space.file+it).delete() }
            File(context.noBackupFilesDir,space.wrap).delete(); File(context.noBackupFilesDir,space.wrap+".bak").delete()
            runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(rootAlias(space)) }
        }
    }
    private fun loadPassphrase(space:Space)=LocalDatabaseKey(context).open(space.file,space.wrap,rootAlias(space),freshAlias=deviceNamespace!=null)
}
