package net.plainnotes.attachmentpoc

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class PortableCanonicalReviewTest {
    private fun resource(name:String)=requireNotNull(javaClass.getResourceAsStream("/portable-review/$name")).use {it.readBytes()}
    private val bytes get()=resource("canonical.bin")
    private fun reject(b:ByteArray) {try {PortableCanonicalReview.decode(b);fail("Noncanonical input accepted")}catch(_:Exception){}}
    private fun reject(s:String)=reject(s.toByteArray(Charsets.UTF_8))
    private fun tree(v:Any?):Any?=when(v) {
        JSONObject.NULL,null->null
        is JSONObject->v.keys().asSequence().associateWith {tree(v.get(it))}
        is JSONArray->(0 until v.length()).map {tree(v.get(it))}
        is Number->v.toString().toLong()
        else->v
    }
    @Test fun independentPythonReferenceMatchesAllDecodedFieldsAndUnsignedUuidOrder() {
        val decoded=PortableCanonicalReview.decode(bytes)
        // Independent stdlib JSON input, compared structurally after only mandated array ordering.
        val expected=JSONObject(String(resource("source.json"),Charsets.UTF_8))
        for(field in listOf("records","documents","links","portableEnvelopes")) {
            val a=expected.getJSONArray(field);val key=if(field=="portableEnvelopes")"version" else "uuid"
            expected.put(field,JSONArray((0 until a.length()).map {a.getJSONObject(it)}.sortedBy {it.getString(key)}))
        }
        val a=expected.getJSONArray("versions");expected.put("versions",JSONArray((0 until a.length()).map {a.getJSONObject(it)}.sortedWith(compareBy<JSONObject> {it.getString("document")}.thenBy {it.getLong("ordinal")})))
        assertEquals(tree(expected),decoded)
        val sha=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it.toInt() and 255)}
        assertEquals(String(resource("canonical.sha256"),Charsets.US_ASCII).trim(),sha)
        val root=generateSequence(Path.of(System.getProperty("user.dir"))) {it.parent}.first {Files.exists(it.resolve("tools/med02-format-review/canonical_reference.py"))}
        val process=ProcessBuilder("python3",root.resolve("tools/med02-format-review/canonical_reference.py").toString(),"--check").redirectErrorStream(true).start()
        try {assertTrue(process.waitFor(30,TimeUnit.SECONDS));assertEquals(process.inputStream.bufferedReader().readText(),0,process.exitValue())}finally {if(process.isAlive)process.destroyForcibly()}
    }
    @Test fun alternateWhitespaceKeysAndDuplicateIdentitiesAreRejected() {
        val s=String(bytes,Charsets.UTF_8)
        val wrongOrder="{"+s.removePrefix("{\"version\":1,").dropLast(1)+",\"version\":1}"
        for(v in listOf(" $s",s+"\n",wrongOrder,s.replaceFirst("\"version\":1,","\"version\":1,\"version\":1,"),
            s.replaceFirst("\"version\":1,","\"unknown\":1,"),s.replaceFirst("\"dataset\":","\"epoch\":"),
            s.replace("00000000-0000-0000-0000-000000000002","00000000-0000-0000-0000-000000000001")))reject(v)
    }
    @Test fun integersRemainExactAndAlternateRepresentationsOverflowAndCoercionsRefuse() {
        val s=String(bytes,Charsets.UTF_8);assertTrue(s.contains("9223372036854775807"));assertTrue(s.contains("-9223372036854775808"))
        for(v in listOf("1.0","1e0","01","+1","-0","true","\"1\""))reject(s.replaceFirst("\"version\":1,","\"version\":$v,"))
        reject(s.replace("9223372036854775807","9223372036854775808"));reject(s.replace("-9223372036854775808","-9223372036854775809"))
    }
    @Test fun utf8AndStringEscapesAreStrictAndNoUnicodeNormalizationIsApplied() {
        val s=String(bytes,Charsets.UTF_8);assertTrue(s.contains("e\u0301"));assertTrue(s.contains("🔬"));assertTrue(s.contains("\\u000a"))
        for(e in listOf("\\n","\\u000A","\n","\\ud800"))reject(s.replace("\\u000a",e))
        reject(s.replace("É","\\u00c9"));reject(s.replace("application/pdf","application\\/pdf"))
        reject(byteArrayOf(0xef.toByte(),0xbb.toByte(),0xbf.toByte())+bytes)
        reject(bytes+byteArrayOf(0xc0.toByte(),0xaf.toByte()))
    }
    @Test fun fieldTypesBoundedEnvelopeClosureAndInputLengthRefuse() {
        val s=String(bytes,Charsets.UTF_8)
        reject(s.replace("\"wrappedDek\":\"01","\"wrappedDek\":\"g1"))
        reject(s.replace("\"title\":","\"extra\":null,\"title\":"))
        reject(s.replace("\"sourceId\":9223372036854775807","\"sourceId\":null"))
        reject(s.replace("\"mime\":\"image/png\"","\"mime\":\"unknown/type\""))
        reject(s.replaceFirst("\"portableEnvelopes\":[", "\"portableEnvelopes\":[null,"))
        reject(ByteArray(PortableCanonicalReview.MAX_BYTES+1))
    }
}
