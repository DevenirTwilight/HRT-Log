package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/** Sole persistent routing truth, in the original encrypted root database. No filesystem rename,
 * candidate READY flag or attachment_store row independently makes a library visible. */
internal object LibraryAuthority {
    data class Active(val generation:UUID,val alias:String,val device:Long,val inode:Long,val dataset:String,val epoch:String)
    fun current(db:SupportSQLiteDatabase):Active? {
        val id=db.query("SELECT active_generation FROM library_authority WHERE singleton=1").use {
            if(!it.moveToFirst() || it.isNull(0))null else it.getString(0)
        } ?: return null
        val uuid=UUID.fromString(id);check(uuid.toString()==id)
        return db.query("SELECT database_alias,directory_device,directory_inode,dataset_uuid,epoch_uuid,state FROM library_candidate WHERE generation_uuid=?",arrayOf(id)).use {
            check(it.moveToFirst() && it.getString(5)=="ACTIVE" && !it.isNull(1) && !it.isNull(2)) {"Incomplete active generation"}
            check(it.getString(0)=="notes.gen.db.$id") {"Unknown local key identity"}
            Active(uuid,it.getString(0),it.getLong(1),it.getLong(2),it.getString(3),it.getString(4))
        }
    }
    fun requireNoPending(db:SupportSQLiteDatabase) {
        db.query("SELECT 1 FROM library_candidate WHERE state IN('PREPARED','READY') LIMIT 1").use {
            if(it.moveToFirst())throw LibraryRestorePending()
        }
    }
    fun identity(db:SupportSQLiteDatabase)=current(db)?.generation?.toString() ?: "LEGACY"
}
