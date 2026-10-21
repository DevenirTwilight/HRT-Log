package net.plainnotes.app.data

import android.database.MatrixCursor
import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import org.json.JSONObject
import org.junit.Assert.*

/** Exercise the private pure memo and every row header against the independently frozen decoder.
 * Reflection exposes no production testing hooks or cross-transaction state. Storage/full-history
 * equivalence is covered separately by the complete frozen-audit differential contract. */
internal object ConsumptionMemoOracle {
    private val presentationClass=MeasuredConsumptionStore::class.java.declaredClasses.single{it.simpleName=="PresentationAudit"}
    private val constructor=presentationClass.getDeclaredConstructor(java.lang.Long.TYPE).apply{isAccessible=true}
    private val consume=MeasuredConsumptionStore::class.java.getDeclaredMethod("consumption",JSONObject::class.java,MeasuredOperationChain.AuditProof::class.java,presentationClass).apply{isAccessible=true}
    private val fixture=ConsumptionReceiptOracle::class.java.getDeclaredMethod("fixture",MeasuredStockEffect::class.java,java.lang.Integer.TYPE,java.lang.Integer.TYPE).apply{isAccessible=true}
    private val frozen=ConsumptionReceiptOracle::class.java.getDeclaredMethod("old",JSONObject::class.java).apply{isAccessible=true}
    private fun old(row:JSONObject):MeasuredConsumptionReceipt=invoke {frozen.invoke(ConsumptionReceiptOracle,row) as MeasuredConsumptionReceipt}
    private inline fun <T> invoke(block:()->T):T = try{block()}catch(e:InvocationTargetException){throw e.targetException}
    private fun row(effect:MeasuredStockEffect=MeasuredStockEffect.DEDUCT_CURRENT,lines:Int=1,take:Int=2)=fixture.invoke(ConsumptionReceiptOracle,effect,lines,take) as JSONObject
    private fun proof(row:JSONObject):MeasuredOperationChain.AuditProof {
        val db=Proxy.newProxyInstance(SupportSQLiteDatabase::class.java.classLoader,arrayOf(SupportSQLiteDatabase::class.java)) { _,method,args -> when(method.name) {
            "inTransaction"->true
            "query"->{
                require(args?.size==1 && args[0]=="SELECT operation_uuid,input_json FROM measured_operation")
                MatrixCursor(arrayOf("operation_uuid","input_json"),1).apply{addRow(arrayOf(row.getString("operation_uuid"),row.getString("input_json")))}
            }
            else->error("Unexpected pure-memo database call ${method.name}")
        }} as SupportSQLiteDatabase
        return MeasuredOperationChain.AuditProof.capture(db,0)
    }
    private fun current(row:JSONObject,proof:MeasuredOperationChain.AuditProof,presentation:Any):MeasuredConsumptionReceipt=invoke {
        val decoded=consume.invoke(MeasuredConsumptionStore,row,proof,presentation)
        decoded.javaClass.getDeclaredMethod("getReceipt").apply{isAccessible=true}.invoke(decoded) as MeasuredConsumptionReceipt
    }
    private fun values(presentation:Any)=presentationClass.getDeclaredField("decodedConsumption").apply{isAccessible=true}.get(presentation) as Map<*,*>
    private fun bytes(presentation:Any)=presentationClass.getDeclaredField("consumptionBytes").apply{isAccessible=true}.getLong(presentation)
    private fun close(presentation:Any){(presentation as AutoCloseable).close()}
    private fun changed(base:JSONObject,change:(JSONObject)->Unit):JSONObject {
        val envelope=JSONObject(base.getString("input_json"));change(envelope)
        return JSONObject(base.toString()).put("input_json",ReferencePayload.canonical(envelope))
            .put("request_sha256",ReferencePayload.sha(ReferencePayload.canonical(envelope.getJSONObject("request"))))
    }
    private fun invalid(block:()->Unit) {
        try{block();fail("Malformed row survived pure memo")}catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
    }
    fun retainedCostSummary():Map<String,Long> {
        val plain=row()
        val nativeLike=changed(plain) {envelope->
            val identity="SYNTHETIC compound-19 ester/hydrate not equivalent 有效成分 非等效 成分 non équivalent "+"x".repeat(80)
            val request=envelope.getJSONObject("request")
            request.getJSONArray("uses").getJSONObject(0).getJSONObject("quantity").getJSONObject("meaning").put("identity",identity)
            envelope.getJSONArray("captures").getJSONObject(0).getJSONObject("requested_stock").getJSONObject("meaning").put("identity",identity)
            envelope.getJSONObject("chain").put("sequence","2000").put("previous_operation_uuid","00000000-0000-4000-8000-000000000999").put("previous_payload_sha256","b".repeat(64))
        }
        fun cost(row:JSONObject):Long {
            val memo=constructor.newInstance(8L*1024*1024)
            try {
                val text=row.getString("input_json")
                val decoded=invoke{presentationClass.getDeclaredMethod("consumption",String::class.java).apply{isAccessible=true}.invoke(memo,text)}
                return decoded.javaClass.getDeclaredMethod("retainedBytes",String::class.java).apply{isAccessible=true}.invoke(decoded,text) as Long
            } finally {close(memo)}
        }
        return linkedMapOf("short_single_use_estimate" to cost(plain),"native_like_single_use_estimate" to cost(nativeLike),
            "native_like_input_utf16_chars" to nativeLike.getString("input_json").length.toLong(),"native_like_2000_estimate" to cost(nativeLike)*2000L)
    }
    fun check() {
        println("CONSUMPTION_MEMO_SYNTHETIC_RETAINED "+retainedCostSummary())
        for(budget in listOf(0L,1L,8L*1024*1024,16L*1024*1024)) {
            val memo=constructor.newInstance(budget)
            try {
                for(effect in MeasuredStockEffect.entries)for(lines in listOf(0,1,2,4))for(take in 0..2) {
                    val value=row(effect,lines,take)
                    proof(value).use { p ->repeat(2){assertEquals(old(value),current(value,p,memo))} }
                }
                if(budget<=1)assertTrue(values(memo).isEmpty()) else assertTrue(values(memo).isNotEmpty())
                assertTrue(bytes(memo) in 0..budget)
                // Exact full text must select separate immutable results even with the same UUID.
                val first=row(lines=1);val second=row(lines=2)
                proof(first).use{p->assertEquals(old(first),current(first,p,memo))}
                proof(second).use{p->assertEquals(old(second),current(second,p,memo))}
                proof(first).use { p ->
                    assertEquals(old(first),current(first,p,memo))
                    // Every dependency is checked again after a successful memo hit.
                    for(edit in listOf<(JSONObject)->Unit>(
                        {it.put("kind","REVERSE")},{it.put("request_sha256","0".repeat(64))},
                        {it.put("dataset_uuid","00000000-0000-4000-8000-000000009999")},
                        {it.put("record_uuid","00000000-0000-4000-8000-000000009999")},
                        {it.put("record_revision",2)},{it.put("operation_uuid","00000000-0000-4000-8000-000000009999")},
                        {it.put("input_json",second.getString("input_json"))},
                    ))invalid{current(JSONObject(first.toString()).apply(edit),p,memo)}
                    val issue=JSONObject(first.toString()).put("unknown_reason","SYNTHETIC changed row field")
                    assertEquals(old(issue),current(issue,p,memo))
                    assertEquals("SYNTHETIC changed row field",current(issue,p,memo).issue)
                    assertEquals(old(first),current(first,p,memo))
                }
                val malformed=listOf<(JSONObject)->Unit>(
                    {it.getJSONArray("captures").getJSONObject(0).put("take_quanta","01")},
                    {it.getJSONArray("captures").getJSONObject(0).put("used_before_quanta",1)},
                    {it.getJSONArray("captures").getJSONObject(0).put("ledger_head_id",JSONObject.NULL)},
                    {it.getJSONArray("captures").getJSONObject(0).put("stock_state",JSONObject.NULL)},
                    {it.getJSONArray("captures").getJSONObject(0).put("requested_stock",JSONObject.NULL)},
                    {it.getJSONArray("captures").getJSONObject(0).put("snapshot_uuid","bad")},
                    {it.getJSONArray("captures").getJSONObject(0).put("issue","")},
                    {it.getJSONArray("captures").getJSONObject(0).put("extra",true)},
                )
                for(edit in malformed) {
                    val value=changed(first,edit)
                    invalid{old(value)}
                    proof(value).use{p->invalid{current(value,p,memo)}}
                }
                if(budget>1) {
                    val value=values(memo).values.first()!!
                    assertFalse("No mutable JSONObject retained",value.javaClass.declaredFields.any{it.type==JSONObject::class.java})
                    for(name in listOf("captures","lines")) {
                        val list=value.javaClass.getDeclaredMethod("get"+name.replaceFirstChar{it.uppercaseChar()}).apply{isAccessible=true}.invoke(value) as MutableList<*>
                        try{list.clear();fail("Memo list escaped mutably")}catch(_:UnsupportedOperationException){}
                    }
                }
            } finally {close(memo)}
            assertTrue(values(memo).isEmpty());assertEquals(0L,bytes(memo))
            val decoder=presentationClass.getDeclaredMethod("consumption",String::class.java).apply{isAccessible=true}
            try{invoke{decoder.invoke(memo,row().getString("input_json"))};fail("Closed memo reused")}catch(_:IllegalStateException){}
        }
    }
}
