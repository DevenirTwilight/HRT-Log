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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=android.app.Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class BackupExportFlowTest {
    private lateinit var context:Context
    private lateinit var db:NotesDatabase
    private lateinit var model:NotesViewModel
    private lateinit var file:File
    private val key="b2_backup_export_completed_utc"
    @Before fun setup(){
        context=ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("prefs",0).edit().clear().commit()
        db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build();db.openHelper.writableDatabase
        val repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=net.plainnotes.app.syntheticLibrary(db)})
        model=NotesViewModel(repo,ReminderCoordinator(context,repo),context).also {net.plainnotes.app.awaitRuntimeLibrary(it)}
        file=File.createTempFile("b2-synthetic-export-",".pnbak",context.cacheDir)
    }
    @After fun close(){ViewModelStore().apply{put("model",model);clear()};db.close();file.delete();context.getSharedPreferences("prefs",0).edit().clear().commit()}
    private fun await(done:()->Boolean){val end=System.nanoTime()+20_000_000_000L;while(!done()&&System.nanoTime()<end){shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10)};assertTrue("Expected data operation outcome",done())}
    private fun recorded(){assertTrue("A normally closed encrypted export must record its completion time",context.getSharedPreferences("prefs",0).contains(key));assertTrue(file.readBytes().take(6).toByteArray().decodeToString()=="PNBAK1")}
    @Test fun exportBackupRecordsOnlyACompletedEncryptedExport(){
        val password="synthetic-only-password".toCharArray()
        model.exportBackup(Uri.fromFile(file),password)
        await{model.dataJob.value is NotesViewModel.DataJob.Done}
        recorded()
    }
    @Test fun cancellationBeforeDispatchStillClearsTheSubmittedPassword(){
        val password="synthetic-only-password".toCharArray()
        val job=model.exportBackup(Uri.fromFile(file),password);job.cancel();await{job.isCompleted}
        assertTrue("No submitted password may survive cancelled dispatch",password.all{it=='\u0000'})
        assertFalse(context.getSharedPreferences("prefs",0).contains(key))
    }
    @Test fun disguiseGateBackupAlsoRecordsTheCompletedExport(){
        val password="synthetic-only-password".toCharArray()
        assertTrue(runBlocking{model.backupTo(Uri.fromFile(file),password)})
        recorded()
    }
}
