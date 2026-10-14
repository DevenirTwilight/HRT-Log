package net.plainnotes.poc

import net.plainnotes.app.domain.AttachmentSession
import java.io.*
import java.nio.file.Files
import java.util.UUID
import org.junit.Test
import org.junit.Assert.*

abstract class B2ImportContract {
    private fun fixture(block:(File,AttachmentSession,B2ImportSandbox.Database)->Unit) {
        val root=Files.createTempDirectory("synthetic-b2-import-").toFile()
        val session=AttachmentSession(UUID.randomUUID(),UUID.randomUUID())
        val old=File(root,"old.cipher")
        val oldKey=ByteArray(32).also(java.security.SecureRandom()::nextBytes);val oldAad="B2-TEST-ONLY-old".toByteArray()
        try {
            B2StreamingCandidate.primitive(oldKey).newEncryptingStream(old.outputStream(),oldAad).use{it.write(byteArrayOf(42))}
            B2StreamingCandidate.openAuthenticated(old,oldKey,oldAad).use{assertEquals(1L,it.size())}
        } finally {oldKey.fill(0)}
        try{block(root,session,B2ImportSandbox.Database(active=old))}finally{session.revoke();check(root.deleteRecursively())}
    }
    private fun reject(block:()->Unit) {var failed=false;try{block()}catch(_:IOException){failed=true}catch(_:IllegalStateException){failed=true};assertTrue("Expected safe refusal",failed)}
    @Test fun everyPrecommitFailureKeepsOldPointerAndCipher()=fixture {root,session,db ->
        val old=db.active;val original=old!!.readBytes()
        for(point in B2ImportSandbox.Seam.entries.filter{it!=B2ImportSandbox.Seam.AFTER_COMMIT}) {
            val operation=B2ImportSandbox.Operation()
            reject{B2ImportSandbox.import(root,ByteArrayInputStream(ByteArray(140000)),session.issue(),db,operation,
                seam={if(it==point)throw IOException("Injected $point failure including ENOSPC")})}
            assertEquals(old,db.active);assertEquals(0L,db.revision);assertArrayEquals(original,old!!.readBytes())
            assertEquals(B2ImportSandbox.Phase.ABORTED,operation.phase);assertFalse(operation.file?.exists()==true)
        }
    }
    @Test fun revokeBeforeDuringAndAfterAuthenticationPreventsCommit() {
        for(point in listOf<B2ImportSandbox.Seam?>(null,B2ImportSandbox.Seam.WRITE,B2ImportSandbox.Seam.BEFORE_COMMIT))fixture {root,session,db ->
            val owner=session.issue();if(point==null)session.revoke()
            reject{B2ImportSandbox.import(root,ByteArrayInputStream(ByteArray(140000)),owner,db,seam={if(it==point)session.revoke()})}
            assertEquals(0L,db.revision);assertEquals("old.cipher",db.active!!.name)
        }
    }
    @Test fun providerUnknownLengthErrorAndStallNeverPublish()=fixture {root,session,db ->
        val failing=object:InputStream(){var reads=0;override fun read():Int=throw IOException("No single-byte path");override fun read(b:ByteArray,off:Int,len:Int):Int {if(reads++>2)throw IOException("Provider disconnected");b.fill(7,off,off+len);return len}}
        reject{B2ImportSandbox.import(root,failing,session.issue(),db)}
        val stalled=object:InputStream(){override fun read()=0;override fun read(b:ByteArray,off:Int,len:Int)=0}
        reject{B2ImportSandbox.import(root,stalled,session.issue(),db)}
        assertEquals(0L,db.revision);assertEquals(1,root.listFiles()!!.size)
    }
    @Test fun repeatedAttemptAndConcurrentStaleRevisionCannotOverwrite()=fixture {root,session,db ->
        val first=B2ImportSandbox.Operation();val second=B2ImportSandbox.Operation()
        assertNotEquals(first.attempt,second.attempt);assertNotEquals(first.version,second.version);assertFalse(first.key.contentEquals(second.key))
        B2ImportSandbox.import(root,ByteArrayInputStream(byteArrayOf(1)),session.issue(),db,first,expected=0)
        val active=db.active!!;val original=active.readBytes()
        reject{B2ImportSandbox.import(root,ByteArrayInputStream(byteArrayOf(2)),session.issue(),db,second,expected=0)}
        reject{B2ImportSandbox.import(root,ByteArrayInputStream(byteArrayOf(3)),session.issue(),db,first)}
        assertEquals(1L,db.revision);assertEquals(active,db.active);assertArrayEquals(original,active.readBytes())
    }
    @Test fun cleanupFailureRetainsOwnedEvidenceAndForeignFiles()=fixture {root,session,db ->
        val foreign=File(root,"foreign.cipher").also{it.writeBytes(byteArrayOf(99))};val operation=B2ImportSandbox.Operation()
        reject{B2ImportSandbox.import(root,ByteArrayInputStream(byteArrayOf(1)),session.issue(),db,operation,
            seam={if(it==B2ImportSandbox.Seam.BEFORE_COMMIT)throw IOException("Cancel")},cleanup={false})}
        assertEquals(B2ImportSandbox.Phase.CLEANUP_PENDING,operation.phase);assertTrue(operation.file!!.exists())
        assertEquals(B2ImportSandbox.Reconcile.QUARANTINE_COMPLETE_ORPHAN,B2ImportSandbox.classify(operation.file!!,db,operation,true))
        assertEquals(B2ImportSandbox.Reconcile.CLEANUP_OWNED_INCOMPLETE,B2ImportSandbox.classify(operation.file!!,db,operation,false))
        assertEquals(B2ImportSandbox.Reconcile.FOREIGN_KEEP,B2ImportSandbox.classify(foreign,db,operation,false))
        assertArrayEquals(byteArrayOf(99),foreign.readBytes())
    }
    @Test fun twoConcurrentImportersShareOneCasWinner()=fixture {root,session,db ->
        val barrier=java.util.concurrent.CountDownLatch(2)
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        val operations=listOf(B2ImportSandbox.Operation(),B2ImportSandbox.Operation())
        try {
            val pending=operations.mapIndexed { index,operation ->
                val capability=session.issue()
                pool.submit(java.util.concurrent.Callable {
                    try {
                        B2ImportSandbox.import(root,ByteArrayInputStream(ByteArray(140000){index.toByte()}),capability,db,operation,expected=0,
                            seam={if(it==B2ImportSandbox.Seam.BEFORE_COMMIT){barrier.countDown();assertTrue(barrier.await(30,java.util.concurrent.TimeUnit.SECONDS))}})
                        true
                    } catch(_:IllegalStateException){false}
                })
            }
            assertEquals(listOf(false,true),pending.map{it.get(40,java.util.concurrent.TimeUnit.SECONDS)}.sorted())
            assertEquals(1L,db.revision);assertEquals(2,root.listFiles()!!.size)
            assertEquals(1,operations.count{it.phase==B2ImportSandbox.Phase.COMMITTED})
            assertEquals(1,operations.count{it.phase==B2ImportSandbox.Phase.ABORTED})
        } finally {pool.shutdownNow()}
    }
    @Test fun acknowledgementFailureAfterCommitDoesNotDeleteActive()=fixture {root,session,db ->
        val operation=B2ImportSandbox.Operation()
        reject{B2ImportSandbox.import(root,ByteArrayInputStream(byteArrayOf(1)),session.issue(),db,operation,
            seam={if(it==B2ImportSandbox.Seam.AFTER_COMMIT)throw IOException("Receipt lost")})}
        assertEquals(1L,db.revision);assertEquals(B2ImportSandbox.Phase.COMMITTED,operation.phase);assertTrue(db.active!!.exists())
        assertEquals(B2ImportSandbox.Reconcile.KEEP_ACTIVE,B2ImportSandbox.classify(db.active!!,db,operation,false))
    }
}
