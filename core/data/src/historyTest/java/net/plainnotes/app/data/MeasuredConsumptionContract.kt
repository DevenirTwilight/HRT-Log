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

}
