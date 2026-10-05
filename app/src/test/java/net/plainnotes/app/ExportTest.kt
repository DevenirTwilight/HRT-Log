package net.plainnotes.app

import androidx.test.core.app.ApplicationProvider
import net.plainnotes.app.data.*
import net.plainnotes.app.export.CsvExport
import net.plainnotes.app.export.ExportData
import net.plainnotes.app.export.PdfReport
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35])
class ExportTest {
    private val now = System.currentTimeMillis()
    private val data = ExportData(
        listOf(MedicationEntity(1, "Gel, \"synthetic\"", "E2", "GEL", "MG", 1.5, 80.0, null, 15, 120, false, null, true, true, 0)),
        mapOf(1L to ProfileEntity(1, "E2", "gel")),
        listOf(RecordEntity(1, 1, taken_utc = now - 3_600_000, taken_zone = "Europe/Paris", actual_dose = 1.5, status = "ON_TIME", origin = "APP", revision = 1, config_snapshot = "{}"),
            RecordEntity(2, 1, scheduled_utc = now - 90_000_000, scheduled_zone = "Europe/Paris", status = "MISSED", origin = "AUTO_MISSED", revision = 1, config_snapshot = "{}")),
        listOf(LabValueEntity(1, "E2", 150.0, "pg/mL", now - 7_200_000, "Europe/Paris", 50.0, 300.0, "pg/mL", note = "line1\nline2")),
        listOf(CheckinItemEntity(1, "MOOD", null, true, 0)), listOf(CheckinScoreEntity(java.time.LocalDate.now().toString(), 1, 4)),
        listOf(DayNoteEntity(java.time.LocalDate.now().toString(), "note, with comma")), mapOf(1L to "daily"), { "Mood" })

    @Test fun csvZipHasThreeQuotedFilesWithBom() {
        val out = ByteArrayOutputStream(); CsvExport.write(data, out)
        val files = HashMap<String, String>()
        ZipInputStream(out.toByteArray().inputStream()).use { z -> generateSequence { z.nextEntry }.forEach { e -> files[e.name] = String(z.readBytes()) } }
        assertEquals(setOf("intakes.csv", "wellbeing.csv", "labs.csv"), files.keys)
        files.values.forEach { assertTrue(it.startsWith("﻿")) }
        assertTrue(files.getValue("intakes.csv").contains("\"Gel, \"\"synthetic\"\"\""))
        assertEquals(3, files.getValue("intakes.csv").trim().lines().size)
        assertTrue(files.getValue("wellbeing.csv").contains("Mood,4,\"note, with comma\""))
        assertTrue(files.getValue("labs.csv").contains("\"line1\nline2\""))
    }

    @Test fun pdfReportIsWritten() {
        // Robolectric has no native PdfDocument backend; the check runs on real devices / instrumented tests.
        val supported = runCatching { android.graphics.pdf.PdfDocument().apply { finishPage(startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(10, 10, 1).create())); close() } }.isSuccess
        org.junit.Assume.assumeTrue("PdfDocument unavailable under Robolectric", supported)
        val out = ByteArrayOutputStream()
        PdfReport.write(ApplicationProvider.getApplicationContext(), data, 90, null, out)
        assertTrue(String(out.toByteArray().copyOfRange(0, 5)) == "%PDF-")
    }
}
