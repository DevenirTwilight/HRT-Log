package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import net.plainnotes.app.ui.PlainExportDialog
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],application=android.app.Application::class)
class ExportNoticeLayoutTest(private val locale:String,private val scale:Float,private val width:Int,private val dark:Boolean) {
    companion object {@JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}@{1}/{2}/dark={3}")
        fun params()=listOf("en","zh-rCN","zh-rTW","fr-rFR").flatMap{l->listOf(1f,1.3f,2f).flatMap{s->listOf(320,411).flatMap{w->listOf(false,true).map{d->arrayOf<Any>(l,s,w,d)}}}}}
    private val setup=TestRule{base,_->object:org.junit.runners.model.Statement(){override fun evaluate(){RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h640dp-xxhdpi");RuntimeEnvironment.setFontScale(scale);base.evaluate()}}}
    private val ui=createAndroidComposeRule<ComponentActivity>()
    @get:Rule val chain=RuleChain.outerRule(setup).around(ui)
    @Test fun noticeHasCompleteAccessibleTextAndUsableActions(){
        var exported=false;var cancelled=false
        ui.setContent{MaterialTheme(colorScheme=if(dark)darkColorScheme() else lightColorScheme()){
            PlainExportDialog(ui.activity.getString(R.string.export_csv),{cancelled=true},{exported=true})
        }}
        val node=ui.onNodeWithTag("export-privacy-notice").fetchSemanticsNode()
        val layouts=mutableListOf<TextLayoutResult>();node.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!(layouts)
        val layout=layouts.single();assertFalse(layout.hasVisualOverflow)
        assertEquals(ui.activity.getString(R.string.export_privacy_notice),layout.layoutInput.text.text)
        assertTrue(layout.layoutInput.text.text.contains(".pnbak"))
        val density=ui.activity.resources.displayMetrics.density
        val export=ui.onNodeWithText(ui.activity.getString(R.string.export_action)).assertIsDisplayed()
        assertTrue(export.fetchSemanticsNode().boundsInRoot.height>=48*density-1)
        ui.onAllNodes(hasText("",substring=true),useUnmergedTree=true).fetchSemanticsNodes().forEach { textNode ->
            val results=mutableListOf<TextLayoutResult>()
            textNode.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            results.forEach { r ->
                val last=r.lineCount-1
                assertEquals("All characters must be laid out",r.layoutInput.text.length,r.getLineEnd(last,false))
                assertTrue("No line may be ellipsized",(0..last).none{r.isLineEllipsized(it)})
                assertTrue("Actual glyph rows must fit the text box",(0..last).all{r.getLineRight(it)-r.getLineLeft(it)<=r.size.width})
                assertTrue("Actual glyph rows must fit vertically",r.getLineBottom(last)<=r.size.height)
            }
        }
        assertFalse(exported);export.performClick();assertTrue(exported)
        ui.onNodeWithText(ui.activity.getString(R.string.cancel)).assertIsDisplayed().performClick();assertTrue(cancelled)
    }
}
