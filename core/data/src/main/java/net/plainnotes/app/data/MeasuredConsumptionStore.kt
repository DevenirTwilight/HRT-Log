package net.plainnotes.app.data

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
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
    data class AlreadyAccounted(override val containerUuid:String,val requested:Quantity):PhysicalUseResult
}
internal data class MeasuredConsumptionReceipt(val operationUuid:String,val recordUuid:String,val recordRevision:Int,val lines:List<PhysicalUseResult>,val issue:String?)
internal data class MeasuredCountReceipt(val operationUuid:String,val result:MeasureResult<Quantity>)
/** One frozen container only: no sum across different strengths, units, sources or ingredient identities. */
data class ConfirmedPhysicalStock(val containerUuid:String,val presentationSha256:String,val remaining:Quantity)
internal data class AuditedStockProjection(val containerUuid:String,val presentationSha256:String,val usedQuanta:Long,val remaining:Quantity,val cacheMatches:Boolean)

/** Internal P4 adapter used by the captured Repository transaction; public UI admission remains disabled.
 * Actual records, source captures and stock entries commit in one transaction.
 * A later revision accepts prior exact history only after its complete audited inverse.
 */
internal object MeasuredConsumptionStore {
    // Materialize proven live handles once; preserve the exact NOT EXISTS coverage predicate.
    internal const val EXACT_SOURCE_COVERAGE_QUERY="SELECT 1 FROM supply_container c LEFT JOIN (SELECT DISTINCT i.live_handle FROM reference_identity i JOIN reference_event e ON e.subject_uuid=i.uuid JOIN reference_snapshot s ON s.uuid=e.snapshot_uuid WHERE i.kind='CONTAINER' AND i.live_handle IS NOT NULL AND s.kind='PRESENTATION') covered ON covered.live_handle=c.id WHERE c.inventory_encoding='INTEGER_QUANTA_V1' AND covered.live_handle IS NULL LIMIT 1"
    private fun fail(code:ReferenceError):Nothing=throw ReferenceFailure(code)

    /** Read-only per-container projection audit. This does not replace whole-backup/zero-effect operation validation.
     * Never repairs a cache or interprets an old scalar container as integer-mode stock.
     */
    suspend fun auditProjection(database:NotesDatabase,scope:ReferenceScope,containerUuid:String):MeasureResult<AuditedStockProjection> {
        val db=database.openHelper.writableDatabase;check(db.inTransaction());ReferenceStore.checkScope(db,scope)
        return auditStagedProjection(db,scope.dataset,containerUuid)
    }

    /** Candidate staging audit: only business identities are required, never the device-local runtime epoch.
     * Not a backup acceptance gate: the caller still needs whole-dataset/zero-effect operation validation.
     * The live wrapper above checks its epoch before calling this same read-only SQL implementation.
     */
    /** Pure payload reuse is confined to one read-only transaction, never a cross-write trust cache. */
    private class PresentationAudit {
        private val byPayload=HashMap<String,FrozenPackageMeasurement>()
        fun read(payload:String)=byPayload.getOrPut(payload){PackageMeasurementPayload.read(payload)}
    }
    internal fun auditStagedProjection(db:SupportSQLiteDatabase,dataset:String,containerUuid:String):MeasureResult<AuditedStockProjection> =
        auditStagedProjection(db,dataset,containerUuid,PresentationAudit())
    private fun auditStagedProjection(db:SupportSQLiteDatabase,dataset:String,containerUuid:String,presentations:PresentationAudit):MeasureResult<AuditedStockProjection> {
        check(db.inTransaction())
        try {
            require(ReferencePayload.uuid(dataset) && ReferencePayload.uuid(containerUuid))
            require(ReferenceStore.row(db,"reference_dataset","uuid=?",arrayOf(dataset))!=null)
            val identity=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(containerUuid)) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
            require(identity.getString("dataset_uuid")==dataset && identity.getString("kind")=="CONTAINER")
            if(identity.isNull("live_handle"))fail(ReferenceError.UNKNOWN_IDENTITY)
            val owner=identity.getString("owner_uuid");val medication=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(owner))!!
            require(medication.getString("dataset_uuid")==dataset && medication.getString("kind")=="MEDICATION" && !medication.isNull("live_handle"))
            val box=projectionBox(db,identity.getLong("live_handle"));require(box.medication_id==medication.getLong("live_handle"))
            val first=ReferenceStore.row(db,"reference_event","subject_uuid=? ORDER BY revision LIMIT 1",arrayOf(containerUuid))
                ?: return MeasureResult.Failure(MeasureError.MISSING_HISTORICAL_SNAPSHOT)
            require(first.getString("dataset_uuid")==dataset && !first.isNull("snapshot_uuid"))
            val snapshot=ReferenceStore.row(db,"reference_snapshot","uuid=?",arrayOf(first.getString("snapshot_uuid")))!!
            require(snapshot.getString("dataset_uuid")==dataset && snapshot.getString("kind")=="PRESENTATION")
            val frozen=presentations.read(snapshot.getString("payload_json"));require(frozen.sha256==snapshot.getString("payload_sha256"))
            val result=frozen.stockScale();if(result is MeasureResult.Failure)return result
            val scale=(result as MeasureResult.Success).value
            require(box.inventory_encoding==InventoryEncoding.EXACT && box.capacity==scale.capacityQuanta.toDouble() && box.initial_used_amount==0.0)
            val entries=projectionEntries(db,box.id)
            val operationChain by lazy {MeasuredOperationChain.validate(db)}
            var used=0L;var previousId=0L;val deltas=ArrayList<Long>()
            fun same(a:Quantity,b:Quantity)=a.unit==b.unit && a.meaning==b.meaning && a.value.compareTo(b.value)==0
            for(entry in entries) {
                require(entry.id>previousId && entry.used_delta.isFinite() && entry.used_delta!=0.0 && kotlin.math.abs(entry.used_delta)<=StockQuantum.MAX_QUANTA && entry.used_delta.toLong().toDouble()==entry.used_delta)
                val delta=entry.used_delta.toLong();val allocation=ReferenceStore.row(db,"measured_allocation","ledger_entry_id=?",arrayOf(entry.id))!!
                require(allocation.getString("dataset_uuid")==dataset && allocation.getString("container_uuid")==containerUuid && allocation.getString("operation_uuid")==entry.operation_id)
                val event=ReferenceStore.row(db,"reference_event","operation_uuid=?",arrayOf(allocation.getString("presentation_event_uuid")))!!
                require(event.getString("dataset_uuid")==dataset && event.getString("subject_uuid")==containerUuid && event.getString("snapshot_uuid")==snapshot.getString("uuid"))
                val operation=ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(entry.operation_id))!!
                require(operation.getString("dataset_uuid")==dataset && operation.getString("kind")==entry.kind)
                val text=allocation.getString("quantity");val parsed=Measurements.parse(text);require(parsed is MeasureResult.Success)
                require((Measurements.decimalText(parsed.value) as MeasureResult.Success).value==text && parsed.value.signum()>0)
                require(allocation.getString("unit")==scale.quantum.unit.name)
                val physical=Quantity(parsed.value,scale.quantum.unit,scale.quantum.meaning)
                require(same(physical,amount(scale,kotlin.math.abs(delta))))
                if(entry.kind!="ADJUST") {
                    val record=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(operation.getString("record_uuid")))!!
                    require(record.getString("kind")=="RECORD" && record.getString("dataset_uuid")==dataset && record.getString("owner_uuid")==owner && !record.isNull("live_handle") && record.getLong("live_handle")==entry.dose_record_id)
                    require(operation.getLong("record_revision")==entry.dose_revision)
                    db.query("SELECT revision FROM dose_record WHERE id=?",arrayOf(entry.dose_record_id)).use { c ->
                        require(c.moveToFirst() && c.getType(0)==Cursor.FIELD_TYPE_INTEGER && c.getLong(0)>=operation.getInt("record_revision"))
                    }
                }
                when(entry.kind) {
                    "CONSUME"->{
                        require(delta>0 && allocation.isNull("reverses_uuid"))
                        val envelope=JSONObject(operation.getString("input_json"));val request=MeasuredUsePayload.read(ReferencePayload.canonical(envelope.getJSONObject("request"))).request()
                        require(request.medicationUuid==owner)
                        val input=request.uses.single{it.containerUuid==containerUuid};require(input.presentationEventUuid==event.getString("operation_uuid") && input.presentationSha256==frozen.sha256)
                        val expected=physicalStock(frozen,scale,input.quantity)
                        require(expected is MeasureResult.Success)
                        val wanted=scale.toQuanta(expected.value,ActualPackage(scale.ref,true));require(wanted is MeasureResult.Success)
                        val line=receipt(operation).lines.single{it.containerUuid==containerUuid};require(line is PhysicalUseResult.Known && same(line.requested,expected.value) && same(line.allocated,physical))
                        val captures=envelope.getJSONArray("captures");val capture=(0 until captures.length()).map{captures.getJSONObject(it)}.single{it.getString("container_uuid")==containerUuid}
                        require(capture.getString("snapshot_uuid")==snapshot.getString("uuid") && capture.getString("used_before_quanta")==used.toString() && capture.getString("take_quanta")==delta.toString())
                        require(capture.getString("requested_quanta")==wanted.value.toString() && delta==minOf(wanted.value,scale.capacityQuanta-used))
                        val count=db.query("SELECT COUNT(*) FROM measured_allocation WHERE operation_uuid=? AND container_uuid=?",arrayOf(entry.operation_id,containerUuid)).use{it.moveToFirst();it.getLong(0)};require(count==1L)
                    }
                    "ADJUST"->{
                        require(entry.dose_record_id==null && entry.dose_revision==null && allocation.isNull("reverses_uuid"))
                        val capture=adjustmentCapture(operation);val request=capture.request
                        capture.bridgeFor?.let{MeasuredReconciliation.verifyBridge(db,operation,it,operationChain)}
                        require(request.getString("container_uuid")==containerUuid && request.getString("medication_uuid")==owner && request.getString("presentation_event_uuid")==event.getString("operation_uuid") && request.getString("presentation_sha256")==frozen.sha256)
                        require(capture.ledgerHead==previousId && request.getString("expected_used_quanta")==used.toString())
                        val remaining=PackageMeasurementPayload.quantity(request.getJSONObject("remaining_stock"))
                        val target=if(remaining.value.signum()==0) {require(Measurements.add(scale.quantum.copy(value=BigDecimal.ZERO),remaining) is MeasureResult.Success);0L}
                            else {val n=scale.toQuanta(remaining,ActualPackage(scale.ref,true));require(n is MeasureResult.Success);n.value}
                        require(request.getString("remaining_quanta")==target.toString() && delta==scale.capacityQuanta-target-used)
                    }
                    "REVERSE"->{
                        require(delta<0 && !allocation.isNull("reverses_uuid"))
                        val original=ReferenceStore.row(db,"measured_allocation","uuid=?",arrayOf(allocation.getString("reverses_uuid")))!!
                        val source=ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(original.getString("operation_uuid")))!!
                        val prior=entries.single{it.id==original.getLong("ledger_entry_id")}
                        require(original.getString("dataset_uuid")==dataset && original.getString("container_uuid")==containerUuid && original.isNull("reverses_uuid"))
                        require(original.getString("quantity")==text && original.getString("unit")==allocation.getString("unit") && original.getString("presentation_event_uuid")==event.getString("operation_uuid"))
                        require(prior.kind=="CONSUME" && prior.id<entry.id && prior.used_delta== -entry.used_delta && entry.reversal_of_id==prior.id && entry.dose_record_id==prior.dose_record_id)
                        val input=reverseCapture(operation)
                        if(input.has("reconciliation"))MeasuredReconciliation.verify(db,operation,input,operationChain)
                        require(input.getString("kind")=="REVERSE" && input.getString("dataset_uuid")==dataset && input.getString("medication_uuid")==owner && input.getString("record_uuid")==operation.getString("record_uuid") && input.getInt("record_revision")==operation.getInt("record_revision"))
                        require(source.getString("kind")=="CONSUME" && source.getString("dataset_uuid")==dataset && source.getString("record_uuid")==operation.getString("record_uuid") && input.getString("source_operation_uuid")==source.getString("operation_uuid") && input.getString("source_request_sha256")==source.getString("request_sha256"))
                        val sourceIds=db.query("SELECT uuid FROM measured_allocation WHERE operation_uuid=? ORDER BY uuid",arrayOf(source.getString("operation_uuid"))).use{c->buildList{while(c.moveToNext())add(c.getString(0))}}
                        val captured=input.getJSONArray("source_allocation_uuids");require(sourceIds==(0 until captured.length()).map{captured.getString(it)})
                        val reversed=db.query("SELECT reverses_uuid FROM measured_allocation WHERE operation_uuid=? ORDER BY reverses_uuid",arrayOf(operation.getString("operation_uuid"))).use{c->buildList{while(c.moveToNext()){require(!c.isNull(0));add(c.getString(0))}}}
                        require(sourceIds==reversed)

                    }
                    else->error("Unsupported measured ledger kind")
                }
                used+=delta;require(used in 0..scale.capacityQuanta);deltas+=delta;previousId=entry.id
            }
            val reduced=scale.restoredUsed(0,deltas);require(reduced is MeasureResult.Success && reduced.value==used)
            val remaining=scale.remaining(used);require(remaining is MeasureResult.Success)
            return MeasureResult.Success(AuditedStockProjection(containerUuid,frozen.sha256,used,remaining.value,box.used_amount==used.toDouble()),algorithm=StockQuantum.ALGORITHM)
        } catch(e:ReferenceFailure){throw e} catch(e:Exception){fail(ReferenceError.INVALID_SOURCE)}
    }

    /** Full read-only audit shared by internal Repository writes, staging and exact backup restore. */
    internal fun auditStagedDataset(db:SupportSQLiteDatabase):List<AuditedStockProjection> {
        check(db.inTransaction())
        try {
            BackupValidation.validateCandidateStoredColumns(db)
            val reads=ReferenceAuditReads(db)
            try {return auditReadSnapshot(reads)} finally {reads.finish()}
        } catch(e:ReferenceFailure){throw e} catch(e:Exception){fail(ReferenceError.INVALID_SOURCE)}
    }
    private fun auditReadSnapshot(db:SupportSQLiteDatabase):List<AuditedStockProjection> {
            BackupValidation.validateMeasuredCandidateState(db)
            db.query(EXACT_SOURCE_COVERAGE_QUERY).use{require(!it.moveToFirst())}
            db.query("SELECT 1 FROM measured_operation o JOIN reference_event e ON e.operation_uuid=o.operation_uuid LIMIT 1").use{require(!it.moveToFirst())}
            db.query("PRAGMA foreign_key_check").use{require(!it.moveToFirst())}
            val identities=db.query("SELECT DISTINCT i.uuid,i.dataset_uuid FROM reference_identity i JOIN reference_event e ON e.subject_uuid=i.uuid JOIN reference_snapshot s ON s.uuid=e.snapshot_uuid WHERE i.kind='CONTAINER' AND s.kind='PRESENTATION' ORDER BY i.uuid").use { c ->
                buildList {while(c.moveToNext())add(c.getString(0) to c.getString(1))}
            }
            val presentations=PresentationAudit()
            val projections=identities.mapNotNull { (uuid,dataset) ->
                val identity=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(uuid))!!
                if(identity.isNull("live_handle")) {
                    // FK-linked history cannot survive physical removal by attaching to a reused integer ID.
                    db.query("SELECT 1 FROM measured_allocation WHERE container_uuid=? LIMIT 1",arrayOf(uuid)).use{require(!it.moveToFirst())}
                    null
                } else {
                    val result=auditStagedProjection(db,dataset,uuid,presentations);require(result is MeasureResult.Success)
                    require(result.value.cacheMatches);result.value
                }
            }
            auditOperations(db,presentations)
            return projections
    }

    internal fun forecastBalances(db:SupportSQLiteDatabase,projections:List<AuditedStockProjection>):Map<Long,List<ConfirmedPhysicalStock>> {
        check(db.inTransaction())
        return projections.mapNotNull {p->
            db.query("SELECT c.medication_id,c.state FROM reference_identity i JOIN supply_container c ON c.id=i.live_handle WHERE i.uuid=? AND i.kind='CONTAINER'",arrayOf(p.containerUuid)).use {c->
                require(c.moveToFirst())
                if(c.getString(1) !in setOf("SEALED","IN_USE","EMPTY"))null else c.getLong(0) to ConfirmedPhysicalStock(p.containerUuid,p.presentationSha256,p.remaining)
            }
        }.groupBy({it.first},{it.second})
    }

    /** Verify operations that a ledger-only walk cannot see, including empty sources and no-op counts. */
    private fun auditOperations(db:SupportSQLiteDatabase,presentations:PresentationAudit) {
        fun rows(table:String,where:String="1",args:Array<out Any?> = emptyArray()):List<JSONObject> =
            db.query("SELECT ${if(table=="reference_event" || table=="measured_operation")"operation_uuid" else "uuid"} FROM `$table` WHERE $where",args).use { c ->
                buildList {while(c.moveToNext())add(ReferenceStore.row(db,table,"${if(table=="reference_event" || table=="measured_operation")"operation_uuid" else "uuid"}=?",arrayOf(c.getString(0)))!!)}
            }
        // Caches live only in this read-only audit transaction: no persisted or cross-write trust.
        val identityRows=HashMap<String,JSONObject>()
        val sources=HashMap<List<String>,Pair<JSONObject,FrozenPackageMeasurement>>()
        val scales=HashMap<String,MeasureResult<StockQuantum>>()
        fun stockScale(frozen:FrozenPackageMeasurement)=scales.getOrPut(frozen.sha256){frozen.stockScale()}
        fun identity(uuid:String,kind:String,dataset:String,owner:String?=null):JSONObject {
            val r=identityRows.getOrPut(uuid){ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(uuid))!!}
            require(r.getString("kind")==kind && r.getString("dataset_uuid")==dataset)
            if(owner!=null)require(r.getString("owner_uuid")==owner)
            return r
        }
        fun source(container:String,eventId:String,sha:String,dataset:String,owner:String):Pair<JSONObject,FrozenPackageMeasurement> = sources.getOrPut(listOf(container,eventId,sha,dataset,owner)) {
            val box=identity(container,"CONTAINER",dataset,owner)
            val event=ReferenceStore.row(db,"reference_event","operation_uuid=?",arrayOf(eventId))!!
            require(event.getString("dataset_uuid")==dataset && event.getString("subject_uuid")==container && !event.isNull("snapshot_uuid"))
            val snap=ReferenceStore.row(db,"reference_snapshot","uuid=?",arrayOf(event.getString("snapshot_uuid")))!!
            require(snap.getString("dataset_uuid")==dataset && snap.getString("kind")=="PRESENTATION" && snap.getString("payload_sha256")==sha)
            val frozen=presentations.read(snap.getString("payload_json"));require(frozen.sha256==sha)
            box to frozen
        }
        val chain=MeasuredOperationChain.validate(db)
        data class PrefixIndex(val usedAtHead:Map<Long,Long>,val headBeforeSequence:java.util.TreeMap<Int,Long>)
        val prefixes=HashMap<Pair<String,Long>,PrefixIndex>()
        fun prefix(box:JSONObject,scale:StockQuantum,head:Long,sequence:Int):Long {
            require(head>=0)
            if(box.isNull("live_handle")) {require(head==0L);return 0L}
            val index=prefixes.getOrPut(box.getString("uuid") to scale.capacityQuanta) {
                val entries=projectionEntries(db,box.getLong("live_handle"))
                val atHead=HashMap<Long,Long>().apply{put(0,0)}
                val beforeSequence=java.util.TreeMap<Int,Long>()
                var used=0L
                for(e in entries) {
                    require(e.used_delta.toLong().toDouble()==e.used_delta && kotlin.math.abs(e.used_delta)<=StockQuantum.MAX_QUANTA)
                    // Same exact reducer and every bounded prefix, computed once rather than once per operation.
                    val next=scale.restoredUsed(used,listOf(e.used_delta.toLong()));require(next is MeasureResult.Success)
                    used=next.value;atHead[e.id]=used
                    val seq=chain.getValue(e.operation_id).sequence
                    beforeSequence[seq]=maxOf(beforeSequence[seq] ?: 0,e.id)
                }
                var maximum=0L
                for(entry in beforeSequence.entries){maximum=maxOf(maximum,entry.value);entry.setValue(maximum)}
                PrefixIndex(atHead,beforeSequence)
            }
            require(head==(index.headBeforeSequence.lowerEntry(sequence)?.value ?: 0L))
            return requireNotNull(index.usedAtHead[head])
        }
        fun physical(frozen:FrozenPackageMeasurement,scale:StockQuantum,input:Quantity):MeasureResult<Quantity> =
            physicalStock(frozen,scale,input)
        val operations=rows("measured_operation")
        require(operations.size<=BackupLimits.MAX_ROWS)
        val observations=operations.filter{it.getString("kind")=="ADJUST" && adjustmentCapture(it).bridgeFor==null}.map { row ->
            val capture=adjustmentCapture(row)
            Triple(capture.request.getString("container_uuid"),chain.getValue(row.getString("operation_uuid")).sequence,capture.ledgerHead)
        }.groupBy{it.first}
        val consumed=HashSet<Pair<String,Int>>();val reversed=HashSet<String>();val confirmed=HashSet<String>()
        val allocated=HashSet<String>()
        for(op in operations) {
            val id=op.getString("operation_uuid");val dataset=op.getString("dataset_uuid")
            require(ReferencePayload.uuid(id) && ReferencePayload.uuid(dataset))
            require(op.getString("input_json").toByteArray(Charsets.UTF_8).size<=ReferencePayload.MAX_BYTES)
            BackupLimits.checkJson(op.getString("input_json"))
            val allocations=rows("measured_allocation","operation_uuid=?",arrayOf(id))
            allocations.forEach{require(allocated.add(it.getString("uuid")))}
            when(op.getString("kind")) {
                "CONSUME"->{
                    val result=receipt(op);val envelope=JSONObject(op.getString("input_json"))
                    val request=MeasuredUsePayload.read(ReferencePayload.canonical(envelope.getJSONObject("request"))).request()
                    require(consumed.add(request.record.uuid to request.record.revision))
                    identity(request.medicationUuid,"MEDICATION",dataset)
                    val record=identity(request.record.uuid,"RECORD",dataset,request.medicationUuid)
                    if(!record.isNull("live_handle"))db.query("SELECT revision,taken_utc,taken_zone,status,actual_dose,config_snapshot,rule_version_id,slot_key,scheduled_utc,scheduled_zone,planned_dose,late_after_minutes_snapshot FROM dose_record WHERE id=?",arrayOf(record.getLong("live_handle"))).use { c ->
                        require(c.moveToFirst());val revision=integer(c,0);require(revision>=request.record.revision)
                        if(revision==request.record.revision.toLong()) {
                            require(!c.isNull(1) && integer(c,1)==request.record.takenUtc)
                            require((if(c.isNull(2))null else c.getString(2))==request.record.takenZone && c.getString(3)==request.record.status)
                            require(java.lang.Long.toHexString(java.lang.Double.doubleToRawLongBits(scalar(c,4))).padStart(16,'0')==request.record.legacyActualDoseBits)
                            require(ReferencePayload.sha(c.getString(5))==request.record.configSha256)
                            request.record.scheduleSha256?.let { expected ->
                                require(MeasuredUsePayload.scheduleSha(if(c.isNull(6))null else integer(c,6),if(c.isNull(7))null else c.getString(7),
                                    if(c.isNull(8))null else integer(c,8),if(c.isNull(9))null else c.getString(9),if(c.isNull(10))null else scalar(c,10),
                                    if(c.isNull(11))null else integer(c,11).also{require(it in 0..Int.MAX_VALUE.toLong())}.toInt())==expected)
                            }
                        }
                    }
                    val captures=envelope.getJSONArray("captures")
                    val expectedAllocations=HashSet<String>();val issues=ArrayList<String?>();val left=ArrayList<Quantity>()
                    for((i,input) in request.uses.withIndex()) {
                        val c=captures.getJSONObject(i)
                        val (box,frozen)=source(input.containerUuid,input.presentationEventUuid,input.presentationSha256,dataset,request.medicationUuid)
                        val event=ReferenceStore.row(db,"reference_event","operation_uuid=?",arrayOf(input.presentationEventUuid))!!
                        require(c.getString("snapshot_uuid")==event.getString("snapshot_uuid"))
                        val confirmations=rows("record_reference_capture","record_uuid=? AND record_revision=? AND kind='CONFIRM' AND container_uuid=?",arrayOf(request.record.uuid,request.record.revision,input.containerUuid))
                        require(confirmations.size==1)
                        val confirmation=confirmations.single();require(confirmation.getString("dataset_uuid")==dataset && confirmation.getString("actual_snapshot_uuid")==c.getString("snapshot_uuid"))
                        require(confirmed.add(confirmation.getString("uuid")))
                        val scaleResult=stockScale(frozen);require(scaleResult is MeasureResult.Success);val scale=scaleResult.value
                        val head=if(c.isNull("ledger_head_id"))null else c.getString("ledger_head_id").toLong()
                        val state=ReferencePayload.optional(c,"stock_state")
                        val used=if(state==null) {require(box.isNull("live_handle") && head==null && c.isNull("used_before_quanta"));null}
                            else {require(head!=null && state in setOf("SEALED","IN_USE","EMPTY","VOIDED","DISCARDED"));prefix(box,scale,head,chain.getValue(id).sequence).also{require(c.getString("used_before_quanta")==it.toString())}}
                        val conversion=physical(frozen,scale,input.quantity)
                        val wanted:MeasureResult<Long> = when(conversion) {is MeasureResult.Success->scale.toQuanta(conversion.value,ActualPackage(scale.ref,true));is MeasureResult.Failure->conversion}
                        val take:Long;val issue:String?
                        if(request.stockEffect!=MeasuredStockEffect.DEDUCT_CURRENT) {
                            take=0;issue=if(request.stockEffect==MeasuredStockEffect.ALREADY_ACCOUNTED)"ALREADY_ACCOUNTED" else "STOCK_EFFECT_NOT_CONFIRMED"
                            require(c.isNull("requested_stock") && c.isNull("requested_quanta"))
                            if(request.stockEffect==MeasuredStockEffect.UNKNOWN)left+=input.quantity
                        } else if(wanted is MeasureResult.Failure) {
                            take=0;issue=wanted.error.name
                            require(c.isNull("requested_stock") && c.isNull("requested_quanta"))
                            left+=input.quantity
                        } else {
                            require(conversion is MeasureResult.Success && wanted is MeasureResult.Success)
                            require(sameQuantity(PackageMeasurementPayload.quantity(c.getJSONObject("requested_stock")),conversion.value))
                            require(c.getString("requested_quanta")==wanted.value.toString())
                            val available=if(used!=null && state in setOf("SEALED","IN_USE","EMPTY"))scale.capacityQuanta-used else 0L
                            take=minOf(wanted.value,available)
                            issue=when {used==null->"CONTAINER_MISSING";state=="VOIDED"->"VOIDED";state=="DISCARDED"->"DISCARDED";take<wanted.value->"INSUFFICIENT_STOCK";else->null}
                            left+=amount(scale,wanted.value-take)
                        }
                        require(c.getString("take_quanta")==take.toString() && ReferencePayload.optional(c,"issue")==issue)
                        issues+=issue
                        val matching=allocations.filter{it.getString("container_uuid")==input.containerUuid}
                        if(take==0L)require(matching.isEmpty()) else {require(matching.size==1);expectedAllocations+=matching.single().getString("uuid")}
                    }
                    require(expectedAllocations==allocations.map{it.getString("uuid")}.toSet())
                    val reason=when {request.stockEffect==MeasuredStockEffect.ALREADY_ACCOUNTED->null;request.stockEffect==MeasuredStockEffect.UNKNOWN->"STOCK_EFFECT_NOT_CONFIRMED";issues.isEmpty()->"ACTUAL_PACKAGE_NOT_CONFIRMED";issues.size==1->issues.single();issues.any{it!=null}->"MULTIPLE_SOURCE_BALANCES";else->null}
                    require(result.issue==reason)
                    val unallocated=(left.singleOrNull() ?: request.unlinkedActual)?.takeIf{reason!=null}
                    require(ReferencePayload.optional(op,"unallocated_quantity")==unallocated?.let{(Measurements.decimalText(it.value) as MeasureResult.Success).value})
                    require(ReferencePayload.optional(op,"unallocated_unit")==unallocated?.unit?.name)
                }
                "ADJUST"->{
                    val capture=adjustmentCapture(op);val r=capture.request
                    capture.bridgeFor?.let{MeasuredReconciliation.verifyBridge(db,op,it,chain)}
                    val (box,frozen)=source(r.getString("container_uuid"),r.getString("presentation_event_uuid"),r.getString("presentation_sha256"),dataset,r.getString("medication_uuid"))
                    val scaleResult=stockScale(frozen);require(scaleResult is MeasureResult.Success);val scale=scaleResult.value
                    val used=prefix(box,scale,capture.ledgerHead,chain.getValue(id).sequence);require(r.getString("expected_used_quanta")==used.toString())
                    val target=PackageMeasurementPayload.quantity(r.getJSONObject("remaining_stock"))
                    val n=if(target.value.signum()==0){require(Measurements.add(scale.quantum.copy(value=BigDecimal.ZERO),target) is MeasureResult.Success);0L}
                        else {val q=scale.toQuanta(target,ActualPackage(scale.ref,true));require(q is MeasureResult.Success);q.value}
                    require(r.getString("remaining_quanta")==n.toString())
                    val delta=scale.capacityQuanta-n-used
                    if(delta==0L)require(allocations.isEmpty()) else require(allocations.size==1)
                }
                "REVERSE"->{
                    val r=reverseCapture(op);val originalId=r.getString("source_operation_uuid");require(reversed.add(originalId))
                    val original=ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(originalId))!!
                    require(chain.getValue(originalId).sequence<chain.getValue(id).sequence)
                    require(original.getString("kind")=="CONSUME" && original.getString("dataset_uuid")==dataset && original.getString("record_uuid")==op.getString("record_uuid") && original.getInt("record_revision")<=op.getInt("record_revision") && original.getString("request_sha256")==r.getString("source_request_sha256"))
                    val request=MeasuredUsePayload.read(ReferencePayload.canonical(JSONObject(original.getString("input_json")).getJSONObject("request"))).request()
                    require(r.getString("medication_uuid")==request.medicationUuid)
                    val record=identity(request.record.uuid,"RECORD",dataset,request.medicationUuid)
                    if(!record.isNull("live_handle"))db.query("SELECT revision FROM dose_record WHERE id=?",arrayOf(record.getLong("live_handle"))).use { c ->
                        require(c.moveToFirst() && integer(c,0)>=op.getLong("record_revision"))
                    }
                    val originalAllocations=rows("measured_allocation","operation_uuid=?",arrayOf(originalId))
                    if(r.has("reconciliation"))MeasuredReconciliation.verify(db,op,r,chain)
                    for(allocation in originalAllocations) {
                        require(r.has("reconciliation") || observations[allocation.getString("container_uuid")].orEmpty().none { observation ->
                            observation.second<chain.getValue(id).sequence && observation.third>=allocation.getLong("ledger_entry_id")
                        }) // An independent count containing the consumption requires explicit reconciliation.
                    }
                    val originalIds=originalAllocations.map{it.getString("uuid")}.sorted()
                    val ids=r.getJSONArray("source_allocation_uuids");require(originalIds==(0 until ids.length()).map{ids.getString(it)})
                    require(allocations.map{it.getString("reverses_uuid")}.sorted()==originalIds)
                }
                else->error("Unknown measured operation")
            }
        }
        require(rows("measured_allocation").map{it.getString("uuid")}.toSet()==allocated)
        require(rows("record_reference_capture","kind='CONFIRM'").map{it.getString("uuid")}.toSet()==confirmed)
    }

    private fun sameQuantity(a:Quantity,b:Quantity)=a.unit==b.unit && a.meaning==b.meaning && a.value.compareTo(b.value)==0

    private data class ProjectionBox(val id:Long,val medication_id:Long,val capacity:Double,val initial_used_amount:Double,val used_amount:Double,val inventory_encoding:String)
    private data class ProjectionEntry(val id:Long,val dose_record_id:Long?,val dose_revision:Long?,val operation_id:String,val kind:String,val used_delta:Double,val reversal_of_id:Long?)
    private fun integer(c:Cursor,index:Int):Long {require(c.getType(index)==Cursor.FIELD_TYPE_INTEGER);return c.getLong(index)}
    private fun nullableInteger(c:Cursor,index:Int):Long?=if(c.isNull(index))null else integer(c,index)
    private fun scalar(c:Cursor,index:Int):Double {
        require(c.getType(index)==Cursor.FIELD_TYPE_FLOAT || c.getType(index)==Cursor.FIELD_TYPE_INTEGER)
        return c.getDouble(index).also{require(it.isFinite())}
    }
    private fun projectionBox(db:SupportSQLiteDatabase,id:Long):ProjectionBox =
        db.query("SELECT id,medication_id,capacity,initial_used_amount,used_amount,inventory_encoding FROM supply_container WHERE id=?",arrayOf(id)).use { c ->
            require(c.moveToFirst());ProjectionBox(integer(c,0),integer(c,1),scalar(c,2),scalar(c,3),scalar(c,4),c.getString(5))
        }
    private fun projectionEntries(db:SupportSQLiteDatabase,id:Long):List<ProjectionEntry> =
        db.query("SELECT id,dose_record_id,dose_revision,operation_id,kind,used_delta,reversal_of_id FROM supply_transaction WHERE container_id=? ORDER BY id",arrayOf(id)).use { c ->
            require(c.count<=100000)
            buildList { while(c.moveToNext()) {
                require(c.getType(3)==Cursor.FIELD_TYPE_STRING && c.getType(4)==Cursor.FIELD_TYPE_STRING)
                add(ProjectionEntry(integer(c,0),nullableInteger(c,1),nullableInteger(c,2),c.getString(3),c.getString(4),scalar(c,5),nullableInteger(c,6)))
            } }
        }

    private fun physicalStock(frozen:FrozenPackageMeasurement,scale:StockQuantum,input:Quantity):MeasureResult<Quantity> =
        if(input.meaning==scale.quantum.meaning)Measurements.inUnit(input,scale.quantum.unit)
        else when(val converted=frozen.measurement().convert(input,scale.quantum.meaning,scale.quantum.unit,ActualPackage(scale.ref,true))) {
            is MeasureResult.Failure->converted
            is MeasureResult.Success->MeasureResult.Success(converted.value.quantity)
        }

    private data class Plan(val input:ConfirmedStockUse,val container:ContainerEntity?,val snapshot:String,val scale:StockQuantum?,
        val requested:Quantity?,val wanted:Long?,val used:Long?,val take:Long,val issue:String?)
    private fun amount(scale:StockQuantum,n:Long)=scale.quantum.copy(value=scale.quantum.value.multiply(BigDecimal.valueOf(n)))
    private fun captureJson(db:SupportSQLiteDatabase,p:Plan)=JSONObject().put("container_uuid",p.input.containerUuid).put("presentation_event_uuid",p.input.presentationEventUuid)
        .put("snapshot_uuid",p.snapshot).put("requested_stock",p.requested?.let(PackageMeasurementPayload::quantity) ?: JSONObject.NULL)
        .put("requested_quanta",p.wanted?.toString() ?: JSONObject.NULL).put("used_before_quanta",p.used?.toString() ?: JSONObject.NULL)
        .put("take_quanta",p.take.toString()).put("issue",p.issue ?: JSONObject.NULL)
        .put("stock_state",p.container?.state ?: JSONObject.NULL)
        .put("ledger_head_id",p.container?.let {box->db.query("SELECT COALESCE(MAX(id),0) FROM supply_transaction WHERE container_id=?",arrayOf(box.id)).use{it.moveToFirst();it.getLong(0).toString()}} ?: JSONObject.NULL)
    internal fun existingConsumption(db:SupportSQLiteDatabase,operationUuid:String):Pair<MeasuredConsumptionRequest,MeasuredConsumptionReceipt>? {
        val row=ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(operationUuid)) ?: return null
        if(row.getString("kind")!="CONSUME")fail(ReferenceError.OPERATION_CONFLICT)
        val result=receipt(row)
        return MeasuredUsePayload.read(ReferencePayload.canonical(JSONObject(row.getString("input_json")).getJSONObject("request"))).request() to result
    }
    internal fun existingAdjustment(db:SupportSQLiteDatabase,operationUuid:String):JSONObject? =
        ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(operationUuid))?.let {if(it.getString("kind")!="ADJUST")fail(ReferenceError.OPERATION_CONFLICT);adjustmentCapture(it).request}

    internal fun replayCount(db:SupportSQLiteDatabase,scope:ReferenceScope,medicationUuid:String,operationUuid:String,input:ConfirmedStockCount):MeasuredCountReceipt? {
        val request=existingAdjustment(db,operationUuid) ?: return null
        require(input.userConfirmed)
        if(request.getString("dataset_uuid")!=scope.dataset || request.getString("medication_uuid")!=medicationUuid || request.getString("container_uuid")!=input.containerUuid ||
            request.getString("presentation_event_uuid")!=input.presentationEventUuid || request.getString("presentation_sha256")!=input.presentationSha256 ||
            ReferencePayload.canonical(request.getJSONObject("remaining_input"))!=ReferencePayload.canonical(PackageMeasurementPayload.quantity(input.remaining)))fail(ReferenceError.OPERATION_CONFLICT)
        return MeasuredCountReceipt(operationUuid,MeasureResult.Success(PackageMeasurementPayload.quantity(request.getJSONObject("remaining_stock"))))
    }
    internal fun sourceOfInverse(db:SupportSQLiteDatabase,operationUuid:String):JSONObject? =
        ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(operationUuid))?.let{if(it.getString("kind")!="REVERSE")fail(ReferenceError.OPERATION_CONFLICT);reverseCapture(it)}

    private fun receipt(row:JSONObject):MeasuredConsumptionReceipt = try {readReceipt(row)} catch(e:Exception){fail(ReferenceError.INVALID_SOURCE)}
    private fun readReceipt(row:JSONObject):MeasuredConsumptionReceipt {
        fun keys(value:JSONObject,expected:Set<String>)=require(value.keys().asSequence().toSet()==expected)
        fun text(value:JSONObject,key:String):String {require(value.get(key) is String);return value.getString(key)}
        fun quanta(value:JSONObject,key:String):Long {
            val token=text(value,key);require(Regex("0|[1-9][0-9]{0,15}").matches(token))
            return token.toLong().also{require(it<=StockQuantum.MAX_QUANTA)}
        }
        val envelope=JSONObject(row.getString("input_json"));keys(envelope,setOf("format","request","captures","chain"));MeasuredOperationChain.read(envelope)
        val request=MeasuredUsePayload.read(ReferencePayload.canonical(envelope.getJSONObject("request"))).request()
        if(ReferencePayload.canonical(envelope)!=row.getString("input_json") || envelope.get("format") !is Int || envelope.getInt("format")!=2 || row.getString("kind")!="CONSUME" ||
            ReferencePayload.sha(ReferencePayload.canonical(envelope.getJSONObject("request")))!=row.getString("request_sha256") ||
            row.getString("dataset_uuid")!=request.datasetUuid || row.getString("record_uuid")!=request.record.uuid || row.getInt("record_revision")!=request.record.revision)fail(ReferenceError.INVALID_SOURCE)
        val captures=envelope.getJSONArray("captures");require(captures.length()==request.uses.size)
        val lines=request.uses.mapIndexed {i,input->
            val c=captures.getJSONObject(i)
            keys(c,setOf("container_uuid","presentation_event_uuid","snapshot_uuid","requested_stock","requested_quanta","used_before_quanta","take_quanta","issue","stock_state","ledger_head_id"))
            require(text(c,"container_uuid")==input.containerUuid && text(c,"presentation_event_uuid")==input.presentationEventUuid)
            require(ReferencePayload.uuid(text(c,"snapshot_uuid")))
            if(!c.isNull("stock_state"))require(text(c,"stock_state") in setOf("SEALED","IN_USE","EMPTY","VOIDED","DISCARDED"))
            if(!c.isNull("ledger_head_id")) {val token=text(c,"ledger_head_id");require(Regex("0|[1-9][0-9]{0,18}").matches(token));require(token.toLong()>=0)}
            require(c.isNull("stock_state")==c.isNull("ledger_head_id"))
            if(!c.isNull("used_before_quanta"))quanta(c,"used_before_quanta")
            val takeCount=quanta(c,"take_quanta")
            val issue=if(c.isNull("issue"))null else text(c,"issue").also{require(it.isNotBlank() && it.length<=128)}
            if(c.isNull("requested_stock")) {
                require(c.isNull("requested_quanta") && takeCount==0L && issue!=null)
                if(request.stockEffect==MeasuredStockEffect.ALREADY_ACCOUNTED) {require(issue=="ALREADY_ACCOUNTED");PhysicalUseResult.AlreadyAccounted(input.containerUuid,input.quantity)}
                else PhysicalUseResult.Unknown(input.containerUuid,input.quantity,issue)
            } else {
                val q=PackageMeasurementPayload.quantity(c.getJSONObject("requested_stock"));require(q.value.signum()>0)
                val wantedCount=quanta(c,"requested_quanta");require(wantedCount>0 && takeCount<=wantedCount)
                val wanted=BigDecimal.valueOf(wantedCount);val take=BigDecimal.valueOf(takeCount)
                val allocated=q.copy(value=if(take.signum()==0)BigDecimal.ZERO else q.value.multiply(take).divide(wanted).stripTrailingZeros())
                PhysicalUseResult.Known(input.containerUuid,q,allocated,q.copy(value=q.value.subtract(allocated.value).stripTrailingZeros()))
            }
        }
        return MeasuredConsumptionReceipt(row.getString("operation_uuid"),request.record.uuid,request.record.revision,lines,ReferencePayload.optional(row,"unknown_reason"))
    }
    internal fun reverseCapture(row:JSONObject):JSONObject = try {
        val encoded=row.getString("input_json");val envelope=JSONObject(encoded)
        require(envelope.keys().asSequence().toSet()==setOf("format","request","chain") && envelope.get("format") is Int && envelope.getInt("format")==2)
        require(ReferencePayload.canonical(envelope)==encoded);MeasuredOperationChain.read(envelope)
        val input=envelope.getJSONObject("request")
        val coordinated=input.has("reconciliation")
        require(input.keys().asSequence().toSet()==setOf("format","kind","dataset_uuid","medication_uuid","record_uuid","record_revision","source_operation_uuid","source_request_sha256","source_allocation_uuids")+if(coordinated)setOf("reconciliation") else emptySet())
        require(input.get("format") is Int && input.getInt("format")==if(coordinated)2 else 1)
        require(input.get("kind") is String && input.getString("kind")=="REVERSE")
        if(coordinated)MeasuredReconciliation.read(input.getJSONObject("reconciliation"))
        require(input.get("record_revision") is Int && input.getInt("record_revision")>0 && input.getInt("record_revision")==row.getInt("record_revision"))
        for(key in listOf("dataset_uuid","medication_uuid","record_uuid","source_operation_uuid"))require(input.get(key) is String && ReferencePayload.uuid(input.getString(key)))
        require(input.get("source_request_sha256") is String && Regex("[0-9a-f]{64}").matches(input.getString("source_request_sha256")))
        require(row.getString("kind")=="REVERSE" && input.getString("dataset_uuid")==row.getString("dataset_uuid") && input.getString("record_uuid")==row.getString("record_uuid"))
        val allocations=input.getJSONArray("source_allocation_uuids");require(allocations.length()<=64)
        val ids=(0 until allocations.length()).map{require(allocations.get(it) is String);allocations.getString(it).also{value->require(ReferencePayload.uuid(value))}}
        require(ids==ids.sorted() && ids.distinct().size==ids.size)
        require(ReferencePayload.sha(ReferencePayload.canonical(input))==row.getString("request_sha256"))
        require(row.isNull("unallocated_quantity") && row.isNull("unallocated_unit") && row.isNull("unknown_reason"))
        input
    } catch(e:Exception){fail(ReferenceError.INVALID_SOURCE)}

    internal data class AdjustmentCapture(val request:JSONObject,val ledgerHead:Long,val bridgeFor:String?=null)
    /** Stable user intent is distinct from the ledger head observed during the original execution. */
    internal fun adjustmentCapture(row:JSONObject):AdjustmentCapture = try {
        fun keys(json:JSONObject,expected:Set<String>)=require(json.keys().asSequence().toSet()==expected)
        fun text(json:JSONObject,key:String):String {require(json.get(key) is String);return json.getString(key)}
        fun count(json:JSONObject,key:String,max:Long):Long {
            val token=text(json,key);require(Regex("0|[1-9][0-9]{0,18}").matches(token))
            return token.toLong().also{require(it<=max)}
        }
        val encoded=row.getString("input_json");val envelope=JSONObject(encoded)
        keys(envelope,setOf("format","request","execution","chain"));MeasuredOperationChain.read(envelope)
        require(envelope.get("format") is Int && envelope.getInt("format")==2 && ReferencePayload.canonical(envelope)==encoded)
        val request=envelope.getJSONObject("request")
        val bridge=request.has("accounting_bridge_for")
        keys(request,setOf("format","kind","stock_algorithm","conversion_algorithm","user_confirmed","dataset_uuid","medication_uuid","container_uuid","presentation_event_uuid","presentation_sha256","expected_used_quanta","expected_state","expected_opened_on","remaining_input","remaining_stock","remaining_quanta")+if(bridge)setOf("accounting_bridge_for") else emptySet())
        require(request.get("format") is Int && request.getInt("format")==if(bridge)2 else 1)
        require(text(request,"kind")=="ADJUST")
        val bridgeFor=if(bridge)text(request,"accounting_bridge_for").also{require(ReferencePayload.uuid(it))} else null
        require(text(request,"stock_algorithm")==StockQuantum.ALGORITHM && text(request,"conversion_algorithm")==Measurements.ALGORITHM)
        require(request.get("user_confirmed") is Boolean && request.getBoolean("user_confirmed")== !bridge)
        for(key in listOf("dataset_uuid","medication_uuid","container_uuid","presentation_event_uuid"))require(ReferencePayload.uuid(text(request,key)))
        require(Regex("[0-9a-f]{64}").matches(text(request,"presentation_sha256")))
        require(row.getString("kind")=="ADJUST" && row.getString("dataset_uuid")==text(request,"dataset_uuid") && row.isNull("record_uuid") && row.isNull("record_revision"))
        require(ReferencePayload.sha(ReferencePayload.canonical(request))==row.getString("request_sha256"))
        require(row.isNull("unallocated_quantity") && row.isNull("unallocated_unit") && row.isNull("unknown_reason"))
        count(request,"expected_used_quanta",StockQuantum.MAX_QUANTA);count(request,"remaining_quanta",StockQuantum.MAX_QUANTA)
        require(text(request,"expected_state") in setOf("SEALED","IN_USE","EMPTY","DISCARDED"))
        if(!request.isNull("expected_opened_on"))java.time.LocalDate.parse(text(request,"expected_opened_on"))
        val original=PackageMeasurementPayload.quantity(request.getJSONObject("remaining_input"))
        val normalized=PackageMeasurementPayload.quantity(request.getJSONObject("remaining_stock"))
        val converted=Measurements.add(normalized.copy(value=BigDecimal.ZERO),original)
        require(converted is MeasureResult.Success && converted.value.meaning==normalized.meaning && converted.value.unit==normalized.unit && converted.value.value.compareTo(normalized.value)==0)
        val execution=envelope.getJSONObject("execution");keys(execution,setOf("ledger_head_id"))
        AdjustmentCapture(request,count(execution,"ledger_head_id",Long.MAX_VALUE),bridgeFor)
    } catch(e:Exception){fail(ReferenceError.INVALID_SOURCE)}

    /** Inverse uses only original immutable allocation/ledger facts, never current product strength or catalogue data. */
    suspend fun reverseOriginal(database:NotesDatabase,scope:ReferenceScope,expected:RecordEntity,sourceOperation:String,
        operationUuid:String,created:Instant):Int = reverse(database,scope,expected,sourceOperation,operationUuid,created,null)

    private suspend fun reverse(database:NotesDatabase,scope:ReferenceScope,expected:RecordEntity,sourceOperation:String,
        operationUuid:String,created:Instant,reconciliation:JSONObject?):Int {
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
        if(reconciliation!=null)input.put("format",2).put("reconciliation",reconciliation)
        val canonical=ReferencePayload.canonical(input);val hash=ReferencePayload.sha(canonical)
        if(ReferenceStore.row(db,"reference_event","operation_uuid=?",arrayOf(operationUuid))!=null)fail(ReferenceError.OPERATION_CONFLICT)
        ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(operationUuid))?.let {old->
            if(old.getString("dataset_uuid")!=scope.dataset || old.getString("kind")!="REVERSE" || old.getString("request_sha256")!=hash || ReferencePayload.canonical(reverseCapture(old))!=canonical)fail(ReferenceError.OPERATION_CONFLICT)
            return allocationIds.size
        }
        if(current!=expected || current.revision<source.getInt("record_revision"))fail(ReferenceError.STALE_REVISION)
        db.query("SELECT operation_uuid FROM measured_operation WHERE record_uuid=? AND kind='REVERSE'",arrayOf(record)).use {c->while(c.moveToNext()) {
            if(reverseCapture(ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(c.getString(0)))!!).getString("source_operation_uuid")==sourceOperation)fail(ReferenceError.OPERATION_CONFLICT)
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
            if(box.inventory_encoding!=InventoryEncoding.EXACT || box.state=="VOIDED" || box.used_amount<entry.used_delta || !box.used_amount.isFinite() || box.used_amount.toLong().toDouble()!=box.used_amount)fail(ReferenceError.OPERATION_CONFLICT)
            val quantity=Measurements.parse(a.getString("quantity"));if(quantity !is MeasureResult.Success || quantity.value.signum()<=0 || (Measurements.decimalText(quantity.value) as MeasureResult.Success).value!=a.getString("quantity"))fail(ReferenceError.INVALID_SOURCE)
            MeasureUnit.valueOf(a.getString("unit"))
            Triple(a,entry,box)
        }
        // A count containing the original consumption is an independent observation, including no-op counts.
        // Its captured ledger head establishes causality without interpreting timestamps as operation order.
        val affected=originals.associateBy{it.first.getString("container_uuid")}
        db.query("SELECT operation_uuid FROM measured_operation WHERE dataset_uuid=? AND kind='ADJUST'",arrayOf(scope.dataset)).use {c->while(c.moveToNext()) {
            val observation=adjustmentCapture(ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(c.getString(0)))!!)
            if(observation.bridgeFor==null)affected[observation.request.getString("container_uuid")]?.let {original->
                if(observation.ledgerHead>0)db.query("SELECT container_id FROM supply_transaction WHERE id=?",arrayOf(observation.ledgerHead)).use {head->
                    if(!head.moveToFirst() || head.getLong(0)!=original.third.id)fail(ReferenceError.INVALID_SOURCE)
                }
                if(observation.ledgerHead>=original.second.id && reconciliation==null)fail(ReferenceError.OPERATION_CONFLICT)
            }
        }}
        ReferenceStore.insert(db,"measured_operation",JSONObject().put("operation_uuid",operationUuid).put("dataset_uuid",scope.dataset).put("record_uuid",record)
            .put("record_revision",current.revision).put("kind","REVERSE").put("request_sha256",hash).put("input_json",ReferencePayload.canonical(JSONObject().put("format",2).put("request",input).put("chain",MeasuredOperationChain.next(db))))
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
        presentationEvent:String,presentationSha:String,remaining:Quantity,userConfirmed:Boolean,operationUuid:String,created:Instant):MeasureResult<Quantity> =
        adjust(database,scope,expected,containerUuid,presentationEvent,presentationSha,remaining,userConfirmed,operationUuid,created,null)

    private suspend fun adjust(database:NotesDatabase,scope:ReferenceScope,expected:ContainerEntity,containerUuid:String,
        presentationEvent:String,presentationSha:String,remaining:Quantity,userConfirmed:Boolean,operationUuid:String,created:Instant,bridgeFor:String?):MeasureResult<Quantity> {
        val db=database.openHelper.writableDatabase;check(db.inTransaction());ReferenceStore.checkScope(db,scope)
        if(userConfirmed!=(bridgeFor==null) || !ReferencePayload.uuid(operationUuid) || created.toEpochMilli()<0)fail(ReferenceError.INVALID_SOURCE)
        val dao=database.dao();val identity=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(containerUuid)) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
        if(identity.getString("dataset_uuid")!=scope.dataset || identity.getString("kind")!="CONTAINER" || identity.isNull("live_handle") || identity.getLong("live_handle")!=expected.id)fail(ReferenceError.UNKNOWN_IDENTITY)
        val event=ReferenceStore.row(db,"reference_event","operation_uuid=?",arrayOf(presentationEvent)) ?: fail(ReferenceError.INVALID_SOURCE)
        if(event.getString("dataset_uuid")!=scope.dataset || event.getString("subject_uuid")!=containerUuid || event.isNull("snapshot_uuid"))fail(ReferenceError.INVALID_SOURCE)
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
        if(expected.inventory_encoding!=InventoryEncoding.EXACT || expected.capacity!=stock.capacityQuanta.toDouble() || expected.initial_used_amount!=0.0 || !expected.used_amount.isFinite() || expected.used_amount<0 ||
            expected.used_amount>stock.capacityQuanta || expected.used_amount.toLong().toDouble()!=expected.used_amount)fail(ReferenceError.INVALID_SOURCE)
        val input=JSONObject().put("format",if(bridgeFor==null)1 else 2).put("kind","ADJUST").put("stock_algorithm",StockQuantum.ALGORITHM).put("conversion_algorithm",Measurements.ALGORITHM).put("user_confirmed",userConfirmed).put("dataset_uuid",scope.dataset).put("medication_uuid",identity.getString("owner_uuid"))
            .put("container_uuid",containerUuid).put("presentation_event_uuid",presentationEvent).put("presentation_sha256",presentationSha)
            .put("expected_used_quanta",expected.used_amount.toLong().toString()).put("expected_state",expected.state).put("expected_opened_on",expected.opened_on ?: JSONObject.NULL)
            .put("remaining_input",PackageMeasurementPayload.quantity(remaining)).put("remaining_stock",PackageMeasurementPayload.quantity(target)).put("remaining_quanta",remainingQuanta.toString())
        if(bridgeFor!=null)input.put("accounting_bridge_for",bridgeFor)
        val canonical=ReferencePayload.canonical(input);val hash=ReferencePayload.sha(canonical)
        if(ReferenceStore.row(db,"reference_event","operation_uuid=?",arrayOf(operationUuid))!=null)fail(ReferenceError.OPERATION_CONFLICT)
        ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(operationUuid))?.let {old->
            if(old.getString("dataset_uuid")!=scope.dataset || old.getString("kind")!="ADJUST" || old.getString("request_sha256")!=hash)fail(ReferenceError.OPERATION_CONFLICT)
            val captured=adjustmentCapture(old)
            if(ReferencePayload.canonical(captured.request)!=canonical)fail(ReferenceError.OPERATION_CONFLICT)
            if(captured.ledgerHead>0)db.query("SELECT container_id FROM supply_transaction WHERE id=?",arrayOf(captured.ledgerHead)).use {head->
                if(!head.moveToFirst() || head.getLong(0)!=expected.id)fail(ReferenceError.INVALID_SOURCE)
            }
            return MeasureResult.Success(target) // The original observation, never a new count or today's balance.
        }
        val head=ReferenceStore.row(db,"reference_event","subject_uuid=? ORDER BY revision DESC LIMIT 1",arrayOf(containerUuid))!!
        if(head.getString("operation_uuid")!=presentationEvent)fail(ReferenceError.STALE_REVISION)
        val current=dao.container(expected.id);if(current!=expected)fail(ReferenceError.STALE_REVISION)
        if(current.state=="VOIDED")return MeasureResult.Failure(MeasureError.VOIDED)
        val newUsed=stock.capacityQuanta-remainingQuanta;val delta=newUsed-current.used_amount.toLong()
        val ledgerHead=db.query("SELECT COALESCE(MAX(id),0) FROM supply_transaction WHERE container_id=?",arrayOf(current.id)).use{it.moveToFirst();it.getLong(0)}
        val envelope=JSONObject().put("format",2).put("request",input).put("execution",JSONObject().put("ledger_head_id",ledgerHead.toString())).put("chain",MeasuredOperationChain.next(db))
        ReferenceStore.insert(db,"measured_operation",JSONObject().put("operation_uuid",operationUuid).put("dataset_uuid",scope.dataset).put("record_uuid",JSONObject.NULL)
            .put("record_revision",JSONObject.NULL).put("kind","ADJUST").put("request_sha256",hash).put("input_json",ReferencePayload.canonical(envelope))
            .put("unallocated_quantity",JSONObject.NULL).put("unallocated_unit",JSONObject.NULL).put("unknown_reason",JSONObject.NULL).put("created_utc",created.toEpochMilli()))
        if(delta!=0L) {
            val entry=dao.supply(SupplyEntryEntity(container_id=current.id,operation_id=operationUuid,kind="ADJUST",used_delta=delta.toDouble(),
                created_utc=created.toEpochMilli(),created_zone="UTC",reason=if(bridgeFor==null)"confirmed physical remaining count" else "accounting bridge for explicit reconciliation; not a physical count"))
            ReferenceStore.insert(db,"measured_allocation",JSONObject().put("uuid",UUID.randomUUID().toString()).put("dataset_uuid",scope.dataset).put("operation_uuid",operationUuid)
                .put("container_uuid",containerUuid).put("presentation_event_uuid",presentationEvent).put("ledger_entry_id",entry)
                .put("quantity",(Measurements.decimalText(amount(stock,kotlin.math.abs(delta)).value) as MeasureResult.Success).value).put("unit",stock.quantum.unit.name).put("reverses_uuid",JSONObject.NULL))
            val state=when{newUsed==stock.capacityQuanta->"EMPTY";current.state=="EMPTY" || current.state=="SEALED"->"IN_USE";else->current.state}
            dao.setContainerState(current.id,state,current.opened_on)
        }
        return MeasureResult.Success(target)
    }

    internal fun replayReconciliation(db:SupportSQLiteDatabase,scope:ReferenceScope,source:String,reverse:String,counts:List<ConfirmedStockCount>):Boolean {
        val row=ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(reverse)) ?: return false
        if(row.getString("kind")!="REVERSE")fail(ReferenceError.OPERATION_CONFLICT)
        val input=reverseCapture(row)
        if(row.getString("dataset_uuid")!=scope.dataset || input.getString("source_operation_uuid")!=source || !input.has("reconciliation") ||
            !MeasuredReconciliation.sameInputs(input.getJSONObject("reconciliation"),counts))fail(ReferenceError.OPERATION_CONFLICT)
        MeasuredReconciliation.verify(db,row,input,MeasuredOperationChain.validate(db));return true
    }
    /** Private bridge/reverse writes are committed only with complete confirmed final counts and full audit. */
    internal suspend fun reconcileOriginal(database:NotesDatabase,scope:ReferenceScope,expected:RecordEntity,source:String,operation:String,
        counts:List<ConfirmedStockCount>,created:Instant,checkpoint:(ReconciliationCheckpoint)->Unit) {
        val db=database.openHelper.writableDatabase;check(db.inTransaction());ReferenceStore.checkScope(db,scope)
        if(replayReconciliation(db,scope,source,operation,counts))return
        auditStagedDataset(db)
        val plan=try{MeasuredReconciliation.capture(database,scope,source,operation,counts)}catch(e:Exception){fail(ReferenceError.INVALID_SOURCE)}
        for(target in plan.targets) if(target.bridgeUuid!=null) {
            val result=adjust(database,scope,target.container,target.count.containerUuid,target.count.presentationEventUuid,target.count.presentationSha256,
                requireNotNull(target.bridgeRemaining),false,target.bridgeUuid,created,operation)
            if(result !is MeasureResult.Success)fail(ReferenceError.INVALID_SOURCE)
        }
        checkpoint(ReconciliationCheckpoint.BRIDGES_WRITTEN)
        reverse(database,scope,expected,source,operation,created,plan.proof);checkpoint(ReconciliationCheckpoint.ORIGINAL_REVERSED)
        for(target in plan.targets) {
            val c=target.count
            val result=adjustRemaining(database,scope,database.dao().container(target.container.id),c.containerUuid,c.presentationEventUuid,c.presentationSha256,c.remaining,c.userConfirmed,target.countUuid,created)
            if(result !is MeasureResult.Success)fail(ReferenceError.INVALID_SOURCE)
        }
        checkpoint(ReconciliationCheckpoint.COUNTS_CAPTURED)
    }

    suspend fun consumeNewRecord(database:NotesDatabase,scope:ReferenceScope,expected:RecordEntity,operationUuid:String,
        uses:List<ConfirmedStockUse>,created:Instant,stockEffect:MeasuredStockEffect=MeasuredStockEffect.DEDUCT_CURRENT,unlinkedActual:Quantity?=null):MeasuredConsumptionReceipt {
        val db=database.openHelper.writableDatabase;check(db.inTransaction());ReferenceStore.checkScope(db,scope)
        if(!ReferencePayload.uuid(operationUuid) || created.toEpochMilli()<0)fail(ReferenceError.INVALID_SOURCE)
        val dao=database.dao();val current=dao.recordById(expected.id) ?: fail(ReferenceError.UNKNOWN_IDENTITY)
        val medication=ReferenceStore.identity(db,scope,"MEDICATION",current.medication_id,null)
        val record=ReferenceStore.identity(db,scope,"RECORD",current.id,medication)
        val frozen=MeasuredUsePayload.freeze(MeasuredConsumptionRequest(scope.dataset,medication,MeasuredUsePayload.record(record,expected),uses,stockEffect,unlinkedActual))
        if(ReferenceStore.row(db,"reference_event","operation_uuid=?",arrayOf(operationUuid))!=null)fail(ReferenceError.OPERATION_CONFLICT)
        ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(operationUuid))?.let {old->
            if(old.getString("dataset_uuid")!=scope.dataset || old.getString("record_uuid")!=record || old.getString("kind")!="CONSUME" || old.getString("request_sha256")!=frozen.sha256)fail(ReferenceError.OPERATION_CONFLICT)
            return receipt(old)
        }
        if(current!=expected)fail(ReferenceError.STALE_REVISION)
        if(current.origin!="APP")fail(ReferenceError.INVALID_SOURCE)
        if(current.unallocated_supply_amount!=null)fail(ReferenceError.ALREADY_CONSUMED)
        val prior=dao.supplyFor(current.id)
        if(prior.isNotEmpty()) {
            // A new revision may follow only a complete, audited inverse of all earlier exact consumption.
            val reversed=prior.mapNotNull{it.reversal_of_id}.toSet()
            if(current.revision<=1 || prior.any{it.kind !in setOf("CONSUME","REVERSE") || (it.dose_revision==null || it.dose_revision>=current.revision)} ||
                prior.filter{it.kind=="CONSUME"}.any{it.id !in reversed})fail(ReferenceError.ALREADY_CONSUMED)
            for(entry in prior)db.query("SELECT 1 FROM measured_allocation WHERE ledger_entry_id=?",arrayOf(entry.id)).use{if(!it.moveToFirst())fail(ReferenceError.ALREADY_CONSUMED)}
            auditStagedDataset(db)
        }
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
            if(scale is MeasureResult.Success && box!=null && (box.inventory_encoding!=InventoryEncoding.EXACT || box.capacity!=scale.value.capacityQuanta.toDouble() || box.initial_used_amount!=0.0 ||
                    !box.used_amount.isFinite() || box.used_amount<0 || box.used_amount>scale.value.capacityQuanta || box.used_amount.toLong().toDouble()!=box.used_amount))fail(ReferenceError.INVALID_SOURCE)
            if(stockEffect!=MeasuredStockEffect.DEDUCT_CURRENT) {
                require(scale is MeasureResult.Success)
                Plan(input,box,sid,scale.value,null,null,box?.used_amount?.toLong(),0,if(stockEffect==MeasuredStockEffect.ALREADY_ACCOUNTED)"ALREADY_ACCOUNTED" else "STOCK_EFFECT_NOT_CONFIRMED")
            }
            else if(scale !is MeasureResult.Success)Plan(input,box,sid,null,null,null,null,0,(scale as MeasureResult.Failure).error.name)
            else {
                val s=scale.value
                if(box!=null && (box.inventory_encoding!=InventoryEncoding.EXACT || box.capacity!=s.capacityQuanta.toDouble() || box.initial_used_amount!=0.0 || !box.used_amount.isFinite() ||
                    box.used_amount<0 || box.used_amount>s.capacityQuanta || box.used_amount.toLong().toDouble()!=box.used_amount))fail(ReferenceError.INVALID_SOURCE)
                val physical=physicalStock(presentation,s,input.quantity)
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
        val issue=when {stockEffect==MeasuredStockEffect.ALREADY_ACCOUNTED->null;stockEffect==MeasuredStockEffect.UNKNOWN->"STOCK_EFFECT_NOT_CONFIRMED";plans.isEmpty()->"ACTUAL_PACKAGE_NOT_CONFIRMED";plans.size==1->plans.single().issue;plans.any{it.issue!=null}->"MULTIPLE_SOURCE_BALANCES";else->null}
        val left=(plans.singleOrNull()?.let {p->if(p.requested==null)p.input.quantity else amount(p.scale!!,p.wanted!!-p.take)} ?: unlinkedActual)?.takeIf{issue!=null}
        val envelope=JSONObject().put("format",2).put("request",JSONObject(frozen.json)).put("captures",JSONArray(plans.map{captureJson(db,it)})).put("chain",MeasuredOperationChain.next(db))
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
