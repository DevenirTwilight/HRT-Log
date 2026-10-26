package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import net.plainnotes.app.importer.ChrysalideMigration
import org.json.JSONObject

/** Business-schema16 source retention for dose-unknown Chrysalide events. */
internal object ChrysalideImportSchema {
    const val TABLE="chrysalide_import_item"
    private val create="""CREATE TABLE IF NOT EXISTS chrysalide_import_item(source_digest TEXT NOT NULL PRIMARY KEY,source_id TEXT NOT NULL,source_json TEXT,target_digest TEXT NOT NULL,record_id INTEGER,imported_utc INTEGER NOT NULL,retention TEXT NOT NULL,FOREIGN KEY(record_id) REFERENCES dose_record(id) ON UPDATE NO ACTION ON DELETE SET NULL)"""
    fun exists(db:SupportSQLiteDatabase)=db.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",arrayOf(TABLE)).use{it.moveToFirst()}
    fun migrate(db:SupportSQLiteDatabase){db.execSQL(create);db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_chrysalide_import_item_record_id ON $TABLE(record_id)");install(db)}
    private val valid="length(NEW.source_digest)=64 AND NEW.source_digest NOT GLOB '*[^0-9a-f]*' AND length(NEW.source_id) BETWEEN 1 AND 19 AND NEW.source_id NOT GLOB '*[^0-9]*' AND length(NEW.target_digest)=64 AND NEW.target_digest NOT GLOB '*[^0-9a-f]*' AND NEW.imported_utc>0 AND ((NEW.retention='COMMITTED' AND length(NEW.source_json) BETWEEN 2 AND 65536 AND NEW.record_id IS NOT NULL AND EXISTS(SELECT 1 FROM dose_record r WHERE r.id=NEW.record_id AND r.origin='IMPORT_CHRYSALIDE' AND r.source_record_key='chry:'||NEW.source_digest AND r.actual_dose IS NULL)) OR (NEW.retention='REMOVED' AND NEW.source_json IS NULL AND NEW.record_id IS NULL))"
    fun install(db:SupportSQLiteDatabase){if(!exists(db))return
        for(op in listOf("INSERT","UPDATE")){db.execSQL("DROP TRIGGER IF EXISTS guard_chrysalide_${op.lowercase()}");db.execSQL("CREATE TRIGGER guard_chrysalide_${op.lowercase()} BEFORE $op ON $TABLE WHEN COALESCE(($valid),0)=0 BEGIN SELECT RAISE(ABORT,'Invalid Chrysalide receipt');END")}
        db.execSQL("CREATE TRIGGER IF NOT EXISTS chrysalide_initial BEFORE INSERT ON $TABLE WHEN NEW.retention!='COMMITTED' OR NEW.record_id IS NULL BEGIN SELECT RAISE(ABORT,'Chrysalide receipt needs a committed record');END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS chrysalide_no_delete BEFORE DELETE ON $TABLE BEGIN SELECT RAISE(ABORT,'Retain Chrysalide source identity');END")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS chrysalide_frozen BEFORE UPDATE ON $TABLE WHEN NEW.source_digest IS NOT OLD.source_digest OR NEW.source_id IS NOT OLD.source_id OR NEW.target_digest IS NOT OLD.target_digest OR NEW.imported_utc IS NOT OLD.imported_utc OR (OLD.retention='REMOVED') OR (NEW.retention IS NOT OLD.retention AND NOT(OLD.retention='COMMITTED' AND NEW.retention='REMOVED')) OR (NEW.source_json IS NOT OLD.source_json AND NOT(OLD.retention='COMMITTED' AND NEW.retention='REMOVED' AND NEW.source_json IS NULL)) BEGIN SELECT RAISE(ABORT,'Immutable Chrysalide evidence');END""")
    }
    fun validateVersion(schema:Int,tables:BackupTables){if(schema>=16){require(tables.has(TABLE));return};require(!tables.has(TABLE));
        tables.optJSONArray("dose_record")?.let{a->for(i in 0 until a.length())require(a.getJSONObject(i).opt("origin")!="IMPORT_CHRYSALIDE"){"Misdeclared Chrysalide backup version"}}
        tables.optJSONArray("trash_item")?.let{a->for(i in 0 until a.length()) { val row=a.getJSONObject(i); if(row.optString("kind")==Trash.RECORD) require(!JSONObject(row.getString("payload_json")).has("chrysalide_sources")){"Chrysalide identity requires business schema16"} }}
    }
    fun validate(db:SupportSQLiteDatabase){if(!exists(db)){require(db.version<20);return}
        db.query("SELECT 1 FROM $TABLE AS NEW WHERE COALESCE(($valid),0)=0 LIMIT 1").use{require(!it.moveToFirst()){"Invalid Chrysalide source receipt"}}
        val seen=hashSetOf<String>()
        db.query("SELECT source_digest,source_id,source_json,target_digest,record_id,retention FROM $TABLE").use{c->while(c.moveToNext()){
            val digest=c.getString(0);val id=c.getString(1);val retention=c.getString(5)
            require(seen.add(digest));if(retention=="REMOVED")continue
            val raw=c.getString(2);require(raw.toByteArray().size<=65536);val obj=JSONObject(raw)
            val medication=obj.opt("medication") as? JSONObject ?: error("Missing Chrysalide medication evidence")
            val log=obj.opt("log") as? JSONObject ?: error("Missing Chrysalide log evidence")
            val exportDigest=obj.optString("export_digest")
            require(exportDigest.matches(Regex("[0-9a-f]{64}")))
            val evidence=net.plainnotes.app.importer.Chrysalide.canonical(medication)+"\n"+net.plainnotes.app.importer.Chrysalide.canonical(log)
            require(obj.optString("source")=="chrysalide-v5" && obj.optString("source_digest")==digest && obj.optString("source_id")==id &&
                log.optString("id")==id && log.opt("taken")==true && medication.opt("id")==log.opt("medicationId") &&
                net.plainnotes.app.importer.ChrysalideMigration.digest(exportDigest+"\n"+evidence)==digest)
            val row=Trash.rows(db,"dose_record","id=?",arrayOf(c.getLong(4))).getJSONObject(0)
            require(row.optString("config_snapshot")==raw && row.optString("source_record_key")=="chry:"+digest &&
                row.opt("taken_utc")==java.time.Instant.parse(log.getString("timestamp")).toEpochMilli() && row.optString("taken_zone")=="UTC")
        }}
        db.query("SELECT 1 FROM dose_record r LEFT JOIN $TABLE x ON x.record_id=r.id WHERE r.origin='IMPORT_CHRYSALIDE' AND (r.actual_dose IS NOT NULL OR x.source_digest IS NULL AND NOT EXISTS(SELECT 1 FROM $TABLE z WHERE z.source_digest=substr(r.source_record_key,6) AND z.retention='REMOVED' AND z.record_id IS NULL AND r.deleted_at_utc IS NOT NULL) OR x.retention='COMMITTED' AND r.source_record_key!='chry:'||x.source_digest) LIMIT 1").use{require(!it.moveToFirst()){"Uncovered Chrysalide record"}}
    }
}
