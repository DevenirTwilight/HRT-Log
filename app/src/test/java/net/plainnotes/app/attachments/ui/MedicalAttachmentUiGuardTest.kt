package net.plainnotes.app.attachments.ui

import net.plainnotes.app.attachments.AttachmentFlowController
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** MED-02 attachment UI isolation (REQUIREMENTS §149): no release entry, no storage internals, honest wording. */
class MedicalAttachmentUiGuardTest {
    private val main=File("src/main/java/net/plainnotes/app")
    private val ui=File(main,"attachments/ui")

    @Test fun noReleaseSourceOutsideTheUiPackageReachesIt() {
        assertFalse(AttachmentFlowController.PUBLIC_ENTRY_ENABLED)
        val names=listOf("MedicalAttachmentsSection","MedicalAttachmentViewer","MedicalAttachmentsController","RuntimeAttachmentGateway","MedicalAttachmentGateway","medicalAttachmentsForRecord","MedicalAttachmentRecordPage","MedicalAttachmentRecordContent","MedicalLibraryTransferPage","MedicalLibraryTransferContent","MedicalLibraryTransferController")
        val reaching=main.walkTopDown().filter {it.isFile && it.extension=="kt" && !it.path.startsWith(ui.path)}
            .filter {f->val text=f.readText();names.any {it in text}}.map {it.path}.toList()
        assertEquals("Only the debug synthetic host may open the attachment UI",emptyList<String>(),reaching)
        val debugHost=File("src/debug/java/net/plainnotes/app/debug/attachments/MedicalAttachmentUiTestActivity.kt").readText()
        assertTrue(debugHost.contains("FLAG_SECURE") && debugHost.contains("SyntheticAttachmentFixture") && debugHost.contains("AttachmentOperationTestActivity.AUTHORITY"))
        val manifest=File("src/debug/AndroidManifest.xml").readText()
        assertTrue(Regex("""MedicalAttachmentUiTestActivity"\s+android:exported="false"""").containsMatchIn(manifest))
        assertFalse(File("src/main/AndroidManifest.xml").readText().contains("MedicalAttachmentUiTestActivity"))
        assertTrue(File("../scripts/check_attachment_release_isolation.py").readText().contains("b'MedicalAttachmentUiTestActivity'"))
    }

    @Test fun uiPackageHasNoStorageKeysPathsOrSecondDecryption() {
        val forbidden=listOf("androidx.room","SupportSQLite","java.io.File","KeyStore","Cipher","Tink","AttachmentPersistentStore","AttachmentObjectStreams",
            "AttachmentDeviceKeys","openFileDescriptor","takePersistableUriPermission","SharedPreferences","putParcelable","Bundle")
        ui.listFiles()!!.filter {it.extension=="kt"}.forEach {f->
            val text=f.readText();forbidden.forEach {assertFalse("${f.name} uses $it",it in text)}
        }
    }

    @Test fun wordingIsCalmAndHonestInAllLanguages() {
        val banned=Regex("(?i)secure|encrypt|guarantee|permanent|forever|absolut|絶対|绝对|絕對|永久|加密|安全|chiffr|sécuris|définitiv")
        for(dir in listOf("values","values-zh","values-b+zh+Hant","values-fr")) {
            val text=File("src/main/res/$dir/medical_attachments.xml").readText()
            Regex("""<string name="([^"]+)">([^<]*)</string>""").findAll(text).forEach {m->
                assertFalse("$dir ${m.groupValues[1]}: ${m.groupValues[2]}",banned.containsMatchIn(m.groupValues[2]))
            }
        }
    }
}
