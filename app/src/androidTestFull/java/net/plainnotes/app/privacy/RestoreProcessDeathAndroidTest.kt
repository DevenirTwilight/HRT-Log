package net.plainnotes.app.privacy

import android.content.Intent
import android.net.Uri
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.R
import net.plainnotes.app.data.BackupCodec
import net.plainnotes.app.debug.DataPrivacyTestActivity
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate

/** External script kills this process while a real restore password dialog still contains input. */
class RestoreProcessDeathAndroidTest {
    @get:Rule val ui=createAndroidComposeRule<DataPrivacyTestActivity>()
    private val phase get()=InstrumentationRegistry.getArguments().getString("restore_death_phase")
    private val password="synthetic-only-password"
    private fun file(name:String)=File(ui.activity.cacheDir,name)
    private fun text(id:Int)=ui.activity.getString(id)
    @Test fun prepareOpenPasswordAndWaitForExternalKill(){
        Assume.assumeTrue(phase=="prepare")
        file("b2-restore-death-ready").delete()
        ui.runOnUiThread{ui.activity.model.backupHistory.clear()}
        val source=file("b2-restore-death-source.pnbak")
        runBlocking{ui.activity.repository.setNote(LocalDate.of(2026,1,1),"synthetic restart sentinel");source.writeBytes(ui.activity.repository.exportBackup(password.toCharArray()))}
        SyntheticPicker.result(Intent.ACTION_OPEN_DOCUMENT,Uri.fromFile(source)){ui.onNodeWithText(text(R.string.backup_restore)).performScrollTo().performClick()}
        ui.onNodeWithTag("restore-protection").assertExists()
        ui.onNodeWithText(text(R.string.restore_continue)).performScrollTo().performClick()
        ui.onAllNodes(hasSetTextAction()).onFirst().performTextInput(password)
        ui.onNodeWithText(text(R.string.backup_password)).assertExists()
        assertTrue(ui.activity.model.dataJob.value is NotesViewModel.DataJob.Idle)
        file("b2-restore-death-ready").writeText(Process.myPid().toString()) // no URI, secret or health text
        val end=System.nanoTime()+60_000_000_000L
        while(System.nanoTime()<end)Thread.sleep(50)
        fail("The external script must kill the prepared process before it can finish")
    }
    @Test fun freshProcessHasNoPendingRestoreOrPassword(){
        Assume.assumeTrue(phase=="verify")
        val ready=file("b2-restore-death-ready")
        assertNotEquals("Must be a genuinely new process",ready.readText().trim().toInt(),Process.myPid())
        ui.onNodeWithTag("restore-protection").assertDoesNotExist()
        ui.onNodeWithText(text(R.string.backup_password)).assertDoesNotExist()
        assertTrue(ui.activity.model.dataJob.value is NotesViewModel.DataJob.Idle)
        assertNull(ui.activity.model.backupHistory.state.value.lastExport)
        assertTrue(runBlocking{ui.activity.repository.notes(LocalDate.of(2026,1,1),LocalDate.of(2026,1,1))}.isEmpty())
        val source=file("b2-restore-death-source.pnbak")
        assertTrue("Selected encrypted source remains intact",BackupCodec.decrypt(source.readBytes(),password.toCharArray()).decodeToString().contains("synthetic restart sentinel"))
        assertTrue(ready.delete());assertTrue(source.delete())
    }
}
