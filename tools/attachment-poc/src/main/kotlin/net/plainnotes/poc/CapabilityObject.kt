package net.plainnotes.poc

import net.plainnotes.app.domain.AttachmentSession
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.UUID

/** B1 synthetic security adapter. SYNPOC01 remains a test format. No production file APIs. */
object CapabilityObject {
    class Session(val authority: AttachmentSession = AttachmentSession(UUID.randomUUID(), UUID.randomUUID())) {
        private val checkCapability = authority.issue()
        fun revoke() = authority.revoke()
        fun check(): Boolean = try { checkCapability.check(); true } catch (_: IllegalStateException) { false }
    }
    class Owned internal constructor(private val raw: ChunkObject.Owned,
                                     private val capability: AttachmentSession.Capability,
                                     private val ownsRaw: Boolean = true) : Closeable {
        fun <T> publish(block: () -> T): T = capability.publish { raw.whileOpen(block) }
        fun open(): Reader {
            val child=capability.fork()
            val key=try { raw.syntheticKey() } catch(e:Throwable) {child.close();throw e}
            // Full authentication does not hold the owner or authorization monitor.
            val reader=try { ChunkObject.Reader(raw.file,key,raw.identity) }
                catch(e:Throwable) {child.close();throw e} finally {key.fill(0)}
            try { return child.publish { raw.whileOpen { Reader(reader, child, raw) } } }
            catch (e: Throwable) { child.close();reader.close(); throw e }
        }
        override fun close() { capability.close(); if(ownsRaw)raw.close() }
    }
    class Reader internal constructor(private val raw: ChunkObject.Reader,
                                      private val capability: AttachmentSession.Capability,
                                      private val owner: ChunkObject.Owned) : Closeable {
        val size: Long get()=capability.publish {owner.whileOpen {raw.size}}
        val blockReads get()=raw.blockReads
        val maxScratchBytes get()=raw.maxScratchBytes
        private var closed = false
        @Synchronized fun readAt(offset: Long, destination: ByteArray, requested: Int = destination.size, beforeDelivery: () -> Unit = {}): Int {
            check(!closed); require(requested in 0..destination.size)
            // Bounded scratch; decrypt into owned memory, never caller memory before rechecking authority.
            require(requested <= 1024 * 1024)
            val scratch = ByteArray(requested)
            try {
                capability.check()
                val count = raw.readAt(offset, scratch, requested)
                beforeDelivery()
                return capability.publish { owner.whileOpen { scratch.copyInto(destination, 0, 0, count); count } }
            } catch (e: Throwable) { destination.fill(0, 0, requested); throw e }
            finally { scratch.fill(0) }
        }
        override fun close() {
            capability.close() // Revoke first; any in-flight decrypt cannot deliver its result.
            synchronized(this) {closed=true;raw.close()}
        }
    }
    /** Preview borrows a legacy fixture; closing the preview does not delete/close its original. */
    fun adopt(raw: ChunkObject.Owned, session: Session): Owned = Owned(raw, session.authority.issue(), false)
    fun write(root: File, source: InputStream, session: Session, beforeCommit: () -> Unit = {}): Owned {
        val capability = session.authority.issue()
        // Ciphertext creation is unreferenced staging, not a valid business publication.
        val raw = ChunkObject.write(root, ChunkObject.Identity.fresh(), source, active = session::check)
        try {
            beforeCommit()
            return capability.publish { Owned(raw, capability) }
        } catch (e: Throwable) { capability.close(); raw.close(); check(raw.file.delete()); throw e }
    }
}
