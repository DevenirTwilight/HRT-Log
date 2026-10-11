package net.plainnotes.app.debug

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Real Android UI instrumentation of the shared production component; synthetic fixtures only. */
class UnifiedConcentrationAndroidTest {
    @get:Rule val ui=createAndroidComposeRule<UnifiedConcentrationTestActivity>()
    private fun await(tag:String)=ui.waitUntil(30_000){ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    @Test fun defaultM2OverlayUnitAndStudyAreDistinct() {
        ui.onNodeWithTag("model-M2").assertIsSelected();await("unified-chart")
        ui.onNodeWithTag("research-not-personal").assertExists()
        ui.onNodeWithTag("model-overlay").performClick()
        ui.onNodeWithTag("unified-chart").performScrollTo().performTouchInput { click(center) }
        await("readout-M1");ui.onNodeWithTag("readout-LEGACY").assertExists();ui.onNodeWithTag("difference-M1").assertExists()
        ui.onNodeWithTag("unit-pmol").performScrollTo().performClick();ui.onNodeWithTag("chart-unit").assertTextEquals("pmol/L")
        // Full-width, wrapped mode controls remain reachable on small screens and large fonts.
        ui.onNodeWithTag("display-STUDY").performScrollTo().assertIsDisplayed().performClick()
        ui.onNodeWithTag("display-STUDY").assertIsSelected();await("study-not-personal")
        ui.onNodeWithTag("tail-warning").assertExists()
    }
    @Test fun modelAndAssumptionsSurviveActivityRecreation() {
        await("unified-chart");ui.onNodeWithTag("model-M1").performClick()
        ui.onNodeWithTag("amplitude").performScrollTo().performTextReplacement("200")
        ui.onNodeWithTag("unit-pmol").performScrollTo().performClick()
        ui.activityRule.scenario.recreate();await("unified-chart")
        ui.onNodeWithTag("model-M1").assertIsSelected();ui.onNodeWithTag("unit-pmol").assertIsSelected()
        ui.onNodeWithTag("amplitude").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("200")))
        for ((requested, configuration) in listOf(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to android.content.res.Configuration.ORIENTATION_LANDSCAPE,
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to android.content.res.Configuration.ORIENTATION_PORTRAIT,
        )) {
            ui.activityRule.scenario.onActivity { it.requestedOrientation = requested }
            ui.waitUntil(30_000) { ui.activity.resources.configuration.orientation == configuration }
            await("unified-chart")
            ui.onNodeWithTag("model-M1").assertIsSelected()
            ui.onNodeWithTag("unit-pmol").assertIsSelected()
            ui.onNodeWithTag("chart-unit").assertTextEquals("pmol/L")
            ui.onNodeWithTag("amplitude").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("200")))
        }
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
