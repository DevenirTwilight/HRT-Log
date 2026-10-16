package net.plainnotes.app.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject
import java.util.UUID

/** All mutations require the caller's Room transaction. No legacy medication/record/inventory writes here. */
internal object ReferenceStore {
    private fun id() = UUID.randomUUID().toString()
    private fun fail(code: ReferenceError): Nothing = throw ReferenceFailure(code)
    internal fun row(db: SupportSQLiteDatabase, table: String, where: String, args: Array<out Any?>): JSONObject? {
        require(table in ReferenceSchema.businessTables || table == "reference_runtime")
        return db.query("SELECT * FROM `$table` WHERE $where", args).use { c ->
            if (!c.moveToFirst()) null else JSONObject().apply {
                for (i in 0 until c.columnCount) put(c.getColumnName(i), when (c.getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                    Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                    Cursor.FIELD_TYPE_STRING -> c.getString(i)
                    else -> error("Unexpected reference storage type")
                })
            }
        }
    }
    private fun insert(db: SupportSQLiteDatabase, table: String, value: JSONObject) {
        val allowed = if(table == "reference_runtime") setOf("id","dataset_uuid","epoch") else ReferenceSchema.columns.getValue(table).toSet()
        require(value.keys().asSequence().all { it in allowed })
        val cv = ContentValues()
        value.keys().forEach { key -> when(val v = value.get(key)) {
            JSONObject.NULL -> cv.putNull(key)
            is String -> cv.put(key, v)
            is Int -> cv.put(key, v)
            is Long -> cv.put(key, v)
            else -> error("Exact storage requires strings or integers")
        } }
        db.insert(table, SQLiteDatabase.CONFLICT_ABORT, cv)
    }
    fun scope(db: SupportSQLiteDatabase): ReferenceScope {
        check(db.inTransaction())
        row(db,"reference_runtime","id=1",emptyArray())?.let { runtime ->
            val dataset=runtime.getString("dataset_uuid");val epoch=runtime.getString("epoch")
            if(!ReferencePayload.uuid(dataset) || !ReferencePayload.uuid(epoch) || row(db,"reference_dataset","uuid=?",arrayOf(dataset))==null) fail(ReferenceError.STALE_SCOPE)
            return ReferenceScope(dataset,epoch)
        }
        val datasets=db.query("SELECT uuid FROM reference_dataset").use { c ->
            if(c.moveToFirst()) c.getString(0).also { require(!c.moveToNext()) } else null
        }
        val dataset=datasets ?: id().also { uuid -> insert(db,"reference_dataset",JSONObject().put("uuid",uuid).put("created_utc",System.currentTimeMillis())) }
        val epoch=id()
        insert(db,"reference_runtime",JSONObject().put("id",1).put("dataset_uuid",dataset).put("epoch",epoch))
        return ReferenceScope(dataset,epoch)
    }
    fun checkScope(db: SupportSQLiteDatabase, expected: ReferenceScope) {
        check(db.inTransaction())
        val current=row(db,"reference_runtime","id=1",emptyArray()) ?: fail(ReferenceError.STALE_SCOPE)
        if(current.getString("dataset_uuid")!=expected.dataset || current.getString("epoch")!=expected.epoch) fail(ReferenceError.STALE_SCOPE)
    }
    private fun identity(db: SupportSQLiteDatabase, scope: ReferenceScope, kind: String, handle: Long, owner: String?): String {
        checkScope(db,scope)
        row(db,"reference_identity","dataset_uuid=? AND kind=? AND live_handle=?",arrayOf(scope.dataset,kind,handle))?.let {
            if(ReferencePayload.optional(it,"owner_uuid")!=owner) fail(ReferenceError.UNKNOWN_IDENTITY)
            return it.getString("uuid")
        }
        val table=when(kind) { "MEDICATION" -> "medication"; "RECORD" -> "dose_record"; "CONTAINER" -> "supply_container"; else -> error("Invalid kind") }
        db.query("SELECT 1 FROM `$table` WHERE id=?",arrayOf(handle)).use { if(!it.moveToFirst()) fail(ReferenceError.UNKNOWN_IDENTITY) }
        val uuid=id()
        insert(db,"reference_identity",JSONObject().put("uuid",uuid).put("dataset_uuid",scope.dataset).put("kind",kind)
            .put("original_handle",handle).put("live_handle",handle).put("owner_uuid",owner ?: JSONObject.NULL))
        return uuid
    }
    private fun read(db: SupportSQLiteDatabase, event: JSONObject): MedicationReference {
        val snapshot=ReferencePayload.optional(event,"snapshot_uuid")
        val product=snapshot?.let { uuid ->
            val stored=row(db,"reference_snapshot","uuid=?",arrayOf(uuid)) ?: fail(ReferenceError.INVALID_SOURCE)
            val payload=stored.getString("payload_json")
            if(ReferencePayload.sha(payload)!=stored.getString("payload_sha256")) fail(ReferenceError.INVALID_SOURCE)
            ReferencePayload.readProduct(JSONObject(payload))
        }
        return MedicationReference(event.getString("subject_uuid"),event.getInt("revision"),event.getString("operation_uuid"),snapshot,product)
    }
    fun current(db: SupportSQLiteDatabase, scope: ReferenceScope, medication: Long): MedicationReference? {
        checkScope(db,scope)
        val subject=row(db,"reference_identity","dataset_uuid=? AND kind='MEDICATION' AND live_handle=?",arrayOf(scope.dataset,medication)) ?: return null
        val event=row(db,"reference_event","subject_uuid=? ORDER BY revision DESC LIMIT 1",arrayOf(subject.getString("uuid"))) ?: return null
        return read(db,event)
    }
    fun associate(db: SupportSQLiteDatabase, scope: ReferenceScope, medication: Long, expectedRevision: Int, operation: String, product: UserReferenceProduct?): MedicationReference {
        checkScope(db,scope)
        require(ReferencePayload.uuid(operation) && expectedRevision>=0 && expectedRevision<Int.MAX_VALUE)
        val payload=product?.let { try { ReferencePayload.userProduct(it) } catch (_: Exception) { fail(ReferenceError.INVALID_SOURCE) } }
        val canonical=payload?.let(ReferencePayload::canonical)
        val subject=identity(db,scope,"MEDICATION",medication,null)
        val previous=if(expectedRevision==0) null else row(db,"reference_event","subject_uuid=? AND revision=?",arrayOf(subject,expectedRevision))
        val action=if(product==null) "UNLINK" else if(previous?.let { ReferencePayload.optional(it,"snapshot_uuid") }==null) "LINK" else "CORRECT"
        val hash=requestHash(scope.dataset,subject,expectedRevision,action,payload)
        row(db,"measured_operation","operation_uuid=?",arrayOf(operation))?.let { fail(ReferenceError.OPERATION_CONFLICT) }
        row(db,"reference_event","operation_uuid=?",arrayOf(operation))?.let { stored ->
            if(stored.getString("dataset_uuid")!=scope.dataset || stored.getString("subject_uuid")!=subject || stored.getString("request_sha256")!=hash) fail(ReferenceError.OPERATION_CONFLICT)
            return read(db,stored)
        }
        val head=current(db,scope,medication)
        if((head?.revision ?: 0)!=expectedRevision) fail(ReferenceError.STALE_REVISION)
        if(product==null && head?.snapshotUuid==null) fail(ReferenceError.INVALID_SOURCE)
        val snapshot=canonical?.let { value -> id().also { uuid ->
            insert(db,"reference_snapshot",JSONObject().put("uuid",uuid).put("dataset_uuid",scope.dataset).put("kind","PRODUCT")
                .put("namespace","USER_ENTERED").put("source_version",product!!.sourceVersion).put("payload_json",value).put("payload_sha256",ReferencePayload.sha(value)))
        } }
        val event=JSONObject().put("operation_uuid",operation).put("dataset_uuid",scope.dataset).put("subject_uuid",subject)
            .put("revision",expectedRevision+1).put("previous_uuid",head?.operationUuid ?: JSONObject.NULL).put("action",action)
            .put("snapshot_uuid",snapshot ?: JSONObject.NULL).put("request_sha256",hash).put("created_utc",System.currentTimeMillis())
        insert(db,"reference_event",event)
        return read(db,event)
    }
    fun requestHash(dataset: String, subject: String, expectedRevision: Int, action: String, payload: JSONObject?): String {
        val request=JSONObject().put("dataset",dataset).put("subject",subject).put("expected_revision",expectedRevision).put("action",action).put("product",payload ?: JSONObject.NULL)
        return ReferencePayload.sha(ReferencePayload.canonical(request))
    }
    fun captureHash(capture: JSONObject): String {
        val facts=JSONObject(capture.toString()).apply { remove("payload_sha256") }
        return ReferencePayload.sha(ReferencePayload.canonical(facts))
    }
    /** Only called for newly recorded actual facts, never by link/correct or legacy migration/import. */
    fun captureContext(db: SupportSQLiteDatabase, record: RecordEntity) {
        check(db.inTransaction())
        val runtime=row(db,"reference_runtime","id=1",emptyArray()) ?: return
        val scope=ReferenceScope(runtime.getString("dataset_uuid"),runtime.getString("epoch"))
        val linked=current(db,scope,record.medication_id) ?: return
        if(linked.snapshotUuid==null) return
        val uuid=identity(db,scope,"RECORD",record.id,linked.medicationUuid)
        if(row(db,"record_reference_capture","record_uuid=? AND record_revision=?",arrayOf(uuid,record.revision))!=null) return
        val capture=JSONObject().put("uuid",id()).put("dataset_uuid",scope.dataset).put("record_uuid",uuid).put("record_revision",record.revision)
            .put("capture_revision",1).put("kind","CONTEXT").put("context_event_uuid",linked.operationUuid)
            .put("actual_snapshot_uuid",JSONObject.NULL).put("container_uuid",JSONObject.NULL)
        capture.put("payload_sha256",captureHash(capture))
        insert(db,"record_reference_capture",capture)
    }
}
