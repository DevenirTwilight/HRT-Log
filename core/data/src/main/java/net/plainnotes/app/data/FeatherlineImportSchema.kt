package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import net.plainnotes.app.importer.Featherline
import net.plainnotes.app.importer.Mona
import net.plainnotes.app.importer.MonaMigration
import org.json.JSONObject

/** Additive Room19 source receipts; legacy facts are never assigned guessed UUIDs. */
internal object FeatherlineImportSchema {
    const val TABLE="featherline_import_item"
    val columns=listOf("source_kind","source_id","source_panel_uuid","source_schema","source_digest","source_json","target_digest","record_id","lab_id","panel_uuid","imported_utc","retention")
    fun exists(db:SupportSQLiteDatabase)=db.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",arrayOf(TABLE)).use{it.moveToFirst()}
    fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS featherline_import_item(source_kind TEXT NOT NULL,source_id TEXT NOT NULL,source_panel_uuid TEXT,source_schema INTEGER NOT NULL,
            source_digest TEXT NOT NULL,source_json TEXT,target_digest TEXT NOT NULL,record_id INTEGER,lab_id INTEGER,panel_uuid TEXT,imported_utc INTEGER NOT NULL,retention TEXT NOT NULL,
            PRIMARY KEY(source_kind,source_id),FOREIGN KEY(record_id) REFERENCES dose_record(id) ON UPDATE NO ACTION ON DELETE SET NULL,
            FOREIGN KEY(lab_id) REFERENCES lab_value(id) ON UPDATE NO ACTION ON DELETE SET NULL)""")
        for(col in listOf("record_id","lab_id","source_panel_uuid","panel_uuid"))db.execSQL("CREATE ${if(col in setOf("record_id","lab_id"))"UNIQUE " else ""}INDEX IF NOT EXISTS index_featherline_import_item_$col ON featherline_import_item($col)")
        install(db)
    }
    private val predicate="""NEW.source_schema=7 AND length(NEW.source_id)=36 AND length(NEW.source_digest)=64 AND NEW.source_digest NOT GLOB '*[^0-9a-f]*'
        AND length(NEW.target_digest)=64 AND NEW.target_digest NOT GLOB '*[^0-9a-f]*' AND NEW.imported_utc>0
        AND ((NEW.retention='COMMITTED' AND length(NEW.source_json) BETWEEN 2 AND 131072) OR
            (NEW.retention='REMOVED' AND NEW.source_json IS NULL AND NEW.record_id IS NULL AND NEW.lab_id IS NULL))
        AND ((NEW.source_kind='LOG' AND NEW.lab_id IS NULL AND NEW.source_panel_uuid IS NULL AND NEW.panel_uuid IS NULL
            AND (NEW.record_id IS NULL OR EXISTS(SELECT 1 FROM dose_record r WHERE r.id=NEW.record_id AND r.origin='IMPORT_FEATHERLINE' AND r.source_record_key='featherline:log:'||NEW.source_id)))
        OR (NEW.source_kind='RESULT' AND NEW.record_id IS NULL AND length(NEW.source_panel_uuid)=36 AND length(NEW.panel_uuid)=36
            AND (NEW.lab_id IS NULL OR EXISTS(SELECT 1 FROM lab_value l WHERE l.id=NEW.lab_id))))"""
    fun install(db:SupportSQLiteDatabase) {
        if(!exists(db))return
        for(op in listOf("INSERT","UPDATE"))db.execSQL("CREATE TRIGGER IF NOT EXISTS guard_featherline_${op.lowercase()} BEFORE $op ON $TABLE WHEN COALESCE(($predicate),0)=0 BEGIN SELECT RAISE(ABORT,'Invalid Featherline receipt');END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS featherline_initial BEFORE INSERT ON $TABLE WHEN NEW.retention!='COMMITTED' OR (NEW.record_id IS NULL AND NEW.lab_id IS NULL) BEGIN SELECT RAISE(ABORT,'Source requires a real target');END")
        val frozen=(columns-setOf("source_json","record_id","lab_id","retention")).joinToString(" AND "){"NEW.$it IS OLD.$it"}
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS featherline_frozen BEFORE UPDATE ON $TABLE WHEN NOT($frozen)
            OR (NEW.retention IS NOT OLD.retention AND NOT(OLD.retention='COMMITTED' AND NEW.retention='REMOVED'))
            OR (NEW.source_json IS NOT OLD.source_json AND NOT(OLD.retention='COMMITTED' AND NEW.retention='REMOVED' AND NEW.source_json IS NULL))
            BEGIN SELECT RAISE(ABORT,'Immutable Featherline evidence');END""")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS featherline_retention BEFORE DELETE ON $TABLE BEGIN SELECT RAISE(ABORT,'Retain source identity');END")
    }
    fun validateVersion(schema:Int,tables:BackupTables) {
        if(schema>=15){require(tables.has(TABLE));return}
        require(!tables.has(TABLE)){"Featherline receipts need business15"}
        tables.optJSONArray("dose_record")?.let{a->for(i in 0 until a.length())require(a.getJSONObject(i).opt("origin")!="IMPORT_FEATHERLINE")}
        tables.optJSONArray("trash_item")?.let{a->for(i in 0 until a.length()){
            val raw=a.getJSONObject(i).getString("payload_json");BackupLimits.checkJson(raw);require(!JSONObject(raw).has("featherline_sources"))
        }}
    }
    fun validate(db:SupportSQLiteDatabase) {
        if(!exists(db)){require(db.version<19);return}
        db.query("SELECT 1 FROM $TABLE AS NEW WHERE COALESCE(($predicate),0)=0 LIMIT 1").use{require(!it.moveToFirst())}
        val retained=FeatherlineReceiptStore.retained(db)
        db.query("SELECT * FROM $TABLE").use{c->while(c.moveToNext()) {
            fun text(k:String)=c.getString(c.getColumnIndexOrThrow(k))
            val kind=text("source_kind");val id=text("source_id");require(uuid(id))
            if(kind=="RESULT")require(uuid(text("source_panel_uuid")) && uuid(text("panel_uuid")))
            if(text("retention")=="REMOVED")continue
            if(c.isNull(c.getColumnIndexOrThrow(if(kind=="LOG")"record_id" else "lab_id")))require(FeatherlineReceiptStore.Key(kind,id) in retained)
            val raw=text("source_json");BackupLimits.checkJson(raw);val obj=JSONObject(raw)
            require(Mona.canonical(obj)==raw && MonaMigration.digest(raw)==text("source_digest"))
            val projected=Featherline.evidence(kind,raw)
            if(kind=="LOG")require(projected.doses.singleOrNull()?.id==id && projected.issues.isEmpty())
            else require(projected.labs.singleOrNull()?.let{it.id==id && it.panel==text("source_panel_uuid")}==true && projected.issues.isEmpty())
        }}
        db.query("""SELECT 1 FROM dose_record r LEFT JOIN $TABLE e ON e.source_kind='LOG' AND r.source_record_key='featherline:log:'||e.source_id
            WHERE r.origin='IMPORT_FEATHERLINE' AND (e.source_id IS NULL OR (e.retention='COMMITTED' AND e.record_id IS NOT r.id)
            OR (e.retention='REMOVED' AND r.deleted_at_utc IS NULL)) LIMIT 1""").use{require(!it.moveToFirst())}
    }
    private fun uuid(s:String)=runCatching{java.util.UUID.fromString(s).toString()==s}.getOrDefault(false)
}
