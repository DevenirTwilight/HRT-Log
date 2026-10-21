package net.plainnotes.app.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.measurement.*
import net.plainnotes.app.domain.stock.StockCapacityBasis
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Additional synthetic lifecycle measurements, never a replacement for the frozen large fixture.
 * Growth varies legacy record rows only; measured-operation/container history is deliberately fixed.
 * Same-process reopen is not an application/process restart. No latency threshold is relaxed here. */
abstract class P4LifecycleGrowthContract {
    protected abstract fun factory(key:ByteArray):SupportSQLiteOpenHelper.Factory
    protected open fun encrypted(context:Context,name:String) {}
    @Test fun firstReopenConsecutiveRollbackRestoreAndRecordRowGrowth()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        for(history in listOf(0,365,3650)) {
            val name="synthetic-p4-lifecycle-${UUID.randomUUID()}.db"
            val key=ByteArray(32){(it+53).toByte()}
            var database:NotesDatabase?=null
            fun open():NotesDatabase = Room.databaseBuilder(context,NotesDatabase::class.java,name)
                .openHelperFactory(factory(key.clone())).allowMainThreadQueries().addCallback(SchemaGuards).build()
                .also{it.openHelper.writableDatabase;encrypted(context,name)}
            val started=System.nanoTime()
            MeasuredOperationChain.clearPureInputMemo()
            try {
                database=open()
                val opened=System.nanoTime()
                val repository=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=requireNotNull(database)})
                val at=Instant.parse("2026-10-21T12:00:00Z")
                val meaning=Meaning(QuantityKind.USE_COUNT,"SYNTHETIC lifecycle tablets")
                val medication=requireNotNull(database).dao().insertMedication(MedicationEntity(name="SYNTHETIC lifecycle",molecule="OTHER",unit="MG",dose_per_intake=0.1,container_capacity=28.0,site_rotation=false,notifications_on=false,active=false,sort_order=0))
                val box=requireNotNull(database).withTransaction {
                    val sql=requireNotNull(database).openHelper.writableDatabase
                    MeasuredContainerStore.create(requireNotNull(database),ReferenceStore.previewEpoch(sql),medication,UUID.randomUUID().toString(),
                        PackageMeasurementDraft(UserReferenceProduct("SYNTHETIC lifecycle product",sourceVersion="synthetic-v1",recordedOn="2026-10-21"),MeasureUnit.TABLET,meaning,"0.5","28",StockCapacityBasis.USER_COUNTED),at)
                }
                // Existing factual rows without P4 operations: a precise record-count dimension,
                // not evidence for growth in measured operations, containers or cross-source causality.
                requireNotNull(database).withTransaction {
                    repeat(history){index->requireNotNull(database).dao().record(RecordEntity(medication_id=medication,taken_utc=at.minusSeconds(index+1L).toEpochMilli(),taken_zone="UTC",actual_dose=0.1,status="ON_TIME",origin="APP",revision=1,config_snapshot="{}"))}
                }
                val prepared=System.nanoTime()
                val draft=MeasuredActualUseDraft(at.toEpochMilli(),"UTC",0.1,
                    listOf(ConfirmedStockUse(box.containerUuid,box.presentationEventUuid,box.presentation.sha256,Quantity(BigDecimal("0.5"),MeasureUnit.TABLET,meaning),true)),
                    MeasuredStockEffect.DEDUCT_CURRENT,userConfirmed=true)
                var scope=box.scope
                var successful=0L
                suspend fun verify() {
                    val db=requireNotNull(database)
                    db.withTransaction {
                        val sql=db.openHelper.writableDatabase
                        val projection=MeasuredConsumptionStore.auditStagedDataset(sql).single()
                        assertEquals(successful,projection.usedQuanta)
                        assertTrue(projection.cacheMatches)
                        sql.query("SELECT COUNT(*) FROM dose_record").use{assertTrue(it.moveToFirst());assertEquals(history+successful,it.getLong(0))}
                        sql.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
                    }
                }
                fun row(path:String)=JSONObject().put("scope","SYNTHETIC_P4_LIFECYCLE_RECORD_ROW_GROWTH")
                    .put("path",path).put("history_record_rows",history).put("fixed_containers",1)
                    .put("measured_operations_before",successful).put("pid",android.os.Process.myPid())
                    .put("clock","System.nanoTime monotonic wall").put("large_acceptance_fixture","NOT_RUN_THIS_TEST")
                    .put("process_restart","NOT_RUN_SAME_PROCESS_ONLY").put("api",android.os.Build.VERSION.SDK_INT).put("runtime_max_heap_bytes",Runtime.getRuntime().maxMemory()).put("cross_transaction_trust","NONE")
                fun emit(value:JSONObject){System.out.println("P4_LIFECYCLE_JSON "+value.toString())}
                suspend fun write(path:String,wholeStart:Long=System.nanoTime(),extra:JSONObject=JSONObject()) {
                    val begin=System.nanoTime()
                    val ticket=repository.measuredWriteTicket(scope,medication)
                    val ticketEnd=System.nanoTime()
                    val saved=repository.recordMeasuredUse(ticket,UUID.randomUUID().toString(),draft,at)
                    val end=System.nanoTime()
                    successful++
                    assertEquals(1,saved.receipt.lines.size)
                    val checkStart=System.nanoTime();verify();val checkEnd=System.nanoTime()
                    val result=row(path).put("measured_operations_before",successful-1)
                        .put("ticket_ms",(ticketEnd-begin)/1e6).put("write_including_final_full_audit_ms",(end-ticketEnd)/1e6)
                        .put("ticket_plus_write_ms",(end-begin)/1e6).put("whole_scenario_ms",(end-wholeStart)/1e6)
                        .put("independent_post_commit_full_audit_ms",(checkEnd-checkStart)/1e6)
                        .put("correctness","PASS_FULL_ORACLE_AND_PROJECTION")
                    for(keyName in extra.keys())result.put(keyName,extra.get(keyName))
                    emit(result)
                }
                write("new_database_first_measured_write",started,JSONObject().put("new_database_open_ms",(opened-started)/1e6)
                    .put("synthetic_fixture_provisioning_ms",(prepared-opened)/1e6).put("fixture_provisioning_in_whole_scenario",true))
                val reopenStart=System.nanoTime();requireNotNull(database).close();MeasuredOperationChain.clearPureInputMemo();val closeEnd=System.nanoTime()
                database=open();val reopenEnd=System.nanoTime()
                write("same_process_close_reopen_first_write",reopenStart,JSONObject().put("close_ms",(closeEnd-reopenStart)/1e6)
                    .put("open_ms",(reopenEnd-closeEnd)/1e6))
                repeat(3){index->write("consecutive_write_${index+1}")}
                val raw=requireNotNull(database).openHelper.writableDatabase
                val before=RawData.dump(raw).toString()
                val failureStart=System.nanoTime();var reached=false
                try {
                    val ticket=repository.measuredWriteTicket(scope,medication)
                    repository.recordMeasuredUse(ticket,UUID.randomUUID().toString(),draft,at){point->
                        if(point==ActualUseCheckpoint.STOCK_CAPTURED){reached=true;throw IllegalStateException("SYNTHETIC lifecycle rollback")}
                    }
                    fail("Injected transaction fault did not escape")
                }catch(error:IllegalStateException){assertEquals("SYNTHETIC lifecycle rollback",error.message)}
                val failureEnd=System.nanoTime();assertTrue(reached)
                assertEquals(before,RawData.dump(raw).toString())
                emit(row("exception_rollback").put("ticket_plus_failed_write_ms",(failureEnd-failureStart)/1e6)
                    .put("correctness","PASS_EXACT_DATASET_ROLLBACK").put("invalidation","NO_BASELINE_OR_TRUST_CACHE"))
                write("first_write_after_exception_rollback")
                if(history==0) {
                    val password="SYNTHETIC small lifecycle backup".toCharArray()
                    var archive:ByteArray?=null
                    try {
                        val epoch=repository.referencePreviewEpoch()
                        val exportStart=System.nanoTime();archive=repository.exportMeasuredBackup(password,epoch);val exportEnd=System.nanoTime()
                        val restoreStart=System.nanoTime();repository.restoreMeasuredBackup(requireNotNull(archive),password,epoch);val restoreEnd=System.nanoTime()
                        scope=repository.referenceScope() // Included in whole scenario; no extra audit before the write.
                        assertNotEquals(epoch,scope.epoch)
                        write("small_restore_then_first_write_without_extra_pre_audit",restoreStart,
                            JSONObject().put("restore_ms",(restoreEnd-restoreStart)/1e6).put("prior_export_ms",(exportEnd-exportStart)/1e6)
                                .put("restore_mandatory_full_audits","INCLUDED_IN_RESTORE").put("extra_pre_write_audit",false))
                    }finally{archive?.fill(0);password.fill('\u0000')}
                }
            }finally{database?.close();MeasuredOperationChain.clearPureInputMemo();key.fill(0);context.deleteDatabase(name)}
        }
    }
}
