package net.plainnotes.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import android.system.OsConstants
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import net.plainnotes.app.domain.AttachmentSession
import net.plainnotes.poc.B2StreamingCandidate
import java.io.*
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.*
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.concurrent.Callable

/** TEST APK ONLY. Real Room10/SQLCipher plus separate synthetic protocol tables.
 * Production guards are installed unchanged. No production VERIFIED/VALIDATED or attachment entry.
 * Device aliases belong ONLY to the standalone library test package and this synthetic fixture.
 */
internal class Gate2Store(val context:Context,val fixture:String="synthetic-gate2-${UUID.randomUUID()}"):Closeable {
    val root:File
    private val databaseName="$fixture.db"
    val db:NotesDatabase
    val raw get()=db.openHelper.writableDatabase
    val scope:AttachmentIdentity.Scope
    val session:AttachmentSession
    private val stageAlias="$fixture.staging"
    private val activeAlias="$fixture.active"
    private val leased=mutableMapOf<String,Int>()
    init {
        check(fixture.startsWith("synthetic-gate2-")&&fixture.matches(Regex("[a-zA-Z0-9-]+")))
        check(context.packageName=="net.plainnotes.app.data.test"){"Standalone synthetic test APK only"}
        root=File(context.noBackupFilesDir,fixture).apply{check(mkdirs()||isDirectory)}
        System.loadLibrary("sqlcipher")
        val fresh=!context.getDatabasePath(databaseName).exists()
        db=Room.databaseBuilder(context,NotesDatabase::class.java,databaseName).allowMainThreadQueries()
            .openHelperFactory(SupportOpenHelperFactory(ByteArray(32){71})).addCallback(SchemaGuards).build()
        scope=db.runInTransaction(Callable {
            val s=AttachmentIdentity.initialize(raw,true)?:AttachmentIdentity.current(raw)!!
            raw.execSQL("CREATE TABLE IF NOT EXISTS synthetic_gate2_current(id INTEGER PRIMARY KEY CHECK(id=1), revision INTEGER NOT NULL, active TEXT, quota_bytes INTEGER NOT NULL DEFAULT 0)")
            raw.execSQL("INSERT OR IGNORE INTO synthetic_gate2_current(id,revision) VALUES(1,0)")
            raw.execSQL("CREATE TABLE IF NOT EXISTS synthetic_gate2_operation(id TEXT PRIMARY KEY, dataset TEXT NOT NULL, epoch TEXT NOT NULL, attempt TEXT NOT NULL UNIQUE, version TEXT NOT NULL UNIQUE, object_id TEXT NOT NULL UNIQUE, handle TEXT NOT NULL, physical TEXT NOT NULL, phase TEXT NOT NULL, expected INTEGER NOT NULL, reservation INTEGER NOT NULL, context BLOB NOT NULL, stage_wrap BLOB NOT NULL, active_wrap BLOB, plain_hash BLOB, cipher_hash BLOB, plain_bytes INTEGER, cipher_bytes INTEGER, owned_device INTEGER, owned_inode INTEGER, parent_device INTEGER, parent_inode INTEGER)")
            s
        })
        session=AttachmentSession(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch))
        // Key creation is an explicit fixture setup, never an unwrap fallback.
        if(fresh){createAlias(stageAlias);createAlias(activeAlias)}
        // Reopening an existing fixture never replaces missing key material. Unwrap fails closed.
        RandomAccessFile(context.getDatabasePath(databaseName),"r").use{val b=ByteArray(16);it.readFully(b);check(!String(b).startsWith("SQLite format 3"))}
    }
    private fun keys()=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
    private fun createAlias(alias:String) {
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    fun removeActiveAlias()=keys().deleteEntry(activeAlias)
    private fun wrappingKey(alias:String)=keys().getKey(alias,null) as? SecretKey?:throw IllegalStateException("Synthetic key unavailable")
    private fun wrap(key:ByteArray,alias:String,aad:ByteArray):ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.ENCRYPT_MODE,wrappingKey(alias));updateAAD(aad);iv+doFinal(key)
    }
    private fun unwrap(bytes:ByteArray,alias:String,aad:ByteArray):ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        require(bytes.size==60);init(Cipher.DECRYPT_MODE,wrappingKey(alias),GCMParameterSpec(128,bytes.copyOfRange(0,12)));updateAAD(aad);doFinal(bytes.copyOfRange(12,bytes.size))
    }
    /** Proposed context transcript, TEST_ONLY marker, no production protocol ID/magic. */
    private fun contextBytes(attempt:UUID,version:UUID,obj:UUID,handle:UUID):ByteArray=
        net.plainnotes.poc.Gate2Context.encode(UUID.fromString(scope.logicalVault),obj,version,attempt,handle)
    data class Operation(val id:String,val attempt:String,val version:String,val objectId:String,val physical:String,val context:ByteArray,val expected:Long)
    enum class Seam { AFTER_CREATE, WRITE, BEFORE_FILE_SYNC, AFTER_FILE_SYNC, AUTHENTICATED, BEFORE_DIR_SYNC, AFTER_DIR_SYNC, BEFORE_COMMIT, IN_COMMIT, AFTER_COMMIT }
    fun revision():Long=raw.query("SELECT revision FROM synthetic_gate2_current WHERE id=1").use{check(it.moveToFirst());it.getLong(0)}
    fun active():String?=raw.query("SELECT active FROM synthetic_gate2_current WHERE id=1").use{check(it.moveToFirst());if(it.isNull(0))null else it.getString(0)}
    fun phase(id:String):String=raw.query("SELECT phase FROM synthetic_gate2_operation WHERE id=?",arrayOf(id)).use{check(it.moveToFirst());it.getString(0)}
    fun prepare(idempotency:UUID=UUID.randomUUID(),reservation:Long=1024*1024):Operation = AttachmentTransaction.commit(db,scope,session.issue()) {
        require(reservation in 56..(17L*1024*1024))
        val id=AttachmentOperations.prepare(raw,scope,idempotency,"IMPORT",reservation,1)
        raw.query("SELECT attempt,version,object_id,physical,context,expected FROM synthetic_gate2_operation WHERE id=?",arrayOf(id)).use{c ->
            if(c.moveToFirst())return@commit Operation(id,c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getBlob(4),c.getLong(5))
        }
        val attempt=UUID.randomUUID();val version=UUID.randomUUID();val obj=UUID.randomUUID();val handle=UUID.randomUUID();val physical=UUID.randomUUID()
        val ctx=contextBytes(attempt,version,obj,handle);val key=ByteArray(32).also(SecureRandom()::nextBytes)
        val envelope=try{wrap(key,stageAlias,ctx+"/staging/$id/${scope.dataset}/$physical".toByteArray())}finally{key.fill(0)}
        val used=raw.query("SELECT COALESCE(SUM(reservation),0) FROM synthetic_gate2_operation WHERE phase NOT IN('ABORTED','CLEANED','RELEASED_FOREIGN')").use{check(it.moveToFirst());it.getLong(0)}
        check(used+reservation<=40L*1024*1024){"Synthetic quota reservation"}
        val expected=revision()
        raw.execSQL("INSERT INTO synthetic_gate2_operation(id,dataset,epoch,attempt,version,object_id,handle,physical,phase,expected,reservation,context,stage_wrap) VALUES(?,?,?,?,?,?,?,?,'PREPARED',?,?,?,?)",
            arrayOf(id,scope.dataset,scope.epoch,attempt.toString(),version.toString(),obj.toString(),handle.toString(),physical.toString(),expected,reservation,ctx,envelope))
        Operation(id,attempt.toString(),version.toString(),obj.toString(),physical.toString(),ctx,expected)
    }
    fun file(op:Operation)=File(root,"${op.physical}/${op.objectId}.cipher")
    private fun row(id:String)=raw.query("SELECT attempt,version,object_id,physical,context,expected FROM synthetic_gate2_operation WHERE id=?",arrayOf(id)).use {
        check(it.moveToFirst());Operation(id,it.getString(0),it.getString(1),it.getString(2),it.getString(3),it.getBlob(4),it.getLong(5))
    }
    private fun stageKey(op:Operation):ByteArray=raw.query("SELECT stage_wrap FROM synthetic_gate2_operation WHERE id=?",arrayOf(op.id)).use {
        check(it.moveToFirst());unwrap(it.getBlob(0),stageAlias,op.context+"/staging/${op.id}/${scope.dataset}/${op.physical}".toByteArray())
    }
    private fun update(op:Operation,phase:String)=db.runInTransaction {raw.execSQL("UPDATE synthetic_gate2_operation SET phase=? WHERE id=?",arrayOf(phase,op.id))}
    enum class LeaseState { FREE, HELD_THIS_PROCESS, HELD_OTHER_PROCESS }
    /** Live-writer lease: an OS advisory (fcntl) lock on a per-operation file, held for the whole import. The kernel
     * drops it when the holder dies, so a fresh process can recover a force-stopped writer. POSIX record locks belong
     * to the process: closing ANY descriptor of the file drops every lock this process holds on it. So this process
     * keeps exactly one channel per locked file and never opens a probe channel on a file it already locks.
     * An unsupported/failed lock call throws and is never treated as permission to proceed. */
    internal object Leases {
        private val held=HashMap<String,FileChannel>()
        /** RED CONTROL ONLY: restores the earlier probe that opened (and closed) a second channel. */
        @Volatile var unsafeLegacyProbeForRedTest=false
        private fun open(f:File)=FileChannel.open(f.toPath(),StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)
        @Synchronized fun acquire(f:File):Closeable? {
            val key=f.canonicalPath;if(key in held)return null
            val channel=open(f)
            val lock=try{channel.tryLock()}catch(t:Throwable){channel.close();throw t}
            if(lock==null){channel.close();return null}  // safe: this process holds no lock on this file
            held[key]=channel
            return Closeable{synchronized(Leases){if(held[key]===channel){held.remove(key);channel.close()}}}
        }
        @Synchronized fun state(f:File):LeaseState {
            val key=f.canonicalPath;if(key in held)return LeaseState.HELD_THIS_PROCESS
            val channel=open(f)
            try{val lock=channel.tryLock()?:return LeaseState.HELD_OTHER_PROCESS;lock.release();return LeaseState.FREE}finally{channel.close()}
        }
        fun legacySecondChannel(f:File):Closeable? {
            val channel=open(f)
            val lock=try{channel.tryLock()}catch(e:OverlappingFileLockException){null}catch(t:Throwable){channel.close();throw t}
            if(lock==null){channel.close();return null}
            return Closeable{channel.close()}
        }
    }
    private fun leaseFile(id:String)=File(root,"leases/$id.lock").also{check(it.parentFile!!.mkdirs()||it.parentFile!!.isDirectory)}
    /** Lease for maintenance (reconcile, confirmed release); the writer always uses the registry. */
    private fun maintenanceLease(id:String):Closeable?=
        if(Leases.unsafeLegacyProbeForRedTest)Leases.legacySecondChannel(leaseFile(id)) else Leases.acquire(leaseFile(id))
    fun leaseState(id:String):LeaseState=Leases.state(leaseFile(id))
    fun leaseFileOf(id:String)=leaseFile(id)
    /** TEST ONLY (cross-process lease evidence): records, as the live writer itself would, that [op] is WRITING with
     * the leaf a peer process created under its own lease. SQL stays in this single process; the peer never opens the
     * database, matching the single-process production app. */
    fun recordPeerWriting(op:Operation) {
        val leaf=Os.lstat(file(op).path);check(OsConstants.S_ISREG(leaf.st_mode))
        db.runInTransaction{raw.execSQL("UPDATE synthetic_gate2_operation SET phase='WRITING',owned_device=?,owned_inode=? WHERE id=? AND phase='PREPARED'",arrayOf(leaf.st_dev,leaf.st_ino,op.id))}
        check(phase(op.id)=="WRITING")
    }
    private fun <T> withLease(lease:Closeable?,block:()->T):T {
        lease?:throw IllegalStateException("Synthetic writer lease held")
        try{return block()}finally{lease.close()}
    }
    /** Test-only admission: declared MIME must match the signature and the display name must be a plain file name.
     * A signature is NOT parser validation; production VERIFIED/VALIDATED stay forbidden. */
    data class Admission(val mime:String,val displayName:String?)
    fun import(op:Operation,source:InputStream,capability:AttachmentSession.Capability=session.issue(),
               fault:(Seam)->Unit={},cleanup:(File)->Boolean={it.delete()},unsafeCleanupForRedTest:Boolean=false,admission:Admission?=null)=
        withLease(Leases.acquire(leaseFile(op.id))){importLeased(op,source,capability,fault,cleanup,unsafeCleanupForRedTest,admission)}
    private fun importLeased(op:Operation,source:InputStream,capability:AttachmentSession.Capability,
               fault:(Seam)->Unit,cleanup:(File)->Boolean,unsafeCleanupForRedTest:Boolean,admission:Admission?) {
        check(phase(op.id)=="PREPARED");val target=file(op)
        // Only a parent directory this operation created itself (empty when created) may later vouch for a leaf
        // whose inode was never recorded.
        val freshParent=target.parentFile!!.mkdirs();check(freshParent||target.parentFile!!.isDirectory)
        val scratch=ByteArray(8192);var created=false;val head=ByteArray(64);var headLength=0
        val dir=Os.open(target.parentFile!!.path,OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,0)
        val pinned=Os.fstat(dir)
        val key=try{stageKey(op)}catch(e:Throwable){Os.close(dir);throw e}
        try {
            capability.publishFor(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch)){Unit}
            admission?.let{net.plainnotes.poc.B2InputPolicy.displayName(it.displayName)}
            db.runInTransaction {raw.execSQL("UPDATE synthetic_gate2_operation SET phase='WRITING',parent_device=?,parent_inode=? WHERE id=?",
                arrayOf(if(freshParent)pinned.st_dev else null,if(freshParent)pinned.st_ino else null,op.id))}
            // Fixed durable reservation for this attempt; no native SQL round-trip per 8KiB chunk.
            val reserved=reservation(op.id);val primitive=B2StreamingCandidate.primitive(key)
            FileChannel.open(target.toPath(),StandardOpenOption.WRITE,StandardOpenOption.CREATE_NEW,LinkOption.NOFOLLOW_LINKS).use { fd ->
                created=true;fault(Seam.AFTER_CREATE);val ownedStat=Os.stat(target.path);raw.execSQL("UPDATE synthetic_gate2_operation SET owned_device=?,owned_inode=? WHERE id=?",arrayOf(ownedStat.st_dev,ownedStat.st_ino,op.id))
                val out=object:FilterOutputStream(Channels.newOutputStream(fd)){override fun close(){flush()}}
                primitive.newEncryptingStream(out,op.context).use { cipher ->
                    var total=0L;var zeros=0
                    while(true){capability.check();val n=source.read(scratch);if(n<0)break
                        if(n==0){check(++zeros<=8){"Synthetic provider stalled"};continue};zeros=0;total+=n
                        check(total<=B2StreamingCandidate.MAX_BYTES && primitive.expectedCiphertextSize(total)<=reserved){"Synthetic input limit"}
                        if(headLength<head.size){val c=minOf(n,head.size-headLength);System.arraycopy(scratch,0,head,headLength,c);headLength+=c}
                        cipher.write(scratch,0,n);scratch.fill(0);fault(Seam.WRITE)}
                }
                admission?.let{net.plainnotes.poc.B2InputPolicy.signature(head.copyOf(headLength),it.mime)}
                check(fd.size()<=reserved);fault(Seam.BEFORE_FILE_SYNC);fd.force(true);fault(Seam.AFTER_FILE_SYNC)
            }
            val evidence=B2StreamingCandidate.openAuthenticated(target,key,op.context,capability).use{it.evidence}
            db.runInTransaction {raw.execSQL("UPDATE synthetic_gate2_operation SET phase='AUTHENTICATED',plain_hash=?,cipher_hash=?,plain_bytes=?,cipher_bytes=? WHERE id=?",
                arrayOf(evidence.plainHash,evidence.cipherHash,evidence.length,target.length(),op.id))}
            fault(Seam.AUTHENTICATED);fault(Seam.BEFORE_DIR_SYNC)
            val current=Os.stat(target.parentFile!!.path);check(current.st_dev==pinned.st_dev&&current.st_ino==pinned.st_ino){"Synthetic parent replaced"}
            check(OsConstants.S_ISDIR(pinned.st_mode));Os.fsync(dir)
            val rootFd=Os.open(root.path,OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW,0);try{Os.fsync(rootFd)}finally{Os.close(rootFd)}
            fault(Seam.AFTER_DIR_SYNC);fault(Seam.BEFORE_COMMIT)
            val activeWrap=wrap(key,activeAlias,op.context+"/active/${scope.dataset}/1".toByteArray())
            AttachmentTransaction.commit(db,scope,capability) {
                check(phase(op.id)=="AUTHENTICATED");check(ownedFile(op)){"Synthetic owned leaf replaced before commit"}
                raw.execSQL("UPDATE synthetic_gate2_current SET active=?,revision=revision+1 WHERE id=1 AND revision=?",arrayOf(op.id,op.expected))
                val changed=raw.query("SELECT changes()").use{check(it.moveToFirst());it.getLong(0)};check(changed==1L){"Synthetic stale CAS"}
                raw.execSQL("UPDATE synthetic_gate2_operation SET phase='COMMITTED',active_wrap=? WHERE id=?",arrayOf(activeWrap,op.id))
                fault(Seam.IN_COMMIT)
            }
            fault(Seam.AFTER_COMMIT)
        } catch(e:Throwable) {
            // Durable SQL state is authoritative even when the post-commit acknowledgement was lost.
            if(unsafeCleanupForRedTest || phase(op.id)!="COMMITTED") {
                val sameParent=runCatching{Os.stat(target.parentFile!!.path).let{it.st_dev==pinned.st_dev&&it.st_ino==pinned.st_ino}}.getOrDefault(false)
                val sameFile=ownedForCleanup(op)
                val clean=!created||(sameParent&&sameFile&&cleanup(target));update(op,if(clean)"ABORTED" else "CLEANUP_PENDING")
            }
            throw e
        } finally {scratch.fill(0);head.fill(0);key.fill(0);Os.close(dir)}
    }
    private fun reservation(id:String)=raw.query("SELECT reservation FROM synthetic_gate2_operation WHERE id=?",arrayOf(id)).use{check(it.moveToFirst());it.getLong(0)}
    class TrackedLease(private val delegate:B2StreamingCandidate.Lease,private val release:Closeable):Closeable {
        fun size()=delegate.size()
        fun readAt(offset:Long,dst:java.nio.ByteBuffer,beforeDelivery:()->Unit={})=delegate.readAt(offset,dst,beforeDelivery)
        override fun close(){try{delegate.close()}finally{release.close()}}
    }
    fun open(id:String=active()!!):TrackedLease {
        check(AttachmentIdentity.current(raw)==scope);check(ownedInCurrent(id));check(active()==id&&phase(id)=="COMMITTED")
        val op=row(id)
        val values=raw.query("SELECT active_wrap,plain_hash,cipher_hash,plain_bytes FROM synthetic_gate2_operation WHERE id=?",arrayOf(id)).use {
            check(it.moveToFirst());Triple(it.getBlob(0),B2StreamingCandidate.Evidence(it.getLong(3),it.getBlob(1),it.getBlob(2)),Unit)
        }
        val key=unwrap(values.first,activeAlias,op.context+"/active/${scope.dataset}/1".toByteArray())
        try{val guard=borrow(id);try{return TrackedLease(B2StreamingCandidate.openAuthenticated(file(op),key,op.context,session.issue(),values.second),guard)}catch(t:Throwable){guard.close();throw t}}finally{key.fill(0)}
    }
    /** Reconcile never guesses READY from a directory scan. Active ciphertext is fully reauthenticated. */
    private fun ownedFile(op:Operation):Boolean=raw.query("SELECT owned_device,owned_inode FROM synthetic_gate2_operation WHERE id=?",arrayOf(op.id)).use{c->
        check(c.moveToFirst());if(c.isNull(0)||c.isNull(1))false else runCatching{Os.lstat(file(op).path).let{OsConstants.S_ISREG(it.st_mode)&&it.st_dev==c.getLong(0)&&it.st_ino==c.getLong(1)}}.getOrDefault(false)
    }
    /** Crash or failure right after CREATE_NEW, before the leaf inode was recorded: the leaf is ours only when it is a
     * regular file inside the parent this operation created and recorded. Does not resist same-UID/root races. */
    private fun unrecordedOwnedLeaf(op:Operation):Boolean=raw.query("SELECT owned_device,parent_device,parent_inode FROM synthetic_gate2_operation WHERE id=?",arrayOf(op.id)).use{c->
        check(c.moveToFirst());if(!c.isNull(0)||c.isNull(1)||c.isNull(2))false else runCatching{
            val parent=Os.lstat(file(op).parentFile!!.path);val leaf=Os.lstat(file(op).path)
            OsConstants.S_ISDIR(parent.st_mode)&&parent.st_dev==c.getLong(1)&&parent.st_ino==c.getLong(2)&&OsConstants.S_ISREG(leaf.st_mode)&&leaf.st_dev==parent.st_dev
        }.getOrDefault(false)
    }
    private fun ownedForCleanup(op:Operation)=ownedFile(op)||unrecordedOwnedLeaf(op)
    private fun ownedInCurrent(id:String):Boolean=raw.query("SELECT dataset,epoch FROM synthetic_gate2_operation WHERE id=?",arrayOf(id)).use{check(it.moveToFirst());it.getString(0)==scope.dataset&&it.getString(1)==scope.epoch}
    fun reconcile():Map<String,String> {
        val results=mutableMapOf<String,String>()
        val ids=raw.query("SELECT id FROM synthetic_gate2_operation").use{c->buildList{while(c.moveToNext())add(c.getString(0))}}
        for(id in ids){
            // Never classify or clean an operation whose writer is still alive (this process or another one).
            val lease=maintenanceLease(id);if(lease==null){results[id]="SKIP_LIVE_WRITER";continue}
            try{results[id]=classify(id)}finally{lease.close()}
        }
        return results
    }
    private fun classify(id:String):String {
        val op=row(id);val p=phase(id)
        if(p in TERMINAL)return "RETAIN_TERMINAL"
        if(AttachmentIdentity.current(raw)!=scope||!ownedInCurrent(id))return "QUARANTINED_EPOCH"
        if(id==active()&&p=="COMMITTED"){open(id).use{};return "KEEP_ACTIVE"}
        if(p=="AUTHENTICATED"||p=="COMMITTED")return "QUARANTINE_COMPLETE_ORPHAN"
        check(p in setOf("PREPARED","WRITING","CLEANUP_PENDING")){"Unknown synthetic phase"}
        val f=file(op);if(f.exists()&&!ownedForCleanup(op))return "QUARANTINE_FOREIGN_PATH"
        val clean=!f.exists()||f.delete();update(op,if(clean)"ABORTED" else "CLEANUP_PENDING")
        return if(clean)"CLEANED_OWNED_INCOMPLETE" else "CLEANUP_PENDING"
    }
    /** Explicit, confirmed release of a quarantined orphan (complete orphan, foreign path, failed cleanup, retired
     * epoch). Frees its quota reservation. Deletes ciphertext only when it is provably this operation's own file;
     * a foreign file is never deleted. The active pointer is never released here. */
    fun releaseQuarantined(id:String,confirmed:Boolean):String=withLease(maintenanceLease(id)) {
        check(confirmed){"Release requires explicit confirmation"}
        val op=row(id);val p=phase(id)
        val orphan=p=="AUTHENTICATED"||p=="CLEANUP_PENDING"||(!ownedInCurrent(id)&&p !in TERMINAL)
        check(id!=active()&&orphan&&(synchronized(leased){leased[id]?:0})==0){"Not a quarantined orphan"}
        val f=file(op);val own=ownedForCleanup(op)
        if(own)check(f.delete()){"Synthetic orphan delete failed"}
        val released=if(own||!f.exists())"CLEANED" else "RELEASED_FOREIGN"
        update(op,released);released
    }
    fun fileOf(id:String)=file(row(id))
    fun operationOf(id:String)=row(id)
    /** Reserved bytes still counted against the synthetic quota (same rule as prepare). */
    fun reservedBytes():Long=raw.query("SELECT COALESCE(SUM(reservation),0) FROM synthetic_gate2_operation WHERE phase NOT IN('ABORTED','CLEANED','RELEASED_FOREIGN')").use{check(it.moveToFirst());it.getLong(0)}
    private companion object {
        val TERMINAL=setOf("ABORTED","CLEANED","RELEASED_FOREIGN")
    }
    fun gc(id:String):Boolean=synchronized(leased) {
        check(AttachmentIdentity.current(raw)==scope&&ownedInCurrent(id));check(active()!=id);if((leased[id]?:0)>0)return false
        val op=row(id);check(phase(id)=="COMMITTED");check(ownedFile(op));check(file(op).delete());update(op,"CLEANED");true
    }
    fun borrow(id:String):Closeable=synchronized(leased){leased[id]=(leased[id]?:0)+1;var closed=false;Closeable{synchronized(leased){if(!closed){closed=true;leased[id]=leased.getValue(id)-1}}}}
    fun assertProductionOff(){raw.query("SELECT feature_enabled FROM attachment_store WHERE store_state='CURRENT'").use{check(it.moveToFirst());check(it.getLong(0)==0L)}
        raw.query("SELECT count(*) FROM attachment_version").use{check(it.moveToFirst());check(it.getLong(0)==0L)}}
    override fun close(){session.revoke();db.close()}
    fun destroy(){close();context.deleteDatabase(databaseName);check(!root.exists()||root.deleteRecursively());keys().deleteEntry(stageAlias);keys().deleteEntry(activeAlias)}
}
