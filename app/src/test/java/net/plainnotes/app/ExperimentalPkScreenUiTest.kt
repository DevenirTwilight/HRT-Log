package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.data.MedicationSnapshot
import net.plainnotes.app.data.ProfileEntity
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.experimental.ExperimentalPkScreen
import net.plainnotes.app.pk.experimental.ExperimentalSlModelView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Synthetic records only. Opt-in experimental page: modes, candidates, empty state and unit labelling. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class)
class ExperimentalPkScreenUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val now = Instant.parse("2026-03-10T12:00:00Z")
    private val sl = MedicationEntity(1, "SL E2", "E2", "SUBLINGUAL", "MG", 1.0, 30.0, null, 15, 120, false, null, true, true, 0)
    private val profile = ProfileEntity(1, "E2", "sublingual", sl_tier = 2)
    private fun record(id: Long, hoursAgo: Double, dose: Double = 1.0, med: MedicationEntity = sl, p: ProfileEntity = profile) =
        RecordEntity(id, med.id, taken_utc = now.toEpochMilli() - (hoursAgo * 3_600_000).toLong(), taken_zone = "UTC", actual_dose = dose,
            status = "ON_TIME", origin = "APP", revision = 1, config_snapshot = MedicationSnapshot.encode(med, p))
    private val history = listOf(record(1, 40.0), record(2, 28.0, 0.5), record(3, 16.0), record(4, 3.0, 2.0))
    private fun text(id: Int, vararg args: Any): String = if (args.isEmpty()) ui.activity.getString(id) else ui.activity.getString(id, *args)

    private fun show(records: List<RecordEntity>, dark: Boolean = false, onBack: (() -> Unit)? = null) {
        ui.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                ExperimentalPkScreen(records, emptyMap(), PaddingValues(), onBack, now = { now })
            }
        }
    }
    private fun awaitTag(tag: String) = ui.waitUntil(10_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun shown(id: Int, vararg args: Any) = ui.onNodeWithTag("experimental-pk").performScrollToNode(hasText(text(id, *args))).let { ui.onNodeWithText(text(id, *args)).assertExists() }
    private fun mode(name: String) { ui.onNodeWithTag("xpk-mode-$name").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); ui.onNodeWithTag("xpk-mode-$name").assertIsSelected() }
    private fun noConcentrationUnits() {
        ui.onAllNodes(hasText("pg/mL", substring = true, ignoreCase = true)).assertCountEquals(0)
        ui.onAllNodes(hasText("pmol/L", substring = true, ignoreCase = true)).assertCountEquals(0)
    }

    @Test fun legacyIsDefaultAndModesSwitchBetweenLegacyM2AndComparison() {
        show(history)
        awaitTag("xpk-chart")
        shown(R.string.xpk_warning)
        shown(R.string.xpk_unit)
        ui.onNodeWithTag("xpk-legend-legacy").assertExists()
        ui.onAllNodesWithTag("xpk-legend-m2").assertCountEquals(0)
        ui.onAllNodesWithTag("xpk-candidate").assertCountEquals(0)

        mode("M2")
        ui.onAllNodesWithTag("xpk-legend-legacy").assertCountEquals(0)
        ui.onNodeWithTag("xpk-legend-m2").assertTextEquals(text(R.string.xpk_legend_m2, ExperimentalSlModelView.candidates.first().id))
        ui.onNodeWithTag("xpk-legend-range").assertExists()
        ui.onNodeWithTag("xpk-legend-tail").assertExists()
        shown(R.string.xpk_candidate_order)

        mode("COMPARISON")
        ui.onNodeWithTag("xpk-legend-legacy").assertExists()
        ui.onNodeWithTag("xpk-legend-m2").assertExists()
        shown(R.string.xpk_inputs_used, 4, 0)
        shown(R.string.xpk_inputs_none_excluded)
        noConcentrationUnits()
    }

    @Test fun choosingAnotherCandidateShowsItsIdAndAssumptions() {
        show(history)
        awaitTag("xpk-chart")
        mode("M2")
        val other = ExperimentalSlModelView.candidates.last()
        ui.onNodeWithTag("xpk-candidate-${other.id}").performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onAllNodes(hasText(text(R.string.xpk_legend_m2, other.id))).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("xpk-candidate-${other.id}").assertIsSelected()
        ui.onNodeWithTag("xpk-candidate-${ExperimentalSlModelView.candidates.first().id}").assertIsNotSelected()
        shown(R.string.xpk_candidate_price, String.format(java.util.Locale.getDefault(), "%.3g", other.assumedPriceBaselinePgMl))
        ui.onAllNodes(hasText(text(R.string.xpk_legend_m2, ExperimentalSlModelView.candidates.first().id))).assertCountEquals(0)
        noConcentrationUnits()
    }

    @Test fun noQualifyingRecordsGivesAClearMessageAndCounts() {
        val oral = sl.copy(id = 2, route = "ORAL")
        show(listOf(record(9, 2.0, med = oral, p = ProfileEntity(2, "E2", "oral"))))
        awaitTag("xpk-exclusions")
        shown(R.string.xpk_empty_title)
        shown(R.string.xpk_empty_body)
        ui.onNodeWithTag("xpk-exclusions").assertTextEquals(text(R.string.xpk_inputs_excluded, text(R.string.xpk_excl_not_sl_e2, 1)))
        ui.onAllNodesWithTag("xpk-chart").assertCountEquals(0)
        noConcentrationUnits()
    }

    @Test fun backReturnsToTheConcentrationPage() {
        var back = 0
        show(history, onBack = { back++ })
        awaitTag("xpk-chart")
        ui.onNodeWithTag("experimental-pk").performScrollToNode(hasTestTag("xpk-back"))
        ui.onNodeWithTag("xpk-back").performClick()
        ui.runOnIdle { assertEquals(1, back) }
    }

    @Test @Config(qualifiers = "w320dp-h640dp", fontScale = 2.0f) fun largeTextOnASmallScreenKeepsEverythingReachable() {
        show(history)
        awaitTag("xpk-chart")
        mode("COMPARISON")
        shown(R.string.xpk_legend_range)
        shown(R.string.xpk_limit_tail)
        shown(R.string.xpk_version, net.plainnotes.app.pk.experimental.ResearchSublingualV01.version)
        noConcentrationUnits()
    }

    @Test @Config(qualifiers = "night") fun darkThemeRendersTheComparison() {
        show(history, dark = true)
        awaitTag("xpk-chart")
        mode("COMPARISON")
        ui.onNodeWithTag("xpk-chart").assertIsDisplayed()
        shown(R.string.xpk_scale_note)
        noConcentrationUnits()
    }
}
