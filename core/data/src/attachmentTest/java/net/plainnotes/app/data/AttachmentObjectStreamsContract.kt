package net.plainnotes.app.data

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import net.plainnotes.app.domain.AttachmentSession
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.security.MessageDigest
import java.util.UUID

/** Synthetic byte payloads; real public Tink primitive, frozen subtle primitive as independent oracle. */
abstract class AttachmentObjectStreamsContract {
    private fun context() = AttachmentObjectContext(UUID(0,1), UUID(0,2), UUID(0,3), UUID(0,4), UUID(0,5))
    private fun session() = AttachmentSession(UUID.randomUUID(), UUID.randomUUID())
    private val key get() = ByteArray(32) { (it+1).toByte() }
    private fun hash(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it.toInt() and 255) }
    private class Bytes(private val value: ByteArray) : SeekableByteChannel {
        private var position = 0; var closed = false
        override fun read(dst: ByteBuffer): Int {
            check(!closed)
            if (position == value.size) return -1
            val n = minOf(dst.remaining(),value.size-position); dst.put(value,position,n); position += n; return n
        }
        override fun position() = position.toLong()
        override fun position(newPosition: Long): SeekableByteChannel { require(newPosition in 0..value.size.toLong());position=newPosition.toInt();return this }
        override fun size() = value.size.toLong()
        override fun isOpen() = !closed
        override fun close() { closed=true }
        override fun write(src: ByteBuffer): Int = throw UnsupportedOperationException()
        override fun truncate(size: Long): SeekableByteChannel = throw UnsupportedOperationException()
    }
    private fun written(value: ByteArray, parent: AttachmentSession.Capability, ctx: AttachmentObjectContext = context()): Pair<AttachmentObjectStreams.Manifest, ByteArray> {
        val output=ByteArrayOutputStream(); val owned=key
        val manifest=AttachmentObjectStreams.write(parent,ctx,owned,ByteArrayInputStream(value),output,maxOf(1,value.size).toLong())
        assertArrayEquals(ByteArray(32),owned)
        return manifest to output.toByteArray()
    }
    private fun rejected(block: ()->Unit): Throwable {
        try { block() } catch(error: Exception) { return error }
        throw AssertionError("Rejected attachment accepted")
    }
    @Test fun canonicalPortableContextMatchesIndependentNetworkOrderVector() {
        val encoded=context().encode()
        assertEquals(108,encoded.size)
        assertEquals("6e6f7465732e6174746163686d656e742e6f626a6563740000010001" +
            (1..5).joinToString("") { "0000000000000000000000000000000$it" },
            encoded.joinToString("") { "%02x".format(it.toInt() and 255) })
    }
    @Test fun fullAuthenticationPrecedesRandomReadsAcrossAllSegmentBoundaries() {
        for(size in listOf(0,1,65479,65480,65481,65520,65536,131073,1048576)) {
            val value=ByteArray(size) { ((it*17)%251).toByte() };val parent=session().issue()
            val (manifest,ciphertext)=written(value,parent)
            assertEquals(size.toLong(),manifest.plaintextBytes);assertEquals(hash(value),manifest.plaintextSha256)
            assertEquals(hash(ciphertext),manifest.ciphertextSha256)
            val channel=Bytes(ciphertext);val owned=key
            AttachmentObjectStreams.open(parent,manifest,owned,channel).use { reader ->
                assertArrayEquals(ByteArray(32),owned)
                val offsets=if(size==0) listOf(0) else listOf(size-1,0,size/2,size)
                for(offset in offsets) {
                    val output=ByteArray(97) { 42 };val count=reader.readAt(offset.toLong(),output,3,91)
                    if(offset==size) assertEquals(-1,count) else {
                        assertTrue(count>0);assertArrayEquals(value.copyOfRange(offset,offset+count),output.copyOfRange(3,3+count))
                    }
                }
            }
            assertTrue(channel.closed);parent.check()
        }
    }
    @Test fun supportedApiIsWireCompatibleWithFrozenUpstreamPrimitive() {
        val value=ByteArray(132001) { (it%239).toByte() }; val parent=session().issue()
        val (manifest,ciphertext)=written(value,parent)
        val oracle=AesGcmHkdfStreaming(key,"HmacSha256",32,65536,0)
        assertArrayEquals(value,oracle.newDecryptingStream(ByteArrayInputStream(ciphertext),context().encode()).use { it.readBytes() })
        val output=ByteArrayOutputStream();oracle.newEncryptingStream(output,context().encode()).use { it.write(value) }
        val original=output.toByteArray();val expected=manifest.copy(ciphertextBytes=original.size.toLong(),ciphertextSha256=hash(original))
        AttachmentObjectStreams.open(parent,expected,key,Bytes(original)).use { reader ->
            val result=ByteArray(21);assertEquals(21,reader.readAt(66000,result,0,21));assertArrayEquals(value.copyOfRange(66000,66021),result)
        }
    }
    @Test fun corruptFinalTagEvenWithUpdatedPublicHashNeverPublishesFirstByte() {
        val parent=session().issue();val (manifest,ciphertext)=written(ByteArray(150000) { 9 },parent)
        ciphertext[ciphertext.lastIndex]=(ciphertext.last().toInt() xor 1).toByte()
        val channel=Bytes(ciphertext);val owned=key
        rejected { AttachmentObjectStreams.open(parent,manifest.copy(ciphertextSha256=hash(ciphertext)),owned,channel) }
        assertTrue(channel.closed);assertArrayEquals(ByteArray(32),owned);parent.check()
    }
    @Test fun everyContentIdentityFieldIsBoundByAead() {
        val parent=session().issue();val (manifest,ciphertext)=written(byteArrayOf(1,2,3),parent);val c=manifest.context
        for(changed in listOf(c.copy(logicalVault=UUID(1,1)),c.copy(objectId=UUID(1,2)),c.copy(version=UUID(1,3)),c.copy(attempt=UUID(1,4)),c.copy(dekHandle=UUID(1,5)))) {
            val channel=Bytes(ciphertext);rejected { AttachmentObjectStreams.open(parent,manifest.copy(context=changed),key,channel) };assertTrue(channel.closed)
        }
    }
    @Test fun wrongKeyTruncationTrailingBytesAndTrustedCommitmentMismatchAreRejected() {
        val parent=session().issue();val (manifest,bytes)=written(ByteArray(130000) { 8 },parent)
        val candidates=listOf(bytes.copyOf(bytes.size-1), bytes+byteArrayOf(0),bytes.copyOf().also { it[0]=0 })
        for(value in candidates) {
            val channel=Bytes(value)
            rejected { AttachmentObjectStreams.open(parent,manifest.copy(ciphertextBytes=value.size.toLong(),ciphertextSha256=hash(value)),key,channel) }
            assertTrue(channel.closed)
        }
        val wrong=ByteArray(32) { 77 };rejected {AttachmentObjectStreams.open(parent,manifest,wrong,Bytes(bytes))};assertArrayEquals(ByteArray(32),wrong)
        rejected {AttachmentObjectStreams.open(parent,manifest.copy(plaintextSha256="0".repeat(64)),key,Bytes(bytes))}
        rejected {AttachmentObjectStreams.open(parent,manifest.copy(plaintextBytes=129999),key,Bytes(bytes))}
        rejected {AttachmentObjectStreams.open(parent,manifest.copy(ciphertextSha256="0".repeat(64)),key,Bytes(bytes))}
    }
    @Test fun cancelledReaderClosesChannelAndClearsRejectedCallerRangeOnly() {
        val session=session();val parent=session.issue();val (manifest,bytes)=written(byteArrayOf(1,2,3),parent)
        val channel=Bytes(bytes);val reader=AttachmentObjectStreams.open(parent,manifest,key,channel)
        session.revoke();val destination=ByteArray(9) { 7 }
        rejected {reader.readAt(0,destination,2,5)}
        assertTrue(channel.closed);assertArrayEquals(byteArrayOf(7,7,0,0,0,0,0,7,7),destination)
    }
    @Test fun revokedBeforeOpenClosesTransferredChannelAndErasesTransferredKey() {
        val session=session();val parent=session.issue();val (manifest,bytes)=written(byteArrayOf(1),parent);session.revoke()
        val channel=Bytes(bytes);val owned=key
        rejected {AttachmentObjectStreams.open(parent,manifest,owned,channel)}
        assertTrue(channel.closed);assertArrayEquals(ByteArray(32),owned)
    }
    @Test fun oversizeWriteAndInvalidLimitCloseAllTransferredResourcesWithoutReceipt() {
        for(limit in listOf(0L,2L,AttachmentObjectStreams.MAXIMUM_BYTES+1)) {
            var sourceClosed=0;var sinkClosed=0;val owned=key
            val input=object:ByteArrayInputStream(byteArrayOf(1,2,3)) { override fun close() {sourceClosed++} }
            val output=object:ByteArrayOutputStream() { override fun close() {sinkClosed++} }
            rejected {AttachmentObjectStreams.write(session().issue(),context(),owned,input,output,limit)}
            assertEquals(1,sourceClosed);assertEquals(1,sinkClosed);assertArrayEquals(ByteArray(32),owned)
        }
    }
    @Test fun providerFailureRemainsPrimaryEvenWhenBothCleanupActionsFail() {
        val source=object:ByteArrayInputStream(byteArrayOf(1)) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = throw IOException("source failure")
            override fun close(): Unit = throw IOException("source close")
        }
        val target=object:OutputStream() {
            override fun write(value: Int) = Unit
            override fun close(): Unit = throw IOException("sink close")
        }
        val owned=key;val error=rejected {AttachmentObjectStreams.write(session().issue(),context(),owned,source,target,3)}
        assertEquals("source failure",error.message);assertTrue(error.suppressed.isNotEmpty());assertArrayEquals(ByteArray(32),owned)
    }
    @Test fun encryptionFinalCloseFailureNeverProducesAStagedReceipt() {
        val owned=key;val target=object:ByteArrayOutputStream() { override fun close(): Unit=throw IOException("disk close") }
        val error=rejected {AttachmentObjectStreams.write(session().issue(),context(),owned,ByteArrayInputStream(byteArrayOf(1)),target,1)}
        assertEquals("disk close",error.message);assertArrayEquals(ByteArray(32),owned)
    }
    @Test fun freshEncryptionOfSameBytesHasIndependentCiphertextAndSiblingReaderSurvives() {
        val parent=session().issue();val value=ByteArray(999) { 9 }
        val first=written(value,parent);val next=written(value,parent,context().copy(attempt=UUID(0,9)))
        assertFalse(first.second.contentEquals(next.second))
        val a=AttachmentObjectStreams.open(parent,first.first,key,Bytes(first.second))
        val b=AttachmentObjectStreams.open(parent,next.first,key,Bytes(next.second))
        a.close();val out=ByteArray(1);assertEquals(1,b.readAt(0,out,0,1));assertEquals(9,out[0].toInt());b.close();parent.check()
    }
}
