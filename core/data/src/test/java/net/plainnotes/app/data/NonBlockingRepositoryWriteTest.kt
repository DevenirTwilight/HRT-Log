package net.plainnotes.app.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import net.plainnotes.app.domain.measurement.*
import net.plainnotes.app.domain.stock.StockCapacityBasis
import net.plainnotes.app.domain.write.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Real Repository/Room transactions with the TEST_ONLY owner. Framework SQLite, not native
 * SQLCipher acceptance; no runtime facade, safe production resolver, or P4 entry is enabled. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class NonBlockingRepositoryWriteTest {
    private lateinit var db:NotesDatabase
    private lateinit var repo:NotesRepository
    private val raw get()=db.openHelper.writableDatabase
    private val at=Instant.parse("2026-10-16T12:00:00Z")
    private val meaning=Meaning(QuantityKind.USE_COUNT,"SYNTHETIC 2mg tablet")
    @Before fun open() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db}).pinnedTo(Space.PRIMARY)
    }
    @After fun close(){db.close()}
    private suspend fun fixture():Pair<CreatedMeasuredContainer,MeasuredWriteTicket> {
        val medication=db.dao().insertMedication(MedicationEntity(name="SYNTHETIC medication",molecule="OTHER",unit="MG",dose_per_intake=0.1,container_capacity=28.0,site_rotation=false,notifications_on=false,active=false,sort_order=0))
        val box=db.withTransaction {
            MeasuredContainerStore.create(db,ReferenceStore.previewEpoch(raw),medication,UUID.randomUUID().toString(),
                PackageMeasurementDraft(UserReferenceProduct("SYNTHETIC tablets",listOf("SYNTHETIC ingredient"),"2 mg / tablet",sourceVersion="synthetic-v1",recordedOn="2026-10-16"),
                    MeasureUnit.TABLET,meaning,"0.5","28",StockCapacityBasis.USER_COUNTED),at)
        }
        return box to repo.measuredWriteTicket(box.scope,medication)
    }
    private fun draft(box:CreatedMeasuredContainer)=MeasuredActualUseDraft(at.toEpochMilli(),"Europe/Paris",0.125,
        listOf(ConfirmedStockUse(box.containerUuid,box.presentationEventUuid,box.presentation.sha256,Quantity(BigDecimal("0.5"),MeasureUnit.TABLET,meaning),true)),
        MeasuredStockEffect.DEDUCT_CURRENT,userConfirmed=true)
    private fun key(box:CreatedMeasuredContainer,uuid:String)=NonBlockingWritePrototype.Key(box.scope.dataset,box.scope.epoch,uuid,"SYNTHETIC immutable request")
    private suspend fun assertOneCommit(box:CreatedMeasuredContainer) {
        assertEquals(1,db.dao().records().size)
        raw.query("SELECT COUNT(*) FROM supply_transaction").use{assertTrue(it.moveToFirst());assertEquals(1,it.getInt(0))}
        assertEquals(1.0,db.dao().container(box.container.id).used_amount,0.0)
        db.withTransaction {
            assertEquals(FrozenFullAuditStore.auditStagedDataset(raw),MeasuredConsumptionStore.auditStagedDataset(raw))
        }
    }
    private fun runCase(block:suspend CoroutineScope.(CoroutineScope)->Unit)=runBlocking {
        val owner=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try{withTimeout(20000){block(owner)}}finally{owner.cancel();owner.coroutineContext[Job]!!.join()}
    }
    @Test fun leavingObserverDoesNotCancelRealCommitAndSuccessWaitsForResultDelivery()=runCase {owner->
        val (box,ticket)=fixture();val uuid=UUID.randomUUID().toString()
        val committed=CompletableDeferred<Unit>();val deliver=CompletableDeferred<Unit>();val calls=AtomicInteger()
        val task=NonBlockingWritePrototype(owner,Dispatchers.IO,key(box,uuid),action={
            calls.incrementAndGet()
            val result=repo.recordMeasuredUse(ticket,uuid,draft(box),at)
            committed.complete(Unit);deliver.await();result
        },resolver={NonBlockingWritePrototype.Resolution.Unknown})
        val observer=launch{task.state.collect{}}
        task.start();task.start();observer.cancelAndJoin();committed.await()
        assertOneCommit(box)
        assertEquals(WriteStatus.PENDING,task.state.value.status)
        assertFalse(task.cancelBeforeStart());assertEquals(1,calls.get())
        deliver.complete(Unit)
        assertEquals(uuid,task.state.first{it.status==WriteStatus.SUCCESS}.result!!.receipt.operationUuid)
        assertOneCommit(box)
    }
    @Test fun everyRepositoryFaultRollsBackButOwnerNeverGuessesNoCommitOrRetries()=runCase {owner->
        val (box,ticket)=fixture();val before=RawData.dump(raw).toString()
        for(stage in ActualUseCheckpoint.entries) {
            val uuid=UUID.randomUUID().toString();val calls=AtomicInteger();val reached=CompletableDeferred<Unit>()
            val task=NonBlockingWritePrototype(owner,Dispatchers.IO,key(box,uuid),action={
                calls.incrementAndGet()
                repo.recordMeasuredUse(ticket,uuid,draft(box),at){if(it==stage){reached.complete(Unit);throw IllegalStateException("SYNTHETIC transaction fault")}}
            },resolver={NonBlockingWritePrototype.Resolution.Unknown})
            task.start();reached.await();task.state.first{it.phase==WritePhase.RECONCILING}
            while(!task.reconcile(task.key))yield()
            task.state.first{it.phase==WritePhase.MANUAL_CHECK}
            assertEquals(WriteStatus.PENDING,task.state.value.status)
            task.start();assertEquals(1,calls.get())
            assertEquals(before,RawData.dump(raw).toString())
            assertEquals(ticket,repo.measuredWriteTicket(box.scope,ticket.medicationId))
        }
    }
    @Test fun lostDeliveryAfterCommitRemainsUncertainAndDoesNotConsumeAgain()=runCase {owner->
        val (box,ticket)=fixture();val uuid=UUID.randomUUID().toString();val calls=AtomicInteger()
        val task=NonBlockingWritePrototype<RecordedMeasuredUse>(owner,Dispatchers.IO,key(box,uuid),action={
            calls.incrementAndGet();repo.recordMeasuredUse(ticket,uuid,draft(box),at)
            throw IllegalStateException("SYNTHETIC lost committed response")
        },resolver={NonBlockingWritePrototype.Resolution.Unknown})
        task.start();task.state.first{it.phase==WritePhase.RECONCILING}
        while(!task.reconcile(task.key))yield()
        task.state.first{it.phase==WritePhase.MANUAL_CHECK};task.start()
        assertNull(task.state.value.result);assertEquals(1,calls.get());assertOneCommit(box)
    }
    @Test fun ownerCancellationInCommittedDeliveryWindowCannotClaimRollback()=runCase {owner->
        val (box,ticket)=fixture();val uuid=UUID.randomUUID().toString();val committed=CompletableDeferred<Unit>()
        val task=NonBlockingWritePrototype<RecordedMeasuredUse>(owner,Dispatchers.IO,key(box,uuid),action={
            repo.recordMeasuredUse(ticket,uuid,draft(box),at);committed.complete(Unit);awaitCancellation()
        },resolver={NonBlockingWritePrototype.Resolution.Unknown})
        task.start();committed.await();owner.cancel()
        task.state.first{it.phase==WritePhase.RECONCILING}
        assertEquals(WriteStatus.PENDING,task.state.value.status);assertNull(task.state.value.result)
        assertOneCommit(box)
    }
}
