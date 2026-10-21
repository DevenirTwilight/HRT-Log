package net.plainnotes.app.data

import android.database.MatrixCursor
import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*

/** Shared JVM/Android parser boundary corpus. The single-row adapter isolates the real chain
 * reader from storage; it does not claim SQL/transaction or full-history correctness. */
internal object CanonicalOperationBudgetOracle {
    private const val OPERATION="00000000-0000-4000-8000-000000000001"
    private const val OTHER="00000000-0000-4000-8000-000000000002"
    private fun root(value:Any=JSONObject()):JSONObject = JSONObject().put("format",2)
        .put("chain",JSONObject().put("sequence","1").put("previous_operation_uuid",JSONObject.NULL)
            .put("previous_payload_sha256",JSONObject.NULL)).put("payload",value)
    private fun database(text:String):SupportSQLiteDatabase = Proxy.newProxyInstance(
        SupportSQLiteDatabase::class.java.classLoader,arrayOf(SupportSQLiteDatabase::class.java),
    ) { _,method,args -> when(method.name) {
        "inTransaction"->true
        "query"->{
            require(args?.size==1 && args[0]=="SELECT operation_uuid,input_json FROM measured_operation")
            MatrixCursor(arrayOf("operation_uuid","input_json"),1).apply{addRow(arrayOf(OPERATION,text))}
        }
        else->error("Unexpected parser-oracle database call: ${method.name}")
    }} as SupportSQLiteDatabase
    private fun accepted(text:String) {
        // row budget zero deliberately leaves hasCompleteRows=false: canonical proof retention
        // is independent of the optional primitive-header cache and must still enforce all gates.
        MeasuredOperationChain.AuditProof.capture(database(text),0).use { proof ->
            assertFalse(proof.hasCompleteRows)
            assertTrue(proof.matches(OPERATION,text))
            assertTrue(proof.matches(OPERATION,String(text.toCharArray())))
            assertFalse(proof.matches(OTHER,text))
            assertFalse(proof.matches(OPERATION,text+" "))
            assertTrue(text.toByteArray(Charsets.UTF_8).size<=ReferencePayload.MAX_BYTES)
            assertTrue(text.length<=BackupLimits.MAX_JSON_BYTES)
            // Independent existing lexical gate: do not rewrite it to mirror canonical encoding.
            BackupLimits.checkJson(text)
        }
        val proof=MeasuredOperationChain.AuditProof.capture(database(text),0)
        proof.close()
        try{proof.matches(OPERATION,text);fail("Released proof remained usable")}catch(_:IllegalStateException){}
    }
    private fun rejected(text:String) {
        var failure:Exception?=null
        try{MeasuredOperationChain.AuditProof.capture(database(text),0).close()}catch(error:Exception){failure=error}
        assertNotNull("Untrusted text obtained a canonical chain proof",failure)
    }
    private fun rejectedValue(value:Any) {
        var failure:Exception?=null
        try{ReferencePayload.canonical(root(value))}catch(error:Exception){failure=error}
        assertNotNull("Canonical value boundary widened",failure)
    }
    private fun depth(valueDepth:Int):Any {
        var value:Any="leaf"
        repeat(valueDepth-1){index->value=if(index%2==0)JSONObject().put("node",value) else JSONArray().put(value)}
        return value
    }
    fun check() {
        for(value in listOf("","x".repeat(2048),"quotes: \" and slash: \\ and /",
            "\b\t\n\u000c\r\u0001\u001f","BMP 雌二醇 é µg","pair \uD83E\uDDEA","é".repeat(2048),("\uD83E\uDDEA").repeat(1024))) {
            accepted(ReferencePayload.canonical(root(value)))
        }
        accepted(ReferencePayload.canonical(root(depth(16))))
        rejectedValue(depth(17))
        // An empty container itself may be at depth16:17 nested containers including root,
        // still strictly below the independent backup lexical limit32.
        var empty:Any=JSONObject()
        repeat(15){empty=JSONArray().put(empty)}
        accepted(ReferencePayload.canonical(root(empty)))
        rejectedValue(JSONArray().put(empty))
        rejectedValue("x".repeat(2049))
        rejectedValue("NUL\u0000")
        rejectedValue("high\uD83E")
        rejectedValue("low\uDDEA")
        rejectedValue("reversed\uDDEA\uD83E")
        accepted(ReferencePayload.canonical(root(JSONArray().apply{repeat(128){put("x")}})))
        rejectedValue(JSONArray().apply{repeat(129){put("x")}})
        accepted(ReferencePayload.canonical(root(JSONObject().apply{repeat(64){put("k$it","x")}})))
        rejectedValue(JSONObject().apply{repeat(65){put("k$it","x")}})

        val fields=JSONObject().apply{repeat(31){put("p"+it.toString().padStart(2,'0'),"x".repeat(2048))}}
        val whole=root(fields)
        fields.put("p31","")
        val missing=ReferencePayload.MAX_BYTES-ReferencePayload.canonical(whole).toByteArray(Charsets.UTF_8).size
        assertTrue(missing in 1..2048)
        fields.put("p31","x".repeat(missing))
        val limit=ReferencePayload.canonical(whole)
        assertEquals(ReferencePayload.MAX_BYTES,limit.toByteArray(Charsets.UTF_8).size)
        accepted(limit)
        // Valid JSON just over the reference-byte fence still passes the broad backup lexical
        // budget; canonical chain validation must reject it before any matches() certificate.
        val tooLarge=limit.replace("\"p31\":\"","\"p31\":\"x")
        assertEquals(ReferencePayload.MAX_BYTES+1,tooLarge.toByteArray(Charsets.UTF_8).size)
        BackupLimits.checkJson(tooLarge)
        rejected(tooLarge)

        val canonical=ReferencePayload.canonical(root("plain / quote \" slash \\"))
        rejected(" "+canonical)
        rejected(canonical+" ")
        rejected(canonical.replace("plain /","plain \\/"))
        rejected(canonical.replace("plain /","\\u0070lain /"))
        rejected(canonical.replace("\"format\":2","\"format\":2,\"format\":2"))
        rejected(canonical.dropLast(1))
        rejected(canonical.replace("\"sequence\":\"1\"","\"sequence\":\"01\""))
        rejected(ReferencePayload.canonical(root("sentinel")).replace("sentinel","\\u0000"))
        // Raw/escaped malformed surrogate spellings must both fail canonical serialization.
        rejected(root("high\uD83E").toString())
        rejected(root("low\uDDEA").toString())
        rejected(ReferencePayload.canonical(root("sentinel")).replace("sentinel","\\ud83e"))
        rejected(ReferencePayload.canonical(root("sentinel")).replace("sentinel","\\uddea"))
    }
}
