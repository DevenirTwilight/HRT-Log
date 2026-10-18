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
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

abstract class MeasuredConsumptionContract {
    protected abstract fun factory(key:ByteArray):SupportSQLiteOpenHelper.Factory
    protected open fun encrypted(name:String) {}
    protected val context:Context get()=ApplicationProvider.getApplicationContext()
    private lateinit var name:String
    private val key=ByteArray(32){(it+71).toByte()}
    private lateinit var db:NotesDatabase
    private val raw get()=db.openHelper.writableDatabase
    private val at=Instant.parse("2026-10-16T12:00:00Z")
    private val meaning=Meaning(QuantityKind.USE_COUNT,"SYNTHETIC 2mg tablet")
    private val product=UserReferenceProduct("SYNTHETIC tablets",listOf("SYNTHETIC ingredient"),"2 mg / tablet",sourceVersion="synthetic-v1",recordedOn="2026-10-16")
    @Before fun setup(){name="synthetic-consumption-${UUID.randomUUID()}.db";db=Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone())).allowMainThreadQueries().addCallback(SchemaGuards).build();raw;encrypted(name)}
    @After fun cleanup(){db.close();context.deleteDatabase(name)}
    private suspend fun med(name:String="SYNTHETIC medication")=db.dao().insertMedication(MedicationEntity(name=name,molecule="OTHER",unit="MG",dose_per_intake=0.1,container_capacity=28.0,site_rotation=false,notifications_on=false,active=false,sort_order=0))
    private suspend fun box(m:Long,identity:Meaning=meaning,strength:String="2 mg / tablet")=db.withTransaction{MeasuredContainerStore.create(db,ReferenceStore.previewEpoch(raw),m,UUID.randomUUID().toString(),PackageMeasurementDraft(product.copy(strength=strength),MeasureUnit.TABLET,identity,"0.5","28",StockCapacityBasis.USER_COUNTED),at)}
    private suspend fun record(m:Long):RecordEntity {
        val r=RecordEntity(medication_id=m,taken_utc=at.toEpochMilli(),taken_zone="Europe/Paris",actual_dose=0.1,status="ON_TIME",origin="APP",revision=1,config_snapshot="{}")
        return r.copy(id=db.dao().record(r))
    }
    private fun use(box:CreatedMeasuredContainer,n:String="0.5",identity:Meaning=meaning)=ConfirmedStockUse(box.containerUuid,box.presentationEventUuid,box.presentation.sha256,Quantity(BigDecimal(n),MeasureUnit.TABLET,identity),true)
    private suspend fun consume(r:RecordEntity,box:CreatedMeasuredContainer,uses:List<ConfirmedStockUse>,op:String=UUID.randomUUID().toString())=db.withTransaction{MeasuredConsumptionStore.consumeNewRecord(db,box.scope,r,op,uses,at)}
    private fun count(table:String)=raw.query("SELECT COUNT(*) FROM `$table`").use{it.moveToFirst();it.getInt(0)}
    private suspend fun rejects(code:ReferenceError,block:suspend()->Unit){try{block();fail("Expected $code")}catch(e:ReferenceFailure){assertEquals(code,e.code)}}
    @Test fun completeSecondaryAuditIndexesAvoidRepeatedSqlButPartialIndexesNeverProveAbsence()=runBlocking {
        val m=med();val a=box(m);val b=box(m);val r=record(m)
        val receipt=consume(r,a,listOf(use(a),use(b)))
        db.withTransaction{MeasuredConsumptionStore.reverseOriginal(db,a.scope,r,receipt.operationUuid,UUID.randomUUID().toString(),at)}
        db.withTransaction {
            val recordUuid=raw.query("SELECT record_uuid FROM measured_operation WHERE operation_uuid=?",arrayOf(receipt.operationUuid)).use{it.moveToFirst();it.getString(0)}
            val queries=listOf(
                "SELECT COUNT(*) FROM measured_allocation WHERE operation_uuid=? AND container_uuid=?" to arrayOf<Any?>(receipt.operationUuid,a.containerUuid),
                "SELECT uuid FROM `measured_allocation` WHERE operation_uuid=?" to arrayOf<Any?>(receipt.operationUuid),
                "SELECT uuid FROM measured_allocation WHERE operation_uuid=? ORDER BY uuid" to arrayOf<Any?>(receipt.operationUuid),
                "SELECT uuid FROM `record_reference_capture` WHERE record_uuid=? AND record_revision=? AND kind='CONFIRM' AND container_uuid=?" to arrayOf<Any?>(recordUuid,1,a.containerUuid),
                "SELECT uuid FROM `record_reference_capture` WHERE record_uuid=? AND record_revision=? AND kind='CONFIRM' AND container_uuid=?" to arrayOf<Any?>(recordUuid,1L,b.containerUuid),
                "SELECT uuid FROM `record_reference_capture` WHERE record_uuid=? AND record_revision=? AND kind='CONFIRM' AND container_uuid=?" to arrayOf<Any?>(recordUuid,2,a.containerUuid),
                "SELECT uuid FROM `measured_allocation` WHERE operation_uuid=?" to arrayOf<Any?>(UUID.randomUUID().toString()),
                "SELECT COUNT(*) FROM measured_allocation WHERE operation_uuid=? AND container_uuid=?" to arrayOf<Any?>(receipt.operationUuid,UUID.randomUUID().toString()),
            )
            fun values(c:android.database.Cursor)=c.use{buildList{while(it.moveToNext())add(List(it.columnCount){i->if(it.isNull(i))null else it.getString(i)})}}
            for(limit in listOf(0L,1L,512L,8L*1024*1024)) {
                var sqlCalls=0
                val observed=object:androidx.sqlite.db.SupportSQLiteDatabase by raw {
                    override fun query(sql:String,bindArgs:Array<out Any?>):android.database.Cursor {sqlCalls++;return raw.query(sql,bindArgs)}
                }
                val reads=ReferenceAuditReads(observed,limit)
                try {
                    for((sql,args) in queries)assertEquals("Exact predicate/row multiplicity at budget $limit: $sql",values(raw.query(sql,args)),values(reads.query(sql,args)))
                    if(limit==8L*1024*1024)assertEquals("Complete secondary indexes must serve these queries without repeated SQL",0,sqlCalls)
                    else assertEquals("Incomplete table must query SQLite even when a cached group appears absent",queries.size,sqlCalls)
                } finally{reads.finish()}
            }
        }
    }
    @Test fun secondaryConfirmationIndexRetainsDuplicateRowsSoTheWholeAuditStillRejectsAmbiguity()=runBlocking {
        val m=med();val b=box(m);val r=record(m);consume(r,b,listOf(use(b)))
        db.withTransaction {
            val first=ReferenceStore.row(raw,"record_reference_capture","kind='CONFIRM'",emptyArray())!!
            ReferenceStore.insert(raw,"record_reference_capture",org.json.JSONObject(first.toString()).put("uuid",UUID.randomUUID().toString()).put("capture_revision",first.getInt("capture_revision")+1))
            val sql="SELECT uuid FROM `record_reference_capture` WHERE record_uuid=? AND record_revision=? AND kind='CONFIRM' AND container_uuid=?"
            val args=arrayOf<Any?>(first.getString("record_uuid"),1,b.containerUuid)
            fun ids(database:androidx.sqlite.db.SupportSQLiteDatabase)=database.query(sql,args).use{c->buildList{while(c.moveToNext())add(c.getString(0))}.sorted()}
            val expected=ids(raw);assertEquals("Fixture must really contain two distinct confirmations",2,expected.size)
            val reads=ReferenceAuditReads(raw)
            try{assertEquals(expected,ids(reads))}finally{reads.finish()}
            assertThrows("Index must not conceal ambiguous historical confirmation",ReferenceFailure::class.java){MeasuredConsumptionStore.auditStagedDataset(raw)}
            Unit
        }
    }
    @Test fun zeroAllocationAuditScansOperationRowsWithoutRepeatedPrimaryQueries()=runBlocking {
        val m=med();val b=box(m);val r=record(m);val receipt=consume(r,b,emptyList())
        db.withTransaction {
            val before=RawData.dump(raw).toString()
            var pointReads=0
            val counted=object:androidx.sqlite.db.SupportSQLiteDatabase by raw {
                override fun query(sql:String,bindArgs:Array<out Any?>):android.database.Cursor {
                    if(sql.contains("measured_operation",ignoreCase=true) && bindArgs.any{it==receipt.operationUuid})pointReads++
                    return raw.query(sql,bindArgs)
                }
            }
            val projections=MeasuredConsumptionStore.auditStagedDataset(counted)
            assertEquals(listOf(b.containerUuid),projections.map{it.containerUuid})
            assertEquals(0L,projections.single().usedQuanta)
            assertEquals(0,projections.single().remaining.value.compareTo(BigDecimal("28")))
            assertTrue(projections.single().cacheMatches)
            assertEquals(before,RawData.dump(raw).toString())
            assertEquals("Zero-effect operation must be fully audited from its bulk row rather than point-fetched",0,pointReads)
        }
    }
    @Test fun projectionAuditReadsLedgerAndOperationTogetherWithoutPointLookups()=runBlocking {
        val m=med();val b=box(m);val r=record(m);val receipt=consume(r,b,listOf(use(b)))
        db.withTransaction {
            val before=RawData.dump(raw).toString();var pointReads=0;var revisionReads=0
            val counted=object:androidx.sqlite.db.SupportSQLiteDatabase by raw {
                override fun query(sql:String,bindArgs:Array<out Any?>):android.database.Cursor {
                    if(sql.contains("measured_operation",ignoreCase=true) && bindArgs.any{it==receipt.operationUuid})pointReads++
                    if(sql.contains("FROM dose_record",ignoreCase=true))revisionReads++
                    return raw.query(sql,bindArgs)
                }
            }
            val audited=MeasuredConsumptionStore.auditStagedProjection(counted,b.scope.dataset,b.containerUuid)
            assertTrue(audited is MeasureResult.Success);val value=(audited as MeasureResult.Success).value
            assertEquals(1L,value.usedQuanta);assertTrue(value.cacheMatches)
            assertEquals(0,value.remaining.value.compareTo(BigDecimal("27.5")))
            assertEquals(before,RawData.dump(raw).toString())
            assertEquals("Projection must retain both operation and clinical revision checks without separate point queries",listOf(0,0),listOf(pointReads,revisionReads))
        }
    }
    @Test fun projectionAuditNeverHidesAnOrphanLedgerWhenTheScalarCacheIsForged()=runBlocking {
        val m=med();val b=box(m);val r=record(m);consume(r,b,listOf(use(b)))
        val before=db.withTransaction{RawData.dump(raw).toString()}
        class SyntheticRollback:RuntimeException()
        try {db.withTransaction {
            raw.execSQL("DROP TRIGGER immutable_supply_update")
            raw.execSQL("UPDATE supply_transaction SET operation_id=? WHERE dose_record_id=?",arrayOf(UUID.randomUUID().toString(),r.id))
            raw.execSQL("UPDATE supply_container SET used_amount=0 WHERE id=?",arrayOf(b.container.id))
            // An INNER JOIN would hide the ledger and falsely agree with this forged zero cache.
            assertThrows(ReferenceFailure::class.java){MeasuredConsumptionStore.auditStagedProjection(raw,b.scope.dataset,b.containerUuid)}
            throw SyntheticRollback()
        }} catch(_:SyntheticRollback){}
        db.withTransaction {
            assertEquals(before,RawData.dump(raw).toString())
            raw.query("SELECT 1 FROM sqlite_master WHERE type='trigger' AND name='immutable_supply_update'").use{assertTrue(it.moveToFirst())}
        }
    }
    @Test fun auditedCanonicalEvidenceNeverAuthorizesDifferentTextOrHeadersForTheSameOperation()=runBlocking {
        val m=med();val b=box(m);val r=record(m);consume(r,b,emptyList())
        db.withTransaction {
            val before=RawData.dump(raw).toString()
            for(field in listOf("input_json","dataset_uuid")) {
                var chainRead=false;var injected=0;val otherDataset=UUID.randomUUID().toString()
                val forged=object:androidx.sqlite.db.SupportSQLiteDatabase by raw {
                    private fun wrap(sql:String,cursor:android.database.Cursor):android.database.Cursor {
                        if(sql=="SELECT operation_uuid,input_json FROM measured_operation")return object:android.database.CursorWrapper(cursor) {
                            override fun close(){super.close();chainRead=true}
                        }
                        if(!chainRead || !sql.contains("measured_operation",ignoreCase=true) || cursor.getColumnIndex(field)<0)return cursor
                        return object:android.database.CursorWrapper(cursor) {
                            override fun getString(columnIndex:Int):String {
                                val original=super.getString(columnIndex)
                                if(getColumnName(columnIndex)==field) {injected++;return if(field=="input_json")" "+original else otherDataset}
                                return original
                            }
                        }
                    }
                    override fun query(sql:String)=wrap(sql,raw.query(sql))
                    override fun query(sql:String,bindArgs:Array<out Any?>)=wrap(sql,raw.query(sql,bindArgs))
                }
                val error=assertThrows(ReferenceFailure::class.java){MeasuredConsumptionStore.auditStagedDataset(forged)}
                assertEquals(ReferenceError.INVALID_SOURCE,error.code)
                assertTrue("Fixture must replace $field after the genuine complete chain read",chainRead && injected>0)
                assertEquals(before,RawData.dump(raw).toString())
                assertEquals(0L,MeasuredConsumptionStore.auditStagedDataset(raw).single().usedQuanta)
            }
        }
    }
    @Test fun fractionalPhysicalUseWritesOneExactIntegerWithoutChangingTheDoseFact()=runBlocking {
        val m=med();val b=box(m);val r=record(m);val before=db.dao().medication(m)
        val receipt=consume(r,b,listOf(use(b)))
        val line=receipt.lines.single() as PhysicalUseResult.Known
        assertEquals(BigDecimal("0.5"),line.allocated.value);assertEquals(BigDecimal.ZERO,line.unallocated.value)
        assertEquals(1.0,db.dao().container(b.container.id).used_amount,0.0)
        assertEquals(r,db.dao().recordById(r.id));assertEquals(before,db.dao().medication(m));assertNull(db.dao().profile(m))
        assertEquals(1,count("supply_transaction"));assertEquals(1,count("measured_allocation"));assertEquals(1,count("record_reference_capture"))
        db.withTransaction{assertTrue(ReferenceStore.recordContexts(raw,b.scope,r.id).single().actualPackageConfirmed)}
    }
    @Test fun retriesAreIdempotentAndConflictingOrSecondOperationsCannotDeductAgain()=runBlocking {
        val m=med();val b=box(m);val r=record(m);val op=UUID.randomUUID().toString()
        val first=consume(r,b,listOf(use(b)),op);assertEquals(first,consume(r,b,listOf(use(b)),op))
        rejects(ReferenceError.OPERATION_CONFLICT){consume(r,b,listOf(use(b,"1")),op)}
        rejects(ReferenceError.ALREADY_CONSUMED){consume(r,b,listOf(use(b)))}
        assertEquals(1,count("measured_operation"));assertEquals(1,count("supply_transaction"));assertEquals(1.0,db.dao().container(b.container.id).used_amount,0.0)
    }
    @Test fun unsupportedMassConversionPreservesTheActualRecordAndNeverUsesZeroAsUnknown()=runBlocking {
        val m=med();val b=box(m);val r=record(m)
        val input=use(b).copy(quantity=Quantity(BigDecimal("2"),MeasureUnit.MG,Meaning(QuantityKind.ACTIVE_MASS,"SYNTHETIC ingredient")))
        val result=consume(r,b,listOf(input));val line=result.lines.single() as PhysicalUseResult.Unknown
        assertEquals("INSUFFICIENT_SOURCE",line.reason);assertEquals(BigDecimal("2"),line.requested.value)
        assertEquals(r,db.dao().recordById(r.id));assertEquals(0,count("supply_transaction"));assertEquals(0.0,db.dao().container(b.container.id).used_amount,0.0)
        assertEquals("INSUFFICIENT_SOURCE",result.issue)
    }
    @Test fun explicitlyConfirmedDifferentStrengthsRemainSeparatePhysicalFacts()=runBlocking {
        val m=med();val one=box(m);val otherMeaning=meaning.copy(identity="SYNTHETIC 1mg tablet");val two=box(m,otherMeaning,"1 mg / tablet");val r=record(m)
        val receipt=consume(r,one,listOf(use(one),use(two,"1",otherMeaning)))
        assertEquals(2,receipt.lines.size);val lines=receipt.lines.associateBy{it.containerUuid}
        assertEquals(BigDecimal("0.5"),(lines[one.containerUuid] as PhysicalUseResult.Known).allocated.value)
        assertEquals(BigDecimal("1"),(lines[two.containerUuid] as PhysicalUseResult.Known).allocated.value)
        assertEquals(1.0,db.dao().container(one.container.id).used_amount,0.0);assertEquals(2.0,db.dao().container(two.container.id).used_amount,0.0)
        assertEquals(r,db.dao().recordById(r.id)) // No automatic clinical dose composition from the two strengths.
    }
    @Test fun partialExhaustionKeepsAnExactPhysicalRemainder()=runBlocking {
        val m=med();val b=box(m);consume(record(m),b,listOf(use(b,"27.5")))
        val r=record(m);val result=consume(r,b,listOf(use(b,"1")));val line=result.lines.single() as PhysicalUseResult.Known
        assertEquals(BigDecimal("0.5"),line.allocated.value);assertEquals(BigDecimal("0.5"),line.unallocated.value)
        assertEquals("INSUFFICIENT_STOCK",result.issue);assertEquals("EMPTY",db.dao().container(b.container.id).state)
        assertEquals(56.0,db.dao().container(b.container.id).used_amount,0.0);assertEquals(r,db.dao().recordById(r.id))
    }
    @Test fun allocationFailureRollsBackTheNewRecordSourceAndLedgerTogether()=runBlocking {
        val m=med();val b=box(m)
        raw.execSQL("CREATE TRIGGER synthetic_allocation_failure BEFORE INSERT ON measured_allocation BEGIN SELECT RAISE(ABORT,'synthetic measured allocation failure'); END")
        try{db.withTransaction{val r=record(m);MeasuredConsumptionStore.consumeNewRecord(db,b.scope,r,UUID.randomUUID().toString(),listOf(use(b)),at)};fail("Fault was not injected")}
        catch(e:Exception){assertTrue(generateSequence<Throwable>(e){it.cause}.any{it.message.orEmpty().contains("synthetic measured allocation failure")})}
        assertEquals(0,count("dose_record"));assertEquals(0,count("measured_operation"));assertEquals(0,count("supply_transaction"));assertEquals(0,count("record_reference_capture"))
        assertEquals(0.0,db.dao().container(b.container.id).used_amount,0.0);assertEquals(2,count("reference_identity"))
    }
    @Test fun foreignMedicationSourceAndStaleDatasetAreRejectedBeforeAnyStockWrite()=runBlocking {
        val m=med();val other=med("SYNTHETIC other");val b=box(m);val foreign=box(other);val r=record(m)
        rejects(ReferenceError.UNKNOWN_IDENTITY){consume(r,b,listOf(use(foreign)))}
        db.withTransaction{ReferenceSchema.retireRuntime(raw);ReferenceSchema.restoreRuntime(raw)}
        rejects(ReferenceError.STALE_SCOPE){consume(r,b,listOf(use(b)))}
        assertEquals(0,count("measured_operation"));assertEquals(0,count("supply_transaction"));assertEquals(r,db.dao().recordById(r.id))
    }
    @Test fun unconfirmedSourceRemainsUnknownWithoutInventingConsumption()=runBlocking {
        val m=med();val b=box(m);val r=record(m);val receipt=consume(r,b,emptyList())
        assertEquals("ACTUAL_PACKAGE_NOT_CONFIRMED",receipt.issue);assertTrue(receipt.lines.isEmpty())
        assertEquals(0,count("supply_transaction"));assertEquals(r,db.dao().recordById(r.id));assertEquals(1,count("measured_operation"))
    }
    @Test fun reversingUsesOriginalQuantaAfterCurrentMedicationAndReferenceChange()=runBlocking {
        val m=med();val b=box(m);val r=record(m);val receipt=consume(r,b,listOf(use(b,"28")))
        db.withTransaction {
            val current=db.dao().medication(m);db.dao().updateMedication(current.copy(name="SYNTHETIC renamed",dose_per_intake=99.0))
            ReferenceStore.associate(raw,b.scope,m,0,UUID.randomUUID().toString(),product.copy(strength="SYNTHETIC changed current reference"))
        }
        val op=UUID.randomUUID().toString()
        db.withTransaction{assertEquals(1,MeasuredConsumptionStore.reverseOriginal(db,b.scope,r,receipt.operationUuid,op,at))}
        db.withTransaction{assertEquals(1,MeasuredConsumptionStore.reverseOriginal(db,b.scope,r,receipt.operationUuid,op,at))}
        assertEquals(0.0,db.dao().container(b.container.id).used_amount,0.0)
        assertEquals(listOf(56.0,-56.0),db.dao().supplyFor(r.id).map{it.used_delta})
        assertEquals(2,count("measured_operation"));assertEquals(2,count("measured_allocation"));assertEquals(r,db.dao().recordById(r.id))
        rejects(ReferenceError.OPERATION_CONFLICT){db.withTransaction{MeasuredConsumptionStore.reverseOriginal(db,b.scope,r,receipt.operationUuid,UUID.randomUUID().toString(),at)}}
    }
    @Test fun reversalFailureRollsBackAllInverseEntriesAndLeavesOriginalConsumption()=runBlocking {
        val m=med();val b=box(m);val r=record(m);val receipt=consume(r,b,listOf(use(b)))
        raw.execSQL("CREATE TRIGGER synthetic_reverse_failure BEFORE INSERT ON measured_allocation WHEN NEW.reverses_uuid IS NOT NULL BEGIN SELECT RAISE(ABORT,'synthetic measured reverse failure'); END")
        try{db.withTransaction{MeasuredConsumptionStore.reverseOriginal(db,b.scope,r,receipt.operationUuid,UUID.randomUUID().toString(),at)};fail("Inverse fault missed")}
        catch(e:Exception){assertTrue(generateSequence<Throwable>(e){it.cause}.any{it.message.orEmpty().contains("synthetic measured reverse failure")})}
        assertEquals(1.0,db.dao().container(b.container.id).used_amount,0.0);assertEquals(1,count("supply_transaction"));assertEquals(1,count("measured_operation"))
        assertEquals(r,db.dao().recordById(r.id))
    }

    @Test fun remainingAdjustmentIsExactAndIdempotentWithRealZeroAndNoOpTargets()=runBlocking {
        val m=med();val b=box(m);val op=UUID.randomUUID().toString()
        val target=Quantity(BigDecimal("27.5"),MeasureUnit.TABLET,meaning)
        suspend fun adjust(expected:ContainerEntity,value:Quantity,id:String)=db.withTransaction{MeasuredConsumptionStore.adjustRemaining(db,b.scope,expected,b.containerUuid,b.presentationEventUuid,b.presentation.sha256,value,true,id,at)}
        assertTrue(adjust(b.container,target,op) is MeasureResult.Success)
        assertTrue(adjust(b.container,target,op) is MeasureResult.Success)
        assertEquals(1.0,db.dao().container(b.container.id).used_amount,0.0);assertEquals(1,count("supply_transaction"))
        val current=db.dao().container(b.container.id);val noop=UUID.randomUUID().toString()
        assertTrue(adjust(current,target,noop) is MeasureResult.Success);assertTrue(adjust(current,target,noop) is MeasureResult.Success)
        assertEquals(1,count("supply_transaction"));assertEquals(2,count("measured_operation"))
        val zero=target.copy(value=BigDecimal.ZERO);assertTrue(adjust(current,zero,UUID.randomUUID().toString()) is MeasureResult.Success)
        assertEquals(56.0,db.dao().container(b.container.id).used_amount,0.0);assertEquals("EMPTY",db.dao().container(b.container.id).state)
        assertTrue(db.dao().records().isEmpty())
    }
    @Test fun staleRemainingCountCannotOverwriteAConcurrentActualUseAndUnitsNeverMix()=runBlocking {
        val m=med();val b=box(m);consume(record(m),b,listOf(use(b)))
        rejects(ReferenceError.STALE_REVISION){db.withTransaction{MeasuredConsumptionStore.adjustRemaining(db,b.scope,b.container,b.containerUuid,b.presentationEventUuid,b.presentation.sha256,Quantity(BigDecimal("27"),MeasureUnit.TABLET,meaning),true,UUID.randomUUID().toString(),at)}}
        val before=count("measured_operation");val current=db.dao().container(b.container.id)
        val result=db.withTransaction{MeasuredConsumptionStore.adjustRemaining(db,b.scope,current,b.containerUuid,b.presentationEventUuid,b.presentation.sha256,Quantity(BigDecimal.ZERO,MeasureUnit.ML,Meaning(QuantityKind.FORMULATION,"SYNTHETIC solution")),true,UUID.randomUUID().toString(),at)}
        assertEquals(MeasureResult.Failure(MeasureError.INCOMPATIBLE_UNIT),result)
        assertEquals(before,count("measured_operation"));assertEquals(current,db.dao().container(b.container.id))
    }

    @Test fun malformedFrozenReceiptsCannotBeAcceptedAsSuccessfulRetries()=runBlocking {
        val m=med();val b=box(m);val r=record(m);val original=consume(r,b,listOf(use(b)))
        val row=ReferenceStore.row(raw,"measured_operation","operation_uuid=?",arrayOf(original.operationUuid))!!
        val mutations:List<(org.json.JSONObject)->Unit> = listOf(
            {it.put("format","1")},
            {it.put("unexpected",true)},
            {it.getJSONArray("captures").getJSONObject(0).put("take_quanta","0e0")},
            {it.getJSONArray("captures").getJSONObject(0).put("requested_quanta","01")},
            {it.getJSONArray("captures").getJSONObject(0).put("used_before_quanta","9007199254740992")},
            {it.getJSONArray("captures").getJSONObject(0).put("snapshot_uuid","not-a-uuid")},
            {it.getJSONArray("captures").getJSONObject(0).put("unknown_extra",true)},
        )
        for(mutate in mutations){
            val input=org.json.JSONObject(row.getString("input_json"));mutate(input)
            val op=UUID.randomUUID().toString();val forged=org.json.JSONObject(row.toString()).put("operation_uuid",op).put("input_json",ReferencePayload.canonical(input))
            db.withTransaction{ReferenceStore.insert(raw,"measured_operation",forged)}
            rejects(ReferenceError.INVALID_SOURCE){consume(r,b,listOf(use(b)),op)}
        }
        assertEquals(1,count("supply_transaction"));assertEquals(1.0,db.dao().container(b.container.id).used_amount,0.0)
    }

    @Test fun legacyAllocationMustSkipMeasuredContainersWhileKeepingLegacyBehavior()=runBlocking {
        val m=med();val b=box(m)
        val old=db.dao().insertContainer(ContainerEntity(medication_id=m,capacity=28.0,initial_used_amount=0.0,used_amount=0.0,state="SEALED"))
        val r=record(m)
        db.withTransaction{assertEquals(0.0,SupplyLedger.allocate(db.dao(),r),0.0)}
        assertEquals(0.0,db.dao().container(b.container.id).used_amount,0.0)
        assertEquals(0.1,db.dao().container(old).used_amount,0.0)
        assertEquals(old,db.dao().supplyFor(r.id).single().container_id)
    }
    @Test fun legacyAdjustmentAndReverseMustRefuseMeasuredHistory()=runBlocking {
        val m=med();val b=box(m)
        try{db.withTransaction{SupplyLedger.setRemaining(db.dao(),b.container.id,27.5)};fail("Legacy correction accepted internal quanta")}
        catch(e:IllegalArgumentException){assertTrue(e.message.orEmpty().contains("Measured"))}
        val r=record(m);consume(r,b,listOf(use(b)))
        try{db.withTransaction{SupplyLedger.reverse(db.dao(),r.id)};fail("Legacy reverse omitted frozen measurement evidence")}
        catch(e:IllegalArgumentException){assertTrue(e.message.orEmpty().contains("Measured"))}
        assertEquals(1.0,db.dao().container(b.container.id).used_amount,0.0);assertEquals(1,count("supply_transaction"))
    }
    @Test fun legacyMedicationResizeMustLeaveFrozenPackagingUntouched()=runBlocking {
        val m=med();val b=box(m)
        val old=db.dao().insertContainer(ContainerEntity(medication_id=m,capacity=28.0,initial_used_amount=0.0,used_amount=0.0,state="SEALED"))
        val before=ReferenceStore.row(raw,"reference_snapshot","payload_sha256=?",arrayOf(b.presentation.sha256))!!
        val repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
        repo.saveMedication(db.dao().medication(m).copy(container_capacity=100.0),null,net.plainnotes.app.domain.RuleKind.EVERY_N_DAYS,1,listOf(java.time.LocalTime.NOON),emptySet(),at,resizeContainers=true)
        assertEquals(56.0,db.dao().container(b.container.id).capacity,0.0)
        assertEquals(100.0,db.dao().container(old).capacity,0.0)
        assertEquals(before.toString(),ReferenceStore.row(raw,"reference_snapshot","payload_sha256=?",arrayOf(b.presentation.sha256))!!.toString())
    }

    @Test fun legacyReplacementAndDiscardCannotMutateMeasuredPackaging()=runBlocking {
        val m=med();val b=box(m);consume(record(m),b,listOf(use(b)))
        val before=db.dao().container(b.container.id)
        val repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
        try{repo.replaceContainer(m,28.0);fail("Legacy replace closed a measured source")}
        catch(e:IllegalArgumentException){assertTrue(e.message.orEmpty().contains("Measured"))}
        try{repo.discardContainer(b.container.id);fail("Legacy discard bypassed explicit packaging identity")}
        catch(e:IllegalArgumentException){assertTrue(e.message.orEmpty().contains("Measured"))}
        assertEquals(before,db.dao().container(b.container.id));assertEquals(1,db.dao().containers().size)
        assertEquals(1,count("supply_transaction"));assertEquals(1,count("measured_operation"))
    }
    @Test fun measurementIdentityDoesNotFollowAReusedIntegerHandle()=runBlocking {
        val m=med();val b=box(m)
        db.withTransaction{db.dao().deleteContainer(b.container.id)}
        db.dao().insertContainer(ContainerEntity(id=b.container.id,medication_id=m,capacity=28.0,initial_used_amount=0.0,used_amount=0.0,state="SEALED"))
        assertTrue(db.dao().measuredContainerIds().isEmpty())
        val r=record(m);db.withTransaction{assertEquals(0.0,SupplyLedger.allocate(db.dao(),r),0.0)}
        assertEquals(0.1,db.dao().container(b.container.id).used_amount,0.0)
        assertEquals(1,count("reference_snapshot"));assertEquals(0,count("measured_operation"))
    }
    @Test fun legacyAllocationCannotReapplyAnAlreadyCapturedPhysicalUse()=runBlocking {
        val m=med();val b=box(m);val r=record(m);consume(r,b,listOf(use(b)))
        val old=db.dao().insertContainer(ContainerEntity(medication_id=m,capacity=28.0,initial_used_amount=0.0,used_amount=0.0,state="SEALED"))
        try{db.withTransaction{SupplyLedger.allocate(db.dao(),r)};fail("Legacy allocation repeated an already measured use")}
        catch(e:IllegalArgumentException){assertTrue(e.message.orEmpty().contains("Measured"))}
        assertEquals(0.0,db.dao().container(old).used_amount,0.0);assertEquals(1.0,db.dao().container(b.container.id).used_amount,0.0)
        assertEquals(r,db.dao().recordById(r.id));assertEquals(1,count("supply_transaction"))
    }
    @Test fun syntheticPumpStockUsesOnlyExplicitDirectedContentAndConfirmedCapacity()=runBlocking {
        val m=med();val pump=Meaning(QuantityKind.USE_COUNT,"SYNTHETIC gel pump")
        val active=Meaning(QuantityKind.ACTIVE_MASS,"SYNTHETIC estradiol")
        fun q(value:String,unit:MeasureUnit,identity:Meaning)=Quantity(BigDecimal(value),unit,identity)
        val draft=PackageMeasurementDraft(product.copy(name="SYNTHETIC pump gel",ingredients=listOf("SYNTHETIC estradiol"),strength="0.75 mg per pump"),MeasureUnit.PUMP,pump,"1","60",StockCapacityBasis.USER_COUNTED,
            nominalTotal=q("80",MeasureUnit.G,Meaning(QuantityKind.FORMULATION,"SYNTHETIC gel")),nominalUses=q("64",MeasureUnit.PUMP,pump),
            relations=listOf(DeclaredPackageContent(q("0.75",MeasureUnit.MG,active),q("1",MeasureUnit.PUMP,pump),"SYNTHETIC fixture evidence","synthetic directed inverse","2026-10-16","Explicit confirmed source only",true)))
        val b=db.withTransaction{MeasuredContainerStore.create(db,ReferenceStore.previewEpoch(raw),m,UUID.randomUUID().toString(),draft,at)}
        val first=record(m)
        val mass=ConfirmedStockUse(b.containerUuid,b.presentationEventUuid,b.presentation.sha256,q("1.5",MeasureUnit.MG,active),true)
        val result=consume(first,b,listOf(mass)).lines.single() as PhysicalUseResult.Known
        assertEquals(q("2",MeasureUnit.PUMP,pump),result.allocated)
        val second=record(m);consume(second,b,listOf(mass.copy(quantity=q("1",MeasureUnit.PUMP,pump))))
        assertEquals(60.0,db.dao().container(b.container.id).capacity,0.0);assertEquals(3.0,db.dao().container(b.container.id).used_amount,0.0)
        assertEquals(first,db.dao().recordById(first.id));assertEquals(second,db.dao().recordById(second.id))
        val third=record(m)
        val ester=consume(third,b,listOf(mass.copy(quantity=q("1.5",MeasureUnit.MG,Meaning(QuantityKind.ACTIVE_MASS,"SYNTHETIC estradiol ester"))))).lines.single() as PhysicalUseResult.Unknown
        assertEquals("INSUFFICIENT_SOURCE",ester.reason);assertEquals(3.0,db.dao().container(b.container.id).used_amount,0.0)
    }

    @Test fun reverseCannotRewriteALaterConfirmedRemainingObservation()=runBlocking {
        val m=med();val b=box(m);val r=record(m);val original=consume(r,b,listOf(use(b)))
        db.withTransaction{MeasuredConsumptionStore.adjustRemaining(db,b.scope,db.dao().container(b.container.id),b.containerUuid,b.presentationEventUuid,b.presentation.sha256,
            Quantity(BigDecimal("27"),MeasureUnit.TABLET,meaning),true,UUID.randomUUID().toString(),at)}
        val before=db.dao().container(b.container.id)
        rejects(ReferenceError.OPERATION_CONFLICT){db.withTransaction{MeasuredConsumptionStore.reverseOriginal(db,b.scope,r,original.operationUuid,UUID.randomUUID().toString(),at)}}
        assertEquals(before,db.dao().container(b.container.id));assertEquals(2,count("supply_transaction"));assertEquals(2,count("measured_operation"))
        assertEquals(r,db.dao().recordById(r.id))
    }

    @Test fun noOpRemainingObservationAlsoRequiresReconciliationBeforeAnInverse()=runBlocking {
        val m=med();val b=box(m);val r=record(m);val original=consume(r,b,listOf(use(b)))
        db.withTransaction{MeasuredConsumptionStore.adjustRemaining(db,b.scope,db.dao().container(b.container.id),b.containerUuid,b.presentationEventUuid,b.presentation.sha256,
            Quantity(BigDecimal("27.5"),MeasureUnit.TABLET,meaning),true,UUID.randomUUID().toString(),at)}
        assertEquals(1,count("supply_transaction"));assertEquals(2,count("measured_operation"))
        rejects(ReferenceError.OPERATION_CONFLICT){db.withTransaction{MeasuredConsumptionStore.reverseOriginal(db,b.scope,r,original.operationUuid,UUID.randomUUID().toString(),at)}}
        assertEquals(1.0,db.dao().container(b.container.id).used_amount,0.0);assertEquals(1,count("supply_transaction"));assertEquals(r,db.dao().recordById(r.id))
    }

    @Test fun earlierCountDoesNotBlockTheInverseOfALaterPhysicalUse()=runBlocking {
        val m=med();val b=box(m)
        db.withTransaction{MeasuredConsumptionStore.adjustRemaining(db,b.scope,b.container,b.containerUuid,b.presentationEventUuid,b.presentation.sha256,
            Quantity(BigDecimal("27.5"),MeasureUnit.TABLET,meaning),true,UUID.randomUUID().toString(),at.plusSeconds(60))}
        val observed=db.dao().container(b.container.id);val r=record(m)
        val original=consume(r,b,listOf(use(b))) // Clock intentionally earlier than the count; causal order is the ledger.
        db.withTransaction{MeasuredConsumptionStore.reverseOriginal(db,b.scope,r,original.operationUuid,UUID.randomUUID().toString(),at.minusSeconds(60))}
        assertEquals(observed.used_amount,db.dao().container(b.container.id).used_amount,0.0)
        assertEquals(listOf(1.0,1.0,-1.0),db.dao().supplyForContainer(b.container.id).map{it.used_delta})
        assertEquals(r,db.dao().recordById(r.id))
    }

    @Test fun replayedRemainingCountKeepsItsOriginalCausalAnchorAfterLaterUse()=runBlocking {
        val m=med();val b=box(m);val op=UUID.randomUUID().toString()
        val target=Quantity(BigDecimal("27.5"),MeasureUnit.TABLET,meaning)
        db.withTransaction{MeasuredConsumptionStore.adjustRemaining(db,b.scope,b.container,b.containerUuid,b.presentationEventUuid,b.presentation.sha256,target,true,op,at)}
        val before=ReferenceStore.row(raw,"measured_operation","operation_uuid=?",arrayOf(op))!!
        assertEquals("0",org.json.JSONObject(before.getString("input_json")).getJSONObject("execution").getString("ledger_head_id"))
        consume(record(m),b,listOf(use(b)))
        db.withTransaction{assertTrue(MeasuredConsumptionStore.adjustRemaining(db,b.scope,b.container,b.containerUuid,b.presentationEventUuid,b.presentation.sha256,target,true,op,at.plusSeconds(86400)) is MeasureResult.Success)}
        assertEquals(before.toString(),ReferenceStore.row(raw,"measured_operation","operation_uuid=?",arrayOf(op))!!.toString())
        assertEquals(2.0,db.dao().container(b.container.id).used_amount,0.0);assertEquals(2,count("supply_transaction"));assertEquals(2,count("measured_operation"))
    }
    @Test fun noOpCountBeforeAUseDoesNotBlockItsLaterInverse()=runBlocking {
        val m=med();val b=box(m)
        db.withTransaction{MeasuredConsumptionStore.adjustRemaining(db,b.scope,b.container,b.containerUuid,b.presentationEventUuid,b.presentation.sha256,
            Quantity(BigDecimal("28"),MeasureUnit.TABLET,meaning),true,UUID.randomUUID().toString(),at.plusSeconds(60))}
        val r=record(m);val original=consume(r,b,listOf(use(b)))
        db.withTransaction{MeasuredConsumptionStore.reverseOriginal(db,b.scope,r,original.operationUuid,UUID.randomUUID().toString(),at.minusSeconds(60))}
        assertEquals(0.0,db.dao().container(b.container.id).used_amount,0.0)
        assertEquals(listOf(1.0,-1.0),db.dao().supplyForContainer(b.container.id).map{it.used_delta})
    }
    @Test fun malformedOrNonexistentAdjustmentAnchorsCannotBeReplayed()=runBlocking {
        val m=med();val b=box(m);val op=UUID.randomUUID().toString();val target=Quantity(BigDecimal("27.5"),MeasureUnit.TABLET,meaning)
        db.withTransaction{MeasuredConsumptionStore.adjustRemaining(db,b.scope,b.container,b.containerUuid,b.presentationEventUuid,b.presentation.sha256,target,true,op,at)}
        val original=ReferenceStore.row(raw,"measured_operation","operation_uuid=?",arrayOf(op))!!
        val other=med("SYNTHETIC different owner");val foreign=box(other);val foreignRecord=record(other);consume(foreignRecord,foreign,listOf(use(foreign)))
        val foreignHead=db.dao().supplyFor(foreignRecord.id).single().id.toString()
        val mutations:List<(org.json.JSONObject)->Unit> = listOf(
            {it.put("format","1")},
            {it.getJSONObject("execution").put("unexpected",true)},
            {it.getJSONObject("execution").put("ledger_head_id","01")},
            {it.getJSONObject("execution").put("ledger_head_id","0e0")},
            {it.getJSONObject("execution").put("ledger_head_id","9223372036854775808")},
            {it.getJSONObject("execution").put("ledger_head_id","999999")},
            {it.getJSONObject("execution").put("ledger_head_id",foreignHead)},
        )
        for(mutate in mutations){
            val json=org.json.JSONObject(original.getString("input_json"));mutate(json);val forgedOp=UUID.randomUUID().toString()
            val forged=org.json.JSONObject(original.toString()).put("operation_uuid",forgedOp).put("input_json",ReferencePayload.canonical(json))
            db.withTransaction{ReferenceStore.insert(raw,"measured_operation",forged)}
            rejects(ReferenceError.INVALID_SOURCE){db.withTransaction{MeasuredConsumptionStore.adjustRemaining(db,b.scope,b.container,b.containerUuid,b.presentationEventUuid,b.presentation.sha256,target,true,forgedOp,at)}}
        }
        assertEquals(1.0,db.dao().container(b.container.id).used_amount,0.0);assertEquals(2,count("supply_transaction"))
        assertEquals(1.0,db.dao().container(foreign.container.id).used_amount,0.0)
    }

    @Test fun readOnlyProjectionAuditsPhysicalAmountsFromFrozenLedgerEvidence()=runBlocking {
        val m=med();val b=box(m);val r=record(m);consume(r,b,listOf(use(b)))
        db.withTransaction {
            val result=MeasuredConsumptionStore.auditProjection(db,b.scope,b.containerUuid)
            assertTrue(result is MeasureResult.Success);val audit=(result as MeasureResult.Success).value
            assertEquals(net.plainnotes.app.domain.stock.StockQuantum.ALGORITHM,result.algorithm)
            assertEquals(1L,audit.usedQuanta);assertEquals(Quantity(BigDecimal("27.5"),MeasureUnit.TABLET,meaning),audit.remaining)
            assertTrue(audit.cacheMatches);assertEquals(b.presentation.sha256,audit.presentationSha256)
        }
        assertEquals(r,db.dao().recordById(r.id));assertEquals(1,count("supply_transaction"))
    }
    @Test fun projectionReportsACacheMismatchWithoutRepairingOrRewritingAnything()=runBlocking {
        val m=med();val b=box(m);consume(record(m),b,listOf(use(b)))
        raw.execSQL("UPDATE supply_container SET used_amount=10 WHERE id=?",arrayOf(b.container.id))
        val before=db.dao().container(b.container.id)
        db.withTransaction {
            val result=MeasuredConsumptionStore.auditProjection(db,b.scope,b.containerUuid)
            assertTrue(result is MeasureResult.Success);val audit=(result as MeasureResult.Success).value
            assertFalse(audit.cacheMatches);assertEquals(1L,audit.usedQuanta);assertEquals(BigDecimal("27.5"),audit.remaining.value)
        }
        assertEquals(before,db.dao().container(b.container.id));assertEquals(1,count("supply_transaction"))
    }
    @Test fun projectionRejectsAnUnpairedLegacyEntryInsideMeasuredStock()=runBlocking {
        val m=med();val b=box(m)
        db.withTransaction{db.dao().supply(SupplyEntryEntity(container_id=b.container.id,operation_id=UUID.randomUUID().toString(),kind="ADJUST",used_delta=1.0,created_utc=at.toEpochMilli(),created_zone="UTC"))}
        rejects(ReferenceError.INVALID_SOURCE){db.withTransaction{MeasuredConsumptionStore.auditProjection(db,b.scope,b.containerUuid)}}
        assertEquals(1.0,db.dao().container(b.container.id).used_amount,0.0);assertEquals(1,count("supply_transaction"));assertEquals(0,count("measured_allocation"))
    }
    @Test fun projectionAuditsCountConsumeAndInverseWithoutUsingCurrentMedicationStrength()=runBlocking {
        val m=med();val b=box(m)
        db.withTransaction{MeasuredConsumptionStore.adjustRemaining(db,b.scope,b.container,b.containerUuid,b.presentationEventUuid,b.presentation.sha256,
            Quantity(BigDecimal("27.5"),MeasureUnit.TABLET,meaning),true,UUID.randomUUID().toString(),at)}
        val r=record(m);val original=consume(r,b,listOf(use(b)))
        db.withTransaction{MeasuredConsumptionStore.reverseOriginal(db,b.scope,r,original.operationUuid,UUID.randomUUID().toString(),at)}
        db.dao().updateMedication(db.dao().medication(m).copy(name="SYNTHETIC current changed",dose_per_intake=999.0,container_capacity=999.0))
        db.withTransaction {
            val result=MeasuredConsumptionStore.auditProjection(db,b.scope,b.containerUuid)
            assertTrue(result is MeasureResult.Success);val audit=(result as MeasureResult.Success).value
            assertEquals(1L,audit.usedQuanta);assertEquals(BigDecimal("27.5"),audit.remaining.value);assertTrue(audit.cacheMatches)
        }
        assertEquals(r,db.dao().recordById(r.id));assertEquals(3,count("supply_transaction"))
    }

    @Test fun projectionChecksTheCompleteInverseSetAcrossTwoExplicitSources()=runBlocking {
        val m=med();val a=box(m);val other=Meaning(QuantityKind.USE_COUNT,"SYNTHETIC 1mg tablet");val b=box(m,other,"1 mg / tablet")
        val r=record(m);val original=consume(r,a,listOf(use(a),use(b,identity=other)))
        db.withTransaction{MeasuredConsumptionStore.reverseOriginal(db,a.scope,r,original.operationUuid,UUID.randomUUID().toString(),at)}
        db.withTransaction {
            for(source in listOf(a,b)) {
                val result=MeasuredConsumptionStore.auditProjection(db,a.scope,source.containerUuid)
                assertTrue(result is MeasureResult.Success);val audit=(result as MeasureResult.Success).value
                assertEquals(0L,audit.usedQuanta);assertEquals(BigDecimal("28"),audit.remaining.value);assertTrue(audit.cacheMatches)
            }
        }
        assertEquals(4,count("supply_transaction"));assertEquals(4,count("measured_allocation"));assertEquals(r,db.dao().recordById(r.id))
    }

    @Test fun projectionRejectsAnIncompleteInverseEvenWhenAnotherContainerIsMissingEvidence()=runBlocking {
        val m=med();val a=box(m);val other=Meaning(QuantityKind.USE_COUNT,"SYNTHETIC 1mg tablet");val b=box(m,other,"1 mg / tablet")
        val r=record(m);val original=consume(r,a,listOf(use(a),use(b,identity=other)))
        db.withTransaction{MeasuredConsumptionStore.reverseOriginal(db,a.scope,r,original.operationUuid,UUID.randomUUID().toString(),at)}
        fun guards()=raw.query("SELECT name,sql FROM sqlite_master WHERE type='trigger' ORDER BY name").use{c->buildList{while(c.moveToNext())add(c.getString(0) to c.getString(1))}}
        val beforeGuards=guards()
        val missing=raw.query("SELECT uuid FROM measured_allocation WHERE container_uuid=? AND reverses_uuid IS NOT NULL",arrayOf(b.containerUuid)).use{it.moveToFirst();it.getString(0)}
        // Deliberately craft an untrusted restore-state fixture using the existing atomic maintenance helper.
        // Trusted guards are reinstalled before the read-only audit; no production guard is changed.
        db.withTransaction{RawData.unguarded(raw){raw.execSQL("DELETE FROM measured_allocation WHERE uuid=?",arrayOf(missing))}}
        assertEquals(beforeGuards,guards())
        rejects(ReferenceError.INVALID_SOURCE){db.withTransaction{MeasuredConsumptionStore.auditProjection(db,a.scope,a.containerUuid)}}
        assertEquals(0.0,db.dao().container(a.container.id).used_amount,0.0);assertEquals(0.0,db.dao().container(b.container.id).used_amount,0.0)
        assertEquals(4,count("supply_transaction"));assertEquals(3,count("measured_allocation"));assertEquals(r,db.dao().recordById(r.id))
    }

    /** Trusted installed DDL plus synthetic business rows only; deliberately no local runtime table. */
    private fun <T> staged(block:(androidx.sqlite.db.SupportSQLiteDatabase)->T):T {
        val trusted=raw.query("SELECT tbl_name,sql FROM sqlite_master WHERE type IN ('table','index') AND sql IS NOT NULL ORDER BY CASE type WHEN 'table' THEN 0 ELSE 1 END,name").use { c ->
            buildList { while(c.moveToNext())if(c.getString(0) in DOMAIN_TABLES)add(c.getString(1)) }
        }
        val rows=RawData.dump(raw)
        val configuration=SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
            .callback(object:SupportSQLiteOpenHelper.Callback(1) {
                override fun onConfigure(db:androidx.sqlite.db.SupportSQLiteDatabase){db.setForeignKeyConstraintsEnabled(true)}
                override fun onCreate(db:androidx.sqlite.db.SupportSQLiteDatabase){trusted.forEach(db::execSQL)}
                override fun onUpgrade(db:androidx.sqlite.db.SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Synthetic staging cannot upgrade")
            }).build()
        return androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            val staging=helper.writableDatabase;staging.beginTransaction()
            try {
                for(table in DOMAIN_TABLES) {
                    val values=rows.getJSONArray(table)
                    val order=if(table=="supply_transaction")LedgerReplacement.insertionOrder(values) else ReferenceDependencies.insertionOrder(table,values)
                    for(i in order) {
                        val row=values.getJSONObject(i);val keys=row.keys().asSequence().toList()
                        val args=keys.map{key->row.get(key).let{if(it==org.json.JSONObject.NULL)null else it}}.toTypedArray()
                        staging.execSQL("INSERT INTO `$table` (${keys.joinToString(","){"`$it`"}}) VALUES (${keys.joinToString(","){"?"}})",args)
                    }
                }
                assertEquals(0,staging.query("SELECT COUNT(*) FROM sqlite_master WHERE name='reference_runtime'").use{it.moveToFirst();it.getInt(0)})
                val result=block(staging);staging.setTransactionSuccessful();result
            } finally {staging.endTransaction()}
        }
    }

    @Test fun stagingProjectionReusesTheLiveAuditWithoutRuntimeIdentityOrAnyWrites()=runBlocking {
        val m=med();val b=box(m);val r=record(m);val original=consume(r,b,listOf(use(b)))
        db.withTransaction{MeasuredConsumptionStore.reverseOriginal(db,b.scope,r,original.operationUuid,UUID.randomUUID().toString(),at)}
        consume(record(m),b,listOf(use(b,"1.5")))
        val live=db.withTransaction{MeasuredConsumptionStore.auditProjection(db,b.scope,b.containerUuid)}
        val before=RawData.dump(raw).toString()
        staged { staging ->
            val stagedBefore=RawData.dump(staging).toString()
            assertEquals(live,MeasuredConsumptionStore.auditStagedProjection(staging,b.scope.dataset,b.containerUuid))
            assertEquals(stagedBefore,RawData.dump(staging).toString())
        }
        assertEquals(before,RawData.dump(raw).toString());assertEquals(3,count("supply_transaction"))
    }

    @Test fun stagingRejectsForeignDatasetAndTextInANumericLedgerColumnWithoutAffectingSource()=runBlocking {
        val m=med();val b=box(m);consume(record(m),b,listOf(use(b)))
        val before=RawData.dump(raw).toString()
        staged { staging ->
            try {MeasuredConsumptionStore.auditStagedProjection(staging,UUID.randomUUID().toString(),b.containerUuid);fail("Foreign dataset accepted")}
            catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
            staging.execSQL("UPDATE supply_transaction SET used_delta='not-a-number'")
            try {MeasuredConsumptionStore.auditStagedProjection(staging,b.scope.dataset,b.containerUuid);fail("Text quantity accepted")}
            catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
            assertEquals("not-a-number",staging.query("SELECT used_delta FROM supply_transaction").use{it.moveToFirst();it.getString(0)})
        }
        assertEquals(before,RawData.dump(raw).toString());assertEquals(1.0,db.dao().container(b.container.id).used_amount,0.0)
    }

    @Test fun liveAuditStillRejectsStaleEpochBeforeDelegatingToBusinessOnlyStaging()=runBlocking {
        val m=med();val b=box(m);consume(record(m),b,listOf(use(b)))
        val before=RawData.dump(raw).toString()
        rejects(ReferenceError.STALE_SCOPE){db.withTransaction{MeasuredConsumptionStore.auditProjection(db,b.scope.copy(epoch=UUID.randomUUID().toString()),b.containerUuid)}}
        assertEquals(before,RawData.dump(raw).toString())
        staged { staging ->assertTrue(MeasuredConsumptionStore.auditStagedProjection(staging,b.scope.dataset,b.containerUuid) is MeasureResult.Success)}
    }

    @Test fun globalAuditRejectsForgedUnknownOutcomeEvenWithoutALedgerEntry()=runBlocking {
        val m=med();val b=box(m);val r=record(m);val op=consume(r,b,emptyList()).operationUuid
        staged { staging ->
            staging.execSQL("UPDATE measured_operation SET unknown_reason='FORGED_ZERO' WHERE operation_uuid=?",arrayOf(op))
            try {MeasuredConsumptionStore.auditStagedDataset(staging);fail("Unallocated operation escaped global audit")}
            catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
        }
        assertEquals(0,count("supply_transaction"));assertEquals(r,db.dao().recordById(r.id))
    }

    @Test fun globalAuditIncludesEmptyAndIncompatibleUsesAlongsideExactConsumption()=runBlocking {
        val m=med();val b=box(m)
        consume(record(m),b,emptyList())
        val incompatible=ConfirmedStockUse(b.containerUuid,b.presentationEventUuid,b.presentation.sha256,Quantity(BigDecimal.ONE,MeasureUnit.MG,Meaning(QuantityKind.ACTIVE_MASS,"SYNTHETIC unrelated ingredient")),true)
        consume(record(m),b,listOf(incompatible));consume(record(m),b,listOf(use(b)))
        val before=RawData.dump(raw).toString()
        staged { staging ->
            fun ddl()=staging.query("SELECT type,name,sql FROM sqlite_master ORDER BY type,name").use{c->buildList{while(c.moveToNext())add(listOf(c.getString(0),c.getString(1),if(c.isNull(2))null else c.getString(2)))}}
            val data=RawData.dump(staging).toString();val schema=ddl()
            val audited=MeasuredConsumptionStore.auditStagedDataset(staging)
            assertEquals(1,audited.size);assertEquals(1L,audited.single().usedQuanta)
            assertEquals(data,RawData.dump(staging).toString());assertEquals(schema,ddl())
            assertEquals(1,staging.query("PRAGMA foreign_keys").use{it.moveToFirst();it.getInt(0)})
        }
        assertEquals(before,RawData.dump(raw).toString());assertEquals(1,count("supply_transaction"))
    }

    @Test fun globalAuditValidatesNoopCountAndCompleteInverseHistory()=runBlocking {
        val m=med();val b=box(m)
        db.withTransaction{MeasuredConsumptionStore.adjustRemaining(db,b.scope,b.container,b.containerUuid,b.presentationEventUuid,b.presentation.sha256,
            Quantity(BigDecimal("28"),MeasureUnit.TABLET,meaning),true,UUID.randomUUID().toString(),at)}
        val r=record(m);val consumed=consume(r,b,listOf(use(b)))
        db.withTransaction{MeasuredConsumptionStore.reverseOriginal(db,b.scope,r,consumed.operationUuid,UUID.randomUUID().toString(),at)}
        staged { staging ->assertEquals(0L,MeasuredConsumptionStore.auditStagedDataset(staging).single().usedQuanta)}
        assertEquals(3,count("measured_operation"));assertEquals(2,count("supply_transaction"))
    }

    @Test fun globalAuditRejectsMissingActualConfirmationAndDoesNotRepairCorruptCache()=runBlocking {
        val m=med();val b=box(m);consume(record(m),b,listOf(use(b)))
        val before=RawData.dump(raw).toString()
        staged { staging ->
            staging.execSQL("DELETE FROM record_reference_capture WHERE kind='CONFIRM'")
            try {MeasuredConsumptionStore.auditStagedDataset(staging);fail("Missing actual source accepted")}
            catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
        }
        staged { staging ->
            staging.execSQL("UPDATE supply_container SET used_amount=10")
            try {MeasuredConsumptionStore.auditStagedDataset(staging);fail("Corrupt cache accepted")}
            catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
            assertEquals(10.0,staging.query("SELECT used_amount FROM supply_container").use{it.moveToFirst();it.getDouble(0)},0.0)
        }
        assertEquals(before,RawData.dump(raw).toString())
    }

    @Test fun globalAuditRejectsNoopCountWithAMissingCausalAnchor()=runBlocking {
        val m=med();val b=box(m);val op=UUID.randomUUID().toString()
        db.withTransaction{MeasuredConsumptionStore.adjustRemaining(db,b.scope,b.container,b.containerUuid,b.presentationEventUuid,b.presentation.sha256,
            Quantity(BigDecimal("28"),MeasureUnit.TABLET,meaning),true,op,at)}
        staged { staging ->
            val row=ReferenceStore.row(staging,"measured_operation","operation_uuid=?",arrayOf(op))!!
            val input=org.json.JSONObject(row.getString("input_json"));input.getJSONObject("execution").remove("ledger_head_id")
            staging.execSQL("UPDATE measured_operation SET input_json=? WHERE operation_uuid=?",arrayOf(ReferencePayload.canonical(input),op))
            try {MeasuredConsumptionStore.auditStagedDataset(staging);fail("No-op count with missing anchor accepted")}
            catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
        }
        assertEquals(0,count("supply_transaction"))
    }

    @Test fun globalAuditRejectsNoopObservationPointingAtAFutureSameBalanceHead()=runBlocking {
        val m=med();val b=box(m);val observation=UUID.randomUUID().toString()
        db.withTransaction{MeasuredConsumptionStore.adjustRemaining(db,b.scope,b.container,b.containerUuid,b.presentationEventUuid,b.presentation.sha256,
            Quantity(BigDecimal("28"),MeasureUnit.TABLET,meaning),true,observation,at)}
        val r=record(m);val consumed=consume(r,b,listOf(use(b)))
        db.withTransaction{MeasuredConsumptionStore.reverseOriginal(db,b.scope,r,consumed.operationUuid,UUID.randomUUID().toString(),at)}
        staged { staging ->
            assertEquals(0L,MeasuredConsumptionStore.auditStagedDataset(staging).single().usedQuanta)
            val row=ReferenceStore.row(staging,"measured_operation","operation_uuid=?",arrayOf(observation))!!
            val input=org.json.JSONObject(row.getString("input_json"))
            val future=staging.query("SELECT MAX(id) FROM supply_transaction").use{it.moveToFirst();it.getLong(0)}
            input.getJSONObject("execution").put("ledger_head_id",future.toString())
            staging.execSQL("UPDATE measured_operation SET input_json=? WHERE operation_uuid=?",arrayOf(ReferencePayload.canonical(input),observation))
            val nodes=staging.query("SELECT operation_uuid,input_json FROM measured_operation ORDER BY rowid").use{c->buildList{while(c.moveToNext())add(c.getString(0) to org.json.JSONObject(c.getString(1)))}}
            var previous:String?=null;var hash:String?=null
            for((index,pair) in nodes.withIndex()) {
                val (id,payload)=pair
                payload.put("chain",org.json.JSONObject().put("sequence",(index+1).toString()).put("previous_operation_uuid",previous ?: org.json.JSONObject.NULL).put("previous_payload_sha256",hash ?: org.json.JSONObject.NULL))
                val text=ReferencePayload.canonical(payload);staging.execSQL("UPDATE measured_operation SET input_json=? WHERE operation_uuid=?",arrayOf(text,id))
                previous=id;hash=ReferencePayload.sha(text)
            }
            assertEquals(3,MeasuredOperationChain.validate(staging).size)
            try {MeasuredConsumptionStore.auditStagedDataset(staging);fail("Future no-op anchor escaped the global audit")}
            catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
        }
        assertEquals(0.0,db.dao().container(b.container.id).used_amount,0.0)
    }

    @Test fun globalAuditRejectsOverflowedRevisionEvenWhenAnOperationHasNoAllocation()=runBlocking {
        val m=med();val b=box(m);val r=record(m);val op=consume(r,b,emptyList()).operationUuid
        staged { staging ->
            staging.execSQL("UPDATE measured_operation SET record_revision=4294967297 WHERE operation_uuid=?",arrayOf(op))
            try {MeasuredConsumptionStore.auditStagedDataset(staging);fail("Overflowed revision was truncated into a valid record revision")}
            catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
        }
        assertEquals(r,db.dao().recordById(r.id));assertEquals(0,count("supply_transaction"))
    }

    @Test fun globalAuditRejectsCurrentActualRecordThatDisagreesWithFrozenInput()=runBlocking {
        val m=med();val b=box(m);val r=record(m);consume(r,b,emptyList())
        staged { staging ->
            staging.execSQL("UPDATE dose_record SET actual_dose=7.0 WHERE id=?",arrayOf(r.id))
            try {MeasuredConsumptionStore.auditStagedDataset(staging);fail("Current factual dose disagrees with frozen original input")}
            catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
        }
        assertEquals(r,db.dao().recordById(r.id))
    }

    @Test fun globalAuditRejectsConsumedPackageMarkedVoidedWithoutLegalLifecycleEvidence()=runBlocking {
        val m=med();val b=box(m);consume(record(m),b,listOf(use(b)))
        staged { staging ->
            staging.execSQL("UPDATE supply_container SET state='VOIDED' WHERE id=?",arrayOf(b.container.id))
            try {MeasuredConsumptionStore.auditStagedDataset(staging);fail("Consumed package was accepted as VOIDED")}
            catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
        }
        assertEquals("IN_USE",db.dao().container(b.container.id).state);assertEquals(1,count("supply_transaction"))
    }

    @Test fun globalAuditKeepsRetiredContainerEvidenceSeparateFromAReusedLegacyIntegerId()=runBlocking {
        val m=med();val b=box(m)
        val incompatible=ConfirmedStockUse(b.containerUuid,b.presentationEventUuid,b.presentation.sha256,Quantity(BigDecimal.ONE,MeasureUnit.MG,Meaning(QuantityKind.ACTIVE_MASS,"SYNTHETIC unrelated")),true)
        consume(record(m),b,listOf(incompatible))
        raw.execSQL("DELETE FROM supply_container WHERE id=?",arrayOf(b.container.id))
        raw.execSQL("INSERT INTO supply_container(id,medication_id,capacity,initial_used_amount,used_amount,state) VALUES(?,?,99,9,9,'IN_USE')",arrayOf(b.container.id,m))
        assertTrue(ReferenceStore.row(raw,"reference_identity","uuid=?",arrayOf(b.containerUuid))!!.isNull("live_handle"))
        val before=RawData.dump(raw).toString()
        staged { staging ->assertTrue(MeasuredConsumptionStore.auditStagedDataset(staging).isEmpty())}
        assertEquals(before,RawData.dump(raw).toString());assertEquals(9.0,db.dao().container(b.container.id).used_amount,0.0)
    }

    @Test fun globalAuditKeepsRetiredRecordFactsSeparateFromAnotherMedicationsReusedId()=runBlocking {
        val m=med();val b=box(m);val r=record(m);consume(r,b,emptyList())
        raw.execSQL("DELETE FROM dose_record WHERE id=?",arrayOf(r.id))
        val other=med("SYNTHETIC unrelated new medication")
        db.dao().record(r.copy(medication_id=other,actual_dose=7.0))
        assertTrue(ReferenceStore.row(raw,"reference_identity","kind='RECORD' AND original_handle=?",arrayOf(r.id))!!.isNull("live_handle"))
        staged { staging ->assertEquals(0L,MeasuredConsumptionStore.auditStagedDataset(staging).single().usedQuanta)}
        assertEquals(other,db.dao().recordById(r.id)!!.medication_id);assertEquals(7.0,db.dao().recordById(r.id)!!.actual_dose!!,0.0)
    }

    @Test fun globalAuditRejectsRetiredIdentityAttachedToLiveIntegerLedgerHistory()=runBlocking {
        val m=med();val b=box(m);consume(record(m),b,listOf(use(b)))
        staged { staging ->
            staging.execSQL("UPDATE reference_identity SET live_handle=NULL WHERE uuid=?",arrayOf(b.containerUuid))
            try {MeasuredConsumptionStore.auditStagedDataset(staging);fail("Retired evidence attached to current integer rows")}
            catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
        }
        assertEquals(1.0,db.dao().container(b.container.id).used_amount,0.0)
    }

    @Test fun globalAuditAcceptsRealVoidedUnallocatedUseWithoutInventingConsumption()=runBlocking {
        val m=med();val b=box(m)
        db.withTransaction{PackageCorrections.void(raw,db.dao(),b.container.id,at)}
        val result=consume(record(m),b,listOf(use(b)));assertEquals("VOIDED",result.issue)
        staged { staging ->assertEquals(0L,MeasuredConsumptionStore.auditStagedDataset(staging).single().usedQuanta)}
        assertEquals(0,count("supply_transaction"));assertEquals("VOIDED",db.dao().container(b.container.id).state)
    }

    @Test fun globalAuditRejectsOperationUuidCollidingWithAReferenceEvent()=runBlocking {
        val m=med();val b=box(m);val op=consume(record(m),b,emptyList()).operationUuid
        staged { staging ->
            staging.execSQL("UPDATE measured_operation SET operation_uuid=? WHERE operation_uuid=?",arrayOf(b.presentationEventUuid,op))
            try {MeasuredConsumptionStore.auditStagedDataset(staging);fail("Cross-domain operation UUID collision accepted")}
            catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
        }
        assertEquals(1,count("measured_operation"));assertEquals(0,count("supply_transaction"))
    }

    @Test fun globalAuditRejectsInverseThatWouldRewriteALaterIndependentNoopCount()=runBlocking {
        val m=med();val b=box(m);val r=record(m);val consumed=consume(r,b,listOf(use(b)))
        val inverse=UUID.randomUUID().toString()
        db.withTransaction{MeasuredConsumptionStore.reverseOriginal(db,b.scope,r,consumed.operationUuid,inverse,at)}
        val observation=UUID.randomUUID().toString()
        db.withTransaction{MeasuredConsumptionStore.adjustRemaining(db,b.scope,db.dao().container(b.container.id),b.containerUuid,b.presentationEventUuid,b.presentation.sha256,
            Quantity(BigDecimal("28"),MeasureUnit.TABLET,meaning),true,observation,at)}
        staged { staging ->
            assertEquals(0L,MeasuredConsumptionStore.auditStagedDataset(staging).single().usedQuanta)
            val row=ReferenceStore.row(staging,"measured_operation","operation_uuid=?",arrayOf(observation))!!
            val changed=org.json.JSONObject(row.getString("input_json"));val request=changed.getJSONObject("request")
            request.put("expected_used_quanta","1").put("expected_state","IN_USE").put("remaining_quanta","55")
            request.getJSONObject("remaining_input").put("value","27.5");request.getJSONObject("remaining_stock").put("value","27.5")
            val originalEntry=staging.query("SELECT id FROM supply_transaction WHERE kind='CONSUME'").use{it.moveToFirst();it.getLong(0)}
            changed.getJSONObject("execution").put("ledger_head_id",originalEntry.toString())
            staging.execSQL("UPDATE measured_operation SET request_sha256=?,input_json=? WHERE operation_uuid=?",arrayOf(ReferencePayload.sha(ReferencePayload.canonical(request)),ReferencePayload.canonical(changed),observation))
            // Make every hash and prefix internally valid; the independent count still prohibits this inverse.
            var previous:String?=null;var hash:String?=null
            for((index,id) in listOf(consumed.operationUuid,observation,inverse).withIndex()) {
                val payload=org.json.JSONObject(ReferenceStore.row(staging,"measured_operation","operation_uuid=?",arrayOf(id))!!.getString("input_json"))
                payload.put("chain",org.json.JSONObject().put("sequence",(index+1).toString()).put("previous_operation_uuid",previous ?: org.json.JSONObject.NULL).put("previous_payload_sha256",hash ?: org.json.JSONObject.NULL))
                val text=ReferencePayload.canonical(payload);staging.execSQL("UPDATE measured_operation SET input_json=? WHERE operation_uuid=?",arrayOf(text,id))
                previous=id;hash=ReferencePayload.sha(text)
            }
            assertEquals(3,MeasuredOperationChain.validate(staging).size)
            try {MeasuredConsumptionStore.auditStagedDataset(staging);fail("Inverse silently rewrote the independent count")}
            catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
        }
        assertEquals(0.0,db.dao().container(b.container.id).used_amount,0.0);assertEquals(2,count("supply_transaction"))
    }

    @Test fun globalAuditCannotTreatAnExactContainerWithAllReferencesMissingAsLegacyScalar()=runBlocking {
        val m=med();val b=box(m)
        staged { staging ->
            staging.execSQL("DELETE FROM reference_event")
            staging.execSQL("DELETE FROM reference_snapshot")
            staging.execSQL("DELETE FROM reference_identity WHERE kind='CONTAINER'")
            staging.execSQL("DELETE FROM reference_identity WHERE kind='MEDICATION'")
            staging.execSQL("DELETE FROM reference_dataset")
            assertEquals(56.0,staging.query("SELECT capacity FROM supply_container").use{it.moveToFirst();it.getDouble(0)},0.0)
            try {MeasuredConsumptionStore.auditStagedDataset(staging);fail("Exact integer stock silently became unmarked legacy scalar")}
            catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
        }
        assertEquals(1,count("reference_event"));assertEquals(56.0,db.dao().container(b.container.id).capacity,0.0)
    }

    @Test fun publishedLegacyVoidEvidenceStillRestoresAfterTheStorageMarkerIsAdded()=runBlocking {
        val m=med()
        val first=db.dao().insertContainer(ContainerEntity(medication_id=m,capacity=28.0,initial_used_amount=0.0,used_amount=0.0,state="SEALED"))
        db.dao().insertContainer(ContainerEntity(medication_id=m,capacity=28.0,initial_used_amount=0.0,used_amount=0.0,state="SEALED"))
        db.withTransaction{PackageCorrections.void(raw,db.dao(),first,at)}
        val old=raw.query("SELECT id,payload_json FROM trash_item WHERE ref LIKE 'package-void:%'").use{it.moveToFirst();it.getLong(0) to org.json.JSONObject(it.getString(1))}
        val legacy=old.second.put("version",1);legacy.remove("container_uuid");legacy.getJSONObject("container").remove("inventory_encoding")
        // Published v1 hashed exactly the pre-marker columns of the other legacy containers.
        val others=Trash.rows(raw,"supply_container","medication_id=? AND id!=? ORDER BY id",arrayOf(m,first))
        for(index in 0 until others.length()) {val row=others.getJSONObject(index);assertEquals(InventoryEncoding.LEGACY,row.getString("inventory_encoding"));row.remove("inventory_encoding")}
        val meds=Trash.rows(raw,"medication","id=? ORDER BY id",arrayOf(m))
        val records=Trash.rows(raw,"dose_record","medication_id=? ORDER BY id",arrayOf(m))
        val entries=Trash.rows(raw,"supply_transaction","container_id IN (SELECT id FROM supply_container WHERE medication_id=?) ORDER BY id",arrayOf(m))
        legacy.put("context_sha256",ReferencePayload.sha(meds.toString()+others.toString()+records.toString()+entries.toString()))
        db.withTransaction{RawData.unguarded(raw){raw.execSQL("UPDATE trash_item SET payload_json=? WHERE id=?",arrayOf(legacy.toString(),old.first))}}
        val repository=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
        repository.restoreVoidedContainer(first)
        assertEquals("SEALED",db.dao().container(first).state);assertEquals(0.0,db.dao().container(first).used_amount,0.0)
        assertEquals(2,count("trash_item"));assertEquals(0,count("supply_transaction"))
    }

    @Test fun inventoryEncodingCannotBeChangedThroughOrdinarySqlInEitherDirection()=runBlocking {
        val m=med();val exact=box(m)
        val legacy=db.dao().insertContainer(ContainerEntity(medication_id=m,capacity=28.0,initial_used_amount=0.0,used_amount=0.0,state="SEALED"))
        for((id,encoding) in listOf(exact.container.id to InventoryEncoding.LEGACY,legacy to InventoryEncoding.EXACT)) {
            try {raw.execSQL("UPDATE supply_container SET inventory_encoding=? WHERE id=?",arrayOf(encoding,id));fail("Inventory encoding changed in place")}
            catch(e:android.database.SQLException){assertTrue(e.message.orEmpty().contains("Immutable inventory encoding"))}
        }
        assertEquals(InventoryEncoding.EXACT,db.dao().container(exact.container.id).inventory_encoding)
        assertEquals(InventoryEncoding.LEGACY,db.dao().container(legacy).inventory_encoding)
    }

    @Test fun inventoryVersionErrorsAreRejectedBeforeOpeningTheDestination()=runBlocking {
        val m=med();val b=box(m)
        val guarded=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space):NotesDatabase {fail("Invalid inventory version opened destination");return db}})
        val password="SYNTHETIC inventory version fixture".toCharArray()
        for(schema in listOf(10,11)) {
            val tables=RawData.dump(raw)
            if(schema==11)tables.getJSONArray("supply_container").getJSONObject(0).remove("inventory_encoding")
            val data=BackupCodec.encrypt(org.json.JSONObject().put("format",1).put("schema",schema).put("tables",tables).toString().toByteArray(),password)
            try {guarded.restoreBackup(data,password);fail("Inventory schema mismatch accepted")}
            catch(e:IllegalArgumentException){assertTrue(e.message.orEmpty().contains(if(schema==10)"Misdeclared inventory" else "Missing or invalid inventory"))}
            finally {data.fill(0)}
        }
        password.fill('\u0000');assertEquals(InventoryEncoding.EXACT,db.dao().container(b.container.id).inventory_encoding)
    }

    @Test fun publishedBusinessSchemaTenRestoresLegacyAmountsAndProductReferencesWithoutInferringUnits()=runBlocking {
        val repository=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
        val m=med();repository.addContainers(m,28.0,1,true)
        repository.associateMedication(repository.referenceScope(),m,0,UUID.randomUUID().toString(),product)
        repository.unscheduled(m,at,0.1)
        val password="SYNTHETIC schema10 fixture".toCharArray()
        val backup=repository.exportBackup(password)
        val old=org.json.JSONObject(String(BackupCodec.decrypt(backup,password),Charsets.UTF_8))
        assertEquals(11,old.getInt("schema"));old.put("schema",10)
        PublishedBusinessFixture.stripLegacyEncoding(old.getJSONObject("tables"))
        val encoded=BackupCodec.encrypt(old.toString().toByteArray(),password)
        val before=RawData.dump(raw).toString();val ticket=repository.referenceScope()
        repository.restoreBackup(encoded,password)
        assertEquals(before,RawData.dump(raw).toString());assertNotEquals(ticket.epoch,repository.referenceScope().epoch)
        assertEquals(InventoryEncoding.LEGACY,db.dao().containers().single().inventory_encoding)
        assertEquals(product,repository.medicationReference(repository.referenceScope(),m)!!.product)
        assertEquals(0.1,db.dao().records().single().actual_dose!!,0.0)
        backup.fill(0);encoded.fill(0);password.fill('\u0000')
    }

    @Test fun globalAuditRejectsForgedIdentityInAnAlreadyRestoredPackageAudit()=runBlocking {
        val m=med();val id=db.dao().insertContainer(ContainerEntity(medication_id=m,capacity=28.0,initial_used_amount=0.0,used_amount=0.0,state="SEALED"))
        db.withTransaction{PackageCorrections.void(raw,db.dao(),id,at);PackageCorrections.restore(raw,db.dao(),id,at)}
        staged { staging ->
            val event=staging.query("SELECT id,payload_json FROM trash_item WHERE ref LIKE 'package-void:%'").use{it.moveToFirst();it.getLong(0) to org.json.JSONObject(it.getString(1))}
            event.second.put("container_uuid",UUID.randomUUID().toString())
            staging.execSQL("UPDATE trash_item SET payload_json=? WHERE id=?",arrayOf(event.second.toString(),event.first))
            try {MeasuredConsumptionStore.auditStagedDataset(staging);fail("Restored package hid a forged historical UUID")}
            catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
        }
        assertEquals("SEALED",db.dao().container(id).state);assertEquals(2,count("trash_item"))
    }

    @Test fun malformedTrailingJsonCannotRestoreOrRetireTheActiveDataset()=runBlocking {
        val repository=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
        val m=med();repository.addContainers(m,28.0,1,true)
        val password="SYNTHETIC strict input fixture".toCharArray()
        val valid=repository.exportBackup(password)
        val trailing=String(BackupCodec.decrypt(valid,password),Charsets.UTF_8)+" SYNTHETIC_TRAILING_CONTENT"
        val bad=BackupCodec.encrypt(trailing.toByteArray(Charsets.UTF_8),password)
        val scope=repository.referenceScope();val before=RawData.dump(raw).toString()
        var rejected=false
        try {repository.restoreBackup(bad,password)}catch(_:BackupCodec.BadFile){rejected=true}
        assertTrue("Trailing JSON content was accepted as a complete backup",rejected)
        assertEquals(before,RawData.dump(raw).toString());assertEquals(scope,repository.referenceScope())
        valid.fill(0);bad.fill(0);password.fill('\u0000')
    }

    @Test fun strictInputRetainsProjectionLexemesUntilTheirIntegerContractIsChecked()=runBlocking {
        val m=med();box(m)
        assertEquals(net.plainnotes.app.domain.stock.StockQuantum.MAX_QUANTA,InventoryEncoding.MAX_EXACT_INTEGER)
        for(token in listOf("1.0000000000000001","1e0","-0")) {
            val source=RawData.dump(raw).toString().replace("\"used_amount\":0","\"used_amount\":"+token)
            val parsed=BackupJsonReader.read(source)
            try {RecordBackupCompatibility.validateInventoryVersion(11,parsed);fail("Non-canonical projection accepted: $token")}
            catch(e:IllegalArgumentException){assertTrue(e.message.orEmpty().contains("Non-integer exact inventory"))}
        }
        val valid=BackupJsonReader.read(RawData.dump(raw).toString())
        RecordBackupCompatibility.validateInventoryVersion(11,valid)
        assertTrue(valid.getJSONArray("supply_container").getJSONObject(0).get("capacity") is Int)
    }

    @Test fun strictJsonRejectsAmbiguousSyntaxDuplicateFieldsAndInvalidUtf8()=runBlocking {
        val bad=listOf("{\"x\":1} trailing","{\"x\":1,\"x\":2}","{\"x\":01}","{\"x\":NaN}","{\"x\":Infinity}","{/*comment*/\"x\":1}","{'x':1}","{\"x\":1,}")
        for(text in bad) {
            try {BackupJsonReader.read(text);fail("Invalid JSON accepted: $text")}
            catch(_:BackupCodec.BadFile){}
        }
        try {BackupJsonReader.read(byteArrayOf(123,34,120,34,58,34,0xc3.toByte(),34,125));fail("Malformed UTF-8 accepted")}
        catch(_:BackupCodec.BadFile){}
        val result=BackupJsonReader.read("{\"x\":0.1,\"n\":-0,\"unicode\":\"é µg\"}")
        assertEquals(BigDecimal("0.1"),result.get("x"));assertTrue(result.get("n") is BigDecimal)
        assertEquals("é µg",result.getString("unicode"))
    }

    @Test fun strictJsonEnforcesArrayFieldAndDepthBudgetsBeforeBuildingAnUnboundedTree()=runBlocking {
        val arrays="{\"x\":["+List(BackupLimits.MAX_ROWS+1){"0"}.joinToString(",")+"]}"
        val fields="{"+(0..256).joinToString(","){"\"key$it\":0"}+"}"
        val deep="{\"x\":"+"[".repeat(40)+"0"+"]".repeat(40)+"}"
        for(text in listOf(arrays,fields,deep)) {
            try {BackupJsonReader.read(text);fail("JSON resource budget bypassed")}
            catch(_:BackupCodec.TooLarge){}
        }
        assertEquals(1,BackupJsonReader.read("{\"x\":[0]}").getJSONArray("x").length())
    }

    @Test fun globalAuditRejectsRestoredEvidenceWhoseBodyDiffersFromTheOriginalVoid()=runBlocking {
        val m=med();val b=box(m)
        db.withTransaction {PackageCorrections.void(raw,db.dao(),b.container.id,at);PackageCorrections.restore(raw,db.dao(),b.container.id,at)}
        staged { staging ->
            val row=staging.query("SELECT id,payload_json FROM trash_item WHERE ref LIKE 'package-restore:%'").use {it.moveToFirst();it.getLong(0) to org.json.JSONObject(it.getString(1))}
            row.second.getJSONObject("container").put("used_amount",1)
            staging.execSQL("UPDATE trash_item SET payload_json=? WHERE id=?",arrayOf(row.second.toString(),row.first))
            try {MeasuredConsumptionStore.auditStagedDataset(staging);fail("Restore body changed the original voided physical facts")}
            catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
        }
        assertEquals("SEALED",db.dao().container(b.container.id).state)
        assertEquals(0.0,db.dao().container(b.container.id).used_amount,0.0)
    }

    @Test fun globalAuditRejectsInvalidExactCapacityInPreviouslyRestoredLifecycleEvidence()=runBlocking {
        val m=med();val b=box(m)
        db.withTransaction {PackageCorrections.void(raw,db.dao(),b.container.id,at);PackageCorrections.restore(raw,db.dao(),b.container.id,at)}
        staged { staging ->
            val rows=staging.query("SELECT id,payload_json FROM trash_item WHERE ref LIKE 'package-%'").use {c->buildList {while(c.moveToNext())add(c.getLong(0) to org.json.JSONObject(c.getString(1)))}}
            for((id,payload) in rows) {
                payload.getJSONObject("container").put("capacity",-1)
                staging.execSQL("UPDATE trash_item SET payload_json=? WHERE id=?",arrayOf(payload.toString(),id))
            }
            try {MeasuredConsumptionStore.auditStagedDataset(staging);fail("Historical exact capacity was never validated")}
            catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
        }
        assertEquals(56.0,db.dao().container(b.container.id).capacity,0.0)
    }

    @Test fun internalMeasuredBackupRoundTripsPhysicalHistoryAndReplacesANonemptyTarget()=runBlocking {
        val repository=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
        val m=med();val b=box(m);val r=record(m)
        val receipt=consume(r,b,listOf(use(b,"1.5")))
        db.withTransaction{MeasuredConsumptionStore.reverseOriginal(db,b.scope,r,receipt.operationUuid,UUID.randomUUID().toString(),at)}
        consume(record(m),b,emptyList())
        val password="SYNTHETIC exact round trip".toCharArray()
        val oldEpoch=repository.referencePreviewEpoch()
        val backup=repository.exportMeasuredBackup(password,oldEpoch)
        val expected=RawData.dump(raw).toString()
        val header=BackupJsonReader.read(BackupCodec.decrypt(backup,password))
        assertEquals(11,RecordBackupCompatibility.readVersion(header))
        try {RecordBackupCompatibility.readVersion(header,10);fail("Old reader accepted new inventory business schema")}
        catch(_:BackupCodec.NewerBackup){}
        med("SYNTHETIC target-only medication")
        assertNotEquals(expected,RawData.dump(raw).toString())
        repository.restoreMeasuredBackup(backup,password,oldEpoch)
        assertEquals(expected,RawData.dump(raw).toString())
        val firstEpoch=repository.referencePreviewEpoch();assertNotEquals(oldEpoch,firstEpoch)
        db.withTransaction {assertEquals(1,MeasuredConsumptionStore.auditStagedDataset(raw).size)}
        repository.restoreMeasuredBackup(backup,password,firstEpoch)
        assertEquals(expected,RawData.dump(raw).toString());assertNotEquals(firstEpoch,repository.referencePreviewEpoch())
        backup.fill(0);password.fill('\u0000')
    }

    @Test fun internalMeasuredRestoreRejectsCorruptCacheWithoutReplacingAnyTargetFactOrEpoch()=runBlocking {
        val repository=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
        val m=med();val b=box(m);consume(record(m),b,listOf(use(b)))
        val password="SYNTHETIC corrupt cache".toCharArray();val epoch=repository.referencePreviewEpoch()
        val backup=repository.exportMeasuredBackup(password,epoch)
        val parsed=BackupJsonReader.read(BackupCodec.decrypt(backup,password))
        parsed.getJSONObject("tables").getJSONArray("supply_container").getJSONObject(0).put("used_amount",2)
        val corrupt=BackupCodec.encrypt(parsed.toString().toByteArray(),password)
        val before=RawData.dump(raw).toString()
        rejects(ReferenceError.INVALID_SOURCE){repository.restoreMeasuredBackup(corrupt,password,epoch)}
        assertEquals(before,RawData.dump(raw).toString());assertEquals(epoch,repository.referencePreviewEpoch())
        assertEquals(1.0,db.dao().container(b.container.id).used_amount,0.0)
        backup.fill(0);corrupt.fill(0);password.fill('\u0000')
    }

    @Test fun internalMeasuredBackupRequiresCurrentLocalEpochAndPrimaryBeforeLookingUpIds()=runBlocking {
        val access=object:DatabaseAccess(context){override fun get(space:Space)=db};val repository=NotesRepository(access)
        val m=med();val b=box(m);consume(record(m),b,emptyList())
        val password="SYNTHETIC scope fixture".toCharArray();val epoch=repository.referencePreviewEpoch()
        val backup=repository.exportMeasuredBackup(password,epoch);val before=RawData.dump(raw).toString()
        rejects(ReferenceError.STALE_SCOPE){repository.exportMeasuredBackup(password,UUID.randomUUID().toString())}
        rejects(ReferenceError.STALE_SCOPE){repository.restoreMeasuredBackup(backup,password,UUID.randomUUID().toString())}
        access.select(Space.DECOY)
        rejects(ReferenceError.WRONG_SPACE){repository.restoreMeasuredBackup(backup,password,epoch)}
        rejects(ReferenceError.WRONG_SPACE){repository.exportMeasuredBackup(password,epoch)}
        access.select(Space.PRIMARY)
        assertEquals(before,RawData.dump(raw).toString());assertEquals(epoch,repository.referencePreviewEpoch())
        backup.fill(0);password.fill('\u0000')
    }

    @Test fun publicLegacyBackupEntryDoesNotAccidentallyEnableMeasuredRestoreOrExport()=runBlocking {
        val repository=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
        val m=med();val b=box(m);consume(record(m),b,emptyList())
        val password="SYNTHETIC guarded public fixture".toCharArray();val epoch=repository.referencePreviewEpoch()
        val backup=repository.exportMeasuredBackup(password,epoch);val before=RawData.dump(raw).toString()
        var exportRejected=false;try {repository.exportBackup(password)}catch(_:IllegalArgumentException){exportRejected=true}
        assertTrue("Public exact export became enabled before forecast/UI admission",exportRejected)
        var restoreRejected=false;try {repository.restoreBackup(backup,password)}catch(_:IllegalArgumentException){restoreRejected=true}
        assertTrue("Public exact restore became enabled before forecast/UI admission",restoreRejected)
        assertEquals(before,RawData.dump(raw).toString());assertEquals(epoch,repository.referencePreviewEpoch())
        backup.fill(0);password.fill('\u0000')
    }

    @Test fun measuredRestoreRollsBackEveryReplacementCheckpointIncludingTheNewEpoch()=runBlocking {
        val repository=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
        val m=med();val b=box(m);consume(record(m),b,listOf(use(b)))
        val password="SYNTHETIC atomic restore fixture".toCharArray();val epoch=repository.referencePreviewEpoch()
        val backup=repository.exportMeasuredBackup(password,epoch)
        med("SYNTHETIC target-only data to preserve on rollback")
        val before=RawData.dump(raw).toString()
        fun ddl()=raw.query("SELECT type,name,sql FROM sqlite_master WHERE sql IS NOT NULL ORDER BY type,name").use {c->buildList{while(c.moveToNext())add(listOf(c.getString(0),c.getString(1),c.getString(2)))}}
        val definitions=ddl()
        for(stage in RestoreCheckpoint.entries) {
            var reached=false
            try {
                repository.restoreMeasuredBackup(backup,password,epoch,checkpoint={point->if(point==stage){reached=true;throw IllegalStateException("SYNTHETIC injected restore failure")}})
                fail("Restore committed despite injected $stage failure")
            } catch(e:IllegalStateException){assertEquals("SYNTHETIC injected restore failure",e.message)}
            assertTrue("Checkpoint not exercised: $stage",reached)
            assertEquals(before,RawData.dump(raw).toString());assertEquals(epoch,repository.referencePreviewEpoch())
            assertEquals(definitions,ddl());raw.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
            db.withTransaction{assertEquals(1,MeasuredConsumptionStore.auditStagedDataset(raw).size)}
        }
        repository.restoreMeasuredBackup(backup,password,epoch)
        assertNotEquals(before,RawData.dump(raw).toString());assertNotEquals(epoch,repository.referencePreviewEpoch())
        backup.fill(0);password.fill('\u0000')
    }

    @Test fun measuredBackupRestoresOnANewEncryptedDatabaseWithoutAnyCurrentCatalog()=runBlocking {
        val repository=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
        val m=med();val b=box(m);consume(record(m),b,listOf(use(b,"1.5")))
        val password="SYNTHETIC new device fixture".toCharArray()
        val sourceEpoch=repository.referencePreviewEpoch();val backup=repository.exportMeasuredBackup(password,sourceEpoch)
        val expected=RawData.dump(raw).toString();val otherName="synthetic-new-device-${UUID.randomUUID()}.db"
        val other=Room.databaseBuilder(context,NotesDatabase::class.java,otherName).openHelperFactory(factory(ByteArray(32){(it+99).toByte()})).allowMainThreadQueries().addCallback(SchemaGuards).build()
        try {
            val target=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=other})
            val oldEpoch=target.referencePreviewEpoch()
            target.restoreMeasuredBackup(backup,password,oldEpoch)
            val targetRaw=other.openHelper.writableDatabase
            assertEquals(expected,RawData.dump(targetRaw).toString())
            assertNotEquals(oldEpoch,target.referencePreviewEpoch());assertNotEquals(sourceEpoch,target.referencePreviewEpoch())
            other.withTransaction {assertEquals(1,MeasuredConsumptionStore.auditStagedDataset(targetRaw).size)}
            assertEquals(sourceEpoch,repository.referencePreviewEpoch());assertEquals(expected,RawData.dump(raw).toString())
        } finally {other.close();context.deleteDatabase(otherName);backup.fill(0);password.fill('\u0000')}
    }

    private fun actual(b:CreatedMeasuredContainer,effect:MeasuredStockEffect=MeasuredStockEffect.DEDUCT_CURRENT)=MeasuredActualUseDraft(
        takenUtc=at.toEpochMilli(),takenZone="Europe/Paris",clinicalDose=0.125,uses=listOf(use(b)),stockEffect=effect,userConfirmed=true)
    private fun repository()=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})

    @Test fun repositoryWritesActualClinicalFactAndPhysicalConsumptionAtomicallyAndIdempotently()=runBlocking {
        val m=med();val b=box(m);val repo=repository();val ticket=repo.measuredWriteTicket(b.scope,m)
        val op=UUID.randomUUID().toString();val before=db.dao().medication(m)
        val first=repo.recordMeasuredUse(ticket,op,actual(b),at)
        val replay=repo.recordMeasuredUse(ticket,op,actual(b),at)
        assertEquals(first,replay);assertEquals(1,count("dose_record"));assertEquals(1,count("supply_transaction"))
        assertEquals(0.125,first.record.actual_dose!!,0.0);assertEquals(1.0,db.dao().container(b.container.id).used_amount,0.0)
        assertEquals(before,db.dao().medication(m));assertNull(db.dao().profile(m))
        rejects(ReferenceError.OPERATION_CONFLICT){repo.recordMeasuredUse(ticket,op,actual(b).copy(clinicalDose=2.0),at)}
        rejects(ReferenceError.OPERATION_CONFLICT){repo.recordMeasuredUse(ticket,op,actual(b).copy(stockEffect=MeasuredStockEffect.UNKNOWN),at)}
        assertEquals(1,count("dose_record"));assertEquals(1,count("supply_transaction"))
    }

    @Test fun repositoryActualUseRetainsAlreadyAccountedAndUnknownStockEffectsWithoutDeducting()=runBlocking {
        val m=med();val b=box(m);val repo=repository()
        val accounted=repo.recordMeasuredUse(repo.measuredWriteTicket(b.scope,m),UUID.randomUUID().toString(),actual(b,MeasuredStockEffect.ALREADY_ACCOUNTED),at)
        assertTrue(accounted.receipt.lines.single() is PhysicalUseResult.AlreadyAccounted);assertNull(accounted.receipt.issue)
        val unknown=repo.recordMeasuredUse(repo.measuredWriteTicket(b.scope,m),UUID.randomUUID().toString(),actual(b,MeasuredStockEffect.UNKNOWN),at)
        assertEquals("STOCK_EFFECT_NOT_CONFIRMED",unknown.receipt.issue)
        assertEquals(BigDecimal("0.5"),(unknown.receipt.lines.single() as PhysicalUseResult.Unknown).requested.value)
        assertEquals(2,count("dose_record"));assertEquals(0,count("supply_transaction"));assertEquals(0.0,db.dao().container(b.container.id).used_amount,0.0)
        db.withTransaction{assertEquals(1,MeasuredConsumptionStore.auditStagedDataset(raw).size)}
    }

    @Test fun repositoryPreservesUnlinkedPhysicalActualQuantityAcrossExactBackupRestore()=runBlocking {
        val m=med();val b=box(m);val repo=repository()
        val physical=Quantity(BigDecimal("0.25"),MeasureUnit.ML,Meaning(QuantityKind.FORMULATION,"SYNTHETIC unconfirmed formulation"))
        val draft=actual(b).copy(uses=emptyList(),unlinkedActual=physical)
        val saved=repo.recordMeasuredUse(repo.measuredWriteTicket(b.scope,m),UUID.randomUUID().toString(),draft,at)
        assertEquals("ACTUAL_PACKAGE_NOT_CONFIRMED",saved.receipt.issue);assertEquals(0,count("supply_transaction"))
        val op=raw.query("SELECT input_json,unallocated_quantity,unallocated_unit FROM measured_operation").use{it.moveToFirst();assertEquals("0.25",it.getString(1));assertEquals("ML",it.getString(2));org.json.JSONObject(it.getString(0))}
        assertEquals(physical,MeasuredUsePayload.read(ReferencePayload.canonical(op.getJSONObject("request"))).request().unlinkedActual)
        val password="SYNTHETIC actual physical fact backup".toCharArray();val epoch=repo.referencePreviewEpoch()
        val backup=repo.exportMeasuredBackup(password,epoch);val before=RawData.dump(raw).toString()
        repo.restoreMeasuredBackup(backup,password,epoch)
        assertEquals(before,RawData.dump(raw).toString());assertEquals(0,count("supply_transaction"))
        backup.fill(0);password.fill('\u0000')
    }

    @Test fun repositoryActualUseRollsBackRecordReferenceAndStockAtEveryFaultCheckpoint()=runBlocking {
        val m=med();val b=box(m);val repo=repository();val ticket=repo.measuredWriteTicket(b.scope,m)
        val before=RawData.dump(raw).toString()
        for(stage in ActualUseCheckpoint.entries) {
            var reached=false
            try {repo.recordMeasuredUse(ticket,UUID.randomUUID().toString(),actual(b),at,checkpoint={point->if(point==stage){reached=true;throw IllegalStateException("SYNTHETIC actual-use fault")}});fail("Partial actual use committed at $stage")}
            catch(e:IllegalStateException){assertEquals("SYNTHETIC actual-use fault",e.message)}
            assertTrue(reached);assertEquals(before,RawData.dump(raw).toString());assertEquals(ticket,repo.measuredWriteTicket(b.scope,m))
            assertEquals(0,count("dose_record"));assertEquals(0,count("supply_transaction"))
        }
    }

    @Test fun zeroDifferenceCountInvalidatesAnActualUseTicketEvenWithIdenticalContainerAndLedger()=runBlocking {
        val m=med();val b=box(m);val repo=repository();val ticket=repo.measuredWriteTicket(b.scope,m)
        val before=db.dao().container(b.container.id)
        db.withTransaction{MeasuredConsumptionStore.adjustRemaining(db,b.scope,before,b.containerUuid,b.presentationEventUuid,b.presentation.sha256,Quantity(BigDecimal("28"),MeasureUnit.TABLET,meaning),true,UUID.randomUUID().toString(),at)}
        assertEquals(before,db.dao().container(b.container.id));assertEquals(0,count("supply_transaction"))
        rejects(ReferenceError.STALE_REVISION){repo.recordMeasuredUse(ticket,UUID.randomUUID().toString(),actual(b),at)}
        assertEquals(0,count("dose_record"));assertEquals(1,count("measured_operation"))
    }

    @Test fun returnedBalanceAfterAnAbaAdjustmentDoesNotReviveAnOldActualUseTicket()=runBlocking {
        val m=med();val b=box(m);val repo=repository();val ticket=repo.measuredWriteTicket(b.scope,m)
        for(amount in listOf("27","28"))db.withTransaction{MeasuredConsumptionStore.adjustRemaining(db,b.scope,db.dao().container(b.container.id),b.containerUuid,b.presentationEventUuid,b.presentation.sha256,Quantity(BigDecimal(amount),MeasureUnit.TABLET,meaning),true,UUID.randomUUID().toString(),at)}
        assertEquals(0.0,db.dao().container(b.container.id).used_amount,0.0)
        rejects(ReferenceError.STALE_REVISION){repo.recordMeasuredUse(ticket,UUID.randomUUID().toString(),actual(b),at)}
        assertEquals(0,count("dose_record"));assertEquals(2,count("supply_transaction"))
    }

    @Test fun actualUseRejectsStaleDatasetBeforeLookingUpReusedIdsAndNeverTargetsDecoy()=runBlocking {
        val m=med();val b=box(m);val repo=repository();val ticket=repo.measuredWriteTicket(b.scope,m)
        db.withTransaction{ReferenceSchema.retireRuntime(raw);ReferenceSchema.restoreRuntime(raw)}
        val before=RawData.dump(raw).toString()
        rejects(ReferenceError.STALE_SCOPE){repo.recordMeasuredUse(ticket,UUID.randomUUID().toString(),actual(b),at)}
        val forbidden=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space):NotesDatabase{fail("Decoy actual use opened database");return db}}).pinnedTo(Space.DECOY)
        rejects(ReferenceError.WRONG_SPACE){forbidden.recordMeasuredUse(ticket,UUID.randomUUID().toString(),actual(b),at)}
        assertEquals(before,RawData.dump(raw).toString());assertEquals(0,count("dose_record"))
    }

    private fun countInput(b:CreatedMeasuredContainer,n:String)=ConfirmedStockCount(b.containerUuid,b.presentationEventUuid,b.presentation.sha256,Quantity(BigDecimal(n),MeasureUnit.TABLET,meaning),true)

    @Test fun formalRepositoryCreatesAConfirmedPackageWithoutChangingTheMedicationOrProfile()=runBlocking {
        val m=med();val repo=repository();val original=db.dao().medication(m);val epoch=repo.referencePreviewEpoch();val op=UUID.randomUUID().toString()
        val draft=PackageMeasurementDraft(product,MeasureUnit.TABLET,meaning,"0.5","28",StockCapacityBasis.USER_COUNTED)
        val b=repo.createMeasuredPackage(epoch,original,op,draft,at)
        val replay=repo.createMeasuredPackage(epoch,original,op,draft,at)
        assertEquals(b.container,replay.container);assertEquals(b.containerUuid,replay.containerUuid)
        assertEquals(b.presentationEventUuid,replay.presentationEventUuid);assertEquals(b.presentation.payload,replay.presentation.payload)
        assertEquals(b.scope,replay.scope)
        assertEquals(1,count("supply_container"));assertEquals(56.0,b.container.capacity,0.0)
        assertEquals(original,db.dao().medication(m));assertNull(db.dao().profile(m));assertEquals(0,count("dose_record"))
        val used=repo.recordMeasuredUse(repo.measuredWriteTicket(b.scope,m),UUID.randomUUID().toString(),actual(b),at)
        assertEquals(0.125,used.record.actual_dose!!,0.0);assertEquals(1.0,db.dao().container(b.container.id).used_amount,0.0)
    }

    @Test fun realRepositoryCorrectionUsesOriginalInverseAndCreatesOneNewRevisionWithFrozenHistory()=runBlocking {
        val m=med();val b=box(m);val repo=repository()
        val first=repo.recordMeasuredUse(repo.measuredWriteTicket(b.scope,m),UUID.randomUUID().toString(),actual(b),at)
        val oldOperation=ReferenceStore.row(raw,"measured_operation","operation_uuid=?",arrayOf(first.receipt.operationUuid))!!.toString()
        val ticket=repo.measuredWriteTicket(b.scope,m,first.record.id);val reverse=UUID.randomUUID().toString();val newOp=UUID.randomUUID().toString()
        val draft=actual(b).copy(clinicalDose=0.2,uses=listOf(use(b,"1")))
        val revised=repo.reviseMeasuredUse(ticket,first.receipt.operationUuid,reverse,newOp,draft,at)
        assertEquals(2,revised.record.revision);assertEquals(0.2,revised.record.actual_dose!!,0.0)
        assertEquals(listOf("CONSUME","REVERSE","CONSUME"),db.dao().supplyFor(first.record.id).map{it.kind})
        assertEquals(2.0,db.dao().container(b.container.id).used_amount,0.0)
        assertEquals(oldOperation,ReferenceStore.row(raw,"measured_operation","operation_uuid=?",arrayOf(first.receipt.operationUuid))!!.toString())
        assertEquals(revised,repo.reviseMeasuredUse(ticket,first.receipt.operationUuid,reverse,newOp,draft,at))
        rejects(ReferenceError.OPERATION_CONFLICT){repo.reviseMeasuredUse(ticket,first.receipt.operationUuid,reverse,newOp,draft.copy(clinicalDose=0.3),at)}
        assertEquals(3,count("supply_transaction"));assertEquals(1,count("dose_record"))
    }

    @Test fun repositoryCorrectionRefusesToOverrideAnIndependentLaterStockCount()=runBlocking {
        val m=med();val b=box(m);val repo=repository()
        val first=repo.recordMeasuredUse(repo.measuredWriteTicket(b.scope,m),UUID.randomUUID().toString(),actual(b),at)
        repo.countMeasuredStock(repo.measuredWriteTicket(b.scope,m),UUID.randomUUID().toString(),countInput(b,"27.5"),at)
        val ticket=repo.measuredWriteTicket(b.scope,m,first.record.id);val before=RawData.dump(raw).toString()
        rejects(ReferenceError.OPERATION_CONFLICT){repo.reviseMeasuredUse(ticket,first.receipt.operationUuid,UUID.randomUUID().toString(),UUID.randomUUID().toString(),actual(b).copy(clinicalDose=0.2),at)}
        assertEquals(before,RawData.dump(raw).toString());assertEquals(first.record,db.dao().recordById(first.record.id))
    }

    @Test fun correctionFaultAfterNewConsumptionRollsBackBothInverseAndClinicalRevision()=runBlocking {
        val m=med();val b=box(m);val repo=repository()
        val first=repo.recordMeasuredUse(repo.measuredWriteTicket(b.scope,m),UUID.randomUUID().toString(),actual(b),at)
        val ticket=repo.measuredWriteTicket(b.scope,m,first.record.id);val before=RawData.dump(raw).toString()
        for(stage in ActualUseCheckpoint.entries) {
            var reached=false
            try {repo.reviseMeasuredUse(ticket,first.receipt.operationUuid,UUID.randomUUID().toString(),UUID.randomUUID().toString(),actual(b).copy(clinicalDose=0.2),at,checkpoint={p->if(p==stage){reached=true;throw IllegalStateException("SYNTHETIC revision fault")}});fail("Partial inverse/revision committed")}
            catch(e:IllegalStateException){assertEquals("SYNTHETIC revision fault",e.message)}
            assertTrue(reached);assertEquals(before,RawData.dump(raw).toString())
        }
    }

    @Test fun realRepositoryDeleteAndRestoreAppendExactHistoryAndPreserveTheOriginalClinicalSnapshot()=runBlocking {
        val m=med();val b=box(m);val repo=repository()
        val first=repo.recordMeasuredUse(repo.measuredWriteTicket(b.scope,m),UUID.randomUUID().toString(),actual(b),at)
        val ticket=repo.measuredWriteTicket(b.scope,m,first.record.id);val inverse=UUID.randomUUID().toString()
        val deleted=repo.deleteMeasuredUse(ticket,first.receipt.operationUuid,inverse,at)
        assertNotNull(deleted.deleted_at_utc);assertEquals(2,deleted.revision);assertEquals(0.0,db.dao().container(b.container.id).used_amount,0.0)
        assertEquals(deleted,repo.deleteMeasuredUse(ticket,first.receipt.operationUuid,inverse,at));assertEquals(2,count("supply_transaction"))
        val restoreTicket=repo.measuredWriteTicket(b.scope,m,first.record.id);val restoreOp=UUID.randomUUID().toString()
        val restored=repo.restoreMeasuredUse(restoreTicket,restoreOp,actual(b),at)
        assertNull(restored.record.deleted_at_utc);assertEquals(3,restored.record.revision)
        assertEquals(first.record.config_snapshot,restored.record.config_snapshot);assertEquals(first.record.actual_dose,restored.record.actual_dose)
        assertEquals(1.0,db.dao().container(b.container.id).used_amount,0.0);assertEquals(0,db.dao().trash().count{it.kind==Trash.RECORD})
        assertEquals(restored,repo.restoreMeasuredUse(restoreTicket,restoreOp,actual(b),at));assertEquals(3,count("supply_transaction"))
        val password="SYNTHETIC restored revision backup".toCharArray();val epoch=repo.referencePreviewEpoch()
        val backup=repo.exportMeasuredBackup(password,epoch);val before=RawData.dump(raw).toString();repo.restoreMeasuredBackup(backup,password,epoch)
        assertEquals(before,RawData.dump(raw).toString());backup.fill(0);password.fill('\u0000')
    }

    @Test fun repositoryCountIsIdempotentAndOldTicketsCannotOverwriteEvenAZeroObservation()=runBlocking {
        val m=med();val b=box(m);val repo=repository();val ticket=repo.measuredWriteTicket(b.scope,m);val op=UUID.randomUUID().toString()
        val first=repo.countMeasuredStock(ticket,op,countInput(b,"27.5"),at)
        assertTrue(first.result is MeasureResult.Success)
        assertEquals(first,repo.countMeasuredStock(ticket,op,countInput(b,"27.5"),at));assertEquals(1,count("supply_transaction"))
        rejects(ReferenceError.OPERATION_CONFLICT){repo.countMeasuredStock(ticket,op,countInput(b,"27"),at)}
        rejects(ReferenceError.STALE_REVISION){repo.countMeasuredStock(ticket,UUID.randomUUID().toString(),countInput(b,"28"),at)}
        val fresh=repo.measuredWriteTicket(b.scope,m)
        repo.countMeasuredStock(fresh,UUID.randomUUID().toString(),countInput(b,"27.5"),at)
        rejects(ReferenceError.STALE_REVISION){repo.countMeasuredStock(fresh,UUID.randomUUID().toString(),countInput(b,"28"),at)}
        assertEquals(1,count("supply_transaction"));assertEquals(2,count("measured_operation"))
    }

    @Test fun correctionCannotReuseAnotherRecordsOperationOrAnOlderSourceRevision()=runBlocking {
        val m=med();val b=box(m);val repo=repository()
        val first=repo.recordMeasuredUse(repo.measuredWriteTicket(b.scope,m),UUID.randomUUID().toString(),actual(b),at)
        val second=repo.recordMeasuredUse(repo.measuredWriteTicket(b.scope,m),UUID.randomUUID().toString(),actual(b),at)
        val firstTicket=repo.measuredWriteTicket(b.scope,m,first.record.id)
        rejects(ReferenceError.STALE_REVISION){repo.reviseMeasuredUse(firstTicket,second.receipt.operationUuid,UUID.randomUUID().toString(),UUID.randomUUID().toString(),actual(b),at)}
        val revised=repo.reviseMeasuredUse(firstTicket,first.receipt.operationUuid,UUID.randomUUID().toString(),UUID.randomUUID().toString(),actual(b).copy(clinicalDose=0.2),at)
        val ticket=repo.measuredWriteTicket(b.scope,m,first.record.id)
        rejects(ReferenceError.STALE_REVISION){repo.reviseMeasuredUse(ticket,first.receipt.operationUuid,UUID.randomUUID().toString(),UUID.randomUUID().toString(),actual(b),at)}
        assertEquals(revised.record,db.dao().recordById(first.record.id));assertEquals(second.record,db.dao().recordById(second.record.id))
    }

    @Test fun oldUnscopedPackageVoidCannotMutateExactInventory()=runBlocking {
        val m=med();val b=box(m);val repo=repository();val before=RawData.dump(raw).toString()
        var rejected=false;try {repo.removeMistakenContainer(b.container.id)}catch(_:IllegalArgumentException){rejected=true}
        assertTrue("Old integer-only void bypassed the exact-stock confirmation ticket",rejected)
        assertEquals(before,RawData.dump(raw).toString());assertEquals("SEALED",db.dao().container(b.container.id).state)
    }
    @Test fun oldUnscopedPackageRestoreCannotMutateExactInventory()=runBlocking {
        val m=med();val b=box(m);val repo=repository()
        db.withTransaction{PackageCorrections.void(raw,db.dao(),b.container.id,at)}
        val before=RawData.dump(raw).toString()
        var rejected=false;try {repo.restoreVoidedContainer(b.container.id)}catch(_:IllegalArgumentException){rejected=true}
        assertTrue("Old integer-only restore bypassed the exact-stock confirmation ticket",rejected)
        assertEquals(before,RawData.dump(raw).toString());assertEquals("VOIDED",db.dao().container(b.container.id).state)
    }

    @Test fun scopedPackageVoidRestoreIsIdempotentAndDoesNotReviveAnOldConfirmationTicket()=runBlocking {
        val m=med();val b=box(m);val repo=repository();val original=db.dao().container(b.container.id)
        val ticket=repo.measuredWriteTicket(b.scope,m);val op=UUID.randomUUID().toString()
        val result=repo.correctMeasuredPackage(ticket,op,b.containerUuid,MeasuredPackageAction.VOID,true,at)
        assertEquals(result,repo.correctMeasuredPackage(ticket,op,b.containerUuid,MeasuredPackageAction.VOID,true,at));assertEquals(1,count("trash_item"))
        val restoreTicket=repo.measuredWriteTicket(b.scope,m);val restoreOp=UUID.randomUUID().toString()
        val restored=repo.correctMeasuredPackage(restoreTicket,restoreOp,b.containerUuid,MeasuredPackageAction.RESTORE,true,at)
        assertEquals(restored,repo.correctMeasuredPackage(restoreTicket,restoreOp,b.containerUuid,MeasuredPackageAction.RESTORE,true,at))
        assertEquals(original,db.dao().container(b.container.id));assertEquals(0,count("supply_transaction"));assertEquals(2,count("trash_item"))
        rejects(ReferenceError.STALE_REVISION){repo.recordMeasuredUse(ticket,UUID.randomUUID().toString(),actual(b),at)}
        rejects(ReferenceError.OPERATION_CONFLICT){repo.correctMeasuredPackage(ticket,op,b.containerUuid,MeasuredPackageAction.RESTORE,true,at)}
        assertEquals(0,count("dose_record"))
    }

    @Test fun scopedPackageCorrectionRetainsTheConsumptionGuardAndRejectsForeignIdentity()=runBlocking {
        val m=med();val b=box(m);val other=med("SYNTHETIC other medication");val foreign=box(other);val repo=repository()
        repo.recordMeasuredUse(repo.measuredWriteTicket(b.scope,m),UUID.randomUUID().toString(),actual(b),at)
        val ticket=repo.measuredWriteTicket(b.scope,m);val before=RawData.dump(raw).toString()
        try {repo.correctMeasuredPackage(ticket,UUID.randomUUID().toString(),b.containerUuid,MeasuredPackageAction.VOID,true,at);fail("Consumed exact package was voided")}
        catch(_:PackageConsumptionConflict){}
        rejects(ReferenceError.UNKNOWN_IDENTITY){repo.correctMeasuredPackage(ticket,UUID.randomUUID().toString(),foreign.containerUuid,MeasuredPackageAction.VOID,true,at)}
        assertEquals(before,RawData.dump(raw).toString())
    }

    @Test fun sharedRepositoryForecastKeepsExactPhysicalBalancesSeparateFromClinicalDoseAndChangesVersionOnZeroCount()=runBlocking {
        val m=med();val b=box(m);val other=box(m,strength="SYNTHETIC 1 mg / tablet");val repo=repository()
        db.dao().updateMedication(db.dao().medication(m).copy(active=true,soon_alert_minutes=0,late_after_minutes=60))
        repo.recordMeasuredUse(repo.measuredWriteTicket(b.scope,m),UUID.randomUUID().toString(),actual(b),at)
        val first=repo.stockForecasts(at,java.time.ZoneId.of("Europe/Paris")).getValue(m)
        assertEquals(StockForecast.Status.UNKNOWN,first.status);assertNull(first.available);assertNull(first.firstShort);assertFalse(first.isLow(java.time.LocalDate.parse("2026-10-16"),java.time.ZoneId.of("Europe/Paris")))
        assertEquals(2,first.physicalBalances.size);assertEquals(2,first.physicalBalances.map{it.presentationSha256}.distinct().size)
        assertEquals(BigDecimal("27.5"),first.physicalBalances.single{it.containerUuid==b.containerUuid}.remaining.value)
        assertEquals(BigDecimal("28"),first.physicalBalances.single{it.containerUuid==other.containerUuid}.remaining.value)
        assertTrue(StockUnknownReason.UNKNOWN_UNIT in first.uncertainty);assertFalse(StockUnknownReason.UNKNOWN_SOURCE in first.uncertainty)
        assertEquals(first,repo.stockForecasts(at,java.time.ZoneId.of("Europe/Paris")).getValue(m))
        repo.countMeasuredStock(repo.measuredWriteTicket(b.scope,m),UUID.randomUUID().toString(),countInput(b,"27.5"),at)
        val second=repo.stockForecasts(at,java.time.ZoneId.of("Europe/Paris")).getValue(m)
        assertNotEquals(first.snapshotSha256,second.snapshotSha256);assertEquals(first.physicalBalances,second.physicalBalances)
        assertEquals(1,count("supply_transaction"))
    }

    @Test fun legacyActualUseCannotSilentlyChooseScalarStockWhenTypedStockAlsoExists()=runBlocking {
        val m=med();val exact=box(m);val repo=repository()
        val legacy=db.dao().insertContainer(ContainerEntity(medication_id=m,capacity=20.0,initial_used_amount=0.0,used_amount=0.0,opened_on=null,state="SEALED"))
        val before=RawData.dump(raw).toString()
        try {repo.unscheduled(m,at,0.125);fail("Old allocator silently selected legacy stock without actual-package confirmation")}
        catch(e:IllegalArgumentException){assertTrue(e.message.orEmpty().contains("physical-use"))}
        assertEquals(before,RawData.dump(raw).toString())
        assertEquals(0.0,db.dao().container(legacy).used_amount,0.0)
        assertEquals(0.0,db.dao().container(exact.container.id).used_amount,0.0)
    }
    @Test fun oldAllocatorCannotAddScalarConsumptionToAnExplicitUnknownStockEffect()=runBlocking {
        val m=med();val b=box(m);val repo=repository()
        val saved=repo.recordMeasuredUse(repo.measuredWriteTicket(b.scope,m),UUID.randomUUID().toString(),actual(b,MeasuredStockEffect.UNKNOWN),at)
        val before=RawData.dump(raw).toString()
        try {db.withTransaction{SupplyLedger.allocate(db.dao(),saved.record)};fail("A second allocator accepted an explicitly captured unknown stock effect")}
        catch(e:IllegalArgumentException){assertTrue(e.message.orEmpty().contains("physical-use"))}
        assertEquals(before,RawData.dump(raw).toString());assertEquals(0,count("supply_transaction"))
    }

    @Test fun typedStockOfAnotherMedicationDoesNotBlockLegacyOnlyActualUse()=runBlocking {
        val measured=med();box(measured)
        val legacyMed=med("SYNTHETIC legacy-only medication")
        val id=db.dao().insertContainer(ContainerEntity(medication_id=legacyMed,capacity=20.0,initial_used_amount=0.0,used_amount=0.0,opened_on=null,state="SEALED"))
        repository().unscheduled(legacyMed,at,0.125)
        assertEquals(0.125,db.dao().container(id).used_amount,0.0)
        assertEquals(1,count("dose_record"));assertEquals(1,count("supply_transaction"));assertEquals(0,count("measured_operation"))
        assertEquals(legacyMed,db.dao().records().single().medication_id)
    }

    private suspend fun scheduledFixture():Triple<NotesRepository,CreatedMeasuredContainer,net.plainnotes.app.domain.Slot> {
        val repo=repository();val m=med()
        repo.saveMedication(db.dao().medication(m).copy(active=true,soon_alert_minutes=0,late_after_minutes=60),null,net.plainnotes.app.domain.RuleKind.EVERY_N_DAYS,1,
            listOf(java.time.LocalTime.NOON),emptySet(),at.minusSeconds(86400))
        val b=box(m)
        val slot=repo.planned(at.minusSeconds(1),at.plusSeconds(1),at,java.time.ZoneId.of("UTC")).single{it.slot.medicationId==m}.slot
        return Triple(repo,b,slot)
    }
    @Test fun scheduledActualUseKeepsPlannedFieldsAndRetriesWithoutSecondConsumption()=runBlocking {
        val (repo,b,slot)=scheduledFixture();val ticket=repo.measuredScheduledTicket(b.scope,slot,at);val op=UUID.randomUUID().toString()
        val saved=repo.completeMeasuredSlot(ticket,op,actual(b),at)
        assertEquals(slot.key,saved.record.slot_key);assertEquals(slot.ruleId,saved.record.rule_version_id)
        assertEquals(slot.at.toEpochMilli(),saved.record.scheduled_utc);assertEquals(slot.zone.id,saved.record.scheduled_zone)
        assertEquals(slot.dose,saved.record.planned_dose!!,0.0);assertEquals(0.125,saved.record.actual_dose!!,0.0)
        assertEquals(saved,repo.completeMeasuredSlot(ticket,op,actual(b),at));assertEquals(1,count("dose_record"));assertEquals(1,count("supply_transaction"))
        val frozen=MeasuredConsumptionStore.existingConsumption(raw,op)!!.first
        assertEquals(MeasuredUsePayload.scheduleSha(saved.record),frozen.record.scheduleSha256)
        rejects(ReferenceError.OPERATION_CONFLICT){repo.recordMeasuredUse(ticket.write,op,actual(b),at)}
        rejects(ReferenceError.OPERATION_CONFLICT){repo.completeMeasuredSlot(ticket.copy(slot=slot.copy(dose=2.0)),op,actual(b),at)}
        rejects(ReferenceError.STALE_REVISION){repo.completeMeasuredSlot(ticket,UUID.randomUUID().toString(),actual(b),at)}
    }
    @Test fun scheduledBackfillReusesMissedIdAndSnapshotAndDoesNotGuessStockEffect()=runBlocking {
        val (repo,b,slot)=scheduledFixture();val config=db.dao().rules().single{it.id==slot.ruleId}.config_snapshot
        val old=RecordEntity(medication_id=slot.medicationId,rule_version_id=slot.ruleId,slot_key=slot.key,scheduled_utc=slot.at.toEpochMilli(),scheduled_zone=slot.zone.id,
            planned_dose=slot.dose,late_after_minutes_snapshot=slot.lateMinutes,status="MISSED",origin="APP",revision=1,config_snapshot=config)
        val id=db.dao().record(old)
        db.dao().updateMedication(db.dao().medication(slot.medicationId).copy(name="SYNTHETIC renamed later",dose_per_intake=99.0))
        val ticket=repo.measuredScheduledTicket(b.scope,slot,at)
        val saved=repo.completeMeasuredSlot(ticket,UUID.randomUUID().toString(),actual(b,MeasuredStockEffect.ALREADY_ACCOUNTED),at)
        assertEquals(id,saved.record.id);assertEquals(2,saved.record.revision);assertEquals(config,saved.record.config_snapshot)
        assertEquals(slot.dose,saved.record.planned_dose!!,0.0);assertEquals(0,count("supply_transaction"))
        assertTrue(saved.receipt.lines.single() is PhysicalUseResult.AlreadyAccounted)
    }
    @Test fun scheduledWriteRejectsCanceledSlotWithoutMutation()=runBlocking {
        val (repo,b,slot)=scheduledFixture();val ticket=repo.measuredScheduledTicket(b.scope,slot,at)
        repo.override(slot,net.plainnotes.app.domain.SlotOverride(slot.key,skipped=true),at)
        val before=RawData.dump(raw).toString()
        rejects(ReferenceError.STALE_REVISION){repo.completeMeasuredSlot(ticket,UUID.randomUUID().toString(),actual(b),at)}
        assertEquals(before,RawData.dump(raw).toString());assertEquals(0,count("supply_transaction"))
    }
    @Test fun scheduledWriteRollsBackRecordContextAndStockAtEachFailureBoundary()=runBlocking {
        val (repo,b,slot)=scheduledFixture();val ticket=repo.measuredScheduledTicket(b.scope,slot,at)
        val before=RawData.dump(raw).toString()
        for(point in ActualUseCheckpoint.entries) {
            try{repo.completeMeasuredSlot(ticket,UUID.randomUUID().toString(),actual(b),at){if(it==point)error("synthetic scheduled fault")};fail("Fault not reached")}
            catch(e:IllegalStateException){assertEquals("synthetic scheduled fault",e.message)}
            assertEquals(before,RawData.dump(raw).toString())
        }
        repo.completeMeasuredSlot(ticket,UUID.randomUUID().toString(),actual(b),at)
        assertEquals(1,count("dose_record"));assertEquals(1,count("supply_transaction"))
    }
    @Test fun changedScheduledFactCannotPassFullAuditWithOnlyTheClinicalDoseUnchanged()=runBlocking {
        val (repo,b,slot)=scheduledFixture()
        val saved=repo.completeMeasuredSlot(repo.measuredScheduledTicket(b.scope,slot,at),UUID.randomUUID().toString(),actual(b),at)
        db.withTransaction {RawData.unguarded(raw){raw.execSQL("UPDATE dose_record SET planned_dose=9 WHERE id=?",arrayOf(saved.record.id))}}
        try{db.withTransaction{MeasuredConsumptionStore.auditStagedDataset(raw)};fail("Frozen schedule changed without detection")}
        catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
    }

    @Test fun scheduledDoseOverrideInvalidatesOldTicketAndKeepsClinicalDoseIndependent()=runBlocking {
        val (repo,b,slot)=scheduledFixture();val old=repo.measuredScheduledTicket(b.scope,slot,at)
        repo.override(slot,net.plainnotes.app.domain.SlotOverride(slot.key,dose=0.2),at)
        val before=RawData.dump(raw).toString()
        rejects(ReferenceError.STALE_REVISION){repo.completeMeasuredSlot(old,UUID.randomUUID().toString(),actual(b),at)}
        assertEquals(before,RawData.dump(raw).toString())
        val changed=repo.planned(at.minusSeconds(1),at.plusSeconds(1),at,slot.zone).single{it.slot.key==slot.key}.slot
        val saved=repo.completeMeasuredSlot(repo.measuredScheduledTicket(b.scope,changed,at),UUID.randomUUID().toString(),actual(b),at)
        assertEquals(0.2,saved.record.planned_dose!!,0.0);assertEquals(0.125,saved.record.actual_dose!!,0.0)
    }
    @Test fun scheduledEvidenceSurvivesEncryptedBackupWithoutTheCurrentCatalog()=runBlocking {
        val (repo,b,slot)=scheduledFixture()
        val saved=repo.completeMeasuredSlot(repo.measuredScheduledTicket(b.scope,slot,at),UUID.randomUUID().toString(),actual(b),at)
        val before=RawData.dump(raw).toString();val password="SYNTHETIC scheduled backup".toCharArray()
        val bytes=repo.exportMeasuredBackup(password,repo.referencePreviewEpoch())
        try {
            repo.restoreMeasuredBackup(bytes,password,repo.referencePreviewEpoch())
            assertEquals(before,RawData.dump(raw).toString());assertEquals(saved.record,db.dao().recordById(saved.record.id))
            assertEquals(MeasuredUsePayload.scheduleSha(saved.record),MeasuredConsumptionStore.existingConsumption(raw,saved.receipt.operationUuid)!!.first.record.scheduleSha256)
            assertNotEquals(b.scope.epoch,repo.referencePreviewEpoch())
        }finally{password.fill('\u0000');bytes.fill(0)}
    }
    @Test fun scheduledParisSpringGapPreservesActualAndPlannedZonesWithoutExtraStock()=runBlocking {
        val moment=Instant.parse("2026-03-29T01:00:00Z");val paris=java.time.ZoneId.of("Europe/Paris")
        val repo=repository();val m=med()
        repo.saveMedication(db.dao().medication(m).copy(active=true,soon_alert_minutes=0,late_after_minutes=60),null,net.plainnotes.app.domain.RuleKind.EVERY_N_DAYS,1,
            listOf(java.time.LocalTime.of(2,30)),emptySet(),moment.minusSeconds(86400))
        val b=box(m);val slot=repo.planned(moment.minusSeconds(1),moment.plusSeconds(1),moment,paris).single{it.slot.medicationId==m}.slot
        assertEquals(moment,slot.at);assertEquals(paris,slot.zone)
        val saved=repo.completeMeasuredSlot(repo.measuredScheduledTicket(b.scope,slot,at),UUID.randomUUID().toString(),actual(b,MeasuredStockEffect.ALREADY_ACCOUNTED).copy(takenUtc=moment.toEpochMilli()),at)
        assertEquals("Europe/Paris",saved.record.scheduled_zone);assertEquals("Europe/Paris",saved.record.taken_zone)
        assertEquals(moment.toEpochMilli(),saved.record.taken_utc);assertEquals(0,count("supply_transaction"))
    }

    private suspend fun reconciliationFixture(remaining:String="27"):Triple<NotesRepository,CreatedMeasuredContainer,RecordedMeasuredUse> {
        val m=med();val b=box(m);val repo=repository()
        val first=repo.recordMeasuredUse(repo.measuredWriteTicket(b.scope,m),UUID.randomUUID().toString(),actual(b).copy(uses=listOf(use(b,"14"))),at)
        repo.countMeasuredStock(repo.measuredWriteTicket(b.scope,m),UUID.randomUUID().toString(),countInput(b,remaining),at)
        return Triple(repo,b,first)
    }
    private fun corrected(b:CreatedMeasuredContainer)=actual(b,MeasuredStockEffect.ALREADY_ACCOUNTED).copy(clinicalDose=0.2)
    private suspend fun reconcile(repo:NotesRepository,b:CreatedMeasuredContainer,first:RecordedMeasuredUse,n:String="27",reverse:String=UUID.randomUUID().toString(),next:String=UUID.randomUUID().toString())=
        repo.reconcileMeasuredUse(repo.measuredWriteTicket(b.scope,first.record.medication_id,first.record.id),first.receipt.operationUuid,reverse,next,listOf(countInput(b,n)),corrected(b),at)

    @Test fun reconciliationPreservesCurrentCountEvenWhenOriginalInverseWouldBeNegative()=runBlocking {
        val (repo,b,first)=reconciliationFixture();val original=db.dao().supplyForContainer(b.container.id)
        val old=ReferenceStore.row(raw,"measured_operation","operation_uuid=?",arrayOf(first.receipt.operationUuid))!!.toString()
        val saved=reconcile(repo,b,first)
        assertEquals(2,saved.record.revision);assertEquals(0.2,saved.record.actual_dose!!,0.0)
        assertEquals(2.0,db.dao().container(b.container.id).used_amount,0.0)
        assertEquals(original,db.dao().supplyForContainer(b.container.id).take(original.size))
        assertEquals(listOf(28.0,-26.0,26.0,-28.0,2.0),db.dao().supplyForContainer(b.container.id).map{it.used_delta})
        assertEquals(old,ReferenceStore.row(raw,"measured_operation","operation_uuid=?",arrayOf(first.receipt.operationUuid))!!.toString())
        val bridges=raw.query("SELECT input_json FROM measured_operation WHERE kind='ADJUST'").use{c->buildList{while(c.moveToNext()){val r=org.json.JSONObject(c.getString(0)).getJSONObject("request");if(r.has("accounting_bridge_for"))add(r)}}}
        assertEquals(1,bridges.size);assertFalse(bridges.single().getBoolean("user_confirmed"))
        assertTrue(saved.receipt.lines.single() is PhysicalUseResult.AlreadyAccounted)
        db.withTransaction {assertEquals(2L,MeasuredConsumptionStore.auditStagedDataset(raw).single().usedQuanta)}
    }
    @Test fun reconciliationReplaysOriginalConfirmedInputsWithoutRecountingTodaysBalance()=runBlocking {
        val (repo,b,first)=reconciliationFixture();val ticket=repo.measuredWriteTicket(b.scope,first.record.medication_id,first.record.id)
        val reverse=UUID.randomUUID().toString();val next=UUID.randomUUID().toString();val counts=listOf(countInput(b,"27"))
        val saved=repo.reconcileMeasuredUse(ticket,first.receipt.operationUuid,reverse,next,counts,corrected(b),at)
        val before=RawData.dump(raw).toString()
        assertEquals(saved,repo.reconcileMeasuredUse(ticket,first.receipt.operationUuid,reverse,next,counts,corrected(b),at))
        rejects(ReferenceError.OPERATION_CONFLICT){repo.reconcileMeasuredUse(ticket,first.receipt.operationUuid,reverse,next,listOf(countInput(b,"26")),corrected(b),at)}
        rejects(ReferenceError.OPERATION_CONFLICT){repo.reconcileMeasuredUse(ticket,first.receipt.operationUuid,reverse,next,counts,corrected(b).copy(clinicalDose=0.3),at)}
        assertEquals(before,RawData.dump(raw).toString())
        repo.countMeasuredStock(repo.measuredWriteTicket(b.scope,first.record.medication_id),UUID.randomUUID().toString(),countInput(b,"25"),at)
        assertEquals(saved,repo.reconcileMeasuredUse(ticket,first.receipt.operationUuid,reverse,next,counts,corrected(b),at))
        assertEquals(6.0,db.dao().container(b.container.id).used_amount,0.0)
    }
    @Test fun reconciliationWithoutBridgeStillRequiresTheConfirmedFinalCount()=runBlocking {
        val (repo,b,first)=reconciliationFixture("0")
        reconcile(repo,b,first,"0")
        assertEquals(listOf(28.0,28.0,-28.0,28.0),db.dao().supplyForContainer(b.container.id).map{it.used_delta})
        assertEquals("EMPTY",db.dao().container(b.container.id).state)
        db.withTransaction {assertEquals(56L,MeasuredConsumptionStore.auditStagedDataset(raw).single().usedQuanta)}
    }
    @Test fun reconciliationAtFullRemainingCountHasBoundedBridgeAndNoInventedPhysicalUse()=runBlocking {
        val (repo,b,first)=reconciliationFixture("28")
        reconcile(repo,b,first,"28")
        assertEquals(listOf(28.0,-28.0,28.0,-28.0),db.dao().supplyForContainer(b.container.id).map{it.used_delta})
        assertEquals(0.0,db.dao().container(b.container.id).used_amount,0.0)
        db.withTransaction {assertEquals(0L,MeasuredConsumptionStore.auditStagedDataset(raw).single().usedQuanta)}
    }
    @Test fun reconciliationFailureAtEveryBoundaryRollsBackBridgeInverseCountsAndClinicalFact()=runBlocking {
        val (repo,b,first)=reconciliationFixture();val ticket=repo.measuredWriteTicket(b.scope,first.record.medication_id,first.record.id)
        val before=RawData.dump(raw).toString()
        for(point in ReconciliationCheckpoint.entries) {
            var reached=false
            try {repo.reconcileMeasuredUse(ticket,first.receipt.operationUuid,UUID.randomUUID().toString(),UUID.randomUUID().toString(),listOf(countInput(b,"27")),corrected(b),at){if(it==point){reached=true;error("synthetic reconciliation fault")}};fail("Expected injected fault")}
            catch(e:IllegalStateException){assertTrue(reached);assertEquals("synthetic reconciliation fault",e.message)}
            assertEquals(before,RawData.dump(raw).toString())
        }
    }
    @Test fun reconciliationRejectsMissingIncompatibleUnconfirmedOrForeignCurrentCounts()=runBlocking {
        val (repo,b,first)=reconciliationFixture();val ticket=repo.measuredWriteTicket(b.scope,first.record.medication_id,first.record.id)
        val other=box(med("SYNTHETIC foreign"));val before=RawData.dump(raw).toString()
        val incompatible=countInput(b,"1").copy(remaining=Quantity(BigDecimal.ONE,MeasureUnit.ML,Meaning(QuantityKind.FORMULATION,"SYNTHETIC liquid")))
        for(input in listOf(emptyList(),listOf(incompatible),listOf(countInput(b,"27").copy(userConfirmed=false)),listOf(countInput(other,"27")),listOf(countInput(b,"29")))) {
            rejects(ReferenceError.INVALID_SOURCE){repo.reconcileMeasuredUse(ticket,first.receipt.operationUuid,UUID.randomUUID().toString(),UUID.randomUUID().toString(),input,corrected(b),at)}
            assertEquals(before,RawData.dump(raw).toString())
        }
    }
    @Test fun reconciliationNeverDeductsASecondDoseAfterTheConfirmedCurrentCount()=runBlocking {
        val (repo,b,first)=reconciliationFixture();val before=RawData.dump(raw).toString()
        try {repo.reconcileMeasuredUse(repo.measuredWriteTicket(b.scope,first.record.medication_id,first.record.id),first.receipt.operationUuid,UUID.randomUUID().toString(),UUID.randomUUID().toString(),listOf(countInput(b,"27")),actual(b),at);fail("Implicit deduction after observed current stock")}
        catch(_:IllegalArgumentException){}
        assertEquals(before,RawData.dump(raw).toString())
    }
    @Test fun reconciliationDeleteRestoreAndEncryptedBackupRetainTheObservedCurrentStock()=runBlocking {
        val (repo,b,first)=reconciliationFixture();val ticket=repo.measuredWriteTicket(b.scope,first.record.medication_id,first.record.id);val reverse=UUID.randomUUID().toString()
        val counts=listOf(countInput(b,"27"));val deleted=repo.reconcileDeleteMeasuredUse(ticket,first.receipt.operationUuid,reverse,counts,at)
        assertNotNull(deleted.deleted_at_utc);assertEquals(deleted,repo.reconcileDeleteMeasuredUse(ticket,first.receipt.operationUuid,reverse,counts,at))
        assertEquals(2.0,db.dao().container(b.container.id).used_amount,0.0)
        val restored=repo.restoreMeasuredUse(repo.measuredWriteTicket(b.scope,first.record.medication_id,first.record.id),UUID.randomUUID().toString(),actual(b,MeasuredStockEffect.ALREADY_ACCOUNTED).copy(uses=listOf(use(b,"14"))),at)
        assertNull(restored.record.deleted_at_utc);assertEquals(3,restored.record.revision)
        val before=RawData.dump(raw).toString();val password="SYNTHETIC reconciliation backup".toCharArray()
        val bytes=repo.exportMeasuredBackup(password,repo.referencePreviewEpoch())
        try {repo.restoreMeasuredBackup(bytes,password,repo.referencePreviewEpoch());assertEquals(before,RawData.dump(raw).toString())}
        finally {password.fill('\u0000');bytes.fill(0)}
        assertEquals(2.0,db.dao().container(b.container.id).used_amount,0.0)
    }
    private fun rechainForReconciliationTamper(change:(String,org.json.JSONObject)->Unit) {
        val nodes=raw.query("SELECT operation_uuid,input_json FROM measured_operation").use{c->buildList{while(c.moveToNext())add(c.getString(0) to org.json.JSONObject(c.getString(1)))}}.sortedBy{it.second.getJSONObject("chain").getString("sequence").toInt()}
        var previous:String?=null;var hash:String?=null
        for((index,pair) in nodes.withIndex()) {
            val (id,payload)=pair;change(id,payload)
            payload.put("chain",org.json.JSONObject().put("sequence",(index+1).toString()).put("previous_operation_uuid",previous ?: org.json.JSONObject.NULL).put("previous_payload_sha256",hash ?: org.json.JSONObject.NULL))
            val text=ReferencePayload.canonical(payload)
            raw.execSQL("UPDATE measured_operation SET input_json=?,request_sha256=? WHERE operation_uuid=?",arrayOf(text,ReferencePayload.sha(ReferencePayload.canonical(payload.getJSONObject("request"))),id))
            previous=id;hash=ReferencePayload.sha(text)
        }
    }
    @Test fun reconciliationAuditRejectsChangedTargetEvenWithValidRequestHashesAndExecutionChain()=runBlocking {
        val (repo,b,first)=reconciliationFixture();val reverse=UUID.randomUUID().toString()
        reconcile(repo,b,first,reverse=reverse)
        db.withTransaction {RawData.unguarded(raw){rechainForReconciliationTamper {id,p->if(id==reverse)p.getJSONObject("request").getJSONObject("reconciliation").getJSONArray("targets").getJSONObject(0).put("remaining_input",PackageMeasurementPayload.quantity(countInput(b,"26").remaining))}}}
        db.withTransaction {assertTrue(MeasuredOperationChain.validate(raw).isNotEmpty())}
        rejects(ReferenceError.INVALID_SOURCE){db.withTransaction{MeasuredConsumptionStore.auditStagedDataset(raw)}}
        assertEquals(2.0,db.dao().container(b.container.id).used_amount,0.0)
    }
    @Test fun reconciliationAuditRejectsAnOrphanAccountingBridgeWithAnOtherwiseValidChain()=runBlocking {
        val (repo,b,first)=reconciliationFixture();reconcile(repo,b,first)
        db.withTransaction {RawData.unguarded(raw){rechainForReconciliationTamper {_,p->val r=p.getJSONObject("request");if(r.has("accounting_bridge_for"))r.put("accounting_bridge_for",UUID.randomUUID().toString())}}}
        db.withTransaction {assertTrue(MeasuredOperationChain.validate(raw).isNotEmpty())}
        rejects(ReferenceError.INVALID_SOURCE){db.withTransaction{MeasuredConsumptionStore.auditStagedDataset(raw)}}
    }
    @Test fun reconciliationAuditRejectsMissingNoOpFinalCountEvenWhenBalancesAndChainRemainValid()=runBlocking {
        val (repo,b,first)=reconciliationFixture("28");val reverse=UUID.randomUUID().toString();reconcile(repo,b,first,"28",reverse)
        val proof=MeasuredConsumptionStore.sourceOfInverse(raw,reverse)!!.getJSONObject("reconciliation")
        val finalCount=proof.getJSONArray("targets").getJSONObject(0).getString("count_uuid")
        assertEquals(0,raw.query("SELECT 1 FROM measured_allocation WHERE operation_uuid=?",arrayOf(finalCount)).use{it.count})
        db.withTransaction {RawData.unguarded(raw){raw.execSQL("DELETE FROM measured_operation WHERE operation_uuid=?",arrayOf(finalCount));rechainForReconciliationTamper{_,_->}}}
        db.withTransaction {assertTrue(MeasuredOperationChain.validate(raw).isNotEmpty())}
        rejects(ReferenceError.INVALID_SOURCE){db.withTransaction{MeasuredConsumptionStore.auditStagedDataset(raw)}}
        assertEquals(0.0,db.dao().container(b.container.id).used_amount,0.0)
    }

    @Test fun reconciliationKeepsTwoExplicitlyConfirmedStrengthsSeparateAndRequiresBothCounts()=runBlocking {
        val m=med();val one=box(m);val otherMeaning=meaning.copy(identity="SYNTHETIC other strength")
        val two=box(m,otherMeaning,"SYNTHETIC 1mg tablet");val repo=repository()
        val first=repo.recordMeasuredUse(repo.measuredWriteTicket(one.scope,m),UUID.randomUUID().toString(),actual(one).copy(uses=listOf(use(one,"14"),use(two,"1",otherMeaning))),at)
        val counts=listOf(countInput(two,"28").let{it.copy(remaining=it.remaining.copy(meaning=otherMeaning))},countInput(one,"28"))
        for(c in counts)repo.countMeasuredStock(repo.measuredWriteTicket(one.scope,m),UUID.randomUUID().toString(),c,at)
        val ticket=repo.measuredWriteTicket(one.scope,m,first.record.id);val before=RawData.dump(raw).toString()
        rejects(ReferenceError.INVALID_SOURCE){repo.reconcileMeasuredUse(ticket,first.receipt.operationUuid,UUID.randomUUID().toString(),UUID.randomUUID().toString(),counts.take(1),corrected(one),at)}
        assertEquals(before,RawData.dump(raw).toString())
        val saved=repo.reconcileMeasuredUse(ticket,first.receipt.operationUuid,UUID.randomUUID().toString(),UUID.randomUUID().toString(),counts,corrected(one),at)
        assertEquals(0.2,saved.record.actual_dose!!,0.0)
        assertEquals(0.0,db.dao().container(one.container.id).used_amount,0.0);assertEquals(0.0,db.dao().container(two.container.id).used_amount,0.0)
        db.withTransaction {val projections=MeasuredConsumptionStore.auditStagedDataset(raw);assertEquals(2,projections.size);assertEquals(setOf(meaning,otherMeaning),projections.map{it.remaining.meaning}.toSet())}
    }
    @Test fun reconciliationPreservesUnknownStockEffectWithoutApplyingAnotherConsumption()=runBlocking {
        // Forecasts intentionally contain active medications only; activate this synthetic fixture explicitly.
        val (repo,b,first)=reconciliationFixture()
        db.dao().updateMedication(db.dao().medication(first.record.medication_id).copy(active=true,soon_alert_minutes=0,late_after_minutes=60))
        val ticket=repo.measuredWriteTicket(b.scope,first.record.medication_id,first.record.id)
        val saved=repo.reconcileMeasuredUse(ticket,first.receipt.operationUuid,UUID.randomUUID().toString(),UUID.randomUUID().toString(),listOf(countInput(b,"27")),corrected(b).copy(stockEffect=MeasuredStockEffect.UNKNOWN),at)
        assertEquals("STOCK_EFFECT_NOT_CONFIRMED",saved.receipt.issue)
        assertEquals(2.0,db.dao().container(b.container.id).used_amount,0.0)
        assertEquals(1,db.dao().supplyFor(first.record.id).count{it.kind=="CONSUME"})
        val forecast=repo.stockForecasts(at,java.time.ZoneId.of("Europe/Paris")).getValue(first.record.medication_id)
        assertEquals(StockForecast.Status.UNKNOWN,forecast.status);assertTrue(StockUnknownReason.STOCK_EFFECT_UNKNOWN in forecast.uncertainty)
    }
    @Test fun reconciliationRejectsStaleZeroCountTicketWrongEpochAndDecoyBeforeAnyWrite()=runBlocking {
        val (repo,b,first)=reconciliationFixture();val m=first.record.medication_id;val ticket=repo.measuredWriteTicket(b.scope,m,first.record.id)
        repo.countMeasuredStock(repo.measuredWriteTicket(b.scope,m),UUID.randomUUID().toString(),countInput(b,"27"),at)
        val before=RawData.dump(raw).toString()
        rejects(ReferenceError.STALE_REVISION){repo.reconcileMeasuredUse(ticket,first.receipt.operationUuid,UUID.randomUUID().toString(),UUID.randomUUID().toString(),listOf(countInput(b,"27")),corrected(b),at)}
        rejects(ReferenceError.STALE_SCOPE){repo.reconcileMeasuredUse(ticket.copy(scope=b.scope.copy(epoch=UUID.randomUUID().toString())),first.receipt.operationUuid,UUID.randomUUID().toString(),UUID.randomUUID().toString(),listOf(countInput(b,"27")),corrected(b),at)}
        val forbidden=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space):NotesDatabase{fail("Decoy reconciliation opened a database");return db}}).pinnedTo(Space.DECOY)
        rejects(ReferenceError.WRONG_SPACE){forbidden.reconcileMeasuredUse(ticket,first.receipt.operationUuid,UUID.randomUUID().toString(),UUID.randomUUID().toString(),listOf(countInput(b,"27")),corrected(b),at)}
        assertEquals(before,RawData.dump(raw).toString())
    }
    @Test fun reconciliationUsesFrozenPackagingAfterCurrentMedicationAndProductReferenceChanges()=runBlocking {
        val (repo,b,first)=reconciliationFixture();val m=first.record.medication_id
        db.dao().updateMedication(db.dao().medication(m).copy(name="SYNTHETIC changed current name",dose_per_intake=99.0))
        repo.associateMedication(b.scope,m,0,UUID.randomUUID().toString(),product.copy(strength="SYNTHETIC unrelated current strength"))
        val saved=reconcile(repo,b,first)
        assertEquals(first.record.config_snapshot,saved.record.config_snapshot);assertEquals(2.0,db.dao().container(b.container.id).used_amount,0.0)
        assertEquals(99.0,db.dao().medication(m).dose_per_intake,0.0)
        val sources=raw.query("SELECT DISTINCT presentation_event_uuid FROM measured_allocation").use{c->buildSet{while(c.moveToNext())add(c.getString(0))}}
        assertEquals(setOf(b.presentationEventUuid),sources)
    }

    @Test fun materializedSourceCoverageIsReadOnlyAndMatchesOriginalExistencePredicate()=runBlocking {
        val m=med();val other=med("SYNTHETIC other owner")
        db.withTransaction {
            fun uncovered(query:String)=raw.query(query).use{it.moveToFirst()}
            val original="SELECT 1 FROM supply_container c WHERE c.inventory_encoding='INTEGER_QUANTA_V1' AND NOT EXISTS(SELECT 1 FROM reference_identity i JOIN reference_event e ON e.subject_uuid=i.uuid JOIN reference_snapshot s ON s.uuid=e.snapshot_uuid WHERE i.kind='CONTAINER' AND i.live_handle=c.id AND s.kind='PRESENTATION') LIMIT 1"
            fun agrees(expected:Boolean) {
                val before=RawData.dump(raw).toString()
                assertEquals(expected,uncovered(original));assertEquals(expected,uncovered(MeasuredConsumptionStore.EXACT_SOURCE_COVERAGE_QUERY))
                assertEquals(before,RawData.dump(raw).toString())
            }
            agrees(false)
            db.dao().insertContainer(ContainerEntity(medication_id=m,capacity=28.0,initial_used_amount=0.0,used_amount=0.0,state="SEALED"))
            agrees(false) // A legacy scalar needs no inferred official/physical identity.
            box(m);box(other);box(m,Meaning(QuantityKind.USE_COUNT,"SYNTHETIC different strength"))
            agrees(false) // Independent owners and meanings remain separately validated by the full auditor.
            db.dao().insertContainer(ContainerEntity(medication_id=m,capacity=56.0,initial_used_amount=0.0,used_amount=0.0,state="SEALED",inventory_encoding=InventoryEncoding.EXACT))
            agrees(true)
            rejects(ReferenceError.INVALID_SOURCE){MeasuredConsumptionStore.auditStagedDataset(raw)}
        }
    }

}
