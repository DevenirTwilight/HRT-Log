package net.plainnotes.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import net.plainnotes.app.importer.SqlSource
import net.plainnotes.app.importer.TransMemo
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.time.ZoneId
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Tables in foreign-key order (parents first). Reminder mappings are rebuilt by the scheduler and never exported. */
internal val DOMAIN_TABLES = listOf("medication", "pk_profile", "schedule_rule", "rule_time", "slot_override", "dose_record", "supply_container", "supply_transaction",
    "retained_slot", "appointment", "checkin_item", "checkin_score", "day_note", "lab_analyte", "lab_panel", "lab_value", "pk_settings",
    "stage_review", "symptom_check", "review_effect", "regimen_version", "regimen_rule_link", "milestone", "lab_context_revision", "visit_question", "visit_pack", "history_period_revision", "record_annotation", "trash_item") + ReferenceSchema.businessTables + listOf(ExternalImportSchema.TABLE)

/** Raw-SQL maintenance that has to bypass the append-only ledger triggers: full clear and backup restore. */
internal enum class RestoreCheckpoint { PLAINTEXT_AUTHENTICATED, INPUT_VALIDATED, STAGING_ROWS_INSERTED, STAGING_HISTORY_VALIDATED, STAGING_CLOSED, PREFLIGHT_VALIDATED, IDENTITIES_RETIRED, ROWS_DELETED, ROWS_INSERTED, HISTORY_VALIDATED, EPOCH_REPLACED }

internal object RawData {
    /** New OFF restore candidate only. Never repairs projections or touches an existing library.
     * The caller creates a private in-memory SQLCipher DB; reuse the exact insertion dependencies
     * and frozen complete oracle, with all source business values preserved rather than renumbered.
     */
    internal fun reconstructIsolated(db:SupportSQLiteDatabase,tables:BackupTables,checkActive:()->Unit) {
        check(db.inTransaction())
        db.query("PRAGMA database_list").use {c->while(c.moveToNext())check(c.getString(2).isNullOrEmpty()) {"Isolated reconstruction must stay in memory"}}
        db.query("PRAGMA temp_store").use {check(it.moveToFirst() && it.getInt(0)==2)}
        reconstructEmpty(db,tables,checkActive)
    }
    /** Only the coordinator's newly created held-directory SQLCipher generation is admitted.
     * The RAM-only entry above keeps its original stricter boundary. */
    internal fun reconstructGeneration(db:SupportSQLiteDatabase,tables:BackupTables,
        owned:LibraryGenerationContext,checkActive:()->Unit) {
        check(db.inTransaction());owned.checkCurrent()
        check(db.path==owned.getDatabasePath("notes.db").path)
        db.query("PRAGMA database_list").use {c->while(c.moveToNext())check(c.getString(1) in setOf("main","temp"))}
        db.query("PRAGMA temp_store").use {check(it.moveToFirst() && it.getInt(0)==2)}
        reconstructEmpty(db,tables,checkActive)
    }
    private fun reconstructEmpty(db:SupportSQLiteDatabase,tables:BackupTables,checkActive:()->Unit) {
        require(tables.keys()==DOMAIN_TABLES.toSet() || tables.keys()==RecordBackupCompatibility.tables(13).toSet())
        DOMAIN_TABLES.forEach {t->checkActive();db.query("SELECT 1 FROM $t LIMIT 1").use {check(!it.moveToFirst()) {"Candidate is not empty"}}}
        BackupValidation.validateRows(db,tables)
        val order=tables.optJSONArray("supply_transaction")?.let(LedgerReplacement::insertionOrder).orEmpty()
        unguarded(db) {
            insertRows(db,tables,order);checkActive()
            // Independent original SQL/full-history path, without cross-transaction trust or repair.
            MeasuredConsumptionStore.auditStagedDataset(db,0,0);checkActive()
        }
    }
    private data class Guard(val name: String, val sql: String)
    private fun triggers(db: SupportSQLiteDatabase): List<Guard> = db.query("SELECT name,sql FROM sqlite_master WHERE type='trigger' AND tbl_name NOT LIKE 'attachment_%'").use { c ->
        buildList { while (c.moveToNext()) add(Guard(c.getString(0), c.getString(1))) } }
    /** Only trusted installed DDL is captured; no backup-provided SQL is ever executed. */
    fun <T> unguarded(db: SupportSQLiteDatabase, block: () -> T): T {
        check(db.inTransaction())
        val guards = triggers(db)
        guards.forEach { db.execSQL("DROP TRIGGER IF EXISTS \"${it.name.replace("\"", "\"\"")}\"") }
        return try { block() } finally {
            SchemaGuards.install(db)
            guards.forEach { guard ->
                val exists = db.query("SELECT 1 FROM sqlite_master WHERE type='trigger' AND name=?", arrayOf(guard.name)).use { it.moveToFirst() }
                if (!exists) db.execSQL(guard.sql)
            }
        }
    }
    private fun deleteRows(db: SupportSQLiteDatabase, ledger: List<List<Long>>, references: Map<String, List<List<String>>>) {
        (DOMAIN_TABLES + "reminder_mapping").reversed().forEach { table ->
            if (table in references) ReferenceDependencies.delete(db, table, references.getValue(table))
            else if (table == "supply_transaction") LedgerReplacement.delete(db, ledger)
            else db.execSQL("DELETE FROM $table")
        }
    }
    fun clear(db: SupportSQLiteDatabase) {
        check(db.inTransaction())
        val ledger = LedgerReplacement.current(db)
        val references = ReferenceDependencies.current(db)
        unguarded(db) {
            ReferenceSchema.retireRuntime(db)
            AttachmentIdentity.replaceStructuredDataset(db)
            deleteRows(db, ledger, references)
        }
    }
    fun dump(db:SupportSQLiteDatabase):JSONObject= dumpRows(db,RecordBackupCompatibility.exportTables(db),true)
    fun dump(db:SupportSQLiteDatabase,tableNames:List<String>):JSONObject= dumpRows(db,tableNames,tableNames==DOMAIN_TABLES)
    private fun dumpRows(db:SupportSQLiteDatabase,tableNames:List<String>,requireComplete:Boolean):JSONObject {
        // Explicit trusted subset is used to construct old-version fixture archives; production exports always use all tables.
        require(tableNames.distinct().size == tableNames.size && tableNames.all { it in DOMAIN_TABLES })
        val tables = JSONObject()
        tableNames.forEach { t ->
            val rows = JSONArray()
            forEachBackupRow(db,t,requireComplete){rows.put(it)}
            tables.put(t, rows)
        }
        return tables
    }
    /** Validate in memory before retiring identities or modifying the encrypted target. */
    fun restore(context: Context, db: SupportSQLiteDatabase, tables: JSONObject, upgradeRegimens: Boolean = false, backfillTrash: Boolean = false, measured: Boolean = false, checkpoint: (RestoreCheckpoint)->Unit = {}) {
        restoreRows(context,db,BackupTables.of(tables),upgradeRegimens,backfillTrash,measured,checkpoint)
    }
    internal fun restoreRows(context:Context,db:SupportSQLiteDatabase,tables:BackupTables,upgradeRegimens:Boolean=false,backfillTrash:Boolean=false,measured:Boolean=false,checkpoint:(RestoreCheckpoint)->Unit={}) {
        check(db.inTransaction())
        BackupValidation.validateRows(db, tables)
        val ledger = LedgerReplacement.current(db)
        val references = ReferenceDependencies.current(db)
        val supplyOrder = tables.optJSONArray("supply_transaction")?.let(LedgerReplacement::insertionOrder).orEmpty()
        preflight(context, db, tables, supplyOrder, upgradeRegimens, backfillTrash, measured,checkpoint)
        checkpoint(RestoreCheckpoint.PREFLIGHT_VALIDATED)
        unguarded(db) {
            ReferenceSchema.retireRuntime(db)
            AttachmentIdentity.replaceStructuredDataset(db)
            checkpoint(RestoreCheckpoint.IDENTITIES_RETIRED)
            deleteRows(db, ledger, references)
            checkpoint(RestoreCheckpoint.ROWS_DELETED)
            insertRows(db, tables, supplyOrder)
            checkpoint(RestoreCheckpoint.ROWS_INSERTED)
            finishRestore(db, upgradeRegimens, backfillTrash, measured)
            checkpoint(RestoreCheckpoint.HISTORY_VALIDATED)
            ReferenceSchema.restoreRuntime(db)
            checkpoint(RestoreCheckpoint.EPOCH_REPLACED)
        }
    }

    /** A transient RAM database uses only trusted current table/index definitions, never backup DDL.
     * No plaintext file is created; the actual destination remains SQLCipher with FK enforcement enabled.
     */
    private fun preflight(context: Context, db: SupportSQLiteDatabase, tables: BackupTables, supplyOrder: List<Int>, upgradeRegimens: Boolean, backfillTrash: Boolean, measured: Boolean,checkpoint:(RestoreCheckpoint)->Unit) {
        val definitions = db.query("SELECT type,tbl_name,sql FROM sqlite_master WHERE type IN ('table','index') AND sql IS NOT NULL ORDER BY CASE type WHEN 'table' THEN 0 ELSE 1 END,name").use { c -> buildList {
            while (c.moveToNext()) if (c.getString(1) in DOMAIN_TABLES) add(c.getString(2))
        } }
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // Android's public helper API: explicitly in-memory, never a decrypted temporary file.
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onConfigure(db: SupportSQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
                override fun onCreate(db: SupportSQLiteDatabase) { definitions.forEach(db::execSQL) }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Transient database cannot upgrade")
            }).build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            val staging = helper.writableDatabase
            staging.beginTransaction()
            try {
                insertRows(staging, tables, supplyOrder)
                checkpoint(RestoreCheckpoint.STAGING_ROWS_INSERTED)
                finishRestore(staging, upgradeRegimens, backfillTrash, measured)
                checkpoint(RestoreCheckpoint.STAGING_HISTORY_VALIDATED)
                staging.setTransactionSuccessful()
            } finally { staging.endTransaction() }
        }
        checkpoint(RestoreCheckpoint.STAGING_CLOSED)
    }

    private fun insertRows(db: SupportSQLiteDatabase, tables: BackupTables, supplyOrder: List<Int>) {
        DOMAIN_TABLES.forEach { t ->
            val rows = tables.optJSONArray(t) ?: return@forEach
            val order = if (t == "supply_transaction") supplyOrder else ReferenceDependencies.insertionOrder(t, rows)
            order.forEach { i ->
                val r = rows.getJSONObject(i); val cv = ContentValues()
                r.keys().forEach { k -> when (val v = r.get(k)) {
                    JSONObject.NULL -> cv.putNull(k)
                    is Int -> cv.put(k, v.toLong()); is Long -> cv.put(k, v); is Double -> cv.put(k, v); is Boolean -> cv.put(k, if (v) 1 else 0)
                    // Current domain schema has no BLOB columns. A literal text prefix is not a type tag.
                    is String -> cv.put(k, v)
                    else -> cv.put(k, v.toString())
                } }
                db.insert(t, SQLiteDatabase.CONFLICT_ABORT, cv)
            }
        }
    }

    /** Same row/column/value encoding for DOM fixtures and bounded measured export. */
    internal fun forEachBackupRow(db:SupportSQLiteDatabase,t:String,requireComplete:Boolean,consume:(JSONObject)->Unit) {
        require(t in DOMAIN_TABLES)
            // Fixture archives of older schemas (explicit subset) leave out columns their table does not have yet (e.g. LAB-02 panel_id).
            val present = db.query("PRAGMA table_info(`$t`)").use { c -> buildSet { while (c.moveToNext()) add(c.getString(1)) } }
            val wanted = RecordBackupCompatibility.columns.getValue(t)
            if (requireComplete) require(present.containsAll(wanted)) { "Incomplete table $t" }
            db.query("SELECT ${wanted.filter { it in present }.joinToString(",") { "`$it`" }} FROM $t").use { c ->
                while (c.moveToNext()) consume(JSONObject().apply {
                    for (i in 0 until c.columnCount) when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> put(c.getColumnName(i), JSONObject.NULL)
                        Cursor.FIELD_TYPE_INTEGER -> put(c.getColumnName(i), c.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> {
                            val exact= t=="supply_container" && c.getColumnIndex("inventory_encoding")>=0 && c.getString(c.getColumnIndex("inventory_encoding"))==InventoryEncoding.EXACT
                            if(exact && c.getColumnName(i) in setOf("capacity","initial_used_amount","used_amount"))put(c.getColumnName(i),InventoryEncoding.exactInteger(c.getDouble(i)))
                            else put(c.getColumnName(i),c.getDouble(i))
                        }
                        Cursor.FIELD_TYPE_BLOB -> put(c.getColumnName(i), "b64:" + Base64.encodeToString(c.getBlob(i), Base64.NO_WRAP))
                        else -> put(c.getColumnName(i), c.getString(i))
                    }
                })
            }
    }

    private fun finishRestore(db: SupportSQLiteDatabase, upgradeRegimens: Boolean, backfillTrash: Boolean, measured: Boolean) {
        db.execSQL("UPDATE supply_container SET used_amount = initial_used_amount + COALESCE((SELECT SUM(used_delta) FROM supply_transaction WHERE container_id = supply_container.id), 0) WHERE inventory_encoding='LEGACY_SCALAR'")
        if (upgradeRegimens) RegimenHistory.seed(db)
        if (backfillTrash) Trash.backfill(db)
        if(measured) MeasuredConsumptionStore.auditStagedDataset(db) else BackupValidation.validateState(db)
    }

}

/** Password-encrypted backup file: magic, Argon2id salt, AES-GCM nonce, ciphertext of the JSON dump. */
object BackupCodec {
    private val MAGIC = "PNBAK1".toByteArray()
    const val FORMAT_VERSION = 1
    class WrongPassword : Exception()
    class BadFile(msg: String) : Exception(msg)
    /** The backup was written by a newer version of the app. */
    class NewerBackup : Exception()
    class TooLarge : Exception()

    private fun key(password: CharArray, salt: ByteArray): ByteArray {
        val gen = Argon2BytesGenerator()
        gen.init(Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withSalt(salt).withMemoryAsKB(32 * 1024).withIterations(3).withParallelism(1).build())
        return ByteArray(32).also { gen.generateBytes(password, it) }
    }
    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        if(plain.size>BackupLimits.MAX_JSON_BYTES)throw TooLarge()
        require(password.size >= 8)
        val rnd = SecureRandom(); val salt = ByteArray(16).also(rnd::nextBytes); val nonce = ByteArray(12).also(rnd::nextBytes)
        val k = key(password, salt)
        try {
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(128, nonce)); c.updateAAD(MAGIC)
            return (MAGIC + salt + nonce + c.doFinal(plain)).also{if(it.size>BackupLimits.MAX_FILE_BYTES)throw TooLarge()}
        } finally { k.fill(0) }
    }
    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        if(data.size>BackupLimits.MAX_FILE_BYTES)throw TooLarge()
        if (data.size < MAGIC.size + 28 + 16 || !data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw BadFile("not a backup")
        val salt = data.copyOfRange(MAGIC.size, MAGIC.size + 16); val nonce = data.copyOfRange(MAGIC.size + 16, MAGIC.size + 28)
        val k = key(password, salt)
        try {
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(128, nonce)); c.updateAAD(MAGIC)
            return try { c.doFinal(data, MAGIC.size + 28, data.size - MAGIC.size - 28) } catch (_: AEADBadTagException) { throw WrongPassword() }
        } finally { k.fill(0) }
    }
}

/** Reads a user-selected Trans Memo export (plain SQLite, opened read-only from a private copy). */
class AndroidSqlSource(private val db: SQLiteDatabase) : SqlSource, AutoCloseable {
    override fun userVersion() = db.version
    override fun columns(table: String): List<String>? = db.rawQuery("PRAGMA table_info($table)", null).use { c ->
        buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }.takeIf { it.isNotEmpty() } }
    override fun rows(table: String): List<Map<String, Any?>> = db.rawQuery("SELECT * FROM $table", null).use { c ->
        buildList { while (c.moveToNext()) add((0 until c.columnCount).associate { i -> c.getColumnName(i) to when (c.getType(i)) {
            Cursor.FIELD_TYPE_NULL -> null; Cursor.FIELD_TYPE_INTEGER -> c.getLong(i); Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i); else -> c.getString(i) } }) } }
    override fun close() = db.close()
    companion object { fun open(file: File) = AndroidSqlSource(SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)) }
}

class ImportSummary(val medications: Int, val reusedMedications: Int, val intakes: Int, val duplicates: Int, val containers: Int, val scores: Int, val notes: Int, val appointments: Int)

/** Writes a mapped Trans Memo plan. Imported medications stay paused (needs_review) until the user confirms them. */
internal object TransMemoWriter {
    suspend fun write(dao: NotesDao, raw: SupportSQLiteDatabase, plan: TransMemo.Plan, overwrite: Boolean, zone: ZoneId): ImportSummary {
        if (overwrite) RawData.clear(raw)
        val existing = dao.medications()
        val medIds = HashMap<Long, Long>(); var reused = 0; var created = 0
        val newMeds = HashSet<Long>()
        plan.medications.forEachIndexed { i, m ->
            val match = existing.firstOrNull { it.molecule == m.molecule && it.name.equals(m.name, ignoreCase = true) }
            if (match != null) { medIds[m.sourceId] = match.id; reused++; return@forEachIndexed }
            val review = JSONObject().put("source", "transmemo").put("raw", JSONObject(m.review as Map<*, *>))
                .put("prefill", JSONObject().put("daily", m.prefillDaily).put("times", JSONArray(m.prefillTimes.map { it.toString() })))
            val id = dao.insertMedication(MedicationEntity(name = m.name, molecule = m.molecule, route = null, unit = m.unit, dose_per_intake = m.dose, container_capacity = m.capacity,
                expiry_days_after_open = m.expiryDays, soon_alert_minutes = null, late_after_minutes = null, site_rotation = m.siteRotation, site_set = if (m.siteRotation) "LR" else null,
                notifications_on = false, active = m.active, sort_order = existing.size + i, needs_review = review.toString()))
            medIds[m.sourceId] = id; newMeds += m.sourceId; created++
        }
        val known = dao.records().mapNotNull { it.source_record_key }.toHashSet()
        var intakes = 0; var dups = 0
        plan.intakes.forEach { p ->
            if (p.sourceKey in known) { dups++; return@forEach }
            val med = medIds[p.productId] ?: return@forEach
            dao.record(RecordEntity(medication_id = med, scheduled_utc = p.scheduled?.toEpochMilli(), scheduled_zone = p.scheduled?.let { zone.id }, planned_dose = p.plannedDose,
                late_after_minutes_snapshot = p.lateMinutes, taken_utc = p.taken?.toEpochMilli(), taken_zone = p.taken?.let { zone.id }, actual_dose = p.actualDose,
                status = p.status, site = p.site, origin = "IMPORT_TM", source_record_key = p.sourceKey, revision = 1, config_snapshot = plan.medications.single{it.sourceId==p.productId}.let{source->
                    MedicationSnapshot.encode(dao.medication(med).copy(name=source.name,molecule=source.molecule,unit=source.unit,route=null),null)}))
            intakes++
        }
        var containers = 0
        plan.containers.filter { it.productId in newMeds }.forEach { c ->
            dao.insertContainer(ContainerEntity(medication_id = medIds.getValue(c.productId), capacity = c.capacity, initial_used_amount = c.used, used_amount = c.used,
                opened_on = c.openedOn?.toString(), state = c.state)); containers++
        }
        if (dao.checkinItems().isEmpty()) CHECKIN_DEFAULTS.forEachIndexed { i, k -> dao.insertCheckinItem(CheckinItemEntity(builtin_key = k, enabled = true, sort_order = i)) }
        val items = dao.checkinItems()
        val itemIds = plan.items.associate { it.sourceId to (items.firstOrNull { e -> (it.builtinKey != null && e.builtin_key == it.builtinKey) || (it.label != null && e.custom_label == it.label) }?.id
            ?: dao.insertCheckinItem(CheckinItemEntity(builtin_key = it.builtinKey, custom_label = it.label, enabled = it.enabled, sort_order = items.size + 1,
                legacy = it.builtinKey in LEGACY_KEYS))) }
        val scoresHave = if (plan.scores.isEmpty()) emptySet() else dao.scores(plan.scores.minOf { it.date }.toString(), plan.scores.maxOf { it.date }.toString()).map { it.date to it.item_id }.toSet()
        var scores = 0
        plan.scores.forEach { s -> val item = itemIds[s.itemSourceId] ?: return@forEach
            if ((s.date.toString() to item) !in scoresHave) { dao.score(CheckinScoreEntity(s.date.toString(), item, s.value)); scores++ } }
        var notes = 0
        plan.notes.forEach { (d, t) -> if (dao.notes(d.toString(), d.toString()).isEmpty()) { dao.note(DayNoteEntity(d.toString(), t)); notes++ } }
        val appts = dao.appointments(); var appointments = 0
        plan.appointments.forEach { a -> if (appts.none { it.at_utc == a.at.toEpochMilli() && it.location == a.location }) {
            dao.appointment(AppointmentEntity(type = "OTHER", at_utc = a.at.toEpochMilli(), at_zone = zone.id, location = a.location, practitioner = a.practitioner, note = a.note, remind_minutes_before = a.reminderMinutes)); appointments++ } }
        return ImportSummary(created, reused, intakes, dups, containers, scores, notes, appointments)
    }
}
