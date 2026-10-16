package net.plainnotes.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RuleKind
import net.plainnotes.app.reference.MedicationReferenceController
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class MedicationReferenceControllerTest {
    private lateinit var db:NotesDatabase;private lateinit var repo:NotesRepository;private lateinit var controller:MedicationReferenceController
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
    @Before fun setup(){val context=ApplicationProvider.getApplicationContext<Context>();db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build();repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db});controller=MedicationReferenceController(repo,scope)}
    @After fun cleanup(){scope.cancel();db.close()}
    private val product=UserReferenceProduct("SYNTHETIC source",listOf("SYNTHETIC compound A"),"2 mg / tablet","tablet",listOf("oral"),sourceVersion="user-v1",recordedOn="2026-10-16")
    private suspend fun med()=repo.saveMedication(MedicationEntity(name="SYNTHETIC recorded medication",molecule="E2",route="ORAL",unit="MG",dose_per_intake=0.5,container_capacity=28.0,soon_alert_minutes=0,late_after_minutes=60,site_rotation=false,notifications_on=false,active=true,sort_order=0),"E2",RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.NOON),emptySet(),Instant.parse("2026-03-28T12:00:00Z"))
    private fun count(table:String)=db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM `$table`").use{it.moveToFirst();it.getInt(0)}
    @Test fun openingSearchingAndCancellingMakesNoReferenceWrites()=runBlocking {
        val id=med();controller.open(id).join();controller.search("SYNTHETIC","","")!!.join();assertTrue(controller.close())
        assertEquals(0,count("reference_dataset"));assertEquals(0,count("reference_event"));assertEquals(1,count("medication"))
    }
    @Test fun actualConfirmationUnlinkAndReopenPreserveMedicationStockAndRecordContext()=runBlocking {
        val id=med();repo.addContainers(id,28.0,1,true);repo.unscheduled(id,Instant.parse("2026-03-28T13:00:00Z"),0.5)
        val beforeProfile=repo.profile(id);assertNotNull(beforeProfile);val beforeMed=db.dao().medication(id);val beforeContainers=repo.containers();val beforeRecords=repo.records()
        controller.open(id).join();controller.confirm(product)!!.join();assertTrue(controller.state.value.saved)
        assertEquals(product,repo.medicationReferenceState(id).reference!!.product)
        controller.close();controller.open(id).join();assertEquals(product,controller.state.value.current!!.reference!!.product)
        controller.confirm(null)!!.join();assertNull(repo.medicationReferenceState(id).reference!!.product)
        assertEquals(beforeProfile,repo.profile(id));assertEquals(beforeMed,db.dao().medication(id));assertEquals(beforeContainers,repo.containers());assertEquals(beforeRecords,repo.records())
        assertEquals(1,count("medication"));assertEquals(2,count("reference_event"))
    }
    @Test fun staleEditorCannotOverwriteNewerAssociationAndReloadIsExplicit()=runBlocking {
        val id=med();controller.open(id).join()
        repo.associateInitialMedication(id,UUID.randomUUID().toString(),product.copy(sourceVersion="other-editor"))
        controller.confirm(product)!!.join();assertTrue(controller.state.value.failed);assertTrue(controller.state.value.stale)
        assertNull(controller.confirm(product));assertEquals(1,count("reference_event"))
        controller.open(id).join();controller.confirm(product)!!.join();assertTrue(controller.state.value.saved);assertEquals(2,count("reference_event"))
    }
    @Test fun backupRestoreRetiresOpenEditorWithoutChangingImportedReference()=runBlocking {
        val id=med();repo.associateInitialMedication(id,UUID.randomUUID().toString(),product)
        val archive=repo.exportBackup("SYNTHETIC-password".toCharArray());controller.open(id).join()
        repo.restoreBackup(archive,"SYNTHETIC-password".toCharArray())
        controller.confirm(product.copy(sourceVersion="stale-change"))!!.join();assertTrue(controller.state.value.stale)
        assertEquals(product,repo.medicationReferenceState(id).reference!!.product);assertEquals(1,count("reference_event"))
    }
    @Test fun duplicateConfirmationAndCloseAreRejectedWhileTheRealWriteIsPending()=runBlocking {
        val id=med();val context=ApplicationProvider.getApplicationContext<Context>()
        val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        val block=java.util.concurrent.atomic.AtomicBoolean(false)
        val guarded=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space):NotesDatabase {
            if(block.get()){entered.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS))}
            return db
        }})
        val control=MedicationReferenceController(guarded,scope);control.open(id).join();block.set(true)
        val first=control.confirm(product)!!
        try {
            assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));assertTrue(control.state.value.busy)
            assertNull(control.confirm(product));assertFalse(control.close())
        }finally{release.countDown()}
        first.join();assertTrue(control.state.value.saved);assertEquals(1,count("reference_event"));assertEquals(1,count("medication"))
    }
    @Test fun restoringAnEmptyReferenceDatasetRetiresTheFirstAssociationPreview()=runBlocking {
        val id=med();controller.open(id).join();assertNull(controller.state.value.current!!.scope)
        val archive=repo.exportBackup("SYNTHETIC-empty-reference-password".toCharArray())
        repo.restoreBackup(archive,"SYNTHETIC-empty-reference-password".toCharArray())
        controller.confirm(product)!!.join()
        assertFalse("Stale empty preview committed a link; reference events=${count("reference_event")}",controller.state.value.saved)
        assertTrue("Restoring a dataset with no references must retire an older first-link preview",controller.state.value.stale)
        assertEquals(0,count("reference_event"));assertEquals(0,count("reference_dataset"))
    }
    @Test fun unsavedMedicationCanBrowseButCannotCreateOrLinkOne()=runBlocking {
        controller.open(null).join();assertNull(controller.confirm(product));assertEquals(0,count("medication"));assertEquals(0,count("reference_dataset"))
    }
}
