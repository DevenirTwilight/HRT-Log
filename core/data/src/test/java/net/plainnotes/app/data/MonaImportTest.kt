package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import net.plainnotes.app.importer.Mona
import net.plainnotes.app.importer.MonaMigration
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class MonaImportTest {
    private lateinit var db:NotesDatabase
    private lateinit var repo:NotesRepository
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private val now=Instant.parse("2026-10-25T12:00:00Z")
    @Before fun open(){db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build();db.openHelper.writableDatabase
        repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})}
    @After fun close(){db.close()}
    private fun dose(id:Long=1)=JSONObject().put("id",id).put("takenDateTime","2026-10-25T00:30:00Z").put("takenTimeZone","Europe/Paris").put("takenDose","1.2500").put("scheduledTime","9:0")
        .put("molecule",JSONObject().put("name","estradiol").put("massUnit","mg").toString()).put("administrationRoute","oral").put("ester",JSONObject.NULL).put("dosingBasis","mass").put("genericSupplyItemIds","[]").put("placements","[]")
    private fun blood()=JSONObject().put("id",5).put("dateTime","2026-10-25T08:30:00Z").put("timeZone","Europe/Paris").put("notes","Synthetic draw")
        .put("estradiolLevels",JSONObject().put("value","210.00").put("unit","pg/mL").toString()).put("testosteroneLevels",JSONObject().put("value","9").put("unit","ng/dL").toString())
    private fun root()=JSONObject().put("metadata",JSONObject().put("app_version","synthetic").put("database_version",23).put("export_date","2026-10-25T12:00:00"))
        .put("data",JSONObject().put("medication_intakes",JSONArray().put(dose()).put(dose(2).put("takenDateTime","2026-10-25T01:30:00Z")).put(dose(3).put("takenDateTime",JSONObject.NULL)))
            .put("blood_tests",JSONArray().put(blood())).put("medication_schedules",JSONArray()).put("supply_items",JSONArray()))
    private fun export(r:JSONObject=root())=Mona.read(r.toString())
    private suspend fun write(r:JSONObject=root(),receipt:MonaCommitReceipt=MonaCommitReceipt())=repo.importMona(export(r),emptyMap(),emptyMap(),receipt,now)
    private fun count(table:String)=db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use{it.moveToFirst();it.getInt(0)}
    private fun dump()=RawData.dump(db.openHelper.writableDatabase).toString()
    @Test fun actualTimesOriginalUnitsAndDrawAreCommittedWithoutPlansStockOrPk()=runBlocking {
        val receipt=MonaCommitReceipt();val p=repo.previewMona(export());assertEquals(2,p.addedDoses);assertEquals(2,p.addedLabs);assertNull(receipt.committed)
        val s=write(receipt=receipt);assertEquals(s,receipt.committed);assertEquals(2,s.addedDoses);assertEquals(2,s.addedLabs);assertEquals(1,s.addedDraws);assertEquals(1,s.medications);assertEquals(1,s.unsupported)
        val rows=repo.records().sortedBy{it.taken_utc};assertEquals(3600000L,rows[1].taken_utc!!-rows[0].taken_utc!!)
        assertTrue(rows.all{it.origin=="IMPORT_MONA" && it.actual_dose==1.25 && it.taken_zone=="Europe/Paris" && it.scheduled_utc==null && it.planned_dose==null && it.unallocated_supply_amount==null})
        assertTrue(rows.all{JSONObject(it.config_snapshot).isNull("pk_profile")})
        assertEquals(listOf("ng/dL","pg/mL"),repo.labs().map{it.unit}.sorted());assertEquals(1,repo.labPanels().size)
        assertTrue(repo.labs().all{it.reference_lower==null && it.reference_upper==null})
        for(table in listOf("schedule_rule","pk_profile","supply_container","supply_transaction","lab_context_revision"))assertEquals(table,0,count(table))
        assertTrue(repo.medications().all{!it.active && !it.notifications_on && it.needs_review!=null})
        assertEquals(4,count("external_import_item"));assertEquals(14,RecordBackupCompatibility.exportSchema(db.openHelper.writableDatabase))
        ExternalImportSchema.validate(db.openHelper.writableDatabase)
    }
    @Test fun repeatedAndLaterExportsAreIdempotentWithoutOrphanMedications()=runBlocking {
        write();val before=dump();val r=root();r.getJSONObject("metadata").put("export_date","2026-10-26T10:00:00")
        val again=write(r);assertEquals(0,again.addedDoses+again.addedLabs+again.medications);assertEquals(4,again.existing);assertEquals(before,dump())
        r.getJSONObject("data").getJSONArray("medication_intakes").put(dose(9))
        val next=write(r);assertEquals(1,next.addedDoses);assertEquals(0,next.medications);assertEquals(3,repo.records().size);assertEquals(1,repo.medications().size)
    }
    @Test fun sameSourceIdChangedContentIsAConflictAndDifferentIdIsNotADuplicate()=runBlocking {
        write();val before=dump();val r=root();r.getJSONObject("data").getJSONArray("medication_intakes").getJSONObject(0).put("takenDose","9")
        val changed=write(r);assertEquals(1,changed.conflicts);assertEquals(0,changed.addedDoses);assertEquals(before,dump())
        r.getJSONObject("data").getJSONArray("medication_intakes").put(dose(10))
        val different=write(r);assertEquals(1,different.addedDoses);assertEquals(1,different.conflicts);assertEquals(3,repo.records().size)
    }
    @Test fun localCorrectionAndDeletedHistoryAreNeverOverwrittenOrResurrected()=runBlocking {
        write();val original=repo.records().first();db.dao().updateRecord(original.copy(actual_dose=2.0,revision=2))
        val before=dump();val result=write();assertEquals(1,result.conflicts);assertEquals(before,dump());assertEquals(2.0,db.dao().recordById(original.id)!!.actual_dose!!,0.0)
        repo.deleteRecord(original.id,now);val deleted=dump();val replay=write();assertEquals(0,replay.addedDoses);assertEquals(deleted,dump());assertNotNull(db.dao().recordById(original.id)!!.deleted_at_utc)
    }
    @Test fun insertFailureRollsBackClinicalRowsSourceReceiptsAndCommitAcknowledgement()=runBlocking {
        val before=dump();db.openHelper.writableDatabase.execSQL("CREATE TRIGGER synthetic_mona_failure BEFORE INSERT ON lab_value WHEN NEW.analyte_code='T' BEGIN SELECT RAISE(ABORT,'synthetic failure');END")
        val receipt=MonaCommitReceipt()
        try {write(receipt=receipt);fail("Failure injection must abort")}catch(_:android.database.SQLException){}
        assertNull(receipt.committed);assertEquals(before,dump());assertEquals(0,count("external_import_item"));assertEquals(0,count("medication"))
    }
    @Test fun cancellationInsideActualWriteRollsBackTheWholeTransaction()=runBlocking {
        val p=MonaMigration.plan(export());val before=dump()
        try {db.withTransaction {MonaImportWriter.write(db.dao(),db.openHelper.writableDatabase,p,emptyMap(),emptyMap(),now) {
            if(count("dose_record")>0)throw CancellationException("synthetic cancellation")
        }};fail("Cancellation was ignored")}catch(_:CancellationException){}
        assertEquals(before,dump());assertEquals(0,count("external_import_item"))
    }
    @Test fun receiptCannotBeReusedForAnotherAttemptAndNeverAcknowledgesBeforeCommit()=runBlocking {
        val receipt=MonaCommitReceipt();write(receipt=receipt);val before=dump()
        try {write(receipt=receipt);fail("Receipt reuse accepted")}catch(_:IllegalStateException){}
        assertNotNull(receipt.committed);assertEquals(before,dump())
    }
    @Test fun newLabPartJoinsOnlyItsUnmodifiedOriginalSourceDraw()=runBlocking {
        val r=root();r.getJSONObject("data").getJSONArray("blood_tests").getJSONObject(0).remove("testosteroneLevels");write(r)
        val panel=repo.labPanels().single();val next=write();assertEquals(1,next.addedLabs);assertEquals(0,next.addedDraws);assertEquals(panel.uuid,repo.labPanels().single().uuid);assertEquals(2,repo.labs().size)
    }
    @Test fun encryptedBusinessFourteenRoundTripPreservesSourcesAndOldReaderRefuses()=runBlocking {
        write();val before=dump();val archive=repo.exportBackup("synthetic passphrase".toCharArray())
        val plain=BackupCodec.decrypt(archive,"synthetic passphrase".toCharArray())
        try {
            val json=JSONObject(String(plain));assertEquals(14,json.getInt("schema"))
            try{RecordBackupCompatibility.readVersion(json,13);fail("Old reader accepted Mona identity")}
            catch(_:BackupCodec.NewerBackup){}
            val bad=BackupCodec.encrypt(json.put("schema",13).toString().toByteArray(),"synthetic passphrase".toCharArray())
            try{repo.restoreBackup(bad,"synthetic passphrase".toCharArray());fail("Misdeclared source schema accepted")}catch(_:Exception){}
            assertEquals(before,dump())
            repo.restoreBackup(archive,"synthetic passphrase".toCharArray());assertEquals(before,dump())
            assertEquals(0,write().addedDoses);assertEquals(0,write().addedLabs)
        } finally {plain.fill(0)}
    }
    @Test fun legacyExportOmitsUnusedSourceTableAndKeepsSchemaTwelve()=runBlocking {
        val archive=repo.exportBackup("synthetic passphrase".toCharArray())
        val plain=BackupCodec.decrypt(archive,"synthetic passphrase".toCharArray())
        try{val json=JSONObject(String(plain));assertEquals(12,json.getInt("schema"));assertFalse(json.getJSONObject("tables").has("external_import_item"))}
        finally{plain.fill(0)}
    }
    @Test fun clinicalTrashRestoreAndPurgeKeepExactSourceOwnershipWithoutResurrection()=runBlocking {
        write();val e2=db.dao().labs().first{it.analyte_code=="E2"}
        repo.deleteLab(e2.id,now)
        val trash=db.dao().trash().single();ExternalImportSchema.validate(db.openHelper.writableDatabase)
        assertEquals(0,write().addedLabs)
        repo.restoreTrash(trash.id,now);ExternalImportSchema.validate(db.openHelper.writableDatabase)
        assertEquals(0,write().addedLabs)
        val restored=db.dao().labs().first{it.analyte_code=="E2"};repo.deleteLab(restored.id,now)
        val deleted=db.dao().trash().single();assertTrue(repo.purgeTrash(deleted.id))
        ExternalImportSchema.validate(db.openHelper.writableDatabase)
        db.openHelper.writableDatabase.query("SELECT source_json,retention FROM external_import_item WHERE source_part='E2'").use{assertTrue(it.moveToFirst());assertTrue(it.isNull(0));assertEquals("REMOVED",it.getString(1))}
        db.openHelper.writableDatabase.query("SELECT source_json FROM external_import_item WHERE source_part='T'").use{assertTrue(it.moveToFirst());assertFalse(JSONObject(it.getString(0)).has("estradiolLevels"))}
        assertEquals(0,write().addedLabs)
    }

    @Test fun missingTargetsAndMultipleConflictsAreReportedWithoutRecreation()=runBlocking {
        write();val rows=repo.records().sortedBy{it.id}
        db.withTransaction {RawData.unguarded(db.openHelper.writableDatabase) {
            db.openHelper.writableDatabase.execSQL("DELETE FROM dose_record WHERE id=?",arrayOf(rows[0].id))
        }}
        db.dao().updateRecord(rows[1].copy(actual_dose=3.0,revision=2))
        val before=dump();val replay=write()
        assertEquals(2,replay.conflicts);assertEquals(0,replay.addedDoses)
        assertTrue(replay.reviews.any{it.status==MonaItemStatus.MISSING_TARGET})
        assertTrue(replay.reviews.any{it.status==MonaItemStatus.LOCAL_CHANGED})
        assertEquals(before,dump())
    }
}
