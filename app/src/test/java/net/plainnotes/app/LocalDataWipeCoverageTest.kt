package net.plainnotes.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** PRIVACY-P1 source guard: every SharedPreferences store the app writes is cleared by "Delete all data". A new store,
 * or a wipe step that is dropped, fails here until wipeAll handles it or the store is listed with a reason. */
class LocalDataWipeCoverageTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val sources = listOf("app/src/main", "app/src/full", "core/reminder/src/main", "core/data/src/main")
        .flatMap { File(root, it).walkTopDown().filter { f -> f.extension in setOf("kt", "java") }.toList() }
    private val wipeAll: String = File(root, "app/src/main/java/net/plainnotes/app/NotesViewModel.kt").readText().let { s ->
        val start = s.indexOf("fun wipeAll("); check(start >= 0); s.substring(start, s.indexOf("\n    fun ", start + 1))
    }
    /** How wipeAll clears a store that it does not name literally. */
    private val clearedBy = mapOf(
        "notify" to "NotificationPrefs(app).clear()",
        "stock_alerts" to "StockAlerts.clear(app)",
        "reminder_test" to "ReminderTestLog(app).clear()",
    )

    @Test fun everyPreferenceStoreIsClearedByDeleteAllData() {
        // Literal names, plus a constant declared in the same file (`const val PREFS = "..."`).
        val names = sources.flatMap { f -> val text = f.readText()
            val consts = Regex("""const val (\w+)\s*=\s*"([^"]+)"""").findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
            Regex("""getSharedPreferences\(\s*(?:"([^"]+)"|(\w+))""").findAll(text).map { m ->
                m.groupValues[1].ifEmpty { consts[m.groupValues[2]] ?: error("Unresolved store name ${m.groupValues[2]} in ${f.name}") } }.toList()
        }.toSortedSet()
        assertTrue("Scan found no stores; source layout changed", names.containsAll(listOf("prefs", "shell_notes", "notify", "stock_alerts")))
        val missing = names.filterNot { n -> "\"$n\"" in wipeAll || clearedBy[n]?.let { it in wipeAll } == true }
        assertEquals("Stores not cleared by wipeAll", emptyList<String>(), missing)
    }

    @Test fun stockAlertsAreClearedBeforeTheDatabaseAndVerifiedAtTheEnd() {
        val first = wipeAll.indexOf("StockAlerts.clear(app)"); val db = wipeAll.indexOf("repo.destroyAll()")
        assertTrue("Clear stock alerts before destroying the database", first in 0 until db)
        assertTrue("Verify the store is empty before reporting success", wipeAll.indexOf("StockAlerts.isClear(app)") > wipeAll.indexOf("reminders.sync()"))
    }
}
