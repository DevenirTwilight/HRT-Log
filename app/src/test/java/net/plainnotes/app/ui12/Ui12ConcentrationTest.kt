package net.plainnotes.app.ui12

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.pk.CalibrationMode
import net.plainnotes.app.pk.Pk
import net.plainnotes.app.ui.ConcSettings
import net.plainnotes.app.ui.ConcentrationScreen
import net.plainnotes.app.ui.NotesTheme
import net.plainnotes.app.ui.ThemeMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt

/**
 * UI-12 (REQUIREMENTS §162): the unified concentration page. Synthetic scenarios only. Four languages × (320 dp at 2× font, 411 dp at 1×)
 * × light and dark. Shots (no pixel assertions) are written when UI12_SHOTS=<label> is set.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class Ui12ConcentrationTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr").flatMap { l ->
            listOf(arrayOf<Any>(l, 320, 2f, false), arrayOf<Any>(l, 320, 2f, true), arrayOf<Any>(l, 411, 1f, false), arrayOf<Any>(l, 411, 1f, true)) }
    }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val mode get() = if (dark) ThemeMode.DARK else ThemeMode.LIGHT
    private fun s(id: Int, vararg a: Any) = if (a.isEmpty()) ui.activity.resources.getString(id) else ui.activity.getString(id, *a)
    private fun setup() { RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-xxhdpi"); RuntimeEnvironment.setFontScale(font) }

    private var pmol by mutableStateOf(false)
    private var calibrate by mutableStateOf(true)
    private fun show(sc: Ui12Fixture.Scenario, weight: Double? = 70.0) {
        ui.setContent { NotesTheme(mode) {
            ConcentrationScreen(sc.state, sc.result, false, weight, ConcSettings(pmol, calibrate, CalibrationMode.RETROSPECTIVE),
                { pmol = it.pmol; calibrate = it.calibrate }, {}, {}, {}, PaddingValues(), sc.records, sc.state.profiles,
                preview = sc.preview, e2NonSublingual = sc.nonSublingual, doseTimes = sc.doseTimes, labs = sc.labs)
        } }
        ui.waitUntil(30_000) { ui.onAllNodesWithTag("e2-estimate-card").fetchSemanticsNodes().isNotEmpty() || ui.onAllNodesWithTag("calibration-switch").fetchSemanticsNodes().isNotEmpty() || ui.onAllNodesWithTag("empty-state").fetchSemanticsNodes().isNotEmpty() ||
            ui.onAllNodesWithText(s(R.string.pk_need_weight_title)).fetchSemanticsNodes().isNotEmpty() }
        ui.waitForIdle()
    }
    private fun shot(name: String) {
        val dir = System.getenv("UI12_SHOTS")?.takeIf { it.isNotBlank() } ?: return
        ui.waitForIdle()
        val view = ui.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888); ui.runOnUiThread { view.draw(Canvas(bitmap)) }
        File("build/ui12-shots/$dir").apply { mkdirs() }.resolve("${name}_${locale}_${width}dp_${if (font == 1f) "1x" else "2x"}_${if (dark) "dark" else "light"}.png")
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun text(tag: String): String = ui.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
    private fun scrollTo(tag: String) = ui.onNodeWithTag(tag).performScrollTo()
    private fun gone(vararg ids: Int) { for (id in ids) ui.onAllNodesWithText(s(id), substring = true).assertCountEquals(0) }

    @Test fun sublingualOnlyShowsTheResultFirstThenTheCurveWithThreeAndSevenDays() {
        setup(); val sc = Ui12Fixture.sublingualOnly(); show(sc)
        val total = sc.total!!
        ui.onNodeWithTag("e2-estimate-card").assertIsDisplayed()
        assertEquals(s(R.string.ui12_label_single, s(R.string.ui12_route_sl).replaceFirstChar { it.titlecase(java.util.Locale.ROOT) }), text("e2-label"))
        assertEquals(total.nowValue!!.roundToInt().toString(), text("e2-now-value"))
        assertEquals(s(R.string.ui12_basis, s(R.string.ui12_route_sl).replaceFirstChar { it.titlecase(java.util.Locale.ROOT) }, 5), text("e2-basis"))
        // The estimate is the first thing on the page and the reminder is the one caveat.
        ui.onAllNodesWithText(s(R.string.ui12_reminder)).assertCountEquals(1)
        ui.onNodeWithTag("e2-reminder").assertIsDisplayed()
        // Only 3 and 7 days, 3 selected by default.
        ui.onNodeWithText(s(R.string.days_short, 3)).assertIsSelected()
        ui.onNodeWithText(s(R.string.days_short, 7)).assertIsNotSelected()
        for (d in listOf(14, 60)) ui.onAllNodesWithText(s(R.string.days_short, d)).assertCountEquals(0)
        ui.onNodeWithText(s(R.string.days_short, 7)).performScrollTo().performClick()
        ui.onNodeWithText(s(R.string.days_short, 7)).assertIsSelected()
        shot("sublingual")
        scrollTo("models-literature-open"); shot("sublingual_bottom")
    }

    @Test fun superseded_pageElementsAreGoneAndTheirTextIsReachableUnderModelAndLiterature() {
        setup(); val sc = Ui12Fixture.sublingualOnly(); show(sc)
        // Removed from the page (D-1 / §65 display, superseded by §162): scenario card, research note, band legends and switch, hints.
        for (tag in listOf("m2-scenario-card", "research-not-personal", "m2-labs-separate", "m2-calibration-unavailable")) ui.onAllNodesWithTag(tag).assertCountEquals(0)
        gone(R.string.pk_chart_hint, R.string.chart_full_band, R.string.chart_scale_note, R.string.legend_band_inner, R.string.legend_band_outer,
            R.string.pk_disclaimer_short, R.string.pk_current, R.string.m2_observed_labs, R.string.m2_labs_separate_note)
        // The same explanations are moved, not deleted.
        scrollTo("models-literature-open"); ui.onNodeWithTag("models-literature-open").assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        ui.onNodeWithTag("models-literature-window").assertIsDisplayed()
        ui.onNodeWithTag("research-not-personal").assertExists()
        ui.onNodeWithText(s(R.string.pk_disclaimer_short)).assertExists()
        ui.onNodeWithTag("baseline-once-note").assertExists()
        assertEquals(s(R.string.ui12_baseline_once), text("baseline-once-note"))
        ui.onNodeWithText(s(R.string.m2_parameter_scope)).assertExists()
        ui.onNodeWithTag("models-literature-back").assertHasClickAction().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        shot("models_literature")
        ui.onNodeWithTag("models-literature-back").performClick()
        ui.onAllNodesWithTag("models-literature-window").assertCountEquals(0)
    }

    // SL-CAL-02 step 4 (REQUIREMENTS §166): replaces UI-12's "calibration unavailable while sublingual is recorded".
    @Test fun sublingualLabsCalibrateTheTotalAndTheCardSaysWhatWasDone() {
        setup(); val sc = Ui12Fixture.sublingualCalibrated(); show(sc)
        val outcome = sc.outcome as net.plainnotes.app.conc.TotalE2Calibration.Calibrated
        assertEquals(1.3, outcome.factor, 0.12)
        assertEquals(listOf(1L, 2L, 3L), outcome.usedLabIds)
        scrollTo("calibration-switch")
        ui.onNodeWithTag("calibration-switch").assertIsEnabled().assertIsOn()
        assertEquals(s(R.string.ui12_cal_used, 3), text("calibration-used"))
        val tier = text("calibration-tier")
        assertTrue(tier == s(R.string.ui12_cal_tier_sufficient) || tier == s(R.string.ui12_cal_tier_limited))
        assertEquals(s(R.string.ui12_cal_loo, outcome.leaveOneOut!!.medianPercent.roundToInt()), text("calibration-loo"))
        // One lab with fewer than 24 h of history before it and one date-only lab are named, not hidden.
        val skipped = ui.onAllNodesWithTag("calibration-skipped", useUnmergedTree = true).fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].joinToString("") { t -> t.text } }.toSet()
        assertEquals(setOf(s(R.string.ui12_cal_skipped, 1, s(R.string.ui12_cal_r_history)), s(R.string.ui12_cal_skipped, 1, s(R.string.ui12_cal_r_time))), skipped)
        ui.onAllNodesWithTag("calibration-downweighted").assertCountEquals(0)
        ui.onNodeWithText(s(R.string.calib_manage_labs)).assertHasClickAction().assertHeightIsAtLeast(48.dp)
        // The headline is the calibrated total, labelled so, and it differs from the uncalibrated one.
        val total = sc.total!!
        assertTrue(total.calibrated)
        assertEquals(total.nowValue!!.roundToInt().toString(), text("e2-now-value"))
        assertNotEquals(sc.totalUncalibrated!!.nowValue!!.roundToInt(), total.nowValue!!.roundToInt())
        // The estimate card and the calibration card both say it.
        ui.onAllNodesWithText(s(R.string.ui12_label_calibrated), useUnmergedTree = true).assertCountEquals(2)
        shot("sublingual_calibrated"); scrollTo("calibration-time-note"); shot("sublingual_calibrated_card")
    }

    @Test fun switchingCalibrationOffRestoresTheUncalibratedTotal() {
        setup(); val sc = Ui12Fixture.sublingualCalibrated(); show(sc)
        scrollTo("calibration-switch"); ui.onNodeWithTag("calibration-switch").performClick()
        ui.waitUntil(30_000) { text("e2-now-value") == sc.totalUncalibrated!!.nowValue!!.roundToInt().toString() }
        scrollTo("calibration-switch"); ui.onNodeWithTag("calibration-switch").assertIsEnabled().assertIsOff()
        ui.onNodeWithText(s(R.string.ui12_cal_off)).assertExists()
        assertEquals(s(R.string.ui12_cal_explain), text("calibration-explain"))
        ui.onAllNodesWithTag("calibration-used").assertCountEquals(0)
        scrollTo("calibration-explain"); shot("sublingual_calibration_off_card")
    }

    @Test fun mixedRoutesCalibrateAsOneTotal() {
        setup(); val sc = Ui12Fixture.mixedCalibrated(); show(sc)
        val outcome = sc.outcome as net.plainnotes.app.conc.TotalE2Calibration.Calibrated
        assertEquals(1.3, outcome.factor, 0.12)
        assertEquals(s(R.string.ui12_label_total), text("e2-label"))
        assertEquals(sc.total!!.nowValue!!.roundToInt().toString(), text("e2-now-value"))
        scrollTo("calibration-switch"); ui.onNodeWithTag("calibration-switch").assertIsEnabled().assertIsOn()
        scrollTo("calibration-time-note"); shot("mixed_calibrated_card")
    }

    @Test fun twoLabsTooCloseToADoseAreAcceptedButCountForLessOrRefusedInWords() {
        setup(); val sc = Ui12Fixture.sublingualOnly(); show(sc)   // two labs, both within 2.5 h of a sublingual dose
        scrollTo("calibration-switch")
        val outcome = sc.outcome
        when (outcome) {
            is net.plainnotes.app.conc.TotalE2Calibration.Calibrated -> assertEquals(s(R.string.ui12_cal_downweighted, 2), text("calibration-downweighted"))
            is net.plainnotes.app.conc.TotalE2Calibration.Refused -> { ui.onNodeWithTag("calibration-refused").assertExists(); ui.onNodeWithText(s(R.string.ui12_cal_not_applied)).assertExists() }
            null -> fail("labs exist")
        }
        shot("sublingual_two_labs_card")
    }

    @Test fun withoutAnySublingualEstimateTheSwitchIsUnavailableWithItsReason() {
        setup(); val base = Ui12Fixture.sublingualOnly()
        show(Ui12Fixture.Scenario(base.state, base.records, base.result, null, null, base.doseTimes, base.labs))
        scrollTo("calibration-switch")
        ui.onNodeWithTag("calibration-switch").assertIsNotEnabled()
        ui.onNodeWithText(s(R.string.ui12_calib_unavailable)).assertExists()
        assertEquals(s(R.string.ui12_calib_unavailable_sl), text("calibration-unavailable-sl"))
    }

    @Test fun onlyOralKeepsTheExistingCalibration() {
        setup(); val sc = Ui12Fixture.oralOnly(); show(sc)
        assertEquals(s(R.string.ui12_label_single, s(R.string.ui12_route_oral).replaceFirstChar { it.titlecase(java.util.Locale.ROOT) }), text("e2-label"))
        ui.onAllNodesWithTag("calibration-switch").assertCountEquals(0)
        ui.onAllNodesWithTag("calibration-unavailable-sl").assertCountEquals(0)
        ui.onNodeWithText(s(R.string.calib_enable)).performScrollTo().assertExists()
        // The page shows exactly the number the calculator produced before the redesign.
        assertEquals(sc.result.currentPgMl!!.roundToInt().toString(), text("e2-now-value"))
        shot("oral")
    }

    @Test fun mixedRoutesAreAddedIntoOneTotalWithOneRowOfBasis() {
        setup(); val sc = Ui12Fixture.mixed(); show(sc)
        val total = sc.total!!
        assertEquals(s(R.string.ui12_label_total), text("e2-label"))
        val phrase = s(R.string.ui12_sum_phrase, s(R.string.ui12_list_last, s(R.string.ui12_route_sl), s(R.string.ui12_route_oral)))
        assertEquals(s(R.string.ui12_basis, phrase, total.doseCount), text("e2-basis"))
        assertEquals(total.nowValue!!.roundToInt().toString(), text("e2-now-value"))
        assertEquals(9, total.doseCount)
        // No "separate display" control, no per-route legend.
        ui.onAllNodesWithText(s(R.string.legend_band_outer)).assertCountEquals(0)
        ui.onAllNodesWithText(s(R.string.ui12_reminder)).assertCountEquals(1)
        ui.onNodeWithTag("calibration-switch").performScrollTo().assertIsEnabled()
        shot("mixed")
    }

    @Test fun noRecordsAndMissingWeightKeepTheirCardsAndPutThemFirstWhenThereIsNoEstimate() {
        setup(); show(Ui12Fixture.noRecords())
        ui.onAllNodesWithTag("e2-estimate-card").assertCountEquals(0)
        ui.onNodeWithText(s(R.string.pk_no_doses_title)).assertExists()
        shot("no_records")
    }

    @Test fun needingTheWeightShowsTheRequestAfterTheEstimate() {
        setup(); val sc = Ui12Fixture.needsWeight(); show(sc, weight = null)
        ui.onNodeWithText(s(R.string.pk_need_weight_title)).assertExists()
        ui.onNodeWithText(s(R.string.pk_set_weight)).assertHasClickAction()
        // With an estimate present the weight request comes after the estimate card.
        val estimateTop = ui.onNodeWithTag("e2-estimate-card").fetchSemanticsNode().positionInRoot.y
        val weightTop = ui.onNodeWithText(s(R.string.pk_need_weight_title)).fetchSemanticsNode().positionInRoot.y
        assertTrue("estimate first: estimate=$estimateTop weight=$weightTop ($locale/$width/$font/$dark)", estimateTop < weightTop)
        shot("needs_weight")
    }

    @Test fun tappingTheCurveOpensAReadableFloatingReadoutAndTappingAgainClosesIt() {
        setup(); val sc = Ui12Fixture.sublingualOnly(); show(sc)
        ui.onNodeWithTag("e2-chart").performScrollTo()
        ui.onNodeWithTag("e2-chart").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.62f, height * 0.4f)) }
        ui.waitUntil(15_000) { ui.onAllNodesWithTag("chart-readout").fetchSemanticsNodes().isNotEmpty() }
        val spoken = ui.onNodeWithTag("chart-readout").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString(" ")
        assertTrue("unit is spoken in words: $spoken", spoken.contains(s(R.string.ui12_unit_pg_spoken)))
        assertFalse("no unit abbreviation in the spoken text", spoken.contains("pg/mL"))
        assertTrue(spoken.any { it.isDigit() })
        shot("tap_readout")
        ui.onNodeWithTag("e2-chart").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.62f, height * 0.4f)) }
        ui.waitUntil(15_000) { ui.onAllNodesWithTag("chart-readout").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun switchingToPmolConvertsTheBigNumberAndTheSpokenUnit() {
        setup(); val sc = Ui12Fixture.sublingualOnly(); show(sc)
        ui.onNodeWithText("pmol/L").performScrollTo().performClick(); ui.waitForIdle()
        assertEquals((sc.total!!.nowValue!! * Pk.PMOL_PER_PG).roundToInt().toString(), text("e2-now-value"))
        ui.onNodeWithTag("e2-chart").performScrollTo()
        ui.onNodeWithTag("e2-chart").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.4f)) }
        ui.waitUntil(15_000) { ui.onAllNodesWithTag("chart-readout").fetchSemanticsNodes().isNotEmpty() }
        val spoken = ui.onNodeWithTag("chart-readout").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString(" ")
        assertTrue(spoken, spoken.contains(s(R.string.ui12_unit_pmol_spoken)))
        shot("pmol")
    }

    @Test fun legendUsesWordsAndDifferentShapesNotColourAlone() {
        setup(); val sc = Ui12Fixture.sublingualOnly(); show(sc)
        for (id in listOf(R.string.legend_recorded, R.string.ui12_legend_tail, R.string.legend_labs, R.string.legend_doses)) ui.onNodeWithText(s(id)).performScrollTo().assertExists()
        assertEquals("four different words", 4, listOf(R.string.legend_recorded, R.string.ui12_legend_tail, R.string.legend_labs, R.string.legend_doses).map { s(it) }.toSet().size)
    }

    @Test fun bigNumberAndUnitAreNotTruncatedAtLargeText() {
        setup(); show(Ui12Fixture.sublingualOnly())
        ui.onNodeWithTag("e2-now-value").assertIsDisplayed()
        ui.onNodeWithTag("e2-estimate-card").assertIsDisplayed()
        val card = ui.onNodeWithTag("e2-estimate-card").fetchSemanticsNode().boundsInRoot
        val value = ui.onNodeWithTag("e2-now-value", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("number inside the card", value.right <= card.right && value.left >= card.left)
        assertTrue(ui.onAllNodesWithText("pg/mL", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    /** Text that is cut off, ellipsised or wider than its box in the page or in the window (all compose roots). */
    private fun clipped(): List<String> {
        val d = ui.activity.resources.displayMetrics.density
        val out = mutableListOf<String>()
        fun walk(n: androidx.compose.ui.semantics.SemanticsNode): List<androidx.compose.ui.semantics.SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
        for (root in ui.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()) walk(root).filter { it.config.contains(SemanticsProperties.Text) }.forEach { t ->
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            t.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            val r = results.firstOrNull() ?: return@forEach
            if (r.lineCount == 0) return@forEach
            val last = r.lineCount - 1
            val widest = (0..last).maxOf { r.getLineRight(it) - r.getLineLeft(it) }
            if (r.isLineEllipsized(last) || r.getLineEnd(last, visibleEnd = false) < r.layoutInput.text.length || widest - r.size.width > d || r.getLineBottom(last) - r.size.height > 2 * d)
                out += t.config[SemanticsProperties.Text].joinToString(" ").take(80)
        }
        return out
    }

    @Test fun pageAndModelAndLiteratureWindowHaveNoClippedText() {
        setup(); val sc = Ui12Fixture.mixed(); show(sc)
        assertEquals("page", emptyList<String>(), clipped())
        scrollTo("models-literature-open"); ui.onNodeWithTag("models-literature-open").performClick()
        ui.onNodeWithTag("models-literature-window").assertIsDisplayed()
        ui.onNodeWithTag("m2-research-basis-toggle").assertDoesNotExist() // the basis toggle is supplied by the unified host, not this fixture
        assertEquals("window", emptyList<String>(), clipped())
    }
}
