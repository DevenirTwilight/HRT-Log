package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** Internal until shared forecasting and UI admission; never exposed through an unscoped integer ID. */
internal data class MeasuredWriteTicket(
    val scope:ReferenceScope,val medicationId:Long,val medicationUuid:String,
    val recordId:Long?,val stateSha256:String,
)
internal data class RecordedMeasuredUse(val record:RecordEntity,val receipt:MeasuredConsumptionReceipt)
internal enum class ActualUseCheckpoint { RECORD_WRITTEN, CONTEXT_CAPTURED, STOCK_CAPTURED }

/** Transaction helpers, not a second allocator. All physical conversion remains in MeasuredConsumptionStore. */
internal object MeasuredUseIntegration {
    private fun fail(code:ReferenceError):Nothing=throw ReferenceFailure(code)
    private fun medIdentity(db:SupportSQLiteDatabase,scope:ReferenceScope,id:Long):JSONObject =
        ReferenceStore.row(db,"reference_identity","dataset_uuid=? AND kind='MEDICATION' AND live_handle=?",arrayOf(scope.dataset,id)) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
    private fun state(db:SupportSQLiteDatabase,scope:ReferenceScope,medication:Long,record:Long?):String {
        fun rows(table:String,where:String,args:Array<Any?>)=Trash.rows(db,table,where,args).toString()
        val chain=MeasuredOperationChain.validate(db).values.lastOrNull()
        val json=JSONObject().put("medication",rows("medication","id=?",arrayOf(medication)))
            .put("profile",rows("pk_profile","medication_id=?",arrayOf(medication)))
            .put("containers",rows("supply_container","medication_id=? ORDER BY id",arrayOf(medication)))
            .put("ledger",rows("supply_transaction","container_id IN (SELECT id FROM supply_container WHERE medication_id=?) ORDER BY id",arrayOf(medication)))
            .put("references",rows("reference_event","subject_uuid IN (SELECT uuid FROM reference_identity WHERE dataset_uuid=? AND (uuid=? OR owner_uuid=?)) ORDER BY subject_uuid,revision",arrayOf(scope.dataset,medIdentity(db,scope,medication).getString("uuid"),medIdentity(db,scope,medication).getString("uuid"))))
            .put("chain_sequence",chain?.sequence ?: 0).put("chain_sha256",chain?.payloadSha256 ?: JSONObject.NULL)
            .put("record",record?.let{rows("dose_record","id=? AND medication_id=?",arrayOf(it,medication))} ?: JSONObject.NULL)
        return ReferencePayload.sha(json.toString())
    }
    fun ticket(db:SupportSQLiteDatabase,scope:ReferenceScope,medication:Long,record:Long?=null):MeasuredWriteTicket {
        check(db.inTransaction());ReferenceStore.checkScope(db,scope)
        val identity=medIdentity(db,scope,medication)
        if(record!=null)db.query("SELECT 1 FROM dose_record WHERE id=? AND medication_id=?",arrayOf(record,medication)).use{if(!it.moveToFirst())fail(ReferenceError.UNKNOWN_IDENTITY)}
        return MeasuredWriteTicket(scope,medication,identity.getString("uuid"),record,state(db,scope,medication,record))
    }
    fun validate(db:SupportSQLiteDatabase,ticket:MeasuredWriteTicket) {
        check(db.inTransaction());ReferenceStore.checkScope(db,ticket.scope)
        val identity=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(ticket.medicationUuid)) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
        if(identity.getString("dataset_uuid")!=ticket.scope.dataset || identity.getString("kind")!="MEDICATION" || identity.isNull("live_handle") || identity.getLong("live_handle")!=ticket.medicationId)fail(ReferenceError.UNKNOWN_IDENTITY)
        if(state(db,ticket.scope,ticket.medicationId,ticket.recordId)!=ticket.stateSha256)fail(ReferenceError.STALE_REVISION)
    }
    private fun checkDraft(draft:MeasuredActualUseDraft,created:Instant) {
        require(draft.userConfirmed && draft.clinicalDose.isFinite() && draft.clinicalDose>0)
        require(draft.takenUtc>=0 && draft.takenUtc<=created.plusSeconds(60).toEpochMilli())
        ZoneId.of(draft.takenZone)
    }
    suspend fun create(database:NotesDatabase,ticket:MeasuredWriteTicket,operationUuid:String,draft:MeasuredActualUseDraft,created:Instant,
        checkpoint:(ActualUseCheckpoint)->Unit={}):RecordedMeasuredUse {
        val db=database.openHelper.writableDatabase;check(db.inTransaction());ReferenceStore.checkScope(db,ticket.scope)
        require(ReferencePayload.uuid(operationUuid));checkDraft(draft,created)
        if(ticket.recordId!=null)fail(ReferenceError.INVALID_SOURCE)
        val dao=database.dao()
        MeasuredConsumptionStore.existingConsumption(db,operationUuid)?.let{(original,receipt)->
            if(original.datasetUuid!=ticket.scope.dataset || original.medicationUuid!=ticket.medicationUuid)fail(ReferenceError.OPERATION_CONFLICT)
            val proposed=original.copy(record=original.record.copy(takenUtc=draft.takenUtc,takenZone=draft.takenZone,status="ON_TIME",legacyActualDoseBits=java.lang.Long.toHexString(java.lang.Double.doubleToRawLongBits(draft.clinicalDose)).padStart(16,'0')),
                uses=draft.uses,stockEffect=draft.stockEffect,unlinkedActual=draft.unlinkedActual)
            if(MeasuredUsePayload.freeze(proposed).json!=MeasuredUsePayload.freeze(original).json)fail(ReferenceError.OPERATION_CONFLICT)
            val identity=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(original.record.uuid)) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
            if(identity.isNull("live_handle"))fail(ReferenceError.UNKNOWN_IDENTITY)
            val record=dao.recordById(identity.getLong("live_handle")) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
            return RecordedMeasuredUse(record,receipt)
        }
        validate(db,ticket)
        val medication=dao.medication(ticket.medicationId)
        val record=RecordEntity(medication_id=medication.id,taken_utc=draft.takenUtc,taken_zone=draft.takenZone,actual_dose=draft.clinicalDose,
            status="ON_TIME",origin="APP",revision=1,config_snapshot=MedicationSnapshot.encode(medication,dao.profile(medication.id)))
        val saved=record.copy(id=dao.record(record));checkpoint(ActualUseCheckpoint.RECORD_WRITTEN)
        ReferenceStore.captureContext(db,saved);checkpoint(ActualUseCheckpoint.CONTEXT_CAPTURED)
        val receipt=MeasuredConsumptionStore.consumeNewRecord(database,ticket.scope,saved,operationUuid,draft.uses,created,draft.stockEffect,draft.unlinkedActual)
        checkpoint(ActualUseCheckpoint.STOCK_CAPTURED)
        MeasuredConsumptionStore.auditStagedDataset(db)
        return RecordedMeasuredUse(saved,receipt)
    }
}
