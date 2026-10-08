package net.plainnotes.app.audit

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import net.plainnotes.app.NotesState
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import net.plainnotes.app.R
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.*
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.time.*

/** Regular CI gate: real screens, four translations, narrow/normal width, light/dark at large text. */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],application=android.app.Application::class)
class ButtonLayoutRegressionTest(private val locale:String,private val width:Int,private val dark:Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/{1}dp/dark={2}")
        fun params()=listOf("en","zh-rCN","zh-rTW","fr-rFR").flatMap{l->listOf(320,411).flatMap{w->listOf(false,true).map{arrayOf<Any>(l,w,it)}}}
    }
    private val setup=TestRule{base,_->object:org.junit.runners.model.Statement(){override fun evaluate(){
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h1800dp-xxhdpi");RuntimeEnvironment.setFontScale(2f);base.evaluate()
    }}}
    private val rule=createAndroidComposeRule<ComponentActivity>()
    @get:Rule val chain:RuleChain=RuleChain.outerRule(setup).around(rule)
    @Test fun criticalControlsRemainReadableAndClickable() {
        val ctx=rule.activity
        android.provider.Settings.System.putString(ctx.contentResolver,android.provider.Settings.System.TIME_12_24,"12")
        val med=MedicationEntity(id=1,name="Synthetic",molecule="E2",unit="MG",dose_per_intake=2.0,container_capacity=30.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
        val boxes=listOf(ContainerEntity(1,1,30.0,0.0,10.0,"2026-10-01","IN_USE"))
        val state=NotesState(medications=listOf(med),loading=false)
        var page by mutableIntStateOf(0);var clicks=0
        rule.setContent { NotesTheme(if(dark)ThemeMode.DARK else ThemeMode.LIGHT){Surface {
            when(page){
                0->StockScreen(state,boxes,emptyList(),{clicks++},{_,_->clicks++},{clicks++},PaddingValues())
                1->Column(Modifier.fillMaxWidth().padding(16.dp)){DateTimeRow(LocalDateTime.of(2026,10,28,23,46),{})}
                2->CalendarScreen(state,LocalDate.of(2026,10,28),{},{},{},{},PaddingValues())
                3->SettingsScreen(Appearance(ThemeMode.SYSTEM,false),{},false,{},{},{},PaddingValues())
            }
        }}}
        repeat(4){p->
            rule.runOnIdle{page=p};rule.waitForIdle()
            rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),useUnmergedTree=true).fetchSemanticsNodes().forEach{n->
                val results=mutableListOf<TextLayoutResult>();n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                results.firstOrNull()?.let{r->
                    val d=ctx.resources.displayMetrics.density;val last=r.lineCount-1
                    val clipped=last>=0 && (r.isLineEllipsized(last) || r.getLineEnd(last,visibleEnd=false)<r.layoutInput.text.length ||
                        r.getLineBottom(last)-r.size.height>2*d || (0..last).maxOf{r.getLineRight(it)-r.getLineLeft(it)}-r.size.width>d)
                    assertFalse("page=$p clipped: ${r.layoutInput.text}, size=${r.size}, bottom=${r.getLineBottom(last)}, right=${r.getLineRight(last)}, end=${r.getLineEnd(last)}",clipped)
                }
            }
            rule.onAllNodes(hasClickAction(),useUnmergedTree=true).fetchSemanticsNodes().forEach{n->
                if(n.config.getOrNull(SemanticsProperties.Role) in listOf(Role.Button,Role.RadioButton)) {
                    val density=ctx.resources.displayMetrics.density
                    // LazyColumn may measure offscreen items; their clipped touch bounds are intentionally empty.
                    val y=n.positionInRoot.y
                    if(y>=0 && y+n.size.height<=ctx.window.decorView.height)
                        assertTrue("page=$p collapsed control: ${n.config}",n.touchBoundsInRoot.width/density>=48f && n.touchBoundsInRoot.height/density>=48f)
                }
            }
            if(p==0){
                val labels=listOf(R.string.stock_add,R.string.stock_replace,R.string.stock_adjust)
                val bounds=labels.map{rule.onNodeWithText(ctx.getString(it)).fetchSemanticsNode().boundsInRoot}
                assertTrue("stock action widths differ",bounds.maxOf{it.width}-bounds.minOf{it.width}<=1f)
                assertTrue("stock action heights differ",bounds.maxOf{it.height}-bounds.minOf{it.height}<=1f)
                rule.onNodeWithText(ctx.getString(R.string.stock_add)).performClick();assertEquals(1,clicks)
            }
        }
    }
    @Test fun accessibleTimeInputValidatesAndPreservesTwelveAndTwentyFourHourValues() {
        // Force the narrow-screen fallback independently of AM/PM translation width.
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h1800dp-xxhdpi")
        val ctx=rule.activity
        var is24 by mutableStateOf(false)
        var open by mutableStateOf(false)
        var picked:LocalTime?=null
        android.provider.Settings.System.putString(ctx.contentResolver,android.provider.Settings.System.TIME_12_24,"12")
        rule.setContent{NotesTheme(if(dark)ThemeMode.DARK else ThemeMode.LIGHT){
            key(is24){if(open)TimePickerModal(LocalTime.of(23,46),{open=false},{picked=it})}
        }}
        // Dialog text-input IME animations do not settle under Robolectric; use manual frames.
        rule.mainClock.autoAdvance=false
        fun settle(){
            val looper=org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            repeat(4){rule.mainClock.advanceTimeBy(500);var n=0;while(!looper.isIdle && n++<500)looper.runOneTask()}
        }
        fun nodes():List<SemanticsNode> {
            val roots=mutableListOf<androidx.compose.ui.platform.ViewRootForTest>()
            fun walk(v:android.view.View){if(v is androidx.compose.ui.platform.ViewRootForTest)roots+=v;if(v is android.view.ViewGroup)repeat(v.childCount){walk(v.getChildAt(it))}}
            walk(ctx.window.decorView)
            org.robolectric.shadows.ShadowDialog.getShownDialogs().filter{it.isShowing}.forEach{it.window?.decorView?.let(::walk)}
            fun all(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::all)
            return roots.flatMap{all(it.semanticsOwner.rootSemanticsNode)}
        }
        fun node(label:String)=nodes().single{it.config.getOrNull(SemanticsProperties.Text)?.any{t->t.text==label}==true}
        fun replace(label:String,value:String){assertTrue(node(label).config[SemanticsActions.SetText].action!!.invoke(androidx.compose.ui.text.AnnotatedString(value)));settle()}
        fun confirm(){val n=node(ctx.getString(R.string.ok));assertFalse(n.config.contains(SemanticsProperties.Disabled));n.config[SemanticsActions.OnClick].action!!.invoke();settle()}
        fun disabled(){assertTrue(node(ctx.getString(R.string.ok)).config.contains(SemanticsProperties.Disabled))}
        open=true;settle()
        val hour=ctx.getString(R.string.picker_hour);val minute=ctx.getString(R.string.picker_minute)
        replace(hour,"0");disabled()
        replace(hour,"11");replace(minute,"60");disabled()
        replace(minute,"46");confirm()
        assertEquals(LocalTime.of(23,46),picked)
        android.provider.Settings.System.putString(ctx.contentResolver,android.provider.Settings.System.TIME_12_24,"24")
        is24=true;open=true;picked=null;settle()
        replace(hour,"24");disabled()
        replace(hour,"0");replace(minute,"05");confirm()
        assertEquals(LocalTime.of(0,5),picked)
    }

}
