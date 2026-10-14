package net.plainnotes.poc

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

/** Synthetic candidate experiment; no application writer/reader or medical data. */
abstract class B2StreamingContract {
    @Test fun corruptFinalSegmentCannotObtainAuthenticatedReader() {
        val root=Files.createTempDirectory("synthetic-b2-red-").toFile()
        val key=ByteArray(32) { (it+1).toByte() };val aad="synthetic-B2-domain".toByteArray()
        val file=File(root,"candidate.cipher")
        try {
            B2StreamingCandidate.primitive(key).newEncryptingStream(file.outputStream(),aad).use {
                ByteArrayInputStream(ByteArray(180000) { 7 }).copyTo(it,65536)
            }
            java.io.RandomAccessFile(file,"rw").use { it.seek(it.length()-1);val b=it.read();it.seek(it.length()-1);it.write(b xor 1) }
            try {
                B2StreamingCandidate.openAuthenticated(file,key,aad).use { it.read(ByteBuffer.allocate(1)) }
                fail("Corrupt tail obtained a reader and delivered the first byte")
            } catch(expected:java.io.IOException) { }
        } finally {key.fill(0);check(root.deleteRecursively())}
    }
}

/** Additional experiments share exact source across JVM and API26/29/35. */
abstract class B2StreamingMatrixContract {
    private fun fixture(block:(File,ByteArray,ByteArray)->Unit) {
        val root=Files.createTempDirectory("synthetic-b2-matrix-").toFile()
        val key=ByteArray(32).also(java.security.SecureRandom()::nextBytes)
        try {block(root,key,"B2-TEST-ONLY-vault/version/attempt".toByteArray())}
        finally {key.fill(0);check(root.deleteRecursively())}
    }
    private fun cipher(root:File,key:ByteArray,aad:ByteArray,size:Int):File {
        val file=File(root,"${java.util.UUID.randomUUID()}.cipher")
        B2StreamingCandidate.primitive(key).newEncryptingStream(file.outputStream(),aad).use { out ->
            val chunk=ByteArray(8192){(it%251).toByte()};var remaining=size
            while(remaining>0){val n=minOf(chunk.size,remaining);out.write(chunk,0,n);remaining-=n}
            chunk.fill(0)
        };return file
    }
    private fun reject(block:()->Unit) {
        var rejected=false
        try {block()}catch(_:java.io.IOException){rejected=true}catch(_:IllegalStateException){rejected=true}
        assertTrue("Unsafe operation succeeded",rejected)
    }
    @Test fun zeroOneBlockBoundariesOneAndSixteenMiBRoundTripAndSeek()=fixture {root,key,aad ->
        for(n in listOf(0,1,65480,65481,65536,65537,1048576,16777216)) {
            B2StreamingCandidate.openAuthenticated(cipher(root,key,aad,n),key,aad).use { lease ->
                assertEquals(n.toLong(),lease.size())
                for(offset in listOf(0,n/2,maxOf(0,n-17))) {
                    val dst=ByteBuffer.allocate(minOf(17,n-offset));val count=lease.readAt(offset.toLong(),dst)
                    if(n==0)assertTrue(count<=0) else {
                        assertEquals(dst.capacity(),count)
                        for(i in 0 until count)assertEquals(((offset+i)%8192%251).toByte(),dst.array()[i])
                    }
                }
            }
        }
    }
    @Test fun sixteenMiBPlusOneExplicitlyRejected()=fixture {root,key,aad ->
        val file=cipher(root,key,aad,16777217)
        reject{B2StreamingCandidate.openAuthenticated(file,key,aad).close()}
    }
    @Test fun truncatedTrailingReorderedCopiedMissingAndWrongDomainRejected()=fixture {root,key,aad ->
        val original=cipher(root,key,aad,180000).readBytes()
        val cases=listOf(original.copyOf(original.size-1),original+byteArrayOf(0),
            original.copyOfRange(0,65536)+original.copyOfRange(131072,original.size),
            original.copyOfRange(0,40)+original.copyOfRange(65536,131072)+original.copyOfRange(40,65536)+original.copyOfRange(131072,original.size),
            original.copyOfRange(0,65536)+original.copyOfRange(0,65536)+original.copyOfRange(131072,original.size))
        for(bytes in cases){val file=File(root,"bad.cipher").also{it.writeBytes(bytes)};reject{B2StreamingCandidate.openAuthenticated(file,key,aad).close()}}
        val file=File(root,"wrong.cipher").also{it.writeBytes(original)}
        reject{B2StreamingCandidate.openAuthenticated(file,ByteArray(32){8},aad).close()}
        reject{B2StreamingCandidate.openAuthenticated(file,key,aad+1.toByte()).close()}
        file.writeBytes(original.copyOf().also{it[5]=(it[5].toInt() xor 1).toByte()})
        reject{B2StreamingCandidate.openAuthenticated(file,key,aad).close()}
    }
    @Test fun validOlderCipherTupleFailsTrustedCommitment()=fixture {root,key,aad ->
        val a=cipher(root,key,aad,1000);val b=cipher(root,key,aad,1001)
        val evidence=B2StreamingCandidate.openAuthenticated(b,key,aad).use{it.evidence}
        reject{B2StreamingCandidate.openAuthenticated(a,key,aad,expected=evidence).close()}
    }
    @Test fun ownerCloseAndSessionRevokePreventExistingChildRead()=fixture {root,key,aad ->
        val session=net.plainnotes.app.domain.AttachmentSession(java.util.UUID.randomUUID(),java.util.UUID.randomUUID())
        val owner=session.issue();val file=cipher(root,key,aad,1000)
        B2StreamingCandidate.openAuthenticated(file,key,aad,owner).use {lease ->
            owner.close();val dst=ByteBuffer.allocate(17);reject{lease.readAt(0,dst)};assertEquals(0,dst.position())
        }
        val owner2=session.issue()
        B2StreamingCandidate.openAuthenticated(file,key,aad,owner2).use {lease ->
            session.revoke();val dst=ByteBuffer.allocate(17);reject{lease.readAt(0,dst)};assertEquals(0,dst.position())
        }
    }
    @Test fun revokeAfterCompleteAuthenticationBeforeLeaseRejects()=fixture {root,key,aad ->
        val session=net.plainnotes.app.domain.AttachmentSession(java.util.UUID.randomUUID(),java.util.UUID.randomUUID())
        reject{B2StreamingCandidate.openAuthenticated(cipher(root,key,aad,1000),key,aad,session.issue(),beforeLease=session::revoke).close()}
    }
    @Test fun changedBlockAfterOpenIsReauthenticatedWithoutDelivery()=fixture {root,key,aad ->
        val file=cipher(root,key,aad,180000)
        B2StreamingCandidate.openAuthenticated(file,key,aad).use {lease ->
            java.io.RandomAccessFile(file,"rw").use {it.seek(80000);val b=it.read();it.seek(80000);it.write(b xor 1)}
            val dst=ByteBuffer.allocate(17);reject{lease.readAt(100000,dst)};assertEquals(0,dst.position())
        }
    }
    @Test fun revokeAfterDecryptBeforeDeliveryNeverCopiesPlaintext()=fixture {root,key,aad ->
        val session=net.plainnotes.app.domain.AttachmentSession(java.util.UUID.randomUUID(),java.util.UUID.randomUUID())
        B2StreamingCandidate.openAuthenticated(cipher(root,key,aad,1000),key,aad,session.issue()).use {lease ->
            val dst=ByteBuffer.allocate(17)
            reject{lease.readAt(0,dst,session::revoke)}
            assertEquals(0,dst.position());assertArrayEquals(ByteArray(17),dst.array())
        }
    }
    @Test fun streamingInputErrorCannotProduceAuthenticatedObject()=fixture {root,key,aad ->
        val file=File(root,"interrupted.cipher")
        val out=B2StreamingCandidate.primitive(key).newEncryptingStream(file.outputStream(),aad)
        out.write(ByteArray(140000){3});out.flush()
        // Simulate death: never close the encryptor (close would authenticate a legitimate short stream).
        reject{B2StreamingCandidate.openAuthenticated(file,key,aad).close()}
        out.close()
    }
}
