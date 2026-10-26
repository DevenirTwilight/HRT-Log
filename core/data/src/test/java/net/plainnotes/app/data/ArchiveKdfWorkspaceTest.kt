package net.plainnotes.app.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CancellationException

class ArchiveKdfWorkspaceTest {
    @Test fun concurrentDifferentArchivesCannotShareLiveWorkBlocks() {
        val executor=java.util.concurrent.Executors.newFixedThreadPool(2)
        val start=java.util.concurrent.CountDownLatch(1)
        try {
            val jobs=listOf("v3-s7" to "v3-s7.json","clinical-v3" to "clinical-s7.json").map {(cipher,json)->
                executor.submit<Boolean> {
                    check(start.await(10,java.util.concurrent.TimeUnit.SECONDS))
                    FeatherlineContainer.read(File("src/test/resources/featherline/$cipher.cipher").readBytes()).use {c->
                        File("src/test/resources/featherline/$json").readBytes().contentEquals(c.authenticate("synthetic-only-password".toCharArray()))
                    }
                }
            }
            start.countDown();jobs.forEach {assertTrue(it.get(30,java.util.concurrent.TimeUnit.SECONDS))}
        } finally {start.countDown();executor.shutdownNow()}
    }
    @Test fun boundedBlocksAreActuallyReusedAfterCompletionOrFailure() {
        val pool=ArchiveKdfWorkspace.Blocks()
        val first=pool.allocate()
        repeat(65539){pool.allocate()}
        try {pool.allocate();fail("Unbounded pool")}catch(_:IllegalStateException){}
        pool.clear()
        assertSame(first,pool.allocate())
        pool.clear()
        assertSame(first,pool.allocate())
    }
    @Test fun wrongPasswordThenCorrectAndDifferentSaltMatchIndependentVectors() {
        for(name in listOf("v3-s7","clinical-v3")) {
            FeatherlineContainer.read(File("src/test/resources/featherline/$name.cipher").readBytes()).use {c->
                try {c.authenticate("wrong".toCharArray());fail("Authentication bypass")}catch(_:FeatherlineContainer.Authentication){}
                val expected=File("src/test/resources/featherline/"+if(name=="v3-s7")"v3-s7.json" else "clinical-s7.json").readBytes()
                assertArrayEquals(expected,c.authenticate("synthetic-only-password".toCharArray()))
            }
        }
    }
    @Test fun cancellationAfterDerivationDoesNotPublishKeyAndNextUseStillWorks() {
        val key=ByteArray(32){7};var calls=0
        try {ArchiveKdfWorkspace.derive(byteArrayOf(1),ByteArray(16),key){if(++calls==2)throw CancellationException()};fail("Cancelled derivation accepted")}
        catch(_:CancellationException){}
        assertTrue(key.all {it==0.toByte()})
        FeatherlineContainer.read(File("src/test/resources/featherline/v3-s7.cipher").readBytes()).use {c->
            assertArrayEquals(File("src/test/resources/featherline/v3-s7.json").readBytes(),c.authenticate("synthetic-only-password".toCharArray()))
        }
    }
}
