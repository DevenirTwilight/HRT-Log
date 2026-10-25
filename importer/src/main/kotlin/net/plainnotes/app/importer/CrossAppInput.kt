package net.plainnotes.app.importer

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Content-only recognition. A signature is not permission to write: the source parser must
 * still validate its complete schema/version. Limits apply before object-tree construction. */
object CrossAppInput {
    const val MAX_JSON_BYTES = 32 * 1024 * 1024
    const val MAX_SQLITE_BYTES = 64 * 1024 * 1024
    const val MAX_DEPTH = 48
    const val MAX_STRING_CHARS = 65536
    const val MAX_ITEMS = 100000
    class Rejected : Exception()
    enum class Kind { SQLITE, JSON, UNSUPPORTED }
    enum class Source { HRT_TRACKER, TRANS_MEMO }
    data class Identification(val source: Source, val version: String)

    fun kind(prefix: ByteArray): Kind = when {
        prefix.size >= 16 && prefix.copyOfRange(0,16).contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)) -> Kind.SQLITE
        prefix.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t').firstOrNull() in listOf('{','[') -> Kind.JSON
        else -> Kind.UNSUPPORTED
    }

    fun bounded(input: InputStream, maximum: Int, check: () -> Unit = {}): InputStream = object : FilterInputStream(input) {
        private var count = 0L
        private fun add(n: Int) { check(); if (n > 0) count += n; if(count > maximum) throw Rejected() }
        override fun read(): Int { check(); return `in`.read().also { add(if(it < 0) 0 else 1) } }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            check(); return `in`.read(b,off,minOf(len, (maximum-count+1).coerceAtLeast(1).toInt())).also(::add)
        }
        override fun skip(n: Long): Long = throw Rejected() // no unaccounted traversal
    }

    fun readJson(input: InputStream, check: () -> Unit = {}): String {
        val bytes=object:ByteArrayOutputStream() { override fun reset() { buf.fill(0);super.reset() } }; val buffer=ByteArray(16384)
        try {
            val limited=bounded(input,MAX_JSON_BYTES,check)
            while(true) {
                check(); val n=limited.read(buffer)
                if(n<0)break
                if(n==0) { val one=limited.read();if(one<0)break;bytes.write(one) } else bytes.write(buffer,0,n)
            }
            val copy=bytes.toByteArray()
            try {
                val decoder=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                return decoder.decode(ByteBuffer.wrap(copy)).toString().removePrefix("\uFEFF").also(::validateJson)
            } finally { copy.fill(0) }
        } finally { buffer.fill(0);bytes.reset() }
    }

    /** This is a resource guard, not a replacement JSON grammar or a permissive format parser. */
    fun validateJson(text: String) {
        if(text.length>MAX_JSON_BYTES)throw Rejected()
        val stack=ArrayDeque<Char>();var quoted=false;var escaped=false;var stringLength=0;var containers=0;var tokens=0
        for(c in text) {
            if(quoted) {
                if(++stringLength>MAX_STRING_CHARS)throw Rejected()
                if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false
            } else {
                if(c in listOf('"',',',':') && ++tokens>1_000_000)throw Rejected()
                when(c) {
                '"' -> {quoted=true;stringLength=0}
                '{','[' -> {stack.addLast(c);if(stack.size>MAX_DEPTH || ++containers>MAX_ITEMS)throw Rejected()}
                '}',']' -> if(stack.removeLastOrNull() != if(c=='}')'{' else '[')throw Rejected()
                '\u0000' -> throw Rejected()
            }
        }
        }
        if(quoted || stack.isNotEmpty())throw Rejected()
    }

    fun tracker(text: String): Pair<Identification,HrtTracker.Export> {
        validateJson(text)
        val root=if(text.trimStart().startsWith("["))JSONArray(text) else JSONObject(text)
        val obj=root as? JSONObject
        // An array/empty events field alone is not a unique application fingerprint.
        val events=obj?.optJSONArray("events") ?: root as? JSONArray ?: throw Rejected()
        if(obj!=null && (obj.has("metadata") || obj.has("data") || obj.has("medicationLogs")))throw Rejected()
        if(events.length()>MAX_ITEMS)throw Rejected()
        val version=obj?.optJSONObject("meta")?.get("version")
        if(events.length()==0 && version !is Number)throw Rejected()
        for(i in 0 until events.length()) {
            val event=events.optJSONObject(i) ?: throw Rejected()
            if(listOf("timeH","doseMG","route","ester").any { !event.has(it) })throw Rejected()
            if(event.opt("timeH") !is Number || event.opt("doseMG") !is Number || event.opt("route") !is String || event.opt("ester") !is String)throw Rejected()
        }
        val export=HrtTracker.read(text) // original version, route/unit and clinical mapping rules
        return Identification(Source.HRT_TRACKER,version?.toString() ?: "legacy") to export
    }
}
