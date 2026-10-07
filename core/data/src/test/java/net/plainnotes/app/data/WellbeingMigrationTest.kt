package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.TimeZone

/**
 * Schema 1 -> 2 (REQUIREMENTS 15b): mood, energy and sleep merge into the new daily items; the other previous items stay
 * enabled only with a score in the last 30 days. Covers the Room migration, the restore of a synthetic schema-1 backup
 * written by the 0.2.0 code (`backup/v1-synthetic.pnbak`), and that both paths agree. Synthetic data only.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class WellbeingMigrationTest {
    private val today = LocalDate.parse("2026-10-01")
    private val dbName = "migration-test.db"
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NotesDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Before fun utc() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    private fun fixture(): ByteArray = javaClass.classLoader!!.getResourceAsStream("backup/v1-synthetic.pnbak")!!.readBytes()
    private fun fixtureTables(): JSONObject = JSONObject(String(BackupCodec.decrypt(fixture(), "synthetic-backup-pw".toCharArray()), Charsets.UTF_8)).also { assertEquals(1, it.getInt("schema")) }.getJSONObject("tables")

    /** Items as (key or label) -> (enabled, legacy, scores by date). */
    private fun wellbeing(db: SupportSQLiteDatabase): Map<String, Triple<Boolean, Boolean, Map<String, Int>>> {
        val items = mutableMapOf<Long, Triple<String, Boolean, Boolean>>()
        db.query("SELECT id, builtin_key, custom_label, enabled, legacy FROM checkin_item").use { c -> while (c.moveToNext())
            items[c.getLong(0)] = Triple(c.getString(1) ?: "custom:" + c.getString(2), c.getInt(3) != 0, c.getInt(4) != 0) }
        val scores = mutableMapOf<Long, MutableMap<String, Int>>()
        db.query("SELECT item_id, date, value FROM checkin_score").use { c -> while (c.moveToNext()) scores.getOrPut(c.getLong(0)) { mutableMapOf() }[c.getString(1)] = c.getInt(2) }
        return items.entries.associate { (id, t) -> t.first to Triple(t.second, t.third, scores[id].orEmpty().toMap()) }
    }

    private fun assertUpgraded(w: Map<String, Triple<Boolean, Boolean, Map<String, Int>>>) {
        // Merged: same item, new key, every score kept; the old keys are gone.
        assertEquals(mapOf("2026-09-28" to 4, "2026-06-01" to 2), w.getValue("DAY_MOOD").third)
        assertEquals(mapOf("2026-09-29" to 3), w.getValue("DAY_ENERGY").third)
        assertEquals(mapOf("2026-05-02" to 5), w.getValue("DAY_SLEEP").third)
        listOf("MOOD", "ENERGY", "SLEEP_QUALITY").forEach { assertFalse(it, it in w) }
        listOf("DAY_MOOD", "DAY_ENERGY", "DAY_SLEEP", "DAY_BODY").forEach { assertTrue(it, w.getValue(it).first); assertFalse(it, w.getValue(it).second) }
        // Previous items: enabled only with a score in the last 30 days (2026-09-01..2026-10-01).
        assertEquals(Triple(true, true, mapOf("2026-09-15" to 3)), w.getValue("LIBIDO"))
        assertEquals(Triple(true, true, mapOf("2026-09-02" to 4)), w.getValue("SKIN_QUALITY"))
        assertEquals(Triple(false, true, mapOf("2026-06-10" to 1)), w.getValue("PAIN"))
        listOf("OVERALL", "EMO_STABILITY", "AGGRESSIVENESS", "PERIOD_LIKE", "APPETITE").forEach { assertEquals(it, Triple(false, true, emptyMap<String, Int>()), w.getValue(it)) }
        // Custom items are untouched.
        assertEquals(Triple(true, false, mapOf("2026-09-30" to 5)), w.getValue("custom:Synthetic custom"))
    }

    private fun insertV1Wellbeing(db: SupportSQLiteDatabase, tables: JSONObject) {
        for (t in listOf("checkin_item", "checkin_score")) { val rows = tables.getJSONArray(t)
            for (i in 0 until rows.length()) { val r = rows.getJSONObject(i); val cols = r.keys().asSequence().toList()
                db.execSQL("INSERT INTO $t (${cols.joinToString()}) VALUES (${cols.joinToString { "?" }})", cols.map { k -> r.get(k).let { if (it == JSONObject.NULL) null else it } }.toTypedArray()) } }
    }

    @Test fun roomMigrationMergesAndHidesByRecentUse() {
        helper.createDatabase(dbName, 1).use { insertV1Wellbeing(it, fixtureTables()) }
        val db = helper.runMigrationsAndValidate(dbName, 2, true, migration1To2 { today })
        assertUpgraded(wellbeing(db))
        db.query("SELECT COUNT(*) FROM stage_review").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }

    @Test fun restoringA020BackupUpgradesItAndKeepsEverything() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val room = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build(); room.openHelper.writableDatabase
        val repo = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = room })
        repo.restoreBackup(fixture(), "synthetic-backup-pw".toCharArray(), today)
        assertUpgraded(wellbeing(room.openHelper.writableDatabase))
        val meds = repo.medications(); assertEquals(listOf("Synthetic estradiol"), meds.map { it.name })
        val records = repo.records().filter { it.deleted_at_utc == null }
        assertEquals(2, records.count { it.status == "ON_TIME" })
        assertEquals(56.0 + 56.0 - 3.0, repo.containers().sumOf { it.capacity - it.used_amount }, 1e-9)   // 2 mg taken + 1 mg unscheduled
        assertTrue(repo.containers().all { it.source_note == null && it.batch == null })
        assertEquals("Synthetic note", repo.notes(LocalDate.parse("2026-09-28"), LocalDate.parse("2026-09-28")).single().text)
        assertEquals(120.0, repo.labs().single().value, 0.0)
        assertEquals(1, repo.appointments().size)
        assertTrue(repo.stageReviews().isEmpty())
        // Same result as the Room migration path.
        helper.createDatabase(dbName, 1).use { insertV1Wellbeing(it, fixtureTables()) }
        val migrated = helper.runMigrationsAndValidate(dbName, 2, true, migration1To2 { today })
        assertEquals(wellbeing(migrated).mapValues { it.value }, wellbeing(room.openHelper.writableDatabase))
        room.close()
    }

    @Test fun schema2SymptomMigrationPreservesUnknownContext() {
        helper.createDatabase(dbName,2).use{it.execSQL("INSERT INTO symptom_check(date,group_id,note) VALUES ('2026-03-09','SYNTHETIC','old note')")}
        helper.runMigrationsAndValidate(dbName,3,true,migration2To3).use{db->
            db.query("SELECT note,context_snapshot FROM symptom_check").use{assertTrue(it.moveToFirst());assertEquals("old note",it.getString(0));assertTrue(it.isNull(1))}
        }
    }

    @Test fun aBackupFromANewerSchemaIsRefused() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val room = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build(); room.openHelper.writableDatabase
        val repo = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = room })
        val newer = JSONObject().put("format", BackupCodec.FORMAT_VERSION).put("schema", 99).put("tables", JSONObject()).toString().toByteArray()
        assertThrows(BackupCodec.NewerBackup::class.java) { runBlocking { repo.restoreBackup(BackupCodec.encrypt(newer, "synthetic-pw".toCharArray()), "synthetic-pw".toCharArray()) } }
        room.close()
    }
}
