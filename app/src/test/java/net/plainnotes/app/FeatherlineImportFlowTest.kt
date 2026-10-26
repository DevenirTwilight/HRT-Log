package net.plainnotes.app

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.reminder.ReminderCoordinator
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class FeatherlineImportFlowTest {
    private lateinit var context:Context
    private lateinit var db:NotesDatabase
    private lateinit var repo:NotesRepository
    private lateinit var model:NotesViewModel
    private lateinit var input:File
    private var failAfterCommit=false
    private var selected:NotesDatabase?=null
    @Before fun setup(){
        context=ApplicationProvider.getApplicationContext();db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build();db.openHelper.writableDatabase
        repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space):NotesDatabase {
            if(failAfterCommit && !db.inTransaction() && db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM featherline_import_item").use{it.moveToFirst();it.getInt(0)}==3)error("Synthetic post-commit refresh failure")
            return syntheticLibrary(selected ?: db)
        }})
        model=NotesViewModel(repo,ReminderCoordinator(context,repo),context).also{awaitRuntimeLibrary(it)}
        input=File.createTempFile("synthetic-featherline-",".json",context.cacheDir)
        input.writeBytes(File("../core/data/src/test/resources/featherline/clinical-v3.cipher").readBytes())
    }
    @After fun close(){ViewModelStore().apply{put("model",model);clear()};db.close();input.delete()}
    private fun await(done:()->Boolean){val deadline=System.nanoTime()+30_000_000_000L;while(!done()&&System.nanoTime()<deadline){shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10)};assertTrue("Expected terminal import state",done())}
    private fun password():NotesViewModel.DataJob.FeatherlinePassword {
        val j=model.openImport(Uri.fromFile(input));await{j.isCompleted}
        assertTrue(model.dataJob.value is NotesViewModel.DataJob.FeatherlinePassword)
        return model.dataJob.value as NotesViewModel.DataJob.FeatherlinePassword
    }
    private fun preview():NotesViewModel.DataJob.FeatherlineReady {
        val j=requireNotNull(model.decryptFeatherline(password(),"synthetic-only-password".toCharArray()));await{j.isCompleted}
        assertTrue(model.dataJob.value is NotesViewModel.DataJob.FeatherlineReady)
        return model.dataJob.value as NotesViewModel.DataJob.FeatherlineReady
    }
    @Test fun actualEncryptedReadPreviewCommitAndReplayPreserveFacts(){
        val first=preview();assertEquals(1,first.preview.newDoses);assertEquals(2,first.preview.newLabs)
        assertTrue(runBlocking{repo.records()}.isEmpty());assertTrue(runBlocking{repo.labs()}.isEmpty())
        val job=requireNotNull(model.runFeatherlineImport(first));assertNull(model.runFeatherlineImport(first));await{job.isCompleted}
        val result=model.dataJob.value as NotesViewModel.DataJob.FeatherlineImported
        assertEquals(1,result.summary.doses);assertEquals(2,result.summary.labs)
        val saved=runBlocking{repo.records()};val labs=runBlocking{repo.labs()}
        assertEquals("IMPORT_FEATHERLINE",saved.single().origin);assertEquals(1.0,saved.single().actual_dose!!,0.0)
        val again=preview();assertEquals(3,again.preview.existing);assertEquals(0,again.preview.newDoses+again.preview.newLabs)
        val replay=requireNotNull(model.runFeatherlineImport(again));await{replay.isCompleted}
        assertEquals(0,(model.dataJob.value as NotesViewModel.DataJob.FeatherlineImported).summary.doses)
        assertEquals(saved,runBlocking{repo.records()});assertEquals(labs,runBlocking{repo.labs()})
    }
    @Test fun wrongPasswordRetryUsesTheSameSnapshotWithoutReopeningProvider(){
        val p=password();input.delete()
        val wrong="wrong".toCharArray();val job=requireNotNull(model.decryptFeatherline(p,wrong));await{job.isCompleted}
        assertTrue(wrong.all{it=='\u0000'});val retry=model.dataJob.value as NotesViewModel.DataJob.FeatherlinePassword
        assertEquals(R.string.featherline_auth_failed,retry.error);assertTrue(runBlocking{repo.records()}.isEmpty())
        val correct=requireNotNull(model.decryptFeatherline(retry,"synthetic-only-password".toCharArray()));await{correct.isCompleted}
        assertTrue(model.dataJob.value is NotesViewModel.DataJob.FeatherlineReady)
    }
    @Test fun cancelClosesEncryptedSnapshotAndOldPreviewCannotBeReplayed(){
        val p=password();model.cancelAutomaticImport()
        assertNull(model.decryptFeatherline(p,"synthetic-only-password".toCharArray()))
        try{p.archive.authenticate("synthetic-only-password".toCharArray());fail("Cancelled snapshot read") }catch(_:IllegalStateException){}
        val ready=preview();model.cancelAutomaticImport();assertNull(model.runFeatherlineImport(ready));assertTrue(runBlocking{repo.records()}.isEmpty())
    }
    @Test fun revokedOrReplacedLibraryCannotReceiveThePreparedClinicalRows(){
        val ready=preview();val other=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        try{selected=syntheticLibrary(other);val job=requireNotNull(model.runFeatherlineImport(ready));await{job.isCompleted}
            assertTrue(model.dataJob.value is NotesViewModel.DataJob.Failed);assertTrue(runBlocking{db.dao().records()}.isEmpty());assertTrue(runBlocking{other.dao().records()}.isEmpty())
        }finally{selected=null;other.close()}
    }
    @Test fun postCommitRefreshFailureCannotReportTheRealTransactionAsFailure(){
        val ready=preview();failAfterCommit=true;val job=requireNotNull(model.runFeatherlineImport(ready));await{job.isCompleted}
        assertTrue(model.dataJob.value is NotesViewModel.DataJob.FeatherlineImported)
        assertEquals(1,(model.dataJob.value as NotesViewModel.DataJob.FeatherlineImported).summary.doses)
        failAfterCommit=false;assertEquals(1,runBlocking{repo.records()}.size);assertEquals(2,runBlocking{repo.labs()}.size)
    }
    @Test fun damagedCiphertextNeverPublishesClinicalPreview(){
        val b=input.readBytes();b[b.lastIndex]=(b.last().toInt() xor 1).toByte();input.writeBytes(b)
        val p=password();val job=requireNotNull(model.decryptFeatherline(p,"synthetic-only-password".toCharArray()));await{job.isCompleted}
        assertTrue(model.dataJob.value is NotesViewModel.DataJob.FeatherlinePassword);assertTrue(runBlocking{repo.records()}.isEmpty())
    }
}
