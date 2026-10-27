package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase

/** Room21 is the downgrade fence. Existing stores retain their previous state until explicit activation.
 * No object/key, clinical fact, archive framing or quota is changed by this migration. */
internal object AttachmentActivationSchema {
    fun migrate(db:SupportSQLiteDatabase) {
        check(db.inTransaction())
        install(db, migrating=true)
    }
    fun install(db:SupportSQLiteDatabase,migrating:Boolean=false) {
        if(!migrating && db.version<21)return
        val names=listOf("guard_attachment_store_insert","guard_attachment_store_update")
        val statements=names.map {name->
            db.query("SELECT sql FROM sqlite_master WHERE type='trigger' AND name=?",arrayOf(name)).use {
                check(it.moveToFirst()) {"Missing attachment store guard"}
                val sql=it.getString(0)
                check("NEW.feature_enabled IN(0,1)" in sql) {"Unrecognized attachment activation guard"}
                name to sql.replace(" AND NEW.feature_enabled=0","")
            }
        }
        val own=!db.inTransaction()
        if(own)db.beginTransaction()
        try {
            statements.forEach {(name,sql)->db.execSQL("DROP TRIGGER `$name`");db.execSQL(sql)}
            if(own)db.setTransactionSuccessful()
        }finally {if(own)db.endTransaction()}
    }
    /** Caller already holds the existing authorized native transaction; no alternate authority. */
    fun activate(db:SupportSQLiteDatabase,expected:AttachmentIdentity.Scope) {
        check(db.inTransaction() && db.version>=21)
        check(AttachmentIdentity.current(db)==expected) {"Attachment dataset changed"}
        db.execSQL("UPDATE attachment_store SET feature_enabled=1 WHERE store_uuid=? AND record_epoch=? AND store_state='CURRENT' AND space='PRIMARY'",arrayOf(expected.dataset,expected.epoch))
        db.query("SELECT feature_enabled FROM attachment_store WHERE store_uuid=? AND record_epoch=?",arrayOf(expected.dataset,expected.epoch)).use {check(it.moveToFirst() && it.getInt(0)==1)}
    }
}
