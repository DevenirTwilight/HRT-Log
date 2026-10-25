package net.plainnotes.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.importer.TransMemo
import net.plainnotes.app.reminder.ReminderCoordinator
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File

/** Unified single-snapshot Mona read, real scoped writer and truthful commit result. Synthetic only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=android.app.Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class MonaImportFlowTest {
    private lateinit var context: Context
    private lateinit var db: NotesDatabase
    private lateinit var repo: NotesRepository
    private lateinit var model: NotesViewModel
    private lateinit var input: File
    private var failAfterCommit=false
    private var selected:NotesDatabase?=null
    @Before fun setup() {
        context=ApplicationProvider.getApplicationContext()
        db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        db.openHelper.writableDatabase
        repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space):NotesDatabase {
            if(failAfterCommit && !db.inTransaction() && db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM external_import_item").use{it.moveToFirst();it.getInt(0)}==4)error("Synthetic post-COMMIT refresh failure")
            return net.plainnotes.app.syntheticLibrary(selected ?: db)
        }})
        model=NotesViewModel(repo,ReminderCoordinator(context,repo),context).also {net.plainnotes.app.awaitRuntimeLibrary(it)}
        input=File.createTempFile("synthetic-mona-", ".db", context.cacheDir)
        input.writeText(File("../importer/src/test/resources/mona23_synthetic.json").readText())
    }
    @After fun close() { ViewModelStore().apply{put("model",model);clear()};db.close();input.delete() }
    private fun await(done:()->Boolean) {
        val deadline=System.nanoTime()+20_000_000_000L
        while(!done()&&System.nanoTime()<deadline){shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10)}
        assertTrue("Expected safe data operation state",done())
    }
    private fun assertNoCopy() {
        val dir=File(context.noBackupFilesDir,"import")
        assertTrue("Plain SQLite copy must be removed before preview is published",dir.listFiles().orEmpty().none{it.isFile})
    }
    private fun preview():NotesViewModel.DataJob.MonaReady {
        val job=model.openImport(Uri.fromFile(input));await {job.isCompleted}
        assertTrue(model.dataJob.value is NotesViewModel.DataJob.MonaReady)
        return model.dataJob.value as NotesViewModel.DataJob.MonaReady
    }
    @Test fun automaticPreviewCommitAndRepeatKeepActualRecordsAndLabs() {
        val first=preview();assertEquals(2,first.preview.addedDoses);assertEquals(2,first.preview.addedLabs)
        assertTrue(runBlocking {repo.records()}.isEmpty())
        val job=requireNotNull(model.runMonaImport(first));assertNull(model.runMonaImport(first));await {job.isCompleted}
        val result=model.dataJob.value as NotesViewModel.DataJob.MonaImported
        assertEquals(2,result.summary.addedDoses);assertEquals(2,result.summary.addedLabs)
        val saved=runBlocking {repo.records()};val labs=runBlocking {repo.labs()}
        assertTrue(saved.all {it.origin=="IMPORT_MONA" && it.scheduled_utc==null && it.actual_dose==1.25})
        val again=preview();assertEquals(4,again.preview.existing)
        val repeatJob=requireNotNull(model.runMonaImport(again));await {repeatJob.isCompleted}
        assertEquals(0,(model.dataJob.value as NotesViewModel.DataJob.MonaImported).summary.addedDoses)
        assertEquals(saved,runBlocking {repo.records()});assertEquals(labs,runBlocking {repo.labs()})
    }
    @Test fun cancelPreviewCannotBeReplayedAndWritesNothing() {
        val ready=preview();model.cancelAutomaticImport();assertNull(model.runMonaImport(ready))
        assertTrue(runBlocking {repo.records()}.isEmpty());assertTrue(runBlocking {repo.labs()}.isEmpty())
    }
    @Test fun malformedAndUnsupportedVersionsNeverPublishSuccess() {
        for(text in listOf("{",input.readText().replace("\"database_version\": 23","\"database_version\": 24"))) {
            input.writeText(text);val job=model.openImport(Uri.fromFile(input));await {job.isCompleted}
            assertTrue(model.dataJob.value is NotesViewModel.DataJob.Failed)
            assertTrue(runBlocking {repo.records()}.isEmpty())
        }
    }
    @Test fun stalePickerIsRejectedWithoutClinicalWrites() {
        val job=model.openImport(Uri.fromFile(input),null);await {job.isCompleted}
        assertEquals(R.string.library_scope_changed,(model.dataJob.value as NotesViewModel.DataJob.Failed).message)
        assertTrue(runBlocking {repo.records()}.isEmpty())
    }
    @Test fun databaseFailureRollsBackBothTypesAndReportsFailure() {
        val ready=preview()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER synthetic_fail BEFORE INSERT ON lab_value BEGIN SELECT RAISE(ABORT,'synthetic failure');END")
        val job=requireNotNull(model.runMonaImport(ready));await {job.isCompleted}
        assertTrue(model.dataJob.value is NotesViewModel.DataJob.Failed)
        assertTrue(runBlocking {repo.records()}.isEmpty());assertTrue(runBlocking {repo.labs()}.isEmpty())
    }
    @Test fun postCommitReadFailureCannotTurnSavedClinicalFactsIntoFailure() {
        val ready=preview();failAfterCommit=true
        val job=requireNotNull(model.runMonaImport(ready));await {job.isCompleted}
        assertTrue(model.dataJob.value is NotesViewModel.DataJob.MonaImported)
        assertEquals(2,(model.dataJob.value as NotesViewModel.DataJob.MonaImported).summary.addedDoses)
        failAfterCommit=false;assertEquals(2,runBlocking {repo.records()}.size);assertEquals(2,runBlocking {repo.labs()}.size)
    }
    @Test fun revokedPreviewAndOldLibraryCannotWrite() {
        val ready=preview();model.runtimeStopped(0);shadowOf(Looper.getMainLooper()).idle()
        val job=requireNotNull(model.runMonaImport(ready));await {job.isCompleted}
        assertTrue(model.dataJob.value is NotesViewModel.DataJob.Failed)
        assertTrue(runBlocking {db.dao().records()}.isEmpty())
    }
    @Test fun previewCannotBeReboundToAnotherDataset() {
        val ready=preview()
        val other=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        try {
            selected=net.plainnotes.app.syntheticLibrary(other)
            val job=requireNotNull(model.runMonaImport(ready));await {job.isCompleted}
            assertTrue(model.dataJob.value is NotesViewModel.DataJob.Failed)
            assertTrue(runBlocking {other.dao().records()}.isEmpty());assertTrue(runBlocking {db.dao().records()}.isEmpty())
        } finally {selected=null;other.close()}
    }
}
