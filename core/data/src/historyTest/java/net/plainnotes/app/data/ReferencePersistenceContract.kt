package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import net.plainnotes.app.domain.RuleKind
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

/** Real production reference/Room/PNBAK1 calls. Every record and source here is synthetic. */
abstract class ReferencePersistenceContract {
    protected abstract fun factory(key: ByteArray): SupportSQLiteOpenHelper.Factory
    protected open fun encrypted(name: String) {}
    protected val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var name: String
    private lateinit var db: NotesDatabase
    private lateinit var repo: NotesRepository
    private val key=ByteArray(32) { (it+91).toByte() }
    private val raw get()=db.openHelper.writableDatabase
    private val at=Instant.parse("2026-03-28T12:00:00Z")
    private val password get()="SYNTHETIC-reference-password".toCharArray()
    private fun open() {
        db=Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone()))
            .addMigrations(*allMigrations()).addCallback(SchemaGuards).allowMainThreadQueries().build()
        raw
        repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
    }
    @Before fun setup() {name="synthetic-reference-${UUID.randomUUID()}.db";open()}
    @After fun cleanup() {db.close();context.deleteDatabase(name)}
    private fun id()=UUID.randomUUID().toString()
    private fun product(label:String="SYNTHETIC user-entered product",version:String="synthetic-v1")=UserReferenceProduct(name=label,
        ingredients=listOf("SYNTHETIC ingredient A","SYNTHETIC ingredient B"),strength="2 mg / tablet (synthetic)",form="tablet",routes=listOf("oral","other user-recorded route"),sourceVersion=version,recordedOn="2026-10-16")
    private suspend fun med(label:String="SYNTHETIC medication")=repo.saveMedication(MedicationEntity(name=label,molecule="OTHER",unit="TABLET",dose_per_intake=1.0,
        container_capacity=10.0,soon_alert_minutes=0,late_after_minutes=60,site_rotation=false,notifications_on=false,active=true,sort_order=0),null,RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.NOON),emptySet(),at)
    private fun business()=RawData.dump(raw).toString()
    private fun legacy()=RawData.dump(raw,DOMAIN_TABLES.filterNot { it in ReferenceSchema.businessTables }).toString()
    private fun guards()=raw.query("SELECT name,sql FROM sqlite_master WHERE type='trigger' ORDER BY name").use { c->buildList {while(c.moveToNext())add(c.getString(0) to c.getString(1))} }
    private fun count(table:String)=raw.query("SELECT COUNT(*) FROM `$table`").use {it.moveToFirst();it.getInt(0)}
    private fun failure(code:ReferenceError, block:suspend ()->Unit)=runBlocking {
        try {block();fail("Expected reference rejection: $code")} catch(e:ReferenceFailure) {assertEquals(code,e.code)}
    }
    @Test fun linkingCorrectingAndUnlinkingNeverRewritesLegacyFactsOrStock()=runBlocking {
        val medication=med();repo.addContainers(medication,10.0,1,true);repo.unscheduled(medication,at,1.0)
        val before=legacy();val scope=repo.referenceScope()
        val first=repo.associateMedication(scope,medication,0,id(),product());assertEquals(1,first.revision)
        val second=repo.associateMedication(scope,medication,1,id(),product(version="synthetic-v2"));assertEquals(2,second.revision)
        val third=repo.associateMedication(scope,medication,2,id(),null);assertEquals(3,third.revision);assertNull(third.product)
        assertEquals(before,legacy());assertEquals(0,count("record_reference_capture"))
        assertEquals(3,count("reference_event"));assertEquals(2,count("reference_snapshot"))
        assertEquals(1,db.dao().records().single().revision)
        assertEquals(listOf("CONSUME"),db.dao().supplyFor(db.dao().records().single().id).map {it.kind})
    }
    @Test fun sameOperationIsIdempotentEvenAfterLaterUnlinkButChangedPayloadIsRejected()=runBlocking {
        val medication=med();val scope=repo.referenceScope();val operation=id();val value=product()
        val first=repo.associateMedication(scope,medication,0,operation,value)
        assertEquals(first,repo.associateMedication(scope,medication,0,operation,value))
        repo.associateMedication(scope,medication,1,id(),null)
        val before=business()
        assertEquals(first,repo.associateMedication(scope,medication,0,operation,value))
        failure(ReferenceError.OPERATION_CONFLICT){repo.associateMedication(scope,medication,0,operation,product(version="different"))}
        assertEquals(before,business());assertNull(repo.medicationReference(scope,medication)!!.product)
    }
    @Test fun staleAndRacingEditorsCannotAppendTwoHeadsOrOrphanSnapshots()=runBlocking {
        val medication=med();val scope=repo.referenceScope()
        val results=(1..2).map { i->async(Dispatchers.Default) {
            try {repo.associateMedication(scope,medication,0,id(),product("SYNTHETIC editor $i"));null}
            catch(e:ReferenceFailure){e.code}
        }}.awaitAll()
        assertEquals(1,results.count {it==null});assertEquals(1,results.count {it==ReferenceError.STALE_REVISION})
        assertEquals(1,count("reference_event"));assertEquals(1,count("reference_snapshot"))
    }
    @Test fun futureRecordCapturesContextNotActualPackageAndCorrectionsLeaveItFrozen()=runBlocking {
        val medication=med();repo.unscheduled(medication,at.minusSeconds(86400),1.0)
        val scope=repo.referenceScope();val first=repo.associateMedication(scope,medication,0,id(),product(version="before"))
        repo.unscheduled(medication,at,1.0)
        assertEquals(1,count("record_reference_capture"))
        val capture=RawData.dump(raw).getJSONArray("record_reference_capture").getJSONObject(0)
        assertEquals("CONTEXT",capture.getString("kind"));assertTrue(capture.isNull("actual_snapshot_uuid"));assertTrue(capture.isNull("container_uuid"))
        assertEquals(first.operationUuid,capture.getString("context_event_uuid"))
        val before=capture.toString();val legacy=legacy()
        repo.associateMedication(scope,medication,1,id(),product(version="after"))
        assertEquals(before,RawData.dump(raw).getJSONArray("record_reference_capture").getJSONObject(0).toString())
        assertEquals(legacy,legacy());assertEquals(2,db.dao().records().size)
    }
    @Test fun completeNewBackupRoundtripRetiresOldEditingEpochAndReopensWithoutCatalog()=runBlocking {
        val medication=med();val scope=repo.referenceScope();val operation=id()
        repo.associateMedication(scope,medication,0,operation,product());repo.addContainers(medication,10.0,1,true)
        repo.unscheduled(medication,at,1.0);val record=db.dao().records().single();repo.editRecord(record.id,at.plusSeconds(60),1.5,at.plusSeconds(86400))
        val before=business();val expectedGuards=guards();val backup=repo.exportBackup(password)
        val decoded=JSONObject(String(BackupCodec.decrypt(backup,password),Charsets.UTF_8))
        assertEquals(10,decoded.getInt("schema"));assertFalse(decoded.getJSONObject("tables").has("reference_runtime"))
        repeat(2) {
            repo.restoreBackup(backup,password)
            assertEquals(before,business());assertEquals(expectedGuards,guards())
            failure(ReferenceError.STALE_SCOPE){repo.associateMedication(scope,medication,0,operation,product())}
        }
        val fresh=repo.referenceScope();assertEquals(scope.dataset,fresh.dataset);assertNotEquals(scope.epoch,fresh.epoch)
        assertEquals(product(),repo.medicationReference(fresh,medication)!!.product)
        db.close();encrypted(name);open()
        assertEquals(before,business());assertEquals(fresh,repo.referenceScope())
        assertEquals(product(),repo.medicationReference(fresh,medication)!!.product)
        raw.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())};backup.fill(0)
    }
    @Test fun oldSchemaNineBackupClearsNewReferencesAndKeepsLegacyStockUnknown()=runBlocking {
        val medication=med();repo.addContainers(medication,10.0,1,true);repo.unscheduled(medication,at,1.0)
        val legacy=legacy();val oldTables=RawData.dump(raw,DOMAIN_TABLES.filterNot {it in ReferenceSchema.businessTables})
        val archive=BackupCodec.encrypt(JSONObject().put("format",1).put("schema",9).put("tables",oldTables).toString().toByteArray(),password)
        val scope=repo.referenceScope();repo.associateMedication(scope,medication,0,id(),product())
        repo.restoreBackup(archive,password)
        assertEquals(legacy,legacy());ReferenceSchema.businessTables.forEach {assertEquals(0,count(it))}
        failure(ReferenceError.STALE_SCOPE){repo.medicationReference(scope,medication)}
        val fresh=repo.referenceScope();assertNotEquals(scope.dataset,fresh.dataset);assertNull(repo.medicationReference(fresh,medication))
        assertEquals(1,db.dao().supplyFor(db.dao().records().single().id).size)
    }
    @Test fun corruptDigestOrEventChainFailsWithoutChangingTargetOrEpochOrGuards()=runBlocking {
        val medication=med();val scope=repo.referenceScope();repo.associateMedication(scope,medication,0,id(),product())
        val before=business();val expectedGuards=guards()
        val original=RawData.dump(raw)
        val mutations:List<(JSONObject)->Unit> = listOf(
            { it.getJSONArray("reference_snapshot").getJSONObject(0).put("payload_sha256","f".repeat(64));Unit },
            { it.getJSONArray("reference_event").getJSONObject(0).put("revision",2);Unit },
            { it.getJSONArray("reference_event").getJSONObject(0).put("request_sha256","e".repeat(64));Unit },
            { it.getJSONArray("reference_event").getJSONObject(0).put("previous_uuid",id());Unit },
            { it.getJSONArray("reference_identity").getJSONObject(0).put("dataset_uuid",id());Unit },
            { it.getJSONArray("reference_event").put(JSONObject(it.getJSONArray("reference_event").getJSONObject(0).toString()));Unit }
        )
        mutations.forEach { mutation->
            val invalid=JSONObject(original.toString());mutation(invalid)
            try {db.withTransaction {RawData.restore(context,raw,invalid)};fail("Corrupt reference accepted")} catch(_:Exception){}
            assertEquals(before,business());assertEquals(scope,repo.referenceScope());assertEquals(expectedGuards,guards())
        }
    }
    @Test fun injectedFailureRollsBackReferenceBusinessRuntimeAndLegacyRows()=runBlocking {
        val medication=med();val scope=repo.referenceScope();repo.associateMedication(scope,medication,0,id(),product())
        val before=business();val expected=guards();val source=RawData.dump(raw)
        try {db.withTransaction {RawData.restore(context,raw,source);error("SYNTHETIC-reference-before-commit")};fail("Failure lost")}
        catch(e:IllegalStateException){assertEquals("SYNTHETIC-reference-before-commit",e.message)}
        assertEquals(before,business());assertEquals(scope,repo.referenceScope());assertEquals(expected,guards())
    }
    @Test fun tombstoneCannotRebindReusedIntegerRecordIdOrRewriteOriginalCapture()=runBlocking {
        val medication=med();val scope=repo.referenceScope();repo.associateMedication(scope,medication,0,id(),product())
        repo.unscheduled(medication,at,1.0);val record=db.dao().records().single()
        val old=RawData.dump(raw).getJSONArray("record_reference_capture").getJSONObject(0).getString("record_uuid")
        db.withTransaction {raw.execSQL("DELETE FROM dose_record WHERE id=?",arrayOf(record.id))}
        raw.query("SELECT live_handle FROM reference_identity WHERE uuid=?",arrayOf(old)).use {assertTrue(it.moveToFirst());assertTrue(it.isNull(0))}
        db.dao().record(record.copy(id=record.id,taken_utc=at.plusSeconds(120).toEpochMilli()))
        db.withTransaction {ReferenceStore.captureContext(raw,db.dao().recordById(record.id)!!)}
        val identities=RawData.dump(raw).getJSONArray("record_reference_capture")
        assertEquals(2,identities.length());assertNotEquals(identities.getJSONObject(0).getString("record_uuid"),identities.getJSONObject(1).getString("record_uuid"))
        assertEquals(old,identities.getJSONObject(0).getString("record_uuid"))
    }
    @Test fun archivedMedicationRemainsAssociatedAndReferenceHistoryIsAppendOnly()=runBlocking {
        val medication=med();val scope=repo.referenceScope();repo.associateMedication(scope,medication,0,id(),product())
        db.dao().updateMedication(db.dao().medication(medication).copy(active=false))
        assertEquals(product(),repo.medicationReference(scope,medication)!!.product)
        listOf("reference_dataset","reference_identity","reference_snapshot","reference_event").forEach { table->
            val sql=if(table=="reference_identity") "UPDATE $table SET live_handle=NULL" else "DELETE FROM $table"
            try {raw.execSQL(sql);fail("History guard lost: $table")} catch(_:android.database.SQLException){}
        }
        ReferenceSchema.validate(raw)
    }
}
