package net.plainnotes.app.data

import androidx.room.*

/** Immutable source-row identity; Chrysalide's numeric id is evidence, not a global key. */
@Entity(tableName="chrysalide_import_item",primaryKeys=["source_digest"],
    foreignKeys=[ForeignKey(entity=RecordEntity::class,parentColumns=["id"],childColumns=["record_id"],onDelete=ForeignKey.SET_NULL)],
    indices=[Index(value=["record_id"],unique=true)])
data class ChrysalideImportEntity(val source_digest:String,val source_id:String,val source_json:String?,val target_digest:String,
    val record_id:Long?,val imported_utc:Long,val retention:String="COMMITTED")
