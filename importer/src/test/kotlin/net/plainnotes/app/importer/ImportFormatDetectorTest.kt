package net.plainnotes.app.importer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.sql.DriverManager

class ImportFormatDetectorTest {
    private val tracker=javaClass.getResource("/hrttracker_v2_synthetic.json")!!.readText()
    @Test fun realSyntheticTrackerUsesExistingParser() {
        val parsed=ImportFormatDetector.json(tracker) as ImportFormatDetector.Parsed.Tracker
        val original=HrtTracker.read(tracker)
        assertEquals(original.weightKg,parsed.export.weightKg)
        assertEquals(original.events.map{listOf(it.id,it.at,it.doseMg,it.route,it.ester,it.extras)},parsed.export.events.map{listOf(it.id,it.at,it.doseMg,it.route,it.ester,it.extras)})
        assertEquals(original.labs.map{listOf(it.id,it.at,it.value,it.unit)},parsed.export.labs.map{listOf(it.id,it.at,it.value,it.unit)})
        assertEquals(ImportFormatDetector.Container.JSON,ImportFormatDetector.header(tracker.toByteArray()))
    }
    @Test fun containersAreDisjointRegardlessOfFilename() {
        assertEquals(ImportFormatDetector.Container.SQLITE,ImportFormatDetector.header("SQLite format 3\u0000rest".toByteArray()))
        assertEquals(ImportFormatDetector.Container.FEATHERLINE,ImportFormatDetector.header("HRTBKP1ciphertext".toByteArray()))
        assertEquals(ImportFormatDetector.Container.HRT_LOG,ImportFormatDetector.header("PNBAK1ciphertext".toByteArray()))
        assertEquals(ImportFormatDetector.Container.UNKNOWN,ImportFormatDetector.header("not a database.json".toByteArray()))
        assertThrows<ImportFormatDetector.Damaged>{ImportFormatDetector.json("SQLite format 3\u0000")}
    }
    @Test fun ambiguousUnknownDamagedAndExcessiveInputsRefuse() {
        assertThrows<ImportFormatDetector.Unknown>{ImportFormatDetector.json("[]")}
        assertThrows<ImportFormatDetector.Unknown>{ImportFormatDetector.json("{\"events\":[]}")}
        assertThrows<ImportFormatDetector.Damaged>{ImportFormatDetector.json("{\"events\":")}
        assertThrows<ImportFormatDetector.Damaged>{ImportFormatDetector.json(tracker+"{}")}
        assertThrows<ImportFormatDetector.TooLarge>{ImportFormatDetector.json("[".repeat(65)+"]".repeat(65))}
        assertThrows<ImportFormatDetector.Unsupported>{ImportFormatDetector.json(tracker.replace("\"version\": 2","\"version\": 99"))}
    }
    @Test fun actualSqliteSchemaAndVersionAreRequired() {
        val file=Files.createTempFile("synthetic-import", ".db")
        try {DriverManager.getConnection("jdbc:sqlite:$file").use {db->
            val sql=javaClass.getResource("/transmemo_v8_synthetic.sql")!!.readText()
            sql.split(";\n").map{it.lines().filterNot{line->line.trim().startsWith("--")}.joinToString("\n").trim()}
                .filter{it.isNotEmpty()}.forEach{db.createStatement().use{s->s.execute(it)}}
            val source=JdbcSource(db)
            val original=TransMemo.read(source)
            val detected=(ImportFormatDetector.sqlite(source) as ImportFormatDetector.Parsed.Memo).export
            assertEquals(original.products.map{it.id to it.name},detected.products.map{it.id to it.name})
            assertEquals(original.intakes.map{listOf(it.id,it.productId,it.takenAt,it.realDose,it.state)},detected.intakes.map{listOf(it.id,it.productId,it.takenAt,it.realDose,it.state)})
            assertEquals(original.notes,detected.notes)
            db.createStatement().use{it.execute("PRAGMA user_version=9")}
            assertThrows<ImportFormatDetector.Unsupported>{ImportFormatDetector.sqlite(source)}
            db.createStatement().use{it.execute("DROP TABLE intakes")}
            assertThrows<ImportFormatDetector.Unknown>{ImportFormatDetector.sqlite(source)}
        }}finally{Files.deleteIfExists(file)}
    }
}
