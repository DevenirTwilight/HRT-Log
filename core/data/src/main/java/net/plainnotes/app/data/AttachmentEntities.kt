package net.plainnotes.app.data

import androidx.room.*

/** B1 metadata only. No file import or feature activation API. */

@Entity(tableName="attachment_store", primaryKeys=["store_uuid"], foreignKeys=[ForeignKey(entity=AttachmentGenerationEntity::class, parentColumns=["store_uuid","generation_uuid"], childColumns=["store_uuid","active_generation_uuid"], onDelete=ForeignKey.NO_ACTION, deferred=true)], indices=[Index(value=["space"], name="index_attachment_store_space", unique=true),
    Index(value=["store_uuid","record_epoch"], name="index_attachment_store_store_uuid_record_epoch", unique=true),
    Index(value=["store_uuid","active_generation_uuid"], name="index_attachment_store_store_uuid_active_generation_uuid", unique=false)])
data class AttachmentStoreEntity(
    val store_uuid: String,
    val logical_vault_uuid: String,
    val store_state: String,
    val space: String,
    val record_epoch: String,
    val generation: Long,
    val active_generation_uuid: String?,
    @ColumnInfo(defaultValue="0") val feature_enabled: Long
)


@Entity(tableName="attachment_record_identity", primaryKeys=["store_uuid","record_epoch","record_uuid"], foreignKeys=[ForeignKey(entity=AttachmentStoreEntity::class, parentColumns=["store_uuid","record_epoch"], childColumns=["store_uuid","record_epoch"], onDelete=ForeignKey.RESTRICT, deferred=false)], indices=[Index(value=["store_uuid","record_epoch","target_kind","legacy_id","state"], name="index_attachment_record_identity_store_uuid_record_epoch_target_kind_legacy_id_state", unique=false),
    Index(value=["store_uuid","record_epoch","target_kind","legacy_id"], name="index_attachment_record_identity_store_uuid_record_epoch_target_kind_legacy_id", unique=true)])
data class AttachmentRecordIdentityEntity(
    val store_uuid: String,
    val record_epoch: String,
    val record_uuid: String,
    val target_kind: String,
    val legacy_id: Long,
    val state: String,
    val trash_id: Long?,
    val evidence_digest: ByteArray
)


@Entity(tableName="attachment_document", primaryKeys=["store_uuid","document_uuid"], foreignKeys=[ForeignKey(entity=AttachmentVersionEntity::class, parentColumns=["store_uuid","document_uuid","version_uuid"], childColumns=["store_uuid","document_uuid","active_version_uuid"], onDelete=ForeignKey.NO_ACTION, deferred=true),
    ForeignKey(entity=AttachmentStoreEntity::class, parentColumns=["store_uuid"], childColumns=["store_uuid"], onDelete=ForeignKey.RESTRICT, deferred=false)], indices=[Index(value=["store_uuid","document_uuid","active_version_uuid"], name="index_attachment_document_store_uuid_document_uuid_active_version_uuid", unique=false)])
data class AttachmentDocumentEntity(
    val store_uuid: String,
    val document_uuid: String,
    val revision: Long,
    val title: String,
    val category: String,
    val state: String,
    val active_version_uuid: String?,
    val deleted_utc: Long?
)


@Entity(tableName="attachment_version", primaryKeys=["store_uuid","document_uuid","version_uuid"], foreignKeys=[ForeignKey(entity=AttachmentDocumentEntity::class, parentColumns=["store_uuid","document_uuid"], childColumns=["store_uuid","document_uuid"], onDelete=ForeignKey.RESTRICT, deferred=false)], indices=[Index(value=["store_uuid","status"], name="index_attachment_version_store_uuid_status", unique=false),
    Index(value=["store_uuid","document_uuid","ordinal"], name="index_attachment_version_store_uuid_document_uuid_ordinal", unique=true),
    Index(value=["store_uuid","object_uuid"], name="index_attachment_version_store_uuid_object_uuid", unique=true)])
data class AttachmentVersionEntity(
    val store_uuid: String,
    val document_uuid: String,
    val version_uuid: String,
    val ordinal: Long,
    val object_uuid: String,
    val protocol_revision: Long,
    val dek_handle_uuid: String,
    val plaintext_sha256: ByteArray,
    val ciphertext_sha256: ByteArray,
    val plaintext_bytes: Long,
    val ciphertext_bytes: Long,
    val mime: String,
    val status: String
)


@Entity(tableName="attachment_link", primaryKeys=["store_uuid","link_uuid"], foreignKeys=[ForeignKey(entity=AttachmentDocumentEntity::class, parentColumns=["store_uuid","document_uuid"], childColumns=["store_uuid","document_uuid"], onDelete=ForeignKey.RESTRICT, deferred=false),
    ForeignKey(entity=AttachmentRecordIdentityEntity::class, parentColumns=["store_uuid","record_epoch","record_uuid"], childColumns=["store_uuid","record_epoch","record_uuid"], onDelete=ForeignKey.RESTRICT, deferred=false)], indices=[Index(value=["store_uuid","document_uuid","state"], name="index_attachment_link_store_uuid_document_uuid_state", unique=false),
    Index(value=["store_uuid","record_epoch","record_uuid","document_uuid"], name="index_attachment_link_store_uuid_record_epoch_record_uuid_document_uuid", unique=true)])
data class AttachmentLinkEntity(
    val store_uuid: String,
    val link_uuid: String,
    val record_epoch: String,
    val record_uuid: String,
    val document_uuid: String,
    val revision: Long,
    val state: String
)


@Entity(tableName="attachment_operation", primaryKeys=["store_uuid","operation_uuid"], foreignKeys=[ForeignKey(entity=AttachmentGenerationEntity::class, parentColumns=["store_uuid","generation_uuid"], childColumns=["store_uuid","resource_generation_uuid"], onDelete=ForeignKey.RESTRICT, deferred=false),
    ForeignKey(entity=AttachmentDocumentEntity::class, parentColumns=["store_uuid","document_uuid"], childColumns=["store_uuid","document_uuid"], onDelete=ForeignKey.RESTRICT, deferred=false),
    ForeignKey(entity=AttachmentStoreEntity::class, parentColumns=["store_uuid"], childColumns=["store_uuid"], onDelete=ForeignKey.RESTRICT, deferred=false)], indices=[Index(value=["store_uuid","phase"], name="index_attachment_operation_store_uuid_phase", unique=false),
    Index(value=["store_uuid","idempotency_uuid"], name="index_attachment_operation_store_uuid_idempotency_uuid", unique=true),
    Index(value=["store_uuid","resource_generation_uuid"], name="index_attachment_operation_store_uuid_resource_generation_uuid", unique=false),
    Index(value=["store_uuid","document_uuid"], name="index_attachment_operation_store_uuid_document_uuid", unique=false)])
data class AttachmentOperationEntity(
    val store_uuid: String,
    val operation_uuid: String,
    val idempotency_uuid: String,
    val kind: String,
    val phase: String,
    val expected_generation: Long,
    val expected_document_revision: Long?,
    val document_uuid: String?,
    val resource_generation_uuid: String?,
    val object_uuid: String?,
    val version_uuid: String?,
    val dek_handle_uuid: String?,
    val staged_wrapped_dek: ByteArray?,
    val staged_wrapped_dek_sha256: ByteArray?,
    val reserved_bytes: Long,
    val reserved_objects: Long,
    val updated_utc: Long
)


@Entity(tableName="attachment_generation", primaryKeys=["store_uuid","generation_uuid"], foreignKeys=[ForeignKey(entity=AttachmentStoreEntity::class, parentColumns=["store_uuid"], childColumns=["store_uuid"], onDelete=ForeignKey.RESTRICT, deferred=false)], indices=[])
data class AttachmentGenerationEntity(
    val store_uuid: String,
    val generation_uuid: String,
    val state: String,
    val manifest_digest: ByteArray?,
    val created_utc: Long
)


@Entity(tableName="attachment_key_envelope", primaryKeys=["store_uuid","envelope_uuid"], foreignKeys=[ForeignKey(entity=AttachmentVersionEntity::class, parentColumns=["store_uuid","document_uuid","version_uuid"], childColumns=["store_uuid","document_uuid","version_uuid"], onDelete=ForeignKey.RESTRICT, deferred=false)], indices=[Index(value=["store_uuid","document_uuid","version_uuid","wrap_epoch"], name="index_attachment_key_envelope_store_uuid_document_uuid_version_uuid_wrap_epoch", unique=true)])
data class AttachmentKeyEnvelopeEntity(
    val store_uuid: String,
    val document_uuid: String,
    val version_uuid: String,
    val envelope_uuid: String,
    val dek_handle_uuid: String,
    val wrap_epoch: Long,
    val wrapping_key_alias: String,
    val wrapped_dek: ByteArray,
    val created_utc: Long,
    val state: String
)
