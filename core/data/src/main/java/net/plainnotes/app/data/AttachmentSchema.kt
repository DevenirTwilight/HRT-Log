package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase

/** Reviewed B1 constraints: no feature enable or VERIFIED transition until B2/B3 approval.
 * Room cannot express CHECK or partial indexes; enforce equivalent predicates on create/reopen.
 * No legacy table/row is rewritten by this additive installation.
 */
internal object AttachmentSchema {
    val tables = listOf(
        """CREATE TABLE attachment_store (
 store_uuid TEXT PRIMARY KEY NOT NULL CHECK(length(store_uuid)=36),
 logical_vault_uuid TEXT NOT NULL CHECK(length(logical_vault_uuid)=36),
 store_state TEXT NOT NULL CHECK(store_state IN('STAGING','CURRENT','RETIRED')),
 space TEXT NOT NULL CHECK(space='PRIMARY'),
 record_epoch TEXT NOT NULL CHECK(length(record_epoch)=36),
 generation INTEGER NOT NULL CHECK(generation>=1),
 active_generation_uuid TEXT,
 feature_enabled INTEGER NOT NULL DEFAULT 0 CHECK(feature_enabled IN(0,1)),
 FOREIGN KEY(store_uuid,active_generation_uuid) REFERENCES attachment_generation(store_uuid,generation_uuid) DEFERRABLE INITIALLY DEFERRED,
 UNIQUE(store_uuid,record_epoch)
)""",
        """CREATE TABLE attachment_record_identity (
 store_uuid TEXT NOT NULL, record_epoch TEXT NOT NULL,
 record_uuid TEXT NOT NULL CHECK(length(record_uuid)=36),
 target_kind TEXT NOT NULL CHECK(target_kind IN('APPOINTMENT','LAB','DOSE_RECORD')),
 legacy_id INTEGER NOT NULL CHECK(legacy_id>0),
 state TEXT NOT NULL CHECK(state IN('ACTIVE','TRASHED','PURGED')),
 trash_id INTEGER, evidence_digest BLOB NOT NULL CHECK(length(evidence_digest)=32),
 PRIMARY KEY(store_uuid,record_epoch,record_uuid),
 FOREIGN KEY(store_uuid,record_epoch) REFERENCES attachment_store(store_uuid,record_epoch) ON DELETE RESTRICT,
 CHECK((state='TRASHED' AND trash_id IS NOT NULL) OR (state!='TRASHED' AND trash_id IS NULL))
)""",
        """CREATE TABLE attachment_document (
 store_uuid TEXT NOT NULL, document_uuid TEXT NOT NULL CHECK(length(document_uuid)=36),
 revision INTEGER NOT NULL CHECK(revision>=1), title TEXT NOT NULL,
 category TEXT NOT NULL CHECK(category IN('REPORT','PRESCRIPTION','CERTIFICATE','RECORD','OTHER')),
 state TEXT NOT NULL CHECK(state IN('ACTIVE','TRASHED','PURGE_PENDING','PURGED')),
 active_version_uuid TEXT, deleted_utc INTEGER,
 PRIMARY KEY(store_uuid,document_uuid),
 FOREIGN KEY(store_uuid) REFERENCES attachment_store(store_uuid) ON DELETE RESTRICT,
 FOREIGN KEY(store_uuid,document_uuid,active_version_uuid) REFERENCES attachment_version(store_uuid,document_uuid,version_uuid) DEFERRABLE INITIALLY DEFERRED,
 CHECK((state='ACTIVE' AND deleted_utc IS NULL) OR (state!='ACTIVE' AND deleted_utc IS NOT NULL))
)""",
        """CREATE TABLE attachment_version (
 store_uuid TEXT NOT NULL, document_uuid TEXT NOT NULL, version_uuid TEXT NOT NULL CHECK(length(version_uuid)=36),
 ordinal INTEGER NOT NULL CHECK(ordinal>=1), object_uuid TEXT NOT NULL CHECK(length(object_uuid)=36),
 protocol_revision INTEGER NOT NULL CHECK(protocol_revision>=1), dek_handle_uuid TEXT NOT NULL CHECK(length(dek_handle_uuid)=36),
 plaintext_sha256 BLOB NOT NULL CHECK(length(plaintext_sha256)=32),
 ciphertext_sha256 BLOB NOT NULL CHECK(length(ciphertext_sha256)=32),
 plaintext_bytes INTEGER NOT NULL CHECK(plaintext_bytes BETWEEN 0 AND 16777216),
 ciphertext_bytes INTEGER NOT NULL CHECK(ciphertext_bytes>0), mime TEXT NOT NULL,
 status TEXT NOT NULL CHECK(status IN('STAGED','VERIFIED','MISSING','CORRUPT','PURGED')),
 PRIMARY KEY(store_uuid,document_uuid,version_uuid),
 UNIQUE(store_uuid,object_uuid), UNIQUE(store_uuid,document_uuid,ordinal),
 FOREIGN KEY(store_uuid,document_uuid) REFERENCES attachment_document(store_uuid,document_uuid) ON DELETE RESTRICT
)""",
        """CREATE TABLE attachment_link (
 store_uuid TEXT NOT NULL, link_uuid TEXT NOT NULL CHECK(length(link_uuid)=36),
 record_epoch TEXT NOT NULL, record_uuid TEXT NOT NULL, document_uuid TEXT NOT NULL,
 revision INTEGER NOT NULL CHECK(revision>=1), state TEXT NOT NULL CHECK(state IN('ACTIVE','RETRACTED','QUARANTINED')),
 PRIMARY KEY(store_uuid,link_uuid),
 FOREIGN KEY(store_uuid,record_epoch,record_uuid) REFERENCES attachment_record_identity(store_uuid,record_epoch,record_uuid) ON DELETE RESTRICT,
 FOREIGN KEY(store_uuid,document_uuid) REFERENCES attachment_document(store_uuid,document_uuid) ON DELETE RESTRICT
)""",
        """CREATE TABLE attachment_operation (
 store_uuid TEXT NOT NULL, operation_uuid TEXT NOT NULL CHECK(length(operation_uuid)=36),
 idempotency_uuid TEXT NOT NULL CHECK(length(idempotency_uuid)=36),
 kind TEXT NOT NULL CHECK(kind IN('IMPORT','REPLACE','TRASH','RESTORE','PURGE','BACKUP','FULL_RESTORE','OLD_RESTORE')),
 phase TEXT NOT NULL CHECK(phase IN('PREPARED','WRITING','AUTHENTICATED','COMMITTED','CLEANED','ABORTED')),
 expected_generation INTEGER NOT NULL CHECK(expected_generation>=1), expected_document_revision INTEGER,
 document_uuid TEXT,
 resource_generation_uuid TEXT, object_uuid TEXT, version_uuid TEXT, dek_handle_uuid TEXT, staged_wrapped_dek BLOB, staged_wrapped_dek_sha256 BLOB,
 reserved_bytes INTEGER NOT NULL CHECK(reserved_bytes>=0),
 reserved_objects INTEGER NOT NULL CHECK(reserved_objects>=0), updated_utc INTEGER NOT NULL CHECK(updated_utc>0),
 PRIMARY KEY(store_uuid,operation_uuid), UNIQUE(store_uuid,idempotency_uuid),
 FOREIGN KEY(store_uuid) REFERENCES attachment_store(store_uuid) ON DELETE RESTRICT,
 FOREIGN KEY(store_uuid,document_uuid) REFERENCES attachment_document(store_uuid,document_uuid) ON DELETE RESTRICT,
 FOREIGN KEY(store_uuid,resource_generation_uuid) REFERENCES attachment_generation(store_uuid,generation_uuid) ON DELETE RESTRICT,
 CHECK(resource_generation_uuid IS NULL OR length(resource_generation_uuid)=36),
 CHECK(object_uuid IS NULL OR length(object_uuid)=36),
 CHECK(version_uuid IS NULL OR length(version_uuid)=36),
 CHECK(dek_handle_uuid IS NULL OR length(dek_handle_uuid)=36),
 CHECK(staged_wrapped_dek IS NULL OR length(staged_wrapped_dek) BETWEEN 1 AND 1024),
 CHECK(staged_wrapped_dek_sha256 IS NULL OR length(staged_wrapped_dek_sha256)=32),
 CHECK(staged_wrapped_dek IS NULL OR staged_wrapped_dek_sha256 IS NOT NULL),
 CHECK(kind NOT IN('IMPORT','REPLACE') OR phase NOT IN('WRITING','AUTHENTICATED','COMMITTED') OR (resource_generation_uuid IS NOT NULL AND object_uuid IS NOT NULL AND version_uuid IS NOT NULL AND dek_handle_uuid IS NOT NULL AND staged_wrapped_dek IS NOT NULL)),
 CHECK(kind NOT IN('IMPORT','REPLACE') OR phase!='CLEANED' OR (resource_generation_uuid IS NOT NULL AND object_uuid IS NOT NULL AND version_uuid IS NOT NULL AND dek_handle_uuid IS NOT NULL))
)""",
        """CREATE TABLE attachment_generation (
 store_uuid TEXT NOT NULL, generation_uuid TEXT NOT NULL CHECK(length(generation_uuid)=36),
 state TEXT NOT NULL CHECK(state IN('STAGING','VALIDATED','RETIRED','PURGE_PENDING','PURGED')),
 manifest_digest BLOB CHECK(manifest_digest IS NULL OR length(manifest_digest)=32), created_utc INTEGER NOT NULL CHECK(created_utc>0),
 PRIMARY KEY(store_uuid,generation_uuid), FOREIGN KEY(store_uuid) REFERENCES attachment_store(store_uuid) ON DELETE RESTRICT,
 CHECK(state='STAGING' OR manifest_digest IS NOT NULL)
)""",
        """CREATE TABLE attachment_key_envelope (
 store_uuid TEXT NOT NULL, document_uuid TEXT NOT NULL, version_uuid TEXT NOT NULL,
 envelope_uuid TEXT NOT NULL CHECK(length(envelope_uuid)=36), dek_handle_uuid TEXT NOT NULL CHECK(length(dek_handle_uuid)=36),
 wrap_epoch INTEGER NOT NULL CHECK(wrap_epoch>=1), wrapping_key_alias TEXT NOT NULL CHECK(length(wrapping_key_alias)>0),
 wrapped_dek BLOB NOT NULL CHECK(length(wrapped_dek)>0), created_utc INTEGER NOT NULL CHECK(created_utc>0),
 state TEXT NOT NULL CHECK(state IN('AVAILABLE','TOMBSTONED')),
 PRIMARY KEY(store_uuid,envelope_uuid), UNIQUE(store_uuid,document_uuid,version_uuid,wrap_epoch),
 FOREIGN KEY(store_uuid,document_uuid,version_uuid) REFERENCES attachment_version(store_uuid,document_uuid,version_uuid) ON DELETE RESTRICT
)""")
    private val indices = listOf(
        """CREATE UNIQUE INDEX index_attachment_store_space ON attachment_store(space) WHERE store_state='CURRENT'""",
        """CREATE UNIQUE INDEX index_attachment_store_store_uuid_record_epoch ON attachment_store(store_uuid,record_epoch)""",
        """CREATE INDEX index_attachment_store_store_uuid_active_generation_uuid ON attachment_store(store_uuid,active_generation_uuid)""",
        """CREATE INDEX index_attachment_record_identity_store_uuid_record_epoch_target_kind_legacy_id_state ON attachment_record_identity(store_uuid,record_epoch,target_kind,legacy_id,state)""",
        """CREATE UNIQUE INDEX index_attachment_record_identity_store_uuid_record_epoch_target_kind_legacy_id ON attachment_record_identity(store_uuid,record_epoch,target_kind,legacy_id) WHERE state!='PURGED'""",
        """CREATE INDEX index_attachment_document_store_uuid_document_uuid_active_version_uuid ON attachment_document(store_uuid,document_uuid,active_version_uuid)""",
        """CREATE INDEX index_attachment_version_store_uuid_status ON attachment_version(store_uuid,status)""",
        """CREATE UNIQUE INDEX index_attachment_version_store_uuid_document_uuid_ordinal ON attachment_version(store_uuid,document_uuid,ordinal)""",
        """CREATE UNIQUE INDEX index_attachment_version_store_uuid_object_uuid ON attachment_version(store_uuid,object_uuid)""",
        """CREATE INDEX index_attachment_link_store_uuid_document_uuid_state ON attachment_link(store_uuid,document_uuid,state)""",
        """CREATE UNIQUE INDEX index_attachment_link_store_uuid_record_epoch_record_uuid_document_uuid ON attachment_link(store_uuid,record_epoch,record_uuid,document_uuid) WHERE state='ACTIVE'""",
        """CREATE INDEX index_attachment_operation_store_uuid_phase ON attachment_operation(store_uuid,phase)""",
        """CREATE UNIQUE INDEX index_attachment_operation_store_uuid_idempotency_uuid ON attachment_operation(store_uuid,idempotency_uuid)""",
        """CREATE INDEX index_attachment_operation_store_uuid_resource_generation_uuid ON attachment_operation(store_uuid,resource_generation_uuid)""",
        """CREATE INDEX index_attachment_operation_store_uuid_document_uuid ON attachment_operation(store_uuid,document_uuid)""",
        """CREATE UNIQUE INDEX index_attachment_key_envelope_store_uuid_document_uuid_version_uuid_wrap_epoch ON attachment_key_envelope(store_uuid,document_uuid,version_uuid,wrap_epoch)""")
    private val protections = listOf(
        """DROP INDEX IF EXISTS index_attachment_store_space""",
        """CREATE UNIQUE INDEX index_attachment_store_space ON attachment_store(space) WHERE store_state='CURRENT'""",
        """CREATE TRIGGER guard_attachment_store_insert BEFORE INSERT ON attachment_store BEGIN SELECT CASE WHEN COALESCE(((length(NEW.store_uuid)=36) AND (length(NEW.logical_vault_uuid)=36) AND (NEW.store_state IN('STAGING','CURRENT','RETIRED')) AND (NEW.space='PRIMARY') AND (length(NEW.record_epoch)=36) AND (NEW.generation>=1) AND (NEW.feature_enabled IN(0,1)) AND NEW.feature_enabled=0),0)=0 THEN RAISE(ABORT,'Invalid attachment_store') END; END""",
        """CREATE TRIGGER guard_attachment_store_update BEFORE UPDATE ON attachment_store BEGIN SELECT CASE WHEN COALESCE(((length(NEW.store_uuid)=36) AND (length(NEW.logical_vault_uuid)=36) AND (NEW.store_state IN('STAGING','CURRENT','RETIRED')) AND (NEW.space='PRIMARY') AND (length(NEW.record_epoch)=36) AND (NEW.generation>=1) AND (NEW.feature_enabled IN(0,1)) AND NEW.feature_enabled=0),0)=0 THEN RAISE(ABORT,'Invalid attachment_store') END; END""",
        """DROP INDEX IF EXISTS index_attachment_record_identity_store_uuid_record_epoch_target_kind_legacy_id""",
        """CREATE UNIQUE INDEX index_attachment_record_identity_store_uuid_record_epoch_target_kind_legacy_id ON attachment_record_identity(store_uuid,record_epoch,target_kind,legacy_id) WHERE state!='PURGED'""",
        """CREATE TRIGGER guard_attachment_record_identity_insert BEFORE INSERT ON attachment_record_identity BEGIN SELECT CASE WHEN COALESCE(((length(NEW.record_uuid)=36) AND (NEW.target_kind IN('APPOINTMENT','LAB','DOSE_RECORD')) AND (NEW.legacy_id>0) AND (NEW.state IN('ACTIVE','TRASHED','PURGED')) AND (length(NEW.evidence_digest)=32) AND ((NEW.state='TRASHED' AND NEW.trash_id IS NOT NULL) OR (NEW.state!='TRASHED' AND NEW.trash_id IS NULL))),0)=0 THEN RAISE(ABORT,'Invalid attachment_record_identity') END; END""",
        """CREATE TRIGGER guard_attachment_record_identity_update BEFORE UPDATE ON attachment_record_identity BEGIN SELECT CASE WHEN COALESCE(((length(NEW.record_uuid)=36) AND (NEW.target_kind IN('APPOINTMENT','LAB','DOSE_RECORD')) AND (NEW.legacy_id>0) AND (NEW.state IN('ACTIVE','TRASHED','PURGED')) AND (length(NEW.evidence_digest)=32) AND ((NEW.state='TRASHED' AND NEW.trash_id IS NOT NULL) OR (NEW.state!='TRASHED' AND NEW.trash_id IS NULL))),0)=0 THEN RAISE(ABORT,'Invalid attachment_record_identity') END; END""",
        """CREATE TRIGGER guard_attachment_document_insert BEFORE INSERT ON attachment_document BEGIN SELECT CASE WHEN COALESCE(((length(NEW.document_uuid)=36) AND (NEW.revision>=1) AND (NEW.category IN('REPORT','PRESCRIPTION','CERTIFICATE','RECORD','OTHER')) AND (NEW.state IN('ACTIVE','TRASHED','PURGE_PENDING','PURGED')) AND ((NEW.state='ACTIVE' AND NEW.deleted_utc IS NULL) OR (NEW.state!='ACTIVE' AND NEW.deleted_utc IS NOT NULL))),0)=0 THEN RAISE(ABORT,'Invalid attachment_document') END; END""",
        """CREATE TRIGGER guard_attachment_document_update BEFORE UPDATE ON attachment_document BEGIN SELECT CASE WHEN COALESCE(((length(NEW.document_uuid)=36) AND (NEW.revision>=1) AND (NEW.category IN('REPORT','PRESCRIPTION','CERTIFICATE','RECORD','OTHER')) AND (NEW.state IN('ACTIVE','TRASHED','PURGE_PENDING','PURGED')) AND ((NEW.state='ACTIVE' AND NEW.deleted_utc IS NULL) OR (NEW.state!='ACTIVE' AND NEW.deleted_utc IS NOT NULL))),0)=0 THEN RAISE(ABORT,'Invalid attachment_document') END; END""",
        """CREATE TRIGGER guard_attachment_version_insert BEFORE INSERT ON attachment_version BEGIN SELECT CASE WHEN COALESCE(((length(NEW.version_uuid)=36) AND (NEW.ordinal>=1) AND (length(NEW.object_uuid)=36) AND (NEW.protocol_revision>=1) AND (length(NEW.dek_handle_uuid)=36) AND (length(NEW.plaintext_sha256)=32) AND (length(NEW.ciphertext_sha256)=32) AND (NEW.plaintext_bytes BETWEEN 0 AND 16777216) AND (NEW.ciphertext_bytes>0) AND (NEW.status IN('STAGED','VERIFIED','MISSING','CORRUPT','PURGED')) AND NEW.status!='VERIFIED'),0)=0 THEN RAISE(ABORT,'Invalid attachment_version') END; END""",
        """CREATE TRIGGER guard_attachment_version_update BEFORE UPDATE ON attachment_version BEGIN SELECT CASE WHEN COALESCE(((length(NEW.version_uuid)=36) AND (NEW.ordinal>=1) AND (length(NEW.object_uuid)=36) AND (NEW.protocol_revision>=1) AND (length(NEW.dek_handle_uuid)=36) AND (length(NEW.plaintext_sha256)=32) AND (length(NEW.ciphertext_sha256)=32) AND (NEW.plaintext_bytes BETWEEN 0 AND 16777216) AND (NEW.ciphertext_bytes>0) AND (NEW.status IN('STAGED','VERIFIED','MISSING','CORRUPT','PURGED')) AND NEW.status!='VERIFIED'),0)=0 THEN RAISE(ABORT,'Invalid attachment_version') END; END""",
        """DROP INDEX IF EXISTS index_attachment_link_store_uuid_record_epoch_record_uuid_document_uuid""",
        """CREATE UNIQUE INDEX index_attachment_link_store_uuid_record_epoch_record_uuid_document_uuid ON attachment_link(store_uuid,record_epoch,record_uuid,document_uuid) WHERE state='ACTIVE'""",
        """CREATE TRIGGER guard_attachment_link_insert BEFORE INSERT ON attachment_link BEGIN SELECT CASE WHEN COALESCE(((length(NEW.link_uuid)=36) AND (NEW.revision>=1) AND (NEW.state IN('ACTIVE','RETRACTED','QUARANTINED'))),0)=0 THEN RAISE(ABORT,'Invalid attachment_link') END; END""",
        """CREATE TRIGGER guard_attachment_link_update BEFORE UPDATE ON attachment_link BEGIN SELECT CASE WHEN COALESCE(((length(NEW.link_uuid)=36) AND (NEW.revision>=1) AND (NEW.state IN('ACTIVE','RETRACTED','QUARANTINED'))),0)=0 THEN RAISE(ABORT,'Invalid attachment_link') END; END""",
        """CREATE TRIGGER guard_attachment_operation_insert BEFORE INSERT ON attachment_operation BEGIN SELECT CASE WHEN COALESCE(((length(NEW.operation_uuid)=36) AND (length(NEW.idempotency_uuid)=36) AND (NEW.kind IN('IMPORT','REPLACE','TRASH','RESTORE','PURGE','BACKUP','FULL_RESTORE','OLD_RESTORE')) AND (NEW.phase IN('PREPARED','WRITING','AUTHENTICATED','COMMITTED','CLEANED','ABORTED')) AND (NEW.expected_generation>=1) AND (NEW.reserved_bytes>=0) AND (NEW.reserved_objects>=0) AND (NEW.updated_utc>0) AND (NEW.resource_generation_uuid IS NULL OR length(NEW.resource_generation_uuid)=36) AND (NEW.object_uuid IS NULL OR length(NEW.object_uuid)=36) AND (NEW.version_uuid IS NULL OR length(NEW.version_uuid)=36) AND (NEW.dek_handle_uuid IS NULL OR length(NEW.dek_handle_uuid)=36) AND (NEW.staged_wrapped_dek IS NULL OR length(NEW.staged_wrapped_dek) BETWEEN 1 AND 1024) AND (NEW.staged_wrapped_dek_sha256 IS NULL OR length(NEW.staged_wrapped_dek_sha256)=32) AND (NEW.staged_wrapped_dek IS NULL OR NEW.staged_wrapped_dek_sha256 IS NOT NULL) AND (NEW.kind NOT IN('IMPORT','REPLACE') OR NEW.phase NOT IN('WRITING','AUTHENTICATED','COMMITTED') OR (NEW.resource_generation_uuid IS NOT NULL AND NEW.object_uuid IS NOT NULL AND NEW.version_uuid IS NOT NULL AND NEW.dek_handle_uuid IS NOT NULL AND NEW.staged_wrapped_dek IS NOT NULL)) AND (NEW.kind NOT IN('IMPORT','REPLACE') OR NEW.phase!='CLEANED' OR (NEW.resource_generation_uuid IS NOT NULL AND NEW.object_uuid IS NOT NULL AND NEW.version_uuid IS NOT NULL AND NEW.dek_handle_uuid IS NOT NULL))),0)=0 THEN RAISE(ABORT,'Invalid attachment_operation') END; END""",
        """CREATE TRIGGER guard_attachment_operation_update BEFORE UPDATE ON attachment_operation BEGIN SELECT CASE WHEN COALESCE(((length(NEW.operation_uuid)=36) AND (length(NEW.idempotency_uuid)=36) AND (NEW.kind IN('IMPORT','REPLACE','TRASH','RESTORE','PURGE','BACKUP','FULL_RESTORE','OLD_RESTORE')) AND (NEW.phase IN('PREPARED','WRITING','AUTHENTICATED','COMMITTED','CLEANED','ABORTED')) AND (NEW.expected_generation>=1) AND (NEW.reserved_bytes>=0) AND (NEW.reserved_objects>=0) AND (NEW.updated_utc>0) AND (NEW.resource_generation_uuid IS NULL OR length(NEW.resource_generation_uuid)=36) AND (NEW.object_uuid IS NULL OR length(NEW.object_uuid)=36) AND (NEW.version_uuid IS NULL OR length(NEW.version_uuid)=36) AND (NEW.dek_handle_uuid IS NULL OR length(NEW.dek_handle_uuid)=36) AND (NEW.staged_wrapped_dek IS NULL OR length(NEW.staged_wrapped_dek) BETWEEN 1 AND 1024) AND (NEW.staged_wrapped_dek_sha256 IS NULL OR length(NEW.staged_wrapped_dek_sha256)=32) AND (NEW.staged_wrapped_dek IS NULL OR NEW.staged_wrapped_dek_sha256 IS NOT NULL) AND (NEW.kind NOT IN('IMPORT','REPLACE') OR NEW.phase NOT IN('WRITING','AUTHENTICATED','COMMITTED') OR (NEW.resource_generation_uuid IS NOT NULL AND NEW.object_uuid IS NOT NULL AND NEW.version_uuid IS NOT NULL AND NEW.dek_handle_uuid IS NOT NULL AND NEW.staged_wrapped_dek IS NOT NULL)) AND (NEW.kind NOT IN('IMPORT','REPLACE') OR NEW.phase!='CLEANED' OR (NEW.resource_generation_uuid IS NOT NULL AND NEW.object_uuid IS NOT NULL AND NEW.version_uuid IS NOT NULL AND NEW.dek_handle_uuid IS NOT NULL))),0)=0 THEN RAISE(ABORT,'Invalid attachment_operation') END; END""",
        """CREATE TRIGGER guard_attachment_generation_insert BEFORE INSERT ON attachment_generation BEGIN SELECT CASE WHEN COALESCE(((length(NEW.generation_uuid)=36) AND (NEW.state IN('STAGING','VALIDATED','RETIRED','PURGE_PENDING','PURGED')) AND (NEW.manifest_digest IS NULL OR length(NEW.manifest_digest)=32) AND (NEW.created_utc>0) AND (NEW.state='STAGING' OR NEW.manifest_digest IS NOT NULL) AND NEW.state!='VALIDATED'),0)=0 THEN RAISE(ABORT,'Invalid attachment_generation') END; END""",
        """CREATE TRIGGER guard_attachment_generation_update BEFORE UPDATE ON attachment_generation BEGIN SELECT CASE WHEN COALESCE(((length(NEW.generation_uuid)=36) AND (NEW.state IN('STAGING','VALIDATED','RETIRED','PURGE_PENDING','PURGED')) AND (NEW.manifest_digest IS NULL OR length(NEW.manifest_digest)=32) AND (NEW.created_utc>0) AND (NEW.state='STAGING' OR NEW.manifest_digest IS NOT NULL) AND NEW.state!='VALIDATED'),0)=0 THEN RAISE(ABORT,'Invalid attachment_generation') END; END""",
        """CREATE TRIGGER guard_attachment_key_envelope_insert BEFORE INSERT ON attachment_key_envelope BEGIN SELECT CASE WHEN COALESCE(((length(NEW.envelope_uuid)=36) AND (length(NEW.dek_handle_uuid)=36) AND (NEW.wrap_epoch>=1) AND (length(NEW.wrapping_key_alias)>0) AND (length(NEW.wrapped_dek)>0) AND (NEW.created_utc>0) AND (NEW.state IN('AVAILABLE','TOMBSTONED'))),0)=0 THEN RAISE(ABORT,'Invalid attachment_key_envelope') END; END""",
        """CREATE TRIGGER guard_attachment_key_envelope_update BEFORE UPDATE ON attachment_key_envelope BEGIN SELECT CASE WHEN COALESCE(((length(NEW.envelope_uuid)=36) AND (length(NEW.dek_handle_uuid)=36) AND (NEW.wrap_epoch>=1) AND (length(NEW.wrapping_key_alias)>0) AND (length(NEW.wrapped_dek)>0) AND (NEW.created_utc>0) AND (NEW.state IN('AVAILABLE','TOMBSTONED'))),0)=0 THEN RAISE(ABORT,'Invalid attachment_key_envelope') END; END""",
        """CREATE TRIGGER attachment_store_crypto_identity BEFORE UPDATE ON attachment_store WHEN NEW.store_uuid!=OLD.store_uuid OR NEW.logical_vault_uuid!=OLD.logical_vault_uuid OR NEW.space!=OLD.space BEGIN SELECT RAISE(ABORT,'immutable metadata crypto mapping'); END""",
        """CREATE TRIGGER attachment_record_identity_insert BEFORE INSERT ON attachment_record_identity WHEN NEW.state='ACTIVE' BEGIN
 SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM attachment_store WHERE store_uuid=NEW.store_uuid AND record_epoch=NEW.record_epoch AND store_state='CURRENT') THEN RAISE(ABORT,'stale record epoch') END;
 SELECT CASE WHEN NOT ((NEW.target_kind='APPOINTMENT' AND EXISTS(SELECT 1 FROM appointment WHERE id=NEW.legacy_id)) OR (NEW.target_kind='LAB' AND EXISTS(SELECT 1 FROM lab_value WHERE id=NEW.legacy_id)) OR (NEW.target_kind='DOSE_RECORD' AND EXISTS(SELECT 1 FROM dose_record WHERE id=NEW.legacy_id AND deleted_at_utc IS NULL AND status IN('ON_TIME','LATE') AND taken_utc IS NOT NULL AND actual_dose>0))) THEN RAISE(ABORT,'missing typed target') END;
END""",
        """CREATE TRIGGER attachment_identity_immutable BEFORE UPDATE ON attachment_record_identity WHEN NEW.store_uuid!=OLD.store_uuid OR NEW.record_epoch!=OLD.record_epoch OR NEW.record_uuid!=OLD.record_uuid OR NEW.target_kind!=OLD.target_kind OR NEW.legacy_id!=OLD.legacy_id BEGIN SELECT RAISE(ABORT,'immutable identity'); END""",
        """CREATE TRIGGER attachment_link_scope_insert BEFORE INSERT ON attachment_link WHEN NEW.state='ACTIVE' BEGIN
 SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM attachment_store s JOIN attachment_record_identity r ON r.store_uuid=s.store_uuid AND r.record_epoch=s.record_epoch WHERE s.store_uuid=NEW.store_uuid AND s.store_state='CURRENT' AND r.record_epoch=NEW.record_epoch AND r.record_uuid=NEW.record_uuid AND r.state='ACTIVE') THEN RAISE(ABORT,'inactive record scope') END;
 SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM attachment_document d JOIN attachment_version v ON v.store_uuid=d.store_uuid AND v.document_uuid=d.document_uuid AND v.version_uuid=d.active_version_uuid WHERE d.store_uuid=NEW.store_uuid AND d.document_uuid=NEW.document_uuid AND d.state='ACTIVE' AND v.status='VERIFIED') THEN RAISE(ABORT,'inactive document/version') END;
END""",
        """CREATE TRIGGER attachment_link_scope_update BEFORE UPDATE ON attachment_link WHEN NEW.state='ACTIVE' BEGIN
 SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM attachment_store s JOIN attachment_record_identity r ON r.store_uuid=s.store_uuid AND r.record_epoch=s.record_epoch WHERE s.store_uuid=NEW.store_uuid AND s.store_state='CURRENT' AND r.record_epoch=NEW.record_epoch AND r.record_uuid=NEW.record_uuid AND r.state='ACTIVE') THEN RAISE(ABORT,'inactive record scope') END;
 SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM attachment_document d JOIN attachment_version v ON v.store_uuid=d.store_uuid AND v.document_uuid=d.document_uuid AND v.version_uuid=d.active_version_uuid WHERE d.store_uuid=NEW.store_uuid AND d.document_uuid=NEW.document_uuid AND d.state='ACTIVE' AND v.status='VERIFIED') THEN RAISE(ABORT,'inactive document/version') END;
END""",
        """CREATE TRIGGER attachment_active_verified BEFORE UPDATE OF active_version_uuid ON attachment_document WHEN NEW.active_version_uuid IS NOT NULL BEGIN SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM attachment_version v WHERE v.store_uuid=NEW.store_uuid AND v.document_uuid=NEW.document_uuid AND v.version_uuid=NEW.active_version_uuid AND v.status='VERIFIED') THEN RAISE(ABORT,'unverified version') END; END""",
        """CREATE TRIGGER attachment_no_active_insert BEFORE INSERT ON attachment_document WHEN NEW.active_version_uuid IS NOT NULL BEGIN SELECT RAISE(ABORT,'insert staged document first'); END""",
        """CREATE TRIGGER attachment_record_identity_restore BEFORE UPDATE OF state ON attachment_record_identity WHEN NEW.state='ACTIVE' BEGIN
 SELECT CASE WHEN OLD.state='PURGED' THEN RAISE(ABORT,'purged identity cannot restore') END;
 SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM attachment_store WHERE store_uuid=NEW.store_uuid AND record_epoch=NEW.record_epoch AND store_state='CURRENT') THEN RAISE(ABORT,'stale record epoch') END;
 SELECT CASE WHEN NOT ((NEW.target_kind='APPOINTMENT' AND EXISTS(SELECT 1 FROM appointment WHERE id=NEW.legacy_id)) OR (NEW.target_kind='LAB' AND EXISTS(SELECT 1 FROM lab_value WHERE id=NEW.legacy_id)) OR (NEW.target_kind='DOSE_RECORD' AND EXISTS(SELECT 1 FROM dose_record WHERE id=NEW.legacy_id AND deleted_at_utc IS NULL AND status IN('ON_TIME','LATE') AND taken_utc IS NOT NULL AND actual_dose>0))) THEN RAISE(ABORT,'missing typed target') END;
END""",
        """CREATE TRIGGER attachment_version_identity BEFORE UPDATE ON attachment_version WHEN NEW.store_uuid!=OLD.store_uuid OR NEW.document_uuid!=OLD.document_uuid OR NEW.version_uuid!=OLD.version_uuid OR NEW.ordinal!=OLD.ordinal OR NEW.object_uuid!=OLD.object_uuid OR NEW.protocol_revision!=OLD.protocol_revision OR NEW.plaintext_bytes!=OLD.plaintext_bytes OR NEW.ciphertext_bytes!=OLD.ciphertext_bytes OR NEW.plaintext_sha256!=OLD.plaintext_sha256 OR NEW.ciphertext_sha256!=OLD.ciphertext_sha256 OR NEW.dek_handle_uuid!=OLD.dek_handle_uuid OR NEW.mime!=OLD.mime BEGIN SELECT RAISE(ABORT,'immutable version bytes'); END""",
        """CREATE TRIGGER attachment_generation_activate BEFORE UPDATE OF active_generation_uuid ON attachment_store WHEN NEW.active_generation_uuid IS NOT NULL BEGIN
 SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM attachment_generation WHERE store_uuid=NEW.store_uuid AND generation_uuid=NEW.active_generation_uuid AND state='VALIDATED') THEN RAISE(ABORT,'unvalidated generation') END;
 SELECT CASE WHEN NEW.generation!=OLD.generation+1 THEN RAISE(ABORT,'generation CAS increment required') END;
END""",
        """CREATE TRIGGER attachment_generation_identity BEFORE UPDATE ON attachment_generation WHEN NEW.store_uuid!=OLD.store_uuid OR NEW.generation_uuid!=OLD.generation_uuid OR NEW.created_utc!=OLD.created_utc OR (OLD.state!='STAGING' AND NEW.manifest_digest IS NOT OLD.manifest_digest) BEGIN SELECT RAISE(ABORT,'generation identity immutable'); END""",
        """CREATE TRIGGER attachment_envelope_handle BEFORE INSERT ON attachment_key_envelope BEGIN
 SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM attachment_version WHERE store_uuid=NEW.store_uuid AND document_uuid=NEW.document_uuid AND version_uuid=NEW.version_uuid AND dek_handle_uuid=NEW.dek_handle_uuid) THEN RAISE(ABORT,'wrong DEK handle') END;
END""",
        """CREATE TRIGGER attachment_envelope_immutable BEFORE UPDATE ON attachment_key_envelope WHEN NEW.store_uuid!=OLD.store_uuid OR NEW.document_uuid!=OLD.document_uuid OR NEW.version_uuid!=OLD.version_uuid OR NEW.envelope_uuid!=OLD.envelope_uuid OR NEW.dek_handle_uuid!=OLD.dek_handle_uuid OR NEW.wrap_epoch!=OLD.wrap_epoch OR NEW.wrapping_key_alias!=OLD.wrapping_key_alias OR NEW.wrapped_dek!=OLD.wrapped_dek OR NEW.created_utc!=OLD.created_utc OR OLD.state='TOMBSTONED' OR NEW.state!='TOMBSTONED' BEGIN SELECT RAISE(ABORT,'append-only envelope'); END""",
        """CREATE TRIGGER attachment_envelope_keep BEFORE DELETE ON attachment_key_envelope BEGIN SELECT RAISE(ABORT,'retain envelope tombstone'); END""",
        """CREATE TRIGGER attachment_identity_tombstone BEFORE UPDATE ON attachment_record_identity WHEN OLD.state='PURGED' AND NEW.state!='PURGED' BEGIN SELECT RAISE(ABORT,'purged identity is terminal'); END""",
        """CREATE TRIGGER attachment_identity_keep BEFORE DELETE ON attachment_record_identity BEGIN SELECT RAISE(ABORT,'identity history retained'); END""",
        """CREATE TRIGGER attachment_link_keep BEFORE DELETE ON attachment_link BEGIN SELECT RAISE(ABORT,'link history retained'); END""",
        """CREATE TRIGGER attachment_generation_no_restage BEFORE UPDATE ON attachment_generation WHEN OLD.state!='STAGING' AND NEW.state='STAGING' BEGIN SELECT RAISE(ABORT,'generation cannot return to staging'); END""",
        """CREATE TRIGGER attachment_generation_terminal BEFORE UPDATE ON attachment_generation WHEN OLD.state='PURGED' AND NEW.state!='PURGED' BEGIN SELECT RAISE(ABORT,'purged generation is terminal'); END""",
        """CREATE TRIGGER attachment_retired_store_terminal BEFORE UPDATE ON attachment_store WHEN OLD.store_state='RETIRED' AND NEW.store_state!='RETIRED' BEGIN SELECT RAISE(ABORT,'retired dataset is terminal'); END""",
        """CREATE TRIGGER attachment_same_crypto_version BEFORE INSERT ON attachment_version BEGIN
 SELECT CASE WHEN EXISTS(SELECT 1 FROM attachment_version v JOIN attachment_store oldscope ON oldscope.store_uuid=v.store_uuid JOIN attachment_store newscope ON newscope.store_uuid=NEW.store_uuid WHERE oldscope.logical_vault_uuid=newscope.logical_vault_uuid AND v.version_uuid=NEW.version_uuid AND (v.document_uuid!=NEW.document_uuid OR v.object_uuid!=NEW.object_uuid OR v.dek_handle_uuid!=NEW.dek_handle_uuid OR v.protocol_revision!=NEW.protocol_revision OR v.plaintext_sha256!=NEW.plaintext_sha256 OR v.ciphertext_sha256!=NEW.ciphertext_sha256 OR v.plaintext_bytes!=NEW.plaintext_bytes OR v.ciphertext_bytes!=NEW.ciphertext_bytes OR v.mime!=NEW.mime)) THEN RAISE(ABORT,'crypto version identity conflict') END;
END""",
        """CREATE TRIGGER attachment_operation_owned_resources BEFORE UPDATE ON attachment_operation WHEN OLD.phase!='PREPARED' AND (NEW.resource_generation_uuid IS NOT OLD.resource_generation_uuid OR NEW.object_uuid IS NOT OLD.object_uuid OR NEW.version_uuid IS NOT OLD.version_uuid OR NEW.dek_handle_uuid IS NOT OLD.dek_handle_uuid OR NEW.staged_wrapped_dek_sha256 IS NOT OLD.staged_wrapped_dek_sha256 OR (NEW.staged_wrapped_dek IS NOT OLD.staged_wrapped_dek AND NOT (OLD.staged_wrapped_dek IS NOT NULL AND NEW.staged_wrapped_dek IS NULL AND NEW.phase IN('CLEANED','ABORTED')))) BEGIN SELECT RAISE(ABORT,'operation resource ownership frozen'); END""",
        """CREATE TRIGGER attachment_operation_no_reprepare BEFORE UPDATE ON attachment_operation WHEN OLD.phase!='PREPARED' AND NEW.phase='PREPARED' BEGIN SELECT RAISE(ABORT,'operation cannot return to prepared'); END""",
        """CREATE TRIGGER attachment_operation_terminal BEFORE UPDATE ON attachment_operation WHEN OLD.phase IN('CLEANED','ABORTED') AND NEW.phase!=OLD.phase BEGIN SELECT RAISE(ABORT,'operation terminal'); END""",
        """CREATE TRIGGER attachment_operation_no_key_revival BEFORE UPDATE ON attachment_operation WHEN OLD.phase IN('CLEANED','ABORTED') AND OLD.staged_wrapped_dek IS NULL AND NEW.staged_wrapped_dek IS NOT NULL BEGIN SELECT RAISE(ABORT,'staging key cannot revive'); END""",
        """CREATE TRIGGER attachment_operation_identity BEFORE UPDATE ON attachment_operation WHEN NEW.store_uuid!=OLD.store_uuid OR NEW.operation_uuid!=OLD.operation_uuid OR NEW.idempotency_uuid!=OLD.idempotency_uuid OR NEW.kind!=OLD.kind OR NEW.expected_generation!=OLD.expected_generation OR NEW.document_uuid IS NOT OLD.document_uuid OR NEW.expected_document_revision IS NOT OLD.expected_document_revision BEGIN SELECT RAISE(ABORT,'immutable operation identity'); END""",
        """CREATE TRIGGER attachment_operation_phase BEFORE UPDATE OF phase ON attachment_operation WHEN NEW.phase!=OLD.phase AND NOT ((OLD.phase='PREPARED' AND NEW.phase IN('WRITING','ABORTED')) OR (OLD.phase='WRITING' AND NEW.phase='ABORTED')) BEGIN SELECT RAISE(ABORT,'B1 operation cannot attest or publish files'); END""",
        """CREATE TRIGGER attachment_operation_initial BEFORE INSERT ON attachment_operation WHEN NEW.phase!='PREPARED' BEGIN SELECT RAISE(ABORT,'operation starts prepared'); END""",
        """CREATE TRIGGER attachment_current_write_insert BEFORE INSERT ON attachment_document WHEN NOT EXISTS(SELECT 1 FROM attachment_store WHERE store_uuid=NEW.store_uuid AND store_state='CURRENT') BEGIN SELECT RAISE(ABORT,'inactive dataset'); END""",
        """CREATE TRIGGER attachment_store_epoch_immutable BEFORE UPDATE ON attachment_store WHEN NEW.record_epoch!=OLD.record_epoch BEGIN SELECT RAISE(ABORT,'epoch changes require fresh dataset'); END""")
    private val lifecycle = listOf(
        """CREATE TRIGGER attachment_version_terminal BEFORE UPDATE ON attachment_version WHEN OLD.status='PURGED' AND NEW.status!='PURGED' BEGIN SELECT RAISE(ABORT,'purged version is terminal'); END""",
        """CREATE TRIGGER attachment_version_keep BEFORE DELETE ON attachment_version BEGIN SELECT RAISE(ABORT,'version history retained'); END""",
        """CREATE TRIGGER attachment_document_identity BEFORE UPDATE ON attachment_document WHEN NEW.store_uuid!=OLD.store_uuid OR NEW.document_uuid!=OLD.document_uuid OR (OLD.state='PURGED' AND NEW.state!='PURGED') BEGIN SELECT RAISE(ABORT,'document identity terminal'); END""",
        """CREATE TRIGGER attachment_link_identity BEFORE UPDATE ON attachment_link WHEN NEW.store_uuid!=OLD.store_uuid OR NEW.link_uuid!=OLD.link_uuid OR NEW.record_epoch!=OLD.record_epoch OR NEW.record_uuid!=OLD.record_uuid OR NEW.document_uuid!=OLD.document_uuid BEGIN SELECT RAISE(ABORT,'immutable link identity'); END""",
        """CREATE TRIGGER attachment_generation_keep BEFORE DELETE ON attachment_generation BEGIN SELECT RAISE(ABORT,'generation history retained'); END""",
        """CREATE TRIGGER attachment_operation_keep BEFORE DELETE ON attachment_operation BEGIN SELECT RAISE(ABORT,'operation history retained'); END""",
        """CREATE TRIGGER attachment_store_keep BEFORE DELETE ON attachment_store BEGIN SELECT RAISE(ABORT,'dataset history retained'); END"""
    )
    fun migrate(db: SupportSQLiteDatabase) { tables.forEach(db::execSQL); indices.forEach(db::execSQL); install(db) }
    fun install(db: SupportSQLiteDatabase) {
        val exists=db.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name='attachment_store'").use { it.moveToFirst() }
        if(!exists)return // Legacy migration helper still exercises Room1..9.
        (protections+lifecycle).forEach { statement ->
            if(statement.startsWith("CREATE TRIGGER")) {
                val name=statement.split(' ')[2]
                db.execSQL("DROP TRIGGER IF EXISTS $name")
            }
            db.execSQL(statement)
        }
        AttachmentJournalSchema.install(db)
    }
}
