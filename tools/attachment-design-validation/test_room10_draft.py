"""Synthetic ONLY: stdlib SQLite validates draft DDL, not Android Room/SQLCipher."""
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[2]
SCHEMA = ROOT / 'core/data/schemas/net.plainnotes.app.data.NotesDatabase/9.json'
DDL = Path(__file__).with_name('room10_draft.sql').read_text()

def uid(): return str(uuid.uuid4())

def statements(script):
    buf = ''
    for line in script.splitlines(True):
        buf += line
        if sqlite3.complete_statement(buf):
            yield buf
            buf = ''
    if buf.strip(): raise ValueError('incomplete SQL')

def migrate(db, fail=False):
    db.execute('BEGIN IMMEDIATE')
    try:
        for sql in statements(DDL): db.execute(sql)
        if fail: raise OSError('synthetic migration interruption')
        assert not db.execute('PRAGMA foreign_key_check').fetchall()
        db.execute('PRAGMA user_version=10')
        db.commit()
    except BaseException:
        db.rollback()
        raise

def legacy():
    db = sqlite3.connect(':memory:')
    db.execute('PRAGMA foreign_keys=ON')
    s = json.loads(SCHEMA.read_text())['database']
    for e in s['entities']:
        db.execute(e['createSql'].replace('${TABLE_NAME}', e['tableName']))
        for i in e.get('indices', []): db.execute(i['createSql'].replace('${TABLE_NAME}', e['tableName']))
    for sql in s.get('setupQueries', []): db.execute(sql)
    # Read actual Kotlin predicates; reproduce every simple literal, not handwritten weakened guards.
    guards = (ROOT / 'core/data/src/main/java/net/plainnotes/app/data/SchemaGuards.kt').read_text()
    predicates = {}
    for table, predicate in re.findall(r'"(\w+)" to "([^\n]+)"[,\n]', guards):
        if '${' in predicate: continue  # milestone interpolation: explicit NOT_RUN, do not guess constants
        predicates[table] = predicate.replace('$finite', '1.7976931348623157e308')
    for table, predicate in predicates.items():
        for op in (['INSERT'] if table == 'supply_transaction' else ['INSERT','UPDATE']):
            db.execute(f"CREATE TRIGGER guard_{table}_{op.lower()} BEFORE {op} ON {table} BEGIN SELECT CASE WHEN COALESCE(({predicate}),0)=0 THEN RAISE(ABORT,'Invalid {table}') END; END")
    db.execute('DROP INDEX IF EXISTS index_dose_record_slot_key')
    db.execute('CREATE UNIQUE INDEX index_dose_record_slot_key ON dose_record(slot_key) WHERE deleted_at_utc IS NULL')
    for op in ['UPDATE','DELETE']:
        db.execute(f"CREATE TRIGGER immutable_supply_{op.lower()} BEFORE {op} ON supply_transaction BEGIN SELECT RAISE(ABORT,'Append-only supply ledger'); END")
    db.execute("INSERT INTO appointment VALUES(1,'OTHER',1000,'UTC','Synthetic hospital','Synthetic clinician','Synthetic note',0,1001)")
    # Schema contains analyte seed structure; populate solely synthetic analyte, no real measurements.
    e = next(e for e in s['entities'] if e['tableName']=='lab_analyte')
    vals = []
    for f in e['fields']:
        vals.append('SYNTH' if f['columnName']=='code' else ('synthetic' if f['affinity']=='TEXT' else 1))
    db.execute('INSERT INTO lab_analyte VALUES('+','.join('?' for _ in vals)+')',vals)
    db.execute("INSERT INTO lab_value VALUES(1,'SYNTH',1,'synthetic',1000,'UTC',NULL,NULL,NULL,'Synthetic lab','Synthetic note')")
    db.execute("INSERT INTO medication VALUES(1,'Synthetic','Synthetic',NULL,'synthetic',1,30,NULL,NULL,NULL,0,NULL,0,1,0,NULL)")
    db.execute("INSERT INTO supply_container VALUES(1,1,30,0,0,NULL,'SEALED','Synthetic',NULL)")
    db.execute("INSERT INTO supply_transaction VALUES(1,1,NULL,NULL,'synthetic-operation','ADJUST',1,NULL,1000,'UTC','Synthetic')")
    db.execute("INSERT INTO trash_item VALUES(1,'APPOINTMENT','2','2000-01-01',1000,'{}','TRASHED')")
    db.execute('PRAGMA user_version=9'); db.commit()
    return db

def fingerprint(db):
    names = [e['tableName'] for e in json.loads(SCHEMA.read_text())['database']['entities']]
    data = {n:db.execute(f'SELECT * FROM `{n}` ORDER BY rowid').fetchall() for n in names}
    return hashlib.sha256(json.dumps(data, sort_keys=True).encode()).hexdigest()

class Room10Draft(unittest.TestCase):
    def setUp(self):
        self.db = legacy(); self.before = fingerprint(self.db)
        self.objects = dict(self.db.execute("SELECT name,sql FROM sqlite_master WHERE sql IS NOT NULL"))
        migrate(self.db)
        self.store,self.epoch,self.record,self.doc = uid(),uid(),uid(),uid()
        self.db.execute("INSERT INTO attachment_store(store_uuid,logical_vault_uuid,store_state,space,record_epoch,generation,feature_enabled) VALUES(?,?,'CURRENT',?,?,?,0)",(self.store,uid(),'PRIMARY',self.epoch,1))
        self.identity(self.record)
        self.db.execute("INSERT INTO attachment_document VALUES(?,?,1,'Synthetic','REPORT','ACTIVE',NULL,NULL)",(self.store,self.doc))
        verified=self.version('VERIFIED')
        self.db.execute('UPDATE attachment_document SET active_version_uuid=?',(verified,))
        self.db.commit()
    def tearDown(self): self.db.close()
    def identity(self, record, kind='APPOINTMENT', ident=1, epoch=None, store=None):
        self.db.execute("INSERT INTO attachment_record_identity VALUES(?,?,?,?,?,'ACTIVE',NULL,?)",(store or self.store,epoch or self.epoch,record,kind,ident,bytes(32)))
    def link(self, record=None, epoch=None, store=None):
        self.db.execute("INSERT INTO attachment_link VALUES(?,?,?,?,?,1,'ACTIVE')",(store or self.store,uid(),epoch or self.epoch,record or self.record,self.doc))
    def version(self,status='STAGED'):
        version=uid()
        self.db.execute('INSERT INTO attachment_version VALUES(?,?,?,?,?,1,?,?,?,?,?,?,?)',(self.store,self.doc,version,self.db.execute('SELECT COALESCE(MAX(ordinal),0)+1 FROM attachment_version').fetchone()[0],uid(),uid(),bytes(32),bytes([1])*32,10,100,'image/png',status))
        return version
    def test_legacy_data_indices_guards_exact(self):
        self.assertEqual(fingerprint(self.db),self.before)
        after=dict(self.db.execute('SELECT name,sql FROM sqlite_master WHERE sql IS NOT NULL'))
        for name,sql in self.objects.items(): self.assertEqual(after[name],sql,name)
        self.assertEqual(self.db.execute('PRAGMA user_version').fetchone()[0],10)
        self.assertFalse(self.db.execute('PRAGMA foreign_key_check').fetchall())
    def test_migration_interrupt_rolls_back_all(self):
        db=legacy(); before=fingerprint(db)
        with self.assertRaises(OSError): migrate(db,True)
        self.assertEqual(fingerprint(db),before)
        self.assertEqual(db.execute('PRAGMA user_version').fetchone()[0],9)
        self.assertFalse(db.execute("SELECT name FROM sqlite_master WHERE name LIKE 'attachment_%'").fetchall()); db.close()
    def test_no_auto_duplicate_clinical_rows(self):
        self.assertEqual(self.db.execute('SELECT count(*) FROM appointment').fetchone()[0],1)
        self.assertEqual(self.db.execute('SELECT count(*) FROM lab_value').fetchone()[0],1)
        self.assertEqual(self.db.execute('SELECT count(*) FROM attachment_link').fetchone()[0],0)
    def test_disabled_default_and_primary_only(self):
        self.assertEqual(self.db.execute('SELECT feature_enabled FROM attachment_store').fetchone()[0],0)
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute("INSERT INTO attachment_store(store_uuid,logical_vault_uuid,store_state,space,record_epoch,generation,feature_enabled) VALUES(?,?,'CURRENT',?,?,?,0)",(uid(),uid(),'DECOY',uid(),1))
    def test_typed_missing_target_rejected(self):
        with self.assertRaises(sqlite3.IntegrityError): self.identity(uid(),'LAB',999)
        with self.assertRaises(sqlite3.IntegrityError): self.identity(uid(),'UNKNOWN',1)
        self.identity(uid(),'LAB',1)
    def test_duplicate_live_record_rejected(self):
        with self.assertRaises(sqlite3.IntegrityError): self.identity(uid())
    def test_cross_store_link_rejected(self):
        other=uid(); self.db.execute("INSERT INTO attachment_store(store_uuid,logical_vault_uuid,store_state,space,record_epoch,generation,feature_enabled) VALUES(?,?,'STAGING',?,?,?,0)",(other,uid(),'PRIMARY',uid(),1))
        with self.assertRaises(sqlite3.IntegrityError): self.link(store=other)
    def test_stale_epoch_rejected_old_backup_no_id_reassociation(self):
        self.link(); newepoch=uid()
        self.db.execute("UPDATE attachment_link SET state='QUARANTINED'")
        self.db.execute('UPDATE attachment_store SET record_epoch=?,generation=generation+1',(newepoch,))
        self.identity(uid(),epoch=newepoch)
        with self.assertRaises(sqlite3.IntegrityError): self.link()
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE attachment_link SET state='ACTIVE'")
    def test_delete_trash_restore_preserves_link_identity(self):
        self.link(); row=self.db.execute('SELECT * FROM appointment WHERE id=1').fetchone()
        self.db.execute("UPDATE attachment_record_identity SET state='TRASHED',trash_id=99 WHERE record_uuid=?",(self.record,))
        self.db.execute('DELETE FROM appointment WHERE id=1')
        self.assertEqual(self.db.execute('SELECT count(*) FROM attachment_link').fetchone()[0],1)
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE attachment_record_identity SET state='ACTIVE',trash_id=NULL")
        self.db.execute('INSERT INTO appointment VALUES('+','.join('?' for _ in row)+')',row)
        self.db.execute("UPDATE attachment_record_identity SET state='ACTIVE',trash_id=NULL")
        self.assertEqual(self.db.execute('SELECT record_uuid FROM attachment_link').fetchone()[0],self.record)
    def test_purge_then_integer_reuse_new_identity(self):
        self.link(); self.db.execute("UPDATE attachment_record_identity SET state='PURGED',trash_id=NULL")
        fresh=uid(); self.identity(fresh)
        self.assertNotEqual(self.db.execute('SELECT record_uuid FROM attachment_link').fetchone()[0],fresh)
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE attachment_record_identity SET state='ACTIVE' WHERE record_uuid=?",(self.record,))
    def test_verified_only_activation(self):
        version=self.version()
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('UPDATE attachment_document SET active_version_uuid=?',(version,))
        self.db.execute("UPDATE attachment_version SET status='VERIFIED' WHERE version_uuid=?",(version,))
        self.db.execute('UPDATE attachment_document SET active_version_uuid=?',(version,))
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('UPDATE attachment_version SET plaintext_bytes=11')
    def test_parent_deletes_restrict(self):
        self.link(); self.db.commit()
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('DELETE FROM attachment_document')
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('DELETE FROM attachment_record_identity')
    def test_link_duplicates_and_retraction(self):
        self.link()
        with self.assertRaises(sqlite3.IntegrityError): self.link()
        self.db.execute("UPDATE attachment_link SET state='RETRACTED'"); self.link()
    def test_operation_idempotency_nonnegative_reservations(self):
        token=uid(); args=(self.store,uid(),token,'IMPORT','PREPARED',1,None,self.doc,None,None,None,None,None,None,100,1,1)
        self.db.execute('INSERT INTO attachment_operation VALUES('+','.join('?' for _ in args)+')',args)
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('INSERT INTO attachment_operation VALUES('+','.join('?' for _ in args)+')',(self.store,uid())+args[2:])
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('UPDATE attachment_operation SET reserved_bytes=-1')
    def test_ledger_immutable_after_additive_migration(self):
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('UPDATE supply_transaction SET used_delta=2')
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('DELETE FROM supply_transaction')

    def test_cas_stale_generation_has_no_document_side_effect(self):
        expected=1
        self.db.execute('UPDATE attachment_store SET generation=2')
        changed=self.db.execute('UPDATE attachment_document SET revision=revision+1 WHERE store_uuid=? AND revision=1 AND EXISTS(SELECT 1 FROM attachment_store WHERE store_uuid=? AND generation=?)',(self.store,self.store,expected)).rowcount
        self.assertEqual(changed,0)
        self.assertEqual(self.db.execute('SELECT revision FROM attachment_document').fetchone()[0],1)

    def envelope(self, version, epoch=1, handle=None):
        handle=handle or self.db.execute('SELECT dek_handle_uuid FROM attachment_version WHERE version_uuid=?',(version,)).fetchone()[0]
        envelope=uid()
        self.db.execute("INSERT INTO attachment_key_envelope VALUES(?,?,?,?,?,?,?,?,?,'AVAILABLE')",(self.store,self.doc,version,envelope,handle,epoch,'synthetic-key-'+str(epoch),b'synthetic-wrapped-key',1))
        return envelope

    def test_rewrap_adds_envelope_without_mutating_original_version(self):
        version=self.db.execute('SELECT version_uuid FROM attachment_version').fetchone()[0]
        before=self.db.execute('SELECT * FROM attachment_version').fetchall()
        old=self.envelope(version); new=self.envelope(version,2)
        self.db.execute("UPDATE attachment_key_envelope SET state='TOMBSTONED' WHERE envelope_uuid=?",(old,))
        self.assertEqual(self.db.execute('SELECT * FROM attachment_version').fetchall(),before)
        self.assertEqual(self.db.execute("SELECT envelope_uuid FROM attachment_key_envelope WHERE state='AVAILABLE'").fetchone()[0],new)
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE attachment_key_envelope SET state='AVAILABLE' WHERE envelope_uuid=?",(old,))
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('DELETE FROM attachment_key_envelope WHERE envelope_uuid=?',(old,))

    def test_envelope_wrong_handle_duplicate_epoch_and_inplace_mutation_rejected(self):
        version=self.db.execute('SELECT version_uuid FROM attachment_version').fetchone()[0]
        with self.assertRaises(sqlite3.IntegrityError): self.envelope(version,handle=uid())
        self.envelope(version)
        with self.assertRaises(sqlite3.IntegrityError): self.envelope(version)
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE attachment_key_envelope SET wrapped_dek=x'00'")

    def test_generation_activation_requires_validated_state_and_cas_increment(self):
        physical=uid()
        self.db.execute("INSERT INTO attachment_generation VALUES(?,?,'STAGING',NULL,1)",(self.store,physical))
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('UPDATE attachment_store SET active_generation_uuid=?,generation=2',(physical,))
        self.db.execute("UPDATE attachment_generation SET state='VALIDATED',manifest_digest=?",(bytes(32),))
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('UPDATE attachment_store SET active_generation_uuid=?',(physical,))
        changed=self.db.execute('UPDATE attachment_store SET active_generation_uuid=?,generation=generation+1 WHERE generation=1',(physical,)).rowcount
        self.assertEqual(changed,1)
        self.assertEqual(self.db.execute('UPDATE attachment_store SET active_generation_uuid=?,generation=generation+1 WHERE generation=1',(physical,)).rowcount,0)
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('UPDATE attachment_generation SET manifest_digest=?',(bytes([1])*32,))

    def test_new_active_link_rejects_trashed_document_but_old_link_retained(self):
        self.link()
        self.db.execute("UPDATE attachment_document SET state='TRASHED',deleted_utc=1")
        self.db.execute("UPDATE attachment_link SET state='RETRACTED'")
        with self.assertRaises(sqlite3.IntegrityError): self.link()
        self.assertEqual(self.db.execute('SELECT count(*) FROM attachment_link').fetchone()[0],1)
        self.db.execute("UPDATE attachment_document SET state='ACTIVE',deleted_utc=NULL")
        self.link()

    def test_new_active_link_rejects_corrupt_active_version(self):
        self.db.execute("UPDATE attachment_version SET status='CORRUPT'")
        with self.assertRaises(sqlite3.IntegrityError): self.link()

    def test_purged_identity_cannot_revive_through_trash_or_be_deleted(self):
        self.db.execute("UPDATE attachment_record_identity SET state='PURGED',trash_id=NULL")
        # Original typed clinical target remains, making this a real bypass attempt rather than a missing-row rejection.
        self.assertEqual(self.db.execute('SELECT count(*) FROM appointment WHERE id=1').fetchone()[0],1)
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE attachment_record_identity SET state='TRASHED',trash_id=99")
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE attachment_record_identity SET state='ACTIVE',trash_id=NULL")
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('DELETE FROM attachment_record_identity')
        self.assertEqual(self.db.execute('SELECT state FROM attachment_record_identity').fetchone()[0],'PURGED')

    def test_unreferenced_link_tombstone_cannot_be_deleted(self):
        self.link()
        self.db.execute("UPDATE attachment_link SET state='RETRACTED'")
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('DELETE FROM attachment_link')
        self.assertEqual(self.db.execute('SELECT state FROM attachment_link').fetchone()[0],'RETRACTED')

    def test_validated_generation_cannot_restage_or_change_manifest_in_two_steps(self):
        physical=uid(); original=bytes([1])*32
        self.db.execute("INSERT INTO attachment_generation VALUES(?,?,'STAGING',NULL,1)",(self.store,physical))
        self.db.execute("UPDATE attachment_generation SET state='VALIDATED',manifest_digest=?",(original,))
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE attachment_generation SET state='STAGING'")
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('UPDATE attachment_generation SET manifest_digest=?',(bytes([2])*32,))
        self.assertEqual(self.db.execute('SELECT state,manifest_digest FROM attachment_generation').fetchone(),('VALIDATED',original))
        self.db.execute("UPDATE attachment_generation SET state='PURGED'")
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE attachment_generation SET state='VALIDATED'")
        self.assertEqual(self.db.execute('SELECT state FROM attachment_generation').fetchone()[0],'PURGED')

    def staging_dataset(self, vault=None):
        local=uid(); epoch=uid()
        self.db.execute("INSERT INTO attachment_store(store_uuid,logical_vault_uuid,store_state,space,record_epoch,generation,feature_enabled) VALUES(?,?,'STAGING','PRIMARY',?,1,0)",(local,vault or uid(),epoch))
        return local,epoch

    def switch_dataset(self, newlocal, expected_generation=1, interrupt=False):
        self.db.commit()
        self.db.execute('BEGIN IMMEDIATE')
        try:
            changed=self.db.execute("UPDATE attachment_store SET store_state='RETIRED',generation=generation+1 WHERE store_uuid=? AND store_state='CURRENT' AND generation=?",(self.store,expected_generation)).rowcount
            if changed!=1: raise RuntimeError('stale current dataset CAS')
            self.db.execute("UPDATE attachment_link SET state='QUARANTINED' WHERE store_uuid=? AND state='ACTIVE'",(self.store,))
            self.db.execute("UPDATE appointment SET note='Synthetic restored dataset' WHERE id=1")
            if interrupt: raise OSError('synthetic activation interruption')
            changed=self.db.execute("UPDATE attachment_store SET store_state='CURRENT' WHERE store_uuid=? AND store_state='STAGING'",(newlocal,)).rowcount
            if changed!=1: raise RuntimeError('missing staging dataset')
            self.db.commit()
        except BaseException:
            self.db.rollback()
            raise

    def test_same_crypto_vault_different_metadata_namespaces_keep_old_purged_tombstones(self):
        vault=self.db.execute('SELECT logical_vault_uuid FROM attachment_store WHERE store_uuid=?',(self.store,)).fetchone()[0]
        newlocal,_=self.staging_dataset(vault)
        self.db.execute("UPDATE attachment_document SET state='PURGED',deleted_utc=1,revision=5")
        self.db.execute("INSERT INTO attachment_document VALUES(?,?,2,'Restored synthetic','REPORT','ACTIVE',NULL,NULL)",(newlocal,self.doc))
        rows=self.db.execute('SELECT store_uuid,revision,state FROM attachment_document WHERE document_uuid=? ORDER BY revision',(self.doc,)).fetchall()
        self.assertEqual(rows,[(newlocal,2,'ACTIVE'),(self.store,5,'PURGED')])
        self.assertEqual(self.db.execute('SELECT count(DISTINCT logical_vault_uuid) FROM attachment_store').fetchone()[0],1)

    def test_current_dataset_unique_and_crypto_namespace_immutable(self):
        newlocal,_=self.staging_dataset()
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE attachment_store SET store_state='CURRENT' WHERE store_uuid=?",(newlocal,))
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('UPDATE attachment_store SET logical_vault_uuid=? WHERE store_uuid=?',(uid(),self.store))
        self.assertEqual(self.db.execute("SELECT count(*) FROM attachment_store WHERE store_state='CURRENT'").fetchone()[0],1)

    def test_switch_dataset_cas_and_current_query_excludes_retired_metadata(self):
        self.link(); newlocal,_=self.staging_dataset()
        self.db.execute("INSERT INTO attachment_document VALUES(?,?,2,'Restored synthetic','REPORT','ACTIVE',NULL,NULL)",(newlocal,self.doc))
        self.switch_dataset(newlocal)
        rows=self.db.execute("SELECT d.store_uuid,d.revision FROM attachment_document d JOIN attachment_store s ON s.store_uuid=d.store_uuid WHERE s.store_state='CURRENT' AND d.state='ACTIVE'").fetchall()
        self.assertEqual(rows,[(newlocal,2)])
        self.assertEqual(self.db.execute('SELECT state FROM attachment_link WHERE store_uuid=?',(self.store,)).fetchone()[0],'QUARANTINED')
        self.assertEqual(self.db.execute('SELECT count(*) FROM attachment_document').fetchone()[0],2)
        with self.assertRaises(RuntimeError): self.switch_dataset(newlocal)
        self.assertEqual(self.db.execute("SELECT store_uuid FROM attachment_store WHERE store_state='CURRENT'").fetchone()[0],newlocal)

    def test_switch_interruption_rolls_back_current_links_and_legacy_rows(self):
        self.link(); newlocal,_=self.staging_dataset(); before=fingerprint(self.db)
        with self.assertRaises(OSError): self.switch_dataset(newlocal,interrupt=True)
        self.assertEqual(fingerprint(self.db),before)
        self.assertEqual(self.db.execute("SELECT store_uuid FROM attachment_store WHERE store_state='CURRENT'").fetchone()[0],self.store)
        self.assertEqual(self.db.execute('SELECT state FROM attachment_link').fetchone()[0],'ACTIVE')
        self.assertEqual(self.db.execute('SELECT generation FROM attachment_store WHERE store_uuid=?',(self.store,)).fetchone()[0],1)

    def test_staging_or_retired_registry_and_link_cannot_gain_active_state(self):
        self.link(); newlocal,newepoch=self.staging_dataset()
        with self.assertRaises(sqlite3.IntegrityError): self.identity(uid(),store=newlocal,epoch=newepoch)
        self.db.execute("UPDATE attachment_record_identity SET state='TRASHED',trash_id=99 WHERE store_uuid=?",(self.store,))
        self.switch_dataset(newlocal)
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE attachment_record_identity SET state='ACTIVE',trash_id=NULL WHERE store_uuid=?",(self.store,))
        with self.assertRaises(sqlite3.IntegrityError): self.identity(uid(),store=self.store)
        with self.assertRaises(sqlite3.IntegrityError): self.link()
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE attachment_link SET state='ACTIVE' WHERE store_uuid=?",(self.store,))
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE attachment_store SET store_state='STAGING' WHERE store_uuid=?",(self.store,))

    def test_old_pnbak_empty_dataset_uses_fresh_crypto_and_record_domains(self):
        oldvault=self.db.execute('SELECT logical_vault_uuid FROM attachment_store WHERE store_uuid=?',(self.store,)).fetchone()[0]
        newlocal,newepoch=self.staging_dataset()
        self.switch_dataset(newlocal)
        freshvault=self.db.execute('SELECT logical_vault_uuid FROM attachment_store WHERE store_uuid=?',(newlocal,)).fetchone()[0]
        self.assertNotEqual(freshvault,oldvault)
        self.assertNotEqual(newepoch,self.epoch)
        self.assertEqual(self.db.execute('SELECT count(*) FROM attachment_document WHERE store_uuid=?',(newlocal,)).fetchone()[0],0)
        freshrecord=uid(); self.identity(freshrecord,store=newlocal,epoch=newepoch)
        self.assertNotEqual(freshrecord,self.record)

    def crypto_restore_dataset(self):
        vault=self.db.execute('SELECT logical_vault_uuid FROM attachment_store WHERE store_uuid=?',(self.store,)).fetchone()[0]
        local,_=self.staging_dataset(vault)
        self.db.execute("INSERT INTO attachment_document VALUES(?,?,7,'Restored text revision','REPORT','ACTIVE',NULL,NULL)",(local,self.doc))
        row=list(self.db.execute('SELECT * FROM attachment_version WHERE store_uuid=?',(self.store,)).fetchone())
        row[0]=local
        return row

    def test_same_crypto_version_restore_preserves_bytes_across_local_datasets(self):
        row=self.crypto_restore_dataset()
        self.db.execute('INSERT INTO attachment_version VALUES('+','.join('?' for _ in row)+')',row)
        self.assertEqual(self.db.execute('SELECT count(*) FROM attachment_version WHERE version_uuid=?',(row[2],)).fetchone()[0],2)
        self.assertEqual(self.db.execute('SELECT revision FROM attachment_document WHERE store_uuid=?',(row[0],)).fetchone()[0],7)
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('UPDATE attachment_version SET ciphertext_sha256=? WHERE store_uuid=?',(bytes([7])*32,row[0]))

    def test_same_crypto_version_conflicting_identity_rejected(self):
        row=self.crypto_restore_dataset()
        for index,value in [(4,uid()),(5,2),(6,uid()),(7,bytes([7])*32),(8,bytes([8])*32),(9,11),(10,101)]:
            bad=list(row);bad[index]=value
            with self.subTest(column=index):
                with self.assertRaises(sqlite3.IntegrityError): self.db.execute('INSERT INTO attachment_version VALUES('+','.join('?' for _ in bad)+')',bad)
        different_document=uid()
        self.db.execute("INSERT INTO attachment_document VALUES(?,?,1,'Other synthetic','REPORT','ACTIVE',NULL,NULL)",(row[0],different_document))
        bad=list(row); bad[1]=different_document
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('INSERT INTO attachment_version VALUES('+','.join('?' for _ in bad)+')',bad)
        self.assertEqual(self.db.execute('SELECT count(*) FROM attachment_version WHERE store_uuid=?',(row[0],)).fetchone()[0],0)

    def prepared_import(self):
        operation=uid(); args=(self.store,operation,uid(),'IMPORT','PREPARED',1,None,self.doc,None,None,None,None,None,None,100,1,1)
        self.db.execute('INSERT INTO attachment_operation VALUES('+','.join('?' for _ in args)+')',args)
        return operation

    def synthetic_resources(self,local=None):
        local=local or self.store; physical=uid()
        self.db.execute("INSERT INTO attachment_generation VALUES(?,?,'STAGING',NULL,1)",(local,physical))
        return physical,uid(),uid(),uid(),b'synthetic-wrapped-key',hashlib.sha256(b'synthetic-wrapped-key').digest()

    def test_import_requires_complete_resources_before_writing(self):
        operation=self.prepared_import()
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE attachment_operation SET phase='WRITING' WHERE operation_uuid=?",(operation,))
        resources=self.synthetic_resources()
        self.db.execute("UPDATE attachment_operation SET resource_generation_uuid=?,object_uuid=?,version_uuid=?,dek_handle_uuid=?,staged_wrapped_dek=?,staged_wrapped_dek_sha256=?,phase='WRITING' WHERE operation_uuid=?",resources+(operation,))
        self.assertEqual(self.db.execute('SELECT phase FROM attachment_operation WHERE operation_uuid=?',(operation,)).fetchone()[0],'WRITING')
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE attachment_operation SET phase='PREPARED' WHERE operation_uuid=?",(operation,))

    def test_missing_cross_store_generation_and_write_ownership_changes_rejected(self):
        operation=self.prepared_import(); other,_=self.staging_dataset(); resources=self.synthetic_resources(other)
        for generation in [uid(),resources[0]]:
            with self.subTest(generation=generation):
                with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE attachment_operation SET resource_generation_uuid=?,object_uuid=?,version_uuid=?,dek_handle_uuid=?,staged_wrapped_dek=?,staged_wrapped_dek_sha256=?,phase='WRITING' WHERE operation_uuid=?",(generation,)+resources[1:]+(operation,))
        owned=self.synthetic_resources()
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE attachment_operation SET staged_wrapped_dek=? WHERE operation_uuid=?",(bytes(1025),operation))
        self.db.execute("UPDATE attachment_operation SET resource_generation_uuid=?,object_uuid=?,version_uuid=?,dek_handle_uuid=?,staged_wrapped_dek=?,staged_wrapped_dek_sha256=?,phase='WRITING' WHERE operation_uuid=?",owned+(operation,))
        for column,value in [('resource_generation_uuid',uid()),('object_uuid',uid()),('version_uuid',uid()),('dek_handle_uuid',uid()),('staged_wrapped_dek',b'different-synthetic-wrap')]:
            with self.subTest(column=column):
                with self.assertRaises(sqlite3.IntegrityError): self.db.execute('UPDATE attachment_operation SET '+column+'=? WHERE operation_uuid=?',(value,operation))
        self.db.execute("UPDATE attachment_operation SET phase='AUTHENTICATED' WHERE operation_uuid=?",(operation,))
        self.assertEqual(self.db.execute('SELECT resource_generation_uuid,object_uuid,version_uuid,dek_handle_uuid,staged_wrapped_dek,staged_wrapped_dek_sha256 FROM attachment_operation WHERE operation_uuid=?',(operation,)).fetchone(),owned)

    def test_terminal_operation_wipes_staging_key_irreversibly_retaining_resource_audit(self):
        for terminal in ['CLEANED','ABORTED']:
            with self.subTest(terminal=terminal):
                operation=self.prepared_import(); owned=self.synthetic_resources()
                self.db.execute("UPDATE attachment_operation SET resource_generation_uuid=?,object_uuid=?,version_uuid=?,dek_handle_uuid=?,staged_wrapped_dek=?,staged_wrapped_dek_sha256=?,phase='WRITING' WHERE operation_uuid=?",owned+(operation,))
                if terminal=='CLEANED':
                    for phase in ['AUTHENTICATED','COMMITTED']:
                        changed=self.db.execute('UPDATE attachment_operation SET phase=? WHERE operation_uuid=? AND phase=?',(phase,operation,'WRITING' if phase=='AUTHENTICATED' else 'AUTHENTICATED')).rowcount
                        self.assertEqual(changed,1)
                self.db.execute('UPDATE attachment_operation SET phase=?,staged_wrapped_dek=NULL WHERE operation_uuid=?',(terminal,operation))
                audit=self.db.execute('SELECT resource_generation_uuid,object_uuid,version_uuid,dek_handle_uuid,staged_wrapped_dek,staged_wrapped_dek_sha256 FROM attachment_operation WHERE operation_uuid=?',(operation,)).fetchone()
                self.assertEqual(audit,owned[:4]+(None,owned[5]))
                with self.assertRaises(sqlite3.IntegrityError): self.db.execute('UPDATE attachment_operation SET staged_wrapped_dek=? WHERE operation_uuid=?',(owned[4],operation))
                with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE attachment_operation SET phase='WRITING' WHERE operation_uuid=?",(operation,))
                with self.assertRaises(sqlite3.IntegrityError): self.db.execute('UPDATE attachment_operation SET staged_wrapped_dek_sha256=? WHERE operation_uuid=?',(bytes(32),operation))
                with self.assertRaises(sqlite3.IntegrityError): self.db.execute('UPDATE attachment_operation SET object_uuid=? WHERE operation_uuid=?',(uid(),operation))

    def test_schema_guard_not_weakened(self):
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('UPDATE appointment SET remind_minutes_before=-1')
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('UPDATE lab_value SET value=0')

if __name__=='__main__': unittest.main(verbosity=2)
