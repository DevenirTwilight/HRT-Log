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
}
