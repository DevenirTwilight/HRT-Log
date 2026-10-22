package net.plainnotes.app.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AttachmentResourceLifecycleTest {
    private fun session() = AttachmentSession(UUID.randomUUID(), UUID.randomUUID())

    @Test fun revokeReleasesEveryHandleAndClearsOwnedBuffer() {
        val session = session(); val capability = session.issue()
        val closes = AtomicInteger(); val bytes = byteArrayOf(12, 34, 56)
        capability.own(AutoCloseable { closes.incrementAndGet() }); capability.ownBytes(bytes)
        session.revoke()
        assertEquals(1, closes.get()); assertArrayEquals(ByteArray(3), bytes)
        assertThrows(IllegalStateException::class.java) { capability.check() }
        session.revoke(); assertEquals(1, closes.get())
    }

    @Test fun closingParentReleasesDescendantsButNotIndependentReaders() {
        val session = session(); val parent = session.issue(); val child = parent.fork(); val grandchild = child.fork()
        val sibling = session.issue(); val closed = mutableListOf<String>()
        parent.own(AutoCloseable { closed += "parent" }); child.own(AutoCloseable { closed += "child" })
        grandchild.own(AutoCloseable { closed += "grandchild" }); sibling.own(AutoCloseable { closed += "sibling" })
        parent.close()
        assertEquals(listOf("parent", "child", "grandchild"), closed)
        sibling.check(); assertThrows(IllegalStateException::class.java) { grandchild.check() }
        session.revoke(); assertEquals("sibling", closed.last())
    }

    @Test fun rejectedRegistrationReleasesResourceAndPreservesBothErrors() {
        val session = session(); val capability = session.issue(); session.revoke()
        val closes = AtomicInteger(); val cleanup = IllegalArgumentException("synthetic cleanup failure")
        val error = assertThrows(IllegalStateException::class.java) {
            capability.own(AutoCloseable { closes.incrementAndGet(); throw cleanup })
        }
        assertEquals(1, closes.get()); assertEquals(listOf(cleanup), error.suppressed.toList())
    }

    @Test fun oneFailedCleanupDoesNotSkipOtherResourcesOrReenableSession() {
        val session = session(); val capability = session.issue(); val closed = mutableListOf<Int>()
        capability.own(AutoCloseable { closed += 1; throw IllegalStateException("synthetic close") })
        capability.own(AutoCloseable { closed += 2 })
        val bytes = byteArrayOf(7, 8); capability.ownBytes(bytes)
        val error = assertThrows(IllegalStateException::class.java) { session.revoke() }
        assertEquals(1, error.suppressed.size); assertEquals(listOf(1, 2), closed)
        assertArrayEquals(ByteArray(2), bytes)
        assertThrows(IllegalStateException::class.java) { session.issue() }
        assertThrows(IllegalStateException::class.java) { capability.check() }
        val again = assertThrows(IllegalStateException::class.java) { session.revoke() }
        assertEquals(1, again.suppressed.size); assertEquals(listOf(1, 2), closed)
    }

    @Test fun individualCloseIsIdempotentAndDoesNotCancelSibling() {
        val session = session(); val a = session.issue(); val b = session.issue(); val closes = AtomicInteger()
        val lease = a.own(AutoCloseable { closes.incrementAndGet() })
        lease.close(); lease.close(); a.check(); b.check()
        a.close(); session.revoke(); assertEquals(1, closes.get())
    }

    @Test fun revocationRejectsNewWorkWhileSlowCleanupRunsWithoutHoldingPublicationMonitor() {
        val session = session(); val capability = session.issue()
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val executor = Executors.newFixedThreadPool(2)
        capability.own(AutoCloseable { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) })
        try {
            val revoked = executor.submit { session.revoke() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val rejected = executor.submit<Boolean> {
                assertThrows(IllegalStateException::class.java) { capability.publish { error("must not publish") } }
                true
            }
            assertTrue(rejected.get(2, TimeUnit.SECONDS)); assertFalse(revoked.isDone)
            release.countDown(); revoked.get(5, TimeUnit.SECONDS)
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun concurrentCloseAndRevokeWaitForTheSameCleanupExactlyOnce() {
        val session = session(); val capability = session.issue(); val closes = AtomicInteger()
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val secondStarted = CountDownLatch(1)
        val secondReturned = CountDownLatch(1); val executor = Executors.newFixedThreadPool(2)
        val lease = capability.own(AutoCloseable { closes.incrementAndGet(); entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) })
        try {
            val first = executor.submit { lease.close() }; assertTrue(entered.await(5, TimeUnit.SECONDS))
            val second = executor.submit { secondStarted.countDown(); session.revoke(); secondReturned.countDown() }
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS)); assertFalse(secondReturned.await(100, TimeUnit.MILLISECONDS))
            release.countDown(); first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS)
            assertEquals(1, closes.get())
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun closingChildDoesNotCloseParentOrSiblingResources() {
        val session = session(); val parent = session.issue(); val child = parent.fork(); val sibling = parent.fork()
        val closed = mutableListOf<String>()
        parent.own(AutoCloseable { closed += "parent" }); child.own(AutoCloseable { closed += "child" })
        sibling.own(AutoCloseable { closed += "sibling" }); child.close()
        assertEquals(listOf("child"), closed); parent.check(); sibling.check()
        session.revoke(); assertEquals(listOf("child", "parent", "sibling"), closed)
    }

    @Test fun closedReaderAndLatePreparationCannotRetainSensitiveBuffer() {
        val session = session(); val capability = session.issue(); capability.close()
        val bytes = byteArrayOf(1, 2, 3)
        assertThrows(IllegalStateException::class.java) { capability.ownBytes(bytes) }
        assertArrayEquals(ByteArray(3), bytes)
        val late = session.issue(); session.revoke(); val lateBytes = byteArrayOf(4, 5)
        assertThrows(IllegalStateException::class.java) { late.ownBytes(lateBytes) }
        assertArrayEquals(ByteArray(2), lateBytes)
    }

    @Test fun completingCleanupCannotDeadlockWithConcurrentPublicationClosingTheSameLease() {
        val session = session(); val capability = session.issue(); val closes = AtomicInteger()
        val closing = CountDownLatch(1); val release = CountDownLatch(1); val publishing = CountDownLatch(1)
        // Daemon workers let the RED lock-order regression fail with a bounded timeout, not hang the test JVM.
        val executor = Executors.newFixedThreadPool(2) { task -> Thread(task).apply { isDaemon = true } }
        val lease = capability.own(AutoCloseable { closes.incrementAndGet(); closing.countDown(); check(release.await(5, TimeUnit.SECONDS)) })
        try {
            val close = executor.submit { lease.close() }; assertTrue(closing.await(5, TimeUnit.SECONDS))
            val publication = executor.submit { capability.publish { publishing.countDown(); lease.close() } }
            assertTrue(publishing.await(5, TimeUnit.SECONDS)); release.countDown()
            close.get(2, TimeUnit.SECONDS); publication.get(2, TimeUnit.SECONDS)
            assertEquals(1, closes.get()); session.revoke()
        } finally { release.countDown(); executor.shutdownNow() }
    }
}
