package net.plainnotes.app.domain

import java.io.IOException
import java.io.InputStream

/** Bounded, cancellation-aware source transport. It authenticates neither a file nor its format.
 * The explicit maximum is supplied by a future approved admission policy, never by provider metadata.
 * Ownership transfers on open, including failure. No disk files or retained URI permissions are created.
 * A consumer still needs encryption, complete authentication and an authorized atomic commit.
 */
class AttachmentInputStream private constructor(
    private val source: InputStream,
    private val authority: AttachmentSession.Capability,
    private val maximum: Long,
) : InputStream() {
    private val reads = Any()
    @Volatile private var closed = false
    @Volatile var bytesRead: Long = 0
        private set
    private var eof = false
    private var stalled = 0

    override fun read(): Int {
        val one = ByteArray(1)
        try {
            // A broken provider's zero reads cannot turn into a synthetic zero-valued byte.
            while (true) {
                when (read(one, 0, 1)) {
                    -1 -> return -1
                    1 -> return one[0].toInt() and 0xff
                }
            }
        } finally { one.fill(0) }
    }

    override fun read(destination: ByteArray, offset: Int, length: Int): Int = synchronized(reads) {
        if (offset < 0 || length < 0 || length > destination.size - offset) throw IndexOutOfBoundsException()
        // Only one excess byte is read at the limit. Long.MAX_VALUE is excluded by open().
        val requested = minOf(length.toLong(), maximum - bytesRead + 1).toInt()
        try {
            authority.check()
            if (closed) throw IOException("Attachment input closed")
            if (length == 0) return@synchronized 0
            if (eof) return@synchronized -1
            val count = source.read(destination, offset, requested)
            if (count < -1 || count > requested) throw IOException("Invalid attachment provider read")
            authority.publish {
                if (closed) throw IOException("Attachment input closed")
                when {
                    count == -1 -> eof = true
                    count == 0 -> {
                        stalled++
                        if (stalled > 8) throw IOException("Attachment provider stalled")
                    }
                    count > maximum - bytesRead -> throw IOException("Attachment input limit exceeded")
                    else -> { bytesRead += count; stalled = 0 }
                }
            }
            count
        } catch (failure: Throwable) {
            // A provider can write a partial read and then throw. Do not return those bytes as a result.
            destination.fill(0, offset, offset + requested)
            try { close() } catch (cleanup: Throwable) { if (cleanup !== failure) failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    /** Does not acquire the read monitor: closing a provider may unblock an outstanding read.
     * An arbitrary provider can still block in read/close; this is not a worker deadline guarantee.
     */
    override fun close() {
        closed = true
        authority.close()
    }

    companion object {
        fun open(parent: AttachmentSession.Capability, source: InputStream, maximum: Long): AttachmentInputStream {
            var child: AttachmentSession.Capability? = null
            try {
                val reader = parent.fork().also { child = it }
                reader.own(source)
                require(maximum in 1 until Long.MAX_VALUE) { "Explicit positive attachment input limit required" }
                return AttachmentInputStream(source, reader, maximum)
            } catch (failure: Throwable) {
                try { if (child == null) source.close() else child.close() }
                catch (cleanup: Throwable) { if (cleanup !== failure) failure.addSuppressed(cleanup) }
                throw failure
            }
        }
    }
}
