package net.plainnotes.app.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.measurement.*
import net.plainnotes.app.domain.stock.StockCapacityBasis
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class MeasuredContainerStoreTest {
    private lateinit var db:NotesDatabase
    private val at=Instant.parse("2026-10-16T12:00:00Z")
    private val product=UserReferenceProduct("SYNTHETIC tablets",listOf("SYNTHETIC ingredient"),"2 mg / tablet","tablet",listOf("oral"),sourceVersion="synthetic-v1",recordedOn="2026-10-16")
    private val draft=PackageMeasurementDraft(product,MeasureUnit.TABLET,Meaning(QuantityKind.USE_COUNT,"SYNTHETIC tablets 2mg"),"0.5","28",StockCapacityBasis.USER_COUNTED)
    private val raw get()=db.openHelper.writableDatabase
    @Before fun setup(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()}
    @After fun cleanup(){db.close()}
    private suspend fun med(name:String="SYNTHETIC medication")=db.dao().insertMedication(MedicationEntity(name=name,molecule="OTHER",unit="MG",dose_per_intake=1.0,container_capacity=28.0,site_rotation=false,notifications_on=false,active=false,sort_order=0))
    private suspend fun epoch()=db.withTransaction{ReferenceStore.previewEpoch(raw)}
    private suspend fun create(med:Long,op:String,source:PackageMeasurementDraft=draft,ticket:String?=null)=db.withTransaction{MeasuredContainerStore.create(raw,db.dao(),ticket ?: ReferenceStore.previewEpoch(raw),med,op,source,at)}
    private fun count(table:String)=raw.query("SELECT COUNT(*) FROM `$table`").use{it.moveToFirst();it.getInt(0)}
    private suspend fun reject(code:ReferenceError,block:suspend()->Unit){try{block();fail("Expected $code")}catch(e:ReferenceFailure){assertEquals(code,e.code)}}
    @Test fun explicitContainerCreationFreezesASeparateUnitWithoutChangingMedicationOrLegacyStock()=runBlocking {
        val med=med();val before=db.dao().medication(med)
        val old=db.dao().insertContainer(ContainerEntity(medication_id=med,capacity=100.0,initial_used_amount=2.0,used_amount=2.0,state="IN_USE",opened_on="2026-10-15"))
        val legacy=db.dao().container(old)
        val result=create(med,UUID.randomUUID().toString())
        assertEquals(56.0,result.container.capacity,0.0);assertEquals("SEALED",result.container.state)
        assertEquals(draft.product,result.presentation.product());assertEquals(before,db.dao().medication(med));assertEquals(legacy,db.dao().container(old))
        assertNull(db.dao().profile(med));assertTrue(db.dao().records().isEmpty());assertEquals(0,count("supply_transaction"))
        assertEquals(1,count("reference_snapshot"));assertEquals(1,count("reference_event"));assertEquals(2,count("reference_identity"))
    }
    @Test fun sameOperationReplaysOriginalContainerButDifferentPayloadOrOwnerIsRejected()=runBlocking {
        val med=med();val ticket=epoch();val op=UUID.randomUUID().toString();val first=create(med,op,ticket=ticket)
        val replay=create(med,op,ticket=ticket)
        assertEquals(first.container,replay.container);assertEquals(first.containerUuid,replay.containerUuid);assertEquals(1,count("supply_container"))
        reject(ReferenceError.OPERATION_CONFLICT){create(med,op,draft.copy(confirmedCapacity="14"),ticket)}
        val other=med("SYNTHETIC other medication");reject(ReferenceError.OPERATION_CONFLICT){create(other,op,ticket=ticket)}
        assertEquals(1,count("supply_container"));assertEquals(2,count("reference_identity"));assertEquals(1,count("reference_snapshot"))
    }
    @Test fun failureAfterContainerInsertionRollsBackIdentitySnapshotAndContainerTogether()=runBlocking {
        val med=med();val ticket=epoch();val op=UUID.randomUUID().toString()
        raw.execSQL("CREATE TRIGGER synthetic_package_failure BEFORE INSERT ON reference_event BEGIN SELECT RAISE(ABORT,'synthetic failure'); END")
        try{create(med,op,ticket=ticket);fail("Expected injected failure")}catch(_:android.database.sqlite.SQLiteException){}
        for(table in ReferenceSchema.businessTables+"supply_container")assertEquals(table,0,count(table))
        assertEquals(ticket,epoch());assertEquals(1,count("medication"))
    }
    @Test fun voidedReplayDoesNotCreateOrRestoreStock()=runBlocking {
        val med=med();val ticket=epoch();val op=UUID.randomUUID().toString();val created=create(med,op,ticket=ticket)
        db.dao().setContainerState(created.container.id,"VOIDED",null)
        val replay=create(med,op,ticket=ticket)
        assertEquals("VOIDED",replay.container.state);assertEquals(1,count("supply_container"));assertEquals(0,count("supply_transaction"))
    }
    @Test fun physicalDeletionAndReusedIntegerIdNeverReattachOldSource()=runBlocking {
        val med=med();val ticket=epoch();val op=UUID.randomUUID().toString();val original=create(med,op,ticket=ticket)
        db.dao().deleteContainer(original.container.id)
        val replacement=original.container.copy(capacity=99.0)
        db.dao().insertContainer(replacement)
        reject(ReferenceError.STALE_SCOPE){create(med,op,ticket=ticket)}
        reject(ReferenceError.UNKNOWN_IDENTITY){create(med,op,ticket=epoch())}
        assertEquals(replacement,db.dao().container(replacement.id));assertEquals(1,count("reference_snapshot"))
    }
    @Test fun productLinkOperationCannotBeReusedAsContainerCreation()=runBlocking {
        val med=med();val op=UUID.randomUUID().toString();val ticket=epoch()
        db.withTransaction {val scope=ReferenceStore.scope(raw);ReferenceStore.associate(raw,scope,med,0,op,product)}
        reject(ReferenceError.OPERATION_CONFLICT){create(med,op,ticket=ticket)}
        assertEquals(0,count("supply_container"));assertEquals(1,count("reference_event"));assertEquals(1,count("reference_snapshot"))
    }
    @Test fun inactiveFormatRemainsBlockedFromCurrentBackupAcceptance()=runBlocking {
        val med=med();BackupValidation.validateState(raw);create(med,UUID.randomUUID().toString())
        try{BackupValidation.validateState(raw);fail("PRESENTATION was accepted before the format fence and full integration")}catch(e:IllegalArgumentException){assertTrue(e.message.orEmpty().contains("Presentation storage not yet enabled"))}
    }
    @Test fun adapterRequiresTheCallersRealDatabaseTransaction()=runBlocking {
        val med=med();val ticket=epoch()
        try{MeasuredContainerStore.create(raw,db.dao(),ticket,med,UUID.randomUUID().toString(),draft,at);fail("Nontransactional write accepted")}catch(_:IllegalStateException){}
        assertEquals(0,count("supply_container"));assertEquals(0,count("reference_dataset"))
    }
}
