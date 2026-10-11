package net.plainnotes.app.debug

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Real Android UI instrumentation of the shared production component; synthetic fixtures only. */
class UnifiedConcentrationAndroidTest {
    @get:Rule val ui=createAndroidComposeRule<UnifiedConcentrationTestActivity>()
    private fun await(tag:String)=ui.waitUntil(30_000){ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    @Test fun originalUiAndModelSelectionUseOneChart() {
        ui.onNodeWithTag("model-M2").assertIsSelected();await("unified-chart")
        ui.onNodeWithTag("research-not-personal").assertExists()
        val context=ui.activity
        for(id in listOf(net.plainnotes.app.R.string.pk_chart_title,net.plainnotes.app.R.string.calib_title,net.plainnotes.app.R.string.pk_model_title))
            ui.onNodeWithText(context.getString(id)).assertExists()
        ui.onAllNodesWithTag("model-overlay").assertCountEquals(0)
        ui.onNodeWithTag("model-M1").performScrollTo().performClick();await("unified-chart")
        ui.onNodeWithTag("model-M1").assertIsSelected()
        ui.onNodeWithText("pmol/L").performScrollTo().performClick()
        ui.onNodeWithTag("unified-chart").performScrollTo().performTouchInput { click(center) }
        ui.waitUntil(10_000){ui.onAllNodes(hasText("  ·  ",substring=true)).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("model-LEGACY").performScrollTo().performClick()
        ui.onNodeWithText(context.getString(net.plainnotes.app.R.string.pk_current)).assertExists()
    }
    @Test fun modelAndOriginalUnitSurviveActivityRecreationAndRotation() {
        await("unified-chart");ui.onNodeWithTag("model-M1").performScrollTo().performClick();await("unified-chart")
        ui.onNodeWithText("pmol/L").performScrollTo().performClick()
        ui.activityRule.scenario.recreate();await("unified-chart")
        ui.onNodeWithTag("model-M1").assertIsSelected()
        org.junit.Assert.assertTrue(ui.onAllNodesWithText("pmol/L").fetchSemanticsNodes().size>=2)
        for ((requested, configuration) in listOf(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to android.content.res.Configuration.ORIENTATION_LANDSCAPE,
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to android.content.res.Configuration.ORIENTATION_PORTRAIT,
        )) {
            ui.activityRule.scenario.onActivity { it.requestedOrientation=requested }
            ui.waitUntil(30_000){ui.activity.resources.configuration.orientation==configuration}
            await("unified-chart");ui.onNodeWithTag("model-M1").assertIsSelected()
            org.junit.Assert.assertTrue(ui.onAllNodesWithText("pmol/L").fetchSemanticsNodes().size>=2)
        }
    }
    @Test fun literatureAmplitudeHeroAndLegacyCalibrationAreIsolatedOnAndroid() {
        await("unified-chart")
        val doses=listOf(-30.0,-20.0,-3.0).map { net.plainnotes.app.pk.experimental.ExperimentalDose(it,1.0) }
        for(model in listOf(net.plainnotes.app.pk.experimental.ResearchModel.M2,net.plainnotes.app.pk.experimental.ResearchModel.M1)) {
            ui.onNodeWithTag("model-${model.name}").performScrollTo().performClick();await("unified-chart")
            val c=net.plainnotes.app.pk.experimental.ResearchDisplayProtocol.candidate(model)
            val a=net.plainnotes.app.pk.experimental.ResearchDisplayProtocol.anchor(model,c)
            val value=kotlin.math.round(net.plainnotes.app.pk.experimental.ResearchSublingualV01.studyScenario(0.0,doses,c,a)).toInt().toString()
            ui.waitUntil(10_000){ui.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText(ui.activity.getString(net.plainnotes.app.R.string.calib_enable)).performScrollTo().performClick()
            ui.runOnUiThread { ui.activity.syntheticLabPgMl=9000.0 }
            ui.waitForIdle();ui.onNodeWithText(value).assertExists()
            ui.onNodeWithTag("research-peaks").assertExists()
            ui.runOnUiThread { ui.activity.syntheticLabPgMl=150.0 }
        }
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
                net.plainnotes.app.pk.experimental.ResearchSublingualV01.candidates.first().id,
                net.plainnotes.app.pk.experimental.ResearchM1Catalog.candidates.first().id,
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
