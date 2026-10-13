package net.plainnotes.app.privacy

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.R
import net.plainnotes.app.debug.DataPrivacyTestActivity
import org.junit.*
import org.junit.Assert.*
import java.io.File

class BackupCompletionAndroidTest {
    @get:Rule val ui=createAndroidComposeRule<DataPrivacyTestActivity>()
    @Before fun clear(){ui.runOnUiThread{ui.activity.model.backupHistory.clear()}}
    @After fun reset(){ui.runOnUiThread{ui.activity.model.backupHistory.clear()}}
    private fun text(id:Int)=ui.activity.getString(id)
    @Test fun pickerCancellationDoesNotAskForPasswordOrRecordSuccess(){
        SyntheticPicker.result(Intent.ACTION_CREATE_DOCUMENT,null){ui.onNodeWithText(text(R.string.backup_export)).performScrollTo().performClick()}
        ui.onNodeWithText(text(R.string.backup_password_note)).assertDoesNotExist()
        assertNull(ui.activity.model.backupHistory.state.value.lastExport)
        assertTrue(ui.activity.model.dataJob.value is NotesViewModel.DataJob.Idle)
    }
    @Test fun actualExportRecordsTimeAndWipeClearsTheOperationMetadata(){
        val file=File(ui.activity.cacheDir,"b2-synthetic-completed-backup.pnbak")
        try{
            SyntheticPicker.result(Intent.ACTION_CREATE_DOCUMENT,Uri.fromFile(file)){ui.onNodeWithText(text(R.string.backup_export)).performScrollTo().performClick()}
            ui.onAllNodes(hasSetTextAction()).onFirst().performTextInput("synthetic-only-password")
            ui.onAllNodes(hasSetTextAction()).onLast().performTextInput("synthetic-only-password")
            ui.onNodeWithText(text(R.string.ok)).performClick()
            ui.waitUntil(30_000){ui.activity.model.dataJob.value is NotesViewModel.DataJob.Done || ui.activity.model.dataJob.value is NotesViewModel.DataJob.Failed}
            assertTrue("A real encrypted export must succeed",ui.activity.model.dataJob.value is NotesViewModel.DataJob.Done)
            assertNotNull(ui.activity.model.backupHistory.state.value.lastExport)
            assertTrue(file.readBytes().take(6).toByteArray().decodeToString()=="PNBAK1")
            ui.runOnUiThread{ui.activity.model.clearDataJob()}
            var wiped=false;ui.runOnUiThread{ui.activity.model.wipeAll{wiped=true}}
            ui.waitUntil(30_000){wiped}
            assertNull(ui.activity.model.backupHistory.state.value.lastExport)
            assertFalse(ui.activity.model.backupHistory.state.value.reminderEnabled)
        }finally{file.delete()}
    }
    @Test fun passwordAndDestinationDoNotSurviveRecreation(){
        val file=File(ui.activity.cacheDir,"b2-synthetic-recreated-backup.pnbak")
        try{
            SyntheticPicker.result(Intent.ACTION_CREATE_DOCUMENT,Uri.fromFile(file)){ui.onNodeWithText(text(R.string.backup_export)).performScrollTo().performClick()}
            ui.onAllNodes(hasSetTextAction()).onFirst().performTextInput("synthetic-only-password")
            ui.activityRule.scenario.recreate()
            ui.onNodeWithText(text(R.string.backup_password_note)).assertDoesNotExist()
            assertNull(ui.activity.model.backupHistory.state.value.lastExport)
            assertTrue(ui.activity.model.dataJob.value is NotesViewModel.DataJob.Idle)
        }finally{file.delete()}
    }
}
