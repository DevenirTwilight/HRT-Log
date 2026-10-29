package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase

/** Room23 (MED-04): optional, user-entered file details in a table of their own. Additive only: no existing row, trigger, key or object is touched. */
internal object MedicalFileDetailSchema {
    const val VERSION=23
    const val TABLE="attachment_document_detail"
    val categories=listOf("LAB_REPORT","PRESCRIPTION","VISIT_NOTE","CERTIFICATE","IMAGING","OTHER")
    const val INSTITUTION_MAX=80
    private val ddl="""CREATE TABLE `$TABLE` (`store_uuid` TEXT NOT NULL, `document_uuid` TEXT NOT NULL, `file_category` TEXT, `document_date` TEXT,
 `institution` TEXT, `detail_revision` INTEGER NOT NULL, `updated_utc` INTEGER NOT NULL, PRIMARY KEY(`store_uuid`, `document_uuid`),
 FOREIGN KEY(`store_uuid`, `document_uuid`) REFERENCES `attachment_document`(`store_uuid`, `document_uuid`) ON UPDATE NO ACTION ON DELETE RESTRICT )"""
    private val index="CREATE INDEX IF NOT EXISTS `index_attachment_document_detail_store_uuid_document_uuid` ON `$TABLE` (`store_uuid`, `document_uuid`)"
    fun migrate(db:SupportSQLiteDatabase) {
        check(db.inTransaction())
        db.execSQL(ddl);db.execSQL(index)
        install(db,true)
        db.query("PRAGMA foreign_key_check").use {check(!it.moveToFirst()) {"File detail migration violates foreign keys"}}
    }
    /** The same validity rule for every insert and update, however the row arrives (operation, restore, or a future path). */
    private val valid="""(NEW.detail_revision>=1 AND NEW.updated_utc>=0 AND length(NEW.document_uuid)=36
 AND (NEW.file_category IS NULL OR NEW.file_category IN(${categories.joinToString(",") {"'$it'"}}))
 AND (NEW.document_date IS NULL OR (length(NEW.document_date)=10 AND NEW.document_date GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]-[0-3][0-9]'
  AND CAST(substr(NEW.document_date,1,4) AS INTEGER)>=1900 AND CAST(substr(NEW.document_date,6,2) AS INTEGER) BETWEEN 1 AND 12
  AND CAST(substr(NEW.document_date,9,2) AS INTEGER) BETWEEN 1 AND CAST(strftime('%d',date(substr(NEW.document_date,1,8)||'01','+1 month','-1 day')) AS INTEGER)))
 AND (NEW.institution IS NULL OR (length(NEW.institution) BETWEEN 1 AND $INSTITUTION_MAX AND length(trim(NEW.institution))>0)))"""
    fun install(db:SupportSQLiteDatabase,migrating:Boolean=false) {
        if(!migrating && db.version<VERSION)return
        listOf("INSERT","UPDATE").forEach {op->
            val name="guard_medical_file_detail_${op.lowercase()}"
            db.execSQL("DROP TRIGGER IF EXISTS `$name`")
            db.execSQL("CREATE TRIGGER `$name` BEFORE $op ON $TABLE BEGIN SELECT CASE WHEN COALESCE($valid,0)=0 THEN RAISE(ABORT,'Invalid medical file detail') END; END")
        }
        db.execSQL("DROP TRIGGER IF EXISTS `medical_file_detail_revision`")
        db.execSQL("CREATE TRIGGER `medical_file_detail_revision` BEFORE UPDATE ON $TABLE WHEN NEW.detail_revision!=OLD.detail_revision+1 OR NEW.store_uuid!=OLD.store_uuid OR NEW.document_uuid!=OLD.document_uuid BEGIN SELECT RAISE(ABORT,'Detail revisions advance by one'); END")
        db.execSQL("DROP TRIGGER IF EXISTS `medical_file_detail_keep`")
        db.execSQL("CREATE TRIGGER `medical_file_detail_keep` BEFORE DELETE ON $TABLE BEGIN SELECT RAISE(ABORT,'File details are cleared, not deleted'); END")
    }
}
