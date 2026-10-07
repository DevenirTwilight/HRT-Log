package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject
import java.io.InputStream
import java.time.*

/** Limits apply before buffering/parsing. No compression and no untrusted KDF cost in PNBAK1. */
object BackupLimits {
    const val MAX_FILE_BYTES = 32 * 1024 * 1024
    const val MAX_JSON_BYTES = 32 * 1024 * 1024
    const val MAX_ROWS = 100_000
    private const val MAX_DEPTH = 32
    private const val MAX_STRING = 1024 * 1024

    fun read(input: InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (out.size().toLong() + n > MAX_FILE_BYTES) throw BackupCodec.TooLarge()
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /** Lexical budget for JSON including embedded snapshot/effects strings; semantic checks follow. */
    fun checkJson(text: String) {
        if (text.length > MAX_JSON_BYTES) throw BackupCodec.TooLarge()
        var depth = 0; var quoted = false; var escaped = false; var length = 0
        text.forEach { c ->
            if (quoted) {
                if (++length > MAX_STRING) throw BackupCodec.TooLarge()
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> { quoted = true; length = 0 }
                '{', '[' -> if (++depth > MAX_DEPTH) throw BackupCodec.TooLarge()
                '}', ']' -> if (--depth < 0) throw BackupCodec.BadFile("invalid structure")
            }
        }
        if (quoted || depth != 0) throw BackupCodec.BadFile("invalid structure")
    }
}

internal object BackupValidation {
    /** Column allowlist/types taken from trusted schema, never used to construct SQL from backup keys. */
    fun validateTables(db: SupportSQLiteDatabase, tables: JSONObject) {
        require(tables.keys().asSequence().all { it in DOMAIN_TABLES }) { "Unknown table" }
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
                        "date", "opened_on", "anchor_local", "range_from", "range_to" -> LocalDate.parse(v)
                        "local_time" -> { require(v.length == 8); LocalTime.parse(v) }
                        "anchor_zone", "effective_zone", "scheduled_zone", "taken_zone", "at_zone", "created_zone", "sampled_zone", "rescheduled_zone", "zone" -> ZoneId.of(v)
                        "config_snapshot", "effects_json", "context_snapshot", "definition_json", "facts_json" -> { BackupLimits.checkJson(v); JSONObject(v) }
                        "sections" -> VisitSection.parse(v)
                    }
                }
            }
        }
    }

    /** Checks the whole restored state, including values inserted while append-only triggers were disabled. */
    fun validateState(db: SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) { "Invalid reference" } }
        SchemaGuards.validateRestored(db)
        LabContext.validateState(db)
        db.query("SELECT id,definition_json,clinical_signature,zone FROM regimen_version").use{c->while(c.moveToNext()) {
            val definition=RegimenDefinition.read(c.getString(1));require(definition.signature()==c.getString(2) && definition.zone==c.getString(3)){"Invalid regimen snapshot"}
        }}
        db.query("SELECT r.id FROM schedule_rule r LEFT JOIN regimen_rule_link l ON l.rule_id=r.id WHERE l.rule_id IS NULL LIMIT 1").use{require(!it.moveToFirst()){"Missing regimen link"}}
        RegimenHistory.validateLinks(db)
        fun rejectIf(sql: String) = db.query(sql).use { require(!it.moveToFirst()) { "Invalid restored state" } }
        rejectIf("SELECT 1 FROM supply_transaction t JOIN supply_container c ON c.id=t.container_id LEFT JOIN dose_record d ON d.id=t.dose_record_id WHERE t.used_delta=0 OR t.operation_id='' OR t.kind NOT IN ('ADJUST','CONSUME','REVERSE') OR (t.kind='ADJUST' AND (t.dose_record_id IS NOT NULL OR t.reversal_of_id IS NOT NULL)) OR (t.kind='CONSUME' AND (t.used_delta<=0 OR t.reversal_of_id IS NOT NULL OR t.dose_record_id IS NULL OR t.dose_revision IS NULL OR t.dose_revision<1 OR t.dose_revision>d.revision)) OR (t.dose_record_id IS NOT NULL AND d.medication_id!=c.medication_id) LIMIT 1")
        rejectIf("SELECT 1 FROM supply_transaction t LEFT JOIN supply_transaction original ON original.id=t.reversal_of_id WHERE t.kind='REVERSE' AND (original.id IS NULL OR original.kind!='CONSUME' OR t.dose_record_id IS NULL OR t.dose_revision IS NULL OR t.dose_revision<original.dose_revision OR original.container_id!=t.container_id OR original.dose_record_id!=t.dose_record_id OR t.used_delta!=-original.used_delta) LIMIT 1")
        rejectIf("SELECT 1 FROM supply_transaction t JOIN dose_record d ON d.id=t.dose_record_id WHERE t.kind='CONSUME' AND NOT EXISTS (SELECT 1 FROM supply_transaction r WHERE r.reversal_of_id=t.id) AND (d.deleted_at_utc IS NOT NULL OR d.status NOT IN ('ON_TIME','LATE') OR t.dose_revision!=d.revision) LIMIT 1")
        rejectIf("SELECT 1 FROM schedule_rule r WHERE r.kind!='EVERY_N_HOURS' AND NOT EXISTS (SELECT 1 FROM rule_time t WHERE t.rule_id=r.id) LIMIT 1")
    }
}
