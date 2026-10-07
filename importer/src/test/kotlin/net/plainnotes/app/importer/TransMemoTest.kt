package net.plainnotes.app.importer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class JdbcSource(private val c: Connection) : SqlSource {
    override fun userVersion() = c.createStatement().executeQuery("PRAGMA user_version").use { it.next(); it.getInt(1) }
    override fun columns(table: String): List<String>? = c.createStatement().executeQuery("PRAGMA table_info($table)").use { r ->
        buildList { while (r.next()) add(r.getString("name")) }.takeIf { it.isNotEmpty() } }
    override fun rows(table: String): List<Map<String, Any?>> = c.createStatement().executeQuery("SELECT * FROM $table").use { r ->
        val n = r.metaData.columnCount
        buildList { while (r.next()) add((1..n).associate { r.metaData.getColumnName(it) to r.getObject(it) }) } }
}

class TransMemoTest {
    private val paris = ZoneId.of("Europe/Paris")
    private lateinit var export: TmExport

    private fun connect(sql: String): Connection {
        val f = Files.createTempFile("tm", ".db").toFile().apply { deleteOnExit() }
        val c = DriverManager.getConnection("jdbc:sqlite:${f.path}")
        sql.split(";\n").map { it.lines().filterNot { l -> l.trim().startsWith("--") }.joinToString("\n").trim() }.filter { it.isNotEmpty() }.forEach { c.createStatement().execute(it) }
        return c
    }
    private val fixture = javaClass.getResource("/transmemo_v8_synthetic.sql")!!.readText()

    @BeforeEach fun load() { export = TransMemo.read(JdbcSource(connect(fixture))) }

    @Test fun rejectsWrongVersionAndMissingColumns() {
        assertThrows<InvalidExport> { TransMemo.read(JdbcSource(connect(fixture.replace("PRAGMA user_version = 8", "PRAGMA user_version = 7")))) }
        val dropped = connect(fixture).apply { createStatement().execute("ALTER TABLE intakes DROP COLUMN realSide") }
        assertThrows<InvalidExport> { TransMemo.read(JdbcSource(dropped)) }
    }

    @Test fun previewCountsEveryCategory() {
        val p = TransMemo.preview(export, paris)
        assertEquals(4, p.products)
        assertEquals(1, p.takenWithoutTime); assertEquals(2, p.lateWithoutTime); assertEquals(1, p.lateWithTime)
        assertEquals(1, p.missed); assertEquals(1, p.pending); assertEquals(1, p.unknownStates)
        assertEquals(1, p.unknownContainerStates); assertEquals(mapOf(0L to 1, 1L to 1, 4L to 1, 5L to 1), p.wellbeingValues)
        assertEquals(2, p.notesWithText); assertEquals(2, p.emptyNotes)
        assertEquals(setOf("SOMETHING_NEW"), p.unknownMolecules); assertEquals(setOf("TESTOSTERONE"), p.inferredMolecules); assertEquals(setOf("GRAM"), p.unknownUnits)
        assertTrue(p.needsLateChoice && p.needsWellbeingChoice)
    }

    private fun choices(late: TransMemo.LateHandling? = TransMemo.LateHandling.AS_MISSED, wb: TransMemo.WellbeingScale? = TransMemo.WellbeingScale.ONE_TO_FIVE) =
        TransMemo.Choices(mapOf(1L to 120, 2L to 60, 3L to 60, 4L to 60), late, wb)

    @Test fun requiredChoicesHaveNoDefault() {
        assertThrows<TransMemo.MissingChoice> { TransMemo.plan(export, paris, choices(late = null)) }
        assertThrows<TransMemo.MissingChoice> { TransMemo.plan(export, paris, choices(wb = null)) }
        assertThrows<TransMemo.MissingChoice> { TransMemo.plan(export, paris, TransMemo.Choices(mapOf(1L to 120), TransMemo.LateHandling.SKIP, TransMemo.WellbeingScale.SKIP)) }
    }

    @Test fun unknownCodesAreFlaggedNotGuessed() {
        val meds = TransMemo.plan(export, paris, choices()).medications.associateBy { it.sourceId }
        assertEquals("E2", meds.getValue(1).molecule); assertEquals("MG", meds.getValue(1).unit)
        assertEquals("T", meds.getValue(3).molecule); assertEquals("TESTOSTERONE", meds.getValue(3).review["molecule_inferred"])
        assertEquals("OTHER", meds.getValue(4).molecule); assertEquals("SOMETHING_NEW", meds.getValue(4).review["molecule"])
        assertEquals("OTHER", meds.getValue(3).unit); assertEquals("GRAM", meds.getValue(3).review["unit"])
        assertEquals("TESTOSTERONE", meds.getValue(3).name) // empty name falls back to the raw code
        meds.values.forEach { m -> listOf("soonAlertDelay", "lateAlertDelay", "notifications", "intakeInterval").forEach { assertTrue(it in m.review) } }
        assertEquals("", meds.getValue(1).review["route"]) // estradiol route must be chosen
        assertTrue(meds.getValue(1).prefillDaily); assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), meds.getValue(1).prefillTimes)
        assertFalse(meds.getValue(3).prefillDaily) // interval 3: not guessed
        assertTrue(meds.getValue(3).siteRotation); assertFalse(meds.getValue(3).active)
    }

    @Test fun intakesFollowChoicesAndTimeRules() {
        val plan = TransMemo.plan(export, paris, choices())
        val byId = plan.intakes.associateBy { it.sourceKey.split(":")[2].toLong() }
        assertEquals("ON_TIME", byId.getValue(1).status)
        assertEquals("LATE", byId.getValue(2).status) // 3.5 h after, threshold 120 min
        assertEquals(Instant.parse("2026-03-01T22:30:00.308Z"), byId.getValue(2).taken) // fractional seconds kept
        assertEquals(Instant.parse("2026-03-29T01:00:00Z"), byId.getValue(3).scheduled) // 02:30 does not exist: first valid instant, 03:00 CEST (domain rule)
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), byId.getValue(4).scheduled) // overlap: earlier (summer) offset
        assertNull(byId.getValue(4).actualDose) // realDose 0 is not an amount
        assertEquals("MISSED", byId.getValue(5).status); assertNull(byId.getValue(5).taken)
        assertEquals("MISSED", byId.getValue(7).status)
        assertEquals("LEFT", byId.getValue(8).site); assertNull(byId.getValue(1).site)
        assertEquals("LATE", byId.getValue(11).status) // LATE with takenAt behaves like TAKEN
        assertFalse(9L in byId); assertFalse(10L in byId); assertFalse(12L in byId)
        assertEquals(1, plan.skipped["taken_without_time"]); assertEquals(1, plan.skipped["pending"]); assertEquals(1, plan.skipped["unknown_state"])
        val skipLate = TransMemo.plan(export, paris, choices(late = TransMemo.LateHandling.SKIP))
        assertEquals(2, skipLate.skipped["late_skipped"]); assertFalse(skipLate.intakes.any { it.sourceKey.startsWith("tm:intakes:5:") })
    }

    @Test fun lateThresholdBoundary() {
        // Intake 2 was taken 210 min 0.308 s after it was planned.
        fun status(minutes: Int) = TransMemo.plan(export, paris, TransMemo.Choices(mapOf(1L to minutes, 2L to 60, 3L to 60, 4L to 60), TransMemo.LateHandling.SKIP, TransMemo.WellbeingScale.SKIP))
            .intakes.first { it.sourceKey.startsWith("tm:intakes:2:") }.status
        assertEquals("LATE", status(210)); assertEquals("ON_TIME", status(211))
    }

    @Test fun containersWellbeingNotesAndAppointments() {
        val plan = TransMemo.plan(export, paris, choices())
        assertEquals(3, plan.containers.size); assertEquals(1, plan.skipped["container_unknown_state"])
        assertEquals(30.0, plan.containers.first { it.productId == 2L }.used, 1e-9) // clamped to capacity
        assertEquals(LocalDate.parse("2026-02-20"), plan.containers.first().openedOn)
        val items = plan.items.associateBy { it.sourceId }
        assertEquals("OVERALL", items.getValue(1).builtinKey); assertEquals("DAY_ENERGY", items.getValue(2).builtinKey); assertEquals("PERIOD_LIKE", items.getValue(3).builtinKey)
        assertFalse(items.getValue(3).enabled); assertEquals("Synthetic custom", items.getValue(4).label); assertNull(items.getValue(4).builtinKey)
        assertEquals(3, plan.scores.size); assertEquals(1, plan.skipped["score_out_of_range"]) // value 0 on a 1-5 scale
        assertEquals(mapOf(LocalDate.parse("2026-03-02") to "Synthetic text A\nSynthetic text B"), plan.notes); assertEquals(2, plan.skipped["empty_note"])
        val a = plan.appointments.single()
        assertEquals(Instant.parse("2026-04-01T08:00:00Z"), a.at); assertEquals("[Trans Memo: SOME_TYPE]", a.note); assertEquals(60, a.reminderMinutes)
    }

    @Test fun zeroBasedScaleShiftsValues() {
        val plan = TransMemo.plan(export, paris, choices(wb = TransMemo.WellbeingScale.ZERO_BASED))
        assertEquals(listOf(1, 2, 5), plan.scores.map { it.value }.sorted()); assertEquals(1, plan.skipped["score_out_of_range"]) // 5 -> 6 is out of range
        assertTrue(TransMemo.plan(export, paris, choices(wb = TransMemo.WellbeingScale.SKIP)).scores.isEmpty())
    }
}
