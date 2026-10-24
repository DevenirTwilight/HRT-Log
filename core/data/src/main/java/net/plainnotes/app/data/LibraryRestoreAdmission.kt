package net.plainnotes.app.data

import android.content.Context
import android.system.Os
import android.system.OsConstants.*
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.nio.file.Files
import java.security.KeyStore
import java.util.UUID

/** Combined, conservative admission. Old generations and interrupted reservations never disappear
 * from accounting. A free-space observation is not a promise against external ENOSPC/LMK. */
internal object LibraryRestoreAdmission {
    const val DATABASE_BYTES=64L*1024*1024
    const val METADATA_BYTES=32L*1024*1024
    private const val CIPHER_BYTES=256L*1024*1024
    private const val ALIASES=512
    data class Available(val aliases:Int,val ciphertext:Long,val free:Long,val unit:Long) {
        init {require(aliases in 0..512 && ciphertext in 0..CIPHER_BYTES && free>=0 && unit>0)}
    }
    fun inspect(context:Context,root:SupportSQLiteDatabase,checkActive:()->Unit):Available {
        check(root.inTransaction());checkActive()
        val budget=AttachmentStorageBudget()
        var charged=0L;var unresolvedSpace=0L;val aliases=HashSet<String>()
        AttachmentOwnedFiles(context).locked().use {d->
            val usage=AttachmentStorageAccounting.inspect(root,d,AttachmentDeviceKeys(),budget,checkActive)
            require(usage.complete && usage.missingKnownObjects==0L)
            charged=Math.addExact(charged,usage.chargedBytes)
        }
        // A writer admitted before this root transaction can still finish its old reservation.
        root.query("SELECT o.reserved_bytes,j.stage_alias_uuid,j.active_alias_uuid,j.proof FROM attachment_journal j JOIN attachment_operation o USING(store_uuid,operation_uuid)").use {c->while(c.moveToNext()) {
            checkActive();aliases+=AttachmentDeviceKeys.Alias(UUID.fromString(c.getString(1)),AttachmentDeviceKeys.Role.STAGING).value
            aliases+=AttachmentDeviceKeys.Alias(UUID.fromString(c.getString(2)),AttachmentDeviceKeys.Role.ACTIVE).value
            if(c.getString(3)!="COMMITTED") {
                charged=Math.addExact(charged,c.getLong(0));unresolvedSpace=Math.addExact(unresolvedSpace,c.getLong(0))
            }
        }}
        AttachmentOwnedFiles(context,AttachmentOwnedFiles.Namespace.RESTORE_QUARANTINE).locked().use {d->
            val i=d.inventory(4096,checkActive);require(i.entries.all {it.privateRegular})
            charged=Math.addExact(charged,i.entries.fold(i.overheadBytes) {n,e->Math.addExact(n,e.chargedBytes)})
        }
        val known=HashSet<String>();var retainedMetadata=0L
        root.query("SELECT generation_uuid,database_alias,alias_plan,reserved_ciphertext,length(manifest),length(structured_source),state,operation_uuid FROM library_candidate").use {c->while(c.moveToNext()) {
            checkActive();val gen=UUID.fromString(c.getString(0));known+=gen.toString()
            require(c.getString(1)=="notes.gen.db.$gen")
            val slots=LibraryRestoreObjects.decode(c.getBlob(2))
            retainedMetadata=Math.addExact(retainedMetadata,Math.addExact(c.getLong(4),c.getLong(5)));require(retainedMetadata<=METADATA_BYTES)
            if(LibraryLifecycleSchema.released(root,c.getString(7))) {
                require(c.getString(6)=="ABORTED")
                val path=File(LibraryGenerationContext.directory(context),gen.toString())
                require(!Files.exists(path.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS)) {"Released namespace reappeared"}
                val ks=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
                val names=slots.flatMap {s->listOf(AttachmentDeviceKeys.Alias(s.staging,AttachmentDeviceKeys.Role.STAGING).value,AttachmentDeviceKeys.Alias(s.active,AttachmentDeviceKeys.Role.ACTIVE).value)}+c.getString(1)
                require(names.none {ks.containsAlias(it)}) {"Released key identity reappeared"}
                continue // Only never-materialized future allowances; metadata/identities stay retained.
            }
            aliases+=c.getString(1)
            slots.forEach {s->aliases+=AttachmentDeviceKeys.Alias(s.staging,AttachmentDeviceKeys.Role.STAGING).value;aliases+=AttachmentDeviceKeys.Alias(s.active,AttachmentDeviceKeys.Role.ACTIVE).value}
            var actual=0L;var databaseAllocation=0L
            val directory=File(LibraryGenerationContext.directory(context),gen.toString())
            if(Files.exists(directory.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                // Never adopt/open unknown generations. Only lstat a bounded, known namespace.
                val stat=Os.lstat(directory.path);require(S_ISDIR(stat.st_mode) && stat.st_uid==Os.getuid() && stat.st_mode and 63==0)
                Files.newDirectoryStream(directory.toPath()).use {entries->for(e in entries) {
                    val name=e.fileName.toString();val s=Os.lstat(e.toString())
                    if(name=="attachment-objects-v1") {
                        require(S_ISDIR(s.st_mode) && s.st_uid==Os.getuid() && s.st_mode and 63==0)
                        var count=0
                        Files.newDirectoryStream(e).use {objects->for(o in objects) {
                            checkActive();require(++count<=4097);val v=Os.lstat(o.toString());privateFile(v)
                            actual=Math.addExact(actual,maxOf(v.st_size,Math.multiplyExact(v.st_blocks,512)))
                        }}
                        actual=Math.addExact(actual,maxOf(s.st_size,Math.multiplyExact(s.st_blocks,512)))
                    } else {require(name in setOf("notes.db","notes.db-wal","notes.db-shm","notes.db-journal","key.wrap","key.wrap.bak"));privateFile(s);databaseAllocation=Math.addExact(databaseAllocation,maxOf(s.st_size,Math.multiplyExact(s.st_blocks,512)))}
                }}
            }
            charged=Math.addExact(charged,maxOf(c.getLong(3),actual))
            unresolvedSpace=Math.addExact(unresolvedSpace,maxOf(0L,c.getLong(3)-actual))
            // ABORTED does not prove that an old process has finished its current IO primitive.
            // Without an approved owner-release protocol, retain its bounded DB/WAL allowance.
            if(c.getString(6)!in setOf("ACTIVE","RETIRED"))unresolvedSpace=Math.addExact(unresolvedSpace,maxOf(0L,DATABASE_BYTES*3-databaseAllocation))
        }}
        val directory=LibraryGenerationContext.directory(context)
        if(Files.exists(directory.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            val stat=Os.lstat(directory.path);require(S_ISDIR(stat.st_mode) && stat.st_uid==Os.getuid() && stat.st_mode and 63==0)
            Files.newDirectoryStream(directory.toPath()).use {entries->for(e in entries)require(e.fileName.toString() in known) {"Unknown generation retained; admission refused"}}
        }
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)};val names=store.aliases()
        while(names.hasMoreElements()) {checkActive();val a=names.nextElement();if(a.startsWith("notes.obj.v1.") || a.startsWith("notes.gen.db."))aliases+=a;require(aliases.size<=ALIASES)}
        val fs=Os.statvfs(context.noBackupFilesDir.path);require(fs.f_bavail>=0 && fs.f_frsize>0)
        require(charged<=CIPHER_BYTES)
        return Available(ALIASES-aliases.size,CIPHER_BYTES-charged,maxOf(0L,Math.multiplyExact(fs.f_bavail,fs.f_frsize)-unresolvedSpace),fs.f_frsize)
    }
    fun admit(a:Available,objects:List<AttachmentBackupManifest.Version>,json:Int,manifest:Int,retainedMetadata:Long,minimumFreeBytes:Long=32L*1024*1024):Long {
        require(minimumFreeBytes>=32L*1024*1024)
        require(json in 0..BackupLimits.MAX_JSON_BYTES && manifest in 0..MedicalArchiveCrypto.MAX_MANIFEST && retainedMetadata>=0)
        val count=Math.addExact(Math.multiplyExact(objects.size,2),1)
        require(count<=a.aliases) {"Joint local alias budget exhausted"}
        require(Math.addExact(retainedMetadata,Math.addExact(json.toLong(),manifest.toLong()))<=METADATA_BYTES) {"Retained restore metadata budget exhausted"}
        var cipher=Math.multiplyExact(2,a.unit)
        objects.forEach {v->cipher=Math.addExact(cipher,Math.multiplyExact(Math.addExact(v.manifest.ciphertextBytes,a.unit-1)/a.unit,a.unit))}
        require(cipher<=a.ciphertext) {"Joint retained ciphertext budget exhausted"}
        // One bounded candidate DB plus worst-case WAL/rollback, root journal/blob growth and floor.
        val needed=Math.addExact(cipher,Math.addExact(DATABASE_BYTES*3,Math.addExact((json.toLong()+manifest)*3,minimumFreeBytes)))
        require(needed<=a.free) {"Insufficient combined restore storage"}
        return cipher
    }
    fun memory(json:Int,manifest:Int) {
        val vm=Runtime.getRuntime();val remaining=vm.maxMemory()-(vm.totalMemory()-vm.freeMemory())
        val needed=Math.addExact(16L*1024*1024,Math.addExact(json.toLong()*4,manifest.toLong()*4))
        require(remaining>=needed) {"Insufficient bounded reconstruction heap"}
    }
    private fun privateFile(s:android.system.StructStat) {require(S_ISREG(s.st_mode) && s.st_uid==Os.getuid() && s.st_nlink==1L && s.st_mode and 63==0 && s.st_size>=0 && s.st_blocks>=0)}
}
