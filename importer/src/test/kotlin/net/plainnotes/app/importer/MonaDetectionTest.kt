package net.plainnotes.app.importer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MonaDetectionTest {
    @Test fun reviewedMona23UsesTheCommonDetectorAndReportsItsOwnIdentity() {
        val seen=mutableListOf<String>()
        val json="""{"metadata":{"app_version":"synthetic","database_version":23,"export_date":"2026-10-25T12:00:00"},"data":{"medication_intakes":[],"medication_schedules":[],"supply_items":[],"blood_tests":[]}}"""
        ImportFormatDetector.json(json){seen+=it.name}
        assertEquals(listOf("MONA"),seen)
    }
    private fun fixture()=java.io.File("src/test/resources/mona23_synthetic.json").readText()
    @Test fun realTypedSourceAndUnsupportedVersionRemainDistinct() {
        assertTrue(ImportFormatDetector.json(fixture()) is ImportFormatDetector.Parsed.Mona)
        val version=fixture().replace("\"database_version\": 23","\"database_version\": 24")
        assertEquals(ImportFormatDetector.Source.MONA,assertThrows(ImportFormatDetector.Unsupported::class.java){ImportFormatDetector.json(version)}.source)
    }
    @Test fun ambiguousFingerprintAndGenericMetadataCannotSelectAnImporter() {
        assertThrows(ImportFormatDetector.Unknown::class.java){ImportFormatDetector.json("{\"metadata\":{},\"data\":{}}")}
        val root=org.json.JSONObject(fixture()).put("meta",org.json.JSONObject().put("version",2).put("exportedAt","synthetic"))
            .put("events",org.json.JSONArray()).put("weight",60)
        assertThrows(ImportFormatDetector.Unknown::class.java){ImportFormatDetector.json(root.toString())}
    }
    @Test fun damagedTablesAndMissingIdsCannotBecomeEmptySuccess() {
        val root=org.json.JSONObject(fixture());root.getJSONObject("data").put("blood_tests","not an array")
        assertThrows(ImportFormatDetector.Damaged::class.java){ImportFormatDetector.json(root.toString())}
        val missing=org.json.JSONObject(fixture());missing.getJSONObject("data").getJSONArray("medication_intakes").getJSONObject(0).remove("id")
        assertThrows(ImportFormatDetector.Damaged::class.java){ImportFormatDetector.json(missing.toString())}
    }
}
