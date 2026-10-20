package net.plainnotes.app.data

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class IndexedBackupDocumentTest {
    @Test fun indexedRowsAreExactlyTheOriginalStrictParserValuesWithNoRetainedRowTree() {
        val document="""{"schema":12,"other":{"nested":[1,2]},"format":1,"ta\u0062les":{"do\u0073e_record":[{"id":9007199254740991,"text":"é多語😀 {}[]","a":-0,"b":0.123456789012345678901},{"id":2,"text":"escaped backslash \\"}],"medication":[]}}"""
        val bytes=document.toByteArray();val oracle=FrozenRestoreJsonReader.read(bytes)
        BackupJsonReader.indexed(bytes).use { indexed ->
            assertEquals(RecordBackupCompatibility.readVersion(oracle),RecordBackupCompatibility.readVersion(indexed.metadata))
            assertEquals(setOf("dose_record","medication"),indexed.tables.keys())
            for(table in indexed.tables.keys()) {
                val expected=oracle.getJSONObject("tables").getJSONArray(table);val rows=indexed.tables.getJSONArray(table)
                assertEquals(expected.length(),rows.length())
                for(i in 0 until rows.length()) {
                    val actual=rows.getJSONObject(i);val original=expected.getJSONObject(i)
                    assertEquals(original.toString(),actual.toString())
                    for(key in original.keys().asSequence())assertEquals(original.get(key)::class.java,actual.get(key)::class.java)
                    assertNotSame(actual,rows.getJSONObject(i))
                }
                assertEquals(0,indexed.metadata.getJSONObject("tables").getJSONArray(table).length())
            }
        }
        assertTrue(bytes.all{it==0.toByte()})
    }
    @Test fun bomEscapedPayloadAndReorderedTablesMatchTheFrozenOracle() {
        val text=JSONObject().put("tables",JSONObject().put("day_note",JSONArray().put(JSONObject().put("text","é多語😀 \" {}[] \\")))).put("format",1).put("schema",12).toString()
        val bytes=byteArrayOf(0xef.toByte(),0xbb.toByte(),0xbf.toByte())+text.toByteArray()
        val expected=FrozenRestoreJsonReader.read(bytes).getJSONObject("tables").getJSONArray("day_note").getJSONObject(0)
        BackupJsonReader.indexed(bytes).use {assertEquals(expected.toString(),it.tables.getJSONArray("day_note").getJSONObject(0).toString())}
    }
    @Test fun closedViewsCannotReadWipedPlaintextAndRepeatedCloseIsSafe() {
        val bytes="""{"format":1,"schema":12,"tables":{"day_note":[{"date":"2026-10-20","text":"SYNTHETIC"}]}}""".toByteArray()
        val indexed=BackupJsonReader.indexed(bytes);val rows=indexed.tables.getJSONArray("day_note")
        assertEquals("SYNTHETIC",rows.getJSONObject(0).getString("text"));indexed.close();indexed.close()
        assertTrue(bytes.all{it==0.toByte()})
        for(read in listOf<()->Unit>({rows.getJSONObject(0);Unit},{indexed.tables.keys();Unit})) {
            try{read();fail("Closed source must not read")}catch(_:IllegalStateException){}
        }
    }
    @Test fun malformedDocumentsMatchOldRejectionAndWipeOwnedBuffer() {
        for(text in listOf("{} {}","{\"schema\":12,\"schema\":12}","{\"tables\":{\"x\":[{\"a\":1,\"a\":2}]}}","{\"tables\":{\"x\":[NaN]}}","{\"tables\":{\"x\":[1,]}}")) {
            val bytes=text.toByteArray()
            try{FrozenRestoreJsonReader.read(bytes);fail("Old oracle must reject")}catch(_:BackupCodec.BadFile){}
            try{BackupJsonReader.indexed(bytes).close();fail("Indexed parser must reject")}catch(_:BackupCodec.BadFile){}
            assertTrue(bytes.all{it==0.toByte()})
        }
        val utf8="{\"tables\":{}}".toByteArray()+byteArrayOf(0xc3.toByte())
        try{BackupJsonReader.indexed(utf8);fail("Malformed UTF8")}catch(_:BackupCodec.BadFile){}
        assertTrue(utf8.all{it==0.toByte()})
    }
    @Test fun globalArrayAndObjectBudgetsDoNotResetAtRowBoundaries() {
        val rows=JSONArray((0..BackupLimits.MAX_ROWS).map{JSONObject().put("id",it)})
        val text=JSONObject().put("tables",JSONObject().put("day_note",rows)).toString();val bytes=text.toByteArray()
        try{BackupJsonReader.indexed(bytes);fail("Global row budget")}catch(_:BackupCodec.TooLarge){}
        assertTrue(bytes.all{it==0.toByte()})
        val nested="{\"tables\":{\"x\":["+"[".repeat(32)+"0"+"]".repeat(32)+"]}}"
        try{BackupJsonReader.indexed(nested.toByteArray());fail("Original depth budget")}catch(_:BackupCodec.TooLarge){}
    }
    @Test fun cancellationAndResourceFailuresDuringParseWipeAuthenticatedBytes() {
        for(error in listOf<Throwable>(kotlinx.coroutines.CancellationException("SYNTHETIC cancel"),OutOfMemoryError("SYNTHETIC resource fault"))) {
            val bytes="{\"format\":1,\"tables\":{\"day_note\":[{},{}]}}".toByteArray();var checks=0
            try{BackupJsonReader.indexed(bytes,checkActive={if(++checks==4)throw error});fail("Injected failure")}
            catch(actual:Throwable){assertSame(error,actual)}
            assertTrue(bytes.all{it==0.toByte()})
        }
    }
}
