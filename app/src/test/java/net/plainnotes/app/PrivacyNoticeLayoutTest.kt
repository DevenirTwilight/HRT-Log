package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import net.plainnotes.app.ui.AboutScreen
import net.plainnotes.app.ui.NotesTheme
import net.plainnotes.app.ui.PRIVACY_NOTICE_SECTIONS
import net.plainnotes.app.ui.ThemeMode
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

/** PRIVACY-P0: the offline privacy notice opened from About is complete, readable and operable in four languages at
 * 320/411dp, normal and 2x font, light and dark: every section reachable, no clipped/ellipsized text, headings for
 * screen readers, WCAG AA text contrast, one labelled close action of at least 48dp, and no link or network-dependent
 * control. [PrivacyNoticeOlderApiTest] repeats the narrowest/largest case on API 26 and 29. */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class PrivacyNoticeLayoutTest(private val locale: String, private val width: Int, private val scale: Float, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}@{1}dp/x{2}/dark={3}")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr-rFR").flatMap { l -> listOf(320, 411).flatMap { w -> listOf(1f, 2f).map { s -> arrayOf<Any>(l, w, s, (w == 320) == (s == 2f)) } } }
    }
    private val setup = TestRule { base, _ -> object : org.junit.runners.model.Statement() { override fun evaluate() {
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h640dp-xxhdpi"); RuntimeEnvironment.setFontScale(scale); base.evaluate() } } }
    private val ui = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(setup).around(ui)
    @Test fun noticeIsCompleteReadableAndOperable() = assertPrivacyNoticeOperable(ui, dark)
}

private fun contrast(a: Color, b: Color): Float {
    val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (hi + 0.05f) / (lo + 0.05f)
}

internal fun assertPrivacyNoticeOperable(ui: AndroidComposeTestRule<*, ComponentActivity>, dark: Boolean) {
    fun s(id: Int) = ui.activity.getString(id)
    val inNotice = hasAnyAncestor(hasTestTag("privacy-notice"))
    var colors = listOf<Pair<Color, Color>>()
    ui.setContent { NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
        MaterialTheme.colorScheme.let { colors = listOf(it.onSurface to it.surface, it.onSurfaceVariant to it.surface, it.primary to it.surface) }
        AboutScreen(PaddingValues()) } }
    ui.waitForIdle(); assertEquals(3, colors.size)
    // Text colours used by the notice (body, intro, close button) meet WCAG AA 4.5:1 on the notice surface.
    colors.forEach { (fg, bg) -> assertTrue("Contrast ${contrast(fg, bg)} below 4.5:1", contrast(fg, bg) >= 4.5f) }
    val density = ui.activity.resources.displayMetrics.density
    val open = ui.onNodeWithTag("privacy-notice-open").performScrollTo().assertIsDisplayed().assertHasClickAction()
    assertTrue(open.fetchSemanticsNode().boundsInRoot.height >= 48 * density - 1)
    open.assert(hasText(s(R.string.privacy_notice_open), substring = true))
    open.performClick(); ui.waitForIdle()
    ui.onNodeWithTag("privacy-notice").assertExists()

    // Title + one heading per section, all exposed to screen readers.
    val headings = ui.onAllNodes(inNotice and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).fetchSemanticsNodes()
    assertEquals(1 + PRIVACY_NOTICE_SECTIONS.size, headings.size)
    PRIVACY_NOTICE_SECTIONS.forEachIndexed { i, (title, body) ->
        val section = ui.onNodeWithTag("privacy-section-$i").performScrollTo().assertIsDisplayed()
        section.assert(hasAnyDescendant(hasText(s(title))) and hasAnyDescendant(hasText(s(body))))
    }
    // Every glyph laid out; nothing ellipsized or clipped at this width and font scale.
    ui.onAllNodes(inNotice and hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
        val results = mutableListOf<TextLayoutResult>(); node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        results.forEach { r ->
            val last = r.lineCount - 1
            assertEquals("All characters must be laid out", r.layoutInput.text.length, r.getLineEnd(last, false))
            assertTrue("No line may be ellipsized", (0..last).none { r.isLineEllipsized(it) })
            assertTrue("Glyph rows must fit the text box", (0..last).all { r.getLineRight(it) - r.getLineLeft(it) <= r.size.width })
            assertTrue("Glyph rows must fit vertically", r.getLineBottom(last) <= r.size.height)
        }
    }
    // Offline: the only action is closing; no links, buttons to the web or other screens.
    assertEquals(1, ui.onAllNodes(inNotice and hasClickAction()).fetchSemanticsNodes().size)
    val close = ui.onNodeWithTag("privacy-notice-close").assertIsDisplayed().assertHasClickAction()
    close.assert(hasText(s(R.string.privacy_notice_close)))
    assertTrue(close.fetchSemanticsNode().boundsInRoot.height >= 48 * density - 1)
    close.performClick(); ui.waitForIdle()
    ui.onNodeWithTag("privacy-notice").assertDoesNotExist()
}

/** Same assertions on the oldest supported API levels the device CI covers, at the hardest layout point. */
@RunWith(org.robolectric.RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [26, 29], application = android.app.Application::class, qualifiers = "zh-rTW-w320dp-h640dp-xxhdpi", fontScale = 2f)
class PrivacyNoticeOlderApiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    @Test fun noticeIsCompleteReadableAndOperable() = assertPrivacyNoticeOperable(ui, dark = true)
}
