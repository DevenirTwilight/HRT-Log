package net.plainnotes.app.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import net.plainnotes.app.domain.measurement.*
import net.plainnotes.app.domain.stock.StockCapacityBasis
import net.plainnotes.app.domain.write.WriteStatus
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.UUID

/** Host-controlled synthetic SQLCipher process death, not a runtime queue or recovery token. */
class MeasuredSaveDeathProbe {
    private val args get()=InstrumentationRegistry.getArguments()
    private val context get()=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val name="synthetic-measured-save-death.db"
    private val marker get()=File(context.filesDir,"synthetic-write-death-ready.json")
    private val trace get()=File(context.filesDir,"synthetic-write-death-stages.jsonl")
    private val traceLock=Any()
    private fun traceStage(point:String,failure:Throwable?=null)=synchronized(traceLock) {
        // Synthetic checkpoint diagnostics only: no request, dose, SQL text or exception message.
        val row=JSONObject().put("phase",point).put("pid",android.os.Process.myPid())
            .put("elapsed_ms",android.os.SystemClock.elapsedRealtime())
        failure?.let {
            row.put("exception_class",it.javaClass.name)
            if(it is ReferenceFailure)row.put("reference_code",it.code.name)
            row.put("stack_locations",org.json.JSONArray(it.stackTrace.take(12).map{frame->"${frame.className}.${frame.methodName}:${frame.lineNumber}"}))
        }
        trace.appendText(row.toString()+"\n")
    }
    private fun open():NotesDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context,NotesDatabase::class.java,name)
            .openHelperFactory(net.zetetic.database.sqlcipher.SupportOpenHelperFactory(ByteArray(32){(it+21).toByte()}))
            .allowMainThreadQueries().addCallback(SchemaGuards).build()
    }
    @Test fun prepareAndWaitForExternalKill():Unit=runBlocking {
        assumeTrue(args.getString("ansm_write_death_action")=="prepare")
        val stage=requireNotNull(args.getString("ansm_write_death_stage"))
        require(stage in setOf("STOCK_CAPTURED","AFTER_COMMIT"))
        marker.delete();trace.delete();traceStage("FIXTURE_START");context.deleteDatabase(name)
        val db=open();val process=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            val repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db},Space.PRIMARY)
            val m=db.dao().insertMedication(MedicationEntity(name="SYNTHETIC save crash",molecule="OTHER",unit="MG",dose_per_intake=0.1,
                container_capacity=28.0,site_rotation=false,notifications_on=false,active=false,sort_order=0))
            val at=Instant.parse("2026-10-21T12:00:00Z");val meaning=Meaning(QuantityKind.USE_COUNT,"SYNTHETIC crash tablet")
            val box=repo.createMeasuredPackage(repo.referencePreviewEpoch(),db.dao().medication(m),UUID.randomUUID().toString(),
                PackageMeasurementDraft(UserReferenceProduct("SYNTHETIC crash product",sourceVersion="synthetic-v1",recordedOn="2026-10-21"),
                    MeasureUnit.TABLET,meaning,"0.5","28",StockCapacityBasis.USER_COUNTED),at)
            traceStage("PACKAGE_CREATED")
            val raw=db.openHelper.writableDatabase;val before=ReferencePayload.sha(RawData.dump(raw).toString())
            lateinit var owner:MeasuredSaveCoordinator
            fun waitForKill(expected:String) {
                assertEquals(WriteStatus.PENDING,owner.latestTask.value!!.progress.value.status)
                marker.writeText(JSONObject().put("stage",stage).put("pid",android.os.Process.myPid()).put("expected_sha256",expected)
                    .put("epoch",box.scope.epoch).put("expected_records",if(stage=="STOCK_CAPTURED")0 else 1).toString())
                traceStage("READY_FOR_KILL")
                while(true)Thread.sleep(1000)
            }
            owner=MeasuredSaveCoordinator(repo,process,Dispatchers.IO,writeOverride={expected->
                traceStage("WRITER_ENTERED")
                try {
                val result=repo.recordMeasuredUse(expected.ticket,expected.operationUuid,expected.draft,expected.created){point->
                    traceStage(point.name)
                    if(stage=="STOCK_CAPTURED" && point==ActualUseCheckpoint.STOCK_CAPTURED)waitForKill(before)
                }
                traceStage("ROOT_REPOSITORY_RETURNED")
                if(stage=="AFTER_COMMIT") {
                    traceStage("COMMITTED_DUMP_START")
                    val digest=ReferencePayload.sha(RawData.dump(raw).toString())
                    traceStage("COMMITTED_DUMP_COMPLETE");waitForKill(digest)
                }
                result
                }catch(failure:Throwable) {
                    try{traceStage("WRITER_FAILED",failure)}catch(_:Throwable){}
                    throw failure
                }
            })
            val input=MeasuredActualUseDraft(at.toEpochMilli(),"UTC",0.1,listOf(ConfirmedStockUse(box.containerUuid,box.presentationEventUuid,
                box.presentation.sha256,(box.presentation.stockScale() as MeasureResult.Success).value.quantum,true)),MeasuredStockEffect.DEDUCT_CURRENT,userConfirmed=true)
            val prepared=owner.prepare(box.scope,m,UUID.randomUUID().toString(),input,at)
            traceStage("PREPARED");owner.start(prepared)
            while(true)delay(1000) // Host must kill this process, including its real writer thread.
        }finally{process.cancel();db.close()}
    }
    @Test fun verifyFreshProcessHasExactDatasetAndNoAutomaticReplay():Unit=runBlocking {
        assumeTrue(args.getString("ansm_write_death_action")=="verify")
        val saved=JSONObject(marker.readText());assertNotEquals(saved.getInt("pid"),android.os.Process.myPid())
        val db=open()
        try {
            val raw=db.openHelper.writableDatabase
            val repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db},Space.PRIMARY)
            assertNull(MeasuredSaveCoordinator(repo).latestTask.value)
            db.withTransaction {
                assertEquals(saved.getString("expected_sha256"),ReferencePayload.sha(RawData.dump(raw).toString()))
                assertEquals(saved.getString("epoch"),ReferenceStore.previewEpoch(raw))
                assertEquals(saved.getInt("expected_records"),db.dao().records().size)
                raw.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
                raw.query("PRAGMA integrity_check").use{assertTrue(it.moveToFirst());assertEquals("ok",it.getString(0))}
                val projection=MeasuredConsumptionStore.auditStagedDataset(raw)
                assertEquals(FrozenFullAuditStore.auditStagedDataset(raw),projection)
                assertEquals(saved.getInt("expected_records").toLong(),projection.single().usedQuanta)
            }
            val header=context.getDatabasePath(name).inputStream().use{input->ByteArray(16).also{java.io.DataInputStream(input).readFully(it)}}
            assertFalse(String(header,Charsets.US_ASCII).startsWith("SQLite format 3"))
            saved.put("new_pid",android.os.Process.myPid()).put("status","PASS_EXACT_DATASET_FULL_ORACLE_NO_REPLAY")
            File(context.filesDir,"synthetic-write-death-result.json").writeText(saved.toString())
        }finally{db.close()}
        context.deleteDatabase(name);marker.delete()
    }
}
