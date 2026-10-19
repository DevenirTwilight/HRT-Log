package net.plainnotes.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RuleKind
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

/**
 * LAB-02 (docs/design/lab-panel-adr.md §9), synthetic data only: the same results give the same concentration lab points,
 * calibration eligibility, curve and per-lab estimate whether or not they belong to a lab panel. Phase A ran this on the
 * draft SQL; phase B runs it on the production Room 12 path (batch save makes a panel, "save as one blood test" groups).
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class Lab02PanelPkInvarianceTest {
    private lateinit var db: NotesDatabase
    private val at = Instant.parse("2026-03-10T12:00:00Z")
    @After fun close() = db.close()

    @Test fun panelMembershipChangesNoPkResult() = runBlocking {
        val c = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(c, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        val repo = NotesRepository(object : DatabaseAccess(c) { override fun get(space: Space) = db })
        val m = MedicationEntity(name = "Synthetic estimate", molecule = "E2", route = "SUBLINGUAL", unit = "MG", dose_per_intake = 2.0, container_capacity = 40.0,
            soon_alert_minutes = 15, late_after_minutes = 60, site_rotation = false, notifications_on = true, active = true, sort_order = 0)
        val id = repo.saveMedication(m, "E2", RuleKind.EVERY_N_DAYS, 1, listOf(LocalTime.of(8, 0)), emptySet(), at.minusSeconds(20 * 86400), pk = ProfileEntity(0, "E2", "sublingual", sl_tier = 2))
        val snapshot = MedicationSnapshot.encode(m.copy(id = id), ProfileEntity(id, "E2", "sublingual", sl_tier = 2))
        (1..10).forEach { d -> db.dao().record(RecordEntity(medication_id = id, taken_utc = at.minusSeconds(d * 86400L - 3600).toEpochMilli(), taken_zone = "UTC",
            actual_dose = 2.0, status = "ON_TIME", origin = "APP", revision = 1, config_snapshot = snapshot)) }
        val draw = at.minusSeconds(3 * 86400).toEpochMilli()
        repo.saveLabs(listOf("E2" to 150.0, "T" to 30.0, "PRL" to 12.0).map { (code, v) -> LabValueEntity(analyte_code = code, value = v,
            unit = net.plainnotes.app.ui.analyte(code).units.first().first, sampled_utc = draw, sampled_zone = "UTC", laboratory = "Synthetic lab") }, at)
        repo.saveLab(LabValueEntity(analyte_code = "E2", value = 180.0, unit = "pg/mL", sampled_utc = at.minusSeconds(86400).toEpochMilli(), sampled_zone = "UTC"), at)

        fun pk(): List<Any?> {
            val d = db.dao(); val meds = runBlocking { d.medications() }
            val labs = runBlocking { d.labs() }
            val r = ConcentrationCalculator.compute(meds, meds.mapNotNull { x -> runBlocking { d.profile(x.id) }?.let { x.id to it } }.toMap(), runBlocking { d.records() }, emptyList(), labs, null, at)
            val estimates = labs.map { runBlocking { net.plainnotes.app.conc.LabEstimate.capture(d, it) } }
            return listOf(labs, r.labs, r.labEligibility, r.calibration?.labCount, r.e2.toList(), r.timeH.toList(), r.currentPgMl, estimates)
        }
        // The batch save above made one lab panel; its results give the same PK input as the plain result next to it.
        assertEquals(1, repo.labPanels().size); assertTrue(db.dao().labs().filter { it.sampled_utc == draw }.all { it.panel_id != null })
        // Production path (LAB-02 phase B): the same three results, saved one by one, become one lab panel on confirmation.
        val single = draw + 3_600_000
        listOf("E2" to 150.0, "T" to 30.0, "PRL" to 12.0).forEach { (code, v) -> repo.saveLab(LabValueEntity(analyte_code = code, value = v,
            unit = net.plainnotes.app.ui.analyte(code).units.first().first, sampled_utc = single, sampled_zone = "UTC", laboratory = "Synthetic lab"), at) }
        val ungrouped = pk()
        repo.groupAsLabPanel(db.dao().labs().filter { it.sampled_utc == single }.map { it.id }, at)
        assertEquals(2, repo.labPanels().size)
        assertEquals(ungrouped.drop(1), pk().drop(1)) // every PK result; the rows themselves differ only by panel_id
        assertEquals((ungrouped[0] as List<*>).map { (it as LabValueEntity).copy(panel_id = null) }, (pk()[0] as List<*>).map { (it as LabValueEntity).copy(panel_id = null) })
    }
}
