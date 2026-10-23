package net.plainnotes.app.data

import android.os.Looper
import net.plainnotes.app.domain.AttachmentSession
import java.util.UUID

/** Crypto identity stays immutable, but permission to publish must follow the live committed dataset.
 * Do not cache this check or run inside an old caller transaction/snapshot. AppLock still revokes
 * the session; this additional boundary refuses old leases even if a caller forgets that handoff.
 */
internal class AttachmentScopedReader(private val db:NotesDatabase,private val scope:AttachmentIdentity.Scope,
    private val capability:AttachmentSession.Capability,private val reader:AttachmentObjectStreams.Reader):AutoCloseable {
    private fun <T> current(block:()->T):T = try {
        check(Looper.myLooper()!=Looper.getMainLooper()) {"Attachment reads require a worker thread"}
        check(db.isOpen) {"Attachment database closed"}
        val raw=db.openHelper.writableDatabase
        check(!raw.inTransaction()) {"Attachment read cannot use a stale caller transaction"}
        DatabaseAccess.withActiveHandle(db) {
            capability.publishFor(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch)) {
                check(AttachmentIdentity.current(raw)==scope) {"Attachment dataset changed"}
                block()
            }
        }
    } catch(failure:Throwable) {
        try {close()}catch(cleanup:Throwable) {failure.addSuppressed(cleanup)}
        throw failure
    }
    val size:Long get()=current {reader.size}
    fun readAt(position:Long,destination:ByteArray,offset:Int,length:Int):Int {
        if(offset<0 || length<0 || length>destination.size-offset)throw IndexOutOfBoundsException()
        try {
            current {Unit}
            val count=reader.readAt(position,destination,offset,length)
            return current {count}
        } catch(failure:Throwable) {
            destination.fill(0,offset,offset+length)
            try {close()} catch(cleanup:Throwable) {failure.addSuppressed(cleanup)}
            throw failure
        }
    }
    override fun close()=reader.close()
}
