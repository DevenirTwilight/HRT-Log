package net.plainnotes.app.data

import androidx.room.Dao
import androidx.room.Query

/** No unscoped metadata query and no file-verification API. Services pin CURRENT dataset/epoch. */
@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachment_store WHERE store_state='CURRENT' AND space='PRIMARY'")
    suspend fun current(): List<AttachmentStoreEntity>
    @Query("SELECT r.* FROM attachment_record_identity r JOIN attachment_store s ON s.store_uuid=r.store_uuid AND s.record_epoch=r.record_epoch WHERE r.store_uuid=:dataset AND r.record_epoch=:epoch AND r.target_kind=:kind AND r.legacy_id=:id AND r.state!='PURGED' AND s.store_state='CURRENT'")
    suspend fun identity(dataset:String, epoch:String, kind:String, id:Long): AttachmentRecordIdentityEntity?
    @Query("SELECT o.* FROM attachment_operation o JOIN attachment_store s ON s.store_uuid=o.store_uuid WHERE o.store_uuid=:dataset AND s.store_state='CURRENT' AND o.phase NOT IN('CLEANED','ABORTED')")
    suspend fun pending(dataset:String): List<AttachmentOperationEntity>
}
