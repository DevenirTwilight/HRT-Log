package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.plainnotes.app.domain.measurement.*
import net.plainnotes.app.domain.stock.StockCapacityBasis
import net.plainnotes.app.domain.write.*
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/** Runtime owner, real Room/Repository/full audit. Small synthetic framework SQLite contracts;
 * no native/LMK, durable token, UI activation or latency acceptance claim. */
abstract class MeasuredSaveCoordinatorContract {
    private lateinit var primary:NotesDatabase
    private lateinit var decoy:NotesDatabase
    private lateinit var access:DatabaseAccess
    private lateinit var repo:NotesRepository
    private val at=Instant.parse("2026-10-21T12:00:00Z")
    private val meaning=Meaning(QuantityKind.USE_COUNT,"SYNTHETIC runtime tablets")
    private val raw get()=primary.openHelper.writableDatabase
    protected open fun factory():androidx.sqlite.db.SupportSQLiteOpenHelper.Factory?=null
    protected open fun verifyDatabase(database:NotesDatabase) {}
    @Before fun open() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        fun database()=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards)
            .apply{factory()?.let{openHelperFactory(it)}}.build().also{verifyDatabase(it)}
        primary=database();decoy=database()
        access=object:DatabaseAccess(context){override fun get(space:Space)=if(space==Space.PRIMARY)primary else decoy}
        repo=NotesRepository(access)
    }
    @After fun close(){primary.close();decoy.close();MeasuredOperationChain.clearPureInputMemo()}
    private suspend fun fixture():CreatedMeasuredContainer {
        val medication=primary.dao().insertMedication(MedicationEntity(name="SYNTHETIC runtime",molecule="OTHER",unit="MG",dose_per_intake=0.1,
            container_capacity=28.0,site_rotation=false,notifications_on=false,active=false,sort_order=0))
        return primary.withTransaction{MeasuredContainerStore.create(primary,ReferenceStore.previewEpoch(raw),medication,UUID.randomUUID().toString(),
            PackageMeasurementDraft(UserReferenceProduct("SYNTHETIC tablets",sourceVersion="synthetic-v1",recordedOn="2026-10-21"),
                MeasureUnit.TABLET,meaning,"0.5","28",StockCapacityBasis.USER_COUNTED),at)}
    }
    private fun draft(box:CreatedMeasuredContainer)=MeasuredActualUseDraft(at.toEpochMilli(),"UTC",0.125,
        listOf(ConfirmedStockUse(box.containerUuid,box.presentationEventUuid,box.presentation.sha256,Quantity(BigDecimal("0.5"),MeasureUnit.TABLET,meaning),true)),
        MeasuredStockEffect.DEDUCT_CURRENT,userConfirmed=true)
    private suspend fun prepare(owner:MeasuredSaveCoordinator,box:CreatedMeasuredContainer,uuid:String=UUID.randomUUID().toString(),input:MeasuredActualUseDraft=draft(box))=
        owner.prepare(box.scope,box.container.medication_id,uuid,input,at)
    private suspend fun write(expected:PreparedMeasuredCreate)=repo.pinnedTo(Space.PRIMARY).recordMeasuredUse(expected.ticket,expected.operationUuid,expected.draft,expected.created)
    private fun runCase(block:suspend CoroutineScope.(CoroutineScope)->Unit)=runBlocking {
        val process=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try{withTimeout(30000){block(process)}}finally{process.cancel();process.coroutineContext[Job]!!.join()}
    }
    private suspend fun oneCommit(box:CreatedMeasuredContainer) {
        assertEquals(1,primary.dao().records().size);assertTrue(decoy.dao().records().isEmpty())
        primary.withTransaction {
            val checked=MeasuredConsumptionStore.auditStagedDataset(raw)
            assertEquals(FrozenFullAuditStore.auditStagedDataset(raw),checked)
            assertEquals(1L,checked.single{it.containerUuid==box.containerUuid}.usedQuanta)
        }
    }
    private suspend fun busy(block:suspend()->Unit) {
        try{block();fail("Conflicting mutation entered") }catch(_:MeasuredSaveBusyException){}
    }
    @Test fun pageCancellationAndRepeatedStartDoNotDuplicateOrPublishBeforeCommitDelivery()=runCase {process->
        val box=fixture();val committed=CompletableDeferred<Unit>();val delivery=CompletableDeferred<Unit>();val calls=AtomicInteger()
        val owner=MeasuredSaveCoordinator(repo,process,Dispatchers.IO,writeOverride={expected->
            calls.incrementAndGet();val result=write(expected);committed.complete(Unit);delivery.await();result
        })
        val expected=prepare(owner,box);val task=owner.start(expected)
        val page=launch{task.progress.first{it.status==WriteStatus.SUCCESS}}
        committed.await();page.cancelAndJoin()
        assertEquals(WriteStatus.PENDING,task.progress.value.status);assertNull(task.confirmed.value)
        assertSame(task,owner.start(expected));assertFalse(owner.cancelBeforeStart(task));assertFalse(owner.reconcile(task))
        delivery.complete(Unit);task.progress.first{it.status==WriteStatus.SUCCESS}
        assertEquals(1,calls.get());assertEquals(expected.operationUuid,task.confirmed.value!!.operationUuid)
        assertSame(task,owner.start(expected));oneCommit(box)
        repo.transaction{it.note(DayNoteEntity("2026-10-21","SYNTHETIC after commit"))}
    }
    @Test fun lostCommitResponseIsObservedOnlyAfterWriterCompletionWithoutReplay()=runCase {process->
        val box=fixture();val committed=CompletableDeferred<Unit>();val lose=CompletableDeferred<Unit>();val writes=AtomicInteger();val reads=AtomicInteger()
        lateinit var task:ActualSaveTask
        val owner=MeasuredSaveCoordinator(repo,process,Dispatchers.IO,writeOverride={expected->
            writes.incrementAndGet();write(expected);committed.complete(Unit);lose.await();throw IllegalStateException("SYNTHETIC lost delivery")
        },observeOverride={expected->
            assertTrue(task.writer!!.isCompleted);reads.incrementAndGet();repo.observeMeasuredActualUse(expected)
        })
        task=owner.start(prepare(owner,box));committed.await();assertEquals(WriteStatus.PENDING,task.progress.value.status)
        lose.complete(Unit);task.progress.first{it.status==WriteStatus.SUCCESS}
        assertEquals(1,writes.get());assertEquals(1,reads.get());assertFalse(owner.reconcile(task));oneCommit(box)
    }
    @Test fun realRollbackAndAbsentObservationStayManualAndKeepConflictsBlocked()=runCase {process->
        val box=fixture();val before=RawData.dump(raw).toString();val calls=AtomicInteger()
        val owner=MeasuredSaveCoordinator(repo,process,Dispatchers.IO,writeOverride={expected->
            calls.incrementAndGet();repo.recordMeasuredUse(expected.ticket,expected.operationUuid,expected.draft,expected.created){point->
                if(point==ActualUseCheckpoint.STOCK_CAPTURED)throw IllegalStateException("SYNTHETIC rollback")
            }
        })
        val expected=prepare(owner,box);val task=owner.start(expected)
        task.progress.first{it.phase==WritePhase.MANUAL_CHECK}
        assertEquals(before,RawData.dump(raw).toString());assertEquals(WriteStatus.PENDING,task.progress.value.status)
        assertFalse(task.progress.value.retryAllowed);assertSame(task,owner.start(expected));assertEquals(1,calls.get())
        busy{repo.setContainerInfo(box.container.id,"SYNTHETIC forbidden",null)}
    }
    @Test fun conflictGateCoversLegacyMeasuredRestoreImportAndDestructionButAllowsReadsAndDecoy()=runCase {process->
        val box=fixture();val ticket=repo.measuredWriteTicket(box.scope,box.container.medication_id)
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val owner=MeasuredSaveCoordinator(repo,process,Dispatchers.IO,writeOverride={expected->entered.complete(Unit);release.await();write(expected)})
        val task=owner.start(prepare(owner,box));entered.await()
        busy{repo.transaction{error("Generic callback entered")}}
        busy{repo.setContainerInfo(box.container.id,"SYNTHETIC forbidden",null)}
        busy{repo.unscheduled(box.container.medication_id,at,0.125)}
        busy{repo.associateMedication(box.scope,box.container.medication_id,0,UUID.randomUUID().toString(),null)}
        busy{repo.countMeasuredStock(ticket,UUID.randomUUID().toString(),ConfirmedStockCount(box.containerUuid,box.presentationEventUuid,
            box.presentation.sha256,Quantity(BigDecimal("1"),MeasureUnit.TABLET,meaning),true),at)}
        busy{repo.restoreBackup(byteArrayOf(1),"SYNTHETIC".toCharArray())}
        busy{repo.exportBackup("SYNTHETIC".toCharArray())} // Export may seed regimen history.
        val emptyPlan=net.plainnotes.app.importer.TransMemo.Plan(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyMap(),emptyList(),emptyMap())
        busy{repo.importTransMemo(emptyPlan,false)}
        busy{access.close(Space.PRIMARY)};busy{access.destroy(Space.PRIMARY)};busy{repo.destroyAll()}
        assertEquals(box.scope.epoch,repo.referencePreviewEpoch());assertEquals(1,repo.medications().size)
        repo.pinnedTo(Space.DECOY).transaction{it.note(DayNoteEntity("2026-10-21","SYNTHETIC decoy"))}
        assertEquals(1,decoy.dao().notes("2026-10-21","2026-10-21").size)
        release.complete(Unit);task.progress.first{it.status==WriteStatus.SUCCESS};oneCommit(box)
    }
    @Test fun originalPayloadConflictAndForeignOwnerAreRejectedWhileSameConfirmationReusesTask()=runCase {process->
        val box=fixture();val release=CompletableDeferred<Unit>();val entered=CompletableDeferred<Unit>()
        val owner=MeasuredSaveCoordinator(repo,process,Dispatchers.IO,writeOverride={expected->entered.complete(Unit);release.await();write(expected)})
        val uuid=UUID.randomUUID().toString();val original=prepare(owner,box,uuid)
        val changed=prepare(owner,box,uuid,draft(box).copy(clinicalDose=0.25))
        val task=owner.start(original);entered.await();assertSame(task,owner.start(original))
        try{owner.start(changed);fail("Changed request reused UUID")}catch(e:ReferenceFailure){assertEquals(ReferenceError.OPERATION_CONFLICT,e.code)}
        val other=MeasuredSaveCoordinator(repo,process,Dispatchers.IO)
        try{other.start(original);fail("Foreign owner accepted confirmation")}catch(_:IllegalArgumentException){}
        release.complete(Unit);task.progress.first{it.status==WriteStatus.SUCCESS};oneCommit(box)
    }
    @Test fun alreadyRunningLegacyTransactionRejectsReservationAndExceptionReleasesItsCount()=runCase {process->
        val box=fixture();val owner=MeasuredSaveCoordinator(repo,process,Dispatchers.IO);val expected=prepare(owner,box)
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val legacy=async {runCatching{repo.transaction{entered.complete(Unit);release.await();throw IllegalStateException("SYNTHETIC old mutation")}}}
        entered.await();busy{owner.start(expected)};release.complete(Unit);assertTrue(legacy.await().isFailure)
        val task=owner.start(expected);task.progress.first{it.status==WriteStatus.SUCCESS};oneCommit(box)
    }
    @Test fun selectedDecoyCannotRedirectAcceptedPrimaryWrite()=runCase {process->
        val box=fixture();val release=CompletableDeferred<Unit>();val entered=CompletableDeferred<Unit>()
        val owner=MeasuredSaveCoordinator(repo,process,Dispatchers.IO,writeOverride={expected->entered.complete(Unit);release.await();write(expected)})
        val task=owner.start(prepare(owner,box));entered.await();repo.select(Space.DECOY)
        release.complete(Unit);task.progress.first{it.status==WriteStatus.SUCCESS};oneCommit(box)
    }
    @Test fun existingUuidWithDifferentOriginalCreationTimeCannotBecomeRuntimeSuccess()=runCase {process->
        val box=fixture();val owner=MeasuredSaveCoordinator(repo,process,Dispatchers.IO)
        val expected=prepare(owner,box);val frozen=expected.value
        repo.recordMeasuredUse(frozen.ticket,frozen.operationUuid,frozen.draft,frozen.created.plusSeconds(1))
        val before=RawData.dump(raw).toString();val task=owner.start(expected)
        task.progress.first{it.phase==WritePhase.MANUAL_CHECK || it.status!=WriteStatus.PENDING}
        assertEquals(WriteStatus.PENDING,task.progress.value.status)
        assertEquals(WritePhase.MANUAL_CHECK,task.progress.value.phase);assertNull(task.confirmed.value)
        assertEquals(before,RawData.dump(raw).toString());oneCommit(box)
    }
    @Test fun existingUuidCannotBypassCompleteAuditOfUnrelatedCorruption()=runCase {process->
        val box=fixture();val owner=MeasuredSaveCoordinator(repo,process,Dispatchers.IO)
        val expected=prepare(owner,box);write(expected.value)
        val other=fixture();val otherExpected=prepare(owner,other);val otherResult=write(otherExpected.value)
        raw.execSQL("UPDATE dose_record SET actual_dose=7 WHERE id=?",arrayOf(otherResult.record.id))
        val before=RawData.dump(raw).toString()
        primary.withTransaction{assertTrue(runCatching{FrozenFullAuditStore.auditStagedDataset(raw)}.isFailure)}
        val task=owner.start(expected)
        task.progress.first{it.phase==WritePhase.MANUAL_CHECK || it.status!=WriteStatus.PENDING}
        assertEquals(WriteStatus.PENDING,task.progress.value.status)
        assertEquals(WritePhase.MANUAL_CHECK,task.progress.value.phase);assertNull(task.confirmed.value)
        assertEquals(before,RawData.dump(raw).toString())
    }
    @Test fun existingExactOriginalRequestCanConfirmWithoutAnotherDeduction()=runCase {process->
        val box=fixture();val owner=MeasuredSaveCoordinator(repo,process,Dispatchers.IO)
        val expected=prepare(owner,box);write(expected.value)
        val before=RawData.dump(raw).toString();val task=owner.start(expected)
        task.progress.first{it.status==WriteStatus.SUCCESS}
        assertEquals(before,RawData.dump(raw).toString());oneCommit(box)
    }
    @Test fun legacySqlHelperCannotRetargetADecoyTransactionIntoReservedPrimary()=runCase {process->
        val box=fixture();val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val owner=MeasuredSaveCoordinator(repo,process,Dispatchers.IO,writeOverride={expected->entered.complete(Unit);release.await();write(expected)})
        val task=owner.start(prepare(owner,box));entered.await();repo.select(Space.DECOY)
        repo.transaction { dao ->
            repo.select(Space.PRIMARY) // Deterministic UI selection switch inside the suspended legacy transaction.
            val helper=NotesRepository::class.java.getDeclaredMethod("sql").apply{isAccessible=true}
            val selected=helper.invoke(repo) as androidx.sqlite.db.SupportSQLiteDatabase
            selected.execSQL("INSERT INTO day_note(date,text) VALUES (?,?)",arrayOf("2026-10-21","SYNTHETIC scoped SQL"))
            assertTrue("Legacy helper escaped its reserved transaction target",primary.dao().notes("2026-10-21","2026-10-21").isEmpty())
            assertEquals(1,dao.notes("2026-10-21","2026-10-21").size)
        }
        release.complete(Unit);task.progress.first{it.status==WriteStatus.SUCCESS};oneCommit(box)
    }
    @Test fun processObservationAndTypedSnapshotSurvivePageDetachWithoutMaintenanceWrites()=runCase {process->
        val box=fixture();val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val owner=MeasuredSaveCoordinator(repo,process,Dispatchers.IO,writeOverride={expected->entered.complete(Unit);release.await();write(expected)})
        assertNull(owner.latestTask.value)
        val task=owner.start(prepare(owner,box));entered.await();assertSame(task,owner.latestTask.value)
        val page=launch{owner.latestTask.first{it===task}};page.cancelAndJoin()
        val before=RawData.dump(raw).toString()
        val date=at.atZone(java.time.ZoneOffset.UTC).toLocalDate()
        val read=repo.readNotesSnapshot(at,java.time.ZoneOffset.UTC,date,date)
        val calculation=repo.readCalculationInputs(at,at.minusSeconds(3600),at.plusSeconds(86400),java.time.ZoneOffset.UTC)
        assertEquals(1,read.medications.size);assertEquals(1,read.containers.size)
        assertTrue(read.items.isEmpty());assertTrue(read.records.isEmpty());assertTrue(calculation.records.isEmpty())
        assertEquals(read.medications,calculation.medications);assertEquals(before,RawData.dump(raw).toString())
        assertSame(task,owner.latestTask.value);assertEquals(WriteStatus.PENDING,task.progress.value.status)
        release.complete(Unit);task.progress.first{it.status==WriteStatus.SUCCESS}
        assertSame(task,owner.latestTask.value);oneCommit(box)
    }
    private class PausedDispatcher:CoroutineDispatcher() {
        private val queue=ConcurrentLinkedQueue<Runnable>()
        override fun dispatch(context:CoroutineContext,block:Runnable){queue.add(block)}
        fun runAll(){while(true)(queue.poll() ?: return).run()}
    }
    @Test fun queuedCancellationIsTheOnlyImmediateFailedOutcomeAndNeverCallsRepository()=runCase {process->
        val box=fixture();val dispatcher=PausedDispatcher();val calls=AtomicInteger()
        val owner=MeasuredSaveCoordinator(repo,process,dispatcher,writeOverride={calls.incrementAndGet();write(it)})
        val expected=prepare(owner,box);val task=owner.start(expected)
        assertTrue(owner.cancelBeforeStart(task));dispatcher.runAll()
        assertEquals(WriteStatus.FAILED,task.progress.value.status);assertEquals(0,calls.get());assertTrue(primary.dao().records().isEmpty())
        repo.transaction{it.note(DayNoteEntity("2026-10-21","SYNTHETIC released"))}
    }
    @Test fun ownerDropsInheritedRoomContextAndCannotReportNestedCommitBeforeOuterRollback()=runCase {process->
        val box=fixture();lateinit var task:ActualSaveTask
        val attempted=CompletableDeferred<Unit>();val returned=CompletableDeferred<Unit>()
        class Rollback:RuntimeException()
        try {primary.withTransaction {
            val inherited=CoroutineScope(currentCoroutineContext()+process.coroutineContext[Job]!!)
            val owner=MeasuredSaveCoordinator(repo,inherited,Dispatchers.IO,writeOverride={expected->
                attempted.complete(Unit);val result=write(expected);returned.complete(Unit);result
            })
            task=owner.start(prepare(owner,box));attempted.await()
            assertNull(withTimeoutOrNull(250){returned.await()})
            assertEquals(WriteStatus.PENDING,task.progress.value.status)
            throw Rollback()
        }}catch(_:Rollback){}
        returned.await();task.progress.first{it.status==WriteStatus.SUCCESS};oneCommit(box)
    }
}
