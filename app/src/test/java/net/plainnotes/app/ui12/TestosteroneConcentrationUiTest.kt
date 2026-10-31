package net.plainnotes.app.ui12

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.NotesState
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import net.plainnotes.app.pk.CalibrationMode
import net.plainnotes.app.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class TestosteroneConcentrationUiTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr").flatMap { l ->
            listOf(arrayOf<Any>(l,320,2f,false),arrayOf<Any>(l,320,2f,true),arrayOf<Any>(l,411,1f,false),arrayOf<Any>(l,411,1f,true)) }
    }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private fun show(failed: Boolean = false, retry: () -> Unit = {}) {
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h800dp-xxhdpi"); RuntimeEnvironment.setFontScale(font)
        val m=MedicationEntity(1,"SYNTHETIC T","T","T_ANDROGEL_50","MG",50.0,1500.0,
            site_rotation=false,notifications_on=false,active=true,sort_order=0)
        val r=RecordEntity(1,1,taken_utc=System.currentTimeMillis()-8*3_600_000,taken_zone="UTC",actual_dose=50.0,
            status="ON_TIME",origin="APP",revision=1,config_snapshot=MedicationSnapshot.encode(m,null))
        ui.setContent { NotesTheme(if(dark)ThemeMode.DARK else ThemeMode.LIGHT) {
            ConcentrationScreen(NotesState(medications=listOf(m)),null,false,null,ConcSettings(false,true,CalibrationMode.RETROSPECTIVE),
                {},{},{},{},PaddingValues(),records=listOf(r),calculationInputFailed=failed,onRetryCalculation=retry)
        } }
        ui.onNodeWithText(ui.activity.getString(R.string.choice_t)).performClick()
        ui.waitUntil(30_000) { ui.onAllNodesWithTag(if(failed) "t-failed" else "t-relative-chart").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun actualConcentrationSelectorShowsRelativeTAndReturnsWithoutReusingE2Calibration() {
        show()
        ui.onNodeWithTag("t-shape-warning").assertTextContains(ui.activity.getString(R.string.t_shape_warning))
        ui.onNodeWithTag("t-relative-chart").performScrollTo().assertIsDisplayed()
        ui.onAllNodesWithTag("e2-now-value").assertCountEquals(0)
        ui.onAllNodesWithTag("calibration-switch").assertCountEquals(0)
        ui.onNodeWithTag("concentration-analyte").performScrollTo()
        ui.onNodeWithText(ui.activity.getString(R.string.choice_e2)).performClick()
        ui.onAllNodesWithTag("t-relative-chart").assertCountEquals(0)
    }
    @Test fun distinctPumpProductStaysRecordOnlyAndDoesNotBorrowSachetParameters() {
        show()
        ui.onNodeWithText("ANDROGEL 50 mg").performScrollTo().performClick()
        ui.onNodeWithText("ANDROGEL 16.2 mg/g").performClick()
        ui.waitUntil(30_000) { ui.onAllNodesWithTag("t-record-only").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("t-record-only").performScrollTo().assertIsDisplayed()
        ui.onAllNodesWithTag("t-relative-chart").assertCountEquals(0)
    }
    @Test fun failedInputRetryReturnsToTheProductionReadInsteadOfSpinningLocally() {
        var retries=0
        show(failed=true,retry={retries++})
        ui.onNodeWithText(ui.activity.getString(R.string.attach_retry)).performScrollTo().performClick()
        assertEquals(1,retries)
    }

}
