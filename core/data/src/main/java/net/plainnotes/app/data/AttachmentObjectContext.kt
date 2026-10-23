package net.plainnotes.app.data

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.UUID

/** Content identity is portable. Local dataset/epoch are authorization, not content identity. */
internal data class AttachmentObjectContext(
    val logicalVault: UUID, val objectId: UUID, val version: UUID,
    val attempt: UUID, val dekHandle: UUID,
) {
    fun encode(): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out ->
            out.write("notes.attachment.object\u0000".toByteArray(Charsets.US_ASCII))
            out.writeShort(1); out.writeShort(1)
            listOf(logicalVault, objectId, version, attempt, dekHandle).forEach {
                out.writeLong(it.mostSignificantBits); out.writeLong(it.leastSignificantBits)
            }
        }
    }.toByteArray()
}
