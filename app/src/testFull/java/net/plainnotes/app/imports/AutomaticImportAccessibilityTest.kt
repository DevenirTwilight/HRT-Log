package net.plainnotes.app.imports

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import net.plainnotes.app.R
import net.plainnotes.app.debug.DataPrivacyTestActivity
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Smallest viewport / double text, all four languages, both appearance qualifiers.
 * This validates accessibility semantics, not a claim of physical TalkBack execution. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],application=android.app.Application::class)
class AutomaticImportAccessibilityTest(private val locale:String,private val night:String) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/320dp/2x/{1}")
        fun params()=listOf("en","zh-rCN","zh-rTW","fr-rFR").flatMap{l->listOf("night","notnight").map{arrayOf(l,it)}}
    }
    private val setup=TestRule {base,_->object:org.junit.runners.model.Statement(){override fun evaluate(){
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h640dp-$night-xxhdpi");RuntimeEnvironment.setFontScale(2f);base.evaluate()
    }}}
    private val ui=createAndroidComposeRule<DataPrivacyTestActivity>()
    @get:Rule val chain:RuleChain=RuleChain.outerRule(setup).around(ui)
    @Test fun oneImportActionRemainsReadableAndRestoreIsSeparate() {
        val label=ui.activity.getString(R.string.import_other_apps)
        val action=ui.onNodeWithText(label).performScrollTo().assertIsDisplayed().assertHasClickAction().assertIsEnabled()
        val node=action.fetchSemanticsNode();val density=ui.activity.resources.displayMetrics.density
        assertTrue(node.touchBoundsInRoot.height>=48*density-1)
        val layouts=mutableListOf<TextLayoutResult>()
        ui.onAllNodes(hasText(label),useUnmergedTree=true).fetchSemanticsNodes().forEach {
            it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        }
        assertTrue(layouts.isNotEmpty())
        // Match the established settings audit: Robolectric's aggregate overflow flag has
        // known CJK false positives; verify every actual line and visible character instead.
        layouts.forEach {layout->
            val last=layout.lineCount-1
            assertFalse(layout.isLineEllipsized(last))
            assertEquals(label.length,layout.getLineEnd(last,visibleEnd=false))
            assertTrue("bottom clipping",layout.getLineBottom(last)-layout.size.height<=2*density)
            assertTrue("horizontal clipping",(0..last).maxOf{layout.getLineRight(it)-layout.getLineLeft(it)}-layout.size.width<=density)
        }
        ui.onNodeWithText(ui.activity.getString(R.string.import_ht)).assertDoesNotExist()
        ui.onNodeWithText(ui.activity.getString(R.string.import_tm)).assertDoesNotExist()
        ui.onNodeWithText(ui.activity.getString(R.string.backup_restore)).performScrollTo().assertHasClickAction()
        ui.runOnUiThread {ui.activity.model.cancelAutomaticImport()}
        ui.onNodeWithText(ui.activity.getString(R.string.import_cancelled)).performScrollTo().assertIsDisplayed()
    }
}
