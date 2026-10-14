package net.plainnotes.app.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

class AttachmentSessionTest {
    private fun session() = AttachmentSession(UUID.randomUUID(), UUID.randomUUID())
    @Test fun revokeBeforeCommitRejectsWithoutCallingCommit() {
        val s = session(); val c = s.issue(); s.revoke(); var writes = 0
        assertThrows(IllegalStateException::class.java) { c.publish { writes++ } }
        assertEquals(0, writes)
    }
    @Test fun commitAndRevokeHaveOneLinearizationOrder() {
        val s = session(); val c = s.issue(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val revokeStarted = CountDownLatch(1); val revoked = CountDownLatch(1); val executor = Executors.newFixedThreadPool(2)
        try {
            val commit = executor.submit<Int> { c.publish { entered.countDown(); check(release.await(5,TimeUnit.SECONDS)); 1 } }
            assertTrue(entered.await(5,TimeUnit.SECONDS))
            val revoke = executor.submit { revokeStarted.countDown(); s.revoke(); revoked.countDown() }
            assertTrue(revokeStarted.await(5,TimeUnit.SECONDS))
            assertFalse(revoked.await(100,TimeUnit.MILLISECONDS))
            release.countDown(); assertEquals(1,commit.get(5,TimeUnit.SECONDS)); revoke.get(5,TimeUnit.SECONDS)
            assertThrows(IllegalStateException::class.java) { c.publish { error("late commit") } }
        } finally { release.countDown(); executor.shutdownNow() }
    }
    @Test fun slowPreparationDoesNotBlockRevocation() {
        val s = session(); val c = s.issue(); val prepared = CountDownLatch(1); val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val task = executor.submit { prepared.countDown(); check(release.await(5,TimeUnit.SECONDS)); c.publish { error("late result") } }
            assertTrue(prepared.await(5,TimeUnit.SECONDS)); s.revoke(); release.countDown()
            assertThrows(java.util.concurrent.ExecutionException::class.java) { task.get(5,TimeUnit.SECONDS) }
        } finally { release.countDown(); executor.shutdownNow() }
    }
    @Test fun oldProcessWrongDatasetPrivateSpaceAndClosedReaderAreDenied() {
        val a = session(); val b = session(); assertNotEquals(a.processIdentity(),b.processIdentity())
        assertThrows(IllegalStateException::class.java) { a.issue(primary=false) }
        assertThrows(IllegalStateException::class.java) { a.issue(expectedDataset=UUID.randomUUID()) }
        assertThrows(IllegalStateException::class.java) { a.issue(expectedRecordEpoch=UUID.randomUUID()) }
        val c=a.issue(); c.close(); assertThrows(IllegalStateException::class.java) { c.check() }
        a.revoke(); assertThrows(IllegalStateException::class.java) { a.issue() }
    }
    @Test fun childCloseDoesNotRevokeSiblingButOwnerCloseRevokesAllDescendants() {
        val parent=session().issue();val a=parent.fork();val b=parent.fork();val child=b.fork()
        a.close();assertThrows(IllegalStateException::class.java){a.check()};b.check();child.check()
        parent.close();assertThrows(IllegalStateException::class.java){b.check()};assertThrows(IllegalStateException::class.java){child.check()}
    }

}
