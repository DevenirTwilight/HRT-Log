package net.plainnotes.app.data

import net.plainnotes.app.domain.measurement.*
import net.plainnotes.app.domain.stock.StockQuantum
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

internal sealed interface PhysicalUseResult {
    val containerUuid:String
    data class Known(override val containerUuid:String,val requested:Quantity,val allocated:Quantity,val unallocated:Quantity):PhysicalUseResult
    data class Unknown(override val containerUuid:String,val requested:Quantity,val reason:String):PhysicalUseResult
}
internal data class MeasuredConsumptionReceipt(val operationUuid:String,val recordUuid:String,val recordRevision:Int,val lines:List<PhysicalUseResult>,val issue:String?)

/** Isolated P4 adapter. No Repository/App caller yet; backup acceptance/legacy-path isolation remain disabled.
 * Caller must create the actual record and invoke this adapter in the same captured database transaction.
 * A revision/correction with prior stock history needs the dedicated inverse workflow, never this new-use path.
 */
internal object MeasuredConsumptionStore {
    private fun fail(code:ReferenceError):Nothing=throw ReferenceFailure(code)
    private data class Plan(val input:ConfirmedStockUse,val container:ContainerEntity?,val snapshot:String,val scale:StockQuantum?,
        val requested:Quantity?,val wanted:Long?,val used:Long?,val take:Long,val issue:String?)
    private fun amount(scale:StockQuantum,n:Long)=scale.quantum.copy(value=scale.quantum.value.multiply(BigDecimal.valueOf(n)))
    private fun captureJson(p:Plan)=JSONObject().put("container_uuid",p.input.containerUuid).put("presentation_event_uuid",p.input.presentationEventUuid)
        .put("snapshot_uuid",p.snapshot).put("requested_stock",p.requested?.let(PackageMeasurementPayload::quantity) ?: JSONObject.NULL)
        .put("requested_quanta",p.wanted?.toString() ?: JSONObject.NULL).put("used_before_quanta",p.used?.toString() ?: JSONObject.NULL)
        .put("take_quanta",p.take.toString()).put("issue",p.issue ?: JSONObject.NULL)
    private fun receipt(row:JSONObject):MeasuredConsumptionReceipt = try {readReceipt(row)} catch(e:Exception){fail(ReferenceError.INVALID_SOURCE)}
    private fun readReceipt(row:JSONObject):MeasuredConsumptionReceipt {
        val envelope=JSONObject(row.getString("input_json"));val request=MeasuredUsePayload.read(ReferencePayload.canonical(envelope.getJSONObject("request"))).request()
        if(ReferencePayload.canonical(envelope)!=row.getString("input_json") || envelope.getInt("format")!=1 ||
            ReferencePayload.sha(ReferencePayload.canonical(envelope.getJSONObject("request")))!=row.getString("request_sha256") ||
            row.getString("dataset_uuid")!=request.datasetUuid || row.getString("record_uuid")!=request.record.uuid || row.getInt("record_revision")!=request.record.revision)fail(ReferenceError.INVALID_SOURCE)
        val captures=envelope.getJSONArray("captures");require(captures.length()==request.uses.size)
        val lines=request.uses.mapIndexed {i,input->
            val c=captures.getJSONObject(i);require(c.getString("container_uuid")==input.containerUuid && c.getString("presentation_event_uuid")==input.presentationEventUuid)
            if(c.isNull("requested_stock"))PhysicalUseResult.Unknown(input.containerUuid,input.quantity,c.getString("issue"))
            else {
                val q=PackageMeasurementPayload.quantity(c.getJSONObject("requested_stock"));val wanted=BigDecimal(c.getString("requested_quanta"));val take=BigDecimal(c.getString("take_quanta"));require(wanted.signum()>0 && take.signum()>=0 && take<=wanted)
                val allocated=q.copy(value=if(take.signum()==0)BigDecimal.ZERO else q.value.multiply(take).divide(wanted).stripTrailingZeros())
                PhysicalUseResult.Known(input.containerUuid,q,allocated,q.copy(value=q.value.subtract(allocated.value).stripTrailingZeros()))
            }
        }
        return MeasuredConsumptionReceipt(row.getString("operation_uuid"),request.record.uuid,request.record.revision,lines,ReferencePayload.optional(row,"unknown_reason"))
    }
    /** Inverse uses only original immutable allocation/ledger facts, never current product strength or catalogue data. */
    suspend fun reverseOriginal(database:NotesDatabase,scope:ReferenceScope,expected:RecordEntity,sourceOperation:String,
        operationUuid:String,created:Instant):Int {
        val db=database.openHelper.writableDatabase;check(db.inTransaction());ReferenceStore.checkScope(db,scope)
        if(!ReferencePayload.uuid(operationUuid) || !ReferencePayload.uuid(sourceOperation) || created.toEpochMilli()<0)fail(ReferenceError.INVALID_SOURCE)
        val dao=database.dao();val current=dao.recordById(expected.id) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
        val owner=ReferenceStore.identity(db,scope,"MEDICATION",current.medication_id,null)
        val record=ReferenceStore.identity(db,scope,"RECORD",current.id,owner)
        val source=ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(sourceOperation)) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
        if(source.getString("dataset_uuid")!=scope.dataset || source.getString("record_uuid")!=record || source.getString("kind")!="CONSUME")fail(ReferenceError.UNKNOWN_IDENTITY)
        val allocationIds=db.query("SELECT uuid FROM measured_allocation WHERE operation_uuid=? ORDER BY uuid",arrayOf(sourceOperation)).use{c->buildList{while(c.moveToNext())add(c.getString(0))}}
        val input=JSONObject().put("format",1).put("kind","REVERSE").put("dataset_uuid",scope.dataset).put("medication_uuid",owner)
            .put("record_uuid",record).put("record_revision",expected.revision).put("source_operation_uuid",sourceOperation)
            .put("source_request_sha256",source.getString("request_sha256")).put("source_allocation_uuids",JSONArray(allocationIds))
        val canonical=ReferencePayload.canonical(input);val hash=ReferencePayload.sha(canonical)
        if(ReferenceStore.row(db,"reference_event","operation_uuid=?",arrayOf(operationUuid))!=null)fail(ReferenceError.OPERATION_CONFLICT)
        ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(operationUuid))?.let {old->
            if(old.getString("dataset_uuid")!=scope.dataset || old.getString("kind")!="REVERSE" || old.getString("request_sha256")!=hash || old.getString("input_json")!=canonical)fail(ReferenceError.OPERATION_CONFLICT)
            return allocationIds.size
        }
        if(current!=expected || current.revision<source.getInt("record_revision"))fail(ReferenceError.STALE_REVISION)
        db.query("SELECT input_json FROM measured_operation WHERE record_uuid=? AND kind='REVERSE'",arrayOf(record)).use {c->while(c.moveToNext()) {
            if(JSONObject(c.getString(0)).getString("source_operation_uuid")==sourceOperation)fail(ReferenceError.OPERATION_CONFLICT)
        }}
        val entries=dao.supplyFor(current.id);val byId=entries.associateBy{it.id};val reversed=entries.mapNotNull{it.reversal_of_id}.toSet()
        val originals=allocationIds.map {id->
            val a=ReferenceStore.row(db,"measured_allocation","uuid=?",arrayOf(id))!!
            if(a.getString("dataset_uuid")!=scope.dataset || !a.isNull("reverses_uuid"))fail(ReferenceError.INVALID_SOURCE)
            val entry=byId[a.getLong("ledger_entry_id")] ?: fail(ReferenceError.INVALID_SOURCE)
            if(entry.kind!="CONSUME" || entry.id in reversed || entry.operation_id!=sourceOperation || !entry.used_delta.isFinite() || entry.used_delta<=0 ||
                entry.used_delta>StockQuantum.MAX_QUANTA || entry.used_delta.toLong().toDouble()!=entry.used_delta)fail(ReferenceError.OPERATION_CONFLICT)
            val container=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(a.getString("container_uuid"))) ?: fail(ReferenceError.INVALID_SOURCE)
            if(container.getString("dataset_uuid")!=scope.dataset || ReferencePayload.optional(container,"owner_uuid")!=owner || container.isNull("live_handle") || container.getLong("live_handle")!=entry.container_id)fail(ReferenceError.UNKNOWN_IDENTITY)
            val box=dao.container(entry.container_id)
            if(box.state=="VOIDED" || box.used_amount<entry.used_delta || !box.used_amount.isFinite() || box.used_amount.toLong().toDouble()!=box.used_amount)fail(ReferenceError.OPERATION_CONFLICT)
            val quantity=Measurements.parse(a.getString("quantity"));if(quantity !is MeasureResult.Success || quantity.value.signum()<=0 || (Measurements.decimalText(quantity.value) as MeasureResult.Success).value!=a.getString("quantity"))fail(ReferenceError.INVALID_SOURCE)
            MeasureUnit.valueOf(a.getString("unit"))
            Triple(a,entry,box)
        }
        ReferenceStore.insert(db,"measured_operation",JSONObject().put("operation_uuid",operationUuid).put("dataset_uuid",scope.dataset).put("record_uuid",record)
            .put("record_revision",current.revision).put("kind","REVERSE").put("request_sha256",hash).put("input_json",canonical)
            .put("unallocated_quantity",JSONObject.NULL).put("unallocated_unit",JSONObject.NULL).put("unknown_reason",JSONObject.NULL).put("created_utc",created.toEpochMilli()))
        originals.forEach {(a,entry,box)->
            val reverse=dao.supply(SupplyEntryEntity(container_id=entry.container_id,dose_record_id=current.id,dose_revision=current.revision,operation_id=operationUuid,
                kind="REVERSE",used_delta=-entry.used_delta,reversal_of_id=entry.id,created_utc=created.toEpochMilli(),created_zone="UTC",reason="reverse frozen physical stock use"))
            ReferenceStore.insert(db,"measured_allocation",JSONObject().put("uuid",UUID.randomUUID().toString()).put("dataset_uuid",scope.dataset).put("operation_uuid",operationUuid)
                .put("container_uuid",a.getString("container_uuid")).put("presentation_event_uuid",a.getString("presentation_event_uuid")).put("ledger_entry_id",reverse)
                .put("quantity",a.getString("quantity")).put("unit",a.getString("unit")).put("reverses_uuid",a.getString("uuid")))
            if(box.state=="EMPTY")dao.setContainerState(box.id,"IN_USE",box.opened_on)
        }
        return originals.size
    }

    /** Explicit physical remaining-count correction. Zero is a real target, never an unknown value. */
    suspend fun adjustRemaining(database:NotesDatabase,scope:ReferenceScope,expected:ContainerEntity,containerUuid:String,
        presentationEvent:String,presentationSha:String,remaining:Quantity,userConfirmed:Boolean,operationUuid:String,created:Instant):MeasureResult<Quantity> {
        val db=database.openHelper.writableDatabase;check(db.inTransaction());ReferenceStore.checkScope(db,scope)
        if(!userConfirmed || !ReferencePayload.uuid(operationUuid) || created.toEpochMilli()<0)fail(ReferenceError.INVALID_SOURCE)
        val dao=database.dao();val identity=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(containerUuid)) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
        if(identity.getString("dataset_uuid")!=scope.dataset || identity.getString("kind")!="CONTAINER" || identity.isNull("live_handle") || identity.getLong("live_handle")!=expected.id)fail(ReferenceError.UNKNOWN_IDENTITY)
        val event=ReferenceStore.row(db,"reference_event","operation_uuid=?",arrayOf(presentationEvent)) ?: fail(ReferenceError.INVALID_SOURCE)
        if(event.getString("dataset_uuid")!=scope.dataset || event.getString("subject_uuid")!=containerUuid || event.isNull("snapshot_uuid"))fail(ReferenceError.INVALID_SOURCE)
        val head=ReferenceStore.row(db,"reference_event","subject_uuid=? ORDER BY revision DESC LIMIT 1",arrayOf(containerUuid))!!
        if(head.getString("operation_uuid")!=presentationEvent)fail(ReferenceError.STALE_REVISION)
        val snapshot=ReferenceStore.row(db,"reference_snapshot","uuid=?",arrayOf(event.getString("snapshot_uuid"))) ?: fail(ReferenceError.INVALID_SOURCE)
        val frozen=PackageMeasurementPayload.read(snapshot.getString("payload_json"))
        if(snapshot.getString("dataset_uuid")!=scope.dataset || snapshot.getString("kind")!="PRESENTATION" || snapshot.getString("payload_sha256")!=presentationSha || frozen.sha256!=presentationSha)fail(ReferenceError.INVALID_SOURCE)
        val scale=frozen.stockScale();if(scale is MeasureResult.Failure)return scale
        val stock=(scale as MeasureResult.Success).value
        val normalized=Measurements.add(stock.quantum.copy(value=BigDecimal.ZERO),remaining)
        if(normalized is MeasureResult.Failure)return normalized
        val target=(normalized as MeasureResult.Success).value
        val count=if(target.value.signum()==0)MeasureResult.Success(0L) else stock.toQuanta(target,ActualPackage(stock.ref,true))
        if(count is MeasureResult.Failure)return count
        val remainingQuanta=(count as MeasureResult.Success).value
        if(expected.capacity!=stock.capacityQuanta.toDouble() || expected.initial_used_amount!=0.0 || !expected.used_amount.isFinite() || expected.used_amount<0 ||
            expected.used_amount>stock.capacityQuanta || expected.used_amount.toLong().toDouble()!=expected.used_amount)fail(ReferenceError.INVALID_SOURCE)
        val input=JSONObject().put("format",1).put("kind","ADJUST").put("dataset_uuid",scope.dataset).put("medication_uuid",identity.getString("owner_uuid"))
            .put("container_uuid",containerUuid).put("presentation_event_uuid",presentationEvent).put("presentation_sha256",presentationSha)
            .put("expected_used_quanta",expected.used_amount.toLong().toString()).put("expected_state",expected.state).put("expected_opened_on",expected.opened_on ?: JSONObject.NULL)
            .put("remaining_input",PackageMeasurementPayload.quantity(remaining)).put("remaining_stock",PackageMeasurementPayload.quantity(target)).put("remaining_quanta",remainingQuanta.toString())
        val canonical=ReferencePayload.canonical(input);val hash=ReferencePayload.sha(canonical)
        if(ReferenceStore.row(db,"reference_event","operation_uuid=?",arrayOf(operationUuid))!=null)fail(ReferenceError.OPERATION_CONFLICT)
        ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(operationUuid))?.let {old->
            if(old.getString("dataset_uuid")!=scope.dataset || old.getString("kind")!="ADJUST" || old.getString("request_sha256")!=hash || old.getString("input_json")!=canonical)fail(ReferenceError.OPERATION_CONFLICT)
            return MeasureResult.Success(target)
        }
        val current=dao.container(expected.id);if(current!=expected)fail(ReferenceError.STALE_REVISION)
        if(current.state=="VOIDED")return MeasureResult.Failure(MeasureError.VOIDED)
        val newUsed=stock.capacityQuanta-remainingQuanta;val delta=newUsed-current.used_amount.toLong()
        ReferenceStore.insert(db,"measured_operation",JSONObject().put("operation_uuid",operationUuid).put("dataset_uuid",scope.dataset).put("record_uuid",JSONObject.NULL)
            .put("record_revision",JSONObject.NULL).put("kind","ADJUST").put("request_sha256",hash).put("input_json",canonical)
            .put("unallocated_quantity",JSONObject.NULL).put("unallocated_unit",JSONObject.NULL).put("unknown_reason",JSONObject.NULL).put("created_utc",created.toEpochMilli()))
        if(delta!=0L) {
            val entry=dao.supply(SupplyEntryEntity(container_id=current.id,operation_id=operationUuid,kind="ADJUST",used_delta=delta.toDouble(),
                created_utc=created.toEpochMilli(),created_zone="UTC",reason="confirmed physical remaining count"))
            ReferenceStore.insert(db,"measured_allocation",JSONObject().put("uuid",UUID.randomUUID().toString()).put("dataset_uuid",scope.dataset).put("operation_uuid",operationUuid)
                .put("container_uuid",containerUuid).put("presentation_event_uuid",presentationEvent).put("ledger_entry_id",entry)
                .put("quantity",(Measurements.decimalText(amount(stock,kotlin.math.abs(delta)).value) as MeasureResult.Success).value).put("unit",stock.quantum.unit.name).put("reverses_uuid",JSONObject.NULL))
            val state=when{newUsed==stock.capacityQuanta->"EMPTY";current.state=="EMPTY" || current.state=="SEALED"->"IN_USE";else->current.state}
            dao.setContainerState(current.id,state,current.opened_on)
        }
        return MeasureResult.Success(target)
    }

    suspend fun consumeNewRecord(database:NotesDatabase,scope:ReferenceScope,expected:RecordEntity,operationUuid:String,
        uses:List<ConfirmedStockUse>,created:Instant):MeasuredConsumptionReceipt {
        val db=database.openHelper.writableDatabase;check(db.inTransaction());ReferenceStore.checkScope(db,scope)
        if(!ReferencePayload.uuid(operationUuid) || created.toEpochMilli()<0)fail(ReferenceError.INVALID_SOURCE)
        val dao=database.dao();val current=dao.recordById(expected.id) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
        val medication=ReferenceStore.identity(db,scope,"MEDICATION",current.medication_id,null)
        val record=ReferenceStore.identity(db,scope,"RECORD",current.id,medication)
        val frozen=MeasuredUsePayload.freeze(MeasuredConsumptionRequest(scope.dataset,medication,MeasuredUsePayload.record(record,expected),uses))
        if(ReferenceStore.row(db,"reference_event","operation_uuid=?",arrayOf(operationUuid))!=null)fail(ReferenceError.OPERATION_CONFLICT)
        ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(operationUuid))?.let {old->
            if(old.getString("dataset_uuid")!=scope.dataset || old.getString("record_uuid")!=record || old.getString("kind")!="CONSUME" || old.getString("request_sha256")!=frozen.sha256)fail(ReferenceError.OPERATION_CONFLICT)
            return receipt(old)
        }
        if(current!=expected)fail(ReferenceError.STALE_REVISION)
        if(current.origin!="APP")fail(ReferenceError.INVALID_SOURCE)
        if(current.unallocated_supply_amount!=null || dao.supplyFor(current.id).isNotEmpty())fail(ReferenceError.ALREADY_CONSUMED)
        db.query("SELECT 1 FROM measured_operation WHERE record_uuid=? AND record_revision=? LIMIT 1",arrayOf(record,current.revision)).use{if(it.moveToFirst())fail(ReferenceError.ALREADY_CONSUMED)}
        val request=frozen.request()
        val plans=request.uses.map {input->
            val identity=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(input.containerUuid)) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
            if(identity.getString("dataset_uuid")!=scope.dataset || identity.getString("kind")!="CONTAINER" || ReferencePayload.optional(identity,"owner_uuid")!=medication)fail(ReferenceError.UNKNOWN_IDENTITY)
            val event=ReferenceStore.row(db,"reference_event","operation_uuid=?",arrayOf(input.presentationEventUuid)) ?: fail(ReferenceError.INVALID_SOURCE)
            if(event.getString("dataset_uuid")!=scope.dataset || event.getString("subject_uuid")!=input.containerUuid || event.isNull("snapshot_uuid"))fail(ReferenceError.INVALID_SOURCE)
            val head=ReferenceStore.row(db,"reference_event","subject_uuid=? ORDER BY revision DESC LIMIT 1",arrayOf(input.containerUuid))!!
            if(head.getString("operation_uuid")!=input.presentationEventUuid)fail(ReferenceError.STALE_REVISION)
            val snapshot=ReferenceStore.row(db,"reference_snapshot","uuid=?",arrayOf(event.getString("snapshot_uuid"))) ?: fail(ReferenceError.INVALID_SOURCE)
            if(snapshot.getString("dataset_uuid")!=scope.dataset || snapshot.getString("kind")!="PRESENTATION" || snapshot.getString("payload_sha256")!=input.presentationSha256)fail(ReferenceError.INVALID_SOURCE)
            val presentation=PackageMeasurementPayload.read(snapshot.getString("payload_json"));if(presentation.sha256!=input.presentationSha256)fail(ReferenceError.INVALID_SOURCE)
            val sid=snapshot.getString("uuid");val box=if(identity.isNull("live_handle"))null else dao.container(identity.getLong("live_handle"))
            if(box!=null && box.medication_id!=current.medication_id)fail(ReferenceError.UNKNOWN_IDENTITY)
            val scale=presentation.stockScale()
            if(scale !is MeasureResult.Success)Plan(input,box,sid,null,null,null,null,0,(scale as MeasureResult.Failure).error.name)
            else {
                val s=scale.value
                if(box!=null && (box.capacity!=s.capacityQuanta.toDouble() || box.initial_used_amount!=0.0 || !box.used_amount.isFinite() ||
                    box.used_amount<0 || box.used_amount>s.capacityQuanta || box.used_amount.toLong().toDouble()!=box.used_amount))fail(ReferenceError.INVALID_SOURCE)
                val physical=if(input.quantity.meaning==s.quantum.meaning)Measurements.inUnit(input.quantity,s.quantum.unit)
                    else when(val conversion=presentation.measurement().convert(input.quantity,s.quantum.meaning,s.quantum.unit,ActualPackage(s.ref,true))) {
                        is MeasureResult.Failure->conversion
                        is MeasureResult.Success->MeasureResult.Success(conversion.value.quantity)
                    }
                if(physical is MeasureResult.Failure)Plan(input,box,sid,s,null,null,box?.used_amount?.toLong(),0,physical.error.name)
                else {
                    val q=(physical as MeasureResult.Success).value;val n=s.toQuanta(q,ActualPackage(s.ref,true))
                    if(n is MeasureResult.Failure)Plan(input,box,sid,s,null,null,box?.used_amount?.toLong(),0,n.error.name)
                    else {
                        val wanted=(n as MeasureResult.Success).value
                        val available=if(box!=null && box.state in setOf("SEALED","IN_USE","EMPTY"))s.capacityQuanta-box.used_amount.toLong() else 0L
                        val take=minOf(wanted,available)
                        Plan(input,box,sid,s,q,wanted,box?.used_amount?.toLong(),take,when{box==null->"CONTAINER_MISSING";box.state=="VOIDED"->"VOIDED";box.state=="DISCARDED"->"DISCARDED";take<wanted->"INSUFFICIENT_STOCK";else->null})
                    }
                }
            }
        }
        val issue=when {plans.isEmpty()->"ACTUAL_PACKAGE_NOT_CONFIRMED";plans.size==1->plans.single().issue;plans.any{it.issue!=null}->"MULTIPLE_SOURCE_BALANCES";else->null}
        val left=plans.singleOrNull()?.let {p->if(p.requested==null)p.input.quantity else amount(p.scale!!,p.wanted!!-p.take)}?.takeIf{issue!=null}
        val envelope=JSONObject().put("format",1).put("request",JSONObject(frozen.json)).put("captures",JSONArray(plans.map(::captureJson)))
        ReferenceStore.insert(db,"measured_operation",JSONObject().put("operation_uuid",operationUuid).put("dataset_uuid",scope.dataset).put("record_uuid",record)
            .put("record_revision",current.revision).put("kind","CONSUME").put("request_sha256",frozen.sha256).put("input_json",ReferencePayload.canonical(envelope))
            .put("unallocated_quantity",left?.let{(Measurements.decimalText(it.value) as MeasureResult.Success).value} ?: JSONObject.NULL)
            .put("unallocated_unit",left?.unit?.name ?: JSONObject.NULL).put("unknown_reason",issue ?: JSONObject.NULL).put("created_utc",created.toEpochMilli()))
        plans.forEach {p->
            if(p.take>0) {
                val box=p.container!!;val scale=p.scale!!
                val entry=dao.supply(SupplyEntryEntity(container_id=box.id,dose_record_id=current.id,dose_revision=current.revision,operation_id=operationUuid,kind="CONSUME",
                    used_delta=p.take.toDouble(),created_utc=created.toEpochMilli(),created_zone="UTC",reason="confirmed physical stock use"))
                ReferenceStore.insert(db,"measured_allocation",JSONObject().put("uuid",UUID.randomUUID().toString()).put("dataset_uuid",scope.dataset).put("operation_uuid",operationUuid)
                    .put("container_uuid",p.input.containerUuid).put("presentation_event_uuid",p.input.presentationEventUuid).put("ledger_entry_id",entry)
                    .put("quantity",(Measurements.decimalText(amount(scale,p.take).value) as MeasureResult.Success).value).put("unit",scale.quantum.unit.name).put("reverses_uuid",JSONObject.NULL))
                val state=if(p.used!!+p.take==scale.capacityQuanta)"EMPTY" else "IN_USE"
                dao.setContainerState(box.id,state,box.opened_on) // First actual use establishes IN_USE, never guesses the opening date.
            }
            val revision=db.query("SELECT COALESCE(MAX(capture_revision),0)+1 FROM record_reference_capture WHERE record_uuid=? AND record_revision=?",arrayOf(record,current.revision)).use{it.moveToFirst();it.getLong(0)}
            if(revision>Int.MAX_VALUE)fail(ReferenceError.INVALID_SOURCE)
            val capture=JSONObject().put("uuid",UUID.randomUUID().toString()).put("dataset_uuid",scope.dataset).put("record_uuid",record).put("record_revision",current.revision)
                .put("capture_revision",revision.toInt()).put("kind","CONFIRM").put("context_event_uuid",JSONObject.NULL).put("actual_snapshot_uuid",p.snapshot).put("container_uuid",p.input.containerUuid)
            capture.put("payload_sha256",ReferenceStore.captureHash(capture));ReferenceStore.insert(db,"record_reference_capture",capture)
        }
        return receipt(ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(operationUuid))!!)
    }
}
