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

}
