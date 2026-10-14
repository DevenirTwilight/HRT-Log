package net.plainnotes.poc

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files

abstract class CapabilityObjectContract {
    @Test fun revokedBeforeBusinessPublicationCannotReturnCapability() {
        val root = Files.createTempDirectory("synthetic-b1-revoke").toFile()
        val session = CapabilityObject.Session()
        try {
            try {
                CapabilityObject.write(root, ByteArrayInputStream(byteArrayOf(7)), session,
                    beforeCommit = { session.revoke() }).use { fail("Revoked publication returned a capability") }
            } catch (expected: IllegalStateException) { }
            assertEquals(0, root.listFiles()!!.size)
        } finally { root.deleteRecursively() }
    }
    @Test fun closingOwnerRevokesAlreadyOpenedReader() {
        val root = Files.createTempDirectory("synthetic-b1-close").toFile()
        try {
            val owner = CapabilityObject.write(root, ByteArrayInputStream(byteArrayOf(8)), CapabilityObject.Session())
            owner.open().use { reader ->
                owner.close()
                try { reader.readAt(0, ByteArray(1)); fail("Closed owner leaked bytes") }
                catch (expected: IllegalStateException) { }
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun revokeAfterDecryptionBeforeDeliveryErasesResponse() {
        val root = Files.createTempDirectory("synthetic-b1-delivery").toFile()
        val session = CapabilityObject.Session()
        try {
            CapabilityObject.write(root,ByteArrayInputStream(byteArrayOf(9)),session).use { owner ->
                owner.open().use { reader ->
                    val destination=byteArrayOf(42)
                    try{reader.readAt(0,destination,beforeDelivery=session::revoke);fail("Revoked result leaked")}
                    catch(_:IllegalStateException){}
                    assertArrayEquals(byteArrayOf(0),destination)
                }
            }
        } finally {root.deleteRecursively()}
    }

    @Test fun readerCloseDuringDeliveryDoesNotRevokeSibling() {
        val root=Files.createTempDirectory("synthetic-b1-reader-close").toFile()
        try {
            CapabilityObject.write(root,ByteArrayInputStream(byteArrayOf(4)),CapabilityObject.Session()).use {owner->
                owner.open().use{first->owner.open().use{second->
                    val output=byteArrayOf(12)
                    try{first.readAt(0,output,beforeDelivery=first::close);fail("Closed reader delivered bytes")}
                    catch(_:IllegalStateException){}
                    assertArrayEquals(byteArrayOf(0),output)
                    assertEquals(1,second.readAt(0,output));assertArrayEquals(byteArrayOf(4),output)
                }}
            }
        }finally{root.deleteRecursively()}
    }
    @Test fun externallyClosedLegacyOwnerCannotLeakThroughSafeAdapter() {
        val root=Files.createTempDirectory("synthetic-b1-external-close").toFile()
        try {
            ChunkObject.write(root,ChunkObject.Identity.fresh(),ByteArrayInputStream(byteArrayOf(5))).use{raw->
                CapabilityObject.adopt(raw,CapabilityObject.Session()).use{owner->owner.open().use{reader->
                    raw.close();val output=byteArrayOf(99)
                    try{reader.readAt(0,output);fail("Externally closed owner delivered content")}catch(_:IllegalStateException){}
                    assertArrayEquals(byteArrayOf(0),output)
                }}
            }
        }finally{root.deleteRecursively()}
    }

}
