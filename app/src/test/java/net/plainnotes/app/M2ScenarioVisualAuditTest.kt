package net.plainnotes.app

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import net.plainnotes.app.debug.UnifiedConcentrationTestActivity
import org.junit.*
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Audit only (M2_VISUAL_AUDIT=1), D-1: the M2 scenario page in four languages × font scale 1.0/1.3/2.0 × 320/411 dp ×
 * light/dark, with the research basis expanded and labs listed separately. Saves screenshots to build/m2-visual-audit
 * and fails if any text is cut off, ellipsized or wider than its box.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class M2ScenarioVisualAuditTest(private val locale: String, private val scale: Float, private val width: Int, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}@{1}/{2}dp/dark={3}")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr-rFR").flatMap { l -> listOf(1f, 1.3f, 2f).flatMap { s ->
            listOf(320, 411).flatMap { w -> listOf(false, true).map { d -> arrayOf<Any>(l, s, w, d) } } } }
    }
    private val setup = TestRule { base, _ -> object : org.junit.runners.model.Statement() {
        override fun evaluate() {
            Assume.assumeTrue(System.getenv("M2_VISUAL_AUDIT") == "1")
            RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h2400dp-xxhdpi"); RuntimeEnvironment.setFontScale(scale); base.evaluate()
        }
    } }
    private val ui = createAndroidComposeRule<UnifiedConcentrationTestActivity>()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(setup).around(ui)

    private fun all(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::all)

    @Test fun scenarioPageHasNoClippedText() {
        ui.runOnUiThread { ui.activity.auditDark = dark }
        ui.waitUntil(30_000) { ui.onAllNodesWithTag("e2-chart").fetchSemanticsNodes().isNotEmpty() }
        // UI-12: the research basis moved under "Model and literature"; its text is audited by Ui12ConcentrationTest.pageAndModelAndLiteratureWindowHaveNoClippedText.
        ui.waitForIdle()
        val d = ui.activity.resources.displayMetrics.density
        val problems = mutableListOf<String>()
        all(ui.onRoot(useUnmergedTree = true).fetchSemanticsNode()).filter { it.config.contains(SemanticsProperties.Text) }.forEach { t ->
            val results = mutableListOf<TextLayoutResult>()
            t.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            val r = results.firstOrNull() ?: return@forEach
            if (r.lineCount == 0) return@forEach
            val last = r.lineCount - 1
            val widest = (0..last).maxOf { r.getLineRight(it) - r.getLineLeft(it) }
            if (r.isLineEllipsized(last) || r.getLineEnd(last, visibleEnd = false) < r.layoutInput.text.length ||
                widest - r.size.width > d || r.getLineBottom(last) - r.size.height > 2 * d)
                problems += t.config[SemanticsProperties.Text].joinToString(" ").take(80)
        }
        val out = File("build/m2-visual-audit/${locale}_${scale}_${width}_${if (dark) "dark" else "light"}").apply { mkdirs() }
        val view = ui.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try { view.draw(android.graphics.Canvas(bitmap)); File(out, "m2.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
        File(out, "problems.txt").writeText(problems.joinToString("\n"))
        Assert.assertEquals("Clipped text in $locale/$scale/$width/dark=$dark", emptyList<String>(), problems)
    }
}
