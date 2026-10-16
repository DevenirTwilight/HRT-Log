package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject
import java.util.UUID

/** One additive schema shared by product references and measured-stock provenance. */
internal object ReferenceSchema {
    val businessTables = listOf("reference_dataset", "reference_identity", "reference_snapshot", "reference_event", "record_reference_capture", "measured_operation", "measured_allocation")
    val columns = mapOf(
        "reference_dataset" to listOf("uuid","created_utc"),
        "reference_identity" to listOf("uuid","dataset_uuid","kind","original_handle","live_handle","owner_uuid"),
        "reference_snapshot" to listOf("uuid","dataset_uuid","kind","namespace","source_version","payload_json","payload_sha256"),
        "reference_event" to listOf("operation_uuid","dataset_uuid","subject_uuid","revision","previous_uuid","action","snapshot_uuid","request_sha256","created_utc"),
        "record_reference_capture" to listOf("uuid","dataset_uuid","record_uuid","record_revision","capture_revision","kind","context_event_uuid","actual_snapshot_uuid","container_uuid","payload_sha256"),
        "measured_operation" to listOf("operation_uuid","dataset_uuid","record_uuid","record_revision","kind","request_sha256","input_json","unallocated_quantity","unallocated_unit","unknown_reason","created_utc"),
        "measured_allocation" to listOf("uuid","dataset_uuid","operation_uuid","container_uuid","presentation_event_uuid","ledger_entry_id","quantity","unit","reverses_uuid"),
    )
    private val definitions = listOf(
        """CREATE TABLE IF NOT EXISTS `reference_dataset` (`uuid` TEXT NOT NULL, `created_utc` INTEGER NOT NULL, PRIMARY KEY (`uuid`))""",
        """CREATE TABLE IF NOT EXISTS `reference_runtime` (`id` INTEGER NOT NULL, `dataset_uuid` TEXT NOT NULL, `epoch` TEXT NOT NULL, PRIMARY KEY (`id`))""",
        """CREATE TABLE IF NOT EXISTS `reference_identity` (`uuid` TEXT NOT NULL, `dataset_uuid` TEXT NOT NULL, `kind` TEXT NOT NULL, `original_handle` INTEGER NOT NULL, `live_handle` INTEGER, `owner_uuid` TEXT, PRIMARY KEY (`uuid`), FOREIGN KEY (`dataset_uuid`) REFERENCES `reference_dataset` (`uuid`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY (`owner_uuid`) REFERENCES `reference_identity` (`uuid`) ON UPDATE NO ACTION ON DELETE RESTRICT)""",
        """CREATE UNIQUE INDEX IF NOT EXISTS `index_reference_identity_dataset_uuid_kind_live_handle` ON `reference_identity` (`dataset_uuid`, `kind`, `live_handle`)""",
        """CREATE INDEX IF NOT EXISTS `index_reference_identity_owner_uuid` ON `reference_identity` (`owner_uuid`)""",
        """CREATE TABLE IF NOT EXISTS `reference_snapshot` (`uuid` TEXT NOT NULL, `dataset_uuid` TEXT NOT NULL, `kind` TEXT NOT NULL, `namespace` TEXT NOT NULL, `source_version` TEXT NOT NULL, `payload_json` TEXT NOT NULL, `payload_sha256` TEXT NOT NULL, PRIMARY KEY (`uuid`), FOREIGN KEY (`dataset_uuid`) REFERENCES `reference_dataset` (`uuid`) ON UPDATE NO ACTION ON DELETE RESTRICT)""",
        """CREATE INDEX IF NOT EXISTS `index_reference_snapshot_dataset_uuid` ON `reference_snapshot` (`dataset_uuid`)""",
        """CREATE TABLE IF NOT EXISTS `reference_event` (`operation_uuid` TEXT NOT NULL, `dataset_uuid` TEXT NOT NULL, `subject_uuid` TEXT NOT NULL, `revision` INTEGER NOT NULL, `previous_uuid` TEXT, `action` TEXT NOT NULL, `snapshot_uuid` TEXT, `request_sha256` TEXT NOT NULL, `created_utc` INTEGER NOT NULL, PRIMARY KEY (`operation_uuid`), FOREIGN KEY (`dataset_uuid`) REFERENCES `reference_dataset` (`uuid`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY (`subject_uuid`) REFERENCES `reference_identity` (`uuid`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY (`previous_uuid`) REFERENCES `reference_event` (`operation_uuid`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY (`snapshot_uuid`) REFERENCES `reference_snapshot` (`uuid`) ON UPDATE NO ACTION ON DELETE RESTRICT)""",
        """CREATE INDEX IF NOT EXISTS `index_reference_event_dataset_uuid` ON `reference_event` (`dataset_uuid`)""",
        """CREATE UNIQUE INDEX IF NOT EXISTS `index_reference_event_subject_uuid_revision` ON `reference_event` (`subject_uuid`, `revision`)""",
        """CREATE INDEX IF NOT EXISTS `index_reference_event_previous_uuid` ON `reference_event` (`previous_uuid`)""",
        """CREATE INDEX IF NOT EXISTS `index_reference_event_snapshot_uuid` ON `reference_event` (`snapshot_uuid`)""",
        """CREATE TABLE IF NOT EXISTS `record_reference_capture` (`uuid` TEXT NOT NULL, `dataset_uuid` TEXT NOT NULL, `record_uuid` TEXT NOT NULL, `record_revision` INTEGER NOT NULL, `capture_revision` INTEGER NOT NULL, `kind` TEXT NOT NULL, `context_event_uuid` TEXT, `actual_snapshot_uuid` TEXT, `container_uuid` TEXT, `payload_sha256` TEXT NOT NULL, PRIMARY KEY (`uuid`), FOREIGN KEY (`dataset_uuid`) REFERENCES `reference_dataset` (`uuid`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY (`record_uuid`) REFERENCES `reference_identity` (`uuid`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY (`context_event_uuid`) REFERENCES `reference_event` (`operation_uuid`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY (`actual_snapshot_uuid`) REFERENCES `reference_snapshot` (`uuid`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY (`container_uuid`) REFERENCES `reference_identity` (`uuid`) ON UPDATE NO ACTION ON DELETE RESTRICT)""",
        """CREATE INDEX IF NOT EXISTS `index_record_reference_capture_dataset_uuid` ON `record_reference_capture` (`dataset_uuid`)""",
        """CREATE UNIQUE INDEX IF NOT EXISTS `index_record_reference_capture_record_uuid_record_revision_capture_revision` ON `record_reference_capture` (`record_uuid`, `record_revision`, `capture_revision`)""",
        """CREATE INDEX IF NOT EXISTS `index_record_reference_capture_context_event_uuid` ON `record_reference_capture` (`context_event_uuid`)""",
        """CREATE INDEX IF NOT EXISTS `index_record_reference_capture_actual_snapshot_uuid` ON `record_reference_capture` (`actual_snapshot_uuid`)""",
        """CREATE INDEX IF NOT EXISTS `index_record_reference_capture_container_uuid` ON `record_reference_capture` (`container_uuid`)""",
        """CREATE TABLE IF NOT EXISTS `measured_operation` (`operation_uuid` TEXT NOT NULL, `dataset_uuid` TEXT NOT NULL, `record_uuid` TEXT, `record_revision` INTEGER, `kind` TEXT NOT NULL, `request_sha256` TEXT NOT NULL, `input_json` TEXT NOT NULL, `unallocated_quantity` TEXT, `unallocated_unit` TEXT, `unknown_reason` TEXT, `created_utc` INTEGER NOT NULL, PRIMARY KEY (`operation_uuid`), FOREIGN KEY (`dataset_uuid`) REFERENCES `reference_dataset` (`uuid`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY (`record_uuid`) REFERENCES `reference_identity` (`uuid`) ON UPDATE NO ACTION ON DELETE RESTRICT)""",
        """CREATE INDEX IF NOT EXISTS `index_measured_operation_dataset_uuid` ON `measured_operation` (`dataset_uuid`)""",
        """CREATE INDEX IF NOT EXISTS `index_measured_operation_record_uuid` ON `measured_operation` (`record_uuid`)""",
        """CREATE TABLE IF NOT EXISTS `measured_allocation` (`uuid` TEXT NOT NULL, `dataset_uuid` TEXT NOT NULL, `operation_uuid` TEXT NOT NULL, `container_uuid` TEXT NOT NULL, `presentation_event_uuid` TEXT NOT NULL, `ledger_entry_id` INTEGER NOT NULL, `quantity` TEXT NOT NULL, `unit` TEXT NOT NULL, `reverses_uuid` TEXT, PRIMARY KEY (`uuid`), FOREIGN KEY (`dataset_uuid`) REFERENCES `reference_dataset` (`uuid`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY (`operation_uuid`) REFERENCES `measured_operation` (`operation_uuid`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY (`container_uuid`) REFERENCES `reference_identity` (`uuid`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY (`presentation_event_uuid`) REFERENCES `reference_event` (`operation_uuid`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY (`ledger_entry_id`) REFERENCES `supply_transaction` (`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY (`reverses_uuid`) REFERENCES `measured_allocation` (`uuid`) ON UPDATE NO ACTION ON DELETE RESTRICT)""",
        """CREATE INDEX IF NOT EXISTS `index_measured_allocation_dataset_uuid` ON `measured_allocation` (`dataset_uuid`)""",
        """CREATE INDEX IF NOT EXISTS `index_measured_allocation_operation_uuid` ON `measured_allocation` (`operation_uuid`)""",
        """CREATE INDEX IF NOT EXISTS `index_measured_allocation_container_uuid` ON `measured_allocation` (`container_uuid`)""",
        """CREATE INDEX IF NOT EXISTS `index_measured_allocation_presentation_event_uuid` ON `measured_allocation` (`presentation_event_uuid`)""",
        """CREATE UNIQUE INDEX IF NOT EXISTS `index_measured_allocation_ledger_entry_id` ON `measured_allocation` (`ledger_entry_id`)""",
        """CREATE UNIQUE INDEX IF NOT EXISTS `index_measured_allocation_reverses_uuid` ON `measured_allocation` (`reverses_uuid`)""",
    )
    fun migrate(db: SupportSQLiteDatabase) { definitions.forEach(db::execSQL) }
    private fun exists(db: SupportSQLiteDatabase) = db.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name='reference_dataset'").use { it.moveToFirst() }

    private val predicates = mapOf(
        "reference_dataset" to "length(NEW.uuid)=36 AND NEW.created_utc>=0",
        "reference_identity" to "length(NEW.uuid)=36 AND NEW.kind IN ('MEDICATION','RECORD','CONTAINER') AND NEW.original_handle>0 AND (NEW.live_handle IS NULL OR NEW.live_handle=NEW.original_handle) AND ((NEW.kind='MEDICATION' AND NEW.owner_uuid IS NULL AND (NEW.live_handle IS NULL OR EXISTS(SELECT 1 FROM medication WHERE id=NEW.live_handle))) OR (NEW.kind IN ('RECORD','CONTAINER') AND EXISTS(SELECT 1 FROM reference_identity o WHERE o.uuid=NEW.owner_uuid AND o.kind='MEDICATION' AND o.dataset_uuid=NEW.dataset_uuid AND (NEW.live_handle IS NULL OR (NEW.kind='RECORD' AND EXISTS(SELECT 1 FROM dose_record d WHERE d.id=NEW.live_handle AND d.medication_id=o.live_handle)) OR (NEW.kind='CONTAINER' AND EXISTS(SELECT 1 FROM supply_container c WHERE c.id=NEW.live_handle AND c.medication_id=o.live_handle))))))",
        "reference_snapshot" to "length(NEW.uuid)=36 AND NEW.kind IN ('PRODUCT','PRESENTATION') AND length(NEW.namespace)>0 AND length(NEW.source_version)>0 AND length(NEW.payload_json) BETWEEN 2 AND 65536 AND length(NEW.payload_sha256)=64",
        "reference_event" to "length(NEW.operation_uuid)=36 AND NEW.revision BETWEEN 1 AND 2147483647 AND NEW.action IN ('LINK','UNLINK','CORRECT') AND length(NEW.request_sha256)=64 AND NEW.created_utc>=0 AND EXISTS(SELECT 1 FROM reference_identity i WHERE i.uuid=NEW.subject_uuid AND i.dataset_uuid=NEW.dataset_uuid AND i.kind IN ('MEDICATION','CONTAINER')) AND ((NEW.action='UNLINK' AND NEW.snapshot_uuid IS NULL) OR (NEW.action IN ('LINK','CORRECT') AND EXISTS(SELECT 1 FROM reference_snapshot s JOIN reference_identity i ON i.uuid=NEW.subject_uuid WHERE s.uuid=NEW.snapshot_uuid AND s.dataset_uuid=NEW.dataset_uuid AND s.kind=CASE i.kind WHEN 'MEDICATION' THEN 'PRODUCT' ELSE 'PRESENTATION' END))) AND ((NEW.revision=1 AND NEW.previous_uuid IS NULL) OR EXISTS(SELECT 1 FROM reference_event p WHERE p.operation_uuid=NEW.previous_uuid AND p.subject_uuid=NEW.subject_uuid AND p.dataset_uuid=NEW.dataset_uuid AND p.revision=NEW.revision-1))",
        "record_reference_capture" to "length(NEW.uuid)=36 AND NEW.record_revision BETWEEN 1 AND 2147483647 AND NEW.capture_revision BETWEEN 1 AND 2147483647 AND length(NEW.payload_sha256)=64 AND NEW.kind IN ('CONTEXT','CONFIRM','CLEAR') AND EXISTS(SELECT 1 FROM reference_identity i WHERE i.uuid=NEW.record_uuid AND i.kind='RECORD' AND i.dataset_uuid=NEW.dataset_uuid AND (i.live_handle IS NULL OR EXISTS(SELECT 1 FROM dose_record d WHERE d.id=i.live_handle AND NEW.record_revision<=d.revision)) AND (NEW.context_event_uuid IS NULL OR EXISTS(SELECT 1 FROM reference_event e WHERE e.operation_uuid=NEW.context_event_uuid AND e.subject_uuid=i.owner_uuid AND e.dataset_uuid=NEW.dataset_uuid))) AND ((NEW.kind='CONFIRM' AND EXISTS(SELECT 1 FROM reference_snapshot s JOIN reference_identity c ON c.uuid=NEW.container_uuid JOIN reference_identity r ON r.uuid=NEW.record_uuid WHERE s.uuid=NEW.actual_snapshot_uuid AND s.kind='PRESENTATION' AND s.dataset_uuid=NEW.dataset_uuid AND c.kind='CONTAINER' AND c.dataset_uuid=NEW.dataset_uuid AND c.owner_uuid=r.owner_uuid)) OR (NEW.kind IN ('CONTEXT','CLEAR') AND NEW.actual_snapshot_uuid IS NULL AND NEW.container_uuid IS NULL))",
        "measured_operation" to "length(NEW.operation_uuid)=36 AND length(NEW.request_sha256)=64 AND NEW.kind IN ('CONSUME','REVERSE','ADJUST') AND NEW.created_utc>=0 AND ((NEW.kind='ADJUST' AND NEW.record_uuid IS NULL AND NEW.record_revision IS NULL) OR (NEW.kind IN ('CONSUME','REVERSE') AND NEW.record_revision>=1 AND EXISTS(SELECT 1 FROM reference_identity i WHERE i.uuid=NEW.record_uuid AND i.kind='RECORD' AND i.dataset_uuid=NEW.dataset_uuid)))",
        "measured_allocation" to "length(NEW.uuid)=36 AND length(NEW.quantity)>0 AND length(NEW.unit)>0 AND EXISTS(SELECT 1 FROM measured_operation o WHERE o.operation_uuid=NEW.operation_uuid AND o.dataset_uuid=NEW.dataset_uuid) AND EXISTS(SELECT 1 FROM reference_identity c JOIN supply_transaction t ON t.id=NEW.ledger_entry_id WHERE c.uuid=NEW.container_uuid AND c.kind='CONTAINER' AND c.dataset_uuid=NEW.dataset_uuid AND (c.live_handle=t.container_id OR c.live_handle IS NULL AND c.original_handle=t.container_id)) AND EXISTS(SELECT 1 FROM reference_event e WHERE e.operation_uuid=NEW.presentation_event_uuid AND e.subject_uuid=NEW.container_uuid AND e.dataset_uuid=NEW.dataset_uuid) AND (NEW.reverses_uuid IS NULL OR EXISTS(SELECT 1 FROM measured_allocation a WHERE a.uuid=NEW.reverses_uuid AND a.dataset_uuid=NEW.dataset_uuid AND a.container_uuid=NEW.container_uuid AND a.quantity=NEW.quantity AND a.unit=NEW.unit))"
    )
    fun install(db: SupportSQLiteDatabase) {
        if (!exists(db)) return // Older isolated migration fixtures have not reached this schema yet.
        businessTables.forEach { table ->
            db.execSQL("DROP TRIGGER IF EXISTS reference_guard_${table}")
            db.execSQL("CREATE TRIGGER reference_guard_${table} BEFORE INSERT ON `$table` BEGIN SELECT CASE WHEN COALESCE((${predicates.getValue(table)}),0)=0 THEN RAISE(ABORT,'Invalid reference state') END; END")
            if (table != "reference_identity") db.execSQL("CREATE TRIGGER IF NOT EXISTS reference_immutable_${table}_update BEFORE UPDATE ON `$table` BEGIN SELECT RAISE(ABORT,'Append-only reference state'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS reference_immutable_${table}_delete BEFORE DELETE ON `$table` BEGIN SELECT RAISE(ABORT,'Append-only reference state'); END")
        }
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS reference_identity_update BEFORE UPDATE ON reference_identity
            WHEN NOT(NEW.uuid=OLD.uuid AND NEW.dataset_uuid=OLD.dataset_uuid AND NEW.kind=OLD.kind AND NEW.original_handle=OLD.original_handle AND NEW.owner_uuid IS OLD.owner_uuid AND OLD.live_handle IS NOT NULL AND NEW.live_handle IS NULL AND
              NOT EXISTS(SELECT 1 FROM medication WHERE OLD.kind='MEDICATION' AND id=OLD.live_handle) AND
              NOT EXISTS(SELECT 1 FROM dose_record WHERE OLD.kind='RECORD' AND id=OLD.live_handle) AND
              NOT EXISTS(SELECT 1 FROM supply_container WHERE OLD.kind='CONTAINER' AND id=OLD.live_handle))
            BEGIN SELECT RAISE(ABORT,'Immutable reference identity'); END""")
        listOf("medication" to "MEDICATION", "dose_record" to "RECORD", "supply_container" to "CONTAINER").forEach { (table, kind) ->
            db.execSQL("CREATE TRIGGER IF NOT EXISTS reference_purge_$table AFTER DELETE ON `$table` BEGIN UPDATE reference_identity SET live_handle=NULL WHERE kind='$kind' AND live_handle=OLD.id; END")
        }
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS reference_event_append BEFORE INSERT ON reference_event
            WHEN NEW.revision != COALESCE((SELECT MAX(revision) FROM reference_event WHERE subject_uuid=NEW.subject_uuid),0)+1
            BEGIN SELECT RAISE(ABORT,'Stale reference revision'); END""")
    }
    fun retireRuntime(db: SupportSQLiteDatabase) { if (exists(db)) db.execSQL("DELETE FROM reference_runtime") }
    fun restoreRuntime(db: SupportSQLiteDatabase) {
        val dataset = db.query("SELECT uuid FROM reference_dataset").use { c -> if (c.moveToFirst()) c.getString(0).also { require(!c.moveToNext()) } else null }
        if (dataset != null) db.execSQL("INSERT INTO reference_runtime(id,dataset_uuid,epoch) VALUES(1,?,?)", arrayOf(dataset, UUID.randomUUID().toString()))
    }

    fun validate(db: SupportSQLiteDatabase) {
        if (!exists(db)) return
        db.query("SELECT COUNT(*) FROM reference_dataset").use { require(it.moveToFirst() && it.getInt(0) <= 1) }
        predicates.forEach { (table, predicate) ->
            db.query("SELECT 1 FROM `$table` AS NEW WHERE COALESCE(($predicate),0)=0 LIMIT 1").use { require(!it.moveToFirst()) { "Invalid reference state" } }
        }
        db.query("SELECT uuid FROM reference_dataset UNION ALL SELECT uuid FROM reference_identity UNION ALL SELECT uuid FROM reference_snapshot UNION ALL SELECT operation_uuid FROM reference_event UNION ALL SELECT uuid FROM record_reference_capture UNION ALL SELECT operation_uuid FROM measured_operation UNION ALL SELECT uuid FROM measured_allocation").use { c ->
            while(c.moveToNext()) require(ReferencePayload.uuid(c.getString(0)))
        }
        db.query("SELECT kind,namespace,source_version,payload_json,payload_sha256 FROM reference_snapshot").use { c -> while(c.moveToNext()) {
            val json = JSONObject(c.getString(3)); val canonical = ReferencePayload.canonical(json)
            require(canonical == c.getString(3) && ReferencePayload.sha(canonical) == c.getString(4))
            require(json.getString("kind") == c.getString(0) && json.getString("namespace") == c.getString(1) && json.getString("source_version") == c.getString(2))
            // P1 product linkage only. Presentation/measurement activation has its own reviewed P3/P4 validator.
            require(c.getString(0) == "PRODUCT") { "Presentation storage not yet enabled" }
            ReferencePayload.validateProduct(json)
        } }
        db.query("SELECT * FROM reference_event").use { c -> while(c.moveToNext()) {
            val event=ReferenceStore.row(db,"reference_event","operation_uuid=?",arrayOf(c.getString(c.getColumnIndexOrThrow("operation_uuid"))))!!
            val snapshot=ReferencePayload.optional(event,"snapshot_uuid")?.let { ReferenceStore.row(db,"reference_snapshot","uuid=?",arrayOf(it))!! }
            val payload=snapshot?.let { JSONObject(it.getString("payload_json")) }
            val previous=ReferencePayload.optional(event,"previous_uuid")?.let { ReferenceStore.row(db,"reference_event","operation_uuid=?",arrayOf(it))!! }
            val wasLinked=previous?.let { !it.isNull("snapshot_uuid") } ?: false
            val action=event.getString("action")
            require(if(payload==null) action=="UNLINK" && wasLinked else action==if(wasLinked) "CORRECT" else "LINK")
            require(ReferenceStore.requestHash(event.getString("dataset_uuid"),event.getString("subject_uuid"),event.getInt("revision")-1,action,payload)==event.getString("request_sha256"))
        } }
        db.query("SELECT * FROM record_reference_capture").use { c -> while(c.moveToNext()) {
            val row=ReferenceStore.row(db,"record_reference_capture","uuid=?",arrayOf(c.getString(c.getColumnIndexOrThrow("uuid"))))!!
            require(ReferenceStore.captureHash(row)==row.getString("payload_sha256"))
        } }
        db.query("SELECT 1 FROM measured_operation LIMIT 1").use { require(!it.moveToFirst()) { "Measured inventory not yet enabled" } }
        db.query("SELECT 1 FROM reference_event GROUP BY subject_uuid HAVING MIN(revision)!=1 OR COUNT(*)!=MAX(revision) LIMIT 1").use { require(!it.moveToFirst()) }
        db.query("SELECT 1 FROM record_reference_capture GROUP BY record_uuid,record_revision HAVING MIN(capture_revision)!=1 OR COUNT(*)!=MAX(capture_revision) LIMIT 1").use { require(!it.moveToFirst()) }
    }
}
