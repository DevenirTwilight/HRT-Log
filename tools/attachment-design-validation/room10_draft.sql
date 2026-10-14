-- DESIGN ONLY: synthetic SQLite fixture, NOT a Room migration or production format.
-- Caller supplies random UUID scopes, pins PRIMARY, and wraps this entire DDL in one transaction.
CREATE TABLE attachment_store (
 store_uuid TEXT PRIMARY KEY NOT NULL CHECK(length(store_uuid)=36),
 space TEXT NOT NULL CHECK(space='PRIMARY'),
 record_epoch TEXT NOT NULL CHECK(length(record_epoch)=36),
 generation INTEGER NOT NULL CHECK(generation>=1),
 active_generation_uuid TEXT,
 feature_enabled INTEGER NOT NULL DEFAULT 0 CHECK(feature_enabled IN(0,1)),
 FOREIGN KEY(store_uuid,active_generation_uuid) REFERENCES attachment_generation(store_uuid,generation_uuid) DEFERRABLE INITIALLY DEFERRED,
 UNIQUE(store_uuid,record_epoch)
);
CREATE TABLE attachment_record_identity (
 store_uuid TEXT NOT NULL, record_epoch TEXT NOT NULL,
 record_uuid TEXT NOT NULL CHECK(length(record_uuid)=36),
 target_kind TEXT NOT NULL CHECK(target_kind IN('APPOINTMENT','LAB','DOSE_RECORD')),
 legacy_id INTEGER NOT NULL CHECK(legacy_id>0),
 state TEXT NOT NULL CHECK(state IN('ACTIVE','TRASHED','PURGED')),
 trash_id INTEGER, evidence_digest BLOB NOT NULL CHECK(length(evidence_digest)=32),
 PRIMARY KEY(store_uuid,record_epoch,record_uuid),
 FOREIGN KEY(store_uuid) REFERENCES attachment_store(store_uuid) ON DELETE RESTRICT,
 CHECK((state='TRASHED' AND trash_id IS NOT NULL) OR (state!='TRASHED' AND trash_id IS NULL))
);
CREATE UNIQUE INDEX attachment_identity_live_target ON attachment_record_identity(store_uuid,record_epoch,target_kind,legacy_id) WHERE state!='PURGED';
CREATE TABLE attachment_document (
 store_uuid TEXT NOT NULL, document_uuid TEXT NOT NULL CHECK(length(document_uuid)=36),
 revision INTEGER NOT NULL CHECK(revision>=1), title TEXT NOT NULL,
 category TEXT NOT NULL CHECK(category IN('REPORT','PRESCRIPTION','CERTIFICATE','RECORD','OTHER')),
 state TEXT NOT NULL CHECK(state IN('ACTIVE','TRASHED','PURGE_PENDING','PURGED')),
 active_version_uuid TEXT, deleted_utc INTEGER,
 PRIMARY KEY(store_uuid,document_uuid),
 FOREIGN KEY(store_uuid) REFERENCES attachment_store(store_uuid) ON DELETE RESTRICT,
 FOREIGN KEY(store_uuid,document_uuid,active_version_uuid) REFERENCES attachment_version(store_uuid,document_uuid,version_uuid) DEFERRABLE INITIALLY DEFERRED,
 CHECK((state='ACTIVE' AND deleted_utc IS NULL) OR (state!='ACTIVE' AND deleted_utc IS NOT NULL))
);
CREATE TABLE attachment_version (
 store_uuid TEXT NOT NULL, document_uuid TEXT NOT NULL, version_uuid TEXT NOT NULL CHECK(length(version_uuid)=36),
 ordinal INTEGER NOT NULL CHECK(ordinal>=1), object_uuid TEXT NOT NULL CHECK(length(object_uuid)=36),
 protocol_revision INTEGER NOT NULL CHECK(protocol_revision>=1), dek_handle_uuid TEXT NOT NULL CHECK(length(dek_handle_uuid)=36),
 plaintext_sha256 BLOB NOT NULL CHECK(length(plaintext_sha256)=32),
 plaintext_bytes INTEGER NOT NULL CHECK(plaintext_bytes BETWEEN 0 AND 16777216),
 ciphertext_bytes INTEGER NOT NULL CHECK(ciphertext_bytes>0), mime TEXT NOT NULL,
 status TEXT NOT NULL CHECK(status IN('STAGED','VERIFIED','MISSING','CORRUPT','PURGED')),
 PRIMARY KEY(store_uuid,document_uuid,version_uuid),
 UNIQUE(store_uuid,object_uuid), UNIQUE(store_uuid,document_uuid,ordinal),
 FOREIGN KEY(store_uuid,document_uuid) REFERENCES attachment_document(store_uuid,document_uuid) ON DELETE RESTRICT
);
CREATE TABLE attachment_link (
 store_uuid TEXT NOT NULL, link_uuid TEXT NOT NULL CHECK(length(link_uuid)=36),
 record_epoch TEXT NOT NULL, record_uuid TEXT NOT NULL, document_uuid TEXT NOT NULL,
 revision INTEGER NOT NULL CHECK(revision>=1), state TEXT NOT NULL CHECK(state IN('ACTIVE','RETRACTED','QUARANTINED')),
 PRIMARY KEY(store_uuid,link_uuid),
 FOREIGN KEY(store_uuid,record_epoch,record_uuid) REFERENCES attachment_record_identity(store_uuid,record_epoch,record_uuid) ON DELETE RESTRICT,
 FOREIGN KEY(store_uuid,document_uuid) REFERENCES attachment_document(store_uuid,document_uuid) ON DELETE RESTRICT
);
CREATE UNIQUE INDEX attachment_link_active_unique ON attachment_link(store_uuid,record_epoch,record_uuid,document_uuid) WHERE state='ACTIVE';
CREATE INDEX attachment_link_document ON attachment_link(store_uuid,document_uuid,state);
CREATE INDEX attachment_version_status ON attachment_version(store_uuid,status);
CREATE INDEX attachment_identity_target ON attachment_record_identity(store_uuid,record_epoch,target_kind,legacy_id,state);
CREATE TABLE attachment_operation (
 store_uuid TEXT NOT NULL, operation_uuid TEXT NOT NULL CHECK(length(operation_uuid)=36),
 idempotency_uuid TEXT NOT NULL CHECK(length(idempotency_uuid)=36),
 kind TEXT NOT NULL CHECK(kind IN('IMPORT','REPLACE','TRASH','RESTORE','PURGE','BACKUP','FULL_RESTORE','OLD_RESTORE')),
 phase TEXT NOT NULL CHECK(phase IN('PREPARED','WRITING','AUTHENTICATED','COMMITTED','CLEANED','ABORTED')),
 expected_generation INTEGER NOT NULL CHECK(expected_generation>=1), expected_document_revision INTEGER,
 document_uuid TEXT, reserved_bytes INTEGER NOT NULL CHECK(reserved_bytes>=0),
 reserved_objects INTEGER NOT NULL CHECK(reserved_objects>=0), updated_utc INTEGER NOT NULL CHECK(updated_utc>0),
 PRIMARY KEY(store_uuid,operation_uuid), UNIQUE(store_uuid,idempotency_uuid),
 FOREIGN KEY(store_uuid) REFERENCES attachment_store(store_uuid) ON DELETE RESTRICT,
 FOREIGN KEY(store_uuid,document_uuid) REFERENCES attachment_document(store_uuid,document_uuid) ON DELETE RESTRICT
);
CREATE INDEX attachment_operation_pending ON attachment_operation(store_uuid,phase);
CREATE TRIGGER attachment_record_identity_insert BEFORE INSERT ON attachment_record_identity WHEN NEW.state='ACTIVE' BEGIN
 SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM attachment_store WHERE store_uuid=NEW.store_uuid AND record_epoch=NEW.record_epoch) THEN RAISE(ABORT,'stale record epoch') END;
 SELECT CASE WHEN NOT ((NEW.target_kind='APPOINTMENT' AND EXISTS(SELECT 1 FROM appointment WHERE id=NEW.legacy_id)) OR (NEW.target_kind='LAB' AND EXISTS(SELECT 1 FROM lab_value WHERE id=NEW.legacy_id)) OR (NEW.target_kind='DOSE_RECORD' AND EXISTS(SELECT 1 FROM dose_record WHERE id=NEW.legacy_id AND deleted_at_utc IS NULL))) THEN RAISE(ABORT,'missing typed target') END;
END;
CREATE TRIGGER attachment_identity_immutable BEFORE UPDATE ON attachment_record_identity WHEN NEW.store_uuid!=OLD.store_uuid OR NEW.record_epoch!=OLD.record_epoch OR NEW.record_uuid!=OLD.record_uuid OR NEW.target_kind!=OLD.target_kind OR NEW.legacy_id!=OLD.legacy_id BEGIN SELECT RAISE(ABORT,'immutable identity'); END;
CREATE TRIGGER attachment_link_scope_insert BEFORE INSERT ON attachment_link WHEN NEW.state='ACTIVE' BEGIN
 SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM attachment_store s JOIN attachment_record_identity r ON r.store_uuid=s.store_uuid AND r.record_epoch=s.record_epoch WHERE s.store_uuid=NEW.store_uuid AND r.record_epoch=NEW.record_epoch AND r.record_uuid=NEW.record_uuid AND r.state='ACTIVE') THEN RAISE(ABORT,'inactive record scope') END;
 SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM attachment_document d JOIN attachment_version v ON v.store_uuid=d.store_uuid AND v.document_uuid=d.document_uuid AND v.version_uuid=d.active_version_uuid WHERE d.store_uuid=NEW.store_uuid AND d.document_uuid=NEW.document_uuid AND d.state='ACTIVE' AND v.status='VERIFIED') THEN RAISE(ABORT,'inactive document/version') END;
END;
CREATE TRIGGER attachment_link_scope_update BEFORE UPDATE ON attachment_link WHEN NEW.state='ACTIVE' BEGIN
 SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM attachment_store s JOIN attachment_record_identity r ON r.store_uuid=s.store_uuid AND r.record_epoch=s.record_epoch WHERE s.store_uuid=NEW.store_uuid AND r.record_epoch=NEW.record_epoch AND r.record_uuid=NEW.record_uuid AND r.state='ACTIVE') THEN RAISE(ABORT,'inactive record scope') END;
 SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM attachment_document d JOIN attachment_version v ON v.store_uuid=d.store_uuid AND v.document_uuid=d.document_uuid AND v.version_uuid=d.active_version_uuid WHERE d.store_uuid=NEW.store_uuid AND d.document_uuid=NEW.document_uuid AND d.state='ACTIVE' AND v.status='VERIFIED') THEN RAISE(ABORT,'inactive document/version') END;
END;
CREATE TRIGGER attachment_active_verified BEFORE UPDATE OF active_version_uuid ON attachment_document WHEN NEW.active_version_uuid IS NOT NULL BEGIN SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM attachment_version v WHERE v.store_uuid=NEW.store_uuid AND v.document_uuid=NEW.document_uuid AND v.version_uuid=NEW.active_version_uuid AND v.status='VERIFIED') THEN RAISE(ABORT,'unverified version') END; END;
CREATE TRIGGER attachment_no_active_insert BEFORE INSERT ON attachment_document WHEN NEW.active_version_uuid IS NOT NULL BEGIN SELECT RAISE(ABORT,'insert staged document first'); END;
-- No triggers attached to legacy tables. Identity/trash hooks are REQUIRED in the future repository transaction.
-- user_version is set by the synthetic runner only after all checks, never by this draft.
CREATE TRIGGER attachment_record_identity_restore BEFORE UPDATE OF state ON attachment_record_identity WHEN NEW.state='ACTIVE' BEGIN
 SELECT CASE WHEN OLD.state='PURGED' THEN RAISE(ABORT,'purged identity cannot restore') END;
 SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM attachment_store WHERE store_uuid=NEW.store_uuid AND record_epoch=NEW.record_epoch) THEN RAISE(ABORT,'stale record epoch') END;
 SELECT CASE WHEN NOT ((NEW.target_kind='APPOINTMENT' AND EXISTS(SELECT 1 FROM appointment WHERE id=NEW.legacy_id)) OR (NEW.target_kind='LAB' AND EXISTS(SELECT 1 FROM lab_value WHERE id=NEW.legacy_id)) OR (NEW.target_kind='DOSE_RECORD' AND EXISTS(SELECT 1 FROM dose_record WHERE id=NEW.legacy_id AND deleted_at_utc IS NULL))) THEN RAISE(ABORT,'missing typed target') END;
END;
CREATE TRIGGER attachment_version_identity BEFORE UPDATE ON attachment_version WHEN NEW.store_uuid!=OLD.store_uuid OR NEW.document_uuid!=OLD.document_uuid OR NEW.version_uuid!=OLD.version_uuid OR NEW.ordinal!=OLD.ordinal OR NEW.object_uuid!=OLD.object_uuid OR NEW.protocol_revision!=OLD.protocol_revision OR NEW.plaintext_bytes!=OLD.plaintext_bytes OR NEW.ciphertext_bytes!=OLD.ciphertext_bytes OR NEW.plaintext_sha256!=OLD.plaintext_sha256 OR NEW.dek_handle_uuid!=OLD.dek_handle_uuid OR NEW.mime!=OLD.mime BEGIN SELECT RAISE(ABORT,'immutable version bytes'); END;

CREATE TABLE attachment_generation (
 store_uuid TEXT NOT NULL, generation_uuid TEXT NOT NULL CHECK(length(generation_uuid)=36),
 state TEXT NOT NULL CHECK(state IN('STAGING','VALIDATED','RETIRED','PURGE_PENDING','PURGED')),
 manifest_digest BLOB CHECK(manifest_digest IS NULL OR length(manifest_digest)=32), created_utc INTEGER NOT NULL CHECK(created_utc>0),
 PRIMARY KEY(store_uuid,generation_uuid), FOREIGN KEY(store_uuid) REFERENCES attachment_store(store_uuid) ON DELETE RESTRICT,
 CHECK(state='STAGING' OR manifest_digest IS NOT NULL)
);
CREATE TRIGGER attachment_generation_activate BEFORE UPDATE OF active_generation_uuid ON attachment_store WHEN NEW.active_generation_uuid IS NOT NULL BEGIN
 SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM attachment_generation WHERE store_uuid=NEW.store_uuid AND generation_uuid=NEW.active_generation_uuid AND state='VALIDATED') THEN RAISE(ABORT,'unvalidated generation') END;
 SELECT CASE WHEN NEW.generation!=OLD.generation+1 THEN RAISE(ABORT,'generation CAS increment required') END;
END;
CREATE TRIGGER attachment_generation_identity BEFORE UPDATE ON attachment_generation WHEN NEW.store_uuid!=OLD.store_uuid OR NEW.generation_uuid!=OLD.generation_uuid OR NEW.created_utc!=OLD.created_utc OR (OLD.state!='STAGING' AND NEW.manifest_digest IS NOT OLD.manifest_digest) BEGIN SELECT RAISE(ABORT,'generation identity immutable'); END;
CREATE TABLE attachment_key_envelope (
 store_uuid TEXT NOT NULL, document_uuid TEXT NOT NULL, version_uuid TEXT NOT NULL,
 envelope_uuid TEXT NOT NULL CHECK(length(envelope_uuid)=36), dek_handle_uuid TEXT NOT NULL CHECK(length(dek_handle_uuid)=36),
 wrap_epoch INTEGER NOT NULL CHECK(wrap_epoch>=1), wrapping_key_alias TEXT NOT NULL CHECK(length(wrapping_key_alias)>0),
 wrapped_dek BLOB NOT NULL CHECK(length(wrapped_dek)>0), created_utc INTEGER NOT NULL CHECK(created_utc>0),
 state TEXT NOT NULL CHECK(state IN('AVAILABLE','TOMBSTONED')),
 PRIMARY KEY(store_uuid,envelope_uuid), UNIQUE(store_uuid,document_uuid,version_uuid,wrap_epoch),
 FOREIGN KEY(store_uuid,document_uuid,version_uuid) REFERENCES attachment_version(store_uuid,document_uuid,version_uuid) ON DELETE RESTRICT
);
CREATE TRIGGER attachment_envelope_handle BEFORE INSERT ON attachment_key_envelope BEGIN
 SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM attachment_version WHERE store_uuid=NEW.store_uuid AND document_uuid=NEW.document_uuid AND version_uuid=NEW.version_uuid AND dek_handle_uuid=NEW.dek_handle_uuid) THEN RAISE(ABORT,'wrong DEK handle') END;
END;
CREATE TRIGGER attachment_envelope_immutable BEFORE UPDATE ON attachment_key_envelope WHEN NEW.store_uuid!=OLD.store_uuid OR NEW.document_uuid!=OLD.document_uuid OR NEW.version_uuid!=OLD.version_uuid OR NEW.envelope_uuid!=OLD.envelope_uuid OR NEW.dek_handle_uuid!=OLD.dek_handle_uuid OR NEW.wrap_epoch!=OLD.wrap_epoch OR NEW.wrapping_key_alias!=OLD.wrapping_key_alias OR NEW.wrapped_dek!=OLD.wrapped_dek OR NEW.created_utc!=OLD.created_utc OR OLD.state='TOMBSTONED' OR NEW.state!='TOMBSTONED' BEGIN SELECT RAISE(ABORT,'append-only envelope'); END;
CREATE TRIGGER attachment_envelope_keep BEFORE DELETE ON attachment_key_envelope BEGIN SELECT RAISE(ABORT,'retain envelope tombstone'); END;
-- Irreversible logical tombstones: block multi-step revival and physical loss of identity history.
CREATE TRIGGER attachment_identity_tombstone BEFORE UPDATE ON attachment_record_identity WHEN OLD.state='PURGED' AND NEW.state!='PURGED' BEGIN SELECT RAISE(ABORT,'purged identity is terminal'); END;
CREATE TRIGGER attachment_identity_keep BEFORE DELETE ON attachment_record_identity BEGIN SELECT RAISE(ABORT,'identity history retained'); END;
CREATE TRIGGER attachment_link_keep BEFORE DELETE ON attachment_link BEGIN SELECT RAISE(ABORT,'link history retained'); END;
CREATE TRIGGER attachment_generation_no_restage BEFORE UPDATE ON attachment_generation WHEN OLD.state!='STAGING' AND NEW.state='STAGING' BEGIN SELECT RAISE(ABORT,'generation cannot return to staging'); END;
CREATE TRIGGER attachment_generation_terminal BEFORE UPDATE ON attachment_generation WHEN OLD.state='PURGED' AND NEW.state!='PURGED' BEGIN SELECT RAISE(ABORT,'purged generation is terminal'); END;
