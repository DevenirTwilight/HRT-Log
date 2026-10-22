package net.plainnotes.app.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AttachmentInputStreamTest {
    private fun session() = AttachmentSession(UUID.randomUUID(), UUID.randomUUID())
    private class Source(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closes = 0; var delivered = 0
        override fun read(b: ByteArray, offset: Int, length: Int): Int = super.read(b, offset, length).also { if (it > 0) delivered += it }
        override fun close() { closes++ }
    }

    @Test fun exactLimitAndEmptySourceReachRealEofWithoutRepeatingProviderReads() {
        for (bytes in listOf(ByteArray(0), byteArrayOf(1, 2, 3, 4))) {
            val session = session(); val source = Source(bytes)
            AttachmentInputStream.open(session.issue(), source, 4).use { input ->
                assertArrayEquals(bytes, input.readBytes()); assertEquals(bytes.size.toLong(), input.bytesRead)
                assertEquals(-1, input.read()); assertEquals(-1, input.read())
            }
            session.revoke(); assertEquals(1, source.closes)
        }
    }

    @Test fun oversizedSourceReadsOnlyOneProbeByteAndNeverReturnsTheExcess() {
        val session = session(); val source = Source(ByteArray(100) { 7 })
        val input = AttachmentInputStream.open(session.issue(), source, 4)
        val buffer = ByteArray(8) { 99 }
        assertEquals(4, input.read(buffer, 2, 4)); assertEquals(4L, input.bytesRead)
        assertThrows(IOException::class.java) { input.read(buffer, 2, 4) }
        assertEquals(5, source.delivered); assertEquals(0.toByte(), buffer[2]); assertEquals(99.toByte(), buffer[0])
        assertEquals(1, source.closes); assertThrows(IllegalStateException::class.java) { input.read() }
    }

    @Test fun aLargeFirstReadRejectsRatherThanPublishingAPartialSuccess() {
        val source = Source(ByteArray(30) { 8 }); val input = AttachmentInputStream.open(session().issue(), source, 4)
        val buffer = ByteArray(20) { 5 }
        assertThrows(IOException::class.java) { input.read(buffer) }
        assertEquals(5, source.delivered); assertArrayEquals(ByteArray(5), buffer.copyOfRange(0, 5))
        assertEquals(0L, input.bytesRead); assertEquals(1, source.closes)
    }

    @Test fun cancellingOneReaderDoesNotRevokeParentOrSibling() {
        val session = session(); val parent = session.issue(); val a = Source(byteArrayOf(1)); val b = Source(byteArrayOf(2))
        val first = AttachmentInputStream.open(parent, a, 10); val second = AttachmentInputStream.open(parent, b, 10)
        first.close(); parent.check(); assertEquals(2, second.read()); assertEquals(1, a.closes); assertEquals(0, b.closes)
        session.revoke(); assertEquals(1, b.closes); assertThrows(IllegalStateException::class.java) { second.read() }
    }

    @Test fun partialProviderFailureIsPreservedAndRequestedRangeCleared() {
        val original = IOException("synthetic reliable-pipe error"); val cleanup = IOException("synthetic close error")
        val source = object : InputStream() {
            override fun read(): Int = error("bulk only")
            override fun read(b: ByteArray, off: Int, len: Int): Int { b[off] = 42; throw original }
            override fun close() { throw cleanup }
        }
        val input = AttachmentInputStream.open(session().issue(), source, 8); val buffer = ByteArray(8) { 3 }
        val failure = assertThrows(IOException::class.java) { input.read(buffer, 2, 3) }
        assertSame(original, failure); assertEquals(1, failure.suppressed.size)
        assertArrayEquals(byteArrayOf(3, 3, 0, 0, 0, 3, 3, 3), buffer); assertEquals(0L, input.bytesRead)
    }

    @Test fun stalledAndMalformedProvidersCannotSpinOrForgeCounts() {
        for (count in listOf(0, -2, 100)) {
            var reads = 0; var closes = 0
            val source = object : InputStream() {
                override fun read(): Int = error("bulk only")
                override fun read(b: ByteArray, off: Int, len: Int): Int { reads++; return count }
                override fun close() { closes++ }
            }
            val input = AttachmentInputStream.open(session().issue(), source, 8)
            assertThrows(IOException::class.java) { input.read() }
            assertEquals(if (count == 0) 9 else 1, reads); assertEquals(1, closes); assertEquals(0L, input.bytesRead)
        }
    }

    @Test fun invalidLimitsAndRevokedOpenCloseTransferredSources() {
        for (maximum in listOf(0L, -1L, Long.MAX_VALUE)) {
            val source = Source(byteArrayOf(1))
            assertThrows(IllegalArgumentException::class.java) { AttachmentInputStream.open(session().issue(), source, maximum) }
            assertEquals(1, source.closes)
        }
        val session = session(); val cap = session.issue(); session.revoke(); val source = Source(byteArrayOf(1))
        assertThrows(IllegalStateException::class.java) { AttachmentInputStream.open(cap, source, 8) }
        assertEquals(1, source.closes)
    }

    @Test fun longLimitsDoNotOverflowAndInvalidBufferRangesDoNotReadTheProvider() {
        val source = Source(byteArrayOf(1, 2)); val input = AttachmentInputStream.open(session().issue(), source, Long.MAX_VALUE - 1)
        input.use {
            assertThrows(IndexOutOfBoundsException::class.java) { it.read(ByteArray(2), Int.MAX_VALUE, 1) }
            assertThrows(IndexOutOfBoundsException::class.java) { it.read(ByteArray(2), 0, -1) }
            assertEquals(0, source.delivered); assertEquals(0, it.read(ByteArray(0)))
            assertArrayEquals(byteArrayOf(1, 2), it.readBytes()); assertFalse(it.markSupported())
            assertThrows(IOException::class.java) { it.reset() }
        }
    }

    @Test fun revocationDuringReadRejectsLateBytesAndClearsCallerRange() {
        val session = session(); val started = CountDownLatch(1); val closed = CountDownLatch(1)
        val source = object : InputStream() {
            override fun read(): Int = error("bulk only")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                started.countDown(); check(closed.await(5, TimeUnit.SECONDS)); b[off] = 88; return 1
            }
            override fun close() { closed.countDown() }
        }
        val input = AttachmentInputStream.open(session.issue(), source, 8); val buffer = ByteArray(4) { 7 }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val read = executor.submit { assertThrows(IllegalStateException::class.java) { input.read(buffer) } }
            assertTrue(started.await(5, TimeUnit.SECONDS)); session.revoke(); read.get(5, TimeUnit.SECONDS)
            assertArrayEquals(ByteArray(4), buffer); assertEquals(0L, input.bytesRead)
        } finally { closed.countDown(); executor.shutdownNow() }
    }

    @Test fun chunkBoundariesAgreeWithIndependentByteCountOracle() {
        for (maximum in 1..32) for (size in 0..maximum + 2) for (chunk in listOf(1, 3, 128)) {
            val original = ByteArray(size) { (it * 17).toByte() }; val source = Source(original)
            val input = AttachmentInputStream.open(session().issue(), source, maximum.toLong())
            val result = java.io.ByteArrayOutputStream(); var failure: IOException? = null
            try {
                input.use {
                    val buffer = ByteArray(chunk)
                    while (true) {
                        val count = it.read(buffer)
                        if (count < 0) break
                        if (count > 0) result.write(buffer, 0, count)
                    }
                }
            } catch (expected: IOException) { failure = expected }
            if (size <= maximum) {
                assertNull(failure); assertArrayEquals(original, result.toByteArray())
                assertEquals(size.toLong(), input.bytesRead)
            } else {
                assertNotNull(failure); assertEquals("Attachment input limit exceeded", failure!!.message)
                assertTrue(result.size() <= maximum)
                assertArrayEquals(original.copyOf(result.size()), result.toByteArray())
            }
            assertTrue(source.delivered <= maximum + 1); assertEquals(1, source.closes)
        }
    }
}
