package net.plainnotes.app.importer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant

class MonaTest {
    private fun molecule(name: String = "estradiol", unit: String = "mg") = JSONObject().put("name", name).put("massUnit", unit).put("rateUnit", "µg/day").toString()
    private fun intake(id: Long = 1234567890123) = JSONObject().put("id", id).put("takenDateTime", "2026-10-25T00:30:00.123456Z")
        .put("takenTimeZone", "Europe/Paris").put("takenDose", "1.2500").put("molecule", molecule())
        .put("administrationRoute", "oral").put("ester", JSONObject.NULL).put("dosingBasis", "mass")
        .put("genericSupplyItemIds", "[]").put("placements", "[]")
    private fun backup() = JSONObject().put("metadata", JSONObject().put("app_version", "synthetic-0.0.1").put("database_version", 23).put("export_date", "2026-10-25T12:00:00"))
        .put("data", JSONObject().put("medication_intakes", JSONArray().put(intake())).put("medication_schedules", JSONArray()).put("supply_items", JSONArray()).put("blood_tests", JSONArray()))
    private fun read(root: JSONObject) = Mona.read(root.toString())

    @Test fun actualCurrentSqlRowsPreserveIdentityExactTimeDecimalRouteAndMolecule() {
        val result = read(backup()); val row = result.intakes.single()
        assertEquals(23, result.metadata.databaseVersion); assertEquals("synthetic-0.0.1", result.metadata.appVersion)
        assertEquals("1234567890123", row.id); assertEquals(Instant.parse("2026-10-25T00:30:00.123456Z"), row.taken!!.instant)
        assertEquals("2026-10-25T00:30:00.123456Z", row.taken.original); assertEquals("Europe/Paris", row.taken.zone)
        assertEquals("1.2500", row.doseOriginal); assertEquals(BigDecimal("1.2500"), row.dose)
        assertEquals("mg", row.doseUnit); assertEquals("oral", row.route); assertEquals("estradiol", row.molecule!!.name)
        assertNull(row.ester); assertTrue(result.issues.isEmpty())
    }
    @Test fun plannedOrMissingActualTimeDoesNotBecomeTakenOrMissed() {
        val root = backup(); root.getJSONObject("data").getJSONArray("medication_intakes").getJSONObject(0).put("takenDateTime", JSONObject.NULL).put("scheduledTime", "08:00")
        val result = read(root); assertNull(result.intakes.single().taken)
        assertTrue(result.issues.any { it.reason == Mona.Reason.NOT_TAKEN }); assertEquals("08:00", result.intakes.single().scheduledTime)
    }
    @Test fun dstOverlapInstantsAreNeverReconstructedFromWallTime() {
        val root = backup(); val rows = root.getJSONObject("data").getJSONArray("medication_intakes")
        rows.getJSONObject(0).put("takenDateTime", "2026-10-25T00:30:00Z")
        rows.put(intake(1234567890124).put("takenDateTime", "2026-10-25T01:30:00Z"))
        val result = read(root); assertEquals(3600, result.intakes[1].taken!!.instant!!.epochSecond - result.intakes[0].taken!!.instant!!.epochSecond)
        assertTrue(result.intakes.all { it.taken!!.zone == "Europe/Paris" })
    }
    @Test fun timezoneOrOffsetAbsenceNeverUsesDeviceDefault() {
        val root = backup(); root.getJSONObject("data").getJSONArray("medication_intakes").getJSONObject(0).put("takenDateTime", "2026-10-25T00:30:00").remove("takenTimeZone")
        val result = read(root); val row = result.intakes.single(); assertNull(row.taken!!.instant); assertNull(row.taken.zone)
        assertTrue(result.issues.any { it.field == "timestamp" }); assertTrue(result.issues.any { it.field == "timeZone" })
    }
    @Test fun unknownRouteUnitAndEsterRemainOriginalAndAreNotMadeEquivalent() {
        val root = backup(); root.getJSONObject("data").getJSONArray("medication_intakes").getJSONObject(0)
            .put("administrationRoute", "unknown-route").put("ester", "unreviewed-ester").put("molecule", molecule("estradiol unknown salt", "arbitrary-unit"))
        val result = read(root); val row = result.intakes.single()
        assertEquals("unknown-route", row.route); assertEquals("unreviewed-ester", row.ester); assertEquals("arbitrary-unit", row.doseUnit)
        assertEquals("estradiol unknown salt", row.molecule!!.name); assertTrue(result.issues.any { it.reason == Mona.Reason.UNKNOWN_VALUE })
    }
    @Test fun patchReleaseRateIsNotReinterpretedAsMassOrPatchCount() {
        val root = backup(); root.getJSONObject("data").getJSONArray("medication_intakes").getJSONObject(0).put("dosingBasis", "releaseRate").put("administrationRoute", "patch").put("takenDose", "50")
        val row = read(root).intakes.single(); assertEquals("µg/day", row.doseUnit); assertEquals(BigDecimal("50"), row.dose); assertEquals("releaseRate", row.dosingBasis)
    }
    @Test fun hormoneValuesRemainSeparateReportedValuesWithTheirOwnUnitsAndNoConversion() {
        val root = backup(); root.getJSONObject("data").getJSONArray("blood_tests").put(JSONObject().put("id", 34).put("dateTime", "2026-10-25T08:30:00Z").put("timeZone", "UTC")
            .put("estradiolLevels", JSONObject().put("value", "210.00").put("unit", "pg/mL").toString())
            .put("testosteroneLevels", JSONObject().put("value", "0").put("unit", "nmol/L").toString()))
        val result = read(root); val lab = result.bloodTests.single(); assertEquals("34", lab.id)
        assertEquals(listOf("E2", "T"), lab.values.map { it.analyte }); assertEquals(listOf("210.00", "0"), lab.values.map { it.valueOriginal })
        assertEquals(listOf("pg/mL", "nmol/L"), lab.values.map { it.unit }); assertEquals(BigDecimal.ZERO, lab.values[1].value)
        assertTrue(result.issues.isEmpty()) // Retains the reported zero, no claim about detection limit or true biological zero.
    }
    @Test fun schedulesAndStockAreReferenceOnlyAndMissingFkIsExplicit() {
        val root = backup(); val data = root.getJSONObject("data")
        data.getJSONArray("medication_schedules").put(JSONObject().put("id", 4).put("name", "Synthetic plan").put("dose", "2").put("startDate", "2026-01-01")
            .put("molecule", molecule()).put("administrationRoute", "oral").put("dosingBasis", "mass").put("scheduling", "{\"type\":\"synthetic-unmapped\"}"))
        data.getJSONArray("supply_items").put(JSONObject().put("id", 9).put("name", "Synthetic supply").put("type", "medication").put("molecule", molecule()).put("administrationRoute", "oral").put("dosingBasis", "mass").put("totalDose", "56").put("usedDose", "1").put("dosePerUnit", "2"))
        data.getJSONArray("medication_intakes").getJSONObject(0).put("scheduleId", 4).put("medicationSupplyItemId", 99)
        val result = read(root); assertEquals(1, result.schedules.size); assertEquals(1, result.supplies.size)
        assertEquals("4", result.intakes.single().scheduleId); assertEquals("99", result.intakes.single().medicationSupplyItemId)
        assertEquals(2, result.issues.count { it.reason == Mona.Reason.REFERENCE_ONLY }); assertTrue(result.issues.any { it.reason == Mona.Reason.MISSING_REFERENCE })
    }
    @Test fun malformedClinicalValueIsNotZeroOrARecommendedDefault() {
        for (value in listOf("NaN", "Infinity", "-1", "0", "1e999999")) {
            val root = backup(); root.getJSONObject("data").getJSONArray("medication_intakes").getJSONObject(0).put("takenDose", value)
            val result = read(root); assertNull(result.intakes.single().dose); assertEquals(value, result.intakes.single().doseOriginal)
            assertTrue(result.issues.any { it.field == "takenDose" && it.reason == Mona.Reason.INVALID_VALUE })
        }
    }
    @Test fun onlyReviewedVersionAndCompleteUniqueTypedRowsAreAccepted() {
        for (version in listOf(4, 22, 24)) { val root = backup(); root.getJSONObject("metadata").put("database_version", version); assertThrows<Mona.InvalidExport> { read(root) } }
        val duplicate = backup(); duplicate.getJSONObject("data").getJSONArray("medication_intakes").put(intake()); assertThrows<Mona.InvalidExport> { read(duplicate) }
        val wrongType = backup(); wrongType.getJSONObject("data").getJSONArray("medication_intakes").getJSONObject(0).put("takenDose", 2.0); assertThrows<Mona.InvalidExport> { read(wrongType) }
        val missingTable = backup(); missingTable.getJSONObject("data").remove("blood_tests"); assertThrows<Mona.InvalidExport> { read(missingTable) }
        assertThrows<Mona.InvalidExport> { Mona.read("{}") }; assertThrows<Mona.InvalidExport> { Mona.read("{\"metadata\":") }
    }
    @Test fun unsupportedFieldsAndCollectionsAreNotSilentlyCountedAsImported() {
        val root = backup(); root.getJSONObject("data").put("future_table", JSONArray().put(JSONObject().put("id", 3)))
        root.getJSONObject("data").getJSONArray("medication_intakes").getJSONObject(0).put("unmappedField", "synthetic")
        val result = read(root); assertTrue(result.issues.any { it.reason == Mona.Reason.UNSUPPORTED_FIELD && it.field == "unmappedField" })
        assertEquals(1, result.issues.single { it.reason == Mona.Reason.UNSUPPORTED_COLLECTION }.count)
    }
    @Test fun nestingIsRejectedBeforeConstructingDeepJsonObjectGraph() {
        assertThrows<Mona.InvalidExport> { Mona.read("[".repeat(10000) + "]".repeat(10000)) }
    }
}
