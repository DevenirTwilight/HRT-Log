package net.plainnotes.app.attachmentpoc

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.app.domain.AttachmentSession
import net.plainnotes.poc.B2StreamingCandidate
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** B2-GATE3, TEST ONLY: reads go through the real kernel ProxyFileDescriptor (pread / lseek+read / dup), not a direct
 * callback call. The safety contract measured here: once the owner capability is revoked, no read that STARTS
 * afterwards is answered with plaintext by this app. Bytes the kernel already cached from earlier delivery are
 * outside the app's reach and are only observed, never claimed revoked. Synthetic bytes, emulator only. */
class KernelProxyFdGate3AndroidTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val key = ByteArray(32) { (it + 77).toByte() }
    private val aad = "B2-GATE3-TEST-ONLY/kernel-proxy-fd".toByteArray()
    private val mib = 1024L * 1024
    private val size = (4 * mib).toInt()
    private fun plain(i: Long) = ((i * 131 + 7) % 251).toByte()
    private fun expected(offset: Long, n: Int) = ByteArray(n) { plain(offset + it) }

    /** Counts what the kernel actually asks for; a read counts as "after revocation" only if it STARTED after it. */
    private class Counted(private val lease: B2StreamingCandidate.Lease) : ProxyFileDescriptorCallback() {
        val reads = AtomicInteger(); val deliveredAfterRevoke = AtomicLong(); val released = CountDownLatch(1)
        @Volatile var revoked = false
        override fun onGetSize(): Long = try { lease.size() } catch (e: Exception) { throw ErrnoException("Synthetic revoked", OsConstants.EBADF, e) }
        override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
            reads.incrementAndGet(); val startedAfterRevoke = revoked
            try {
                var done = 0
                while (done < size) {
                    val n = lease.readAt(offset + done, ByteBuffer.wrap(data, done, minOf(size - done, B2StreamingCandidate.SCRATCH)))
                    if (n <= 0) break; done += n
                }
                if (startedAfterRevoke) deliveredAfterRevoke.addAndGet(done.toLong())
                return done
            } catch (e: Exception) { data.fill(0); throw ErrnoException("Synthetic authenticated read", OsConstants.EIO, e) }
        }
        override fun onRelease() { lease.close(); released.countDown() }
    }
    private sealed class Outcome { class Bytes(val value: ByteArray) : Outcome(); class Errno(val errno: Int) : Outcome() }
    private fun pread(fd: FileDescriptor, offset: Long, n: Int): Outcome {
        val out = ByteArray(n)
        return try { var done = 0; while (done < n) { val r = Os.pread(fd, out, done, n - done, offset + done); if (r <= 0) break; done += r }; Outcome.Bytes(out.copyOf(done)) }
        catch (e: ErrnoException) { assertArrayEquals("Failed read must not leak bytes", ByteArray(n), out); Outcome.Errno(e.errno) }
    }
    private fun describe(o: Outcome) = when (o) { is Outcome.Bytes -> "CACHE_OR_READ(${o.value.size})"; is Outcome.Errno -> "ERRNO(${OsConstants.errnoName(o.errno)})" }
    private fun evidence(test: String, vararg f: Pair<String, Any?>) = android.util.Log.i("GATE3_KERNEL",
        "test=$test api=${android.os.Build.VERSION.SDK_INT} pid=${android.os.Process.myPid()} " + f.joinToString(" ") { "${it.first}=${it.second}" })

    private fun fixture(test: (File, B2StreamingCandidate.Evidence) -> Unit) {
        SyntheticEmulatorGuard.requireEmulator()
        val root = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "synthetic-gate3-kernel-").toFile()
        try {
            val file = File(root, "synthetic.cipher")
            file.outputStream().use { out -> B2StreamingCandidate.primitive(key).newEncryptingStream(out, aad).use { c ->
                val chunk = ByteArray(65536); var at = 0L
                while (at < size) { for (i in chunk.indices) chunk[i] = plain(at + i); c.write(chunk); at += chunk.size }
            } }
            test(file, B2StreamingCandidate.openAuthenticated(file, key, aad).use { it.evidence })
        } finally { root.deleteRecursively() }
    }
    private fun open(cb: Counted): Pair<ParcelFileDescriptor, HandlerThread> {
        val thread = HandlerThread("synthetic-gate3-kernel").apply { start() }
        val pfd = (context.getSystemService(Context.STORAGE_SERVICE) as StorageManager)
            .openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY, cb, Handler(thread.looper))
        return pfd to thread
    }

    @Test fun kernelReadsReachCallbackAndRevocationStopsNewPlaintext() = fixture { file, evidence ->
        val red = InstrumentationRegistry.getArguments().getString("gate3_kernel_red") == "true"
        val session = AttachmentSession(UUID.randomUUID(), UUID.randomUUID())
        // RED CONTROL ONLY: a lease bound to an unrelated capability ignores the owner's revocation.
        val capability = if (red) AttachmentSession(UUID.randomUUID(), UUID.randomUUID()).issue() else session.issue()
        val cb = Counted(B2StreamingCandidate.openAuthenticated(file, key, aad, capability, evidence))
        val (pfd, thread) = open(cb)
        try {
            assertEquals(size.toLong(), Os.fstat(pfd.fileDescriptor).st_size)
            val first = pread(pfd.fileDescriptor, 0, 4096)
            assertTrue("pread must reach the app callback through the kernel", cb.reads.get() > 0)
            assertArrayEquals(expected(0, 4096), (first as Outcome.Bytes).value)
            Os.lseek(pfd.fileDescriptor, 2 * mib, OsConstants.SEEK_SET)
            val viaSeek = ByteArray(4096); assertEquals(4096, Os.read(pfd.fileDescriptor, viaSeek, 0, 4096))
            assertArrayEquals(expected(2 * mib, 4096), viaSeek)
            val readsBeforeRevoke = cb.reads.get()
            session.revoke(); cb.revoked = true
            val far = pread(pfd.fileDescriptor, 3 * mib + mib / 2, 4096)   // never delivered, far beyond readahead
            val same = pread(pfd.fileDescriptor, 0, 4096)                   // delivered before revocation
            evidence("kernelRevocation", "red" to red, "readsBeforeRevoke" to readsBeforeRevoke, "readsAfterRevoke" to (cb.reads.get() - readsBeforeRevoke),
                "farOffset" to describe(far), "sameOffset" to describe(same), "deliveredAfterRevoke" to cb.deliveredAfterRevoke.get())
            assertEquals("Revoked kernel read delivered new plaintext", 0L, cb.deliveredAfterRevoke.get())
            assertTrue("A never-delivered offset must be refused after revocation", far is Outcome.Errno)
            if (same is Outcome.Bytes) assertArrayEquals("Kernel cache may only replay bytes delivered before revocation", expected(0, same.value.size), same.value)
        } finally { pfd.close(); thread.quitSafely() }
    }

    @Test fun dupDescriptorOutlivesOwnersCloseButNotRevocation() = fixture { file, evidence ->
        val session = AttachmentSession(UUID.randomUUID(), UUID.randomUUID())
        val cb = Counted(B2StreamingCandidate.openAuthenticated(file, key, aad, session.issue(), evidence))
        val (pfd, thread) = open(cb); val dup = pfd.dup()
        try {
            assertArrayEquals(expected(0, 4096), (pread(dup.fileDescriptor, 0, 4096) as Outcome.Bytes).value)
            pfd.close()                                                     // the owner closes its own descriptor only
            val mid = pread(dup.fileDescriptor, mib, 4096)
            // Closing our descriptor does NOT stop a consumer's dup: revocation must be enforced in the callback.
            assertArrayEquals("A dup keeps reading after the owner's close", expected(mib, 4096), (mid as Outcome.Bytes).value)
            assertEquals("onRelease must wait for the last descriptor", 1L, cb.released.count)
            session.revoke(); cb.revoked = true
            val far = pread(dup.fileDescriptor, 3 * mib, 4096)
            evidence("dupAfterRevoke", "midBeforeRevoke" to describe(mid), "farAfterRevoke" to describe(far), "deliveredAfterRevoke" to cb.deliveredAfterRevoke.get())
            assertTrue("A dup must be refused after revocation", far is Outcome.Errno)
            assertEquals(0L, cb.deliveredAfterRevoke.get())
            dup.close()
            assertTrue("onRelease after the last descriptor closes", cb.released.await(30, TimeUnit.SECONDS))
        } finally { runCatching { dup.close() }; runCatching { pfd.close() }; thread.quitSafely() }
    }
}
