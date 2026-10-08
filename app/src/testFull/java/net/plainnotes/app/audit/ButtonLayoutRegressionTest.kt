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
        val med=MedicationEntity(id=1,name="Medication synthétique",molecule="E2",unit="MG",dose_per_intake=2.0,container_capacity=30.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
        val boxes=listOf(ContainerEntity(1,1,30.0,0.0,10.0,"2026-10-01","IN_USE"))
        val state=NotesState(medications=listOf(med),loading=false)
        val taken=LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val records=listOf(RecordEntity(id=1,medication_id=1,taken_utc=taken,taken_zone=ZoneId.systemDefault().id,actual_dose=2.0,status="LATE",origin="APP",revision=1,
            config_snapshot=MedicationSnapshot.encode(med,ProfileEntity(1,"EV","oral"))))
        val extra=net.plainnotes.app.NotesViewModel.ExtraState(records=records)
        var page by mutableIntStateOf(0);var clicks=0
        rule.setContent { NotesTheme(if(dark)ThemeMode.DARK else ThemeMode.LIGHT){Surface {
            when(page){
                0->StockScreen(state,boxes,emptyList(),{clicks++},{_,_->clicks++},{clicks++},PaddingValues())
                1->Column(Modifier.fillMaxWidth().padding(16.dp)){DateTimeRow(LocalDateTime.of(2026,10,28,23,46),{})}
                2->CalendarScreen(state,LocalDate.of(2026,10,28),{},{},{},{},PaddingValues(),extra=extra)
                3->SettingsScreen(Appearance(ThemeMode.SYSTEM,false),{},false,{},{},{},PaddingValues())
                4->HistoryScreen(state,records,{},{},PaddingValues())
            }
        }}}
        repeat(5){p->
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
        var pickedDate:LocalDate?=null
        var calendar by mutableStateOf(false)
        android.provider.Settings.System.putString(ctx.contentResolver,android.provider.Settings.System.TIME_12_24,"12")
        rule.setContent{NotesTheme(if(dark)ThemeMode.DARK else ThemeMode.LIGHT){
            key(is24,calendar){if(open){
                if(calendar)DatePickerModal(LocalDate.of(2026,10,28),{open=false},{pickedDate=it})
                else TimePickerModal(LocalTime.of(23,46),{open=false},{picked=it})
            }}
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
        calendar=true;open=true;settle()
        val date=ctx.getString(R.string.picker_date)
        replace(date,"2026-02-30");disabled()
        replace(date,"2024-02-29");confirm()
        assertEquals(LocalDate.of(2024,2,29),pickedDate)
    }

    @Test fun visitPackLongConfirmationDoesNotOverlapCancel() {
        val ctx=rule.activity
        var open by mutableStateOf(false)
        rule.setContent{NotesTheme(if(dark)ThemeMode.DARK else ThemeMode.LIGHT){
            if(open)VisitPackDialog(AppointmentEntity(id=1,type="ENDO",at_utc=Instant.now().toEpochMilli(),at_zone="UTC",remind_minutes_before=60),
                NotesState(loading=false),net.plainnotes.app.NotesViewModel.ExtraState(),{open=false}){_,_->}
        }}
        rule.mainClock.autoAdvance=false
        fun settle(){val l=org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper());repeat(4){rule.mainClock.advanceTimeBy(500);var n=0;while(!l.isIdle && n++<500)l.runOneTask()}}
        open=true;settle()
        val roots=mutableListOf<androidx.compose.ui.platform.ViewRootForTest>()
        fun walk(v:android.view.View){if(v is androidx.compose.ui.platform.ViewRootForTest)roots+=v;if(v is android.view.ViewGroup)repeat(v.childCount){walk(v.getChildAt(it))}}
        org.robolectric.shadows.ShadowDialog.getShownDialogs().filter{it.isShowing}.forEach{it.window?.decorView?.let(::walk)}
        fun all(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::all)
        val nodes=roots.flatMap{all(it.semanticsOwner.rootSemanticsNode)}
        fun control(res:Int)=nodes.single{it.config.getOrNull(SemanticsProperties.Role)==Role.Button && it.config.getOrNull(SemanticsProperties.Text)?.any{t->t.text==ctx.getString(res)}==true}
        val export=control(R.string.export_pdf);val cancel=control(R.string.cancel)
        val a=export.boundsInRoot;val b=cancel.boundsInRoot
        assertFalse("PDF confirmation overlaps cancel",minOf(a.right,b.right)>maxOf(a.left,b.left) && minOf(a.bottom,b.bottom)>maxOf(a.top,b.top))
        assertTrue("PDF action group widths differ",kotlin.math.abs(a.width-b.width)<=1f)
        assertTrue("PDF action group heights differ",kotlin.math.abs(a.height-b.height)<=1f)
        cancel.config[SemanticsActions.OnClick].action!!.invoke();settle();assertFalse(open)
    }

    @Test fun timelineDetailsAreOnlyInTheMenuAndAllRecordsRemainAccessible() {
        val ctx=rule.activity
        val zone=ZoneId.systemDefault();val start=LocalDate.now(zone).minusDays(60)
        val med=MedicationEntity(id=1,name="Synthetic medication",molecule="E2",route="SUBLINGUAL",unit="MG",dose_per_intake=2.0,container_capacity=30.0,
            site_rotation=false,notifications_on=false,active=true,sort_order=0)
        val identity=MedicationSnapshot.encode(med,ProfileEntity(1,"E2","sublingual"))
        val rows=(0..20).map{day->RecordEntity(id=day+1L,medication_id=1,taken_utc=start.plusDays(day.toLong()).atStartOfDay(zone).plusHours(8).toInstant().toEpochMilli(),
            taken_zone=zone.id,actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=identity)}
        val standard=net.plainnotes.app.domain.TherapyStandard("E2","E2","SUBLINGUAL","MG",null,"EVERY_N_DAYS",1,0,listOf(2.0))
        val user=HistoryPeriodEntity(id=1,period_key="synthetic-user",revision=1,state=HistoryPeriods.CONFIRMED,medication_id=1,
            identity_json=identity,standard_json=HistoryPeriods.standardJson(standard),from_date=start.toString(),until_date=start.plusDays(22).toString(),zone=zone.id,
            evidence_json="{}",origin=HistoryPeriods.USER_ORIGIN,created_utc=Instant.now().toEpochMilli(),kind=HistoryPeriods.PERIOD)
        var extra by mutableStateOf(net.plainnotes.app.NotesViewModel.ExtraState(records=rows,historyPeriods=listOf(user)))
        var selected:List<Long>?=null
        rule.setContent{NotesTheme(if(dark)ThemeMode.DARK else ThemeMode.LIGHT){Surface{
            LongitudinalScreen(NotesState(medications=listOf(med),loading=false),extra,{},{},{},PaddingValues(),onImportedHistory={selected=it})
        }}}
        val key=net.plainnotes.app.timeline.PeriodTimelineProjection.build(extra,emptyList()).projection.periods.single{it.finalStandardSpanKeys.isNotEmpty()}.key
        rule.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag("period-menu:$key"))
        rule.onNodeWithTag("period-history:$key").assertDoesNotExist()
        rule.onNodeWithText(ctx.getString(R.string.period_saved_changes)).assertDoesNotExist()
        rule.onNodeWithText(ctx.getString(R.string.period_user_edited)).assertDoesNotExist()
        rule.onNodeWithText(ctx.getString(R.string.period_inferred)).assertDoesNotExist()
        rule.onNodeWithTag("period-menu:$key").performClick()
        rule.onNodeWithTag("period-diagnostics:$key").assertDoesNotExist()
        rule.onNodeWithTag("period-edit-any:$key").assertExists()
        rule.onNodeWithTag("period-delete:$key").assertExists()
        rule.onNodeWithTag("period-details:$key").assertExists()
        val records=rule.onNodeWithTag("period-history:$key").assertTextContains(ctx.getString(R.string.period_records))
        val node=records.fetchSemanticsNode();val results=mutableListOf<TextLayoutResult>()
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),useUnmergedTree=true).fetchSemanticsNodes().filter{n->
            n.config[SemanticsProperties.Text].any{it.text==ctx.getString(R.string.period_records)}}.forEach{n->
            n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        }
        assertTrue(results.isNotEmpty());results.forEach{r->
            val last=r.lineCount-1;val d=ctx.resources.displayMetrics.density
            assertFalse("Menu text clipped: ${r.layoutInput.text} size=${r.size} bottom=${r.getLineBottom(last)}",r.isLineEllipsized(last) ||
                r.getLineEnd(last,visibleEnd=false)<r.layoutInput.text.length || r.getLineBottom(last)-r.size.height>2*d ||
                (0..last).maxOf{r.getLineRight(it)-r.getLineLeft(it)}-r.size.width>d)
        }
        assertTrue(node.boundsInRoot.height>=48*ctx.resources.displayMetrics.density-1)
        records.performClick();rule.runOnIdle{assertEquals(rows.map{it.id},selected);extra=extra.copy(historyPeriods=emptyList())}
        // Automatic recognition is still explicitly qualified, never presented as a user statement.
        rule.onNodeWithTag("period-timeline").performScrollToNode(hasText(ctx.getString(R.string.period_inferred)))
        rule.onNodeWithText(ctx.getString(R.string.period_inferred)).assertIsDisplayed()
        assertTrue(Destination.entries.contains(Destination.VISITS));assertEquals(R.string.visits,Destination.VISITS.title);assertTrue(Destination.VISITS.ready)
    }

}
