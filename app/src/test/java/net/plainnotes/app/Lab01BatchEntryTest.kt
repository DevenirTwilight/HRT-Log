package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.LabValueEntity
import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LAB-01 A, synthetic input only: date/time and laboratory once, one ordinary result per filled row; blank rows ignored, a
 * repeated test blocks saving, presets add only missing rows, progesterone keeps the single dialog's method choice, and a
 * failed save keeps everything typed.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class Lab01BatchEntryTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private fun s(id: Int, vararg a: Any) = ui.activity.getString(id, *a)
    private val saved = mutableListOf<Pair<List<LabValueEntity>, Boolean>>()
    private var failed by mutableStateOf(false)
    private fun show() = ui.setContent { NotesTheme(ThemeMode.LIGHT) { LabBatchDialog({}, { v, e -> saved += v to e }, failed = failed) } }
    private fun rows() = ui.onAllNodes(tagPrefix("lab-batch-row:")).fetchSemanticsNodes().size
    private fun type(tag: String, text: String) = ui.onNodeWithTag(tag).performScrollTo().performTextInput(text)

    @Test fun sharedTimeAndLabOneResultPerFilledRowBlankRowsIgnored() {
        show()
        type("lab-batch-lab", "Synthetic lab")
        ui.onNodeWithTag("lab-preset:HORMONES").performClick()
        assertEquals(6, rows())
        ui.onNodeWithTag("lab-batch-save").assertIsNotEnabled()
        type("lab-batch-value:0", "4.5"); type("lab-batch-value:2", "150")
        ui.onNodeWithTag("lab-batch-save").assertIsEnabled().performClick()
        val (values, estimate) = saved.single()
        assertFalse(estimate)
        assertEquals(listOf("FSH", "E2"), values.map { it.analyte_code })
        assertEquals(listOf(4.5, 150.0), values.map { it.value })
        assertEquals(1, values.map { Triple(it.sampled_utc, it.sampled_zone, it.laboratory) }.toSet().size)
        assertEquals("Synthetic lab", values.first().laboratory)
        assertTrue(values.all { it.id == 0L && it.reference_unit == null })
    }

    @Test fun presetsAddOnlyMissingRows() {
        show()
        type("lab-batch-value:0", "150") // the first row is E2 and no longer untouched
        ui.onNodeWithTag("lab-preset:HORMONES").performClick()
        assertEquals(6, rows())
        ui.onNodeWithTag("lab-preset:HORMONES").performClick()
        assertEquals(6, rows())
        ui.onNodeWithTag("lab-preset:LIVER").performClick(); assertEquals(9, rows())
        ui.onNodeWithTag("lab-preset:KIDNEY").performClick(); assertEquals(11, rows())
    }

    @Test fun anUntouchedFirstRowIsReplacedByAPreset() {
        show()
        ui.onNodeWithTag("lab-preset:LIVER").performClick()
        assertEquals(3, rows())
        type("lab-batch-value:0", "20"); type("lab-batch-value:1", "21"); type("lab-batch-value:2", "22")
        ui.onNodeWithTag("lab-batch-save").performClick()
        assertEquals(listOf("ALT", "AST", "GGT"), saved.single().first.map { it.analyte_code })
    }

    @Test fun aRepeatedTestIsMarkedAndBlocksSaving() {
        show()
        ui.onNodeWithTag("lab-batch-add-row").performScrollTo().performClick()
        type("lab-batch-value:0", "150")
        ui.onNodeWithTag("lab-batch-save").assertIsEnabled() // the second E2 row is still blank
        type("lab-batch-value:1", "160")
        ui.onNodeWithTag("lab-batch-duplicate:1").assertTextEquals(s(R.string.lab_batch_duplicate))
        ui.onNodeWithTag("lab-batch-duplicate:0").assertExists()
        ui.onNodeWithTag("lab-batch-save").assertIsNotEnabled()
        ui.onNodeWithTag("lab-batch-remove:1").performScrollTo().performClick()
        ui.onNodeWithTag("lab-batch-duplicate:0").assertDoesNotExist()
        ui.onNodeWithTag("lab-batch-save").assertIsEnabled()
    }

    @Test fun progesteroneKeepsTheMethodChoiceAndRangeUsesTheRowUnit() {
        show()
        ui.onNodeWithTag("lab-preset:HORMONES").performClick()
        ui.onNodeWithText(s(R.string.lab_p4_method_ms)).performScrollTo().performClick()
        type("lab-batch-value:3", "0.4")
        ui.onNodeWithTag("lab-batch-range-toggle:3").performScrollTo().performClick()
        type("lab-batch-lo:3", "0.1"); type("lab-batch-hi:3", "0.9")
        ui.onNodeWithTag("lab-batch-save").performClick()
        val p4 = saved.single().first.single()
        assertEquals("P4_MS", p4.analyte_code); assertEquals("ng/mL", p4.unit)
        assertEquals(0.1, p4.reference_lower); assertEquals(0.9, p4.reference_upper); assertEquals("ng/mL", p4.reference_unit)
    }

    @Test fun anInvalidRangeOpensItselfAndBlocksSaving() {
        show()
        type("lab-batch-value:0", "150")
        ui.onNodeWithTag("lab-batch-range-toggle:0").performScrollTo().performClick()
        type("lab-batch-lo:0", "200"); type("lab-batch-hi:0", "100")
        ui.onNodeWithTag("lab-batch-save").assertIsNotEnabled()
        ui.onNodeWithTag("lab-batch-range-toggle:0").performScrollTo().performClick() // stays open while invalid
        ui.onNodeWithTag("lab-batch-lo:0").assertExists()
    }

    @Test fun estimateChoiceIsPassedOnAndAFailureKeepsTheForm() {
        show()
        type("lab-batch-lab", "Synthetic lab"); type("lab-batch-value:0", "150")
        ui.onNodeWithTag("lab-batch-estimate").performScrollTo().performClick()
        ui.onNodeWithTag("lab-batch-save").performClick()
        assertTrue(saved.single().second)
        failed = true; ui.waitForIdle()
        ui.onNodeWithTag("lab-batch-failed").assertTextEquals(s(R.string.lab_batch_failed))
        ui.onNodeWithTag("lab-batch-value:0").assertTextContains("150")
        ui.onNodeWithTag("lab-batch-lab").assertTextContains("Synthetic lab")
        ui.onNodeWithTag("lab-batch-save").assertIsEnabled()
    }
}

internal fun tagPrefix(prefix: String) = SemanticsMatcher("testTag starts with $prefix") { n ->
    n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }?.startsWith(prefix) == true }
