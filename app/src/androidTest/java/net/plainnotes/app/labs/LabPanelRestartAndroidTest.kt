package net.plainnotes.app.labs

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * LAB-02, synthetic data in a persistent Room 12 database across two separately started app processes
 * (scripts/run_lab_panel_restart.py): a lab panel, a panel in the bin and an encrypted backup survive the restart, the bin
 * restores the panel whole and the backup restores the panels as they were.
 */
class LabPanelRestartAndroidTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "synthetic-lab-panel-restart"
    private fun database() = Room.databaseBuilder(context, NotesDatabase::class.java, name).addMigrations(*allMigrations()).allowMainThreadQueries().addCallback(SchemaGuards).build()
    private fun repo(db: NotesDatabase) = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = db })
    private val at = Instant.parse("2026-03-10T12:00:00Z"); private val t0 = at.minusSeconds(86400).toEpochMilli()
    private fun lab(code: String, v: Double, unit: String, t: Long = t0) = LabValueEntity(analyte_code = code, value = v, unit = unit, sampled_utc = t, sampled_zone = "UTC", laboratory = "Synthetic lab")
    private fun state(r: NotesRepository) = runBlocking { listOf(r.labs(), r.labPanels(), r.labContexts()).toString() }

    @Test fun prepare() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("lab_panel_phase") == "prepare")
        context.deleteDatabase(name)
        val db = database()
        try {
            val r = repo(db)
            r.saveLabs(listOf(lab("E2", 150.0, "pg/mL"), lab("T", 30.0, "ng/dL")), at)
            r.saveLabs(listOf(lab("PRL", 12.0, "ng/mL", t0 - 3_600_000)), at)
            assertEquals(14, db.openHelper.writableDatabase.version)
            File(context.filesDir, "synthetic-lab-panel-state").writeText(state(r))
            File(context.filesDir, "synthetic-lab-panel-backup.pnbak").writeBytes(r.exportBackup("synthetic-lab-pass".toCharArray()))
            r.deleteLabPanel(r.labPanels().first { it.sampled_utc == t0 }.id, at)
            File(context.filesDir, "synthetic-lab-panel-pid").writeText(android.os.Process.myPid().toString())
        } finally { db.close() }
    }

    @Test fun verifyFreshProcess() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("lab_panel_phase") == "verify")
        assertNotEquals(File(context.filesDir, "synthetic-lab-panel-pid").readText().toInt(), android.os.Process.myPid())
        val db = database()
        try {
            val r = repo(db)
            assertEquals(1, r.labPanels().size); assertEquals(1, r.labs().size)
            val item = r.trash().single(); assertEquals(Trash.LAB_PANEL, item.kind)
            r.restoreTrash(item.id, at)
            assertEquals(File(context.filesDir, "synthetic-lab-panel-state").readText(), state(r))
            r.deleteLabPanel(r.labPanels().first().id, at)
            r.restoreBackup(File(context.filesDir, "synthetic-lab-panel-backup.pnbak").readBytes(), "synthetic-lab-pass".toCharArray())
            assertEquals(File(context.filesDir, "synthetic-lab-panel-state").readText(), state(r))
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
