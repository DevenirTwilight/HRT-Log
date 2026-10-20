package net.plainnotes.app.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.measurement.*
import net.plainnotes.app.domain.stock.StockCapacityBasis
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.UUID

/** Manual synthetic API26 process-death contract. Host must kill the waiting transaction. */
class MeasuredRestoreDeathProbe {
    private val args get()=InstrumentationRegistry.getArguments()
    private val context get()=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val name="synthetic-measured-restore-death.db"
    private val marker get()=File(context.filesDir,"synthetic-restore-death-ready.json")
    private fun open():NotesDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context,NotesDatabase::class.java,name)
            .openHelperFactory(net.zetetic.database.sqlcipher.SupportOpenHelperFactory(ByteArray(32){(it+13).toByte()}))
            .allowMainThreadQueries().addCallback(SchemaGuards).build()
    }
    @Test fun prepareAndWaitForExternalKill()=runBlocking {
        assumeTrue(args.getString("ansm_restore_death_action")=="prepare")
        val stage=requireNotNull(args.getString("ansm_restore_death_stage"))
        require(stage in setOf("ROWS_DELETED","ROWS_INSERTED"))
        marker.delete();context.deleteDatabase(name)
        open().use {db->
            val repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db},Space.PRIMARY)
            val med=MedicationEntity(name="SYNTHETIC crash source",molecule="OTHER",unit="MG",dose_per_intake=0.1,container_capacity=28.0,site_rotation=false,notifications_on=false,active=false,sort_order=0)
            val m=db.dao().insertMedication(med);val at=Instant.parse("2026-10-20T12:00:00Z")
            val meaning=Meaning(QuantityKind.USE_COUNT,"SYNTHETIC crash tablet")
            val box=repo.createMeasuredPackage(repo.referencePreviewEpoch(),db.dao().medication(m),UUID.randomUUID().toString(),
                PackageMeasurementDraft(UserReferenceProduct("SYNTHETIC crash product",sourceVersion="synthetic-v1",recordedOn="2026-10-20"),MeasureUnit.TABLET,meaning,"0.5","28",StockCapacityBasis.USER_COUNTED),at)
            repo.recordMeasuredUse(repo.measuredWriteTicket(box.scope,m),UUID.randomUUID().toString(),
                MeasuredActualUseDraft(at.toEpochMilli(),"UTC",0.1,listOf(ConfirmedStockUse(box.containerUuid,box.presentationEventUuid,box.presentation.sha256,box.presentation.stockScale().let{(it as MeasureResult.Success).value.quantum},true)),MeasuredStockEffect.DEDUCT_CURRENT,userConfirmed=true),at)
            val password="SYNTHETIC crash password".toCharArray()
            val archive=repo.exportMeasuredBackup(password,repo.referencePreviewEpoch())
            db.dao().insertMedication(med.copy(name="SYNTHETIC target-only row"))
            val raw=db.openHelper.writableDatabase;val epoch=repo.referencePreviewEpoch()
            val expected=ReferencePayload.sha(RawData.dump(raw).toString())
            try {repo.restoreMeasuredBackup(archive,password,epoch,checkpoint={point->if(point.name==stage) {
                marker.writeText(JSONObject().put("stage",stage).put("pid",android.os.Process.myPid()).put("expected_sha256",expected).put("epoch",epoch).toString())
                while(true)Thread.sleep(1000)
            }})}finally{archive.fill(0);password.fill('\u0000')}
            fail("Host did not interrupt the pending restore")
        }
    }
    @Test fun verifyFreshProcessRollsBackExactDataset()=runBlocking {
        assumeTrue(args.getString("ansm_restore_death_action")=="verify")
        val saved=JSONObject(marker.readText());assertNotEquals(saved.getInt("pid"),android.os.Process.myPid())
        open().use {db->
            val raw=db.openHelper.writableDatabase
            assertEquals(saved.getString("expected_sha256"),ReferencePayload.sha(RawData.dump(raw).toString()))
            assertEquals(saved.getString("epoch"),ReferenceStore.previewEpoch(raw))
            raw.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
            db.withTransaction{val p=MeasuredConsumptionStore.auditStagedDataset(raw);assertEquals(1,p.size);assertEquals(1L,p.single().usedQuanta)}
            saved.put("new_pid",android.os.Process.myPid()).put("status","PASS_DATASET_EPOCH_AND_FULL_ORACLE")
            File(context.filesDir,"synthetic-restore-death-result.json").writeText(saved.toString())
        }
        context.deleteDatabase(name);marker.delete()
    }
}
