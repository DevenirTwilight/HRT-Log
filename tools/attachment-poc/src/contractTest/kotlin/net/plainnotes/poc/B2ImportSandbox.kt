package net.plainnotes.poc

import net.plainnotes.app.domain.AttachmentSession
import java.io.*
import java.nio.channels.FileChannel
import java.nio.channels.Channels
import java.nio.file.*
import java.security.SecureRandom
import java.util.UUID

/** TEST_ONLY real ciphertext IO + explicitly in-memory DB/CAS model, NOT a Room repository.
 * No SAF URI persistence, production key wrapper, clinical records, full backup or READY rows.
 * Directory fsync is a platform seam, default MODEL ONLY. Process death persistence is NOT proven.
 */
object B2ImportSandbox {
    enum class Seam { WRITE, FILE_SYNC, AUTHENTICATE, DIRECTORY_SYNC, BEFORE_COMMIT, AFTER_COMMIT }
    enum class Phase { PREPARED, WRITING, AUTHENTICATED, COMMITTED, ABORTED, CLEANUP_PENDING }
    class Database(var revision:Long=0,var active:File?=null) {
        @Synchronized fun cas(expected:Long,file:File){check(revision==expected);active=file;revision++}
    }
    class Operation(val id:UUID=UUID.randomUUID(),val attempt:UUID=UUID.randomUUID(),val version:UUID=UUID.randomUUID()) {
        var phase=Phase.PREPARED
        val key=ByteArray(32).also(SecureRandom()::nextBytes)
        var file:File?=null
    }
    fun import(root:File,source:InputStream,capability:AttachmentSession.Capability,db:Database,
               operation:Operation=Operation(),expected:Long=db.revision,
               seam:(Seam)->Unit={},syncDirectory:()->Unit={},cleanup:(File)->Boolean={it.delete()}):Operation {
        check(operation.phase==Phase.PREPARED)
        check(root.isDirectory&&!Files.isSymbolicLink(root.toPath()))
        val owned=File(root,"${operation.id}-${operation.attempt}.cipher")
        val aad=("B2-TEST-ONLY/"+operation.version+"/"+operation.attempt).toByteArray()
        val bytes=ByteArray(8192)
        var created=false;var committed=false
        try {
            capability.check()
            FileChannel.open(owned.toPath(),StandardOpenOption.WRITE,StandardOpenOption.CREATE_NEW,LinkOption.NOFOLLOW_LINKS).use { channel ->
                created=true;operation.file=owned;operation.phase=Phase.WRITING
                val raw=Channels.newOutputStream(channel)
                // Encryptor close must finish its tag but cannot close the pinned file before fsync.
                val nonClosing=object:FilterOutputStream(raw){override fun close(){flush()}}
                B2StreamingCandidate.primitive(operation.key).newEncryptingStream(nonClosing,aad).use { encrypted ->
                    var total=0L;var stalled=0
                    while(true) {
                        capability.check();val n=source.read(bytes)
                        if(n<0)break
                        if(n==0){if(++stalled>8)throw IOException("Provider stalled");continue}
                        stalled=0;total+=n;if(total>B2StreamingCandidate.MAX_BYTES)throw IOException("Candidate quota")
                        encrypted.write(bytes,0,n);bytes.fill(0);seam(Seam.WRITE)
                    }
                }
                seam(Seam.FILE_SYNC);channel.force(true)
            }
            seam(Seam.AUTHENTICATE)
            B2StreamingCandidate.openAuthenticated(owned,operation.key,aad,capability).use { }
            operation.phase=Phase.AUTHENTICATED
            seam(Seam.DIRECTORY_SYNC);syncDirectory()
            seam(Seam.BEFORE_COMMIT)
            capability.publish {db.cas(expected,owned);operation.phase=Phase.COMMITTED;committed=true}
            seam(Seam.AFTER_COMMIT)
            return operation
        } catch(e:Throwable) {
            if(!committed){operation.phase=if(!created||cleanup(owned))Phase.ABORTED else Phase.CLEANUP_PENDING}
            throw e
        } finally {bytes.fill(0);operation.key.fill(0)}
    }
    enum class Reconcile { KEEP_ACTIVE, QUARANTINE_COMPLETE_ORPHAN, CLEANUP_OWNED_INCOMPLETE, FOREIGN_KEEP }
    fun classify(file:File,db:Database,operation:Operation?,authenticated:Boolean):Reconcile = when {
        file==db.active -> Reconcile.KEEP_ACTIVE
        operation?.file!=file -> Reconcile.FOREIGN_KEEP
        authenticated -> Reconcile.QUARANTINE_COMPLETE_ORPHAN
        else -> Reconcile.CLEANUP_OWNED_INCOMPLETE
    }
}
