package net.plainnotes.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.plainnotes.app.domain.AttachmentSession
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Application bridge only: existing authenticated codecs and sole root-COMMIT coordinators.
 * It owns no alternate authority, key, format or durable state. Caller must register the capability
 * with its RuntimeLibrarySession and discard the old screen after a committed activation. */
class MedicalLibraryTransfer internal constructor(private val access:DatabaseAccess,private val expected:LibraryRequestScope,private val permission:()->Unit) {
    data class Summary(val format:Int,val libraries:Int,val versions:Int)
    data class Committed(val operation:UUID,val generation:UUID,val libraries:Int)
    data class Status(val phase:String,val generation:UUID,val currentlyActive:Boolean) {
        val committed get()=phase in setOf("COMMITTED","ACTIVE","RETIRED")
    }
    private fun scope():AttachmentIdentity.Scope {
        permission()
        val db=access.get(Space.PRIMARY)
        return DatabaseAccess.withActiveHandle(db) {
            val live=requireNotNull(AttachmentIdentity.current(db.openHelper.writableDatabase))
            if(DatabaseAccess.handleIdentity(db)!=expected.generation || live.dataset!=expected.dataset || live.epoch!=expected.epoch)throw LibraryScopeMismatch()
            live
        }
    }
    suspend fun export(cap:AttachmentSession.Capability,password:CharArray,output:OutputStream)=try {
        withContext(Dispatchers.IO) {
            scope();cap.check()
            MedicalAttachmentOperations(access,expected,permission).writeCollection(cap,password,output)
        }
    }finally {password.fill('\u0000')}

    /** Full authentication is read-only. Takes channel ownership on every path; never plaintext disk. */
    suspend fun prepare(cap:AttachmentSession.Capability,password:CharArray,source:SeekableByteChannel,operation:UUID=UUID.randomUUID()):Prepared=try {
        withContext(Dispatchers.IO) {
            scope();cap.check()
            source.position(0);val head=ByteBuffer.allocate(8)
            while(head.hasRemaining())check(source.read(head)>0) {"Truncated medical archive"}
            source.position(0)
            val format=String(head.array(),Charsets.US_ASCII)
            var first:MedicalArchiveReader.Archive?=null
            var second:MedicalCollectionReader.Archive?=null
            try {
                when(format) {"PNMEDBK1"->first=MedicalArchiveReader.read(cap,password,source);"PNMEDBK2"->second=MedicalCollectionReader.read(cap,password,source);else->error("Unsupported medical archive")}
                scope();cap.check()
                val summary=first?.let {Summary(1,1,it.manifest().versions.size)} ?: requireNotNull(second).index().let {Summary(2,it.libraries.size,it.libraries.sumOf {l->l.members.size})}
                Prepared(cap,operation,summary,first,second)
            }catch(t:Throwable) {runCatching {first?.close()};runCatching {second?.close()};throw t}
        }
    }catch(t:Throwable) {runCatching {source.close()};throw t}finally {password.fill('\u0000')}

    inner class Prepared internal constructor(private val cap:AttachmentSession.Capability,val operation:UUID,val summary:Summary,
        private val first:MedicalArchiveReader.Archive?,private val second:MedicalCollectionReader.Archive?):AutoCloseable {
        private val started=AtomicBoolean(false)
        private val closed=AtomicBoolean(false)
        @Volatile var committedReceipt:Committed?=null
            private set
        /** Exactly one write attempt. Lost response is reconciled, never a second call to restore. */
        suspend fun restore():Committed=withContext(Dispatchers.IO) {
            check(!closed.get() && started.compareAndSet(false,true)) {"Restore already attempted or closed"}
            val origin=scope();cap.check()
            val receipt=if(first!=null)LibraryRestoreCoordinator(access).restore(origin,cap,operation,first).let {Committed(it.operation,it.generation,1)}
                else LibraryCollectionRestore(access).restore(origin,cap,operation,requireNotNull(second)).let {Committed(it.operation,it.activeGeneration,it.libraries)}
            // No refresh/current-scope check after the authority COMMIT: this receipt is a fact.
            committedReceipt=receipt
            receipt
        }
        override fun close() {if(closed.compareAndSet(false,true))try {first?.close()}finally {second?.close()}}
    }
    /** Read-only journal lookup, usable with a fresh authorized scope after restart/switch. */
    suspend fun status(cap:AttachmentSession.Capability,operation:UUID):Status?=withContext(Dispatchers.IO) {
        val current=scope();cap.check()
        LibraryCollectionRestore(access).state(current,cap,operation)?.let {Status(it.phase,it.generation,it.currentlyActive)}
            ?: LibraryRestoreCoordinator(access).state(current,cap,operation)?.let {Status(it.phase,it.generation,it.currentlyActive)}
    }
    /** Explicit cancellation of a VERIFIED pending journal, not UNKNOWN recovery or destruction.
     * Original coordinators retain every object/key/reservation; no refund or automatic retry. */
    suspend fun abortPending(cap:AttachmentSession.Capability,operation:UUID):Boolean=withContext(Dispatchers.IO) {
        val current=scope();cap.check()
        val group=LibraryCollectionRestore(access).state(current,cap,operation)
        if(group!=null) {
            check(group.phase in setOf("PREPARED","READY")) {"Not a pending collection"}
            LibraryCollectionRestore(access).abort(current,cap,operation)
        }else {
            val single=requireNotNull(LibraryRestoreCoordinator(access).state(current,cap,operation))
            check(single.phase in setOf("PREPARED","DATABASE_COMMITTED","OBJECTS_COMMITTED","READY")) {"Not a pending restore"}
            LibraryRestoreCoordinator(access).abort(current,cap,operation)
        }
    }
}
