package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import net.plainnotes.app.domain.Slot

/** Internal until shared forecasting and UI admission; never exposed through an unscoped integer ID. */
internal data class MeasuredWriteTicket(
    val scope:ReferenceScope,val medicationId:Long,val medicationUuid:String,
    val recordId:Long?,val stateSha256:String,
)
internal data class MeasuredScheduledTicket(val write:MeasuredWriteTicket,val slot:Slot)
internal data class RecordedMeasuredUse(val record:RecordEntity,val receipt:MeasuredConsumptionReceipt)
internal enum class MeasuredPackageAction { VOID, RESTORE }
internal data class MeasuredPackageReceipt(val operationUuid:String,val containerUuid:String,val action:MeasuredPackageAction)
internal enum class ReconciliationCheckpoint { BRIDGES_WRITTEN, ORIGINAL_REVERSED, COUNTS_CAPTURED, RECORD_WRITTEN, CONTEXT_CAPTURED, STOCK_CAPTURED }
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
            .put("rules",rows("schedule_rule","medication_id=? ORDER BY id",arrayOf(medication)))
            .put("times",rows("rule_time","rule_id IN (SELECT id FROM schedule_rule WHERE medication_id=?) ORDER BY id",arrayOf(medication)))
            .put("containers",rows("supply_container","medication_id=? ORDER BY id",arrayOf(medication)))
            .put("package_events",rows("trash_item","kind='CORRECTION' AND EXISTS (SELECT 1 FROM supply_container c WHERE c.medication_id=? AND (trash_item.ref LIKE ('package-void:'||c.id||':%') OR trash_item.ref LIKE ('package-restore:'||c.id||':%'))) ORDER BY id",arrayOf(medication)))
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
    private fun owner(db:SupportSQLiteDatabase,ticket:MeasuredWriteTicket) {
        ReferenceStore.checkScope(db,ticket.scope)
        val identity=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(ticket.medicationUuid)) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
        if(identity.getString("dataset_uuid")!=ticket.scope.dataset || identity.getString("kind")!="MEDICATION" || identity.isNull("live_handle") || identity.getLong("live_handle")!=ticket.medicationId)fail(ReferenceError.UNKNOWN_IDENTITY)
    }
    fun validate(db:SupportSQLiteDatabase,ticket:MeasuredWriteTicket) {
        check(db.inTransaction());owner(db,ticket)
        if(state(db,ticket.scope,ticket.medicationId,ticket.recordId)!=ticket.stateSha256)fail(ReferenceError.STALE_REVISION)
    }
    private fun checkDraft(draft:MeasuredActualUseDraft,created:Instant) {
        require(draft.userConfirmed && draft.clinicalDose.isFinite() && draft.clinicalDose>0)
        require(draft.takenUtc>=0 && draft.takenUtc<=created.plusSeconds(60).toEpochMilli())
        ZoneId.of(draft.takenZone)
    }
    suspend fun create(database:NotesDatabase,ticket:MeasuredWriteTicket,operationUuid:String,draft:MeasuredActualUseDraft,created:Instant,
        checkpoint:(ActualUseCheckpoint)->Unit={}):RecordedMeasuredUse {
        val db=database.openHelper.writableDatabase;check(db.inTransaction());owner(db,ticket)
        require(ReferencePayload.uuid(operationUuid));checkDraft(draft,created)
        if(ticket.recordId!=null)fail(ReferenceError.INVALID_SOURCE)
        val dao=database.dao()
        MeasuredConsumptionStore.existingConsumption(db,operationUuid)?.let{(original,receipt)->
            if(original.datasetUuid!=ticket.scope.dataset || original.medicationUuid!=ticket.medicationUuid || original.record.scheduleSha256!=null)fail(ReferenceError.OPERATION_CONFLICT)
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
    internal suspend fun slotRecord(database:NotesDatabase,key:String):RecordEntity? {
        val id=database.openHelper.writableDatabase.query("SELECT id FROM dose_record WHERE slot_key=?",arrayOf(key)).use{if(it.moveToFirst())it.getLong(0) else null}
        return id?.let{database.dao().recordById(it)}
    }
    suspend fun completeScheduled(database:NotesDatabase,ticket:MeasuredScheduledTicket,operationUuid:String,draft:MeasuredActualUseDraft,created:Instant,
        checkpoint:(ActualUseCheckpoint)->Unit={}):RecordedMeasuredUse {
        val db=database.openHelper.writableDatabase;check(db.inTransaction());owner(db,ticket.write)
        require(ReferencePayload.uuid(operationUuid));checkDraft(draft,created)
        val slot=ticket.slot;val dao=database.dao()
        if(slot.medicationId!=ticket.write.medicationId || slot.skipped)fail(ReferenceError.INVALID_SOURCE)
        val scheduledHash=MeasuredUsePayload.scheduleSha(slot.ruleId,slot.key,slot.at.toEpochMilli(),slot.zone.id,slot.dose,slot.lateMinutes)
        val status=if(draft.takenUtc>slot.at.plusSeconds(slot.lateMinutes.toLong()*60).toEpochMilli())"LATE" else "ON_TIME"
        MeasuredConsumptionStore.existingConsumption(db,operationUuid)?.let{(original,receipt)->
            if(original.datasetUuid!=ticket.write.scope.dataset || original.medicationUuid!=ticket.write.medicationUuid ||
                original.record.scheduleSha256!=scheduledHash || !sameActual(original,draft,status))fail(ReferenceError.OPERATION_CONFLICT)
            val identity=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(original.record.uuid)) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
            if(identity.isNull("live_handle"))fail(ReferenceError.UNKNOWN_IDENTITY)
            val record=dao.recordById(identity.getLong("live_handle")) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
            return RecordedMeasuredUse(record,receipt)
        }
        validate(db,ticket.write)
        val existing=slotRecord(database,slot.key)
        if(existing?.id!=ticket.write.recordId)fail(ReferenceError.STALE_REVISION)
        if(existing!=null && (existing.deleted_at_utc!=null || existing.origin!="APP" || existing.status !in setOf("MISSED","UNCONFIRMED")))fail(ReferenceError.ALREADY_CONSUMED)
        val rule=dao.rules().singleOrNull{it.id==slot.ruleId && it.medication_id==slot.medicationId} ?: fail(ReferenceError.INVALID_SOURCE)
        val record=RecordEntity(id=existing?.id ?: 0,medication_id=slot.medicationId,rule_version_id=slot.ruleId,slot_key=slot.key,
            scheduled_utc=slot.at.toEpochMilli(),scheduled_zone=slot.zone.id,planned_dose=slot.dose,late_after_minutes_snapshot=slot.lateMinutes,
            taken_utc=draft.takenUtc,taken_zone=draft.takenZone,actual_dose=draft.clinicalDose,status=status,origin="APP",revision=(existing?.revision ?: 0)+1,
            config_snapshot=existing?.config_snapshot ?: rule.config_snapshot)
        if(existing!=null && MeasuredUsePayload.scheduleSha(existing)!=scheduledHash)fail(ReferenceError.STALE_REVISION)
        val saved=if(existing==null)record.copy(id=dao.record(record)) else record.also{dao.updateRecord(it)}
        checkpoint(ActualUseCheckpoint.RECORD_WRITTEN)
        ReferenceStore.captureContext(db,saved);checkpoint(ActualUseCheckpoint.CONTEXT_CAPTURED)
        val receipt=MeasuredConsumptionStore.consumeNewRecord(database,ticket.write.scope,saved,operationUuid,draft.uses,created,draft.stockEffect,draft.unlinkedActual)
        checkpoint(ActualUseCheckpoint.STOCK_CAPTURED);MeasuredConsumptionStore.auditStagedDataset(db)
        return RecordedMeasuredUse(saved,receipt)
    }
    suspend fun count(database:NotesDatabase,ticket:MeasuredWriteTicket,operationUuid:String,input:ConfirmedStockCount,created:Instant):MeasuredCountReceipt {
        val db=database.openHelper.writableDatabase;check(db.inTransaction());owner(db,ticket)
        require(ReferencePayload.uuid(operationUuid) && input.userConfirmed)
        if(ticket.recordId!=null)fail(ReferenceError.INVALID_SOURCE)
        MeasuredConsumptionStore.replayCount(db,ticket.scope,ticket.medicationUuid,operationUuid,input)?.let{return it}
        validate(db,ticket)
        val identity=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(input.containerUuid)) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
        if(identity.getString("dataset_uuid")!=ticket.scope.dataset || identity.getString("kind")!="CONTAINER" || identity.getString("owner_uuid")!=ticket.medicationUuid || identity.isNull("live_handle"))fail(ReferenceError.UNKNOWN_IDENTITY)
        val box=database.dao().container(identity.getLong("live_handle"))
        val result=MeasuredConsumptionStore.adjustRemaining(database,ticket.scope,box,input.containerUuid,input.presentationEventUuid,input.presentationSha256,input.remaining,input.userConfirmed,operationUuid,created)
        MeasuredConsumptionStore.auditStagedDataset(db)
        return MeasuredCountReceipt(operationUuid,result)
    }
    private fun sameActual(request:MeasuredConsumptionRequest,draft:MeasuredActualUseDraft,status:String):Boolean {
        val proposed=request.copy(record=request.record.copy(takenUtc=draft.takenUtc,takenZone=draft.takenZone,status=status,
            legacyActualDoseBits=java.lang.Long.toHexString(java.lang.Double.doubleToRawLongBits(draft.clinicalDose)).padStart(16,'0')),
            uses=draft.uses,stockEffect=draft.stockEffect,unlinkedActual=draft.unlinkedActual)
        return MeasuredUsePayload.freeze(proposed).json==MeasuredUsePayload.freeze(request).json
    }
    private fun status(record:RecordEntity,taken:Long)=if(record.scheduled_utc!=null && record.late_after_minutes_snapshot!=null &&
        taken>record.scheduled_utc+record.late_after_minutes_snapshot.toLong()*60_000L)"LATE" else "ON_TIME"
    private suspend fun actualRecord(database:NotesDatabase,ticket:MeasuredWriteTicket):RecordEntity {
        val id=ticket.recordId ?: fail(ReferenceError.INVALID_SOURCE)
        val record=database.dao().recordById(id) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
        if(record.medication_id!=ticket.medicationId || record.origin!="APP" || record.status !in setOf("ON_TIME","LATE"))fail(ReferenceError.INVALID_SOURCE)
        return record
    }
    private fun currentSource(db:SupportSQLiteDatabase,ticket:MeasuredWriteTicket,record:RecordEntity,sourceOperation:String):MeasuredConsumptionRequest {
        val source=MeasuredConsumptionStore.existingConsumption(db,sourceOperation)?.first ?: fail(ReferenceError.UNKNOWN_IDENTITY)
        val identity=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(source.record.uuid)) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
        if(source.datasetUuid!=ticket.scope.dataset || source.medicationUuid!=ticket.medicationUuid || identity.isNull("live_handle") || identity.getLong("live_handle")!=record.id || source.record.revision!=record.revision)fail(ReferenceError.STALE_REVISION)
        return source
    }
    suspend fun revise(database:NotesDatabase,ticket:MeasuredWriteTicket,sourceOperation:String,reverseOperation:String,newOperation:String,
        draft:MeasuredActualUseDraft,created:Instant,checkpoint:(ActualUseCheckpoint)->Unit={}):RecordedMeasuredUse {
        val db=database.openHelper.writableDatabase;check(db.inTransaction());owner(db,ticket)
        require(listOf(sourceOperation,reverseOperation,newOperation).all(ReferencePayload::uuid) && setOf(sourceOperation,reverseOperation,newOperation).size==3)
        checkDraft(draft,created)
        val current=actualRecord(database,ticket)
        MeasuredConsumptionStore.existingConsumption(db,newOperation)?.let{(request,receipt)->
            val inverse=MeasuredConsumptionStore.sourceOfInverse(db,reverseOperation) ?: fail(ReferenceError.OPERATION_CONFLICT)
            val row=ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(newOperation))!!
            val subject=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(request.record.uuid)) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
            if(subject.isNull("live_handle") || subject.getLong("live_handle")!=current.id || request.datasetUuid!=ticket.scope.dataset || request.medicationUuid!=ticket.medicationUuid ||
                inverse.getString("source_operation_uuid")!=sourceOperation || inverse.getString("record_uuid")!=request.record.uuid ||
                JSONObject(row.getString("input_json")).getJSONObject("chain").getString("previous_operation_uuid")!=reverseOperation ||
                !sameActual(request,draft,status(current,draft.takenUtc)))fail(ReferenceError.OPERATION_CONFLICT)
            return RecordedMeasuredUse(current,receipt)
        }
        validate(db,ticket);require(current.deleted_at_utc==null && current.revision<Int.MAX_VALUE)
        currentSource(db,ticket,current,sourceOperation)
        MeasuredConsumptionStore.reverseOriginal(database,ticket.scope,current,sourceOperation,reverseOperation,created)
        val next=current.copy(taken_utc=draft.takenUtc,taken_zone=draft.takenZone,actual_dose=draft.clinicalDose,status=status(current,draft.takenUtc),revision=current.revision+1)
        database.dao().updateRecord(next);checkpoint(ActualUseCheckpoint.RECORD_WRITTEN)
        ReferenceStore.captureContext(db,next);checkpoint(ActualUseCheckpoint.CONTEXT_CAPTURED)
        val result=MeasuredConsumptionStore.consumeNewRecord(database,ticket.scope,next,newOperation,draft.uses,created,draft.stockEffect,draft.unlinkedActual)
        checkpoint(ActualUseCheckpoint.STOCK_CAPTURED);MeasuredConsumptionStore.auditStagedDataset(db)
        return RecordedMeasuredUse(next,result)
    }
    suspend fun reconcileRevise(database:NotesDatabase,ticket:MeasuredWriteTicket,sourceOperation:String,reverseOperation:String,newOperation:String,
        counts:List<ConfirmedStockCount>,draft:MeasuredActualUseDraft,created:Instant,checkpoint:(ReconciliationCheckpoint)->Unit):RecordedMeasuredUse {
        val db=database.openHelper.writableDatabase;check(db.inTransaction());owner(db,ticket)
        require(listOf(sourceOperation,reverseOperation,newOperation).all(ReferencePayload::uuid) && setOf(sourceOperation,reverseOperation,newOperation).size==3)
        checkDraft(draft,created);require(draft.stockEffect!=MeasuredStockEffect.DEDUCT_CURRENT)
        val current=actualRecord(database,ticket)
        MeasuredConsumptionStore.existingConsumption(db,newOperation)?.let{(request,receipt)->
            val inverse=MeasuredConsumptionStore.sourceOfInverse(db,reverseOperation) ?: fail(ReferenceError.OPERATION_CONFLICT)
            val subject=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(request.record.uuid)) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
            if(subject.isNull("live_handle") || subject.getLong("live_handle")!=current.id || request.datasetUuid!=ticket.scope.dataset || request.medicationUuid!=ticket.medicationUuid ||
                inverse.getString("source_operation_uuid")!=sourceOperation || inverse.getString("record_uuid")!=request.record.uuid || !sameActual(request,draft,status(current,draft.takenUtc)))fail(ReferenceError.OPERATION_CONFLICT)
            if(!MeasuredConsumptionStore.replayReconciliation(db,ticket.scope,sourceOperation,reverseOperation,counts))fail(ReferenceError.OPERATION_CONFLICT)
            val targets=MeasuredReconciliation.read(inverse.getJSONObject("reconciliation"))
            val operation=ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(newOperation))!!
            if(JSONObject(operation.getString("input_json")).getJSONObject("chain").getString("previous_operation_uuid")!=targets.last().getString("count_uuid"))fail(ReferenceError.OPERATION_CONFLICT)
            return RecordedMeasuredUse(current,receipt)
        }
        validate(db,ticket);require(current.deleted_at_utc==null && current.revision<Int.MAX_VALUE)
        currentSource(db,ticket,current,sourceOperation)
        MeasuredConsumptionStore.reconcileOriginal(database,ticket.scope,current,sourceOperation,reverseOperation,counts,created,checkpoint)
        val next=current.copy(taken_utc=draft.takenUtc,taken_zone=draft.takenZone,actual_dose=draft.clinicalDose,status=status(current,draft.takenUtc),revision=current.revision+1)
        database.dao().updateRecord(next);checkpoint(ReconciliationCheckpoint.RECORD_WRITTEN)
        ReferenceStore.captureContext(db,next);checkpoint(ReconciliationCheckpoint.CONTEXT_CAPTURED)
        val result=MeasuredConsumptionStore.consumeNewRecord(database,ticket.scope,next,newOperation,draft.uses,created,draft.stockEffect,draft.unlinkedActual)
        checkpoint(ReconciliationCheckpoint.STOCK_CAPTURED);MeasuredConsumptionStore.auditStagedDataset(db)
        return RecordedMeasuredUse(next,result)
    }
    suspend fun reconcileDelete(database:NotesDatabase,ticket:MeasuredWriteTicket,sourceOperation:String,reverseOperation:String,
        counts:List<ConfirmedStockCount>,created:Instant,checkpoint:(ReconciliationCheckpoint)->Unit):RecordEntity {
        val db=database.openHelper.writableDatabase;check(db.inTransaction());owner(db,ticket)
        require(listOf(sourceOperation,reverseOperation).all(ReferencePayload::uuid) && sourceOperation!=reverseOperation)
        val current=actualRecord(database,ticket)
        MeasuredConsumptionStore.sourceOfInverse(db,reverseOperation)?.let{inverse->
            val identity=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(inverse.getString("record_uuid"))) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
            if(inverse.getString("dataset_uuid")!=ticket.scope.dataset || inverse.getString("medication_uuid")!=ticket.medicationUuid || inverse.getString("source_operation_uuid")!=sourceOperation ||
                identity.isNull("live_handle") || identity.getLong("live_handle")!=current.id || current.deleted_at_utc==null || current.revision!=inverse.getInt("record_revision")+1 ||
                !MeasuredConsumptionStore.replayReconciliation(db,ticket.scope,sourceOperation,reverseOperation,counts))fail(ReferenceError.OPERATION_CONFLICT)
            return current
        }
        validate(db,ticket);require(current.deleted_at_utc==null && current.revision<Int.MAX_VALUE)
        currentSource(db,ticket,current,sourceOperation)
        MeasuredConsumptionStore.reconcileOriginal(database,ticket.scope,current,sourceOperation,reverseOperation,counts,created,checkpoint)
        val next=current.copy(deleted_at_utc=created.toEpochMilli(),revision=current.revision+1);database.dao().updateRecord(next);checkpoint(ReconciliationCheckpoint.RECORD_WRITTEN)
        val date=Instant.ofEpochMilli(requireNotNull(current.taken_utc)).atZone(ZoneId.of(requireNotNull(current.taken_zone))).toLocalDate().toString()
        db.execSQL("DELETE FROM trash_item WHERE kind='RECORD' AND ref=?",arrayOf(current.id.toString()))
        Trash.insert(db,Trash.RECORD,current.id.toString(),date,JSONObject(),created.toEpochMilli())
        checkpoint(ReconciliationCheckpoint.CONTEXT_CAPTURED);MeasuredConsumptionStore.auditStagedDataset(db);return next
    }
    suspend fun delete(database:NotesDatabase,ticket:MeasuredWriteTicket,sourceOperation:String,reverseOperation:String,created:Instant):RecordEntity {
        val db=database.openHelper.writableDatabase;check(db.inTransaction());owner(db,ticket)
        require(listOf(sourceOperation,reverseOperation).all(ReferencePayload::uuid) && sourceOperation!=reverseOperation)
        val current=actualRecord(database,ticket)
        MeasuredConsumptionStore.sourceOfInverse(db,reverseOperation)?.let{inverse->
            val identity=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(inverse.getString("record_uuid"))) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
            if(inverse.getString("dataset_uuid")!=ticket.scope.dataset || inverse.getString("medication_uuid")!=ticket.medicationUuid || inverse.getString("source_operation_uuid")!=sourceOperation ||
                identity.isNull("live_handle") || identity.getLong("live_handle")!=current.id || current.deleted_at_utc==null || current.revision!=inverse.getInt("record_revision")+1)fail(ReferenceError.OPERATION_CONFLICT)
            return current
        }
        validate(db,ticket);require(current.deleted_at_utc==null && current.revision<Int.MAX_VALUE)
        currentSource(db,ticket,current,sourceOperation)
        MeasuredConsumptionStore.reverseOriginal(database,ticket.scope,current,sourceOperation,reverseOperation,created)
        val next=current.copy(deleted_at_utc=created.toEpochMilli(),revision=current.revision+1);database.dao().updateRecord(next)
        val date=Instant.ofEpochMilli(requireNotNull(current.taken_utc)).atZone(ZoneId.of(requireNotNull(current.taken_zone))).toLocalDate().toString()
        db.execSQL("DELETE FROM trash_item WHERE kind='RECORD' AND ref=?",arrayOf(current.id.toString()))
        Trash.insert(db,Trash.RECORD,current.id.toString(),date,JSONObject(),created.toEpochMilli())
        MeasuredConsumptionStore.auditStagedDataset(db);return next
    }
    suspend fun restore(database:NotesDatabase,ticket:MeasuredWriteTicket,newOperation:String,draft:MeasuredActualUseDraft,created:Instant,
        checkpoint:(ActualUseCheckpoint)->Unit={}):RecordedMeasuredUse {
        val db=database.openHelper.writableDatabase;check(db.inTransaction());owner(db,ticket)
        require(ReferencePayload.uuid(newOperation));checkDraft(draft,created)
        val current=actualRecord(database,ticket)
        MeasuredConsumptionStore.existingConsumption(db,newOperation)?.let{(request,receipt)->
            val identity=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(request.record.uuid)) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
            if(request.datasetUuid!=ticket.scope.dataset || request.medicationUuid!=ticket.medicationUuid || identity.isNull("live_handle") ||
                identity.getLong("live_handle")!=current.id || !sameActual(request,draft,current.status))fail(ReferenceError.OPERATION_CONFLICT)
            return RecordedMeasuredUse(current,receipt)
        }
        validate(db,ticket);require(current.deleted_at_utc!=null && current.revision<Int.MAX_VALUE)
        require(current.taken_utc==draft.takenUtc && current.taken_zone==draft.takenZone && current.actual_dose?.toRawBits()==draft.clinicalDose.toRawBits())
        val item=database.dao().trash().single{it.kind==Trash.RECORD && it.ref==current.id.toString() && it.state==Trash.TRASHED}
        val next=current.copy(deleted_at_utc=null,revision=current.revision+1);database.dao().updateRecord(next);checkpoint(ActualUseCheckpoint.RECORD_WRITTEN)
        AttachmentIdentity.restored(db,item);db.execSQL("DELETE FROM trash_item WHERE id=?",arrayOf(item.id))
        ReferenceStore.captureContext(db,next);checkpoint(ActualUseCheckpoint.CONTEXT_CAPTURED)
        val result=MeasuredConsumptionStore.consumeNewRecord(database,ticket.scope,next,newOperation,draft.uses,created,draft.stockEffect,draft.unlinkedActual)
        checkpoint(ActualUseCheckpoint.STOCK_CAPTURED);MeasuredConsumptionStore.auditStagedDataset(db)
        return RecordedMeasuredUse(next,result)
    }

    suspend fun packageAction(database:NotesDatabase,ticket:MeasuredWriteTicket,operationUuid:String,containerUuid:String,action:MeasuredPackageAction,userConfirmed:Boolean,created:Instant):MeasuredPackageReceipt {
        val db=database.openHelper.writableDatabase;check(db.inTransaction());owner(db,ticket)
        require(ReferencePayload.uuid(operationUuid) && ReferencePayload.uuid(containerUuid) && userConfirmed && ticket.recordId==null)
        val identity=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(containerUuid)) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
        if(identity.getString("dataset_uuid")!=ticket.scope.dataset || identity.getString("kind")!="CONTAINER" || identity.getString("owner_uuid")!=ticket.medicationUuid || identity.isNull("live_handle"))fail(ReferenceError.UNKNOWN_IDENTITY)
        val id=identity.getLong("live_handle");val verb=action.name.lowercase()
        val result=MeasuredPackageReceipt(operationUuid,containerUuid,action)
        val old=db.query("SELECT ref,payload_json FROM trash_item WHERE kind='CORRECTION' AND ref LIKE ?",arrayOf("%:$operationUuid")).use{c->
            if(!c.moveToFirst())null else {val row=c.getString(0) to JSONObject(c.getString(1));if(c.moveToNext())fail(ReferenceError.INVALID_SOURCE);row}
        }
        if(old!=null) {
            if(old.first!="package-$verb:$id:$operationUuid" || old.second.optInt("version")!=2 || old.second.optString("container_uuid")!=containerUuid)fail(ReferenceError.OPERATION_CONFLICT)
            return result
        }
        for(table in listOf("measured_operation","reference_event"))db.query("SELECT 1 FROM $table WHERE operation_uuid=?",arrayOf(operationUuid)).use{if(it.moveToFirst())fail(ReferenceError.OPERATION_CONFLICT)}
        validate(db,ticket)
        val box=database.dao().container(id)
        if(box.inventory_encoding!=InventoryEncoding.EXACT)fail(ReferenceError.INVALID_SOURCE)
        if((action==MeasuredPackageAction.VOID && box.state=="VOIDED") || (action==MeasuredPackageAction.RESTORE && box.state!="VOIDED"))fail(ReferenceError.STALE_REVISION)
        if(action==MeasuredPackageAction.VOID)PackageCorrections.void(db,database.dao(),id,created,operationUuid)
        else PackageCorrections.restore(db,database.dao(),id,created,operationUuid)
        MeasuredConsumptionStore.auditStagedDataset(db)
        return result
    }

}
