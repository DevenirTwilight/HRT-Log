package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class VoidedPackageTest {
    private lateinit var db:NotesDatabase
    private lateinit var repo:NotesRepository
    private val dao get()=db.dao()
    @Before fun open() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        db.openHelper.writableDatabase
        repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
    }
    @After fun close()=db.close()
    private fun med()=runBlocking {dao.insertMedication(MedicationEntity(name="Synthetic",molecule="E2",route="ORAL",unit="MG",dose_per_intake=2.0,container_capacity=30.0,soon_alert_minutes=0,late_after_minutes=60,site_rotation=false,notifications_on=true,active=true,sort_order=0))}
    private fun box(m:Long)=runBlocking{repo.addContainers(m,30.0,1,true);dao.containers().last().id}
    @Test fun unusedMistakeIsRetainedAsVoidedInsteadOfDeleted()=runBlocking {
        val m=med();val id=box(m)
        repo.removeMistakenContainer(id)
        assertEquals("VOIDED",dao.container(id).state)
        assertTrue(repo.containers().isEmpty())
        assertNull(StockForecasts.available(m,repo.containers()))
    }
    @Test fun adjustmentOnlyMistakeCanBeVoidedWithoutChangingLedgerOrOtherStock()=runBlocking {
        val m=med();val real=box(m);val fake=box(m)
        repo.correctStock(fake,2.0)
        val entries=dao.supplyForContainer(fake);val realBefore=dao.container(real)
        assertEquals(32.0,StockForecasts.available(m,repo.containers())!!,0.0)
        repo.removeMistakenContainer(fake)
        assertEquals("VOIDED",dao.container(fake).state)
        assertEquals(entries,dao.supplyForContainer(fake))
        assertEquals(realBefore,dao.container(real))
        assertEquals(30.0,StockForecasts.available(m,repo.containers())!!,0.0)
    }
    @Test fun multipleAdjustmentsAndRepeatedVoidAreIdempotent()=runBlocking {
        val m=med();val id=box(m)
        repo.correctStock(id,8.0);repo.correctStock(id,2.0)
        val ledger=dao.supplyForContainer(id)
        repo.removeMistakenContainer(id)
        val before=RawData.dump(db.openHelper.writableDatabase).toString()
        repo.removeMistakenContainer(id)
        assertEquals(before,RawData.dump(db.openHelper.writableDatabase).toString())
        assertEquals(2,ledger.size);assertEquals(ledger,dao.supplyForContainer(id))
        assertEquals("VOIDED",dao.container(id).state)
    }
    @Test fun forecastAndCountsTreatOnlyVoidedAsUntracked()=runBlocking {
        val m=med()
        repeat(3){val id=box(m);repo.correctStock(id,2.0);repo.removeMistakenContainer(id)}
        val hidden=dao.voidedContainers()
        assertEquals(3,hidden.size);assertTrue(dao.containers().isEmpty())
        assertNull(StockForecasts.available(m,hidden))
        assertEquals(StockForecast.Status.UNTRACKED,StockForecasts.forecast(m,hidden,emptyList()).status)
    }
    @Test fun voidedCannotBeAdjustedDiscardedModifiedOrRevivedByDirectStateUpdate()=runBlocking {
        val m=med();val id=box(m);repo.correctStock(id,2.0);repo.removeMistakenContainer(id)
        val before=RawData.dump(db.openHelper.writableDatabase).toString()
        for(action in listOf<suspend()->Unit>(
            {repo.correctStock(id,3.0)}, {repo.setRemaining(id,3.0)}, {repo.discardContainer(id)},
            {repo.setContainerInfo(id,"changed",null)}, {dao.setContainerState(id,"IN_USE",null)})) {
            assertThrows(Exception::class.java){runBlocking{action()}}
            assertEquals(before,RawData.dump(db.openHelper.writableDatabase).toString())
        }
    }
    @Test fun automaticAllocationAndReversalNeverUseOrReviveVoided()=runBlocking {
        val m=med();val fake=box(m);repo.correctStock(fake,2.0);repo.removeMistakenContainer(fake)
        val real=box(m)
        db.withTransaction {
            val id=dao.record(RecordEntity(medication_id=m,taken_utc=1000,taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot="{}"))
            SupplyLedger.allocate(dao,dao.recordById(id)!!)
            assertEquals(real,dao.supplyFor(id).single().container_id)
            SupplyLedger.reverse(dao,id)
            SupplyLedger.reverse(dao,id)
        }
        assertEquals("VOIDED",dao.container(fake).state)
        assertEquals(listOf("ADJUST"),dao.supplyForContainer(fake).map{it.kind})
        assertEquals(30.0,StockForecasts.available(m,repo.containers())!!,0.0)
    }
    @Test fun realConsumptionAndEvenReversedConsumptionCannotBeDirectlyVoided()=runBlocking {
        val m=med();val id=box(m)
        val record=dao.record(RecordEntity(medication_id=m,taken_utc=1000,taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot="{}"))
        db.withTransaction{SupplyLedger.allocate(dao,dao.recordById(record)!!)}
        assertThrows(PackageConsumptionConflict::class.java){runBlocking{repo.removeMistakenContainer(id)}}
        db.withTransaction{SupplyLedger.reverse(dao,record)}
        assertThrows(PackageConsumptionConflict::class.java){runBlocking{repo.removeMistakenContainer(id)}}
        assertEquals("IN_USE",dao.container(id).state);assertNotNull(dao.recordById(record))
    }
    @Test fun restoreOriginalStateAndAmountWithoutAdditionalSupplyEntry()=runBlocking {
        val m=med();val id=box(m);repo.correctStock(id,2.0)
        val original=dao.container(id);val entries=dao.supplyForContainer(id)
        repo.removeMistakenContainer(id);repo.restoreVoidedContainer(id)
        assertEquals(original,dao.container(id));assertEquals(entries,dao.supplyForContainer(id))
        assertEquals(2.0,StockForecasts.available(m,repo.containers())!!,0.0)
        val before=RawData.dump(db.openHelper.writableDatabase).toString()
        repo.restoreVoidedContainer(id)
        assertEquals(before,RawData.dump(db.openHelper.writableDatabase).toString())
    }
    @Test fun restorationDoesNotAlwaysOpenThePackage()=runBlocking {
        val m=med()
        for(state in listOf("SEALED","EMPTY","DISCARDED")) {
            val used=if(state=="EMPTY")30.0 else 0.0
            val id=dao.insertContainer(ContainerEntity(medication_id=m,capacity=30.0,initial_used_amount=used,used_amount=used,state=state))
            repo.removeMistakenContainer(id);repo.restoreVoidedContainer(id)
            assertEquals(state,dao.container(id).state)
        }
    }
    @Test fun laterStockOrIntakeChangesBlockRestorationAndRollBack()=runBlocking {
        val m=med();val id=box(m);repo.removeMistakenContainer(id)
        box(m)
        val before=RawData.dump(db.openHelper.writableDatabase).toString()
        assertThrows(PackageRestoreConflict::class.java){runBlocking{repo.restoreVoidedContainer(id)}}
        assertEquals(before,RawData.dump(db.openHelper.writableDatabase).toString())
        assertEquals("VOIDED",dao.container(id).state)
    }
    @Test fun failedVoidRollsBackItsEvidence()=runBlocking {
        val m=med();val id=box(m)
        val raw=db.openHelper.writableDatabase;val before=RawData.dump(raw).toString()
        raw.execSQL("CREATE TRIGGER test_abort_void BEFORE UPDATE ON supply_container WHEN NEW.state='VOIDED' BEGIN SELECT RAISE(ABORT,'test failure'); END")
        assertThrows(Exception::class.java){runBlocking{repo.removeMistakenContainer(id)}}
        assertEquals(before,RawData.dump(raw).toString())
    }
    @Test fun backupRoundTripRetainsVoidEvidenceAndSafeRestore()=runBlocking {
        val m=med();val id=box(m);repo.correctStock(id,2.0);repo.removeMistakenContainer(id)
        val password="synthetic-package-pass".toCharArray()
        val bytes=repo.exportBackup(password)
        repo.restoreBackup(bytes,password)
        assertEquals("VOIDED",dao.container(id).state);assertTrue(repo.containers().isEmpty())
        assertEquals(1,dao.supplyForContainer(id).size)
        repo.restoreVoidedContainer(id)
        assertEquals(2.0,StockForecasts.available(m,repo.containers())!!,0.0)
    }
    @Test fun backupWithoutVoidEvidenceIsRejectedAtomically()=runBlocking {
        val m=med();val id=box(m);val pass="synthetic-package-pass".toCharArray()
        val plain=org.json.JSONObject(String(BackupCodec.decrypt(repo.exportBackup(pass),pass)))
        plain.getJSONObject("tables").getJSONArray("supply_container").getJSONObject(0).put("state","VOIDED")
        val before=RawData.dump(db.openHelper.writableDatabase).toString()
        assertThrows(Exception::class.java){runBlocking{repo.restoreBackup(BackupCodec.encrypt(plain.toString().toByteArray(),pass),pass)}}
        assertEquals(before,RawData.dump(db.openHelper.writableDatabase).toString());assertEquals("IN_USE",dao.container(id).state)
    }
    @Test fun originalAdjustmentAndCorrectionEvidenceStayImmutable()=runBlocking {
        val m=med();val id=box(m);repo.correctStock(id,2.0);repo.removeMistakenContainer(id)
        val raw=db.openHelper.writableDatabase
        for(sql in listOf("DELETE FROM supply_transaction","UPDATE supply_transaction SET used_delta=1", "DELETE FROM trash_item WHERE ref LIKE 'package-void:%'"))
            assertThrows(Exception::class.java){raw.execSQL(sql)}
        assertEquals("VOIDED",dao.container(id).state)
    }

    @Test fun editingAndDeletingIntakeDoesNotReviveTheMistakenPackage()=runBlocking {
        val m=med();val fake=box(m);repo.removeMistakenContainer(fake);box(m)
        val record=dao.record(RecordEntity(medication_id=m,taken_utc=1000,taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot="{}"))
        db.withTransaction{SupplyLedger.allocate(dao,dao.recordById(record)!!)}
        repo.editRecord(record,java.time.Instant.ofEpochMilli(2000),1.0)
        repo.deleteRecord(record)
        assertEquals("VOIDED",dao.container(fake).state)
        assertTrue(dao.supplyForContainer(fake).isEmpty())
        assertEquals(30.0,StockForecasts.available(m,repo.containers())!!,0.0)
    }

    @Test fun mistakenExtraBoxIsRemovedAfterCorrectingTheOriginalOneTabletBox()=runBlocking {
        val m=med();val real=box(m);repo.correctStock(real,0.0)
        val fake=box(m);repo.correctStock(fake,2.0)
        repo.correctStock(real,2.0)
        assertEquals(4.0,StockForecasts.available(m,repo.containers())!!,0.0)
        val realBefore=dao.container(real);val history=dao.supplyForContainer(fake)
        repo.removeMistakenContainer(fake)
        assertEquals(2.0,StockForecasts.available(m,repo.containers())!!,0.0)
        assertEquals(realBefore,dao.container(real));assertEquals(history,dao.supplyForContainer(fake))
        assertEquals(listOf(real),repo.containers().map{it.id})
    }
    @Test fun replacementSkipsVoidedSealedPackage()=runBlocking {
        val m=med()
        val fake=dao.insertContainer(ContainerEntity(medication_id=m,capacity=30.0,initial_used_amount=0.0,used_amount=0.0,state="SEALED"))
        repo.removeMistakenContainer(fake)
        val real=dao.insertContainer(ContainerEntity(medication_id=m,capacity=30.0,initial_used_amount=0.0,used_amount=0.0,state="SEALED"))
        repo.replaceContainer(m,30.0)
        assertEquals("VOIDED",dao.container(fake).state);assertEquals("IN_USE",dao.container(real).state)
        assertEquals(listOf(real),repo.containers().map{it.id})
    }
    @Test fun newActualIntakeBlocksUnsafeAutomaticRestore()=runBlocking {
        val m=med();val id=box(m);repo.removeMistakenContainer(id)
        dao.record(RecordEntity(medication_id=m,taken_utc=1000,taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,unallocated_supply_amount=2.0,config_snapshot="{}"))
        val before=RawData.dump(db.openHelper.writableDatabase).toString()
        assertThrows(PackageRestoreConflict::class.java){runBlocking{repo.restoreVoidedContainer(id)}}
        assertEquals(before,RawData.dump(db.openHelper.writableDatabase).toString())
    }

}
