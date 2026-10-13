package net.plainnotes.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import net.plainnotes.app.data.*
import net.plainnotes.app.reminder.ReminderCoordinator
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],qualifiers="w411dp-h891dp-xxhdpi",application=android.app.Application::class)
class PlainExportEntryTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private lateinit var model:NotesViewModel
    private lateinit var db:NotesDatabase
    private val launches=mutableListOf<Intent>()
    private var request=0
    private val registry=object:ActivityResultRegistry(){override fun<I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?){
        request=requestCode;launches+=contract.createIntent(ui.activity,input)
    }}
    private val owner=object:ActivityResultRegistryOwner{override val activityResultRegistry=registry}
    @Before fun setup(){
        db=Room.inMemoryDatabaseBuilder(ui.activity,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        db.openHelper.writableDatabase
        val repo=NotesRepository(object:DatabaseAccess(ui.activity){override fun get(space:Space)=db})
        model=NotesViewModel(repo,ReminderCoordinator(ui.activity,repo),ui.activity.applicationContext)
    }
    @After fun close(){ViewModelStore().apply{put("model",model);clear()};db.close()}
    private fun text(id:Int)=ui.activity.getString(id)
    private fun data(){ui.setContent{CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner){MaterialTheme{Column(Modifier.verticalScroll(rememberScrollState())){DataSection(model,emptyMap())}}}}}
    private fun notice(){ui.onNodeWithText("This export file is not encrypted by HRT Log. After you save it, protection depends on the permissions and provider of the location you choose. Keep it secure and avoid unintended sharing. Encrypted backups (.pnbak) are different.").assertExists();assertTrue("Notice must precede SAF",launches.isEmpty())}
    private fun cancel(){ui.onNodeWithText(text(R.string.cancel)).performClick();assertTrue(launches.isEmpty())}
    private fun picker(mime:String,prefix:String){assertEquals(1,launches.size);assertEquals(Intent.ACTION_CREATE_DOCUMENT,launches.single().action);assertEquals(mime,launches.single().type);assertTrue(launches.single().getStringExtra(Intent.EXTRA_TITLE)!!.startsWith(prefix))}
    @Test fun csvRequiresNoticeAndCanCancelBeforePicker(){
        data();ui.onNodeWithText(text(R.string.export_csv)).performScrollTo().performClick();notice();cancel()
        ui.onNodeWithText(text(R.string.export_csv)).performScrollTo().performClick();notice()
        ui.onNodeWithText(text(R.string.export_action)).performClick();picker("application/zip","notes-export-")
        ui.runOnUiThread{registry.dispatchResult(request,Activity.RESULT_CANCELED,null)}
        assertTrue(model.dataJob.value is NotesViewModel.DataJob.Idle)
    }
    @Test fun encryptedBackupIsSeparateFromPlainExports(){
        data();ui.onNodeWithText(text(R.string.backup_export_desc)).assertExists()
        // Opening the password dialog is exercised with the same assertions on API35 (see PlainExportPrivacyAndroidTest).
        assertTrue(launches.isEmpty())
        ui.onNodeWithTag("export-privacy-notice").assertDoesNotExist()
    }
    @Test fun backupPickerComesBeforeAnyPasswordAndCancellationStaysIdle(){
        data();ui.onNodeWithText(text(R.string.backup_export)).performScrollTo().performClick()
        picker("application/octet-stream","notes-")
        ui.onNodeWithText(text(R.string.backup_password_note)).assertDoesNotExist()
        ui.runOnUiThread{registry.dispatchResult(request,Activity.RESULT_CANCELED,null)}
        ui.onNodeWithText(text(R.string.backup_password_note)).assertDoesNotExist()
        assertTrue(model.dataJob.value is NotesViewModel.DataJob.Idle)
        assertNull(model.backupHistory.state.value.lastExport)
    }
    @Test fun cancelledProtectionBackupCannotAutomaticallyStartRestore(){
        data();ui.onNodeWithText(text(R.string.backup_restore)).performScrollTo().performClick()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT,launches.single().action)
        val source=Uri.parse("content://synthetic-provider/restore-source")
        ui.runOnUiThread{registry.dispatchResult(request,Activity.RESULT_OK,Intent().setData(source))}
        ui.onNodeWithTag("restore-protection").assertExists()
        ui.onNodeWithText(text(R.string.backup_password)).assertDoesNotExist()
        ui.onNodeWithText(text(R.string.restore_backup_current)).performScrollTo().performClick()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT,launches.last().action)
        ui.runOnUiThread{registry.dispatchResult(request,Activity.RESULT_CANCELED,null)}
        ui.onNodeWithText(text(R.string.restore_backup_cancelled)).assertExists()
        assertTrue("Cancelled backup picker must never start a restore operation",model.dataJob.value is NotesViewModel.DataJob.Idle)
        assertNull(model.backupHistory.state.value.lastExport)
        ui.onNodeWithText(text(R.string.cancel)).performScrollTo().performClick()
        ui.onNodeWithTag("restore-protection").assertDoesNotExist()
    }
    @Test fun reportNoticePrecedesPickerWithChartChoice(){
        data();ui.onNodeWithText(text(R.string.export_pdf)).performScrollTo().performClick();notice();cancel()
        ui.onNodeWithText(text(R.string.export_pdf)).performScrollTo().performClick();notice()
        ui.onNodeWithText(text(R.string.report_include_chart)).assertExists()
        ui.onNodeWithText(text(R.string.export_action)).performClick();picker("application/pdf","notes-report-")
    }
    @Test fun summaryNoticePrecedesPickerAndCancelCreatesNothing(){
        val show=mutableStateOf(true);var dismissed=false
        ui.setContent{CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner){MaterialTheme{if(show.value)SummaryExportDialog(model,NotesState()) {show.value=false;dismissed=true}}}}
        notice();cancel();assertTrue(dismissed)
        ui.onNodeWithText(text(R.string.wb_summary)).assertDoesNotExist()
        ui.runOnUiThread{show.value=true};notice();ui.onNodeWithText(text(R.string.export_pdf)).performClick();picker("application/pdf","notes-summary-")
    }
    @Test fun visitNoticePrecedesPickerAndUsesTheChosenDestination(){
        var output:Uri?=null;val show=mutableStateOf(true);var dismissed=false
        val appointment=AppointmentEntity(1,"ENDO",Instant.now().toEpochMilli(),"UTC",remind_minutes_before=60)
        ui.setContent{CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner){MaterialTheme{if(show.value)VisitPackDialog(appointment,NotesState(),NotesViewModel.ExtraState(),{show.value=false;dismissed=true}){u,_->output=u}}}}
        notice();cancel();assertTrue(dismissed);ui.onNodeWithText(text(R.string.visit_generate)).assertDoesNotExist()
        ui.runOnUiThread{show.value=true};notice();ui.onNodeWithText(text(R.string.export_pdf)).performClick();picker("application/pdf","notes-visit-")
        val target=Uri.parse("content://synthetic-provider/visit-output")
        ui.runOnUiThread{registry.dispatchResult(request,Activity.RESULT_OK,Intent().setData(target))}
        ui.waitForIdle();assertEquals(target,output)
    }
}
