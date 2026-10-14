package net.plainnotes.poc

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files

/** Passing tests reproduce accepted POC gaps. They do NOT certify or fix production security. */
class SecurityReviewEvidenceTest {
    private fun fixture(block: (java.io.File) -> Unit) {
        val root = Files.createTempDirectory("synthetic-b0-review-").toFile()
        try { block(root) } finally { check(root.deleteRecursively()) }
    }
    @Test fun evidenceRevocationInFinalPublishSeamStillPublishes() = fixture { root ->
        var active = true
        ChunkObject.write(root, ChunkObject.Identity.fresh(), ByteArrayInputStream(byteArrayOf(7)),
            active = { active }, fault = { stage, _ -> if (stage == ChunkObject.Stage.RENAME) active = false }
        ).use { owned ->
            assertFalse(active)
            assertTrue("AUDIT FINDING: final publication is not atomic with revocation", owned.file.exists())
        }
    }
    @Test fun evidenceOwnerCloseDoesNotRevokePreviouslyOpenedReader() = fixture { root ->
        val owned = ChunkObject.write(root, ChunkObject.Identity.fresh(), ByteArrayInputStream(byteArrayOf(8)))
        owned.open().use { reader ->
            owned.close()
            val bytes = ByteArray(1)
            assertEquals("AUDIT FINDING: Reader has independent lifetime/key", 1, reader.readAt(0, bytes))
            assertArrayEquals(byteArrayOf(8), bytes)
        }
    }
    @Test fun evidenceCompleteOldTupleRemainsAuthenticButTrustedNewVersionRejects() = fixture { root ->
        val oldId = ChunkObject.Identity.fresh()
        val nextId = ChunkObject.Identity(oldId.store, oldId.objectId, java.util.UUID.randomUUID())
        ChunkObject.write(root, oldId, ByteArrayInputStream(byteArrayOf(1))).use { old ->
            ChunkObject.write(root, nextId, ByteArrayInputStream(byteArrayOf(2))).use { next ->
                old.open().use { reader ->
                    val bytes = ByteArray(1)
                    assertEquals(1, reader.readAt(0, bytes))
                    assertArrayEquals(byteArrayOf(1), bytes)
                }
                try { old.open(next.identity).use { fail("Old tuple must not match trusted new version") } }
                catch (expected: IllegalArgumentException) { /* Trusted version mismatch, not global rollback detection. */ }
            }
        }
    }
}
