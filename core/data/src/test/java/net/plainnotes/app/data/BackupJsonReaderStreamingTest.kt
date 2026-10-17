package net.plainnotes.app.data

import java.io.StringReader
import java.math.BigDecimal
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class BackupJsonReaderStreamingTest {
    private fun bad(bytes:ByteArray) {
        try {BackupJsonReader.read(bytes);fail("Untrusted input must be rejected")}
        catch(_:BackupCodec.BadFile){}
    }
    @Test fun utf8BufferBoundariesAndNumericLexemesRemainExact() {
        val text="{\"text\":\""+"多語é😀".repeat(4097)+"\",\"integer\":9007199254740991,\"decimal\":0.123456789012345678901,\"negativeZero\":-0}"
        val bytes=BackupJsonReader.read(text.toByteArray());val original=BackupJsonReader.read(text)
        assertEquals(original.getString("text"),bytes.getString("text"))
        assertEquals(9007199254740991L,bytes.getLong("integer"))
        assertEquals(BigDecimal("0.123456789012345678901"),bytes.get("decimal"))
        assertEquals(BigDecimal("-0"),bytes.get("negativeZero"))
        assertEquals(original.length(),bytes.length())
        original.keys().asSequence().forEach {key->
            assertEquals(original.get(key),bytes.get(key))
            assertEquals(original.get(key)::class.java,bytes.get(key)::class.java)
        }
    }
    @Test fun malformedUtf8IsRejectedEvenAfterACompleteRootAndAcrossTheDecoderBuffer() {
        bad("{}".toByteArray()+byteArrayOf(0xc3.toByte()))
        bad(("{\"x\":\""+"a".repeat(8191)).toByteArray()+byteArrayOf(0xed.toByte(),0xa0.toByte(),0x80.toByte())+"\"}".toByteArray())
        bad(byteArrayOf(0xef.toByte(),0xbb.toByte()))
    }
    @Test fun duplicateKeysTrailingDocumentsAndNonObjectRootsStillFail() {
        listOf("{\"n\":1,\"n\":2}","{} {}","[]","null","{\"x\":NaN}","{\"x\":Infinity}","{\"x\":1,}").forEach{bad(it.toByteArray())}
    }
    @Test fun lexicalStateSurvivesReaderChunksIncludingEscapesAndNestedSnapshotText() {
        val valid=JSONObject().put("nested",("quoted \\\" {[}] "+"a".repeat(8189)).repeat(2)).toString()
        for(chunk in listOf(1,7,8192)) {
            val reader=object:StringReader(valid){override fun read(buffer:CharArray,offset:Int,length:Int)=super.read(buffer,offset,minOf(length,chunk))}
            reader.use{BackupLimits.checkJson(it)}
        }
        assertEquals(JSONObject(valid).getString("nested"),BackupJsonReader.read(valid.toByteArray()).getString("nested"))
    }
    @Test fun originalLexicalStringAndDepthBudgetsAreNotRelaxed() {
        val allowed="{\"x\":\""+"a".repeat(1024*1024-1)+"\"}"
        BackupLimits.checkJson(allowed);BackupLimits.checkJson(StringReader(allowed))
        val tooLong="{\"x\":\""+"a".repeat(1024*1024)+"\"}"
        for(test in listOf<()->Unit>({BackupLimits.checkJson(tooLong)},{BackupLimits.checkJson(StringReader(tooLong))},{BackupJsonReader.read(tooLong.toByteArray())},
            {BackupJsonReader.read(("[".repeat(33)+"]".repeat(33)).toByteArray())})) {
            try {test();fail("The original lexical budget must reject this input")}catch(_:BackupCodec.TooLarge){}
        }
    }
}
