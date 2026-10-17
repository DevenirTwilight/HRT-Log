package net.plainnotes.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Frozen pre-optimization encoder is an independent byte oracle, never used by production. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class ReferenceCanonicalCompatibilityTest {
    private object Before {
        const val MAX_TEXT=2048
        const val MAX_BYTES=65536
    /** Fixed JSON escaping and scalar Unicode validation; independent of Android's JSONObject writer version. */
    private fun quote(text: String): String = buildString {
        append('"')
        var index=0
        while(index<text.length) {
            val c=text[index++]
            require(c!='\u0000')
            when {
                Character.isHighSurrogate(c) -> {
                    require(index<text.length && Character.isLowSurrogate(text[index]))
                    append(c);append(text[index++])
                }
                Character.isLowSurrogate(c) -> error("Unpaired Unicode surrogate")
                c=='"' -> append("\\\"")
                c=='\\' -> append("\\\\")
                c=='\b' -> append("\\b")
                c=='\t' -> append("\\t")
                c=='\n' -> append("\\n")
                c=='\u000c' -> append("\\f")
                c=='\r' -> append("\\r")
                c.code<32 -> append("\\u"+c.code.toString(16).padStart(4,'0'))
                else -> append(c)
            }
        }
        append('"')
    }
    fun canonical(json: JSONObject): String {
        fun encode(value: Any, depth: Int): String {
            require(depth <= 16) { "Reference nesting limit" }
            return when (value) {
                JSONObject.NULL -> "null"
                is JSONObject -> {
                    require(value.length() <= 64)
                    value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { key ->
                        require(key.length <= 64)
                        quote(key) + ":" + encode(value.get(key), depth + 1)
                    }
                }
                is JSONArray -> {
                    require(value.length() <= 128)
                    (0 until value.length()).joinToString(",", "[", "]") { encode(value.get(it), depth + 1) }
                }
                is String -> { require(value.length <= MAX_TEXT); quote(value) }
                is Int, is Long, is Boolean -> value.toString()
                else -> error("Exact reference values must use decimal strings, not floating point")
            }
        }
        return encode(json, 0).also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) }
    }
    }
    private fun compare(json:JSONObject) {
        val expected=Before.canonical(json)
        val actual=ReferencePayload.canonical(json)
        assertArrayEquals(expected.toByteArray(Charsets.UTF_8),actual.toByteArray(Charsets.UTF_8))
        assertEquals(ReferencePayload.sha(expected),ReferencePayload.sha(actual))
    }
    @Test fun everyValidBmpScalarAndAstralPairsPreserveBytesAndDigests() {
        val bmp=(1..65535).filter{it !in 0xD800..0xDFFF}.map{it.toChar()}.joinToString("")
        bmp.chunked(1024).forEach{compare(JSONObject().put("scalar",it))}
        val astral=buildString {for(point in 0x10000..0x10FFFF step 997)append(String(Character.toChars(point)))}
        astral.chunked(1000).forEach{compare(JSONObject().put("astral",it))}
    }
    @Test fun deterministicNestedValuesAndEscapingMatchFrozenEncoder() {
        val random=java.util.Random(20261017L)
        val alphabet=listOf("é","片","µ","œ","\"","\\","\n","\b","\t","\r","\u000c","\u0001","😀","\u2028","\u202e","e\u0301","/")
        repeat(250) {
            val value=(0 until random.nextInt(1000)).joinToString(""){alphabet[random.nextInt(alphabet.size)]}
            compare(JSONObject().put("z",JSONArray().put(value).put(Long.MIN_VALUE).put(Long.MAX_VALUE).put(JSONObject.NULL))
                .put("a",JSONObject().put("é\\\"",true).put("false",false).put("int",Int.MIN_VALUE)))
        }
    }
    @Test fun invalidScalarTypesAndAllBoundsStillReject() {
        val invalid=listOf(JSONObject().put("x","\u0000"),JSONObject().put("x","\ud800"),JSONObject().put("x","\udc00"),
            JSONObject().put("\ud800","x"),JSONObject().put("x",1.25),JSONObject().put("x","a".repeat(2049)),
            JSONObject().put("k".repeat(65),1),JSONObject().put("x",JSONArray(List(129){1})),
            JSONObject().apply{repeat(65){put("key$it",1)}},
            JSONObject().apply{repeat(64){put("k$it","片".repeat(1024))}})
        for(json in invalid) {
            assertTrue(runCatching{Before.canonical(json)}.isFailure)
            assertTrue(runCatching{ReferencePayload.canonical(json)}.isFailure)
        }
        var json=JSONObject().put("end",1);repeat(17){json=JSONObject().put("child",json)}
        assertTrue(runCatching{Before.canonical(json)}.isFailure)
        assertTrue(runCatching{ReferencePayload.canonical(json)}.isFailure)
        compare(JSONObject().put("limit","a".repeat(2048)))
        compare(JSONObject().put("array",JSONArray(List(128){it})))
    }
}
