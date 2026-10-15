package net.plainnotes.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** PRIVACY-P1 licence guard: every runtime library declared by the shipped modules is named in THIRD_PARTY_NOTICES.md
 * and the in-app licence text; Bouncy Castle's MIT notice is reproduced verbatim (copyright line included) in both.
 * A new runtime dependency without a notice entry fails here. */
class ThirdPartyNoticesTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private fun read(path: String) = File(root, path).readText()
    private val notices = read("THIRD_PARTY_NOTICES.md")
    private val shipped = listOf("app", "core/data", "core/domain", "core/reminder", "core/ui", "importer", "pk-engine")
    /** Version-catalog alias of a runtime library → the name both notices must carry. */
    private val names = mapOf(
        "activity.compose" to "AndroidX", "appcompat" to "AndroidX", "biometric" to "AndroidX", "core.ktx" to "AndroidX",
        "lifecycle.compose" to "AndroidX", "lifecycle.viewmodel" to "AndroidX", "sqlite" to "AndroidX",
        "compose.bom" to "Compose", "compose.icons" to "Compose", "compose.material" to "Compose", "compose.preview" to "Compose", "compose.ui" to "Compose",
        "room.ktx" to "Room", "room.runtime" to "Room", "work" to "WorkManager", "coroutines" to "kotlinx.coroutines",
        "hilt.android" to "Hilt", "sqlcipher" to "SQLCipher", "bouncycastle" to "Bouncy Castle",
    )

    @Test fun everyRuntimeLibraryHasANotice() {
        val aliases = shipped.flatMap { m -> File(root, "$m/build.gradle.kts").takeIf { it.exists() }?.readText()?.let { s ->
            Regex("""\b(?:implementation|api|runtimeOnly)\((?:platform\()?libs\.([A-Za-z0-9.]+)""").findAll(s).map { it.groupValues[1] }.toList() } ?: emptyList() }.toSortedSet()
        assertTrue("Dependency scan found nothing; build layout changed", "bouncycastle" in aliases && "sqlcipher" in aliases)
        assertEquals("Runtime libraries without a notice mapping", emptyList<String>(), aliases.filterNot { it in names })
        val inApp = read("app/src/main/res/values/strings.xml").substringAfter("name=\"licenses_body\">").substringBefore("</string>")
        aliases.map { names.getValue(it) }.toSet().forEach { n ->
            assertTrue("$n missing from THIRD_PARTY_NOTICES.md", n in notices); assertTrue("$n missing from the in-app licence text", n in inApp)
        }
    }

    @Test fun bouncyCastleNoticeMatchesTheShippedArtifact() {
        val version = Regex("""bouncycastle = \{ module = "org.bouncycastle:bcprov-jdk18on", version = "([^"]+)" \}""").find(read("gradle/libs.versions.toml"))!!.groupValues[1]
        assertTrue("Notice names the exact artifact and version", "org.bouncycastle:bcprov-jdk18on:$version" in notices)
        val copyright = "Copyright (c) 2000-2026 The Legion of the Bouncy Castle Inc."
        val legal = read("app/src/main/res/values/legal_notices.xml")
        listOf(copyright, "Permission is hereby granted, free of charge", "shall be included in all copies or substantial portions of the Software",
            "IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE").forEach { line ->
            assertTrue("THIRD_PARTY_NOTICES.md: $line", line in notices); assertTrue("In-app notice: $line", line in legal)
        }
        for (locale in listOf("values", "values-zh", "values-b+zh+Hant", "values-fr"))
            assertTrue("$locale licence text names Bouncy Castle", "Bouncy Castle" in read("app/src/main/res/$locale/strings.xml").substringAfter("name=\"licenses_body\">").substringBefore("</string>"))
        assertTrue("Settings shows the notice", "R.string.licenses_bouncycastle_notice" in read("app/src/main/java/net/plainnotes/app/ui/SettingsScreen.kt"))
    }
}
