package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import androidx.test.core.app.ApplicationProvider
import net.plainnotes.app.importer.Chrysalide
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class ChrysalideImportTest {
    private lateinit var db:NotesDatabase;private lateinit var repo:NotesRepository;private lateinit var raw:androidx.sqlite.db.SupportSQLiteDatabase
    @Before fun open(){val c=ApplicationProvider.getApplicationContext<Context>();db=Room.inMemoryDatabaseBuilder(c,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build();raw=db.openHelper.writableDatabase;repo=NotesRepository(object:DatabaseAccess(c){override fun get(space:Space)=db})}
    @After fun close(){db.close()}
    private val json="""{"version":5,"exportedAt":"2026-10-26T00:00:00Z","medications":[{"id":7,"name":"Estradiol source","type":"estrogen","dosage":2,"unit":"mg","method":"pill","startDate":"2024-01-01T00:00:00.000Z","createdAt":"2024-01-01T00:00:00.000Z","updatedAt":"2024-01-01T00:00:00.000Z","isActive":true}],"medicationLogs":[{"id":1,"medicationId":7,"timestamp":"2024-03-31T07:30:00Z","taken":true,"scheduledTime":"08:00","doseIndex":0,"notes":"synthetic"},{"id":2,"medicationId":7,"timestamp":"2024-03-31T08:00:00Z","taken":false,"scheduledTime":"08:00"}],"bloodTests":[{"id":3,"date":"2024-02-01","lab":"synthetic","results":[{"marker":"estradiol","value":120,"unit":"pg/mL"}]}],"physicalProgress":[],"appointments":[],"reminders":[],"userProfile":[],"journalEntries":[],"objectives":[],"milestones":[],"treatmentChanges":[],"practitioners":[],"acts":[],"actTodos":[]}"""
    private fun export()=Chrysalide.read(json)
    private suspend fun target()=db.dao().insertMedication(MedicationEntity(name="Synthetic HRT medication",molecule="E2",route="ORAL",unit="MG",dose_per_intake=2.0,container_capacity=2.0,site_rotation=false,notifications_on=false,active=false,sort_order=0))
    @Test fun doseUnknownHistoryIsExplicitIdempotentAndTombstoneProtected()=runBlocking {
        val e=export();val med=target();val before=repo.previewChrysalide(e);assertEquals(1,before.newCount);assertEquals(1,before.plan.export.bloodTests.size)
        val receipt=ChrysalideCommitReceipt();val result=repo.importChrysalide(e,mapOf(7L to med),receipt,Instant.parse("2026-10-26T12:00:00Z"))
        assertEquals(result,receipt.committed);assertEquals(1,result.added);assertEquals(2,result.unsupported)
        val record=db.dao().records().single();assertEquals("IMPORT_CHRYSALIDE",record.origin);assertNull(record.actual_dose);assertEquals("UTC",record.taken_zone);assertEquals("chry:",record.source_record_key!!.take(5))
        assertEquals("Estradiol source",JSONObject(record.config_snapshot).getJSONObject("medication").getString("name"))
        ChrysalideImportSchema.validate(raw);assertEquals(1,repo.previewChrysalide(e).items.count{it.status==ChrysalideItemStatus.EXISTING})
        repo.deleteRecord(record.id,Instant.parse("2026-10-26T13:00:00Z"));val trash=db.dao().trash().single()
        assertEquals(0,repo.importChrysalide(e,mapOf(7L to med)).added);ChrysalideReceiptStore.validateTrash(raw)
        repo.restoreTrash(trash.id);assertEquals(0,repo.importChrysalide(e,mapOf(7L to med)).added)
        repo.deleteRecord(record.id,Instant.parse("2026-10-26T14:00:00Z"));assertTrue(repo.purgeTrash(db.dao().trash().single().id))
        assertEquals(0,repo.importChrysalide(e,mapOf(7L to med)).added);raw.query("SELECT retention,source_json FROM chrysalide_import_item").use{assertTrue(it.moveToFirst());assertEquals("REMOVED",it.getString(0));assertTrue(it.isNull(1))}
        ChrysalideImportSchema.validate(raw)
    }
    @Test fun userEditsAreConflictAndTransactionFailureRollsBackReceiptAndRecord()=runBlocking {
        val e=export();val med=target();repo.importChrysalide(e,mapOf(7L to med))
        val old=db.dao().records().single()
        val changed=Chrysalide.read(json.replace("synthetic","other synthetic"))
        assertEquals(1,repo.previewChrysalide(changed).conflicts) // Same local integer ID, distinct export/content: ambiguous, never rebound.
        db.dao().updateRecord(old.copy(note="local edit",revision=2))
        assertEquals(1,repo.previewChrysalide(e).conflicts);assertEquals(0,repo.importChrysalide(e,mapOf(7L to med)).added)
        assertEquals(1,repo.previewChrysalide(changed).conflicts)
        assertEquals(0,repo.importChrysalide(changed,mapOf(7L to med)).added)
        val other=Chrysalide.read(json.replace("\"id\":1", "\"id\":9"))
        val snapshot=RawData.dump(raw).toString()
        raw.execSQL("CREATE TRIGGER synthetic_chry_failure BEFORE INSERT ON dose_record WHEN NEW.origin='IMPORT_CHRYSALIDE' BEGIN SELECT RAISE(ABORT,'synthetic failure');END")
        try{repo.importChrysalide(other,mapOf(7L to med));fail("Expected rollback")}catch(_:Exception){}
        assertEquals(snapshot,RawData.dump(raw).toString());ChrysalideImportSchema.validate(raw)
    }
    @Test fun businessSchema16IncludesReceiptsAndOldReadersRefuseIt()=runBlocking {
        val med=target();repo.importChrysalide(export(),mapOf(7L to med))
        val archive=repo.exportBackup("synthetic backup password".toCharArray());val plain=BackupCodec.decrypt(archive,"synthetic backup password".toCharArray())
        try{val json=JSONObject(String(plain));assertEquals(16,json.getInt("schema"));assertTrue(json.getJSONObject("tables").has("chrysalide_import_item"));assertThrowsNewer(json,15)
            repo.restoreBackup(archive,"synthetic backup password".toCharArray());ChrysalideImportSchema.validate(raw)
        }finally{plain.fill(0)}
    }
    private fun assertThrowsNewer(json:JSONObject,reader:Int){try{RecordBackupCompatibility.readVersion(json,reader);fail("Old reader accepted Chrysalide schema")}catch(_:BackupCodec.NewerBackup){}}
}
