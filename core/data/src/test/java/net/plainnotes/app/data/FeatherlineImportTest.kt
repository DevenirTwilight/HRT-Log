package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import net.plainnotes.app.importer.Featherline
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class FeatherlineImportTest {
    private lateinit var db:NotesDatabase
    private lateinit var repo:NotesRepository
    private val now=Instant.parse("2026-10-25T12:00:00Z")
    private val raw get()=db.openHelper.writableDatabase
    @Before fun open(){val context=ApplicationProvider.getApplicationContext<Context>();db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build();raw;repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})}
    @After fun close(){db.close()}
    private fun root()=JSONObject(File("src/test/resources/featherline/clinical-s7.json").readText())
    private suspend fun write(json:JSONObject=root(),receipt:FeatherlineCommitReceipt=FeatherlineCommitReceipt())=repo.importFeatherline(Featherline.read(json.toString()),receipt,now)
    private fun dump()=RawData.dump(raw).toString()
    private fun count(table:String)=raw.query("SELECT COUNT(*) FROM $table").use{it.moveToFirst();it.getInt(0)}
    @Test fun realCommitPreservesClinicalFactsWithoutPkStockOrPlan()=runBlocking {
        val receipt=FeatherlineCommitReceipt();val p=repo.previewFeatherline(Featherline.read(root().toString()));assertEquals(1,p.newDoses);assertEquals(2,p.newLabs);assertNull(receipt.committed)
        val result=write(receipt=receipt);assertEquals(result,receipt.committed);assertEquals(1,result.doses);assertEquals(2,result.labs);assertEquals(1,result.panels)
        val dose=repo.records().single();assertEquals("IMPORT_FEATHERLINE",dose.origin);assertEquals(1.0,dose.actual_dose!!,0.0);assertEquals(1792888200000L,dose.taken_utc);assertEquals("Europe/Paris",dose.taken_zone)
        assertTrue(JSONObject(dose.config_snapshot).isNull("pk_profile"));assertEquals("EV",JSONObject(dose.config_snapshot).getJSONObject("external_context").getString("ester"))
        assertEquals(listOf("ng/dL","pg/mL"),repo.labs().map{it.unit}.sorted());assertEquals(210.0,repo.labs().single{it.analyte_code=="E2"}.value,0.0)
        for(table in listOf("schedule_rule","pk_profile","supply_container","supply_transaction","lab_context_revision","external_import_item"))assertEquals(table,0,count(table))
        assertEquals(3,count("featherline_import_item"));FeatherlineImportSchema.validate(raw)
    }
    @Test fun sameUuidIsIdempotentButChangedSourceAndLocalEditAreConflicts()=runBlocking {
        write();val before=dump();assertEquals(3,write().existing);assertEquals(before,dump())
        val changed=root();changed.getJSONArray("medicationLogs").getJSONObject(0).put("count",2)
        assertEquals(1,write(changed).conflicts);assertEquals(before,dump())
        val r=repo.records().single();db.dao().updateRecord(r.copy(actual_dose=4.0,revision=2));val edited=dump()
        assertEquals(1,write().conflicts);assertEquals(edited,dump())
        val distinct=root();distinct.getJSONArray("medicationLogs").getJSONObject(0).put("uuid","00000000-0000-4000-8000-000000000009")
        assertEquals(1,write(distinct).doses);assertEquals(2,repo.records().size)
    }
    @Test fun clinicalDeleteRestoreAndPurgeRetainTypedSourceTombstones()=runBlocking {
        write();repo.deleteRecord(repo.records().single().id,now);val deleted=dump();assertEquals(0,write().doses);assertEquals(deleted,dump())
        val recordTrash=db.dao().trash().single();repo.restoreTrash(recordTrash.id,now);assertEquals(0,write().doses)
        val e2=repo.labs().single{it.analyte_code=="E2"};repo.deleteLab(e2.id,now);FeatherlineImportSchema.validate(raw)
        assertEquals(0,write().labs);repo.restoreTrash(db.dao().trash().single().id,now);FeatherlineImportSchema.validate(raw)
        repo.deleteLab(repo.labs().single{it.analyte_code=="E2"}.id,now);assertTrue(repo.purgeTrash(db.dao().trash().single().id))
        FeatherlineImportSchema.validate(raw);assertEquals(0,write().labs)
        raw.query("SELECT source_json,retention FROM featherline_import_item WHERE source_kind='RESULT' AND source_id LIKE '%004'").use{assertTrue(it.moveToFirst());assertTrue(it.isNull(0));assertEquals("REMOVED",it.getString(1))}
    }
    @Test fun failedInsertAndCancellationRollBackAllClinicalFactsAndReceipts()=runBlocking {
        val before=dump();raw.execSQL("CREATE TRIGGER synthetic_featherline_failure BEFORE INSERT ON lab_value WHEN NEW.analyte_code='T' BEGIN SELECT RAISE(ABORT,'synthetic failure');END")
        val receipt=FeatherlineCommitReceipt();try{write(receipt=receipt);fail("Invalid second result committed")}catch(_:android.database.SQLException){}
        assertNull(receipt.committed);assertEquals(before,dump());raw.execSQL("DROP TRIGGER synthetic_featherline_failure")
        var checks=0
        try {db.withTransaction{FeatherlineImportWriter.write(db.dao(),raw,Featherline.read(root().toString()),now){if(++checks==4)throw CancellationException("synthetic cancel")}};fail("Cancel committed")}
        catch(_:CancellationException){}
        assertEquals(before,dump())
    }
    @Test fun nativeBusinessFifteenRoundTripsAndOldReaderRejectsBeforeMutation()=runBlocking {
        write();val before=dump();val archive=repo.exportBackup("synthetic passphrase".toCharArray());val plain=BackupCodec.decrypt(archive,"synthetic passphrase".toCharArray())
        try {
            val json=JSONObject(String(plain));assertEquals(15,json.getInt("schema"));assertTrue(json.getJSONObject("tables").has("external_import_item"))
            try{RecordBackupCompatibility.readVersion(json,14);fail("Old reader accepted Featherline") }catch(_:BackupCodec.NewerBackup){}
            val bad=BackupCodec.encrypt(json.put("schema",14).toString().toByteArray(),"synthetic passphrase".toCharArray())
            var refused=false;try{repo.restoreBackup(bad,"synthetic passphrase".toCharArray())}catch(_:Exception){refused=true};assertTrue(refused);assertEquals(before,dump())
            repo.restoreBackup(archive,"synthetic passphrase".toCharArray());assertEquals(before,dump());assertEquals(3,write().existing)
        } finally{plain.fill(0)}
    }
    @Test fun editedSourcePanelAndChangedMembershipDoNotAcceptNewResult()=runBlocking {
        write();val panel=repo.labPanels().single();raw.execSQL("UPDATE lab_panel SET note='SYNTHETIC edited' WHERE id=?",arrayOf(panel.id))
        val next=root();next.getJSONArray("bloodTestPanels").getJSONObject(0).getJSONArray("results").getJSONObject(0).put("uuid","00000000-0000-4000-8000-000000000019")
        val before=dump();val result=write(next);assertEquals(0,result.labs);assertEquals(1,result.conflicts);assertEquals(before,dump())
    }
    @Test fun commitReceiptCannotBeReplayed()=runBlocking {
        val receipt=FeatherlineCommitReceipt();write(receipt=receipt);val before=dump()
        try{write(receipt=receipt);fail("Receipt reused")}catch(_:IllegalStateException){}
        assertEquals(before,dump())
    }
}
