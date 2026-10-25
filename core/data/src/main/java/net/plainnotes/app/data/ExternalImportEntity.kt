package net.plainnotes.app.data

import androidx.room.*

/** Source evidence, not a second current clinical record. IDs remain typed and local to this DB.
 * Each reported lab analyte has its own fragment, so purging one never retains its value inside
 * the evidence for another analyte. A source blood-test id/parent UUID still groups the draw. */
@Entity(tableName="external_import_item",primaryKeys=["source_app","source_kind","source_id","source_part"],
    foreignKeys=[ForeignKey(entity=RecordEntity::class,parentColumns=["id"],childColumns=["record_id"],onDelete=ForeignKey.SET_NULL),
        ForeignKey(entity=LabValueEntity::class,parentColumns=["id"],childColumns=["lab_id"],onDelete=ForeignKey.SET_NULL)],
    indices=[Index(value=["record_id"],unique=true),Index(value=["lab_id"],unique=true),Index(value=["panel_uuid"])])
data class ExternalImportEntity(
    val source_app:String="MONA",val source_kind:String,val source_id:String,val source_part:String="",
    val source_schema:Int=23,val app_version:String,val export_date:String,
    val source_digest:String,val source_json:String?,val target_digest:String,
    val record_id:Long?=null,val lab_id:Long?=null,val panel_uuid:String?=null,
    val imported_utc:Long,val retention:String="COMMITTED",
)
