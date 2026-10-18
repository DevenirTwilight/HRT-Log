package net.plainnotes.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * LAB-02 phase A (test-only draft, docs/design/lab-panel-adr.md §2, §5, §6): on a synthetic database built from the exported
 * v11 schema, the draft 11→12 SQL only adds lab_panel and a nullable lab_value.panel_id. Every existing row (values, frozen
 * contexts, trash payloads, stable identities) is unchanged, panel_id is null everywhere, foreign keys stay intact, and the
 * draft consistency trigger refuses a member whose time or laboratory differs from its panel. Nothing here is production code.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [28])
class LabPanelDraftTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val name = "synthetic-lab-panel-draft-${UUID.randomUUID()}.db"
    @After fun clean() { context.deleteDatabase(name) }

    private fun schema(version: Int) = context.assets.open("net.plainnotes.app.data.NotesDatabase/$version.json").bufferedReader()
        .use { JSONObject(it.readText()).getJSONObject("database") }
    private fun fixture() = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
        .callback(object : SupportSQLiteOpenHelper.Callback(11) {
            override fun onConfigure(db: SupportSQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
            override fun onCreate(db: SupportSQLiteDatabase) {
                val definition = schema(11); val entities = definition.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val e = entities.getJSONObject(i); val table = e.getString("tableName")
                    db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = e.optJSONArray("indices") ?: JSONArray()
                    for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
                val setup = definition.getJSONArray("setupQueries"); for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("fixture only")
        }).build())

    private fun seed(db: SupportSQLiteDatabase) {
        SchemaGuards.install(db)
        db.execSQL("INSERT INTO lab_analyte(code,canonical_unit) VALUES('E2','pg/mL'),('T','ng/dL'),('PRL','ng/mL')")
        listOf(Triple(1, "E2", 150.0), Triple(2, "T", 30.0), Triple(3, "PRL", 12.0)).forEach { (id, code, v) ->
            db.execSQL("INSERT INTO lab_value(id,analyte_code,value,unit,sampled_utc,sampled_zone,laboratory,note) VALUES(?,?,?,?,?,?,?,?)",
                arrayOf<Any?>(id, code, v, if (code == "E2") "pg/mL" else if (code == "T") "ng/dL" else "ng/mL", 1_773_000_000_000L, "UTC", "Synthetic lab", null))
        }
        db.execSQL("INSERT INTO lab_context_revision(id,lab_id,revision,captured_utc,origin,context_json) VALUES(1,1,1,1773000000001,'AT_ENTRY','{\"synthetic\":1}')")
        // A lab already in the bin: a snapshot payload, rows only in the payload (Trash.kt format).
        val payload = JSONObject().put("order", JSONArray().put("lab_value").put("lab_context_revision")).put("tables", JSONObject()
            .put("lab_value", JSONArray().put(JSONObject().put("id", 9).put("analyte_code", "E2").put("value", 99.0).put("unit", "pg/mL")
                .put("sampled_utc", 1_772_000_000_000L).put("sampled_zone", "UTC")))
            .put("lab_context_revision", JSONArray()))
        db.execSQL("INSERT INTO trash_item(id,kind,ref,item_date,deleted_utc,payload_json,state) VALUES(1,'LAB','9','2026-02-25',1773000000002,?,'TRASHED')", arrayOf(payload.toString()))
    }

    /** Every row of every v11 table, with types, in rowid order. */
    private fun snapshot(db: SupportSQLiteDatabase, dropPanelColumn: Boolean = false): String {
        val tables = schema(11).getJSONArray("entities").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("tableName") } }
        return JSONObject().apply { tables.forEach { t ->
            val rows = JSONArray()
            db.query("SELECT * FROM `$t` ORDER BY rowid").use { c -> while (c.moveToNext()) rows.put(JSONObject().apply {
                for (i in 0 until c.columnCount) if (!(dropPanelColumn && t == "lab_value" && c.getColumnName(i) == "panel_id"))
                    put(c.getColumnName(i), if (c.isNull(i)) "NULL" else "${c.getType(i)}:${c.getString(i)}")
            }) }
            put(t, rows)
        } }.toString()
    }

    @Test fun draftMigrationKeepsEveryRowAndLeavesPanelIdNull() {
        fixture().use { helper ->
            val db = helper.writableDatabase; seed(db)
            val before = snapshot(db)
            db.beginTransaction(); try { LabPanelDraft.migrate11To12(db); db.setTransactionSuccessful() } finally { db.endTransaction() }
            assertEquals(before, snapshot(db, dropPanelColumn = true))
            db.query("SELECT COUNT(*) FROM lab_value WHERE panel_id IS NOT NULL").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            db.query("SELECT COUNT(*) FROM lab_panel").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            db.query("PRAGMA foreign_key_list(lab_value)").use { c -> val fks = buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("table")) + ":" + c.getString(c.getColumnIndexOrThrow("on_delete"))) }
                assertTrue(fks.toString(), "lab_panel:RESTRICT" in fks); assertTrue(fks.toString(), "lab_analyte:RESTRICT" in fks) }
            // The frozen-context guard still holds after the migration.
            try { db.execSQL("UPDATE lab_context_revision SET context_json='{}' WHERE id=1"); fail("context rewritten") } catch (_: android.database.SQLException) {}
        }
    }

    @Test fun aFailingDraftMigrationRollsBackToV11() {
        fixture().use { helper ->
            val db = helper.writableDatabase; seed(db)
            val before = snapshot(db)
            db.beginTransaction()
            try { LabPanelDraft.migrate11To12(db); db.execSQL("SELECT RAISE(ABORT,'synthetic failure')") } catch (_: android.database.SQLException) {} finally { db.endTransaction() }
            assertEquals(before, snapshot(db))
            db.query("SELECT COUNT(*) FROM sqlite_master WHERE name='lab_panel'").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun draftGuardKeepsMembersConsistentWithTheirPanelAndRestrictBlocksOrphans() {
        fixture().use { helper ->
            val db = helper.writableDatabase; seed(db)
            LabPanelDraft.migrate11To12(db); LabPanelDraft.installGuards(db)
            db.execSQL("INSERT INTO lab_panel(id,uuid,sampled_utc,sampled_zone,laboratory,note,created_utc) VALUES(1,?,1773000000000,'UTC','Synthetic lab',NULL,1773000000005)", arrayOf(UUID.randomUUID().toString()))
            db.execSQL("UPDATE lab_value SET panel_id=1 WHERE id IN (1,2)") // same time, zone and laboratory: accepted
            fun refused(sql: String) = try { db.execSQL(sql); false } catch (_: android.database.SQLException) { true }
            assertTrue(refused("UPDATE lab_value SET sampled_utc=sampled_utc+60000 WHERE id=1")) // a member cannot drift from its panel
            assertTrue(refused("UPDATE lab_value SET laboratory='Other lab' WHERE id=2"))
            assertTrue(refused("INSERT INTO lab_value(analyte_code,value,unit,sampled_utc,sampled_zone,laboratory,panel_id) VALUES('E2',1,'pg/mL',1,'UTC','Synthetic lab',1)"))
            assertTrue(refused("DELETE FROM lab_panel WHERE id=1")) // RESTRICT: members first
            // Leaving the panel (panel_id NULL) then changing the time is allowed (ADR Q1).
            db.execSQL("UPDATE lab_value SET panel_id=NULL, sampled_utc=sampled_utc+60000 WHERE id=1")
            db.execSQL("UPDATE lab_value SET panel_id=NULL WHERE id=2"); db.execSQL("DELETE FROM lab_panel WHERE id=1")
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    @Test fun draftBackupVersionIsRejectedByTheCurrentReaderAndOldBackupsHaveNoPanels() {
        val draft = JSONObject().put("format", BackupCodec.FORMAT_VERSION).put("schema", LabPanelDraft.BACKUP_SCHEMA)
        try { RecordBackupCompatibility.readVersion(draft); fail("a v11 backup must be refused by today's reader") } catch (_: BackupCodec.NewerBackup) {}
        assertEquals(RecordBackupCompatibility.SCHEMA + 1, LabPanelDraft.BACKUP_SCHEMA)
        // The draft column lists only add: every current table and column stays, lab_panel is new, lab_value gains panel_id.
        val current = RecordBackupCompatibility.columns; val next = LabPanelDraft.backupColumns(current)
        current.forEach { (t, cols) -> assertTrue(t, next.getValue(t).containsAll(cols)) }
        assertEquals(current.getValue("lab_value") + "panel_id", next.getValue("lab_value"))
        assertEquals(listOf("id", "uuid", "sampled_utc", "sampled_zone", "laboratory", "note", "created_utc"), next.getValue("lab_panel"))
        // An old backup row has no panel_id: read with the draft list it is null (no regrouping by time, laboratory or integer id).
        val oldRow = JSONObject().put("id", 1).put("analyte_code", "E2").put("value", 1.0)
        assertTrue(oldRow.opt("panel_id") == null)
    }
}

/** LAB-02 phase A draft only; the production migration is written in phase B from docs/design/lab-panel-adr.md. */
internal object LabPanelDraft {
    const val BACKUP_SCHEMA = 11
    fun migrate11To12(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `lab_panel` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `sampled_utc` INTEGER NOT NULL, `sampled_zone` TEXT NOT NULL, `laboratory` TEXT, `note` TEXT, `created_utc` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lab_panel_uuid` ON `lab_panel` (`uuid`)")
        db.execSQL("ALTER TABLE `lab_value` ADD COLUMN `panel_id` INTEGER REFERENCES `lab_panel`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lab_value_panel_id` ON `lab_value` (`panel_id`)")
    }
    private const val consistent = "NEW.panel_id IS NULL OR EXISTS (SELECT 1 FROM lab_panel p WHERE p.id=NEW.panel_id AND p.sampled_utc=NEW.sampled_utc AND p.sampled_zone=NEW.sampled_zone AND p.laboratory IS NEW.laboratory)"
    fun installGuards(db: SupportSQLiteDatabase) = listOf("INSERT", "UPDATE").forEach { op ->
        db.execSQL("DROP TRIGGER IF EXISTS draft_lab_panel_member_${op.lowercase()}")
        db.execSQL("CREATE TRIGGER draft_lab_panel_member_${op.lowercase()} BEFORE $op ON lab_value BEGIN SELECT CASE WHEN NOT ($consistent) THEN RAISE(ABORT,'Lab panel member differs from its panel') END; END")
    }
    fun backupColumns(current: Map<String, List<String>>) = current.mapValues { (t, c) -> if (t == "lab_value") c + "panel_id" else c } +
        ("lab_panel" to listOf("id", "uuid", "sampled_utc", "sampled_zone", "laboratory", "note", "created_utc"))
}
