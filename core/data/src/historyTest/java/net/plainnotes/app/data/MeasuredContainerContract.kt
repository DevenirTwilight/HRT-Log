package net.plainnotes.app.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.measurement.*
import net.plainnotes.app.domain.stock.StockCapacityBasis
import org.junit.*
import org.junit.Assert.*
import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.time.Instant
import java.util.UUID

abstract class MeasuredContainerContract {
    protected abstract fun factory(key:ByteArray):SupportSQLiteOpenHelper.Factory
    protected open fun encrypted(name:String) {}
    protected val context:Context get()=ApplicationProvider.getApplicationContext()
    private lateinit var name:String
    private val key=ByteArray(32){(it+53).toByte()}
    private lateinit var db:NotesDatabase
    private val at=Instant.parse("2026-10-16T12:00:00Z")
    private val product=UserReferenceProduct("SYNTHETIC tablets",listOf("SYNTHETIC ingredient"),"2 mg / tablet","tablet",listOf("oral"),sourceVersion="synthetic-v1",recordedOn="2026-10-16")
    private val draft=PackageMeasurementDraft(product,MeasureUnit.TABLET,Meaning(QuantityKind.USE_COUNT,"SYNTHETIC tablets 2mg"),"0.5","28",StockCapacityBasis.USER_COUNTED)
    private val raw get()=db.openHelper.writableDatabase
    private fun open(){db=Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone())).allowMainThreadQueries().addCallback(SchemaGuards).build();raw}
    @Before fun setup(){name="synthetic-measured-${UUID.randomUUID()}.db";open()}
    @After fun cleanup(){db.close();context.deleteDatabase(name)}
    private suspend fun med(name:String="SYNTHETIC medication")=db.dao().insertMedication(MedicationEntity(name=name,molecule="OTHER",unit="MG",dose_per_intake=1.0,container_capacity=28.0,site_rotation=false,notifications_on=false,active=false,sort_order=0))
    private suspend fun epoch()=db.withTransaction{ReferenceStore.previewEpoch(raw)}
    private suspend fun create(med:Long,op:String,source:PackageMeasurementDraft=draft,ticket:String?=null)=db.withTransaction{MeasuredContainerStore.create(db,ticket ?: ReferenceStore.previewEpoch(raw),med,op,source,at)}
    private fun count(table:String)=raw.query("SELECT COUNT(*) FROM `$table`").use{it.moveToFirst();it.getInt(0)}
    private suspend fun reject(code:ReferenceError,block:suspend()->Unit){try{block();fail("Expected $code")}catch(e:ReferenceFailure){assertEquals(code,e.code)}}
    @Test fun consumptionDecoderMatchesFrozenReceiptOracleForValidAndInterleavedRows() {ConsumptionReceiptOracle.validCorpus()}
    @Test fun consumptionDecoderPreservesFrozenMalformedReceiptRejections() {ConsumptionReceiptOracle.malformedCorpus()}
    @Test fun candidateColumnScanPreservesEveryStorageTypeAndTextBoundaryPredicate() {
        CandidateColumnScanOracle.check(context,factory(key.clone()),false)
    }
    @Test fun candidateColumnScanPreservesGlobalRowAndUtf8ByteBudgets() {
        CandidateColumnScanOracle.check(context,factory(key.clone()),true)
    }
    @Test fun repeatedPayloadValidationStillChecksEveryRowsHeaderDigestAndCompleteText()=runBlocking {
        val med=med();create(med,UUID.randomUUID().toString());create(med,UUID.randomUUID().toString())
        val payloads=raw.query("SELECT payload_json FROM reference_snapshot").use{c->buildList{while(c.moveToNext())add(c.getString(0))}}
        assertEquals(2,payloads.size);assertEquals(payloads[0],payloads[1])
        for(change in listOf("source_version='synthetic-changed'","namespace='WRONG'","kind='PRODUCT'",
            "payload_sha256='${"0".repeat(64)}'","payload_json=replace(payload_json,'SYNTHETIC tablets','SYNTHETIC changed')")) {
            var rejected=false
            try {db.withTransaction {
                raw.execSQL("DROP TRIGGER reference_immutable_reference_snapshot_update")
                raw.execSQL("UPDATE reference_snapshot SET $change WHERE rowid=(SELECT MAX(rowid) FROM reference_snapshot)")
                ReferenceSchema.validateCandidateReferences(raw)
                error("Modified repeated source accepted: $change")
            }} catch(_:IllegalArgumentException){rejected=true}
            assertTrue("Invalid repeated source rejected",rejected)
        }
        db.withTransaction{ReferenceSchema.validateCandidateReferences(raw)}
        assertEquals(2,count("reference_snapshot"))
    }
    @Test fun auditPrimitiveRowsReturnFreshObjectsAndIndependentCursorHeaders()=runBlocking {
        val med=med();val created=create(med,UUID.randomUUID().toString())
        db.withTransaction {
            val reads=ReferenceAuditReads(raw)
            try {
                val before=ReferenceStore.row(raw,"reference_identity","uuid=?",arrayOf(created.containerUuid))!!
                val copy=ReferenceStore.row(reads,"reference_identity","uuid=?",arrayOf(created.containerUuid))!!
                assertEquals(ReferencePayload.canonical(before),ReferencePayload.canonical(copy))
                copy.put("kind","MUTATED")
                assertEquals(ReferencePayload.canonical(before),ReferencePayload.canonical(ReferenceStore.row(reads,"reference_identity","uuid=?",arrayOf(created.containerUuid))!!))
                val sql="SELECT * FROM `reference_identity` WHERE uuid=?"
                val original=raw.query(sql,arrayOf(created.containerUuid)).use{it.getColumnName(0)}
                reads.query(sql,arrayOf(created.containerUuid)).use{it.columnNames[0]="MUTATED"}
                reads.query(sql,arrayOf(created.containerUuid)).use{assertEquals(original,it.getColumnName(0))}
                assertNull(ReferenceStore.row(reads,"reference_identity","uuid=? AND kind=?",arrayOf(created.containerUuid,"RECORD")))
            } finally{reads.finish()}
        }
    }
    @Test fun insufficientAuditCacheNeverTurnsExistingRowsIntoAbsence()=runBlocking {
        val med=med();val created=create(med,UUID.randomUUID().toString())
        for(limit in listOf(0L,1L,512L))db.withTransaction {
            val reads=ReferenceAuditReads(raw,limit)
            try {
                for(table in listOf("reference_identity","reference_snapshot","reference_event")) {
                    val key=if(table=="reference_event")"operation_uuid" else "uuid"
                    raw.query("SELECT $key FROM `$table`").use{c->while(c.moveToNext()) {
                        val id=c.getString(0)
                        assertEquals(ReferenceStore.row(raw,table,"$key=?",arrayOf(id)).toString(),ReferenceStore.row(reads,table,"$key=?",arrayOf(id)).toString())
                    }}
                }
                assertNotNull(ReferenceStore.row(reads,"reference_identity","uuid=?",arrayOf(created.containerUuid)))
                assertNull(ReferenceStore.row(reads,"reference_identity","uuid=?",arrayOf(UUID.randomUUID().toString())))
            } finally{reads.finish()}
        }
    }
    @Test fun auditReadScopeRejectsWritesAndCannotBeReusedInAnotherTransaction()=runBlocking {
        val med=med();val before=db.dao().medication(med)
        val ended=db.withTransaction {
            val reads=ReferenceAuditReads(raw)
            assertThrows(IllegalStateException::class.java){reads.execSQL("DELETE FROM medication")}
            assertThrows(IllegalStateException::class.java){reads.query("DELETE FROM medication")}
            assertThrows(IllegalStateException::class.java){reads.query("PRAGMA user_version=123")}
            assertThrows(IllegalStateException::class.java){reads.compileStatement("DELETE FROM medication")}
            reads.finish();reads
        }
        assertThrows(IllegalStateException::class.java){ended.query("SELECT 1")}
        db.withTransaction{assertThrows(IllegalStateException::class.java){ended.query("SELECT 1")}}
        assertEquals(before,db.dao().medication(med))
    }
    @Test fun auditDetectsUnderlyingWritesBeforeReturningAndTheCallerRollsBack()=runBlocking {
        val med=med();val before=db.dao().medication(med)
        var rejected=false
        try {db.withTransaction {
            val reads=ReferenceAuditReads(raw)
            raw.execSQL("UPDATE medication SET name='SYNTHETIC unexpected change' WHERE id=?",arrayOf(med))
            reads.finish()
        }} catch(_:IllegalStateException){rejected=true}
        assertTrue(rejected);assertEquals(before,db.dao().medication(med))
        db.withTransaction{val reads=ReferenceAuditReads(raw);reads.finish()}
    }
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
        try{create(med,op,ticket=ticket);fail("Expected injected failure")}catch(e:Exception){assertTrue(generateSequence<Throwable>(e){it.cause}.any{it.message.orEmpty().contains("synthetic failure")})}
        for(table in ReferenceSchema.businessTables+"supply_container")assertEquals(table,0,count(table))
        assertEquals(ticket,epoch());assertEquals(1,count("medication"))
    }
    @Test fun voidedReplayDoesNotCreateOrRestoreStock()=runBlocking {
        val med=med();val ticket=epoch();val op=UUID.randomUUID().toString();val created=create(med,op,ticket=ticket)
        db.withTransaction{PackageCorrections.void(raw,db.dao(),created.container.id,at)}
        val replay=create(med,op,ticket=ticket)
        assertEquals("VOIDED",replay.container.state);assertEquals(1,count("supply_container"));assertEquals(0,count("supply_transaction"))
        assertEquals(1,count("trash_item"))
        db.withTransaction{PackageCorrections.restore(raw,db.dao(),created.container.id,at.plusSeconds(1))}
        PackageCorrections.validate(raw)
        val restored=create(med,op,ticket=ticket)
        assertEquals("SEALED",restored.container.state);assertEquals(created.containerUuid,restored.containerUuid)
        assertEquals(2,count("trash_item"));assertEquals(1,count("reference_event"));assertEquals(0,count("supply_transaction"))
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
    @Test fun frozenContainerSurvivesCloseAndReopenWithoutRebinding()=runBlocking {
        val med=med();val ticket=epoch();val op=UUID.randomUUID().toString();val before=create(med,op,ticket=ticket)
        db.close();encrypted(name);open()
        val after=create(med,op,ticket=ticket)
        assertEquals(before.container,after.container);assertEquals(before.containerUuid,after.containerUuid)
        assertEquals(before.presentation.payload,after.presentation.payload);assertEquals(before.presentation.sha256,after.presentation.sha256)
        assertEquals(1,count("reference_event"));assertEquals(1,count("supply_container"));assertEquals(0,count("supply_transaction"))
    }
    @Test fun anotherDatasetWithTheSameIntegerIdCannotSupplyTheCreationTicket()=runBlocking {
        val med=med();val otherName="synthetic-other-measured-${UUID.randomUUID()}.db"
        val other=Room.databaseBuilder(context,NotesDatabase::class.java,otherName).openHelperFactory(factory(ByteArray(32){(it+7).toByte()})).allowMainThreadQueries().addCallback(SchemaGuards).build()
        try {
            val otherMed=other.dao().insertMedication(db.dao().medication(med).copy(name="SYNTHETIC other dataset"))
            assertEquals(med,otherMed)
            val otherTicket=other.withTransaction{ReferenceStore.previewEpoch(other.openHelper.writableDatabase)}
            reject(ReferenceError.STALE_SCOPE){create(med,UUID.randomUUID().toString(),ticket=otherTicket)}
            assertEquals(0,count("reference_dataset"));assertEquals(0,count("supply_container"))
            create(med,UUID.randomUUID().toString())
            assertTrue(other.dao().containers().isEmpty())
            other.openHelper.writableDatabase.query("SELECT COUNT(*) FROM reference_dataset").use{assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
        } finally {other.close();context.deleteDatabase(otherName)}
    }
    @Test fun adapterRequiresTheCallersRealDatabaseTransaction()=runBlocking {
        val med=med();val ticket=epoch()
        try{MeasuredContainerStore.create(db,ticket,med,UUID.randomUUID().toString(),draft,at);fail("Nontransactional write accepted")}catch(_:IllegalStateException){}
        assertEquals(0,count("supply_container"));assertEquals(0,count("reference_dataset"))
    }
}
