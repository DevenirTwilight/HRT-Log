package net.plainnotes.app.data

import java.lang.reflect.InvocationTargetException
import java.math.BigDecimal
import net.plainnotes.app.domain.measurement.*
import net.plainnotes.app.domain.stock.StockQuantum
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*

/** Frozen pre-refactor decoder from65cf08e; independent of the candidate decoder.
 * Synthetic structural fixtures, no database or user records. Reused on JVM and native SQLCipher suites. */
internal object ConsumptionReceiptOracle {
    private fun invalid(error:ReferenceError):Nothing=throw ReferenceFailure(error)
    private fun old(row:JSONObject):MeasuredConsumptionReceipt = try{readReceipt(row)}catch(_:Exception){invalid(ReferenceError.INVALID_SOURCE)}
    private val method=MeasuredConsumptionStore::class.java.getDeclaredMethod("receipt",JSONObject::class.java).apply{isAccessible=true}
    private fun current(row:JSONObject):MeasuredConsumptionReceipt = try{method.invoke(MeasuredConsumptionStore,row) as MeasuredConsumptionReceipt}
        catch(e:InvocationTargetException){throw e.targetException}
    private fun id(n:Int)="00000000-0000-4000-8000-"+n.toString().padStart(12,'0')
    private fun fixture(effect:MeasuredStockEffect=MeasuredStockEffect.DEDUCT_CURRENT,lines:Int=1,take:Int=2):JSONObject {
        val clinical=RecordEntity(id=1,medication_id=1,taken_utc=1000,taken_zone=null,actual_dose=0.1,status="ON_TIME",origin="APP",revision=1,config_snapshot="{}")
        val uses=(1..lines).map { n ->ConfirmedStockUse(id(n),id(n+10),"a".repeat(64),
            Quantity(BigDecimal("0.5"),if(n%2==0)MeasureUnit.PUMP else MeasureUnit.TABLET,Meaning(QuantityKind.USE_COUNT,"SYNTHETIC source $n")),true)}
        val frozen=MeasuredUsePayload.freeze(MeasuredConsumptionRequest(id(100),id(101),MeasuredUsePayload.record(id(102),clinical),uses,effect))
        val captures=JSONArray()
        for((index,input) in frozen.request().uses.withIndex()) {
            val known=effect==MeasuredStockEffect.DEDUCT_CURRENT
            val issue=when{effect==MeasuredStockEffect.ALREADY_ACCOUNTED->"ALREADY_ACCOUNTED";!known->"STOCK_EFFECT_NOT_CONFIRMED";take<2->"INSUFFICIENT_STOCK";else->null}
            captures.put(JSONObject().put("container_uuid",input.containerUuid).put("presentation_event_uuid",input.presentationEventUuid)
                .put("snapshot_uuid",id(500+index)).put("requested_stock",if(known)PackageMeasurementPayload.quantity(input.quantity) else JSONObject.NULL)
                .put("requested_quanta",if(known)"2" else JSONObject.NULL).put("used_before_quanta","0")
                .put("take_quanta",if(known)take.toString() else "0").put("issue",issue ?: JSONObject.NULL)
                .put("stock_state","IN_USE").put("ledger_head_id","0"))
        }
        val envelope=JSONObject().put("format",2).put("request",JSONObject(frozen.json)).put("captures",captures)
            .put("chain",JSONObject().put("sequence","1").put("previous_operation_uuid",JSONObject.NULL).put("previous_payload_sha256",JSONObject.NULL))
        return JSONObject().put("operation_uuid",id(200)).put("kind","CONSUME").put("input_json",ReferencePayload.canonical(envelope))
            .put("request_sha256",frozen.sha256).put("dataset_uuid",id(100)).put("record_uuid",id(102)).put("record_revision",1).put("unknown_reason",JSONObject.NULL)
    }
    private fun change(row:JSONObject,block:(JSONObject)->Unit):JSONObject {
        val value=JSONObject(row.getString("input_json"));block(value)
        return JSONObject(row.toString()).put("input_json",ReferencePayload.canonical(value))
            .put("request_sha256",ReferencePayload.sha(ReferencePayload.canonical(value.getJSONObject("request"))))
    }
    fun validCorpus() {
        val rows=buildList {
            for(effect in MeasuredStockEffect.entries)for(lines in listOf(0,1,2,4))for(take in 0..2)add(fixture(effect,lines,take))
            // Preserve existing row getter behavior; this refactor is not a storage-contract change.
            add(fixture().put("record_revision","1"))
            add(fixture().put("operation_uuid",id(900)))
            add(change(fixture()){it.getJSONObject("chain").put("sequence","2").put("previous_operation_uuid",id(201)).put("previous_payload_sha256","b".repeat(64))})
        }
        for(row in rows)assertEquals(old(row),current(row))
        // Same operation identity with different complete payload must not reuse a previous result.
        val first=fixture(lines=1);val second=fixture(lines=2)
        assertNotEquals(old(first),old(second))
        repeat(3){assertEquals(old(first),current(first));assertEquals(old(second),current(second))}
        assertEquals(39,rows.size)
        val partial=fixture(take=1)
        for(value in listOf(old(partial),current(partial))) {
            val line=value.lines.single() as PhysicalUseResult.Known
            assertEquals(BigDecimal("0.5"),line.requested.value)
            assertEquals(BigDecimal("0.25"),line.allocated.value)
            assertEquals(BigDecimal("0.25"),line.unallocated.value)
        }
        for(value in listOf(old(fixture(MeasuredStockEffect.ALREADY_ACCOUNTED)),current(fixture(MeasuredStockEffect.ALREADY_ACCOUNTED))))
            assertTrue(value.lines.single() is PhysicalUseResult.AlreadyAccounted)
        for(value in listOf(old(fixture(MeasuredStockEffect.UNKNOWN)),current(fixture(MeasuredStockEffect.UNKNOWN))))
            assertEquals("STOCK_EFFECT_NOT_CONFIRMED",(value.lines.single() as PhysicalUseResult.Unknown).reason)
    }
    fun malformedCorpus() {
        val base=fixture()
        val edits=listOf<(JSONObject)->Unit>(
            {it.put("format","2")},{it.put("format",3)},{it.put("extra",true)},
            {it.getJSONObject("chain").put("sequence","01")},{it.getJSONObject("chain").put("previous_operation_uuid",id(201))},
            {it.getJSONArray("captures").put(JSONObject())},
            {it.getJSONArray("captures").getJSONObject(0).put("extra",true)},
            {it.getJSONArray("captures").getJSONObject(0).remove("snapshot_uuid")},
            {it.getJSONArray("captures").getJSONObject(0).put("snapshot_uuid","bad")},
            {it.getJSONArray("captures").getJSONObject(0).put("container_uuid",id(999))},
            {it.getJSONArray("captures").getJSONObject(0).put("presentation_event_uuid",id(999))},
            {it.getJSONArray("captures").getJSONObject(0).put("stock_state","UNKNOWN_STATE")},
            {it.getJSONArray("captures").getJSONObject(0).put("ledger_head_id","-1")},
            {it.getJSONArray("captures").getJSONObject(0).put("ledger_head_id","9223372036854775808")},
            {it.getJSONArray("captures").getJSONObject(0).put("ledger_head_id",JSONObject.NULL)},
            {it.getJSONArray("captures").getJSONObject(0).put("take_quanta",1)},
            {it.getJSONArray("captures").getJSONObject(0).put("take_quanta","-1")},
            {it.getJSONArray("captures").getJSONObject(0).put("take_quanta","9007199254740992")},
            {it.getJSONArray("captures").getJSONObject(0).put("take_quanta","1.5")},
            {it.getJSONArray("captures").getJSONObject(0).put("take_quanta","3")},
            {it.getJSONArray("captures").getJSONObject(0).put("used_before_quanta","-1")},
            {it.getJSONArray("captures").getJSONObject(0).put("requested_quanta","0")},
            {it.getJSONArray("captures").getJSONObject(0).put("requested_stock",JSONObject.NULL)},
            {it.getJSONArray("captures").getJSONObject(0).getJSONObject("requested_stock").put("value","0")},
            {it.getJSONArray("captures").getJSONObject(0).put("issue","")},
            {it.getJSONObject("request").getJSONObject("record").put("legacy_actual_dose_bits","7ff8000000000000")}
        )
        val rows=edits.map{change(base,it)}+listOf(
            JSONObject(base.toString()).put("request_sha256","0".repeat(64)),JSONObject(base.toString()).put("dataset_uuid",id(999)),
            JSONObject(base.toString()).put("record_uuid",id(999)),JSONObject(base.toString()).put("record_revision",2),
            JSONObject(base.toString()).put("kind","REVERSE"),JSONObject(base.toString()).put("input_json"," "+base.getString("input_json")),
            JSONObject(base.toString()).put("input_json",base.getString("input_json").replace("\"format\":2","\"format\":2.0")))
        for((index,row) in rows.withIndex()) {
            try{old(row);fail("Frozen oracle accepted malformed fixture $index")}catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
            try{current(row);fail("Candidate accepted malformed fixture $index")}catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
        }
        assertEquals(33,rows.size)
    }
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
            row.getString("dataset_uuid")!=request.datasetUuid || row.getString("record_uuid")!=request.record.uuid || row.getInt("record_revision")!=request.record.revision)invalid(ReferenceError.INVALID_SOURCE)
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
}
