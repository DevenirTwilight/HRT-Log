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
import java.util.UUID

/**
 * LAB-02 phase A (docs/design/lab-panel-adr.md §9), synthetic data only: the same results give the same concentration lab
 * points, calibration eligibility, curve and per-lab estimate whether or not they belong to a lab panel. The draft 11→12
 * SQL is applied under a real Room v11 database and the results are grouped into a draft panel; PK readers only read
 * lab_value rows, so nothing they see may change. Test-only: no production class knows about panels yet.
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
        val before = pk()

        val raw = db.openHelper.writableDatabase
        // Same draft SQL as core/data LabPanelDraft (ADR §2); test-only, copied because app tests cannot see core/data test classes.
        raw.beginTransaction(); try {
            raw.execSQL("CREATE TABLE IF NOT EXISTS `lab_panel` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `sampled_utc` INTEGER NOT NULL, `sampled_zone` TEXT NOT NULL, `laboratory` TEXT, `note` TEXT, `created_utc` INTEGER NOT NULL)")
            raw.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lab_panel_uuid` ON `lab_panel` (`uuid`)")
            raw.execSQL("ALTER TABLE `lab_value` ADD COLUMN `panel_id` INTEGER REFERENCES `lab_panel`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT")
            raw.execSQL("CREATE INDEX IF NOT EXISTS `index_lab_value_panel_id` ON `lab_value` (`panel_id`)")
            raw.setTransactionSuccessful() } finally { raw.endTransaction() }
        raw.execSQL("INSERT INTO lab_panel(id,uuid,sampled_utc,sampled_zone,laboratory,note,created_utc) VALUES(1,?,?,'UTC','Synthetic lab','Synthetic note',?)",
            arrayOf<Any?>(UUID.randomUUID().toString(), draw, at.toEpochMilli()))
        raw.execSQL("UPDATE lab_value SET panel_id=1 WHERE sampled_utc=?", arrayOf<Any?>(draw))
        raw.query("SELECT COUNT(*) FROM lab_value WHERE panel_id=1").use { it.moveToFirst(); assertEquals(3, it.getInt(0)) }

        assertEquals(before, pk())
    }
}
