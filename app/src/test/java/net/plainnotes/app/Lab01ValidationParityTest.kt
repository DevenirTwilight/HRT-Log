package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LAB-01 A: the batch form accepts exactly what the single-result dialog accepts. The same value/range cases run through
 * both; each must enable or disable Save the same way.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk = [35])
class Lab01ValidationParityTest(private val value: String, private val lo: String, private val hi: String, private val ok: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "value={0} lo={1} hi={2} ok={3}")
        fun cases() = listOf(
            arrayOf<Any>("", "", "", false), arrayOf<Any>("abc", "", "", false), arrayOf<Any>("0", "", "", false), arrayOf<Any>("-3", "", "", false),
            arrayOf<Any>("Infinity", "", "", false), arrayOf<Any>("150", "", "", true), arrayOf<Any>("0,5", "", "", true), arrayOf<Any>("150", "100", "", true),
            arrayOf<Any>("150", "", "200", true), arrayOf<Any>("150", "200", "100", false), arrayOf<Any>("150", "100", "100", true), arrayOf<Any>("150", "x", "", false),
        )
    }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private fun s(id: Int) = ui.activity.getString(id)
    private fun SemanticsNodeInteraction.input(t: String) = apply { if (t.isNotEmpty()) performScrollTo().performTextInput(t) }

    @Test fun singleDialog() {
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { LabDialog(null, {}, {}) } }
        ui.onNode(hasSetTextAction() and hasText(s(R.string.lab_value))).input(value)
        ui.onNode(hasSetTextAction() and hasText(s(R.string.lab_range_low))).input(lo)
        ui.onNode(hasSetTextAction() and hasText(s(R.string.lab_range_high))).input(hi)
        ui.onNode(hasClickAction() and hasText(s(R.string.save))).apply { if (ok) assertIsEnabled() else assertIsNotEnabled() }
    }

    @Test fun batchForm() {
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { LabBatchDialog({}, { _, _ -> }) } }
        ui.onNodeWithTag("lab-batch-value:0").input(value)
        ui.onNodeWithTag("lab-batch-range-toggle:0").performScrollTo().performClick()
        ui.onNodeWithTag("lab-batch-lo:0").input(lo); ui.onNodeWithTag("lab-batch-hi:0").input(hi)
        ui.onNodeWithTag("lab-batch-save").apply { if (ok) assertIsEnabled() else assertIsNotEnabled() }
    }
}
