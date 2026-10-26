package net.plainnotes.app.importer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.json.JSONObject
import java.time.Instant

class ChrysalideImportTest {
    private fun fixture(version:Int=5):String {
        val empty="[]"
        return """{"version":$version,"exportedAt":"2026-10-26T00:00:00.000Z","medications":[{"id":7,"name":"Estradiol","type":"estrogen","dosage":2,"unit":"mg","method":"pill","startDate":"2024-01-01T00:00:00.000Z","createdAt":"2024-01-01T00:00:00.000Z","updatedAt":"2024-01-01T00:00:00.000Z","isActive":true}],"medicationLogs":[{"id":1,"medicationId":7,"timestamp":"2024-03-31T07:30:00.000Z","taken":true,"scheduledTime":"08:00","doseIndex":0,"notes":"synthetic"},{"id":2,"medicationId":7,"timestamp":"2024-03-31T08:00:00.000Z","taken":false,"scheduledTime":"08:00"}],"bloodTests":[{"id":3,"date":"2024-02-01","lab":"synthetic lab","results":[{"marker":"estradiol","value":120,"unit":"pg/mL"}],"notes":"synthetic"}],"physicalProgress":$empty,"appointments":$empty,"reminders":$empty,"userProfile":$empty,"journalEntries":$empty,"objectives":$empty,"milestones":$empty,"treatmentChanges":$empty,"practitioners":$empty,"acts":$empty,"actTodos":$empty}"""
    }
    @Test fun schemaFiveStrictlyRecognizedAndActualDoseIsNotInvented() {
        val p=ImportFormatDetector.json(fixture()) as ImportFormatDetector.Parsed.Chrysalide
        assertEquals(5,p.export.version)
        val plan=ChrysalideMigration.plan(p.export)
        assertEquals(1,plan.intakes.size);assertEquals(Instant.parse("2024-03-31T07:30:00Z"),plan.intakes.single().at)
        assertEquals(2.0,plan.intakes.single().medication.dosage)
        assertTrue(plan.issues.any{it.reason==ChrysalideMigration.Reason.MEDICATION_LOG_HAS_NO_DOSE})
        assertTrue(plan.issues.any{it.reason==ChrysalideMigration.Reason.NOT_TAKEN})
        assertEquals("2024-02-01",p.export.bloodTests.single().dateOnly)
        assertEquals(1,p.export.bloodTests.single().results.size)
        assertEquals(0,p.export.unsupported.size)
    }
    @Test fun unsupportedVersionAndBrokenReferenceFailClosed() {
        assertThrows<ImportFormatDetector.Unsupported>{ImportFormatDetector.json(fixture(6))}
        assertThrows<Chrysalide.InvalidExport>{Chrysalide.read(fixture().replace("\"medicationId\":7","\"medicationId\":99"))}
    }
    @Test fun genericOrOtherJsonIsNotChrysalide() {
        assertThrows<ImportFormatDetector.Unknown>{ImportFormatDetector.json("""{"version":5,"exportedAt":"2026-10-26T00:00:00Z","medications":[],"bloodTests":[]}""")}
        assertThrows<ImportFormatDetector.Unknown>{ImportFormatDetector.json("""{"metadata":{},"data":{}}""")}
        assertThrows<ImportFormatDetector.Unknown>{ImportFormatDetector.json("""{"medications":[],"events":[],"labResults":[]}""")}
    }
}
