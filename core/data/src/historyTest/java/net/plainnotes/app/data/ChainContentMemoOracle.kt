package net.plainnotes.app.data

import android.database.MatrixCursor
import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.*

/** The frozen validator independently decides current-chain validity. The candidate codec may
 * retain only exact immutable text→Link/SHA results, never row identities or database facts. */
internal object ChainContentMemoOracle {
    private fun id(n:Int)="00000000-0000-4000-8000-"+n.toString().padStart(12,'0')
    private fun chain(seed:Int=1):List<Pair<String,String>> {
        val rows=ArrayList<Pair<String,String>>()
        repeat(3) {i->
            val previous=rows.lastOrNull()
            val text=ReferencePayload.canonical(JSONObject().put("format",2).put("payload","SYNTHETIC content $seed/$i")
                .put("chain",JSONObject().put("sequence",(i+1).toString()).put("previous_operation_uuid",previous?.first ?: JSONObject.NULL)
                    .put("previous_payload_sha256",previous?.let{ReferencePayload.sha(it.second)} ?: JSONObject.NULL)))
            rows+=id(seed+i) to text
        }
        return rows
    }
    private fun db(rows:List<Pair<String,String>>):SupportSQLiteDatabase = Proxy.newProxyInstance(
        SupportSQLiteDatabase::class.java.classLoader,arrayOf(SupportSQLiteDatabase::class.java),
    ) { _,method,args->when(method.name) {
        "inTransaction"->true
        "query"->{require(args?.size==1 && args[0]=="SELECT operation_uuid,input_json FROM measured_operation")
            MatrixCursor(arrayOf("operation_uuid","input_json"),rows.size).apply{rows.forEach{addRow(arrayOf(it.first,it.second))}}}
        else->error("Unexpected content-memo database call ${method.name}")
    }} as SupportSQLiteDatabase
    private fun result(rows:List<Pair<String,String>>,old:Boolean):List<Triple<String,Int,String>>?=try {
        (if(old)FrozenFullAuditChain.validate(db(rows)) else MeasuredOperationChain.validate(db(rows))).values.map{Triple(it.operationUuid,it.sequence,it.payloadSha256)}
    }catch(_:Exception){null}
    private fun compare(rows:List<Pair<String,String>>) {
        assertEquals("Exact current rows must determine the result",result(rows,true),result(rows,false))
    }
    private val owner=MeasuredOperationChain
    private val type=MeasuredOperationChain::class.java
    private val lock=type.getDeclaredField("checkedInputLock").apply{isAccessible=true}.get(owner)
    private val entries=type.getDeclaredField("checkedInputs").apply{isAccessible=true}
    private val used=type.getDeclaredField("checkedInputBytes").apply{isAccessible=true}
    private val budget=type.getDeclaredField("checkedInputByteBudget").apply{isAccessible=true}
    @Suppress("UNCHECKED_CAST") private fun contents()=entries.get(owner) as MutableMap<String,Any>
    private fun reset(bytes:Long)=synchronized(lock) {contents().clear();used.setLong(owner,0);budget.setLong(owner,bytes)}
    fun check() {
        val originalBudget=synchronized(lock){budget.getLong(owner)}
        try {
            val a=chain();val b=chain(101)
            for(bytes in listOf(0L,1L,2048L,16L*1024*1024)) {
                reset(bytes)
                repeat(2){compare(a);compare(b)}
                compare(a.reversed()) // Query order is not execution sequence.
                compare(emptyList());compare(a.dropLast(1))
                compare(listOf(a[0],a[2]));compare(a.drop(1)) // Gaps are still rejected after warm hits.
                compare(listOf(a[0],id(999) to a[1].second,a[2])) // Warm content cannot cache its row UUID.
                compare(listOf(id(998) to a[0].second,a[1],a[2]))
                compare(listOf(a[0],a[1],id(997) to a[2].second)) // A different final UUID is valid, with a fresh Node.
                compare(a+listOf(id(996) to a[2].second)) // Warm canonical text cannot authorize a duplicate sequence.
                compare(a+listOf(a[0]))
                compare(listOf(a[0].first to (a[0].second+" "))+a.drop(1))
                compare(listOf(a[0].first to a[0].second.replace("SYNTHETIC","ALTERED"))+a.drop(1))
                compare(listOf(a[0],a[1].first to a[1].second.replace("\"sequence\":\"2\"","\"sequence\":\"02\""),a[2]))
                compare(a) // Failed current-chain validation cannot poison later valid uses.
                synchronized(lock) {
                    assertTrue(used.getLong(owner) in 0..bytes)
                    if(bytes<=1)assertTrue(contents().isEmpty())
                    for(value in contents().values) {
                        assertFalse(value.javaClass.declaredFields.any{it.type==JSONObject::class.java || it.type==MeasuredOperationChain.Node::class.java})
                    }
                }
            }
            reset(16L*1024*1024)
            val pool=Executors.newFixedThreadPool(4)
            try {
                val cases=listOf(a,b,a.reversed(),b.reversed(),listOf(a[0],a[2]),listOf(b[0],id(999) to b[1].second,b[2]))
                val jobs=(0 until 48).map{n->Callable{compare(cases[n%cases.size]);true}}
                assertTrue(pool.invokeAll(jobs).all{it.get()})
            } finally {pool.shutdown();assertTrue(pool.awaitTermination(30,TimeUnit.SECONDS))}
            synchronized(lock){assertTrue(used.getLong(owner)<=16L*1024*1024);assertTrue(contents().isNotEmpty())}
            // An empty access instance closes without loading keys or opening a database, but must
            // still release the process codec's plaintext references through the production lifecycle.
            DatabaseAccess(androidx.test.core.app.ApplicationProvider.getApplicationContext()).close()
            synchronized(lock){assertTrue(contents().isEmpty());assertEquals(0L,used.getLong(owner))}
            compare(a)
            MeasuredOperationChain.clearPureInputMemo()
            synchronized(lock){assertTrue(contents().isEmpty());assertEquals(0L,used.getLong(owner))}
            // Reflection only resets this pure optimization, without changing or establishing DB trust.
            reset(0);compare(a)
        } finally {reset(originalBudget)}
    }
}
