package net.plainnotes.app.importer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MonaEvidenceTest {
    @Test fun canonicalStringsRoundTripUnicodeControlCharactersAndQuotes() {
        val value="synthetic é 中文 😀 \" \\ \n\t"+0.toChar()
        val encoded=Mona.canonical(JSONObject().put("value",value))
        assertEquals(value,JSONObject(encoded).getString("value"))
        assertEquals("{\"value\":\"\\ud800\"}",Mona.canonical(JSONObject().put("value","\uD800")))
        assertNotEquals(Mona.canonical("?"),Mona.canonical("\uD800"))
    }
    @Test fun keyOrderDoesNotChangeEvidenceAndSourceIdsRemainExact() {
        val first=JSONObject().put("z",JSONArray().put(9007199254740993L)).put("a","1.2500")
        val second=JSONObject().put("a","1.2500").put("z",JSONArray().put(9007199254740993L))
        assertEquals(Mona.canonical(first),Mona.canonical(second))
        assertEquals(9007199254740993L,JSONObject(Mona.canonical(first)).getJSONArray("z").getLong(0))
    }
    private fun root()=JSONObject().put("metadata",JSONObject().put("app_version","synthetic").put("database_version",23).put("export_date","2026-10-25T12:00:00"))
        .put("data",JSONObject().put("medication_intakes",JSONArray()).put("medication_schedules",JSONArray()).put("supply_items",JSONArray()).put("blood_tests",JSONArray()))
    private fun intake()=JSONObject().put("id",1).put("takenDateTime","2026-10-25T00:30:00Z").put("takenTimeZone","Europe/Paris").put("takenDose","2.00")
        .put("molecule",JSONObject().put("name","estradiol").put("massUnit","mg").toString()).put("administrationRoute","oral").put("dosingBasis","mass")
    @Test fun sourceRowsAreFrozenCopiesNotMutableParserObjects() {
        val root=root();val row=intake();root.getJSONObject("data").getJSONArray("medication_intakes").put(row)
        val export=Mona.readRoot(root);val frozen=export.sourceRows.getValue("medication_intakes:1")
        row.put("takenDose","99");assertEquals("2.00",JSONObject(frozen).getString("takenDose"))
        assertEquals("2.00",export.intakes.single().doseOriginal)
    }
    @Test fun oversizedAndMalformedUnicodeFieldsAreRefused() {
        for(note in listOf("x".repeat(16385),"bad\uD800")) {
            val root=root();root.getJSONObject("data").getJSONArray("medication_intakes").put(intake().put("notes",note))
            assertThrows<Mona.InvalidExport>{Mona.readRoot(root)}
        }
    }
    @Test fun embeddedJsonDepthIsBoundedIndependentlyOfTheOuterString() {
        val root=root();root.getJSONObject("data").getJSONArray("medication_intakes").put(intake().put("molecule","{\"next\":".repeat(70)+"{}"+"}".repeat(70)))
        val export=Mona.readRoot(root)
        assertNull(export.intakes.single().molecule)
        assertTrue(export.issues.any {it.field=="molecule" && it.reason==Mona.Reason.INPUT_LIMIT})
    }
    @Test fun trailingJsonIsNotASecondExportOrAnIgnoredDocument() {
        assertThrows<Mona.InvalidExport>{Mona.read(root().toString()+root().toString())}
    }
}
