package net.plainnotes.app

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.conc.TotalE2Calibration
import net.plainnotes.app.conc.experimental.ResearchHistoricalSlAdapter
import net.plainnotes.app.data.*
import net.plainnotes.app.disguise.Disguise
import net.plainnotes.app.experimental.ConcentrationPreview
import net.plainnotes.app.pk.experimental.ResearchModel
import net.plainnotes.app.security.AppLock
import net.plainnotes.app.security.Session
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import java.time.Instant

/** Real MainActivity + real saved history and labs, never the isolated concentration demo Activity. */
class CalibrationProductAndroidTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var activity: ActivityScenario<MainActivity>? = null
    private lateinit var model: NotesViewModel
    @Before fun setup() {
        net.plainnotes.app.attachmentpoc.SyntheticEmulatorGuard.requireEmulator()
        Disguise.disable(context); AppLock(context).disable(); Session.reset()
        UiPrefs(context).disclaimerAccepted = true
    }
    private val offMain = OffMainApplyRecorder()
    @After fun cleanup() { try { activity?.close(); Session.reset(); offMain.assertNone() } finally { offMain.close() } }
    private fun launch() {
        activity = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        activity!!.onActivity { model = ViewModelProvider(it)[NotesViewModel::class.java] }
        ui.waitUntil(30_000) { model.librarySession.value is net.plainnotes.app.runtime.RuntimeLibrarySession.State.Ready }
    }
    private fun navigate(destination: Destination) {
        ui.waitUntil(30_000) { ui.onAllNodesWithContentDescription(context.getString(R.string.menu)).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription(context.getString(R.string.menu)).performClick()
        val label = context.getString(destination.title)
        ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(label))
        ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(label)).performClick()
    }
    private fun showing(tag: String) = ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun value():String { val node=ui.onNodeWithTag("e2-now-value").fetchSemanticsNode(); return ui.runOnIdle { node.config[SemanticsProperties.Text].joinToString("") { it.text } } }
    private fun switch() { ui.onNodeWithTag("calibration-switch").performScrollTo().performClick() }

    @Test fun savedSublingualHistoryCalibratesTheProductionTotalAndSurvivesReopen() {
        launch()
        val repo = model.libraryRuntime.currentRepository()
        val now = Instant.now()
        val nowH = ConcentrationCalculator.hours(now)
        val records = runBlocking { repo.transaction { dao ->
            assertTrue("This test requires a fresh isolated synthetic installation", dao.medications().isEmpty() && dao.records().isEmpty() && dao.labs().isEmpty())
            val draft = MedicationEntity(name="SYNTHETIC SL E2", molecule="E2",route="SUBLINGUAL",unit="MG",dose_per_intake=1.0,
                container_capacity=30.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
            val id=dao.insertMedication(draft);val med=draft.copy(id=id)
            val profile=ProfileEntity(id,"E2","sublingual",sl_tier=2);dao.profile(profile)
            (0 until 21).map { i ->
                val row=RecordEntity(medication_id=id,taken_utc=now.minusSeconds((3+12L*i)*3600).toEpochMilli(),taken_zone="UTC",
                    actual_dose=1.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=MedicationSnapshot.encode(med,profile))
                row.copy(id=dao.record(row))
            }
        } }
        val preview=ConcentrationPreview.compute(ResearchHistoricalSlAdapter.audit(records,emptyMap(),nowH),ResearchModel.M2,nowH)
        // saveLabs writes ONE sample time per call (a lab panel), so each draw is its own call.
        runBlocking { listOf(30L,54L).forEach { ago ->
            val sample=now.minusSeconds(ago*3600)
            val expected=checkNotNull(preview.valueAt(ConcentrationCalculator.hours(sample)))
            repo.saveLabs(listOf(LabValueEntity(analyte_code="E2",value=preview.anchor.background+1.6*(expected-preview.anchor.background),unit="pg/mL",
                sampled_utc=sample.toEpochMilli(),sampled_zone="UTC")))
        } }
        model.refresh()
        ui.waitUntil(30_000) { model.extra.value.records.size==21 && model.extra.value.labs.size==2 }
        navigate(Destination.CONCENTRATION)
        ui.waitUntil(30_000) { showing("calibration-result") && showing("e2-now-value") }
        val calibrated=value()
        ui.onNodeWithTag("calibration-switch").performScrollTo().assertIsEnabled().assertIsOn()
        val fit=net.plainnotes.app.ui.E2Calibration.run(preview,null,model.conc.value.labs,model.conc.value.result,
            net.plainnotes.app.ui.E2Calibration.firstDoseH(records.map {Instant.ofEpochMilli(it.taken_utc!!)}),nowH) {} as TotalE2Calibration.Calibrated
        assertEquals(1.6,fit.factor,0.2);assertEquals(2,fit.usedLabIds.size)
        switch()
        ui.waitUntil(30_000) { showing("e2-now-value") && !showing("calibration-result") && value()!=calibrated }
        val plain=value();assertNotEquals(calibrated,plain)
        ui.onNodeWithTag("calibration-switch").performScrollTo().assertIsOff()
        switch()
        ui.waitUntil(30_000) { showing("calibration-result") && showing("e2-now-value") && value()!=plain }
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4").close()
        ui.waitUntil(30_000) { !showing("calibration-result") }
        navigate(Destination.CONCENTRATION)
        ui.waitUntil(30_000) { showing("calibration-result") }
        activity!!.close();activity=null
        launch();navigate(Destination.CONCENTRATION)
        ui.waitUntil(30_000) { showing("calibration-result") && showing("e2-now-value") }
        ui.onNodeWithTag("calibration-switch").performScrollTo().assertIsOn()
        runBlocking {assertEquals(21,model.libraryRuntime.currentRepository().records().size);assertEquals(2,model.libraryRuntime.currentRepository().labs().size)}
        context.filesDir.resolve("calibration-native-pid").writeText(android.os.Process.myPid().toString())
    }
    @Test fun newProcessReopensSavedSublingualCalibration() {
        val previous=context.filesDir.resolve("calibration-native-pid").readText().trim().toInt()
        assertNotEquals("The runner must really terminate the earlier application process",previous,android.os.Process.myPid())
        launch()
        runBlocking {assertEquals(21,model.libraryRuntime.currentRepository().records().size);assertEquals(2,model.libraryRuntime.currentRepository().labs().size)}
        assertTrue(UiPrefs(context).conc.calibrate)
        navigate(Destination.CONCENTRATION)
        ui.waitUntil(30_000) { showing("calibration-result") && showing("e2-now-value") }
        ui.onNodeWithTag("calibration-switch").performScrollTo().assertIsOn()
        context.filesDir.resolve("calibration-native-reopen.json").writeText(org.json.JSONObject()
            .put("synthetic_only",true).put("previous_pid",previous).put("new_pid",android.os.Process.myPid())
            .put("sdk",android.os.Build.VERSION.SDK_INT).put("saved_doses",21).put("saved_e2_labs",2)
            .put("real_main_activity",true).put("calibration_recomputed",true).toString())
    }
    /** Actual production destination and database; the relative curve must not reuse E2 calibration. */
    @Test fun savedTestosteroneFormulationUsesActualHistoryInTheProductionDestination() {
        launch()
        val repo = model.libraryRuntime.currentRepository()
        val now = Instant.now()
        val med = MedicationEntity(name="SYNTHETIC ANDROGEL", molecule="T", route="T_ANDROGEL_50", unit="MG", dose_per_intake=50.0,
            container_capacity=1500.0, soon_alert_minutes=15, late_after_minutes=120, site_rotation=false, notifications_on=false, active=true, sort_order=999)
        val id = runBlocking { repo.saveMedication(med, null, net.plainnotes.app.domain.RuleKind.EVERY_N_DAYS, 1,
            listOf(java.time.LocalTime.of(9, 0)), emptySet(), now) }
        runBlocking { repo.transaction { dao ->
            val actual = med.copy(id=id)
            for (hours in listOf(8L, 32L)) dao.record(RecordEntity(medication_id=id,
                taken_utc=now.minusSeconds(hours*3600).toEpochMilli(),taken_zone="UTC",actual_dose=50.0,
                status="ON_TIME",origin="APP",revision=1,config_snapshot=MedicationSnapshot.encode(actual,null)))
        } }
        model.refresh().joinForTest()
        navigate(Destination.CONCENTRATION)
        ui.waitUntil(30_000) { showing("concentration-analyte") }
        ui.onNodeWithText(context.getString(R.string.choice_t)).performClick()
        ui.waitUntil(30_000) { showing("t-relative-chart") }
        ui.onNodeWithTag("t-relative-chart").performScrollTo().assertIsDisplayed()
        ui.onAllNodesWithTag("e2-now-value").assertCountEquals(0)
        ui.onAllNodesWithTag("calibration-switch").assertCountEquals(0)
        ui.onNodeWithTag("t-shape-warning").performScrollTo().assertTextContains(context.getString(R.string.t_shape_warning))
        val rows=runBlocking { repo.records().filter { it.medication_id==id } }
        assertEquals(2,rows.size)
        assertTrue(rows.all { MedicationSnapshot.decode(it.config_snapshot,id)?.route=="T_ANDROGEL_50" })
        context.filesDir.resolve("testosterone-native-pid").writeText(android.os.Process.myPid().toString())
    }
    @Test fun newProcessReopensTestosteroneHistoryWithoutApplyingE2Calibration() {
        val previous=context.filesDir.resolve("testosterone-native-pid").readText().trim().toInt()
        assertNotEquals(previous,android.os.Process.myPid())
        launch();navigate(Destination.CONCENTRATION)
        ui.waitUntil(30_000) { showing("concentration-analyte") }
        ui.onNodeWithText(context.getString(R.string.choice_t)).performClick()
        ui.waitUntil(30_000) { showing("t-relative-chart") }
        ui.onNodeWithTag("t-relative-chart").performScrollTo().assertIsDisplayed()
        ui.onAllNodesWithTag("calibration-switch").assertCountEquals(0)
        context.filesDir.resolve("testosterone-native-reopen.json").writeText(org.json.JSONObject()
            .put("previous_pid",previous).put("new_pid",android.os.Process.myPid())
            .put("real_main_activity",true).put("relative_only",true).put("sdk",android.os.Build.VERSION.SDK_INT).toString())
    }
    private fun kotlinx.coroutines.Job.joinForTest() { runBlocking { join() } }

}
