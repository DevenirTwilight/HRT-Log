package net.plainnotes.app.data

import androidx.room.*

/** Additive reference/measurement storage; existing clinical facts and legacy scalar stock remain separate. */
@Entity(tableName = "reference_dataset")
data class ReferenceDatasetEntity(
    @PrimaryKey val uuid: String,
    val created_utc: Long,
)

@Entity(tableName = "reference_runtime")
data class ReferenceRuntimeEntity(
    @PrimaryKey val id: Int,
    val dataset_uuid: String,
    val epoch: String,
)

@Entity(tableName = "reference_identity", foreignKeys = [ForeignKey(entity = ReferenceDatasetEntity::class, parentColumns = ["uuid"], childColumns = ["dataset_uuid"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = ReferenceIdentityEntity::class, parentColumns = ["uuid"], childColumns = ["owner_uuid"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["dataset_uuid", "kind", "live_handle"], unique = true), Index(value = ["owner_uuid"], unique = false)])
data class ReferenceIdentityEntity(
    @PrimaryKey val uuid: String,
    val dataset_uuid: String,
    val kind: String,
    val original_handle: Long,
    val live_handle: Long?,
    val owner_uuid: String?,
)

@Entity(tableName = "reference_snapshot", foreignKeys = [ForeignKey(entity = ReferenceDatasetEntity::class, parentColumns = ["uuid"], childColumns = ["dataset_uuid"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["dataset_uuid"], unique = false)])
data class ReferenceSnapshotEntity(
    @PrimaryKey val uuid: String,
    val dataset_uuid: String,
    val kind: String,
    val namespace: String,
    val source_version: String,
    val payload_json: String,
    val payload_sha256: String,
)

@Entity(tableName = "reference_event", foreignKeys = [ForeignKey(entity = ReferenceDatasetEntity::class, parentColumns = ["uuid"], childColumns = ["dataset_uuid"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = ReferenceIdentityEntity::class, parentColumns = ["uuid"], childColumns = ["subject_uuid"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = ReferenceEventEntity::class, parentColumns = ["operation_uuid"], childColumns = ["previous_uuid"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = ReferenceSnapshotEntity::class, parentColumns = ["uuid"], childColumns = ["snapshot_uuid"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["dataset_uuid"], unique = false), Index(value = ["subject_uuid", "revision"], unique = true), Index(value = ["previous_uuid"], unique = false), Index(value = ["snapshot_uuid"], unique = false)])
data class ReferenceEventEntity(
    @PrimaryKey val operation_uuid: String,
    val dataset_uuid: String,
    val subject_uuid: String,
    val revision: Int,
    val previous_uuid: String?,
    val action: String,
    val snapshot_uuid: String?,
    val request_sha256: String,
    val created_utc: Long,
)

@Entity(tableName = "record_reference_capture", foreignKeys = [ForeignKey(entity = ReferenceDatasetEntity::class, parentColumns = ["uuid"], childColumns = ["dataset_uuid"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = ReferenceIdentityEntity::class, parentColumns = ["uuid"], childColumns = ["record_uuid"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = ReferenceEventEntity::class, parentColumns = ["operation_uuid"], childColumns = ["context_event_uuid"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = ReferenceSnapshotEntity::class, parentColumns = ["uuid"], childColumns = ["actual_snapshot_uuid"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = ReferenceIdentityEntity::class, parentColumns = ["uuid"], childColumns = ["container_uuid"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["dataset_uuid"], unique = false), Index(value = ["record_uuid", "record_revision", "capture_revision"], unique = true), Index(value = ["context_event_uuid"], unique = false), Index(value = ["actual_snapshot_uuid"], unique = false), Index(value = ["container_uuid"], unique = false)])
data class RecordReferenceCaptureEntity(
    @PrimaryKey val uuid: String,
    val dataset_uuid: String,
    val record_uuid: String,
    val record_revision: Int,
    val capture_revision: Int,
    val kind: String,
    val context_event_uuid: String?,
    val actual_snapshot_uuid: String?,
    val container_uuid: String?,
    val payload_sha256: String,
)

@Entity(tableName = "measured_operation", foreignKeys = [ForeignKey(entity = ReferenceDatasetEntity::class, parentColumns = ["uuid"], childColumns = ["dataset_uuid"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = ReferenceIdentityEntity::class, parentColumns = ["uuid"], childColumns = ["record_uuid"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["dataset_uuid"], unique = false), Index(value = ["record_uuid"], unique = false)])
data class MeasuredOperationEntity(
    @PrimaryKey val operation_uuid: String,
    val dataset_uuid: String,
    val record_uuid: String?,
    val record_revision: Int?,
    val kind: String,
    val request_sha256: String,
    val input_json: String,
    val unallocated_quantity: String?,
    val unallocated_unit: String?,
    val unknown_reason: String?,
    val created_utc: Long,
)

@Entity(tableName = "measured_allocation", foreignKeys = [ForeignKey(entity = ReferenceDatasetEntity::class, parentColumns = ["uuid"], childColumns = ["dataset_uuid"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = MeasuredOperationEntity::class, parentColumns = ["operation_uuid"], childColumns = ["operation_uuid"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = ReferenceIdentityEntity::class, parentColumns = ["uuid"], childColumns = ["container_uuid"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = ReferenceEventEntity::class, parentColumns = ["operation_uuid"], childColumns = ["presentation_event_uuid"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = SupplyEntryEntity::class, parentColumns = ["id"], childColumns = ["ledger_entry_id"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = MeasuredAllocationEntity::class, parentColumns = ["uuid"], childColumns = ["reverses_uuid"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["dataset_uuid"], unique = false), Index(value = ["operation_uuid"], unique = false), Index(value = ["container_uuid"], unique = false), Index(value = ["presentation_event_uuid"], unique = false), Index(value = ["ledger_entry_id"], unique = true), Index(value = ["reverses_uuid"], unique = true)])
data class MeasuredAllocationEntity(
    @PrimaryKey val uuid: String,
    val dataset_uuid: String,
    val operation_uuid: String,
    val container_uuid: String,
    val presentation_event_uuid: String,
    val ledger_entry_id: Long,
    val quantity: String,
    val unit: String,
    val reverses_uuid: String?,
)
