package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase

/** Room22: actual panel identity and genuinely unassociated documents. No clinical/object/key rewrite.
 * Copies live rows using SQL inside the caller's migration transaction. No FK disabling/rename cascade.
 * Original published Room21 DDL and guards remain in AttachmentSchema for historical fixtures. */
internal object MedicalFilesSchema {
    const val VERSION=22
    val relationTable="""CREATE TABLE attachment_relation_operation (
 store_uuid TEXT NOT NULL,record_epoch TEXT NOT NULL,operation_uuid TEXT NOT NULL CHECK(length(operation_uuid)=36),
 kind TEXT NOT NULL CHECK(kind IN('LINK','UNLINK')),target_kind TEXT NOT NULL CHECK(target_kind IN('APPOINTMENT','LAB','LAB_PANEL')),
 legacy_id INTEGER NOT NULL CHECK(legacy_id>0),record_uuid TEXT NOT NULL,document_uuid TEXT NOT NULL,
 expected_document_revision INTEGER NOT NULL CHECK(expected_document_revision>0),expected_link_uuid TEXT,expected_link_revision INTEGER,
 payload_sha256 BLOB NOT NULL CHECK(length(payload_sha256)=32),phase TEXT NOT NULL CHECK(phase IN('PREPARED','COMMITTED','ABORTED')),
 result_link_uuid TEXT,result_revision INTEGER,
 PRIMARY KEY(store_uuid,operation_uuid),
 FOREIGN KEY(store_uuid,record_epoch) REFERENCES attachment_store(store_uuid,record_epoch) ON DELETE RESTRICT,
 FOREIGN KEY(store_uuid,document_uuid) REFERENCES attachment_document(store_uuid,document_uuid) ON DELETE RESTRICT,
 FOREIGN KEY(store_uuid,record_epoch,record_uuid) REFERENCES attachment_record_identity(store_uuid,record_epoch,record_uuid) ON DELETE RESTRICT
)"""
    private val relationIndices=listOf(
        "CREATE INDEX index_attachment_relation_operation_store_uuid_record_epoch_record_uuid ON attachment_relation_operation(store_uuid,record_epoch,record_uuid)",
        "CREATE INDEX index_attachment_relation_operation_store_uuid_document_uuid ON attachment_relation_operation(store_uuid,document_uuid)")
    fun migrate(db:SupportSQLiteDatabase,checkpoint:()->Unit={}) {
        check(db.inTransaction())
        val names=listOf("attachment_link","attachment_journal","attachment_record_identity")
        val triggers=db.query("SELECT name,sql FROM sqlite_master WHERE type='trigger' ORDER BY name").use {c->buildList {
            while(c.moveToNext())if(names.any {c.getString(1).contains(it)})add(c.getString(0) to c.getString(1))}}
        val indices=db.query("SELECT sql FROM sqlite_master WHERE type='index' AND tbl_name IN('attachment_link','attachment_journal','attachment_record_identity') AND sql IS NOT NULL ORDER BY name").use {c->buildList {while(c.moveToNext())add(c.getString(0))}}
        names.forEach {db.execSQL("CREATE TABLE med03_copy_$it AS SELECT * FROM $it")}
        triggers.forEach {(name,_)->db.execSQL("DROP TRIGGER `$name`")}
        names.forEach {db.execSQL("DROP TABLE `$it`")}
        checkpoint() // Same transaction rolls back copies, DDL and original data on failure.
        db.execSQL(AttachmentSchema.tables.single {it.startsWith("CREATE TABLE attachment_record_identity (")}
            .replace("'APPOINTMENT','LAB','DOSE_RECORD'","'APPOINTMENT','LAB','LAB_PANEL','DOSE_RECORD'"))
        db.execSQL(AttachmentSchema.tables.single {it.startsWith("CREATE TABLE attachment_link (")})
        db.execSQL(AttachmentJournalSchema.create.replace("record_uuid TEXT NOT NULL","record_uuid TEXT"))
        names.reversed().forEach {table->db.execSQL("INSERT INTO `$table` SELECT * FROM med03_copy_$table");db.execSQL("DROP TABLE med03_copy_$table")}
        indices.forEach(db::execSQL)
        triggers.forEach {(_,sql)->db.execSQL(sql)}
        db.execSQL(relationTable);relationIndices.forEach(db::execSQL)
        AttachmentSchema.install(db);AttachmentActivationSchema.install(db);install(db,true)
        db.query("PRAGMA foreign_key_check").use {check(!it.moveToFirst()) {"Medical file migration violates foreign keys"}}
    }
    fun install(db:SupportSQLiteDatabase,migrating:Boolean=false) {
        if(!migrating && db.version<VERSION)return
        val oldNames=listOf("guard_attachment_record_identity_insert","guard_attachment_record_identity_update",
            "attachment_record_identity_insert","attachment_record_identity_restore")
        oldNames.forEach {name->
            val old=db.query("SELECT sql FROM sqlite_master WHERE type='trigger' AND name=?",arrayOf(name)).use {check(it.moveToFirst());it.getString(0)}
            val updated=old.replace("'APPOINTMENT','LAB','DOSE_RECORD'","'APPOINTMENT','LAB','LAB_PANEL','DOSE_RECORD'")
                .replace("(NEW.target_kind='LAB' AND EXISTS", "(NEW.target_kind='LAB_PANEL' AND EXISTS(SELECT 1 FROM lab_panel WHERE id=NEW.legacy_id AND uuid=NEW.record_uuid)) OR (NEW.target_kind='LAB' AND EXISTS")
            db.execSQL("DROP TRIGGER `$name`");db.execSQL(updated)
        }
        fun guard(name:String,sql:String) {db.execSQL("DROP TRIGGER IF EXISTS `$name`");db.execSQL(sql)}
        guard("medical_panel_uuid_immutable","CREATE TRIGGER medical_panel_uuid_immutable BEFORE UPDATE OF uuid ON lab_panel WHEN NEW.uuid IS NOT OLD.uuid BEGIN SELECT RAISE(ABORT,'Stable blood draw identity'); END")
        guard("medical_panel_deleted","""CREATE TRIGGER medical_panel_deleted AFTER DELETE ON lab_panel BEGIN
 UPDATE attachment_link SET state='QUARANTINED',revision=revision+1 WHERE state='ACTIVE' AND record_uuid=OLD.uuid AND EXISTS(SELECT 1 FROM attachment_record_identity r WHERE r.store_uuid=attachment_link.store_uuid AND r.record_epoch=attachment_link.record_epoch AND r.record_uuid=OLD.uuid AND r.target_kind='LAB_PANEL' AND r.legacy_id=OLD.id);
 UPDATE attachment_record_identity SET state='PURGED',trash_id=NULL WHERE target_kind='LAB_PANEL' AND legacy_id=OLD.id AND record_uuid=OLD.uuid AND state='ACTIVE'; END""")
        guard("medical_link_terminal","CREATE TRIGGER medical_link_terminal BEFORE UPDATE OF state ON attachment_link WHEN OLD.state!='ACTIVE' AND NEW.state='ACTIVE' BEGIN SELECT RAISE(ABORT,'Reassociation needs a new authenticated explicit link'); END")
        guard("medical_unlinked_journal","""CREATE TRIGGER medical_unlinked_journal BEFORE INSERT ON attachment_journal WHEN NEW.record_uuid IS NULL BEGIN
 SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM attachment_operation o WHERE o.store_uuid=NEW.store_uuid AND o.operation_uuid=NEW.operation_uuid AND o.kind IN('IMPORT','FULL_RESTORE')) THEN RAISE(ABORT,'Unsupported independent object intent') END; END""")
        val relationValid="""length(NEW.operation_uuid)=36 AND NEW.kind IN('LINK','UNLINK') AND NEW.target_kind IN('APPOINTMENT','LAB','LAB_PANEL') AND NEW.legacy_id>0 AND length(NEW.record_uuid)=36 AND length(NEW.document_uuid)=36 AND NEW.expected_document_revision>0 AND length(NEW.payload_sha256)=32 AND NEW.phase IN('PREPARED','COMMITTED','ABORTED') AND ((NEW.expected_link_uuid IS NULL AND NEW.expected_link_revision IS NULL) OR (length(NEW.expected_link_uuid)=36 AND NEW.expected_link_revision>0)) AND ((NEW.phase='COMMITTED' AND length(NEW.result_link_uuid)=36 AND NEW.result_revision>0) OR (NEW.phase!='COMMITTED' AND NEW.result_link_uuid IS NULL AND NEW.result_revision IS NULL))"""
        listOf("INSERT","UPDATE").forEach {op->val name="guard_medical_relation_${op.lowercase()}";guard(name,"CREATE TRIGGER $name BEFORE $op ON attachment_relation_operation BEGIN SELECT CASE WHEN COALESCE(($relationValid),0)=0 THEN RAISE(ABORT,'Invalid relation receipt') END; END")}
        guard("medical_relation_initial","CREATE TRIGGER medical_relation_initial BEFORE INSERT ON attachment_relation_operation WHEN NEW.phase!='PREPARED' BEGIN SELECT RAISE(ABORT,'Relation starts prepared'); END")
        guard("medical_relation_source", """CREATE TRIGGER medical_relation_source BEFORE INSERT ON attachment_relation_operation BEGIN
 SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM attachment_store s JOIN attachment_record_identity r ON r.store_uuid=s.store_uuid AND r.record_epoch=s.record_epoch JOIN attachment_document d ON d.store_uuid=s.store_uuid
 WHERE s.store_uuid=NEW.store_uuid AND s.record_epoch=NEW.record_epoch AND s.store_state='CURRENT' AND r.record_uuid=NEW.record_uuid AND r.target_kind=NEW.target_kind AND r.legacy_id=NEW.legacy_id AND r.state='ACTIVE' AND d.document_uuid=NEW.document_uuid AND d.state='ACTIVE' AND d.revision=NEW.expected_document_revision) THEN RAISE(ABORT,'Unproven association source') END; END""")
        guard("medical_relation_committed", """CREATE TRIGGER medical_relation_committed BEFORE UPDATE ON attachment_relation_operation WHEN NEW.phase='COMMITTED' BEGIN
 SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM attachment_link l WHERE l.store_uuid=NEW.store_uuid AND l.record_epoch=NEW.record_epoch AND l.record_uuid=NEW.record_uuid AND l.document_uuid=NEW.document_uuid AND l.link_uuid=NEW.result_link_uuid AND l.revision=NEW.result_revision AND ((NEW.kind='LINK' AND l.state='ACTIVE') OR (NEW.kind='UNLINK' AND l.state='RETRACTED'))) THEN RAISE(ABORT,'Missing actual association commit') END; END""")
        val frozen=listOf("store_uuid","record_epoch","operation_uuid","kind","target_kind","legacy_id","record_uuid","document_uuid","expected_document_revision","expected_link_uuid","expected_link_revision","payload_sha256").joinToString(" OR "){"NEW.$it IS NOT OLD.$it"}
        guard("medical_relation_frozen","CREATE TRIGGER medical_relation_frozen BEFORE UPDATE ON attachment_relation_operation WHEN $frozen OR OLD.phase!='PREPARED' BEGIN SELECT RAISE(ABORT,'Immutable relation receipt'); END")
        guard("medical_relation_keep","CREATE TRIGGER medical_relation_keep BEFORE DELETE ON attachment_relation_operation BEGIN SELECT RAISE(ABORT,'Relation receipt retained'); END")
    }
}
