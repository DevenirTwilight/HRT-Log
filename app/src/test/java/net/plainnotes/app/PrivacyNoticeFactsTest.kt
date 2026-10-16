package net.plainnotes.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** PRIVACY-P0 guard: the privacy notice states concrete facts; if the code behind one changes, this test fails so
 * the notice (strings, PRIVACY.md, docs/privacy-evidence.md) is updated in the same change. Source-level checks on
 * this repository; the merged release manifest is also checked in CI by scripts/check_release_manifest.py. */
class PrivacyNoticeFactsTest {
    private fun src(path: String) = File(path).takeIf { it.exists() }?.readText() ?: File("../$path").readText()
    private val locales = listOf("values", "values-fr", "values-zh", "values-b+zh+Hant")
    private fun string(locale: String, name: String): String =
        Regex("""<string name="$name">(.*?)</string>""").find(File("src/main/res/$locale/strings.xml").readText())!!.groupValues[1]

    @Test fun backupKdfAndCipherMatchTheNotice() {
        val codec = src("core/data/src/main/java/net/plainnotes/app/data/DataTransfer.kt")
        assertTrue(codec.contains("ARGON2_id")); assertTrue(codec.contains("withMemoryAsKB(32 * 1024)")); assertTrue(codec.contains("withIterations(3)"))
        assertTrue(codec.contains("AES/GCM/NoPadding"))
        for (l in locales) { val body = string(l, "privacy_notice_backup_body")
            assertTrue(l, body.contains("Argon2id") && body.contains("32 Mi") && body.contains("AES-256-GCM") && body.contains("3")) }
        assertEquals("Structured backup serialization is explicitly 10, independent of Room11", true, src("core/data/src/main/java/net/plainnotes/app/data/RecordBackupCompatibility.kt").contains("const val SCHEMA = 10"))
    }

    @Test fun noInternetPermissionAndSystemBackupOff() {
        val manifests = listOf("app/src/main/AndroidManifest.xml", "app/src/full/AndroidManifest.xml",
            "core/data/src/main/AndroidManifest.xml", "core/reminder/src/main/AndroidManifest.xml", "core/ui/src/main/AndroidManifest.xml").map(::src)
        assertTrue(manifests.none { it.contains("android.permission.INTERNET") })
        val app = manifests[0]
        assertTrue(app.contains("""android:allowBackup="false""""))
        val rules = src("app/src/main/res/xml/data_extraction_rules.xml")
        assertTrue(rules.contains("cloud-backup") && rules.contains("device-transfer"))
        for (l in locales) assertTrue(l, string(l, "privacy_notice_device_body").isNotBlank())
    }

    @Test fun notesShellTextIsDisclosedAsUnencrypted() {
        // The Notes disguise shell keeps its text in plain SharedPreferences; the notice must say so.
        assertTrue(src("app/src/full/java/net/plainnotes/app/disguise/Shells.kt").contains("shell_notes"))
        assertTrue(string("values", "privacy_notice_storage_body").contains("not encrypted"))
    }
}
