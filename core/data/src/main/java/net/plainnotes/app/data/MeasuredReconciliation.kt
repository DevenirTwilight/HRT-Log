package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import net.plainnotes.app.domain.measurement.*
import net.plainnotes.app.domain.stock.StockQuantum
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Read-only construction/audit of explicit reconciliation. Accounting bridges are never physical observations. */
internal object MeasuredReconciliation {
    data class Target(val container:ContainerEntity,val count:ConfirmedStockCount,val bridgeRemaining:Quantity?,val countUuid:String,val bridgeUuid:String?)
    data class Plan(val proof:JSONObject,val targets:List<Target>)
    private val fields=setOf("format","user_confirmed","targets")
    private val targetFields=setOf("container_uuid","presentation_event_uuid","presentation_sha256","remaining_input","initial_used_quanta","source_ledger_id","source_quanta","count_uuid","bridge_uuid")
    private fun keys(j:JSONObject,s:Set<String>)=require(j.keys().asSequence().toSet()==s)
    private fun uuid(j:JSONObject,k:String)=j.getString(k).also{require(j.get(k) is String && ReferencePayload.uuid(it))}
    private fun integer(j:JSONObject,k:String,max:Long=StockQuantum.MAX_QUANTA)=j.getString(k).also{require(j.get(k) is String && Regex("0|[1-9][0-9]{0,18}").matches(it))}.toLong().also{require(it<=max)}
    private fun derived(reverse:String,container:String,kind:String)=UUID.nameUUIDFromBytes("ansm-reconcile-v1:$reverse:$container:$kind".toByteArray(Charsets.UTF_8)).toString()
    fun inputs(counts:List<ConfirmedStockCount>):JSONArray {
        require(counts.isNotEmpty() && counts.size<=64 && counts.map{it.containerUuid}.distinct().size==counts.size)
        return JSONArray(counts.sortedBy{it.containerUuid}.map {c->
            require(c.userConfirmed && ReferencePayload.uuid(c.containerUuid) && ReferencePayload.uuid(c.presentationEventUuid) && Regex("[0-9a-f]{64}").matches(c.presentationSha256))
            JSONObject().put("container_uuid",c.containerUuid).put("presentation_event_uuid",c.presentationEventUuid)
                .put("presentation_sha256",c.presentationSha256).put("remaining_input",PackageMeasurementPayload.quantity(c.remaining))
        })
    }
    fun sameInputs(proof:JSONObject,counts:List<ConfirmedStockCount>):Boolean = runCatching {
        val targets=read(proof)
        val actual=JSONArray(targets.map{t->JSONObject().apply{for(k in listOf("container_uuid","presentation_event_uuid","presentation_sha256","remaining_input"))put(k,t.get(k))}})
        ReferencePayload.canonical(JSONObject().put("counts",actual))==ReferencePayload.canonical(JSONObject().put("counts",inputs(counts)))
    }.getOrDefault(false)
    fun read(proof:JSONObject):List<JSONObject> {
        keys(proof,fields);require(proof.get("format") is Int && proof.getInt("format")==1 && proof.get("user_confirmed") is Boolean && proof.getBoolean("user_confirmed"))
        val a=proof.getJSONArray("targets");require(a.length() in 1..64)
        val rows=(0 until a.length()).map{a.getJSONObject(it)}
        val containers=rows.map {t->
            keys(t,targetFields);val c=uuid(t,"container_uuid");uuid(t,"presentation_event_uuid");uuid(t,"count_uuid")
            if(!t.isNull("bridge_uuid"))uuid(t,"bridge_uuid")
            require(t.get("presentation_sha256") is String && Regex("[0-9a-f]{64}").matches(t.getString("presentation_sha256")))
            require(integer(t,"source_ledger_id",Long.MAX_VALUE)>0);require(integer(t,"source_quanta")>0);integer(t,"initial_used_quanta")
            require(PackageMeasurementPayload.quantity(t.getJSONObject("remaining_input")).value.signum()>=0)
            c
        }
        require(containers==containers.sorted() && containers.distinct().size==containers.size)
        return rows
    }
    suspend fun capture(database:NotesDatabase,scope:ReferenceScope,source:String,reverse:String,counts:List<ConfirmedStockCount>):Plan {
        val db=database.openHelper.writableDatabase;check(db.inTransaction());ReferenceStore.checkScope(db,scope)
        val intent=inputs(counts);val byContainer=counts.associateBy{it.containerUuid}
        val allocations=db.query("SELECT container_uuid,ledger_entry_id,presentation_event_uuid FROM measured_allocation WHERE operation_uuid=? ORDER BY container_uuid",arrayOf(source)).use{c->buildList{while(c.moveToNext())add(Triple(c.getString(0),c.getLong(1),c.getString(2)))}}
        require(allocations.isNotEmpty() && allocations.map{it.first}.toSet()==byContainer.keys && allocations.size==counts.size)
        val proofRows=JSONArray();val targets=allocations.mapIndexed {index,(container,ledger,event)->
            val c=byContainer.getValue(container);require(c.presentationEventUuid==event)
            val identity=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(container))!!
            require(identity.getString("dataset_uuid")==scope.dataset && identity.getString("kind")=="CONTAINER" && !identity.isNull("live_handle"))
            val box=database.dao().container(identity.getLong("live_handle"));require(box.inventory_encoding==InventoryEncoding.EXACT && box.state in setOf("SEALED","IN_USE","EMPTY"))
            val e=ReferenceStore.row(db,"reference_event","operation_uuid=?",arrayOf(event))!!
            require(e.getString("dataset_uuid")==scope.dataset && e.getString("subject_uuid")==container)
            val snapshot=ReferenceStore.row(db,"reference_snapshot","uuid=?",arrayOf(e.getString("snapshot_uuid")))!!
            val frozen=PackageMeasurementPayload.read(snapshot.getString("payload_json"));require(frozen.sha256==c.presentationSha256 && snapshot.getString("payload_sha256")==c.presentationSha256)
            val result=frozen.stockScale();require(result is MeasureResult.Success);val scale=result.value
            require(box.capacity==scale.capacityQuanta.toDouble() && box.initial_used_amount==0.0)
            val used=InventoryEncoding.exactInteger(box.used_amount)
            val delta=db.query("SELECT used_delta,container_id,kind FROM supply_transaction WHERE id=?",arrayOf(ledger)).use {r->require(r.moveToFirst() && r.getLong(1)==box.id && r.getString(2)=="CONSUME");InventoryEncoding.exactInteger(r.getDouble(0))}
            require(delta in 1..scale.capacityQuanta)
            val normalized=Measurements.add(scale.quantum.copy(value=java.math.BigDecimal.ZERO),c.remaining);require(normalized is MeasureResult.Success)
            val remaining=normalized.value
            val q=if(remaining.value.signum()==0)0L else {val v=scale.toQuanta(remaining,ActualPackage(scale.ref,true));require(v is MeasureResult.Success);v.value}
            require(q in 0..scale.capacityQuanta)
            val bridge=if(used<delta)derived(reverse,container,"bridge") else null
            val count=derived(reverse,container,"count")
            val input=intent.getJSONObject(index)
            proofRows.put(JSONObject(input.toString()).put("initial_used_quanta",used.toString()).put("source_ledger_id",ledger.toString()).put("source_quanta",delta.toString())
                .put("count_uuid",count).put("bridge_uuid",bridge ?: JSONObject.NULL))
            val bridgeQuantity=if(bridge==null)null else {val v=scale.remaining(delta);require(v is MeasureResult.Success);v.value}
            Target(box,c,bridgeQuantity,count,bridge)
        }
        return Plan(JSONObject().put("format",1).put("user_confirmed",true).put("targets",proofRows).also{read(it)},targets)
    }
    /** Full closed proof, including every bridge and final count. Called by both projection and dataset audit. */
    fun verify(db:SupportSQLiteDatabase,reverse:JSONObject,input:JSONObject,chain:Map<String,MeasuredOperationChain.Node>) {
        val rows=read(input.getJSONObject("reconciliation"));val reverseUuid=reverse.getString("operation_uuid")
        val source=input.getString("source_operation_uuid");val dataset=reverse.getString("dataset_uuid")
        val allocations=db.query("SELECT container_uuid,ledger_entry_id,presentation_event_uuid FROM measured_allocation WHERE operation_uuid=?",arrayOf(source)).use{c->buildMap{while(c.moveToNext())require(put(c.getString(0),c.getLong(1))==null)}}
        require(allocations.keys==rows.map{it.getString("container_uuid")}.toSet())
        for(t in rows)require(db.query("SELECT 1 FROM measured_allocation WHERE operation_uuid=? AND container_uuid=? AND presentation_event_uuid=?",arrayOf(source,t.getString("container_uuid"),t.getString("presentation_event_uuid"))).use{it.moveToFirst()})
        val bridges=rows.filter{!it.isNull("bridge_uuid")};val reverseSeq=chain.getValue(reverseUuid).sequence
        val base=reverseSeq-bridges.size;require(base>0)
        for((index,t) in rows.withIndex()) {
            val container=t.getString("container_uuid");val event=t.getString("presentation_event_uuid")
            require(allocations.getValue(container)==integer(t,"source_ledger_id",Long.MAX_VALUE))
            val sourceDelta=db.query("SELECT used_delta,operation_id FROM supply_transaction WHERE id=?",arrayOf(allocations.getValue(container))).use{c->require(c.moveToFirst() && c.getString(1)==source);InventoryEncoding.exactInteger(c.getDouble(0))}
            require(sourceDelta==integer(t,"source_quanta"))
            val before=integer(t,"initial_used_quanta");val needsBridge=before<sourceDelta
            require(needsBridge== !t.isNull("bridge_uuid"))
            val countUuid=derived(reverseUuid,container,"count");require(t.getString("count_uuid")==countUuid)
            val count=ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(countUuid))!!
            require(count.getString("dataset_uuid")==dataset && chain.getValue(countUuid).sequence==reverseSeq+index+1)
            val countCapture=MeasuredConsumptionStore.adjustmentCapture(count);val countRequest=countCapture.request
            require(countCapture.bridgeFor==null && countRequest.getString("container_uuid")==container && countRequest.getString("presentation_event_uuid")==event && countRequest.getString("presentation_sha256")==t.getString("presentation_sha256"))
            require(ReferencePayload.canonical(countRequest.getJSONObject("remaining_input"))==ReferencePayload.canonical(t.getJSONObject("remaining_input")))
            require(countRequest.getString("expected_used_quanta")== (maxOf(before,sourceDelta)-sourceDelta).toString())
            val sourceIdentity=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(container))!!
            require(sourceIdentity.getString("dataset_uuid")==dataset && sourceIdentity.getString("owner_uuid")==input.getString("medication_uuid"))
            require(db.query("SELECT 1 FROM measured_allocation a WHERE a.operation_uuid=? AND a.container_uuid=? AND a.ledger_entry_id=? AND a.reverses_uuid IS NOT NULL",arrayOf(reverseUuid,container,countCapture.ledgerHead)).use{it.moveToFirst()})
            if(needsBridge) {
                val bridgeUuid=derived(reverseUuid,container,"bridge");require(t.getString("bridge_uuid")==bridgeUuid)
                val bridge=ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(bridgeUuid))!!
                require(bridge.getString("dataset_uuid")==dataset && chain.getValue(bridgeUuid).sequence==base+bridges.indexOf(t))
                val capture=MeasuredConsumptionStore.adjustmentCapture(bridge);val r=capture.request
                require(capture.bridgeFor==reverseUuid && r.getString("container_uuid")==container && r.getString("presentation_event_uuid")==event && r.getString("presentation_sha256")==t.getString("presentation_sha256"))
                require(r.getString("expected_used_quanta")==before.toString())
                val snap=ReferenceStore.row(db,"reference_snapshot","uuid=?",arrayOf(ReferenceStore.row(db,"reference_event","operation_uuid=?",arrayOf(event))!!.getString("snapshot_uuid")))!!
                val result=PackageMeasurementPayload.read(snap.getString("payload_json")).stockScale();require(result is MeasureResult.Success)
                require(r.getString("remaining_quanta")== (result.value.capacityQuanta-sourceDelta).toString())
            } else {
                // No bridge: the original inverse must start directly from this captured used amount.
                val box=sourceIdentity.getLong("live_handle")
                val sum=db.query("SELECT used_delta FROM supply_transaction WHERE container_id=? AND id<? ORDER BY id",arrayOf(box,countCapture.ledgerHead)).use{c->var v=0L;while(c.moveToNext()){val n=c.getDouble(0);require(n.toLong().toDouble()==n);v=Math.addExact(v,n.toLong())};v}
                require(sum==before)
            }
        }
    }
    fun verifyBridge(db:SupportSQLiteDatabase,row:JSONObject,reverseUuid:String,chain:Map<String,MeasuredOperationChain.Node>) {
        val reverse=ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(reverseUuid))!!
        val input=MeasuredConsumptionStore.reverseCapture(reverse);require(input.has("reconciliation"))
        require(read(input.getJSONObject("reconciliation")).count{!it.isNull("bridge_uuid") && it.getString("bridge_uuid")==row.getString("operation_uuid")}==1)
        verify(db,reverse,input,chain)
    }
}
