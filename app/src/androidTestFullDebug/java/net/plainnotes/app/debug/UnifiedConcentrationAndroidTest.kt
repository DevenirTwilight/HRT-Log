package net.plainnotes.app.debug
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.core.app.ApplicationProvider
import android.content.Intent
import androidx.compose.runtime.Recomposer
import org.junit.Before
import org.junit.After
import net.plainnotes.app.R
import net.plainnotes.app.pk.experimental.*
import org.junit.Rule
import org.junit.Test
class UnifiedConcentrationAndroidTest {
    // Keep production's lifecycle/main-thread recomposer instead of Compose's unconfined test loop.
    @get:Rule val ui = AndroidComposeTestRule(
        ActivityScenarioRule<UnifiedConcentrationTestActivity>(Intent(
            ApplicationProvider.getApplicationContext(), UnifiedConcentrationTestActivity::class.java
        ).putExtra(UnifiedConcentrationTestActivity.REAL_WINDOW_RECOMPOSER, true))
    ) { rule ->
        lateinit var activity: UnifiedConcentrationTestActivity
        rule.scenario.onActivity { activity = it }
        activity
    }
    private val windowIdle = object : IdlingResource {
        override val isIdleNow: Boolean
            get() = ui.activity.instrumentationRecomposer?.currentState?.value?.let { it <= Recomposer.State.Idle } ?: true
    }
    @Before fun registerRealWindowIdle() { ui.registerIdlingResource(windowIdle) }
    @After fun unregisterRealWindowIdle() { ui.unregisterIdlingResource(windowIdle) }
    private fun await(tag:String)=ui.waitUntil(30_000){ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun noSelector() { for(choice in ResearchModel.entries)ui.onAllNodesWithTag("model-${choice.name}").assertCountEquals(0) }
    // UI-12 (REQUIREMENTS §162): one estimate card, one "E2 curve", the research note under "Model and literature", readout card instead of the "  ·  " bubble.
    @Test fun unifiedUiHasOneEstimateAndOneChart() {
        await("e2-chart");noSelector();await("e2-estimate-card");ui.onAllNodesWithTag("research-not-personal").assertCountEquals(0)
        for(id in listOf(R.string.ui12_curve_title,R.string.calib_title,R.string.pk_model_title))ui.onNodeWithText(ui.activity.getString(id)).assertExists()
        ui.onNodeWithTag("calibration-switch").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithText("pmol/L").performScrollTo().performClick()
        ui.onNodeWithTag("e2-chart").performScrollTo().performTouchInput { click(center) }
        ui.waitUntil(10_000){ui.onAllNodesWithTag("chart-readout").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun m2AndOriginalUnitSurviveRecreationAndRotation() {
        await("e2-chart");ui.onNodeWithText("pmol/L").performScrollTo().performClick()
        ui.activityRule.scenario.recreate();await("e2-chart");noSelector()
        ui.onNode(hasText("pmol/L") and isSelected()).assertExists()  // D-1: no hero unit label; the selection survives
        for((requested,configuration) in listOf(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to android.content.res.Configuration.ORIENTATION_LANDSCAPE,
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to android.content.res.Configuration.ORIENTATION_PORTRAIT)) {
            ui.activityRule.scenario.onActivity { it.requestedOrientation=requested }
            ui.waitUntil(30_000){ui.activity.resources.configuration.orientation==configuration};await("e2-chart");noSelector()
            ui.onNode(hasText("pmol/L") and isSelected()).assertExists()  // D-1: no hero unit label; the selection survives
        }
    }
    @Test fun observedLabsCannotCalibrateM2OnAndroid() {
        await("e2-chart")
        val value=kotlin.math.round(ResearchSublingualV01.studyScenario(0.0,listOf(-30.0,-20.0,-3.0).map{ExperimentalDose(it,1.0)},ResearchDisplayProtocol.candidate(ResearchModel.M2),ResearchDisplayProtocol.anchor(ResearchModel.M2))).toInt().toString()
        // D-1: no on-screen current number; the scenario is read from the preview the screen was given.
        fun scenario()=ui.activity.lastPreview!!.let{p->kotlin.math.round(p.valueAt(p.asOfH!!)!!).toInt().toString()}
        org.junit.Assert.assertEquals(value,scenario());ui.onAllNodesWithText(value).assertCountEquals(0)
        val before=ui.activity.lastPreview!!.values.copyOf()
        ui.runOnUiThread { ui.activity.syntheticLabPgMl=9000.0;ui.activity.syntheticHistoryRevision++ }
        await("e2-chart");ui.waitForIdle();org.junit.Assert.assertEquals(value,scenario())
        org.junit.Assert.assertArrayEquals(before,ui.activity.lastPreview!!.values,0.0)
        // UI-12: labs are dots on the total (no separate list); calibration stays unavailable with sublingual doses.
        ui.onAllNodesWithTag("m2-labs-separate").assertCountEquals(0)
        ui.onNodeWithTag("calibration-unavailable-sl").performScrollTo().assertExists()
        ui.onNodeWithTag("calibration-switch").assertIsNotEnabled()
    }
    @Test fun mixedRoutesAreOneTotalAndOnlyOtherRoutesUseCompatibilityCalibration() {
        await("e2-chart");ui.runOnUiThread { ui.activity.syntheticExtraRoute="ORAL" }
        ui.waitUntil(30_000){ui.onAllNodesWithText(ui.activity.getString(R.string.ui12_label_total)).fetchSemanticsNodes().isNotEmpty()}
        ui.onAllNodesWithText(ui.activity.getString(R.string.m2_partial_scope)).assertCountEquals(0)
        ui.runOnUiThread { ui.activity.syntheticSublingualEnabled=false }
        ui.waitUntil(30_000){ui.onAllNodesWithTag("calibration-switch").fetchSemanticsNodes().isEmpty() && ui.onAllNodesWithText(ui.activity.getString(R.string.calib_enable)).fetchSemanticsNodes().isNotEmpty()}
        noSelector()
    }
    @Test fun actualProductPreviewReportsPeakAccuracyAndAndroidPerformance() {
        val now=600000.0
        val doses=(0 until 60).map { i -> net.plainnotes.app.pk.DoseEvent("synthetic-$i",net.plainnotes.app.pk.Route.SUBLINGUAL,
            now-i*12.0,1.0,net.plainnotes.app.pk.Ester.E2,70.0,net.plainnotes.app.pk.DoseExtras(sublingualTier=2.0)) }
        val before=android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(before)
        val started=android.os.SystemClock.elapsedRealtimeNanos()
        val p=net.plainnotes.app.experimental.ConcentrationPreview.compute(
            net.plainnotes.app.conc.experimental.SlAdapterAudit(doses,emptyMap()),net.plainnotes.app.pk.experimental.ResearchModel.M2,now)
        val elapsed=(android.os.SystemClock.elapsedRealtimeNanos()-started)/1e6
        val after=android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(after)
        org.junit.Assert.assertEquals(net.plainnotes.app.pk.experimental.ResearchDisplayProtocol.m2Id,p.candidateId)
        org.junit.Assert.assertEquals(p.historyWindowPeak!!.pgMl,p.values.max(),1e-5)
        org.junit.Assert.assertTrue(p.timeH.size in 5041..12000)
        val report=org.json.JSONObject().put("fixture","synthetic-60-doses-over-30-days")
            .put("protocol",net.plainnotes.app.pk.experimental.ResearchDisplayProtocol.version).put("candidate",p.candidateId)
            .put("chart_points",p.timeH.size).put("compute_ms",elapsed).put("sdk",android.os.Build.VERSION.SDK_INT)
            .put("process_pss_before_kib",before.totalPss).put("process_pss_after_kib",after.totalPss)
            .put("history_peak_pgml",p.historyWindowPeak!!.pgMl).put("chart_peak_pgml",p.values.max())
            .put("measurement_limit","Synthetic Debug emulator process PSS, not hardware/FPS/Release or clinical accuracy")
        android.util.Log.i("Pr19DisplayPerf","PR19_DISPLAY_PERFORMANCE_JSON="+report.toString())
    }
    @Test fun thirtyDaySyntheticHistoryReportsAndroidComputeAndProcessMemory() {
        val origin=600_000.0
        val doses=(0 until 60).map { i -> net.plainnotes.app.pk.DoseEvent("synthetic-$i",net.plainnotes.app.pk.Route.SUBLINGUAL,
            origin-i*12.0,1.0,net.plainnotes.app.pk.Ester.E2,70.0,net.plainnotes.app.pk.DoseExtras(sublingualTier=2.0)) }
        val before=android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(before)
        val start=android.os.SystemClock.elapsedRealtimeNanos()
        val result=kotlinx.coroutines.runBlocking { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            net.plainnotes.app.pk.experimental.UnifiedModelComparison.compute(doses,origin-168.0,origin,origin,
                net.plainnotes.app.pk.experimental.ResearchDisplayProtocol.m2Id,
                net.plainnotes.app.pk.experimental.ResearchDisplayProtocol.m1Id,
                net.plainnotes.app.pk.experimental.ResearchDisplay.CONDITIONAL)!!
        } }
        val elapsed=(android.os.SystemClock.elapsedRealtimeNanos()-start)/1_000_000.0
        val after=android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(after)
        org.junit.Assert.assertEquals(60,result.base.doses.size)
        org.junit.Assert.assertTrue(result.base.timeHours.size<=2001)
        org.junit.Assert.assertTrue(result.series.values.all { y -> y.all { it.isFinite() && it>=25.0 } })
        org.junit.Assert.assertTrue(before.totalPss>0 && after.totalPss>0)
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val report = org.json.JSONObject()
            .put("fixture","synthetic-60-doses-over-30-days").put("sdk",android.os.Build.VERSION.SDK_INT)
            .put("abis",org.json.JSONArray(android.os.Build.SUPPORTED_ABIS.toList())).put("chart_points",result.base.timeHours.size)
            .put("compute_ms",elapsed).put("process_pss_before_kib",before.totalPss).put("process_pss_after_kib",after.totalPss)
            .put("native_pss_after_kib",after.nativePss).put("dalvik_pss_after_kib",after.dalvikPss)
            .put("measurement_limit","One Debug emulator execution; process PSS includes UI, instrumentation and other allocations. Not hardware, FPS, Release performance or clinical accuracy.")
        java.io.File(context.cacheDir,"unified-device-performance.json").writeText(report.toString(2)+"\n")
        // AGP may uninstall the target after connected tests. A dedicated synthetic-only log survives cleanup.
        android.util.Log.i("UnifiedDevicePerf", "UNIFIED_DEVICE_PERFORMANCE_JSON=" + report.toString())
    }

}
