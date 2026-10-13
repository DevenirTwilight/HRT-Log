package net.plainnotes.app.stock

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Synthetic persistent Room9 and encrypted backup across two externally separated app processes. */
class VoidedPackageRestartAndroidTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun database()=Room.databaseBuilder(context,NotesDatabase::class.java,"synthetic-void-restart").allowMainThreadQueries().addCallback(SchemaGuards).build()
    private fun repo(db:NotesDatabase)=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
    @Test fun prepare()=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("void_restart_phase")=="prepare")
        context.deleteDatabase("synthetic-void-restart")
        val db=database()
        try {
            val dao=db.dao();val repository=repo(db)
            val m=dao.insertMedication(MedicationEntity(name="Synthetic",molecule="E2",route="ORAL",unit="MG",dose_per_intake=2.0,container_capacity=30.0,site_rotation=false,notifications_on=true,active=true,sort_order=0))
            repository.addContainers(m,30.0,1,true)
            val id=repository.containers().single().id
            repository.correctStock(id,2.0);repository.removeMistakenContainer(id)
            assertEquals("VOIDED",dao.container(id).state);assertTrue(repository.containers().isEmpty())
            File(context.filesDir,"synthetic-void-backup.pnbak").writeBytes(repository.exportBackup("synthetic-void-pass".toCharArray()))
            File(context.filesDir,"synthetic-void-pid").writeText(android.os.Process.myPid().toString())
        } finally {db.close()}
    }
    @Test fun verifyFreshProcess()=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("void_restart_phase")=="verify")
        assertNotEquals(File(context.filesDir,"synthetic-void-pid").readText().toInt(),android.os.Process.myPid())
        val db=database()
        try {
            val repository=repo(db);val hidden=repository.voidedContainers().single()
            assertEquals(28.0,hidden.used_amount,0.0);assertTrue(repository.containers().isEmpty())
            repository.restoreBackup(File(context.filesDir,"synthetic-void-backup.pnbak").readBytes(),"synthetic-void-pass".toCharArray())
            assertEquals("VOIDED",repository.voidedContainers().single().state)
            repository.restoreVoidedContainer(hidden.id)
            assertEquals("IN_USE",repository.containers().single().state)
            assertEquals(2.0,StockForecasts.available(hidden.medication_id,repository.containers())!!,0.0)
        } finally {
            db.close();context.deleteDatabase("synthetic-void-restart")
            File(context.filesDir,"synthetic-void-backup.pnbak").delete();File(context.filesDir,"synthetic-void-pid").delete()
        }
    }
}
