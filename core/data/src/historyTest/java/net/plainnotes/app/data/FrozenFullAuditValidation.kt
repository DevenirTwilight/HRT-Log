package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject
import java.io.InputStream
import java.time.*

/** Frozen pre-candidate full audit at 99e536fea5e86a3dbe6f038d89d6a4281e7f3f11. Test-only; never update with optimizations. */
internal object FrozenFullAuditValidation {
    /** Column allowlist/types taken from trusted schema, never used to construct SQL from backup keys. */
    fun validateTables(db: SupportSQLiteDatabase, tables: JSONObject) = validateRows(db,BackupTables.of(tables))
    internal fun validateRows(db:SupportSQLiteDatabase,tables:BackupTables) {
        require(tables.keys().all { it in DOMAIN_TABLES }) { "Unknown table" }
        var count = 0L
        DOMAIN_TABLES.forEach { table ->
            if (!tables.has(table)) return@forEach // Newly added tables absent in a supported old backup.
            val rows = tables.getJSONArray(table)
            count += rows.length()
            if (count > BackupLimits.MAX_ROWS) throw BackupCodec.TooLarge()
            data class Column(val type: String, val required: Boolean)
            val columns = db.query("PRAGMA table_info(`$table`)").use { c -> buildMap {
                while (c.moveToNext()) put(c.getString(1), Column(c.getString(2), c.getInt(3) != 0 && c.isNull(4)))
            } }
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                require(row.keys().asSequence().all { it in columns }) { "Unknown column" }
                columns.forEach { (key, col) ->
                    if (col.required) require(row.has(key) && !row.isNull(key)) { "Missing column" }
                    if (!row.has(key) || row.isNull(key)) return@forEach
                    val v = row.get(key)
                    require(when (col.type) {
                        "INTEGER" -> v is Int || v is Long
                        "REAL" -> v is Number && v.toDouble().isFinite()
                        "TEXT" -> v is String
                        else -> false
                    }) { "Invalid column type" }
                    if (v is String) when (key) {
                        "date", "opened_on", "anchor_local", "range_from", "range_to", "from_date", "until_date" -> LocalDate.parse(v)
                        "local_time" -> { require(v.length == 8); LocalTime.parse(v) }
                        "anchor_zone", "effective_zone", "scheduled_zone", "taken_zone", "at_zone", "created_zone", "sampled_zone", "rescheduled_zone", "zone" -> ZoneId.of(v)
                        "config_snapshot", "effects_json", "context_snapshot", "definition_json", "facts_json", "standard_json", "identity_json", "evidence_json" -> { BackupLimits.checkJson(v); JSONObject(v) }
                        "sections" -> VisitSection.parse(v)
                    }
                }
            }
        }
    }

    /** Trusted-column SQL checks on an already parsed isolated candidate, before any destination changes. */
    internal fun validateCandidateStoredColumns(db:SupportSQLiteDatabase) {
        check(db.inTransaction());var total=0L;var textBytes=0L
        for(table in DOMAIN_TABLES) {
            // Retain the original early row fence before scanning stored values.
            total+=db.query("SELECT COUNT(*) FROM `$table`").use{it.moveToFirst();it.getLong(0)}
            require(total<=BackupLimits.MAX_ROWS)
            val columns=db.query("PRAGMA table_info(`$table`)").use{c->buildList{while(c.moveToNext())add(c.getString(1) to c.getString(2))}}
            // Trusted schema identifiers only. Aggregate all original column predicates in one table scan.
            val invalid=columns.map { (column,type) ->
                val allowed=when(type){"INTEGER"->"typeof(`$column`)='integer'";"TEXT"->"typeof(`$column`)='text' AND length(`$column`)<=1048576";"REAL"->"typeof(`$column`) IN ('integer','real') AND `$column` BETWEEN -1.7976931348623157e308 AND 1.7976931348623157e308";else->error("Unknown trusted storage type")}
                "`$column` IS NOT NULL AND NOT ($allowed)"
            }
            val lengths=columns.filter{it.second=="TEXT"}.map{(column,_)->"COALESCE(length(CAST(`$column` AS BLOB)),0)"}
            val bytes=if(lengths.isEmpty())"0" else "COALESCE(SUM(${lengths.joinToString("+")}),0)"
            // Same invalid-cell truth values, one aggregate state instead of one per column.
            // Balance the OR tree; no original field predicate or byte/row fence is removed.
            var predicates=invalid
            while(predicates.size>1)predicates=predicates.chunked(2).map { pair ->
                if(pair.size==1)pair[0] else "(${pair[0]} OR ${pair[1]})"
            }
            val anyInvalid=predicates.singleOrNull() ?: "0"
            val projection="$bytes,COALESCE(MAX(CASE WHEN $anyInvalid THEN 1 ELSE 0 END),0)"
            db.query("SELECT $projection FROM `$table`").use { c ->
                require(c.moveToFirst())
                textBytes+=c.getLong(0);require(textBytes<=BackupLimits.MAX_JSON_BYTES)
                require(c.getLong(1)==0L)
            }
        }
    }

    /** Checks the whole restored state, including values inserted while append-only triggers were disabled. */
    fun validateState(db: SupportSQLiteDatabase) = validateState(db, false)

    /** Shared legacy domain checks plus candidate references; measured coverage is checked by the global auditor. */
    internal fun validateMeasuredCandidateState(db: SupportSQLiteDatabase) = validateState(db, true)

    private fun validateState(db: SupportSQLiteDatabase, candidate: Boolean) {
        db.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) { "Invalid reference" } }
        SchemaGuards.validateRestored(db)
        if(candidate) FrozenFullAuditReferences.validateCandidateReferences(db) else {FrozenFullAuditReferences.validate(db);InventoryEncoding.legacyOnly(db)}
        LabContext.validateState(db)
        db.query("SELECT id,definition_json,clinical_signature,zone FROM regimen_version").use{c->while(c.moveToNext()) {
            val definition=RegimenDefinition.read(c.getString(1));require(definition.signature()==c.getString(2) && definition.zone==c.getString(3)){"Invalid regimen snapshot"}
        }}
        db.query("SELECT r.id FROM schedule_rule r LEFT JOIN regimen_rule_link l ON l.rule_id=r.id WHERE l.rule_id IS NULL LIMIT 1").use{require(!it.moveToFirst()){"Missing regimen link"}}
        RegimenHistory.validateLinks(db)
        db.query("SELECT * FROM history_period_revision").use{c->while(c.moveToNext()) {
            fun t(k:String)=c.getString(c.getColumnIndexOrThrow(k))
            fun n(k:String)=c.getLong(c.getColumnIndexOrThrow(k))
            HistoryPeriods.validate(HistoryPeriodEntity(n("id"),t("period_key"),n("revision").toInt(),t("state"),n("medication_id"),t("identity_json"),t("standard_json"),
                t("from_date"),if(c.isNull(c.getColumnIndexOrThrow("until_date")))null else t("until_date"),t("zone"),t("evidence_json"),t("origin"),n("created_utc"),
                t("kind"),if(c.isNull(c.getColumnIndexOrThrow("group_key")))null else t("group_key")))
        }}
        // §39 recycle bin: snapshot payloads parse and only name domain tables; references point at what they hide.
        db.query("SELECT kind,ref,payload_json FROM trash_item").use{c->while(c.moveToNext()){
            val kind=c.getString(0);val ref=c.getString(1);val payload=org.json.JSONObject(c.getString(2))
            if(kind in Trash.SNAPSHOT_KINDS){val order=payload.getJSONArray("order");require(order.length()>0);for(i in 0 until order.length())require(order.getString(i) in DOMAIN_TABLES && payload.getJSONObject("tables").getJSONArray(order.getString(i)).length()>=0)}
            when(kind){"RECORD"->db.query("SELECT 1 FROM dose_record WHERE id=? AND deleted_at_utc IS NOT NULL",arrayOf(ref.toLong())).use{require(it.moveToFirst()){"Invalid trash reference"}}
                "CORRECTION"->if(!PackageCorrections.isAudit(ref))db.query("SELECT 1 FROM history_period_revision WHERE group_key=?",arrayOf(ref)).use{require(it.moveToFirst()){"Invalid trash reference"}}
                "PERIOD"->db.query("SELECT 1 FROM history_period_revision WHERE group_key=?",arrayOf(ref)).use{require(it.moveToFirst()){"Invalid trash reference"}}}
        }}
        PackageCorrections.validate(db)
        fun rejectIf(sql: String) = db.query(sql).use { require(!it.moveToFirst()) { "Invalid restored state" } }
        rejectIf("SELECT 1 FROM supply_transaction t JOIN supply_container c ON c.id=t.container_id LEFT JOIN dose_record d ON d.id=t.dose_record_id WHERE t.used_delta=0 OR t.operation_id='' OR t.kind NOT IN ('ADJUST','CONSUME','REVERSE') OR (t.kind='ADJUST' AND (t.dose_record_id IS NOT NULL OR t.reversal_of_id IS NOT NULL)) OR (t.kind='CONSUME' AND (t.used_delta<=0 OR t.reversal_of_id IS NOT NULL OR t.dose_record_id IS NULL OR t.dose_revision IS NULL OR t.dose_revision<1 OR t.dose_revision>d.revision)) OR (t.dose_record_id IS NOT NULL AND d.medication_id!=c.medication_id) LIMIT 1")
        rejectIf("SELECT 1 FROM supply_transaction t LEFT JOIN supply_transaction original ON original.id=t.reversal_of_id WHERE t.kind='REVERSE' AND (original.id IS NULL OR original.kind!='CONSUME' OR t.dose_record_id IS NULL OR t.dose_revision IS NULL OR t.dose_revision<original.dose_revision OR original.container_id!=t.container_id OR original.dose_record_id!=t.dose_record_id OR t.used_delta!=-original.used_delta) LIMIT 1")
        rejectIf("SELECT 1 FROM supply_transaction t JOIN dose_record d ON d.id=t.dose_record_id WHERE t.kind='CONSUME' AND NOT EXISTS (SELECT 1 FROM supply_transaction r WHERE r.reversal_of_id=t.id) AND (d.deleted_at_utc IS NOT NULL OR d.status NOT IN ('ON_TIME','LATE') OR t.dose_revision!=d.revision) LIMIT 1")
        rejectIf("SELECT 1 FROM schedule_rule r WHERE r.kind!='EVERY_N_HOURS' AND NOT EXISTS (SELECT 1 FROM rule_time t WHERE t.rule_id=r.id) LIMIT 1")
    }
}
