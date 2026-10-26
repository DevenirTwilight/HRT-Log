package net.plainnotes.app.data

import androidx.room.*

/** Separate UUID source namespace; no clinical ID or Mona receipt is reused as source identity. */
@Entity(tableName="featherline_import_item",primaryKeys=["source_kind","source_id"],
    foreignKeys=[ForeignKey(entity=RecordEntity::class,parentColumns=["id"],childColumns=["record_id"],onDelete=ForeignKey.SET_NULL),
        ForeignKey(entity=LabValueEntity::class,parentColumns=["id"],childColumns=["lab_id"],onDelete=ForeignKey.SET_NULL)],
    indices=[Index(value=["record_id"],unique=true),Index(value=["lab_id"],unique=true),Index(value=["source_panel_uuid"]),Index(value=["panel_uuid"])])
data class FeatherlineImportEntity(val source_kind:String,val source_id:String,val source_panel_uuid:String?=null,
    val source_schema:Int=7,val source_digest:String,val source_json:String?,val target_digest:String,
    val record_id:Long?=null,val lab_id:Long?=null,val panel_uuid:String?=null,
    val imported_utc:Long,val retention:String="COMMITTED")
