package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import net.plainnotes.app.domain.measurement.*

/** Synthetic candidate metadata only. Not a Room entity, migration, PNBAK writer or production service. */
internal object Ansm03b0CandidateStore {
    val tables = listOf("ansm_lab_dataset", "ansm_lab_snapshot", "ansm_lab_med", "ansm_lab_container", "ansm_lab_record", "ansm_lab_operation", "ansm_lab_capture")
    const val DDL_STATEMENTS = 8
    fun create(db: SupportSQLiteDatabase, failAfter: Int? = null) {
        val sql = listOf(
            "CREATE TABLE ansm_lab_dataset(id TEXT PRIMARY KEY NOT NULL,state TEXT NOT NULL CHECK(state IN('CURRENT','RETIRED')))",
            "CREATE UNIQUE INDEX ansm_lab_one_current ON ansm_lab_dataset(state) WHERE state='CURRENT'",
            "CREATE TABLE ansm_lab_snapshot(dataset TEXT NOT NULL,id TEXT NOT NULL,payload TEXT NOT NULL,sha TEXT NOT NULL,PRIMARY KEY(dataset,id),FOREIGN KEY(dataset) REFERENCES ansm_lab_dataset(id))",
            "CREATE TABLE ansm_lab_med(dataset TEXT NOT NULL,uuid TEXT NOT NULL,legacy_id INTEGER NOT NULL,PRIMARY KEY(dataset,uuid),UNIQUE(dataset,legacy_id),FOREIGN KEY(dataset) REFERENCES ansm_lab_dataset(id))",
            "CREATE TABLE ansm_lab_container(dataset TEXT NOT NULL,uuid TEXT NOT NULL,med TEXT NOT NULL,legacy_id INTEGER,state TEXT NOT NULL,snapshot TEXT,PRIMARY KEY(dataset,uuid),UNIQUE(dataset,legacy_id),UNIQUE(dataset,uuid,med,snapshot),FOREIGN KEY(dataset,med) REFERENCES ansm_lab_med(dataset,uuid),FOREIGN KEY(dataset,snapshot) REFERENCES ansm_lab_snapshot(dataset,id))",
            "CREATE TABLE ansm_lab_record(dataset TEXT NOT NULL,uuid TEXT NOT NULL,revision INTEGER NOT NULL CHECK(revision>0),med TEXT NOT NULL,legacy_id INTEGER NOT NULL,PRIMARY KEY(dataset,uuid,revision),UNIQUE(dataset,uuid,revision,med),FOREIGN KEY(dataset,med) REFERENCES ansm_lab_med(dataset,uuid))",
            "CREATE TABLE ansm_lab_operation(dataset TEXT NOT NULL,uuid TEXT NOT NULL,record_uuid TEXT NOT NULL,revision INTEGER NOT NULL,med TEXT NOT NULL,fingerprint TEXT NOT NULL,PRIMARY KEY(dataset,uuid),UNIQUE(dataset,uuid,med),FOREIGN KEY(dataset,record_uuid,revision,med) REFERENCES ansm_lab_record(dataset,uuid,revision,med))",
            "CREATE TABLE ansm_lab_capture(dataset TEXT NOT NULL,operation TEXT NOT NULL,container TEXT NOT NULL,med TEXT NOT NULL,snapshot TEXT NOT NULL,amount TEXT NOT NULL,unit TEXT NOT NULL,meaning TEXT NOT NULL,kind TEXT NOT NULL CHECK(kind IN('CONSUME','REVERSE','ADJUST')),reversal_of TEXT,PRIMARY KEY(dataset,operation,container),UNIQUE(dataset,reversal_of,container),FOREIGN KEY(dataset,operation,med) REFERENCES ansm_lab_operation(dataset,uuid,med),FOREIGN KEY(dataset,container,med,snapshot) REFERENCES ansm_lab_container(dataset,uuid,med,snapshot),FOREIGN KEY(dataset,snapshot) REFERENCES ansm_lab_snapshot(dataset,id),FOREIGN KEY(dataset,reversal_of,container) REFERENCES ansm_lab_capture(dataset,operation,container))"
        )
        check(sql.size == DDL_STATEMENTS)
        sql.forEachIndexed { i, statement -> db.execSQL(statement); if (i == failAfter) error("SYNTHETIC_DDL_FAILURE") }
        installGuards(db)
    }
    fun installGuards(db: SupportSQLiteDatabase) {
        for (table in listOf("ansm_lab_snapshot", "ansm_lab_capture", "ansm_lab_operation", "ansm_lab_record")) {
            for (verb in listOf("UPDATE", "DELETE")) db.execSQL("CREATE TRIGGER IF NOT EXISTS ${table}_${verb.lowercase()} BEFORE $verb ON $table BEGIN SELECT RAISE(ABORT,'ANSM_TEST_ONLY_IMMUTABLE'); END")
        }
        db.execSQL("CREATE TRIGGER IF NOT EXISTS ansm_lab_container_frozen BEFORE UPDATE OF snapshot ON ansm_lab_container WHEN EXISTS(SELECT 1 FROM ansm_lab_capture WHERE dataset=OLD.dataset AND container=OLD.uuid) BEGIN SELECT RAISE(ABORT,'ANSM_TEST_ONLY_FROZEN'); END")
    }
    fun current(db: SupportSQLiteDatabase): String? = db.query("SELECT id FROM ansm_lab_dataset WHERE state='CURRENT'").use { if (it.moveToFirst()) it.getString(0) else null }
    fun dataset(db: SupportSQLiteDatabase, id: String) { db.execSQL("INSERT INTO ansm_lab_dataset VALUES (?,'CURRENT')", arrayOf(id)) }
    fun retire(db: SupportSQLiteDatabase, id: String) { db.execSQL("UPDATE ansm_lab_dataset SET state='RETIRED' WHERE id=?", arrayOf(id)) }
    fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun canonical(v: Any?): String = when (v) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> v.keys().asSequence().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(v.get(it)) }
        is JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { canonical(v.get(it)) }
        is String -> JSONObject.quote(v)
        is Boolean, is Int, is Long -> v.toString()
        else -> error("Candidate JSON accepts no binary floating-point values")
    }
    private fun encode(q: Quantity?): Any = q?.let { JSONObject().put("n", it.value.toPlainString()).put("unit", it.unit.name)
        .put("kind", it.meaning.kind.name).put("identity", it.meaning.identity) } ?: JSONObject.NULL
    private fun decode(v: Any): Quantity? {
        if (v === JSONObject.NULL) return null
        val o = v as JSONObject
        val n = Measurements.parse(o.getString("n")); require(n is MeasureResult.Success)
        return Quantity(n.value, MeasureUnit.valueOf(o.getString("unit")), Meaning(QuantityKind.valueOf(o.getString("kind")), o.getString("identity")))
            .also { require(Measurements.error(it) == null) }
    }
    fun payload(s: PresentationSnapshot): String {
        val ref = s.ref
        require(s.relations.all { it.evidence.ref == ref }) { "Candidate source conflict" }
        val relations = JSONArray()
        s.relations.forEach { r -> relations.put(JSONObject().put("from", encode(r.from)).put("to", encode(r.to))
            .put("source", r.evidence.source).put("locator", r.evidence.locator).put("verifiedOn", r.evidence.verifiedOn)
            .put("conditions", r.evidence.conditions).put("verified", r.evidence.verified).put("fraction", r.evidence.fractionalContentVerified)) }
        return canonical(JSONObject().put("schema", "ANSM03B0-LAB-v1").put("algorithm", Measurements.ALGORITHM)
            .put("namespace", ref.namespace).put("product", ref.product).put("presentation", ref.presentation).put("version", ref.version).put("sourceSha", ref.sha256)
            .put("nominal", encode(s.nominalTotal)).put("uses", encode(s.nominalUses)).put("usable", encode(s.confirmedUsable)).put("rate", encode(s.releaseRate)).put("relations", relations))
    }
    fun readPayload(text: String, sha: String): PresentationSnapshot {
        require(text.length <= 32000 && digest(text) == sha)
        val o = JSONObject(text); require(o.getString("schema") == "ANSM03B0-LAB-v1" && o.getString("algorithm") == Measurements.ALGORITHM)
        val ref = PresentationRef(o.getString("namespace"), o.getString("product"), o.getString("presentation"), o.getString("version"), o.getString("sourceSha"))
        require(Measurements.refValid(ref))
        val rows = o.getJSONArray("relations"); require(rows.length() <= 16)
        val relations = (0 until rows.length()).map { i -> val r = rows.getJSONObject(i)
            ContentRelation(requireNotNull(decode(r.get("from"))), requireNotNull(decode(r.get("to"))),
                ConversionEvidence(ref, r.getString("source"), r.getString("locator"), r.getString("verifiedOn"), r.getString("conditions"), r.getBoolean("verified"), r.getBoolean("fraction"))) }
        return PresentationSnapshot(ref, decode(o.get("nominal")), decode(o.get("uses")), relations, decode(o.get("usable")), decode(o.get("rate")))
    }
    fun saveSnapshot(db: SupportSQLiteDatabase, dataset: String, id: String, snapshot: PresentationSnapshot) {
        val text = payload(snapshot); readPayload(text, digest(text))
        db.execSQL("INSERT INTO ansm_lab_snapshot VALUES (?,?,?,?)", arrayOf(dataset, id, text, digest(text)))
    }
    fun loadSnapshot(db: SupportSQLiteDatabase, dataset: String, id: String): PresentationSnapshot = db.query(
        "SELECT payload,sha FROM ansm_lab_snapshot WHERE dataset=? AND id=?", arrayOf(dataset, id)).use {
        require(it.moveToFirst()); readPayload(it.getString(0), it.getString(1)) }
    /** Same UUID, same complete payload is replay; conflicting payload refuses before any capture write. */
    fun operation(db: SupportSQLiteDatabase, dataset: String, id: String, record: String, revision: Int, med: String, fingerprint: String): Boolean {
        val old = db.query("SELECT record_uuid,revision,med,fingerprint FROM ansm_lab_operation WHERE dataset=? AND uuid=?", arrayOf(dataset, id)).use {
            if (it.moveToFirst()) listOf(it.getString(0), it.getInt(1).toString(), it.getString(2), it.getString(3)) else null }
        if (old != null) { require(old == listOf(record, revision.toString(), med, fingerprint)) { "SYNTHETIC_OP_CONFLICT" }; return false }
        db.execSQL("INSERT INTO ansm_lab_operation VALUES (?,?,?,?,?,?)", arrayOf(dataset, id, record, revision, med, fingerprint)); return true
    }
}
