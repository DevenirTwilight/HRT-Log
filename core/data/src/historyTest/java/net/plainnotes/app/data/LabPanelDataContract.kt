package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.util.UUID

/**
 * LAB-02 phase B (docs/design/lab-panel-adr.md), synthetic data only: Room 11→12 keeps every row and groups nothing; panel
 * edits keep members in step (new SAMPLE_CHANGED contexts, old ones frozen); a member edited on its own leaves (Q1); the bin
 * keeps whole panels together and relinks a single result only by panel uuid (Q9); backups round-trip panels, old ones restore
 * ungrouped and a v11 backup is refused by a v10 reader (Q8); the database refuses a member that differs from its panel.
 * Runs under Robolectric (LabPanelDataTest) and on devices API 26/29/35 (LabPanelDataAndroidTest).
 */
abstract class LabPanelDataContract {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    /** Exported schemas are test assets: the instrumentation APK on a device. */
    protected open val assetsContext: Context get() = context
    private val opened = mutableListOf<NotesDatabase>(); private val files = mutableListOf<String>()
    @After fun close() { opened.forEach { it.close() }; files.forEach { context.deleteDatabase(it) } }
    private val at = Instant.parse("2026-03-10T12:00:00Z")
    private val t0 = at.minusSeconds(86400).toEpochMilli()
    private fun memory(): Pair<NotesDatabase, NotesRepository> {
        val db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build().also { opened += it }
        return db to NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = db })
    }
    private fun lab(code: String, v: Double, t: Long = t0, place: String? = "Synthetic lab") = LabValueEntity(analyte_code = code, value = v,
        unit = mapOf("E2" to "pg/mL", "T" to "ng/dL", "PRL" to "ng/mL", "LH" to "IU/L").getValue(code), sampled_utc = t, sampled_zone = "UTC", laboratory = place)
    private suspend fun NotesRepository.batch() = saveLabs(listOf(lab("E2", 150.0), lab("T", 30.0), lab("PRL", 12.0)), at)
    private fun SupportSQLiteDatabase.count(sql: String, vararg args: Any?) = query(sql, args).use { it.moveToFirst(); it.getInt(0) }

    // --- Migration ---
    private fun v11(name: String) = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
        .callback(object : SupportSQLiteOpenHelper.Callback(11) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val definition = assetsContext.assets.open("net.plainnotes.app.data.NotesDatabase/11.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
                val entities = definition.getJSONArray("entities")
                for (i in 0 until entities.length()) { val e = entities.getJSONObject(i); val t = e.getString("tableName")
                    db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", t))
                    val ix = e.optJSONArray("indices") ?: JSONArray(); for (j in 0 until ix.length()) db.execSQL(ix.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", t)) }
                val setup = definition.getJSONArray("setupQueries"); for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("fixture only")
        }).build())
    private fun seed11(db: SupportSQLiteDatabase) {
        SchemaGuards.install(db) // Room 11 guards: no lab_panel yet, so no panel guard is installed
        db.execSQL("INSERT INTO lab_analyte(code,canonical_unit) VALUES('E2','pg/mL'),('T','ng/dL')")
        db.execSQL("INSERT INTO lab_value(id,analyte_code,value,unit,sampled_utc,sampled_zone,laboratory) VALUES(1,'E2',150,'pg/mL',$t0,'UTC','Synthetic lab'),(2,'T',30,'ng/dL',$t0,'UTC','Synthetic lab')")
        db.execSQL("INSERT INTO lab_context_revision(id,lab_id,revision,captured_utc,origin,context_json) VALUES(1,1,1,${t0 + 1},'AT_ENTRY','{\"synthetic\":1}')")
        val payload = Trash.payload("lab_value" to JSONArray().put(JSONObject().put("id", 9).put("analyte_code", "E2").put("value", 99.0).put("unit", "pg/mL")
            .put("sampled_utc", t0 - 86400000).put("sampled_zone", "UTC")), "lab_context_revision" to JSONArray())
        db.execSQL("INSERT INTO trash_item(id,kind,ref,item_date,deleted_utc,payload_json,state) VALUES(1,'LAB','9','2026-03-08',${t0 + 2},?,'TRASHED')", arrayOf(payload.toString()))
    }
    private fun rows(db: SupportSQLiteDatabase, table: String, columns: String = "*") = db.query("SELECT $columns FROM `$table` ORDER BY rowid").use { c ->
        buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { "${c.getColumnName(it)}=${c.getType(it)}:${c.getString(it)}" }) } }

    @Test fun room11To12KeepsEveryRowAndGroupsNothing() {
        val name = "synthetic-lab-panel-migration-${UUID.randomUUID()}.db"; files += name
        val before = v11(name).use { h -> val db = h.writableDatabase; seed11(db)
            listOf("lab_value" to "id,analyte_code,value,unit,sampled_utc,sampled_zone,reference_lower,reference_upper,reference_unit,laboratory,note",
                "lab_context_revision" to "*", "trash_item" to "*", "lab_analyte" to "*").associate { (t, c) -> t to rows(db, t, c) } }
        val room = Room.databaseBuilder(context, NotesDatabase::class.java, name).addMigrations(*allMigrations()).addCallback(SchemaGuards).allowMainThreadQueries().build().also { opened += it }
        val raw = room.openHelper.writableDatabase
        assertEquals(12, raw.version)
        before.forEach { (t, r) -> assertEquals(t, r, rows(raw, t, if (t == "lab_value") "id,analyte_code,value,unit,sampled_utc,sampled_zone,reference_lower,reference_upper,reference_unit,laboratory,note" else "*")) }
        assertEquals(0, raw.count("SELECT COUNT(*) FROM lab_value WHERE panel_id IS NOT NULL"))
        assertEquals(0, raw.count("SELECT COUNT(*) FROM lab_panel"))
        raw.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        // Same date and same laboratory are not grouped by the migration (ADR §2/§3).
        assertEquals(2, raw.count("SELECT COUNT(*) FROM lab_value WHERE sampled_utc=? AND laboratory='Synthetic lab'", t0))
        try { raw.execSQL("UPDATE lab_context_revision SET context_json='{}' WHERE id=1"); fail("frozen context rewritten") } catch (_: android.database.SQLException) {}
    }

    @Test fun aFailedRoom11To12MigrationLeavesRoom11() {
        val name = "synthetic-lab-panel-failed-${UUID.randomUUID()}.db"; files += name
        v11(name).use { seed11(it.writableDatabase) }
        val broken = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) { migration11To12.migrate(db); error("SYNTHETIC lab panel migration interrupted") }
        }
        Room.databaseBuilder(context, NotesDatabase::class.java, name).addMigrations(*allMigrations().filter { it.endVersion != 12 }.toTypedArray(), broken).build().let { room ->
            try { room.openHelper.writableDatabase; fail("interrupted migration succeeded") } catch (e: IllegalStateException) { assertTrue(e.message.orEmpty().contains("SYNTHETIC")) }
            room.close()
        }
        v11(name).use { h -> val db = h.writableDatabase
            assertEquals(11, db.version); assertEquals(0, db.count("SELECT COUNT(*) FROM sqlite_master WHERE name='lab_panel'")); assertEquals(2, db.count("SELECT COUNT(*) FROM lab_value")) }
    }

    // --- Saving and editing ---
    @Test fun batchMakesOnePanelAndThePanelEditKeepsMembersInStep() = runBlocking {
        val (db, repo) = memory(); repo.batch()
        val p = repo.labPanels().single(); val members = repo.labs()
        assertTrue(members.all { it.panel_id == p.id }); assertEquals(36, p.uuid.length); assertEquals("Synthetic lab", p.laboratory)
        val contexts = repo.labContexts()
        repo.saveLabPanel(p.id, t0 + 3_600_000, "Europe/Paris", "  Other lab ", " Synthetic fasting note ", at.plusSeconds(1))
        val q = repo.labPanels().single(); assertEquals(Triple(t0 + 3_600_000, "Europe/Paris", "Other lab"), Triple(q.sampled_utc, q.sampled_zone, q.laboratory)); assertEquals("Synthetic fasting note", q.note)
        assertTrue(repo.labs().all { it.sampled_utc == q.sampled_utc && it.sampled_zone == q.sampled_zone && it.laboratory == q.laboratory && it.panel_id == q.id })
        val after = repo.labContexts()
        assertEquals(contexts, after.filter { it in contexts }) // old revisions frozen
        assertEquals(3, after.count { it.origin == "SAMPLE_CHANGED" })
        repo.saveLabPanel(p.id, q.sampled_utc, q.sampled_zone, "Third lab", null, at.plusSeconds(2))
        assertEquals(after.size, repo.labContexts().size) // laboratory only: no new context, as a single edit
        assertNull(repo.labPanels().single().note)
    }

    @Test fun aMemberEditedOnItsOwnLeavesOnlyWhenTimeOrLaboratoryChanges() = runBlocking {
        val (_, repo) = memory(); repo.batch(); val p = repo.labPanels().single()
        val e2 = repo.labs().first { it.analyte_code == "E2" }
        // The single dialog does not carry panel_id: a value-only edit keeps the result in its panel.
        repo.saveLab(e2.copy(value = 160.0, panel_id = null), at)
        assertEquals(p.id, repo.labs().first { it.id == e2.id }.panel_id)
        repo.saveLab(e2.copy(value = 160.0, laboratory = " Synthetic lab ", panel_id = null), at) // whitespace only: stays
        assertEquals(p.id, repo.labs().first { it.id == e2.id }.panel_id)
        repo.saveLab(e2.copy(value = 160.0, sampled_utc = t0 + 60_000, panel_id = null), at) // Q1
        assertNull(repo.labs().first { it.id == e2.id }.panel_id)
        assertEquals(2, repo.labs().count { it.panel_id == p.id })
        val t = repo.labs().first { it.analyte_code == "T" }
        repo.saveLab(t.copy(laboratory = "Other lab"), at); repo.removeFromLabPanel(repo.labs().first { it.analyte_code == "PRL" }.id)
        assertTrue("an emptied panel is removed", repo.labPanels().isEmpty())
        assertTrue(repo.labs().all { it.panel_id == null }); assertEquals(3, repo.labs().size)
    }

    @Test fun addingTakesThePanelSampleAndTheDatabaseRefusesADriftingMember() = runBlocking {
        val (db, repo) = memory(); repo.batch(); val p = repo.labPanels().single()
        repo.addToLabPanel(p.id, listOf(lab("LH", 5.0, t = 1, place = null)), at)
        val lh = repo.labs().single { it.analyte_code == "LH" }
        assertEquals(Triple(p.sampled_utc, p.sampled_zone, p.laboratory), Triple(lh.sampled_utc, lh.sampled_zone, lh.laboratory)); assertEquals(p.id, lh.panel_id)
        assertEquals("AT_ENTRY", repo.labContexts().last().origin)
        val raw = db.openHelper.writableDatabase
        fun refused(sql: String, vararg a: Any?) = try { raw.execSQL(sql, a); false } catch (_: android.database.SQLException) { true }
        assertTrue(refused("UPDATE lab_value SET sampled_utc=sampled_utc+1 WHERE id=?", lh.id))
        assertTrue(refused("UPDATE lab_value SET laboratory=NULL WHERE id=?", lh.id))
        assertTrue(refused("DELETE FROM lab_panel WHERE id=?", p.id))
        assertTrue(refused("INSERT INTO lab_panel(uuid,sampled_utc,sampled_zone,laboratory,created_utc) VALUES(?,1,'UTC','  ',1)", UUID.randomUUID().toString()))
    }

    @Test fun saveAsOneBloodTestOnlyForUngroupedResultsOfOneExactSample() = runBlocking {
        val (_, repo) = memory()
        listOf(lab("E2", 150.0), lab("T", 30.0, place = " Synthetic lab"), lab("PRL", 12.0, t = t0 + 60_000), lab("LH", 5.0, place = "Other lab")).forEach { repo.saveLab(it, at) }
        val ls = repo.labs(); fun id(c: String) = ls.first { it.analyte_code == c }.id
        val contexts = repo.labContexts()
        for (bad in listOf(listOf(id("E2")), listOf(id("E2"), id("PRL")), listOf(id("E2"), id("LH")))) try { repo.groupAsLabPanel(bad, at); fail("$bad grouped") } catch (_: IllegalArgumentException) {}
        repo.groupAsLabPanel(listOf(id("E2"), id("T")), at)
        val p = repo.labPanels().single(); assertEquals("Synthetic lab", p.laboratory)
        assertEquals(setOf(id("E2"), id("T")), repo.labs().filter { it.panel_id == p.id }.map { it.id }.toSet())
        assertEquals(contexts, repo.labContexts()) // the sample did not change
        try { repo.groupAsLabPanel(listOf(id("E2"), id("T")), at); fail("regrouped") } catch (_: IllegalArgumentException) {}
    }

    // --- Recycle bin ---
    @Test fun aWholePanelGoesToTheBinAndComesBackTogether() = runBlocking {
        val (_, repo) = memory(); repo.batch(); val p = repo.labPanels().single()
        val labs = repo.labs(); val contexts = repo.labContexts()
        repo.deleteLabPanel(p.id, at)
        assertTrue(repo.labs().isEmpty()); assertTrue(repo.labPanels().isEmpty()); assertTrue(repo.labContexts().isEmpty())
        val item = repo.trash().single(); assertEquals(Trash.LAB_PANEL, item.kind); assertEquals(p.uuid, item.ref)
        repo.restoreTrash(item.id, at)
        assertEquals(listOf(p), repo.labPanels()); assertEquals(labs, repo.labs()); assertEquals(contexts, repo.labContexts()); assertTrue(repo.trash().isEmpty())
        repo.deleteLabPanel(p.id, at); assertTrue(repo.purgeTrash(repo.trash().single().id))
        assertTrue(repo.labs().isEmpty()); assertTrue(repo.labPanels().isEmpty()); assertTrue(repo.trash().isEmpty())
    }

    @Test fun aSingleResultReturnsToItsPanelByUuidOrOnItsOwn() = runBlocking {
        val (_, repo) = memory(); repo.batch(); val p = repo.labPanels().single()
        val e2 = repo.labs().first { it.analyte_code == "E2" }
        repo.deleteLab(e2.id, at); assertEquals(2, repo.labs().count { it.panel_id == p.id })
        assertEquals(p.uuid, JSONObject(repo.trash().single().payload_json).getString("panel_uuid"))
        repo.restoreTrash(repo.trash().single().id, at)
        assertEquals(p.id, repo.labs().first { it.id == e2.id }.panel_id)
        // The panel moved meanwhile: the result comes back on its own.
        repo.deleteLab(e2.id, at); repo.saveLabPanel(p.id, t0 + 7_200_000, "UTC", p.laboratory, null, at)
        repo.restoreTrash(repo.trash().single().id, at)
        assertNull(repo.labs().first { it.id == e2.id }.panel_id); assertEquals(t0, repo.labs().first { it.id == e2.id }.sampled_utc)
    }

    @Test fun theLastResultTakesItsEmptiedPanelAlong() = runBlocking {
        val (_, repo) = memory(); repo.saveLabs(listOf(lab("E2", 150.0)), at); val p = repo.labPanels().single(); val e2 = repo.labs().single()
        repo.deleteLab(e2.id, at)
        assertTrue(repo.labPanels().isEmpty()); assertEquals(listOf("lab_panel", "lab_value", "lab_context_revision"),
            JSONObject(repo.trash().single().payload_json).getJSONArray("order").let { a -> (0 until a.length()).map(a::getString) })
        repo.restoreTrash(repo.trash().single().id, at)
        assertEquals(listOf(p), repo.labPanels()); assertEquals(listOf(e2), repo.labs())
    }

    // --- Backup ---
    @Test fun backupsRoundTripPanelsOldOnesRestoreUngroupedAndAnOldReaderRefusesNewOnes() = runBlocking {
        val (db, repo) = memory(); repo.batch(); repo.saveLab(lab("LH", 5.0, t = t0 - 3_600_000), at)
        val pw = "synthetic-pass".toCharArray()
        val labs = repo.labs(); val panels = repo.labPanels(); val contexts = repo.labContexts()
        val backup = repo.exportBackup(pw)
        val json = JSONObject(String(BackupCodec.decrypt(backup, pw), Charsets.UTF_8))
        assertEquals(11, json.getInt("schema")); assertTrue(json.getJSONObject("tables").has("lab_panel"))
        try { RecordBackupCompatibility.readVersion(json, supported = 10); fail("a Room 11 reader accepted a panel backup") } catch (_: BackupCodec.NewerBackup) {}
        repo.deleteLabPanel(panels.single().id, at)
        repo.restoreBackup(backup, pw)
        assertEquals(labs, repo.labs()); assertEquals(panels, repo.labPanels()); assertEquals(contexts, repo.labContexts())
        // The same data written as a schema 10 backup (no lab_panel, no panel_id) restores with no panel.
        val old = JSONObject(json.toString()).put("schema", 10).also { o -> val t = o.getJSONObject("tables"); t.remove("lab_panel")
            val v = t.getJSONArray("lab_value"); for (i in 0 until v.length()) v.getJSONObject(i).remove("panel_id") }
        repo.restoreBackup(BackupCodec.encrypt(old.toString().toByteArray(), pw), pw)
        assertEquals(labs.map { it.copy(panel_id = null) }, repo.labs()); assertTrue(repo.labPanels().isEmpty())
        // A schema 10 backup that claims panels is refused; nothing changes.
        val claims = JSONObject(json.toString()).put("schema", 10)
        try { repo.restoreBackup(BackupCodec.encrypt(claims.toString().toByteArray(), pw), pw); fail("old backup with panels restored") } catch (_: IllegalArgumentException) {}
        assertEquals(labs.map { it.copy(panel_id = null) }, repo.labs())
        // A new backup whose member differs from its panel is refused, and the current data stays.
        val drift = JSONObject(json.toString()).also { o -> val v = o.getJSONObject("tables").getJSONArray("lab_value")
            for (i in 0 until v.length()) if (!v.getJSONObject(i).isNull("panel_id")) { v.getJSONObject(i).put("sampled_utc", 1L); break } }
        try { repo.restoreBackup(BackupCodec.encrypt(drift.toString().toByteArray(), pw), pw); fail("inconsistent panel restored") } catch (_: Exception) {}
        assertEquals(labs.map { it.copy(panel_id = null) }, repo.labs()); assertTrue(repo.labPanels().isEmpty())
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    @Test fun legacyStringIsTheRoom11Text() {
        val v = LabValueEntity(7, "E2", 150.0, "pg/mL", 1L, "UTC", null, 200.0, "pg/mL", "Synthetic lab", null, panel_id = 3)
        assertEquals("LabValueEntity(id=7, analyte_code=E2, value=150.0, unit=pg/mL, sampled_utc=1, sampled_zone=UTC, reference_lower=null, reference_upper=200.0, " +
            "reference_unit=pg/mL, laboratory=Synthetic lab, note=null)", v.legacyString())
        assertEquals(v.copy(panel_id = null).toString().replace(", panel_id=null)", ")"), v.legacyString())
    }
    // LAB-03: synthetic report vocabulary only; no personal report or medical recommendation.
    @Test fun expandedReportKeepsEveryOriginalUnitPanelContextAndBackupThroughTrash() = runBlocking {
        val (db,repo)=memory()
        val examples=mapOf("E2" to (210.0 to "pg/mL"),"T" to (9.0 to "ng/dL"),"LH" to (0.4 to "mIU/mL"),
            "FSH" to (0.2 to "mIU/mL"),"ALT" to (54.0 to "U/L"),"AST" to (29.0 to "U/L"),
            "TG" to (3.05 to "g/L"),"GLU_FASTING" to (0.95 to "g/L"),"HB" to (13.8 to "g/dL"),
            "HCT" to (40.1 to "%"),"RBC" to (4.43 to "millions/mm³"),"PLT" to (224.0 to "× 10³/mm³"),"HBA1C" to (5.4 to "%"))
        val source=net.plainnotes.app.domain.LabCatalog.all.map{definition->
            val v=examples[definition.code] ?: (12.5 to definition.canonicalUnit)
            LabValueEntity(analyte_code=definition.code,value=v.first,unit=v.second,sampled_utc=t0,sampled_zone="Europe/Paris",
                reference_upper=if(definition.code=="ALT")31.0 else null,reference_unit=if(definition.code=="ALT")v.second else null,
                laboratory="SYNTHETIC laboratoire",note=when(definition.code){
                    "E2"->"SYNTHETIC primary 210 pg/mL; secondary rounded 771 pmol/L, same result"
                    "T"->"SYNTHETIC primary 9 ng/dL; secondary rounded 0 nmol/L is not a measured zero"
                    "EGFR"->"SYNTHETIC report value; formula/version unknown; no app calculation"
                    else->"SYNTHETIC assay/instrument unknown; report date differs from sample date"
                })
        }
        assertEquals(45,source.size)
        repo.saveLabs(source,at)
        val saved=repo.labs();val panel=repo.labPanels().single();val contexts=repo.labContexts()
        assertEquals(source,saved.map{it.copy(id=0,panel_id=null)})
        assertTrue(saved.all{it.panel_id==panel.id});assertEquals(45,contexts.size)
        assertEquals(1,saved.count{it.analyte_code=="E2"});assertEquals(9.0,saved.single{it.analyte_code=="T"}.value,0.0)
        contexts.forEach{val captured=JSONObject(it.context_json);assertTrue(captured.has("estimate"));assertTrue(captured.isNull("estimate"))}
        val password="synthetic-lab03-backup".toCharArray()
        val archive=repo.exportBackup(password)
        try {
            repo.deleteLabPanel(panel.id,at)
            assertTrue(repo.labs().isEmpty());assertTrue(repo.labContexts().isEmpty())
            repo.restoreTrash(repo.trash().single().id,at)
            assertEquals(saved,repo.labs());assertEquals(contexts,repo.labContexts());assertEquals(listOf(panel),repo.labPanels())
            repo.restoreBackup(archive,password)
            assertEquals(saved,repo.labs());assertEquals(contexts,repo.labContexts());assertEquals(listOf(panel),repo.labPanels())
            assertEquals(12,db.openHelper.writableDatabase.version);assertEquals(11,RecordBackupCompatibility.SCHEMA)
        }finally{password.fill('\u0000');archive.fill(0)}
    }

    @Test fun expandedReportFailureKeepsOldPanelAndUnknownUnitsWithoutPartialWrites()=runBlocking {
        val (db,repo)=memory();repo.batch()
        repo.saveLab(LabValueEntity(analyte_code="SYNTHETIC_UNKNOWN",value=7.5,unit="opaque-unit",sampled_utc=t0,sampled_zone="UTC",
            reference_upper=10.0,reference_unit="opaque-unit",note="SYNTHETIC original name and unknown method"),at)
        val before=RawData.dump(db.openHelper.writableDatabase).toString()
        val good=LabValueEntity(analyte_code="TC",value=2.1,unit="g/L",sampled_utc=t0,sampled_zone="UTC")
        try{repo.saveLabs(listOf(good,good.copy(analyte_code="TG",unit="")),at);fail("Part of invalid report saved")}
        catch(_:IllegalArgumentException){}
        assertEquals(before,RawData.dump(db.openHelper.writableDatabase).toString())
        assertEquals(7.5,repo.labs().single{it.analyte_code=="SYNTHETIC_UNKNOWN"}.value,0.0)
    }

}
