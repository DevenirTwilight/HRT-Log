package net.plainnotes.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Local request receipts, not a second document store. Logical link history stays in attachment_link. */
@Entity(tableName="attachment_relation_operation",primaryKeys=["store_uuid","operation_uuid"],
    foreignKeys=[ForeignKey(entity=AttachmentStoreEntity::class,parentColumns=["store_uuid","record_epoch"],childColumns=["store_uuid","record_epoch"],onDelete=ForeignKey.RESTRICT),
        ForeignKey(entity=AttachmentDocumentEntity::class,parentColumns=["store_uuid","document_uuid"],childColumns=["store_uuid","document_uuid"],onDelete=ForeignKey.RESTRICT),
        ForeignKey(entity=AttachmentRecordIdentityEntity::class,parentColumns=["store_uuid","record_epoch","record_uuid"],childColumns=["store_uuid","record_epoch","record_uuid"],onDelete=ForeignKey.RESTRICT)],
    indices=[Index(value=["store_uuid","record_epoch","record_uuid"]),Index(value=["store_uuid","document_uuid"])])
data class AttachmentRelationOperationEntity(val store_uuid:String,val record_epoch:String,val operation_uuid:String,
    val kind:String,val target_kind:String,val legacy_id:Long,val record_uuid:String,val document_uuid:String,
    val expected_document_revision:Long,val expected_link_uuid:String?,val expected_link_revision:Long?,
    val payload_sha256:ByteArray,val phase:String,val result_link_uuid:String?,val result_revision:Long?)
