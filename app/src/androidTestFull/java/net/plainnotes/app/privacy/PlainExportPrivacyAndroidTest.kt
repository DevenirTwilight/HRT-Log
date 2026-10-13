package net.plainnotes.app.privacy

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.R
import net.plainnotes.app.debug.DataPrivacyTestActivity
import org.junit.*
import java.io.File

/** Password-field/dialog behavior runs on Android, not the Robolectric idling approximation. */
class PlainExportPrivacyAndroidTest {
    @get:Rule val ui=createAndroidComposeRule<DataPrivacyTestActivity>()
    @Test fun encryptedBackupOpensItsOwnPasswordFlowWithoutPlainExportNotice() {
        fun text(id:Int)=ui.activity.getString(id)
        val file=File(ui.activity.cacheDir,"b2-synthetic-cancelled-backup.pnbak")
        try {
            ui.onNodeWithText(text(R.string.backup_export_desc)).assertExists()
            ui.onNodeWithText(text(R.string.backup_password_note)).assertDoesNotExist()
            SyntheticPicker.result(Intent.ACTION_CREATE_DOCUMENT,Uri.fromFile(file)){
                ui.onNodeWithText(text(R.string.backup_export)).performScrollTo().performClick()
            }
            ui.onNodeWithText(text(R.string.backup_password_note)).assertExists()
            ui.onNodeWithTag("export-privacy-notice").assertDoesNotExist()
            ui.onNodeWithText(text(R.string.cancel)).performClick()
            ui.onNodeWithText(text(R.string.backup_password_note)).assertDoesNotExist()
        }finally{file.delete()}
    }
}
