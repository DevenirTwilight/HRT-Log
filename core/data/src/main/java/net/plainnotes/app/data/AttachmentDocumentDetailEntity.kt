package net.plainnotes.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Room23 (MED-04): what the user typed about a file. Never inferred, never a clinical fact, never part of the original's authentication. */
@Entity(tableName="attachment_document_detail",primaryKeys=["store_uuid","document_uuid"],
    foreignKeys=[ForeignKey(entity=AttachmentDocumentEntity::class,parentColumns=["store_uuid","document_uuid"],childColumns=["store_uuid","document_uuid"],onDelete=ForeignKey.RESTRICT)],
    indices=[Index(value=["store_uuid","document_uuid"])])
data class AttachmentDocumentDetailEntity(val store_uuid:String,val document_uuid:String,val file_category:String?,val document_date:String?,
    val institution:String?,val detail_revision:Long,val updated_utc:Long)
