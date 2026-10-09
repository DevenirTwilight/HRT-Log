package net.plainnotes.app.audit

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import net.plainnotes.app.R
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

/** Interaction is a gate too: replacing vertically arranged buttons by radio rows must fail. */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],application=android.app.Application::class)
class SettingsButtonInteractionTest(private val locale:String,private val width:Int,private val scale:Float,private val dark:Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/{1}dp/{2}/dark={3}")
        fun params()=listOf("en","zh-rCN","zh-rTW","fr-rFR").flatMap{l->listOf(320,411).flatMap{w->listOf(1f,1.3f,2f).flatMap{s->listOf(false,true).map{arrayOf<Any>(l,w,s,it)}}}}
    }
    private val setup=TestRule{base,_->object:org.junit.runners.model.Statement(){override fun evaluate(){
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h1800dp-xxhdpi");RuntimeEnvironment.setFontScale(scale);base.evaluate()
    }}}
    private val ui=createAndroidComposeRule<ComponentActivity>()
    @get:Rule val chain:RuleChain=RuleChain.outerRule(setup).around(ui)

    @Test fun settingsKeepAllButtonOptionsSelectionAndPersistence() {
        val ctx=ui.activity;val prefs=UiPrefs(ctx)
        prefs.appearance=Appearance(ThemeMode.SYSTEM,true,Contrast.STANDARD)
        var appearance by mutableStateOf(prefs.appearance)
        ui.setContent{NotesTheme(if(dark)ThemeMode.DARK else ThemeMode.LIGHT,contrast=appearance.contrast){Surface{
            SettingsScreen(appearance,{appearance=it;prefs.appearance=it},false,{},{},{},PaddingValues())
        }}}
        fun choice(label:String)=ui.onNode(hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
        fun group(ids:List<Int>,validate:(Int)->Unit) {
            val labels=ids.map(ctx::getString)
            labels.forEach{choice(it).assertHasClickAction().assertIsEnabled()}
            val nodes=labels.map{choice(it).fetchSemanticsNode()}
            val inline=nodes.maxOf{it.positionInRoot.y}-nodes.minOf{it.positionInRoot.y}<1f
            nodes.forEach{assertEquals("$locale/$width/$scale must remain buttons",if(inline)Role.RadioButton else Role.Button,it.config[SemanticsProperties.Role])}
            assertTrue("unequal width",nodes.maxOf{it.size.width}-nodes.minOf{it.size.width}<=1)
            assertTrue("unequal height",nodes.maxOf{it.size.height}-nodes.minOf{it.size.height}<=1)
            assertEquals(1,nodes.count{it.config[SemanticsProperties.Selected]})
            labels.forEachIndexed{i,label->
                choice(label).performScrollTo().assertIsDisplayed()
                val n=choice(label).fetchSemanticsNode();val d=ctx.resources.displayMetrics.density
                assertTrue(n.touchBoundsInRoot.width>=48*d-1);assertTrue(n.touchBoundsInRoot.height>=48*d-1)
                val texts=ui.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Text,listOf(androidx.compose.ui.text.AnnotatedString(label))),useUnmergedTree=true).fetchSemanticsNodes()
                val layouts=mutableListOf<TextLayoutResult>();texts.forEach{it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)}
                assertTrue(layouts.isNotEmpty());layouts.forEach{r->val last=r.lineCount-1
                    assertFalse("clipped $label",r.isLineEllipsized(last) || r.getLineEnd(last,visibleEnd=false)<label.length ||
                        r.getLineBottom(last)-r.size.height>2*d || (0..last).maxOf{r.getLineRight(it)-r.getLineLeft(it)}-r.size.width>d)
                }
                choice(label).performClick().assertIsSelected()
                labels.forEachIndexed{j,l->if(i!=j)choice(l).assertIsNotSelected()}
                ui.onAllNodes(hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected) and (hasText(labels[0]) or hasText(labels[1]) or hasText(labels[2]))).assertCountEquals(3)
                ui.runOnIdle{validate(i);assertTrue(prefs.appearance.dynamic)}
            }
        }
        group(listOf(R.string.theme_system,R.string.theme_light,R.string.theme_dark)){assertEquals(ThemeMode.entries[it],prefs.appearance.mode);assertEquals(Contrast.STANDARD,prefs.appearance.contrast)}
        group(listOf(R.string.contrast_low,R.string.contrast_medium,R.string.contrast_high)){assertEquals(Contrast.entries[it],prefs.appearance.contrast);assertEquals(ThemeMode.DARK,prefs.appearance.mode)}
    }
}
