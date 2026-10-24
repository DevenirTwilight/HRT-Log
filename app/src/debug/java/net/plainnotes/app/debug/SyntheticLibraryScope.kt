package net.plainnotes.app.debug

import net.plainnotes.app.data.NotesDatabase
import java.util.UUID

/** Debug synthetic fixtures only; no VERIFIED attachments or active library generation. */
internal fun syntheticLibraryScope(db:NotesDatabase):NotesDatabase {
    db.runInTransaction {
        val raw=db.openHelper.writableDatabase
        val exists=raw.query("SELECT 1 FROM attachment_store WHERE store_state='CURRENT'").use {it.moveToFirst()}
        if(!exists) {
            val dataset=UUID.randomUUID().toString()
            raw.execSQL("INSERT INTO attachment_store(store_uuid,logical_vault_uuid,store_state,space,record_epoch,generation,active_generation_uuid,feature_enabled) VALUES(?,?,'CURRENT','PRIMARY',?,1,NULL,0)",
                arrayOf(dataset,UUID.randomUUID().toString(),UUID.randomUUID().toString()))
            raw.execSQL("INSERT INTO attachment_generation(store_uuid,generation_uuid,state,manifest_digest,created_utc) VALUES(?,?,'STAGING',NULL,?)",arrayOf(dataset,UUID.randomUUID().toString(),System.currentTimeMillis()))
        }
    }
    return db
}
