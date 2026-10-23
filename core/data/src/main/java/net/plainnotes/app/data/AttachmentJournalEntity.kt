package net.plainnotes.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Local protocol evidence. Absent row means UNKNOWN; never inferred from a legacy filename. */
@Entity(tableName="attachment_journal", primaryKeys=["store_uuid","operation_uuid"], indices=[
    Index(value=["store_uuid","object_uuid"],unique=true),
    Index(value=["store_uuid","version_uuid"],unique=true),
    Index(value=["stage_alias_uuid"],unique=true),
    Index(value=["active_alias_uuid"],unique=true),
    Index(value=["store_uuid","attempt_uuid"],unique=true)
], foreignKeys=[
    ForeignKey(entity=AttachmentOperationEntity::class,parentColumns=["store_uuid","operation_uuid"],
        childColumns=["store_uuid","operation_uuid"],onDelete=ForeignKey.RESTRICT)
])
data class AttachmentJournalEntity(
    val store_uuid: String,
    val operation_uuid: String,
    val record_epoch: String,
    val physical_generation: String,
    val document_uuid: String,
    val record_uuid: String,
    val request_sha256: ByteArray,
    val attempt_uuid: String,
    val object_uuid: String,
    val version_uuid: String,
    val dek_handle_uuid: String,
    val stage_alias_uuid: String,
    val active_alias_uuid: String,
    val stage_ready: Long,
    val active_ready: Long,
    val stage_uses: Long,
    val active_uses: Long,
    val proof: String,
    val parent_device: Long?,
    val parent_inode: Long?,
    val file_device: Long?,
    val file_inode: Long?,
    val plaintext_sha256: ByteArray?,
    val ciphertext_sha256: ByteArray?,
    val plaintext_bytes: Long?,
    val ciphertext_bytes: Long?
)
