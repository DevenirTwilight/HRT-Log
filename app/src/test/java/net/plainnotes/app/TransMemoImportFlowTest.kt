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

/** Real read -> in-memory preview -> repository import, using the existing synthetic SQLite fixture only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=android.app.Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class TransMemoImportFlowTest {
    private lateinit var context: Context
    private lateinit var db: NotesDatabase
    private lateinit var repo: NotesRepository
    private lateinit var model: NotesViewModel
    private lateinit var input: File
    @Before fun setup() {
        context=ApplicationProvider.getApplicationContext()
        db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        db.openHelper.writableDatabase
        repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=net.plainnotes.app.syntheticLibrary(db)})
        model=NotesViewModel(repo,ReminderCoordinator(context,repo),context).also {net.plainnotes.app.awaitRuntimeLibrary(it)}
        input=File.createTempFile("synthetic-tm-", ".db", context.cacheDir)
        val sql=File("../importer/src/test/resources/transmemo_v8_synthetic.sql").readText()
        SQLiteDatabase.openOrCreateDatabase(input,null).use { d ->
            sql.split(";\n").map { it.lines().filterNot{l->l.trim().startsWith("--")}.joinToString("\n").trim() }
                .filter{it.isNotEmpty()&&!it.contains("android_metadata")}.forEach(d::execSQL)
        }
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
    @Test fun previewDeletesCopyAndStillImportsParsedMemoryWithoutChangingOriginal() {
        val original=input.readBytes()
        model.openTransMemo(Uri.fromFile(input))
        await{model.dataJob.value is NotesViewModel.DataJob.ImportReady}
        val ready=model.dataJob.value as NotesViewModel.DataJob.ImportReady
        assertEquals(4,ready.export.products.size)
        assertNoCopy()
        model.runImport(ready.export,TransMemo.Choices(mapOf(1L to 120,2L to 60,3L to 60,4L to 60),
            TransMemo.LateHandling.AS_MISSED,TransMemo.WellbeingScale.ONE_TO_FIVE),false)
        await{model.dataJob.value is NotesViewModel.DataJob.Imported}
        assertEquals(9,runBlocking{repo.records()}.size)
        assertArrayEquals(original,input.readBytes())
    }
    @Test fun rapidReopenAndCancelDoNotPublishAnOldPreview() {
        repeat(10){model.openTransMemo(Uri.fromFile(input))}
        model.cancelImport()
        await{model.dataJob.value is NotesViewModel.DataJob.Idle}
        model.openTransMemo(Uri.fromFile(input))
        await{model.dataJob.value is NotesViewModel.DataJob.ImportReady}
        assertNoCopy()
        model.cancelImport()
        assertTrue(model.dataJob.value is NotesViewModel.DataJob.Idle)
        assertNoCopy()
    }
    @Test fun replacingImportKeepsItsRealCommitAndRevokesTheOldScreen() {
        val original=input.readBytes()
        model.openTransMemo(Uri.fromFile(input))
        await {model.dataJob.value is NotesViewModel.DataJob.ImportReady}
        val ready=model.dataJob.value as NotesViewModel.DataJob.ImportReady
        model.runImport(ready.export,TransMemo.Choices(mapOf(1L to 120,2L to 60,3L to 60,4L to 60),
            TransMemo.LateHandling.AS_MISSED,TransMemo.WellbeingScale.ONE_TO_FIVE),true)
        await {model.dataJob.value is NotesViewModel.DataJob.Imported || model.dataJob.value is NotesViewModel.DataJob.Failed}
        assertTrue("A replacing import committed but was reported as failed",model.dataJob.value is NotesViewModel.DataJob.Imported)
        assertEquals(9,runBlocking {repo.records()}.size)
        assertTrue(model.librarySession.value is net.plainnotes.app.runtime.RuntimeLibrarySession.State.Unavailable)
        assertEquals(net.plainnotes.app.runtime.RuntimeLibrarySession.State.Unavailable(R.string.library_change_committed),model.librarySession.value)
        assertArrayEquals(original,input.readBytes());assertNoCopy()
    }
    @Test fun switchingToHrtTrackerCancelsTheTransMemoRead() {
        val tracker=File(context.cacheDir,"b2-synthetic-ht.json")
        try {
            tracker.writeText(File("../importer/src/test/resources/hrttracker_v2_synthetic.json").readText())
            model.openTransMemo(Uri.fromFile(input))
            model.openHrtTracker(Uri.fromFile(tracker))
            await{model.dataJob.value is NotesViewModel.DataJob.HtReady}
            assertNoCopy()
        } finally {tracker.delete()}
    }
    @Test fun invalidSchemaKeepsSafeErrorAndRemovesCopy() {
        SQLiteDatabase.openDatabase(input.path,null,SQLiteDatabase.OPEN_READWRITE).use{it.version=7}
        model.openTransMemo(Uri.fromFile(input))
        await{model.dataJob.value is NotesViewModel.DataJob.Failed}
        assertEquals(R.string.import_invalid,(model.dataJob.value as NotesViewModel.DataJob.Failed).message)
        assertNoCopy()
    }

    @Test fun automaticEntryPreservesPreviewMergeAndDuplicateSemantics() {
        model.openImport(Uri.fromFile(input))
        await {model.dataJob.value is NotesViewModel.DataJob.ImportReady || model.dataJob.value is NotesViewModel.DataJob.Failed}
        assertTrue(model.dataJob.value is NotesViewModel.DataJob.ImportReady)
        val ready=model.dataJob.value as NotesViewModel.DataJob.ImportReady
        assertNoCopy()
        val choices=TransMemo.Choices(mapOf(1L to 120,2L to 60,3L to 60,4L to 60),TransMemo.LateHandling.AS_MISSED,TransMemo.WellbeingScale.ONE_TO_FIVE)
        repeat(2) {
            val operation=model.runImport(ready.export,choices,false)
            await {operation.isCompleted && model.dataJob.value is NotesViewModel.DataJob.Imported}
            assertEquals(9,runBlocking {repo.records()}.size)
        }
    }
    @Test fun stalePickerCannotMutateOrPublishPreview() {
        model.openImport(Uri.fromFile(input),null)
        await {model.dataJob.value is NotesViewModel.DataJob.Failed}
        assertEquals(R.string.library_scope_changed,(model.dataJob.value as NotesViewModel.DataJob.Failed).message)
        assertTrue(runBlocking {repo.records()}.isEmpty());assertNoCopy()
    }

    @Test fun unknownAutomaticInputDoesNotChangeCurrentData() {
        input.writeText("{}")
        model.openImport(Uri.fromFile(input))
        await {model.dataJob.value is NotesViewModel.DataJob.Failed}
        assertEquals(R.string.import_format_unknown,(model.dataJob.value as NotesViewModel.DataJob.Failed).message)
        assertTrue(runBlocking {repo.records()}.isEmpty());assertNoCopy()
    }
}
