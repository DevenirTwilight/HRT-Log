package net.plainnotes.app.data

import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingKey
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingParameters
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import com.google.crypto.tink.util.SecretBytes
import net.plainnotes.app.domain.AttachmentInputStream
import net.plainnotes.app.domain.AttachmentSession
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.SeekableByteChannel
import java.security.MessageDigest

/** Default-OFF crypto adapter, not a filesystem owner/import coordinator or successful SQL save.
 * All parameters come from an authorized caller; production application has no caller yet.
 * No plaintext file, key persistence, Room write or format-admission claim is made here.
 */
internal object AttachmentObjectStreams {
    // Existing attachment metadata bound, not a new product quota.
    const val MAXIMUM_BYTES = 16L * 1024 * 1024
    data class Manifest(val context: AttachmentObjectContext, val plaintextBytes: Long,
        val ciphertextBytes: Long, val plaintextSha256: String, val ciphertextSha256: String) {
        init {
            require(plaintextBytes in 0..MAXIMUM_BYTES)
            require(ciphertextBytes in 1..(MAXIMUM_BYTES + 65536))
            require(listOf(plaintextSha256, ciphertextSha256).all { it.matches(Regex("[0-9a-f]{64}")) })
        }
    }
    private val parameters = AesGcmHkdfStreamingParameters.builder()
        .setKeySizeBytes(32).setDerivedAesGcmKeySizeBytes(32)
        .setHkdfHashType(AesGcmHkdfStreamingParameters.HashType.SHA256)
        .setCiphertextSegmentSizeBytes(65536).build()

    private fun primitive(key: ByteArray): StreamingAead {
        require(key.size == 32)
        StreamingAeadConfig.register()
        val imported = AesGcmHkdfStreamingKey.create(parameters,
            SecretBytes.copyFrom(key, InsecureSecretKeyAccess.get()))
        return KeysetHandle.newBuilder().addEntry(KeysetHandle.importKey(imported)
            .withRandomId().makePrimary()).build().getPrimitive(StreamingAead::class.java)
    }
    private fun digest() = MessageDigest.getInstance("SHA-256")
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
    private class Sink(private val target: OutputStream) : OutputStream() {
        var count = 0L; val sha = digest(); private var closed = false
        override fun write(value: Int) { write(byteArrayOf(value.toByte()), 0, 1) }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            check(!closed)
            if (length > MAXIMUM_BYTES + 65536 - count) throw IOException("Ciphertext limit exceeded")
            target.write(bytes, offset, length); sha.update(bytes, offset, length); count += length
        }
        override fun flush() = target.flush()
        @Synchronized override fun close() { if (!closed) { closed = true; target.close() } }
    }
    /** Takes sole ownership of source, sink and raw DEK even on failure. Receipt is STAGED only.
     * Caller must own a new no-replace encrypted object and later authenticate/fsync/commit it.
     */
    fun write(parent: AttachmentSession.Capability, context: AttachmentObjectContext,
        rawDek: ByteArray, source: InputStream, target: OutputStream, maximum: Long): Manifest {
        var child: AttachmentSession.Capability? = null
        var input: AttachmentInputStream? = null
        val sink = Sink(target)
        val ownedSource = object : FilterInputStream(source) {
            private var closed = false
            @Synchronized override fun close() { if (!closed) { closed=true; source.close() } }
        }
        var primary: Throwable? = null
        var ownedBuffer: ByteArray? = null
        try {
            val owner = parent.fork().also { child = it }
            owner.own(sink)
            input = AttachmentInputStream.open(owner, ownedSource, maximum)
            require(maximum in 1..MAXIMUM_BYTES)
            val buffer = ByteArray(65536).also { ownedBuffer = it }
            val sha = digest()
            primitive(rawDek).newEncryptingStream(sink, context.encode()).use { encrypted ->
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    owner.check(); encrypted.write(buffer, 0, count); sha.update(buffer, 0, count)
                }
            }
            return owner.publish { Manifest(context, input.bytesRead, sink.count,
                sha.digest().hex(), sink.sha.digest().hex()) }
        } catch (failure: Throwable) {
            primary = failure; throw failure
        } finally {
            rawDek.fill(0); ownedBuffer?.fill(0)
            // Never mask an IO/auth error, and never let one cleanup stop the others.
            try { cleanup(child?.let { AutoCloseable { it.close() } }, input, ownedSource, sink) }
            catch (failure: Throwable) { if (primary == null) throw failure else primary.addSuppressed(failure) }
        }
    }
    /** Sole ownership of pinned channel and raw DEK transfers, including on rejection.
     * Expected manifest must be selected by trusted encrypted metadata, not supplied by a provider.
     * Full ciphertext hash + full authenticated plaintext EOF are checked before lease publication.
     */
    fun open(parent: AttachmentSession.Capability, expected: Manifest, rawDek: ByteArray,
        channel: SeekableByteChannel): Reader {
        var child: AttachmentSession.Capability? = null
        val owned = OwnedChannel(channel)
        var ownedBuffer: ByteArray? = null
        try {
            val owner = parent.fork().also { child = it }; owner.own(owned)
            val buffer = ByteArray(65536).also { ownedBuffer = it }
            val aead = primitive(rawDek)
            if (owned.size() != expected.ciphertextBytes) throw IOException("Ciphertext length mismatch")
            owned.position(0)
            val cipherHash = digest(); var ciphertextBytes = 0L
            while (true) {
                owner.check()
                val request = minOf(buffer.size.toLong(), expected.ciphertextBytes-ciphertextBytes+1).toInt()
                val n = owned.read(ByteBuffer.wrap(buffer, 0, request))
                if (n < 0) break
                if (n == 0) throw IOException("Ciphertext channel stalled")
                if (n > expected.ciphertextBytes-ciphertextBytes) throw IOException("Ciphertext grew during verification")
                ciphertextBytes += n; cipherHash.update(buffer, 0, n)
            }
            if (ciphertextBytes != expected.ciphertextBytes || owned.size() != expected.ciphertextBytes ||
                cipherHash.digest().hex() != expected.ciphertextSha256) throw IOException("Ciphertext commitment mismatch")
            owned.position(0)
            val plainHash = digest(); var bytes = 0L
            aead.newDecryptingStream(Channels.newInputStream(NonClosingChannel(owned)), expected.context.encode()).use { plain ->
                while (true) {
                    owner.check()
                    val n = plain.read(buffer)
                    if (n < 0) break
                    if (n == 0) throw IOException("Plaintext stream stalled")
                    if (n > expected.plaintextBytes - bytes) throw IOException("Plaintext length mismatch")
                    bytes += n; plainHash.update(buffer, 0, n)
                }
            }
            if (bytes != expected.plaintextBytes || plainHash.digest().hex() != expected.plaintextSha256)
                throw IOException("Plaintext commitment mismatch")
            owned.position(0)
            val reader = aead.newSeekableDecryptingChannel(NonClosingChannel(owned), expected.context.encode())
            owner.own(reader)
            return owner.publish { Reader(owner, reader, expected.plaintextBytes) }
        } catch (failure: Throwable) {
            try { cleanup(child?.let { AutoCloseable { it.close() } }, owned) } catch (error: Throwable) { failure.addSuppressed(error) }
            throw failure
        } finally { rawDek.fill(0); ownedBuffer?.fill(0) }
    }
    class Reader internal constructor(private val authority: AttachmentSession.Capability,
        private val channel: SeekableByteChannel, val size: Long) : AutoCloseable {
        private val reads = Any()
        fun readAt(position: Long, destination: ByteArray, offset: Int, length: Int): Int = synchronized(reads) {
            require(position in 0..size)
            if (offset < 0 || length < 0 || length > destination.size - offset) throw IndexOutOfBoundsException()
            try {
                authority.check()
                if (length == 0) return@synchronized 0
                if (position == size) return@synchronized -1
                channel.position(position)
                val n = channel.read(ByteBuffer.wrap(destination, offset, minOf(length.toLong(), size-position).toInt()))
                if (n <= 0) throw IOException("Authenticated reader ended early")
                authority.publish { n }
            } catch (failure: Throwable) {
                destination.fill(0, offset, offset+length)
                try { close() } catch (error: Throwable) { failure.addSuppressed(error) }
                throw failure
            }
        }
        override fun close() = authority.close()
    }
    private class OwnedChannel(private val channel: SeekableByteChannel) : SeekableByteChannel by channel {
        private var closed = false
        @Synchronized override fun close() { if (!closed) { closed=true; channel.close() } }
    }
    private class NonClosingChannel(private val channel: SeekableByteChannel) : SeekableByteChannel by channel {
        override fun close() = Unit
    }
    private fun cleanup(vararg resources: AutoCloseable?) {
        var failure: Throwable? = null
        for (resource in resources) try { resource?.close() } catch (error: Throwable) {
            if (failure == null) failure=error else failure.addSuppressed(error)
        }
        failure?.let { throw it }
    }
}
