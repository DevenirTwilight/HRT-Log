package net.plainnotes.app.data

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The in-process storage gate: an operation that merely overlaps another waits for it (bounded) instead of failing. Before this,
 * opening a file in the reader while its own thumbnail was being made could end in "This file couldn't be displayed".
 */
class AttachmentProcessLeaseAndroidTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val original = AttachmentOwnedFiles.waitMillis
    @After fun restore() { AttachmentOwnedFiles.waitMillis = original }

    private fun holdUntilReleased(): Triple<Thread, CountDownLatch, AtomicReference<Throwable?>> {
        val held = CountDownLatch(1); val release = CountDownLatch(1); val failure = AtomicReference<Throwable?>(null)
        val thread = Thread { try { AttachmentOwnedFiles(context).locked().use { held.countDown(); release.await(20, TimeUnit.SECONDS) } } catch (t: Throwable) { failure.set(t); held.countDown() } }
        thread.start()
        assertTrue("the holder must get the gate", held.await(10, TimeUnit.SECONDS))
        assertNull(failure.get())
        return Triple(thread, release, failure)
    }

    @Test fun anOverlappingOperationWaitsForTheHolderAndThenSucceeds() {
        val (holder, release, failure) = holdUntilReleased()
        Thread { Thread.sleep(300); release.countDown() }.start()
        val started = System.nanoTime()
        AttachmentOwnedFiles(context).locked().use { }
        val waited = (System.nanoTime() - started) / 1_000_000
        holder.join(10_000)
        assertNull(failure.get())
        assertTrue("it must really have waited for the holder (waited $waited ms)", waited >= 200)
    }

    @Test fun theWaitIsBoundedAndThenTheOldRefusalIsKept() {
        AttachmentOwnedFiles.waitMillis = 200
        val (holder, release, failure) = holdUntilReleased()
        val started = System.nanoTime()
        try { AttachmentOwnedFiles(context).locked().use { }; fail("a second operation must be refused after the bound") }
        catch (e: IllegalStateException) { assertEquals("Attachment storage busy", e.message) }
        val waited = (System.nanoTime() - started) / 1_000_000
        assertTrue("refused only after waiting about the bound (waited $waited ms)", waited in 150..4_000)
        release.countDown(); holder.join(10_000)
        assertNull(failure.get())
        // The holder let go: the gate is free again.
        AttachmentOwnedFiles(context).locked().use { }
    }

    @Test fun theSameThreadAskingTwiceIsRefusedAtOnceNotAfterAWait() {
        AttachmentOwnedFiles.waitMillis = 30_000
        AttachmentOwnedFiles(context).locked().use {
            val started = System.nanoTime()
            try { AttachmentOwnedFiles(context).locked().use { }; fail("re-entering must be refused") }
            catch (e: IllegalStateException) { assertEquals("Attachment storage busy", e.message) }
            assertTrue("no waiting for itself", (System.nanoTime() - started) / 1_000_000 < 2_000)
        }
        AttachmentOwnedFiles(context).locked().use { }
    }
}
