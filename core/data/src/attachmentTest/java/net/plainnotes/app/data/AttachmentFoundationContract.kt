package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.util.UUID

/** Real Room transactions and guards, synthetic clinical records only. */
abstract class AttachmentFoundationContract {
    private lateinit var db:NotesDatabase
    private lateinit var repo:NotesRepository
    private val raw get()=db.openHelper.writableDatabase
    private lateinit var scope:AttachmentIdentity.Scope
    @Before fun open() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
        runBlocking { db.withTransaction { scope=requireNotNull(AttachmentIdentity.initialize(raw,true)) } }
    }
    @After fun close()=db.close()
    private fun reject(block:()->Unit) { try { block();fail("Unsafe metadata accepted") } catch(_:IllegalStateException){} catch(_:IllegalArgumentException){} catch(_:android.database.SQLException){} }
    private suspend fun visit(id:Long=1)=db.dao().appointment(AppointmentEntity(id=id,type="ENDO",at_utc=1000,at_zone="UTC",location="Synthetic",practitioner="Synthetic",note="Synthetic",remind_minutes_before=0))
    private suspend fun bind(kind:String="APPOINTMENT",id:Long=1)=db.withTransaction { AttachmentIdentity.bind(raw,scope,kind,id) }
    @Test fun room11KeepsThreeAttachmentDomainsDefaultDisabled()=runBlocking {
        assertEquals(12,raw.version)
        assertNotEquals(scope.dataset,scope.logicalVault);assertNotEquals(scope.dataset,scope.epoch)
        assertEquals(1,db.attachments().current().size)
        assertEquals(0L,db.attachments().current().single().feature_enabled)
        raw.query("SELECT generation_uuid FROM attachment_generation WHERE store_uuid=?",arrayOf(scope.dataset)).use {
            assertTrue(it.moveToFirst());assertNotEquals(scope.dataset,it.getString(0));assertNotEquals(scope.logicalVault,it.getString(0))
        }
        reject{raw.execSQL("UPDATE attachment_store SET feature_enabled=1")}
        reject{raw.execSQL("UPDATE attachment_generation SET state='VALIDATED',manifest_digest=zeroblob(32)")}
        db.withTransaction { assertNull(AttachmentIdentity.initialize(raw,false)) }
    }
    @Test fun typedIdentityIsStableAndMissingOrSkippedTargetsReject()=runBlocking {
        visit();val a=bind();assertEquals(a,bind())
        reject{raw.execSQL("INSERT INTO attachment_record_identity(store_uuid,record_epoch,record_uuid,target_kind,legacy_id,state,trash_id,evidence_digest) VALUES (?,?,?,'APPOINTMENT',1,'TRASHED',1,zeroblob(32))",arrayOf(scope.dataset,UUID.randomUUID().toString(),UUID.randomUUID().toString()))}
        reject{runBlocking{bind("LAB",1)}}
        reject{runBlocking{bind("DOSE_RECORD",1)}}
        reject{runBlocking{db.withTransaction { AttachmentIdentity.bind(raw,scope.copy(epoch=UUID.randomUUID().toString()),"APPOINTMENT",1) }}}
    }
    @Test fun visitTrashRestoreKeepsUuidThenPurgePreventsIntegerReuse()=runBlocking {
        visit();val before=bind();repo.deleteAppointment(1,Instant.ofEpochMilli(2000))
        val trash=repo.trash().single{it.kind==Trash.APPOINTMENT}
        assertEquals("TRASHED",db.attachments().identity(scope.dataset,scope.epoch,"APPOINTMENT",1)!!.state)
        repo.restoreTrash(trash.id);assertEquals(before,bind())
        repo.deleteAppointment(1,Instant.ofEpochMilli(3000));repo.purgeTrash(repo.trash().single{it.kind==Trash.APPOINTMENT}.id)
        visit(1);assertNotEquals(before,bind())
        reject{raw.execSQL("UPDATE attachment_record_identity SET state='ACTIVE' WHERE record_uuid=?",arrayOf(before))}
        reject{raw.execSQL("DELETE FROM attachment_record_identity WHERE record_uuid=?",arrayOf(before))}
    }
    @Test fun labTrashRestoreKeepsTypedUuidAndContext()=runBlocking {
        raw.execSQL("INSERT INTO lab_analyte(code,canonical_unit) VALUES ('E2','pg/mL')")
        raw.execSQL("INSERT INTO lab_value(id,analyte_code,value,unit,sampled_utc,sampled_zone) VALUES (1,'E2',12,'pg/mL',1000,'UTC')")
        val before=bind("LAB",1);repo.deleteLab(1,Instant.ofEpochMilli(2000))
        repo.restoreTrash(repo.trash().single{it.kind==Trash.LAB}.id)
        assertEquals(before,bind("LAB",1))
        assertEquals(12.0,db.dao().labs().single().value,0.0)
    }
    @Test fun changedTrashEvidenceFailsAndRestorationRollsBack()=runBlocking {
        visit();bind();repo.deleteAppointment(1,Instant.ofEpochMilli(2000));val t=repo.trash().single{it.kind==Trash.APPOINTMENT}
        val changed=JSONObject(t.payload_json);changed.getJSONObject("tables").getJSONArray("appointment").getJSONObject(0).put("note","different synthetic evidence")
        raw.execSQL("UPDATE trash_item SET payload_json=? WHERE id=?",arrayOf(changed.toString(),t.id))
        reject{runBlocking{repo.restoreTrash(t.id)}}
        assertNull(db.dao().appointmentById(1));assertEquals(1,repo.trash().size)
    }
    @Test fun pnBakBusinessSchemaTenStillExcludesAttachmentsAndRestoreRetiresIdentity()=runBlocking {
        visit();val before=bind();val password="synthetic-only-password".toCharArray()
        val backup=repo.exportBackup(password)
        val decoded=BackupCodec.decrypt(backup,password)
        try {
            val json=JSONObject(String(decoded,Charsets.UTF_8));assertEquals(11,json.getInt("schema"))
            assertFalse(json.getJSONObject("tables").keys().asSequence().any{it.startsWith("attachment_")})
            assertEquals(36,json.getJSONObject("tables").length())
            assertTrue(ReferenceSchema.businessTables.all {json.getJSONObject("tables").has(it)})
            assertFalse(json.getJSONObject("tables").has("reference_runtime"))
        } finally { decoded.fill(0) }
        repo.restoreBackup(backup,password)
        val next=requireNotNull(AttachmentIdentity.current(raw));assertNotEquals(scope.dataset,next.dataset);assertNotEquals(scope.epoch,next.epoch);assertNotEquals(scope.logicalVault,next.logicalVault)
        assertNull(db.attachments().identity(scope.dataset,scope.epoch,"APPOINTMENT",1))
        scope=next;assertNotEquals(before,bind());assertEquals("Synthetic",db.dao().appointmentById(1)!!.note)
        reject{raw.execSQL("UPDATE attachment_store SET store_state='CURRENT' WHERE store_state='RETIRED'")}
        password.fill('\u0000')
    }
    @Test fun wrongPasswordCorruptionAndFailedRestoreDoNotChangeRowsOrEpoch()=runBlocking {
        visit();bind();val password="synthetic-only-password".toCharArray();val backup=repo.exportBackup(password)
        try { repo.restoreBackup(backup,"wrong-password".toCharArray());fail() } catch(_:BackupCodec.WrongPassword){}
        val corrupt=backup.copyOf();corrupt[corrupt.lastIndex]=(corrupt.last().toInt() xor 1).toByte()
        try { repo.restoreBackup(corrupt,password);fail() } catch(_:BackupCodec.WrongPassword){}
        val decoded=BackupCodec.decrypt(backup,password);val invalid=JSONObject(String(decoded,Charsets.UTF_8));decoded.fill(0)
        invalid.getJSONObject("tables").getJSONArray("appointment").getJSONObject(0).put("at_zone","not/a/zone")
        val bytes=invalid.toString().toByteArray();val bad=BackupCodec.encrypt(bytes,password);bytes.fill(0)
        try {repo.restoreBackup(bad,password);fail()} catch(_:Exception){}
        assertEquals(scope,AttachmentIdentity.current(raw));assertEquals("Synthetic",db.dao().appointmentById(1)!!.note)
        assertEquals(1,db.attachments().current().size)
    }
    @Test fun failedDatasetSwitchRollsBackAndStaleHandlesCannotBind()=runBlocking {
        visit();bind()
        try { db.withTransaction { AttachmentIdentity.replaceStructuredDataset(raw);error("synthetic commit failure") };fail() } catch(_:IllegalStateException){}
        assertEquals(scope,AttachmentIdentity.current(raw))
        db.withTransaction { AttachmentIdentity.replaceStructuredDataset(raw) }
        reject{runBlocking{bind()}}
        assertEquals(1,db.attachments().current().size)
    }
    @Test fun unauthenticatedVersionsAndOperationsCannotBecomeReady()=runBlocking {
        val doc=UUID.randomUUID().toString();val version=UUID.randomUUID().toString()
        raw.execSQL("INSERT INTO attachment_document(store_uuid,document_uuid,revision,title,category,state) VALUES (?,?,1,'synthetic','REPORT','ACTIVE')",arrayOf(scope.dataset,doc))
        raw.execSQL("INSERT INTO attachment_version(store_uuid,document_uuid,version_uuid,ordinal,object_uuid,protocol_revision,dek_handle_uuid,plaintext_sha256,ciphertext_sha256,plaintext_bytes,ciphertext_bytes,mime,status) VALUES (?,?,?,1,?,1,?,zeroblob(32),zeroblob(32),1,100,'image/png','STAGED')",arrayOf(scope.dataset,doc,version,UUID.randomUUID().toString(),UUID.randomUUID().toString()))
        reject{raw.execSQL("UPDATE attachment_version SET status='VERIFIED'")}
        reject{raw.execSQL("UPDATE attachment_document SET active_version_uuid=?",arrayOf(version))}
        reject{raw.execSQL("UPDATE attachment_version SET ciphertext_sha256=randomblob(32)")}
    }
    @Test fun operationReplayOwnershipAndTerminalStateAreProtected()=runBlocking {
        val op=UUID.randomUUID().toString();val idem=UUID.randomUUID().toString()
        val insert="INSERT INTO attachment_operation(store_uuid,operation_uuid,idempotency_uuid,kind,phase,expected_generation,reserved_bytes,reserved_objects,updated_utc) VALUES (?,?,?,'IMPORT','PREPARED',1,1,1,1)"
        raw.execSQL(insert,arrayOf(scope.dataset,op,idem));assertEquals(1,db.attachments().pending(scope.dataset).size)
        reject{raw.execSQL(insert,arrayOf(scope.dataset,UUID.randomUUID().toString(),idem))}
        reject{raw.execSQL("UPDATE attachment_operation SET phase='AUTHENTICATED'")}
        reject{raw.execSQL("UPDATE attachment_operation SET phase='COMMITTED'")}
        raw.execSQL("UPDATE attachment_operation SET phase='ABORTED'")
        reject{raw.execSQL("UPDATE attachment_operation SET phase='PREPARED'")}
        assertTrue(db.attachments().pending(scope.dataset).isEmpty())
    }
    @Test fun keyRolesPrivateSpaceAndMissingKeyCannotPretendRecovery() {
        val s=AttachmentKeyFoundation.Scope(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID())
        assertEquals(s,AttachmentKeyFoundation.primaryScope(Space.PRIMARY,s))
        reject{AttachmentKeyFoundation.primaryScope(Space.DECOY,s)}
        val v=AttachmentKeyFoundation.VersionKey(s,UUID.randomUUID(),UUID.randomUUID())
        val e=AttachmentKeyFoundation.DeviceEnvelope(v,1,"hrt.attachment.primary.synthetic",byteArrayOf(1))
        val missing=object:AttachmentKeyFoundation.DeviceKeyProvider {
            override fun unwrapExisting(envelope:AttachmentKeyFoundation.DeviceEnvelope):ByteArray { throw AttachmentKeyFoundation.MissingKey() }
        }
        reject{AttachmentKeyFoundation.existing(missing,e)}
        assertEquals(5,AttachmentKeyFoundation.Role.entries.size)
    }
    @Test fun journalReplayCancelAndFailedTransactionKeepReservationsConsistent()=runBlocking {
        val idem=UUID.randomUUID()
        val a=db.withTransaction{AttachmentOperations.prepare(raw,scope,idem,"IMPORT",10,1)}
        assertEquals(a,db.withTransaction{AttachmentOperations.prepare(raw,scope,idem,"IMPORT",10,1)})
        reject{runBlocking{db.withTransaction{AttachmentOperations.prepare(raw,scope,idem,"IMPORT",11,1)}}}
        db.withTransaction{AttachmentOperations.cancel(raw,scope,a);AttachmentOperations.cancel(raw,scope,a)}
        assertTrue(db.attachments().pending(scope.dataset).isEmpty())
        val other=UUID.randomUUID()
        try{db.withTransaction{AttachmentOperations.prepare(raw,scope,other,"IMPORT",20,1);error("injected SQL transaction failure")}}catch(_:IllegalStateException){}
        raw.query("SELECT COUNT(*) FROM attachment_operation").use{assertTrue(it.moveToFirst());assertEquals(1,it.getInt(0))}
    }
    @Test fun actualRoomCommitIsInsideAuthorityAndStaleCapabilityCannotPublish()=runBlocking {
        val authority=net.plainnotes.app.domain.AttachmentSession(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch))
        val cap=authority.issue()
        val operation=AttachmentTransaction.commit(db,scope,cap) { AttachmentOperations.prepare(raw,scope,UUID.randomUUID(),"IMPORT") }
        assertEquals(operation,db.attachments().pending(scope.dataset).single().operation_uuid)
        authority.revoke()
        reject{AttachmentTransaction.commit(db,scope,cap){AttachmentOperations.prepare(raw,scope,UUID.randomUUID(),"IMPORT")}}
        assertEquals(1,db.attachments().pending(scope.dataset).size)
        val wrong=net.plainnotes.app.domain.AttachmentSession(UUID.randomUUID(),UUID.randomUUID()).issue()
        reject{AttachmentTransaction.commit(db,scope,wrong){error("Wrong capability cannot enter Room transaction")}}
    }

    @Test fun draftLinksAreScopedAndOldRestoreQuarantinesEvenSameIntegerTargets()=runBlocking {
        visit(1);visit(2);val first=bind(id=1);val second=bind(id=2)
        val doc=UUID.randomUUID().toString()
        raw.execSQL("INSERT INTO attachment_document(store_uuid,document_uuid,revision,title,category,state) VALUES (?,?,1,'synthetic','REPORT','ACTIVE')",arrayOf(scope.dataset,doc))
        val insert="INSERT INTO attachment_link(store_uuid,link_uuid,record_epoch,record_uuid,document_uuid,revision,state) VALUES (?,?,?,?,?,1,'RETRACTED')"
        raw.execSQL(insert,arrayOf(scope.dataset,UUID.randomUUID().toString(),scope.epoch,first,doc))
        raw.execSQL(insert,arrayOf(scope.dataset,UUID.randomUUID().toString(),scope.epoch,second,doc))
        reject{raw.execSQL(insert,arrayOf(scope.dataset,UUID.randomUUID().toString(),UUID.randomUUID().toString(),first,doc))}
        val password="synthetic-link-password".toCharArray();repo.restoreBackup(repo.exportBackup(password),password)
        raw.query("SELECT state FROM attachment_link ORDER BY link_uuid").use{assertEquals(2,it.count);while(it.moveToNext())assertEquals("QUARANTINED",it.getString(0))}
        reject{raw.execSQL("UPDATE attachment_link SET state='ACTIVE'")}
        assertEquals(2,db.dao().appointments().size)
    }
    @Test fun clearFailureRollsBackDataDatasetAndReinstallsAllProtection()=runBlocking {
        visit();val before=bind()
        try{db.withTransaction{RawData.clear(raw);error("synthetic interruption after clear")}}catch(_:IllegalStateException){}
        assertEquals(scope,AttachmentIdentity.current(raw));assertEquals(before,bind());assertNotNull(db.dao().appointmentById(1))
        reject{raw.execSQL("DELETE FROM attachment_record_identity")}
        reject{raw.execSQL("UPDATE attachment_store SET feature_enabled=1")}
        raw.query("SELECT name FROM sqlite_master WHERE type='trigger' AND name IN('immutable_supply_update','immutable_supply_delete')").use{assertEquals(2,it.count)}
    }

    @Test fun realIntakeUuidSurvivesEditsDuplicateDeleteAndRestoreWithoutStockCreation()=runBlocking {
        raw.execSQL("INSERT INTO medication(id,name,molecule,unit,dose_per_intake,container_capacity,site_rotation,notifications_on,active,sort_order) VALUES (1,'Synthetic intake','OTHER','MG',1,10,0,0,1,0)")
        raw.execSQL("INSERT INTO dose_record(id,medication_id,taken_utc,taken_zone,actual_dose,unallocated_supply_amount,status,origin,revision,config_snapshot) VALUES (1,1,1000,'UTC',1,1,'ON_TIME','APP',1,'{}')")
        val record=bind("DOSE_RECORD",1)
        repo.editRecord(1,Instant.ofEpochMilli(1100),1.0,Instant.ofEpochMilli(2000));assertEquals(record,bind("DOSE_RECORD",1))
        repo.deleteRecord(1,Instant.ofEpochMilli(3000));val trash=repo.trash().single{it.kind==Trash.RECORD}
        repo.deleteRecord(1,Instant.ofEpochMilli(4000));assertEquals(trash.id,repo.trash().single{it.kind==Trash.RECORD}.id)
        repo.restoreTrash(trash.id);assertEquals(record,bind("DOSE_RECORD",1))
        raw.query("SELECT COUNT(*) FROM supply_container").use{assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))};raw.query("SELECT COUNT(*) FROM supply_transaction").use{assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
        assertEquals(1.0,db.dao().recordById(1)!!.actual_dose!!,0.0)
    }

}
