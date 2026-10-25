package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import net.plainnotes.app.importer.Mona
import net.plainnotes.app.importer.MonaMigration
import org.json.JSONObject
import org.json.JSONArray

/** Additive Room18 source receipts. No old identity is inferred or backfilled. */
internal object ExternalImportSchema {
    const val TABLE="external_import_item"
    val columns=listOf("source_app","source_kind","source_id","source_part","source_schema","app_version","export_date",
        "source_digest","source_json","target_digest","record_id","lab_id","panel_uuid","imported_utc","retention")
    private val create=listOf(
        """CREATE TABLE IF NOT EXISTS external_import_item (source_app TEXT NOT NULL,source_kind TEXT NOT NULL,source_id TEXT NOT NULL,source_part TEXT NOT NULL,
            source_schema INTEGER NOT NULL,app_version TEXT NOT NULL,export_date TEXT NOT NULL,source_digest TEXT NOT NULL,source_json TEXT,target_digest TEXT NOT NULL,
            record_id INTEGER,lab_id INTEGER,panel_uuid TEXT,imported_utc INTEGER NOT NULL,retention TEXT NOT NULL,
            PRIMARY KEY(source_app,source_kind,source_id,source_part),
            FOREIGN KEY(record_id) REFERENCES dose_record(id) ON UPDATE NO ACTION ON DELETE SET NULL,
            FOREIGN KEY(lab_id) REFERENCES lab_value(id) ON UPDATE NO ACTION ON DELETE SET NULL)""",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_external_import_item_record_id ON external_import_item(record_id)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_external_import_item_lab_id ON external_import_item(lab_id)",
        "CREATE INDEX IF NOT EXISTS index_external_import_item_panel_uuid ON external_import_item(panel_uuid)")
    fun migrate(db:SupportSQLiteDatabase) {create.forEach(db::execSQL);install(db)}
    fun exists(db:SupportSQLiteDatabase)=db.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name='external_import_item'").use{it.moveToFirst()}
    private val predicate="""NEW.source_app='MONA' AND NEW.source_schema=23 AND length(NEW.source_id) BETWEEN 1 AND 19 AND NEW.source_id NOT GLOB '*[^0-9]*'
        AND length(NEW.app_version) BETWEEN 1 AND 128 AND length(NEW.export_date) BETWEEN 1 AND 128
        AND length(NEW.source_digest)=64 AND NEW.source_digest NOT GLOB '*[^0-9a-f]*'
        AND length(NEW.target_digest)=64 AND NEW.target_digest NOT GLOB '*[^0-9a-f]*' AND NEW.imported_utc>0
        AND ((NEW.retention='COMMITTED' AND length(NEW.source_json) BETWEEN 2 AND 65536) OR (NEW.retention='REMOVED' AND NEW.source_json IS NULL AND NEW.record_id IS NULL AND NEW.lab_id IS NULL))
        AND ((NEW.source_kind='INTAKE' AND NEW.source_part='' AND NEW.lab_id IS NULL AND NEW.panel_uuid IS NULL AND
            (NEW.record_id IS NULL OR EXISTS(SELECT 1 FROM dose_record r WHERE r.id=NEW.record_id AND r.origin='IMPORT_MONA' AND r.source_record_key='mona:intake:'||NEW.source_id)))
            OR (NEW.source_kind='BLOOD_TEST' AND NEW.source_part IN('E2','T') AND NEW.record_id IS NULL AND length(NEW.panel_uuid)=36
                AND (NEW.lab_id IS NULL OR EXISTS(SELECT 1 FROM lab_value l WHERE l.id=NEW.lab_id ))))""".trimIndent()
    fun install(db:SupportSQLiteDatabase) {
        if(!exists(db))return // Historical migration fixtures are still their real old schema.
        for(op in listOf("INSERT","UPDATE")) {
            db.execSQL("DROP TRIGGER IF EXISTS guard_external_import_item_${op.lowercase()}")
            db.execSQL("CREATE TRIGGER guard_external_import_item_${op.lowercase()} BEFORE $op ON external_import_item WHEN COALESCE(($predicate),0)=0 BEGIN SELECT RAISE(ABORT,'Invalid external import receipt');END")
        }
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS external_import_initial BEFORE INSERT ON external_import_item
            WHEN NEW.retention!='COMMITTED' OR (NEW.record_id IS NULL AND NEW.lab_id IS NULL)
            BEGIN SELECT RAISE(ABORT,'Import receipt requires a real target');END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS external_import_retention BEFORE DELETE ON external_import_item
            BEGIN SELECT RAISE(ABORT,'Source identity retained until explicit dataset replacement');END""")
        val frozen=listOf("source_app","source_kind","source_id","source_part","source_schema","app_version","export_date","source_digest","target_digest","panel_uuid","imported_utc")
            .joinToString(" AND "){"NEW.$it IS OLD.$it"}
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS external_import_frozen BEFORE UPDATE ON external_import_item WHEN NOT($frozen)
            OR (NEW.retention IS NOT OLD.retention AND NOT(OLD.retention='COMMITTED' AND NEW.retention='REMOVED'))
            OR (NEW.source_json IS NOT OLD.source_json AND NOT(OLD.retention='COMMITTED' AND NEW.retention='REMOVED' AND NEW.source_json IS NULL))
            BEGIN SELECT RAISE(ABORT,'Immutable external source evidence');END""")
    }
    /** Independent SQL predicate also applies when trusted restore temporarily removes guards. */
    fun validateStoredColumns(db:SupportSQLiteDatabase) {
        if(!exists(db)){require(db.version<18);return}
        db.query("SELECT 1 FROM external_import_item AS NEW WHERE COALESCE(($predicate),0)=0 LIMIT 1").use{require(!it.moveToFirst()){"Invalid external source receipt"}}
    }
    fun validateVersion(schema:Int,tables:BackupTables) {
        if(schema>=14){require(tables.has(TABLE)){"Missing source receipt table"};return}
        require(!tables.has(TABLE)){"Source receipts require business schema14"}
        val records=tables.optJSONArray("dose_record")
        if(records!=null)for(i in 0 until records.length())require(records.getJSONObject(i).opt("origin")!="IMPORT_MONA"){"Misdeclared Mona backup version"}
        val trash=tables.optJSONArray("trash_item")
        if(trash!=null)for(i in 0 until trash.length()) {
            val payload=trash.getJSONObject(i).getString("payload_json");BackupLimits.checkJson(payload)
            require(!JSONObject(payload).has("mona_sources")){"Source retention metadata requires business schema14"}
        }
    }
    /** Existing rows can be corrected by the user. The frozen source is validated separately
     * from their mutable current values; it must never become a command to overwrite them. */
    fun validate(db:SupportSQLiteDatabase) {
        validateStoredColumns(db)
        if(!exists(db))return
        val retained=MonaReceiptStore.retained(db)
        db.query("SELECT * FROM external_import_item").use {c->while(c.moveToNext()) {
            fun text(key:String)=c.getString(c.getColumnIndexOrThrow(key))
            if(text("retention")=="REMOVED")continue
            val kind=text("source_kind");val part=text("source_part")
            val pointer=if(kind=="INTAKE")"record_id" else "lab_id"
            if(c.isNull(c.getColumnIndexOrThrow(pointer)))require(MonaReceiptStore.Key(kind,text("source_id"),part) in retained){"Unowned missing source target"}
            val json=text("source_json");BackupLimits.checkJson(json)
            val fragment=JSONObject(json)
            require(Mona.canonical(fragment)==json && MonaMigration.digest(json)==text("source_digest")){"Invalid source digest"}
            val id=fragment.get("id");require((id is Int || id is Long) && id.toString()==text("source_id")){"Invalid source identity"}
            val data=JSONObject().put("medication_intakes",JSONArray()).put("medication_schedules",JSONArray()).put("supply_items",JSONArray()).put("blood_tests",JSONArray())
            data.getJSONArray(if(kind=="INTAKE")"medication_intakes" else "blood_tests").put(fragment)
            val root=JSONObject().put("metadata",JSONObject().put("app_version",text("app_version")).put("database_version",23).put("export_date",text("export_date"))).put("data",data)
            val projected=MonaMigration.plan(Mona.read(root.toString()))
            if(kind=="INTAKE")require(projected.doses.singleOrNull()?.sourceId==text("source_id") && projected.issues.isEmpty()){"Invalid original intake evidence"}
            else {
                require(!fragment.has(if(part=="E2")"testosteroneLevels" else "estradiolLevels")){"Mixed analyte evidence"}
                require(projected.labs.singleOrNull()?.analyte==part && projected.issues.isEmpty()){"Invalid original lab evidence"}
            }
        }}
        // Every imported dose has a real receipt. Physical purges may leave an existing
        // ledger-backed soft-deleted row, whose minimal tombstone remains explicit.
        db.query("""SELECT 1 FROM dose_record r LEFT JOIN external_import_item e ON e.source_app='MONA' AND e.source_kind='INTAKE'
            AND r.source_record_key='mona:intake:'||e.source_id
            WHERE r.origin='IMPORT_MONA' AND (e.source_id IS NULL OR (e.retention='COMMITTED' AND e.record_id IS NOT r.id)
                OR (e.retention='REMOVED' AND r.deleted_at_utc IS NULL)) LIMIT 1""").use {require(!it.moveToFirst()){"Uncovered Mona source identity"}}
    }

}
