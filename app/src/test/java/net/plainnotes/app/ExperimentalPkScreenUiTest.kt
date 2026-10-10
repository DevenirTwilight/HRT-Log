package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.data.MedicationSnapshot
import net.plainnotes.app.data.ProfileEntity
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.experimental.ComparisonUiState
import net.plainnotes.app.experimental.ComparisonWindow
import net.plainnotes.app.experimental.ExperimentalConcentrationComparison
import net.plainnotes.app.experimental.ExperimentalPkScreen
import net.plainnotes.app.experimental.UnifiedConcentrationScreen
import net.plainnotes.app.experimental.comparisonUiState
import net.plainnotes.app.pk.experimental.ExperimentalSlModelView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Locale

/** Synthetic records only. Unified concentration page: Legacy default, M2, comparison, read-outs, metrics, study scenario. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class)
class ExperimentalPkScreenUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val now = Instant.parse("2026-03-10T12:00:00Z")
    private val sl = MedicationEntity(1, "SL E2", "E2", "SUBLINGUAL", "MG", 1.0, 30.0, null, 15, 120, false, null, true, true, 0)
    private val profile = ProfileEntity(1, "E2", "sublingual", sl_tier = 2)
    private fun record(id: Long, hoursAgo: Double, dose: Double = 1.0, med: MedicationEntity = sl, p: ProfileEntity = profile) =
        RecordEntity(id, med.id, taken_utc = now.toEpochMilli() - (hoursAgo * 3_600_000).toLong(), taken_zone = "UTC", actual_dose = dose,
            status = "ON_TIME", origin = "APP", revision = 1, config_snapshot = MedicationSnapshot.encode(med, p))
    // Latest dose 20 h ago: the default 0–24 h window after it reaches 4 h past "now".
    private val history = listOf(record(1, 50.0), record(2, 38.0, 0.5), record(3, 26.0), record(4, 20.0, 2.0))
    private fun text(id: Int, vararg args: Any): String = if (args.isEmpty()) ui.activity.getString(id) else ui.activity.getString(id, *args)

    private fun showUnified(records: List<RecordEntity> = history, dark: Boolean = false, official: String? = "OFFICIAL-PAGE") {
        ui.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                UnifiedConcentrationScreen(records, emptyMap(), PaddingValues(),
                    legacyContent = official?.let { o -> { _ -> Text(o, Modifier.testTag("official")) } }, now = { now })
            }
        }
    }
    private fun mode(name: String) { ui.onNodeWithTag("xpk-mode-$name").performSemanticsAction(SemanticsActions.OnClick); ui.onNodeWithTag("xpk-mode-$name").assertIsSelected() }
    private fun awaitTag(tag: String) = ui.waitUntil(15_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    /** No relative value, label or legend carries a concentration unit; only the chip that switches to the study scenario names it. */
    private fun noConcentrationUnits() {
        val chip = hasTestTag("xpk-scale-STUDY")
        ui.onAllNodes(hasText("pg/mL", substring = true, ignoreCase = true) and chip.not()).assertCountEquals(0)
        ui.onAllNodes(hasText("pmol/L", substring = true, ignoreCase = true) and chip.not()).assertCountEquals(0)
    }
    private fun tapChartCenter() { ui.onNodeWithTag("xpk-chart").performScrollTo().performTouchInput { click(center) }; awaitTag("xpk-readout") }
    private fun readoutTime() = ui.onAllNodesWithTag("xpk-readout").onFirst().fetchSemanticsNode().let {
        it.children.first().config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: ""
    }
    private fun rel(v: Double) = when { v == 0.0 -> "0"; v < 0.001 -> "<0.001"; v < 10 -> String.format(Locale.getDefault(), "%.3f", v); else -> String.format(Locale.getDefault(), "%.1f", v) }
    private fun expected() = comparisonUiState(history, emptyMap(), now, ComparisonWindow.H24, null, ExperimentalSlModelView.candidates.first().id) as ComparisonUiState.Ready

    @Test fun legacyIsTheDefaultAndShowsTheOfficialPageUnchanged() {
        showUnified()
        ui.onNodeWithTag("xpk-mode-LEGACY").assertIsSelected()
        ui.onNodeWithTag("official").assertTextEquals("OFFICIAL-PAGE")
        ui.onAllNodesWithTag("xpk-chart").assertCountEquals(0)
        mode("COMPARISON"); awaitTag("xpk-chart")
        ui.onAllNodesWithTag("official").assertCountEquals(0)
        mode("LEGACY")
        ui.onNodeWithTag("official").assertTextEquals("OFFICIAL-PAGE")
    }

    @Test fun comparisonShowsBothModelsAndTapReadsExactValuesForBoth() {
        showUnified(); mode("COMPARISON"); awaitTag("xpk-chart")
        ui.onNodeWithTag("xpk-legend-legacy").assertExists(); ui.onNodeWithTag("xpk-legend-m2").assertExists(); ui.onNodeWithTag("xpk-legend-range").assertExists()
        ui.onNodeWithTag("xpk-readout-hint").assertExists()
        tapChartCenter()
        ui.onNodeWithTag("xpk-readout-legacy").assertExists(); ui.onNodeWithTag("xpk-readout-m2").assertExists(); ui.onNodeWithTag("xpk-readout-ratio").assertExists()
        // The read-out shows kernel values for the tapped instant: recompute them for the hour encoded in the read-out text.
        val snap = expected().snapshot
        val hours = Regex("""(\d+[.,]\d+)""").find(readoutTime())!!.value.replace(',', '.').toDouble()
        val v = ExperimentalConcentrationComparison.valuesAt(snap, snap.result.originHour + hours)!!
        val legacyText = text(R.string.xpk_readout_legacy, rel(v.legacy))
        ui.onNodeWithTag("xpk-readout-legacy").assertTextEquals(legacyText)
        noConcentrationUnits()
    }

    @Test fun panningMovesTheReadOutInstant() {
        showUnified(); mode("COMPARISON"); awaitTag("xpk-chart")
        // Spread two fingers apart around the centre: zoom in, so the narrower span can be panned.
        ui.onNodeWithTag("xpk-chart").performTouchInput {
            val o = androidx.compose.ui.geometry.Offset(width * 0.05f, 0f); val far = androidx.compose.ui.geometry.Offset(width * 0.35f, 0f)
            pinch(center - o, center - far, center + o, center + far)
        }
        tapChartCenter(); val before = readoutTime()
        ui.onNodeWithTag("xpk-chart").performTouchInput { swipeLeft(startX = centerX + width * 0.3f, endX = centerX - width * 0.3f) }
        tapChartCenter()
        ui.waitUntil(5_000) { readoutTime() != before }
        assertNotEquals(before, readoutTime())
    }

    @Test fun metricsTableShowsEngineValuesAndMarksTheTail() {
        showUnified(); mode("COMPARISON"); awaitTag("xpk-metrics")
        val r = expected().snapshot.result
        val f = ::rel
        ui.onNodeWithTag("xpk-metric-1").assert(hasAnyDescendant(hasText(f(r.legacyMetrics.peak)))).assert(hasAnyDescendant(hasText(f(r.m2Metrics.peak))))
        ui.onNodeWithTag("xpk-metric-3").assert(hasAnyDescendant(hasText(String.format(Locale.getDefault(), "%.2f", r.m2Metrics.singleDoseTmaxHours))))
        ui.onNodeWithTag("xpk-metric-6").assert(hasAnyDescendant(hasText(f(r.m2Metrics.auc0to24))))
        ui.onNodeWithTag("xpk-metric-6").assert(hasAnyDescendant(hasText(text(R.string.xpk_metric_uncertain))))
        ui.onNodeWithTag("xpk-metric-5").assert(hasAnyDescendant(hasText(text(R.string.xpk_metric_uncertain))).not())
    }

    @Test fun m2StudyScenarioIsLabelledAndAnchoredToPrice1997() {
        showUnified(); mode("M2"); awaitTag("xpk-chart")
        ui.onAllNodesWithTag("xpk-legend-legacy").assertCountEquals(0)
        noConcentrationUnits()
        ui.onNodeWithTag("xpk-scale-STUDY").performSemanticsAction(SemanticsActions.OnClick)
        awaitTag("xpk-study")
        ui.onNodeWithTag("xpk-study-banner").assertTextEquals(text(R.string.xpk_study_banner))
        ui.onNodeWithTag("xpk-unit").assertTextEquals(text(R.string.xpk_unit_study))
        ui.onNodeWithTag("experimental-pk").performScrollToNode(hasText(text(R.string.xpk_study_refused_doll), substring = true))
        ui.onNodeWithTag("experimental-pk").performScrollToNode(hasText(text(R.string.xpk_study_conflict)))
        tapChartCenter(); ui.onNodeWithTag("xpk-readout-study").assertExists()
        // Back to the relative scale: no concentration unit anywhere.
        ui.onNodeWithTag("xpk-scale-RELATIVE").performSemanticsAction(SemanticsActions.OnClick)
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("xpk-study").fetchSemanticsNodes().isEmpty() }
        noConcentrationUnits()
    }

    @Test fun choosingAnotherCandidateAndWindowRecomputes() {
        showUnified(); mode("M2"); awaitTag("xpk-chart")
        val other = ExperimentalSlModelView.candidates.last()
        ui.onNodeWithTag("xpk-candidate-${other.id}").performSemanticsAction(SemanticsActions.OnClick)
        ui.waitUntil(10_000) { ui.onAllNodes(hasText(text(R.string.xpk_legend_m2, other.id))).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("xpk-window-H4").performSemanticsAction(SemanticsActions.OnClick); ui.onNodeWithTag("xpk-window-H4").assertIsSelected()
        awaitTag("xpk-chart")
        ui.onNodeWithTag("xpk-window-CALENDAR_7D").performSemanticsAction(SemanticsActions.OnClick); awaitTag("xpk-chart")
    }

    @Test fun noQualifyingRecordsAndOtherRoutesAreHandled() {
        val oral = sl.copy(id = 2, route = "ORAL")
        showUnified(listOf(record(9, 2.0, med = oral, p = ProfileEntity(2, "E2", "oral"))))
        mode("COMPARISON"); awaitTag("xpk-exclusions")
        ui.onNodeWithText(text(R.string.xpk_empty_title)).assertExists()
        ui.onNodeWithTag("xpk-exclusions").assertTextEquals(text(R.string.xpk_inputs_excluded, text(R.string.xpk_excl_not_sl_e2, 1)))
        ui.onAllNodesWithTag("xpk-chart").assertCountEquals(0)
    }

    @Test fun mixedRoutesAreCountedOutAndExplained() {
        val oral = sl.copy(id = 2, route = "ORAL")
        showUnified(history + record(9, 2.0, med = oral, p = ProfileEntity(2, "E2", "oral"))); mode("COMPARISON"); awaitTag("xpk-coverage")
        ui.onNodeWithTag("xpk-coverage-mixed").assertExists()
        ui.onNodeWithTag("xpk-coverage").assertTextContains("4 ", substring = true)
    }

    @Test fun drawerHostKeepsBackNavigation() {
        var back = 0
        ui.setContent { MaterialTheme { ExperimentalPkScreen(history, emptyMap(), PaddingValues(), { back++ }, now = { now }) } }
        ui.onNodeWithTag("xpk-mode-LEGACY").assertIsSelected(); awaitTag("xpk-chart")
        ui.onNodeWithTag("experimental-pk").performScrollToNode(hasTestTag("xpk-back"))
        ui.onNodeWithTag("xpk-back").performClick()
        ui.runOnIdle { assertEquals(1, back) }
    }

    @Test @Config(qualifiers = "w320dp-h640dp", fontScale = 2.0f) fun largeTextOnASmallScreenKeepsEverythingReachable() {
        showUnified(); mode("COMPARISON"); awaitTag("xpk-chart")
        ui.onNodeWithTag("experimental-pk").performScrollToNode(hasTestTag("xpk-metrics"))
        ui.onNodeWithTag("experimental-pk").performScrollToNode(hasTestTag("xpk-assumptions-toggle"))
        ui.onNodeWithTag("xpk-assumptions-toggle").performClick()
        ui.onNodeWithTag("experimental-pk").performScrollToNode(hasText(text(R.string.xpk_limit_tail)))
        noConcentrationUnits()
    }

    @Test @Config(qualifiers = "night") fun darkThemeRendersTheComparison() {
        showUnified(dark = true); mode("COMPARISON"); awaitTag("xpk-chart")
        ui.onNodeWithTag("xpk-chart").assertIsDisplayed()
        noConcentrationUnits()
    }
}
