package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.LabPanelEntity
import net.plainnotes.app.data.LabValueEntity
import net.plainnotes.app.ui.*
import net.plainnotes.app.domain.LabCatalog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class LabUi03HomeTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val at = 1773131400000L
    private fun result(id: Long, code: String = "E2", panel: Long? = null) = LabValueEntity(id, code, 120.0,
        analyte(code).unitOptions.firstOrNull() ?: "unknown", at - id * 1000, "UTC", panel_id = panel)
    private fun scroll(tag: String) = ui.onNodeWithTag("labs-home").performScrollToNode(hasTestTag(tag))

    @Test fun formalSingletonAndLegacySingletonShareHomeCardsAndKeepOriginalIdentity() {
        val original = listOf(result(1), result(2, panel = 5))
        val panel = LabPanelEntity(5, "00000000-0000-4000-8000-000000000005", original[1].sampled_utc, "UTC", null, null, 1)
        var opened: LabPanelEntity? = null
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { LabsScreen(original, emptyList(), {}, {}, PaddingValues(), panels = listOf(panel), onPanel = { opened = it }) } }
        scroll("lab-group:${original[0].sampled_utc}:")
        ui.onNodeWithTag("lab-group:${original[0].sampled_utc}:").assertExists()
        ui.onNodeWithTag("lab-home-status:1").assertTextEquals(ui.activity.getString(R.string.lab_home_range_missing))
        scroll("lab-panel:5")
        ui.onNodeWithTag("lab-panel-open").performClick()
        assertEquals(panel, opened)
        assertNull(original[0].panel_id)
        assertEquals(5L, original[1].panel_id)
    }

    @Test fun expansionExposesEveryOriginalResultAndDeleteUsesOriginalRow() {
        val original = (1L..6L).map { result(it).copy(sampled_utc = at, analyte_code = listOf("E2", "T", "PRL", "LH", "FSH", "SHBG")[(it - 1).toInt()]) }
        var deleted: LabValueEntity? = null
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { LabsScreen(original, emptyList(), {}, { deleted = it }, PaddingValues()) } }
        scroll("lab-home-expand:1")
        ui.onNodeWithTag("lab-home-expand:1").performClick()
        scroll("lab-home-more:6")
        ui.onNodeWithTag("lab-home-more:6").performClick()
        ui.onNodeWithText(ui.activity.getString(R.string.remove)).performClick()
        assertEquals(original.last(), deleted)
        assertTrue(original.all { it.panel_id == null })
    }

    @Test fun metricFilterRetainsUnifiedCardsAndExistingTrend() {
        val original = listOf(result(1), result(2))
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { LabsScreen(original, emptyList(), {}, {}, PaddingValues()) } }
        ui.onNodeWithTag("labs-home").performScrollToNode(hasText(ui.activity.getString(R.string.lab_e2)))
        ui.onNodeWithText(ui.activity.getString(R.string.lab_e2)).performClick()
        ui.onNodeWithText(ui.activity.getString(R.string.lab_trend, ui.activity.getString(R.string.lab_e2))).assertExists()
        scroll("lab-group:${original[0].sampled_utc}:")
        ui.onNodeWithTag("lab-group:${original[0].sampled_utc}:").assertExists()
    }

    @Test fun filtersAndNoResultsDoNotInvokeWritesAndBatchActionUsesExistingCallback() {
        var writes = 0; var create = 0
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { LabsScreen(listOf(result(1)), emptyList(), { if (it == null) create++ else writes++ }, { writes++ }, PaddingValues()) } }
        ui.onNodeWithTag("labs-home-add").performClick()
        assertEquals(1, create)
        ui.onNodeWithTag("labs-home-search-toggle").performClick()
        ui.onNodeWithTag("labs-home-search").performTextInput("nonexistent")
        ui.onNodeWithTag("labs-home-no-results").assertExists()
        assertEquals(0, writes)
        ui.onNodeWithTag("labs-home-search").performTextClearance()
        ui.onNodeWithTag("lab-category:LIVER").performScrollTo().performClick()
        ui.onNodeWithTag("labs-home-no-results").assertExists()
        assertEquals(0, writes)
    }

    @Test fun reviewedCatalogCategoriesCoverEveryStableCodeWithoutGuessingAliases() {
        assertEquals(45, LabCatalog.all.size)
        LabCatalog.all.forEach { definition ->
            val expected = if (definition.category.name == "GLUCOSE_METABOLISM") "METABOLISM" else definition.category.name
            assertEquals(definition.code, expected, labHomeCategory(definition.code).name)
        }
        assertEquals(LabHomeCategory.LIPIDS, labHomeCategory("HDL_C"))
        assertEquals(LabHomeCategory.METABOLISM, labHomeCategory("GLU_FASTING"))
        listOf("unrecognized", "HGB", "BILI", "GLU_FAST").forEach {
            assertEquals(it, LabHomeCategory.OTHER, labHomeCategory(it))
        }
    }

    @Test fun newMetricFilterUsesCanonicalTrendUnitAndKeepsOriginalRawCard() {
        val original = listOf(result(1, "TG"), result(2, "TG")).map { it.copy(value = 3.05, unit = "g/L") }
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { LabsScreen(original, emptyList(), {}, {}, PaddingValues()) } }
        ui.onNodeWithTag("lab-filter:TG").performScrollTo().performClick()
        ui.onNodeWithTag("lab-home-trend").performScrollTo().performTouchInput { click(center) }
        ui.waitForIdle()
        ui.onNodeWithText("305 mg/dL", substring = true).assertExists()
        scroll("lab-group-row:1")
        ui.onNodeWithTag("lab-group-row:1").assert(hasContentDescription("3.05", substring = true))
        assertTrue(original.all { it.value == 3.05 && it.unit == "g/L" && it.panel_id == null })
    }

    @Test fun tinyActualMetricTooltipDoesNotRoundNonzeroResultToZero() {
        val original = listOf(result(1, "NEUT_ABS"), result(2, "NEUT_ABS")).map { it.copy(value = 0.0004, unit = "10³/mm³") }
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { LabsScreen(original, emptyList(), {}, {}, PaddingValues()) } }
        ui.onNodeWithTag("lab-filter:NEUT_ABS").performScrollTo().performClick()
        ui.onNodeWithTag("lab-home-trend").performScrollTo().performTouchInput { click(center) }
        ui.waitForIdle()
        ui.onNodeWithText("4.0E-4 10⁹/L", substring = true).assertExists()
        assertTrue(original.all { it.value == 0.0004 && it.unit == "10³/mm³" })
    }

    @Test fun tinyPositiveResultAndItsReportBoundsNeverDisplayOrSpeakAsMeasuredZero() {
        val tiny = result(1, "NEUT_ABS").copy(value = 0.0004, unit = "10³/mm³",
            reference_lower = 0.0001, reference_upper = 0.0005, reference_unit = "10³/mm³")
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { LabsScreen(listOf(tiny), emptyList(), {}, {}, PaddingValues()) } }
        scroll("lab-group-row:1")
        ui.onNodeWithTag("lab-group-row:1").assert(hasContentDescription("4.0E-4", substring = true)).performClick()
        ui.onNodeWithText("4.0E-4 10³/mm³").assertExists()
        ui.onNodeWithText(ui.activity.getString(R.string.lab_range_value, "1.0E-4", "5.0E-4", "10³/mm³")).assertExists()
        assertEquals(0.0004, tiny.value, 0.0)
    }

    @Test @Config(qualifiers = "fr-rFR") fun tinyReportFallbackUsesLocaleWithoutChangingRegularLabFormatting() {
        ui.setContent { NotesTheme(ThemeMode.LIGHT) {
            androidx.compose.material3.Text(labReportNumber(0.0004))
            androidx.compose.material3.Text(labReportNumber(0.0005))
            androidx.compose.material3.Text(labReportNumber(0.001))
            androidx.compose.material3.Text(labReportNumber(13.8))
            androidx.compose.material3.Text(labReportNumber(0.0))
        } }
        ui.onNodeWithText("4,0E-4").assertExists()
        ui.onNodeWithText("5,0E-4").assertExists()
        ui.onNodeWithText("0,001").assertExists()
        ui.onNodeWithText("13,8").assertExists()
        ui.onNodeWithText("0").assertExists()
    }

    @Test fun oneSidedRangesUnknownUnitsAndFlagsAreNeverClinicalJudgements() {
        val x = result(1)
        assertEquals(R.string.lab_home_range_missing, labHomeRangeStatus(x))
        assertEquals(R.string.lab_home_range_unknown, labHomeRangeStatus(x.copy(reference_upper = 100.0, reference_unit = null)))
        assertEquals(R.string.lab_home_range_high, labHomeRangeStatus(x.copy(reference_upper = 100.0, reference_unit = x.unit)))
        assertEquals(R.string.lab_home_range_within, labHomeRangeStatus(x.copy(reference_lower = 100.0, reference_unit = x.unit)))
        assertEquals(R.string.lab_home_range_unknown, labHomeRangeStatus(x.copy(reference_upper = 100.0, reference_unit = "unrecognized")))
    }
}
