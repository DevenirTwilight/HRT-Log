package net.plainnotes.app.catalog

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import net.plainnotes.app.R
import net.plainnotes.app.debug.catalog.*
import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** All four languages × two widths × two font scales × light/dark × three contrast levels.
 * Screenshots are synthetic Robolectric renders, not physical-device/TalkBack acceptance. */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class CatalogPrototypeLayoutTest(private val locale: String, private val width: Int, private val scale: Float, private val dark: Boolean, private val contrast: Contrast) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}dp-{2}x-dark{3}-{4}")
        fun params() = listOf("en", "fr-rFR", "zh-rCN", "zh-rTW").flatMap { l -> listOf(320, 411).flatMap { w -> listOf(1f, 2f).flatMap { f -> listOf(false, true).flatMap { d -> Contrast.entries.map { c -> arrayOf<Any>(l, w, f, d, c) } } } } }
    }
    private val setup = TestRule { base, _ -> object : org.junit.runners.model.Statement() { override fun evaluate() {
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h800dp-xxhdpi"); RuntimeEnvironment.setFontScale(scale); base.evaluate()
    } } }
    private val ui = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(setup).around(ui)
    private fun node(tag: String) = ui.onNodeWithTag("cat-body").let { it.performScrollToNode(hasTestTag(tag)); ui.onNodeWithTag(tag) }
    private fun readable() {
        ui.onNodeWithTag("cat-banner").assertIsDisplayed()
        ui.onAllNodes(hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNodes().forEach { n ->
            val results = mutableListOf<TextLayoutResult>()
            n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            results.forEach { r -> if (r.lineCount > 0) {
                // Native Paragraph.width retains the available 888px for a 96px "Back" label;
                // hasVisualOverflow therefore reports unused paragraph space as overflow. Check actual glyph rows,
                // all characters and ellipsis, as the existing privacy layout acceptance does.
                assertTrue("Glyph width: ${r.layoutInput.text}", (0 until r.lineCount).all { r.getLineRight(it) - r.getLineLeft(it) <= r.size.width })
                assertTrue("Glyph height: ${r.layoutInput.text}", r.getLineBottom(r.lineCount - 1) <= r.size.height)
                assertTrue((0 until r.lineCount).none { r.isLineEllipsized(it) })
                assertEquals(r.layoutInput.text.length, r.getLineEnd(r.lineCount - 1, false))
            } }
        }
        val density = ui.activity.resources.displayMetrics.density
        assertTrue(ui.onNodeWithTag("cat-back").fetchSemanticsNode().boundsInRoot.height >= 48 * density - 1)
    }
    private fun shot(page: String) {
        ui.waitForIdle(); readable()
        val view = ui.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val dir = File("build/reports/tests/ansm02/screenshots").apply { mkdirs() }
        File(dir, "${locale}_${width}_${scale}_${dark}_${contrast}_${page}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun catalogueAndLongDetailRemainReadable() {
        val c = fixture()
        var pairs = listOf<Pair<Color, Color>>()
        ui.setContent { NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, contrast = contrast) {
            androidx.compose.material3.MaterialTheme.colorScheme.let { pairs = listOf(it.onSurface to it.surface, it.onErrorContainer to it.errorContainer) }
            CatalogPrototype(CatalogLoad.Ready(c))
        } }
        ui.waitForIdle()
        pairs.forEach { (a, b) -> val values = listOf(a.luminance(), b.luminance()).sorted(); assertTrue((values[1] + .05f) / (values[0] + .05f) >= 4.5f) }
        shot("editor")
        node("cat-open").performClick()
        node("cat-search").performTextReplacement("association"); ui.onNodeWithTag("cat-search").performImeAction()
        node("cat-product:SYNTH-CIS-6"); shot("results")
        node("cat-product:SYNTH-CIS-6").performClick()
        node("cat-product-name").assertTextEquals(c.products[5].name).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)); shot("detail")
        node("cat-select").performClick()
        node("cat-confirm-boundary").assertExists(); shot("confirm")
        node("cat-cancel").performClick()
        ui.onNodeWithTag("cat-back").performClick()
        node("cat-search").assertTextContains("association")
        ui.onNodeWithTag("cat-back").performClick()
        node("cat-name").assertTextContains("")
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class)
class CatalogPrototypeFlowTest(private val locale: String) {
    companion object { @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}") fun params() = listOf("en", "fr-rFR", "zh-rCN", "zh-rTW").map { arrayOf<Any>(it) } }
    private val setup = TestRule { base, _ -> object : org.junit.runners.model.Statement() { override fun evaluate() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h800dp-xxhdpi"); RuntimeEnvironment.setFontScale(2f); base.evaluate()
    } } }
    private val ui = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(setup).around(ui)
    private fun node(tag: String): SemanticsNodeInteraction { ui.onNodeWithTag("cat-body").performScrollToNode(hasTestTag(tag)); return ui.onNodeWithTag(tag) }
    private fun back() { ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() } }
    @Test fun selectionCancellationRepeatedSelectionAndManualFallbackKeepDraft() {
        val c = fixture()
        ui.setContent { NotesTheme { CatalogPrototype(CatalogLoad.Ready(c)) } }
        node("cat-name").performTextReplacement("Non-France custom medicine")
        node("cat-dose").performTextReplacement("0,5 tablette")
        node("cat-open").performClick()
        node("cat-search").performTextReplacement("ESTRADIOL")
        node("cat-filter-form").performClick(); node("cat-filter-form:comprimé").performClick()
        node("cat-filter-strength").performClick(); node("cat-filter-strength:1 mg").performClick()
        node("cat-filter-route").performClick(); node("cat-filter-route:orale").performClick()
        node("cat-product:SYNTH-CIS-1").performClick()
        node("cat-rcp").performClick(); node("cat-link-message").assertExists()
        node("cat-notice").performClick(); node("cat-link-message").assertExists()
        node("cat-select").performClick(); node("cat-cancel").performClick()
        back(); back()
        node("cat-name").assertTextContains("Non-France custom medicine")
        node("cat-dose").assertTextContains("0,5 tablette")
        ui.onNodeWithTag("cat-selected").assertDoesNotExist()
        repeat(2) {
            node("cat-open").performClick(); node("cat-product:SYNTH-CIS-1").performClick(); node("cat-select").performClick()
            node("cat-filter-cip").performClick(); node("cat-filter-cip:SYNTH-CIP-11").performClick()
            node("cat-confirm").performClick()
            node("cat-selected").assertTextContains("synthetic-v1", substring = true)
            ui.onAllNodesWithTag("cat-selected").assertCountEquals(1)
        }
        node("cat-dose").assertTextContains("0,5 tablette")
        node("cat-unlink").performClick()
        node("cat-open").performClick(); node("cat-search").performTextReplacement("不存在 ailleurs not-covered")
        node("cat-no-results").assertExists(); node("cat-fallback").performClick()
        node("cat-manual").performClick(); node("cat-not-saved").assertExists()
        node("cat-name").assertTextContains("Non-France custom medicine")
    }
    @Test fun loadingFailureRetryAndEmptyCatalogueAllowManualCreation() {
        var load by androidx.compose.runtime.mutableStateOf<CatalogLoad>(CatalogLoad.Loading)
        ui.setContent { NotesTheme { CatalogPrototype(load, onRetry = { load = CatalogLoad.Ready(fixture().copy(products = emptyList())) }) } }
        node("cat-name").performTextReplacement("Kept draft"); node("cat-open").performClick()
        node("cat-loading").assertExists(); node("cat-fallback").assertExists()
        ui.runOnIdle { load = CatalogLoad.Failed }
        node("cat-error").assertExists(); node("cat-retry").performClick()
        node("cat-no-results").assertExists(); node("cat-fallback").performClick()
        node("cat-name").assertTextContains("Kept draft")
    }
}

/** Exercises the actual debug asset loader rather than injecting a Ready state. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CatalogPrototypeHostTest {
    @get:Rule val ui = createAndroidComposeRule<CatalogPrototypeActivity>()
    @Test fun hostLoadsOnlyLocalSyntheticAsset() {
        ui.onNodeWithTag("cat-banner").assertExists()
        ui.onNodeWithTag("cat-body").performScrollToNode(hasTestTag("cat-open"))
        ui.onNodeWithTag("cat-open").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("cat-filter-form").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("cat-body").performScrollToNode(hasTestTag("cat-product:SYNTH-CIS-1"))
        ui.onNodeWithTag("cat-product:SYNTH-CIS-1").assertExists()
        ui.onNodeWithTag("cat-error").assertDoesNotExist()
    }
}
