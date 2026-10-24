package net.plainnotes.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.conc.LabEstimate
import net.plainnotes.app.conc.SublingualEstimatePolicy
import net.plainnotes.app.conc.SublingualEstimatePolicy.PdfE2
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RuleKind
import net.plainnotes.app.reminder.ReminderCoordinator
import net.plainnotes.app.ui.labContextLines
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

/**
 * D-2 (REQUIREMENTS §67 item 5, option D2-2), all synthetic: new lab estimates and new PDFs never carry the retired
 * Legacy sublingual E2 prediction and say why; other-route-only E2 and other compounds keep their estimates; mixed
 * routes get no combined total E2; frozen estimates are displayed unchanged.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class SublingualEstimatePolicyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val at = Instant.parse("2026-03-10T12:00:00Z")
    private lateinit var db: NotesDatabase
    private lateinit var repo: NotesRepository
    private val unavailable get() = context.getString(R.string.lab_estimate_sl_unavailable)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        repo = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = net.plainnotes.app.syntheticLibrary(db) })
    }
    @After fun close() = db.close()

    private fun med(name: String, molecule: String, route: String, pkRoute: String?) = runBlocking {
        val m = MedicationEntity(name = name, molecule = molecule, route = route, unit = "MG", dose_per_intake = 2.0, container_capacity = 40.0,
            soon_alert_minutes = 15, late_after_minutes = 60, site_rotation = false, notifications_on = true, active = true, sort_order = 0)
        val profile = pkRoute?.let { ProfileEntity(0, molecule, it, sl_tier = 2) }
        val id = repo.saveMedication(m, molecule, RuleKind.EVERY_N_DAYS, 1, listOf(LocalTime.of(8, 0)), emptySet(), at.minusSeconds(86400), pk = profile)
        id to MedicationSnapshot.encode(m.copy(id = id), profile?.copy(medication_id = id))
    }
    private fun take(med: Pair<Long, String>, hoursBefore: Long, snapshot: String = med.second) = runBlocking {
        db.dao().record(RecordEntity(medication_id = med.first, taken_utc = at.minusSeconds(hoursBefore * 3600).toEpochMilli(), taken_zone = "UTC",
            actual_dose = 2.0, status = "ON_TIME", origin = "APP", revision = 1, config_snapshot = snapshot))
    }
    private val lab = LabValueEntity(analyte_code = "E2", value = 120.0, unit = "pg/mL", sampled_utc = at.toEpochMilli(), sampled_zone = "UTC")
    private fun capture() = runBlocking { LabEstimate.capture(db.dao(), lab) }
    private fun curves(estimate: String) = JSONObject(estimate).getJSONArray("values").let { v -> (0 until v.length()).map { v.getJSONObject(it).getString("curve") } }
    private fun saveAndLines(): List<String> = runBlocking {
        repo.saveLab(lab, at, estimate = { dao, l -> SublingualEstimatePolicy.apply(LabEstimate.capture(dao, l)) })
        val row = repo.labContexts().single(); LabContext.validate(row.context_json); labContextLines(context, row)
    }
    private fun legacy(): net.plainnotes.app.conc.ConcentrationResult = runBlocking {
        val meds = db.dao().medications(); val profiles = meds.mapNotNull { m -> db.dao().profile(m.id)?.let { m.id to it } }.toMap()
        ConcentrationCalculator.compute(meds, profiles, db.dao().records(), emptyList(), emptyList(), 70.0, at, calibrate = false)
    }
    private fun pdf() = runBlocking { SublingualEstimatePolicy.pdfE2(legacy(), db.dao().records()) }

    @Test fun pureSublingualE2HasNoNewEstimateAndSaysWhy() {
        take(med("SL", "E2", "SUBLINGUAL", "sublingual"), 3)
        assertTrue("Legacy still computes it", "E2" in curves(capture()))
        assertFalse("E2" in curves(SublingualEstimatePolicy.apply(capture())))
        assertTrue(unavailable in saveAndLines())
        assertEquals(PdfE2.SUBLINGUAL_UNAVAILABLE, pdf())
    }

    @Test fun mixedRoutesGetNoCombinedTotalE2() {
        take(med("SL", "E2", "SUBLINGUAL", "sublingual"), 3); take(med("Oral", "E2", "ORAL", "oral"), 5)
        assertFalse("E2" in curves(SublingualEstimatePolicy.apply(capture())))
        assertTrue(unavailable in saveAndLines())
        assertEquals(PdfE2.MIXED_UNAVAILABLE, pdf())
    }

    @Test fun otherRouteOnlyE2IsUnchangedByteForByte() {
        take(med("Oral", "E2", "ORAL", "oral"), 5)
        val raw = capture()
        assertTrue("E2" in curves(raw)); assertEquals(raw, SublingualEstimatePolicy.apply(raw))
        assertFalse(unavailable in saveAndLines())
        assertEquals(PdfE2.DRAW, pdf())
    }

    @Test fun noDosesGivesAnExplicitEmptyStateNotZero() {
        val raw = capture()
        assertEquals(raw, SublingualEstimatePolicy.apply(raw)); assertEquals(emptyList<String>(), curves(raw))
        val lines = saveAndLines()
        assertTrue(context.getString(R.string.lab_context_no_estimate) in lines); assertFalse(unavailable in lines)
        assertEquals(PdfE2.NONE, pdf())
    }

    @Test fun otherCompoundsKeepTheirEstimateBesideSublingualE2() {
        runBlocking { repo.setWeight(70.0) }  // CPA needs weight; E2 must be withheld for SL, not for a missing input
        take(med("SL", "E2", "SUBLINGUAL", "sublingual"), 3); take(med("CPA", "CPA", "ORAL", "oral"), 6)
        val raw = curves(capture()); val kept = curves(SublingualEstimatePolicy.apply(capture()))
        assertTrue("CPA" in raw && "CPA" in kept); assertFalse("E2" in kept)
        assertEquals(raw - "E2", kept)
        assertTrue(unavailable in saveAndLines())
    }

    @Test fun missingWeightForAnotherCompoundNeverHidesTheSublingualReason() {
        take(med("SL", "E2", "SUBLINGUAL", "sublingual"), 3); take(med("CPA", "CPA", "ORAL", "oral"), 6)
        assertTrue(JSONObject(capture()).getJSONArray("missing").length() > 0)
        assertTrue(unavailable in saveAndLines())
    }

    @Test fun unknownImportedHistoryIsNeverAssumedNotSublingual() {
        val oral = med("Oral", "E2", "ORAL", "oral"); take(oral, 5); take(oral, 2, snapshot = "{}")
        assertFalse("E2" in curves(SublingualEstimatePolicy.apply(capture())))
    }

    @Test fun frozenEstimatesAreDisplayedUnchanged() = runBlocking {
        take(med("SL", "E2", "SUBLINGUAL", "sublingual"), 3)
        // A frozen calculator_version 1 estimate that already contains the old sublingual E2 value.
        repo.saveLab(lab, at, estimate = { dao, l -> JSONObject(LabEstimate.capture(dao, l)).put("calculator_version", 1).toString() })
        val row = repo.labContexts().single(); val before = row.context_json
        val lines = labContextLines(context, row)
        assertFalse(unavailable in lines)
        assertTrue(lines.any { it.startsWith(context.getString(R.string.choice_e2) + ":") })
        assertEquals(before, repo.labContexts().single().context_json)
    }

    @Test fun viewModelSavesNewEstimatesThroughThePolicy() = runBlocking {
        take(med("SL", "E2", "SUBLINGUAL", "sublingual"), 3)
        val model = NotesViewModel(repo, ReminderCoordinator(context, repo), context).also {net.plainnotes.app.awaitRuntimeLibrary(it)}
        try {
            model.saveLab(lab, includeEstimate = true)
            val end = System.currentTimeMillis() + 20_000
            while (repo.labContexts().isEmpty() && System.currentTimeMillis() < end) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(20) }
            val estimate = LabContext.validate(repo.labContexts().single().context_json).getJSONObject("estimate")
            assertFalse("E2" in curves(estimate.toString()))
        } finally { androidx.lifecycle.ViewModelStore().apply { put("model", model); clear() } }
    }

    @Test fun pdfPlanNeverDrawsTheLegacySublingualCurveAndAlwaysSaysWhy() {
        val draw = net.plainnotes.app.export.e2ReportPlan(PdfE2.DRAW)
        assertTrue(draw.drawChart); assertEquals(emptyList<Int>(), draw.messages)
        val sl = net.plainnotes.app.export.e2ReportPlan(PdfE2.SUBLINGUAL_UNAVAILABLE)
        assertFalse(sl.drawChart); assertEquals(listOf(R.string.lab_estimate_sl_unavailable), sl.messages)
        val mixed = net.plainnotes.app.export.e2ReportPlan(PdfE2.MIXED_UNAVAILABLE)
        assertFalse(mixed.drawChart); assertEquals(listOf(R.string.lab_estimate_sl_unavailable, R.string.report_mixed_no_total_e2), mixed.messages)
        val none = net.plainnotes.app.export.e2ReportPlan(PdfE2.NONE)
        assertFalse(none.drawChart); assertEquals(emptyList<Int>(), none.messages)
    }
}
