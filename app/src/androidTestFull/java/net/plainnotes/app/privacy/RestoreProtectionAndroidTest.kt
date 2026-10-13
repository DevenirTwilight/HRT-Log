package net.plainnotes.app.privacy

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.R
import net.plainnotes.app.data.BackupCodec
import net.plainnotes.app.debug.DataPrivacyTestActivity
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate

/** Real UI, actual encrypted files and actual repository; synthetic records only. */
class RestoreProtectionAndroidTest {
    @get:Rule val ui=createAndroidComposeRule<DataPrivacyTestActivity>()
    private val day=LocalDate.of(2026,1,1)
    private val password="synthetic-only-password"
    private lateinit var source:File
    private lateinit var output:File
    private lateinit var original:ByteArray
    private lateinit var before:String
    private fun text(id:Int)=ui.activity.getString(id)
    private fun tables()=runBlocking {JSONObject(BackupCodec.decrypt(ui.activity.repository.exportBackup(password.toCharArray()),password.toCharArray()).decodeToString()).getJSONObject("tables").toString()}
    @Before fun setup(){
        source=File(ui.activity.cacheDir,"b2-synthetic-restore-source.pnbak");output=File(ui.activity.cacheDir,"b2-synthetic-restore-output.pnbak");output.delete()
        ui.runOnUiThread{ui.activity.model.backupHistory.clear()}
        runBlocking {ui.activity.repository.setNote(day,"synthetic older note");original=ui.activity.repository.exportBackup(password.toCharArray());source.writeBytes(original);ui.activity.repository.setNote(day,"synthetic current note")}
        before=tables()
    }
    @After fun cleanup(){source.delete();output.delete();ui.runOnUiThread{ui.activity.model.backupHistory.clear()}}
    private fun select(){SyntheticPicker.result(Intent.ACTION_OPEN_DOCUMENT,Uri.fromFile(source)){ui.onNodeWithText(text(R.string.backup_restore)).performScrollTo().performClick()};ui.onNodeWithTag("restore-protection").assertExists();ui.onNodeWithText(text(R.string.backup_password)).assertDoesNotExist()}
    private fun chooseBackup(uri:Uri?){SyntheticPicker.result(Intent.ACTION_CREATE_DOCUMENT,uri){ui.onNodeWithText(text(R.string.restore_backup_current)).performScrollTo().performClick()}}
    private fun enter(confirm:Boolean,pw:String=password){ui.onAllNodes(hasSetTextAction()).onFirst().performTextInput(pw);if(confirm)ui.onAllNodes(hasSetTextAction()).onLast().performTextInput(pw);ui.onNodeWithText(text(R.string.ok)).performClick()}
    private fun untouched(){assertEquals("No current data or revisions may change",before,tables());assertArrayEquals("The restore source must remain unchanged",original,source.readBytes())}
    private fun waitResult(){ui.waitUntil(30_000){ui.activity.model.dataJob.value is NotesViewModel.DataJob.Done || ui.activity.model.dataJob.value is NotesViewModel.DataJob.Failed}}
    @Test fun cancelledChoiceDoesNotTouchData(){select();ui.onNodeWithText(text(R.string.cancel)).performScrollTo().performClick();ui.onNodeWithTag("restore-protection").assertDoesNotExist();untouched();assertNull(ui.activity.model.backupHistory.state.value.lastExport)}
    @Test fun cancelledBackupPickerDoesNotRestore(){select();chooseBackup(null);ui.onNodeWithText(text(R.string.restore_backup_cancelled)).assertExists();ui.onNodeWithText(text(R.string.backup_password)).assertDoesNotExist();assertTrue(ui.activity.model.dataJob.value is NotesViewModel.DataJob.Idle);untouched();assertNull(ui.activity.model.backupHistory.state.value.lastExport)}
    @Test fun sourceCannotBeOverwrittenByProtectionBackup(){select();chooseBackup(Uri.fromFile(source));ui.onNodeWithText(text(R.string.restore_backup_same_target)).assertExists();ui.onNodeWithText(text(R.string.backup_password)).assertDoesNotExist();untouched();assertNull(ui.activity.model.backupHistory.state.value.lastExport)}
    @Test fun successfulBackupStillRequiresExplicitRestoreAndKeepsUrisDistinct(){
        select();chooseBackup(Uri.fromFile(output));enter(true)
        ui.waitUntil(30_000){ui.activity.model.backupHistory.state.value.lastExport!=null && ui.activity.model.dataJob.value is NotesViewModel.DataJob.Idle}
        ui.onNodeWithText(text(R.string.restore_backup_saved)).assertExists();untouched()
        val exported=JSONObject(BackupCodec.decrypt(output.readBytes(),password.toCharArray()).decodeToString()).getJSONObject("tables").toString()
        assertEquals("Output must contain current data",before,exported)
        val time=ui.activity.model.backupHistory.state.value.lastExport
        ui.onNodeWithText(text(R.string.restore_continue)).performScrollTo().performClick();enter(false);waitResult()
        assertTrue(ui.activity.model.dataJob.value is NotesViewModel.DataJob.Done)
        assertEquals("synthetic older note",runBlocking{ui.activity.repository.notes(day,day)}.single().text)
        assertEquals("Restore must not update export history",time,ui.activity.model.backupHistory.state.value.lastExport)
        assertArrayEquals(original,source.readBytes())
    }
    @Test fun failedBackupReturnsToChoiceAndCannotRestore(){
        select();chooseBackup(Uri.parse("content://b2-synthetic-missing-provider/output"));enter(true)
        ui.waitUntil(30_000){ui.activity.model.dataJob.value is NotesViewModel.DataJob.Idle && ui.onAllNodesWithTag("restore-backup-result").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText(text(R.string.restore_backup_failed)).assertExists();untouched();assertNull(ui.activity.model.backupHistory.state.value.lastExport)
    }
    @Test fun wrongRestorePasswordLeavesAllDataUnchanged(){select();ui.onNodeWithText(text(R.string.restore_continue)).performScrollTo().performClick();enter(false,"synthetic-wrong-password");waitResult();assertTrue(ui.activity.model.dataJob.value is NotesViewModel.DataJob.Failed);untouched();assertNull(ui.activity.model.backupHistory.state.value.lastExport)}
    @Test fun damagedRestoreLeavesAllDataUnchanged(){source.writeBytes(byteArrayOf(1,2,3));original=source.readBytes();select();ui.onNodeWithText(text(R.string.restore_continue)).performScrollTo().performClick();enter(false);waitResult();assertTrue(ui.activity.model.dataJob.value is NotesViewModel.DataJob.Failed);untouched()}
    @Test fun cancelledRestorePasswordDoesNotChangeData(){select();ui.onNodeWithText(text(R.string.restore_continue)).performScrollTo().performClick();ui.onAllNodes(hasSetTextAction()).onFirst().performTextInput(password);ui.onNodeWithText(text(R.string.cancel)).performClick();ui.onNodeWithTag("restore-protection").assertExists();untouched();assertNull(ui.activity.model.backupHistory.state.value.lastExport)}
    @Test fun recreationDropsPendingSourceOutputAndPassword(){select();chooseBackup(Uri.fromFile(output));ui.onAllNodes(hasSetTextAction()).onFirst().performTextInput(password);ui.activityRule.scenario.recreate();ui.onNodeWithTag("restore-protection").assertDoesNotExist();ui.onNodeWithText(text(R.string.backup_password)).assertDoesNotExist();assertTrue(ui.activity.model.dataJob.value is NotesViewModel.DataJob.Idle);untouched();assertFalse(output.exists());assertNull(ui.activity.model.backupHistory.state.value.lastExport)}
}
