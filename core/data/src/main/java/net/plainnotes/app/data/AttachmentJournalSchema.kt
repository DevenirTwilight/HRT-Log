package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase

/** Room14 additive local journal. No record-backup format or feature activation change. */
internal object AttachmentJournalSchema {
    val create = """CREATE TABLE attachment_journal (
 store_uuid TEXT NOT NULL,
 operation_uuid TEXT NOT NULL,
 record_epoch TEXT NOT NULL,
 physical_generation TEXT NOT NULL,
 document_uuid TEXT NOT NULL,
 record_uuid TEXT NOT NULL,
 request_sha256 BLOB NOT NULL CHECK(length(request_sha256)=32),
 attempt_uuid TEXT NOT NULL,
 object_uuid TEXT NOT NULL,
 version_uuid TEXT NOT NULL,
 dek_handle_uuid TEXT NOT NULL,
 stage_alias_uuid TEXT NOT NULL,
 active_alias_uuid TEXT NOT NULL,
 stage_ready INTEGER NOT NULL CHECK(stage_ready IN(0,1)),
 active_ready INTEGER NOT NULL CHECK(active_ready IN(0,1)),
 stage_uses INTEGER NOT NULL CHECK(stage_uses IN(0,1)),
 active_uses INTEGER NOT NULL CHECK(active_uses IN(0,1)),
 proof TEXT NOT NULL CHECK(proof IN('RESERVED','OWNED','AUTHENTICATED','COMMITTED','QUARANTINED')),
 parent_device INTEGER,
 parent_inode INTEGER,
 file_device INTEGER,
 file_inode INTEGER,
 plaintext_sha256 BLOB,
 ciphertext_sha256 BLOB,
 plaintext_bytes INTEGER,
 ciphertext_bytes INTEGER,
 PRIMARY KEY(store_uuid,operation_uuid),
 FOREIGN KEY(store_uuid,operation_uuid) REFERENCES attachment_operation(store_uuid,operation_uuid) ON DELETE RESTRICT,
 CHECK((proof IN('RESERVED','QUARANTINED')) OR (parent_device IS NOT NULL AND parent_inode IS NOT NULL AND file_device IS NOT NULL AND file_inode IS NOT NULL)),
 CHECK(COALESCE((proof NOT IN('AUTHENTICATED','COMMITTED')) OR (length(plaintext_sha256)=32 AND length(ciphertext_sha256)=32 AND plaintext_bytes BETWEEN 0 AND 16777216 AND ciphertext_bytes BETWEEN 1 AND 16842752 AND stage_ready=1 AND active_ready=1 AND stage_uses=1 AND active_uses=1),0))
)"""
    private val indices=listOf(
        "CREATE UNIQUE INDEX index_attachment_journal_store_uuid_object_uuid ON attachment_journal(store_uuid,object_uuid)",
        "CREATE UNIQUE INDEX index_attachment_journal_store_uuid_version_uuid ON attachment_journal(store_uuid,version_uuid)",
        "CREATE UNIQUE INDEX index_attachment_journal_stage_alias_uuid ON attachment_journal(stage_alias_uuid)",
        "CREATE UNIQUE INDEX index_attachment_journal_active_alias_uuid ON attachment_journal(active_alias_uuid)",
        "CREATE UNIQUE INDEX index_attachment_journal_store_uuid_attempt_uuid ON attachment_journal(store_uuid,attempt_uuid)"
    )
    fun migrate(db: SupportSQLiteDatabase) { db.execSQL(create); indices.forEach(db::execSQL); AttachmentSchema.install(db) }
    fun install(db: SupportSQLiteDatabase) {
        if (!db.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name='attachment_journal'").use { it.moveToFirst() }) return
        val predicates="""length(NEW.request_sha256)=32 AND NEW.stage_ready IN(0,1) AND NEW.active_ready IN(0,1) AND NEW.stage_uses IN(0,1) AND NEW.active_uses IN(0,1)
            AND NEW.proof IN('RESERVED','OWNED','AUTHENTICATED','COMMITTED','QUARANTINED')
            AND EXISTS(SELECT 1 FROM attachment_operation o JOIN attachment_store s ON s.store_uuid=o.store_uuid WHERE o.store_uuid=NEW.store_uuid AND o.operation_uuid=NEW.operation_uuid
                AND s.record_epoch=NEW.record_epoch AND o.resource_generation_uuid=NEW.physical_generation AND o.object_uuid=NEW.object_uuid AND o.version_uuid=NEW.version_uuid AND o.dek_handle_uuid=NEW.dek_handle_uuid)
            AND (NEW.proof IN('RESERVED','QUARANTINED') OR (NEW.parent_device IS NOT NULL AND NEW.parent_inode IS NOT NULL AND NEW.file_device IS NOT NULL AND NEW.file_inode IS NOT NULL))
            AND (NEW.proof NOT IN('AUTHENTICATED','COMMITTED') OR (length(NEW.plaintext_sha256)=32 AND length(NEW.ciphertext_sha256)=32 AND NEW.plaintext_bytes BETWEEN 0 AND 16777216 AND NEW.ciphertext_bytes BETWEEN 1 AND 16842752 AND NEW.stage_ready=1 AND NEW.active_ready=1 AND NEW.stage_uses=1 AND NEW.active_uses=1))"""
        for(op in listOf("insert","update")) {
            db.execSQL("DROP TRIGGER IF EXISTS guard_attachment_journal_$op")
            db.execSQL("CREATE TRIGGER guard_attachment_journal_$op BEFORE ${op.uppercase()} ON attachment_journal BEGIN SELECT CASE WHEN COALESCE(($predicates),0)=0 THEN RAISE(ABORT,'invalid local protocol evidence') END; END")
        }
        val statements = listOf(
            """CREATE TRIGGER attachment_journal_identity BEFORE UPDATE ON attachment_journal WHEN NEW.store_uuid IS NOT OLD.store_uuid OR NEW.operation_uuid IS NOT OLD.operation_uuid OR NEW.record_epoch IS NOT OLD.record_epoch OR NEW.physical_generation IS NOT OLD.physical_generation OR NEW.document_uuid IS NOT OLD.document_uuid OR NEW.record_uuid IS NOT OLD.record_uuid OR NEW.request_sha256 IS NOT OLD.request_sha256 OR NEW.attempt_uuid IS NOT OLD.attempt_uuid OR NEW.object_uuid IS NOT OLD.object_uuid OR NEW.version_uuid IS NOT OLD.version_uuid OR NEW.dek_handle_uuid IS NOT OLD.dek_handle_uuid OR NEW.stage_alias_uuid IS NOT OLD.stage_alias_uuid OR NEW.active_alias_uuid IS NOT OLD.active_alias_uuid BEGIN SELECT RAISE(ABORT,'immutable local protocol identity'); END""",
            """CREATE TRIGGER attachment_journal_keep BEFORE DELETE ON attachment_journal BEGIN SELECT RAISE(ABORT,'retain local protocol evidence'); END""",
            """CREATE TRIGGER attachment_journal_initial BEFORE INSERT ON attachment_journal WHEN NEW.proof!='RESERVED' OR NEW.stage_ready!=0 OR NEW.active_ready!=0 OR NEW.stage_uses!=0 OR NEW.active_uses!=0 OR NEW.file_inode IS NOT NULL OR NEW.parent_inode IS NOT NULL OR NEW.plaintext_sha256 IS NOT NULL BEGIN SELECT RAISE(ABORT,'journal starts unprovisioned'); END""",
            """CREATE TRIGGER attachment_journal_uses BEFORE UPDATE ON attachment_journal WHEN
                NEW.stage_ready<OLD.stage_ready OR NEW.active_ready<OLD.active_ready OR NEW.stage_uses<OLD.stage_uses OR NEW.active_uses<OLD.active_uses OR
                (NEW.stage_uses>OLD.stage_uses AND (NEW.stage_ready!=1 OR NEW.proof!='RESERVED')) OR
                (NEW.active_uses>OLD.active_uses AND (NEW.active_ready!=1 OR NEW.proof!='OWNED'))
                BEGIN SELECT RAISE(ABORT,'wrapping reservations cannot be refunded or reused'); END""",
            """CREATE TRIGGER attachment_journal_phase BEFORE UPDATE ON attachment_journal WHEN NEW.proof!=OLD.proof AND NOT (
                (OLD.proof='RESERVED' AND NEW.proof IN('OWNED','QUARANTINED')) OR
                (OLD.proof='OWNED' AND NEW.proof IN('AUTHENTICATED','QUARANTINED')) OR
                (OLD.proof='AUTHENTICATED' AND NEW.proof IN('COMMITTED','QUARANTINED')))
                BEGIN SELECT RAISE(ABORT,'invalid journal transition'); END""",
            """CREATE TRIGGER attachment_journal_evidence BEFORE UPDATE ON attachment_journal WHEN
                (OLD.parent_inode IS NOT NULL AND (NEW.parent_inode IS NOT OLD.parent_inode OR NEW.parent_device IS NOT OLD.parent_device OR NEW.file_inode IS NOT OLD.file_inode OR NEW.file_device IS NOT OLD.file_device)) OR
                (OLD.plaintext_sha256 IS NOT NULL AND (NEW.plaintext_sha256 IS NOT OLD.plaintext_sha256 OR NEW.ciphertext_sha256 IS NOT OLD.ciphertext_sha256 OR NEW.plaintext_bytes IS NOT OLD.plaintext_bytes OR NEW.ciphertext_bytes IS NOT OLD.ciphertext_bytes))
                BEGIN SELECT RAISE(ABORT,'frozen object evidence'); END"""
        )
        statements.forEach { sql -> db.execSQL("DROP TRIGGER IF EXISTS ${sql.split(' ')[2]}"); db.execSQL(sql) }
        // Preserve every B1 value/identity check; only replace its blanket readiness ban with evidence.
        for (operation in listOf("insert", "update")) {
            val name="guard_attachment_version_$operation"
            val original=db.query("SELECT sql FROM sqlite_master WHERE type='trigger' AND name=?", arrayOf(name)).use { check(it.moveToFirst());it.getString(0) }
            val predicate="""(NEW.status!='VERIFIED' OR EXISTS(SELECT 1 FROM attachment_journal j JOIN attachment_operation o ON o.store_uuid=j.store_uuid AND o.operation_uuid=j.operation_uuid
                WHERE j.store_uuid=NEW.store_uuid AND j.document_uuid=NEW.document_uuid AND j.version_uuid=NEW.version_uuid AND j.object_uuid=NEW.object_uuid AND j.dek_handle_uuid=NEW.dek_handle_uuid
                AND j.proof IN('AUTHENTICATED','COMMITTED') AND o.phase IN('AUTHENTICATED','COMMITTED') AND NEW.protocol_revision=1
                AND j.plaintext_sha256=NEW.plaintext_sha256 AND j.ciphertext_sha256=NEW.ciphertext_sha256 AND j.plaintext_bytes=NEW.plaintext_bytes AND j.ciphertext_bytes=NEW.ciphertext_bytes))"""
            check(original.contains("NEW.status!='VERIFIED'"))
            db.execSQL("DROP TRIGGER $name"); db.execSQL(original.replace("NEW.status!='VERIFIED'", predicate))
        }
        db.execSQL("DROP TRIGGER attachment_operation_phase")
        db.execSQL("""CREATE TRIGGER attachment_operation_phase BEFORE UPDATE OF phase ON attachment_operation WHEN NEW.phase!=OLD.phase AND NOT (
            (OLD.phase='PREPARED' AND NEW.phase IN('WRITING','ABORTED')) OR
            (OLD.phase IN('WRITING','AUTHENTICATED') AND NEW.phase='ABORTED') OR
            (OLD.phase='WRITING' AND NEW.phase='AUTHENTICATED' AND EXISTS(SELECT 1 FROM attachment_journal j WHERE j.store_uuid=NEW.store_uuid AND j.operation_uuid=NEW.operation_uuid AND j.proof='AUTHENTICATED')) OR
            (OLD.phase='AUTHENTICATED' AND NEW.phase='COMMITTED' AND EXISTS(SELECT 1 FROM attachment_journal j JOIN attachment_document d ON d.store_uuid=j.store_uuid AND d.document_uuid=j.document_uuid JOIN attachment_key_envelope k ON k.store_uuid=j.store_uuid AND k.version_uuid=j.version_uuid AND k.document_uuid=j.document_uuid WHERE j.store_uuid=NEW.store_uuid AND j.operation_uuid=NEW.operation_uuid AND j.proof='COMMITTED' AND d.active_version_uuid=j.version_uuid AND k.state='AVAILABLE')))
            BEGIN SELECT RAISE(ABORT,'missing authenticated local operation evidence'); END""")
    }
}
