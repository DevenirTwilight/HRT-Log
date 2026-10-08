package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.TherapyStandard
import net.plainnotes.app.timeline.PeriodTimelineProjection
import net.plainnotes.app.timeline.TimelineEdits
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

/** REQUIREMENTS §37b entry points: card menu, new period, merge choice, simple mode. Synthetic data. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class TimelineEditUiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val zone=ZoneId.systemDefault()
    private val med=MedicationEntity(1,"Synthetic E2","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
    private val start=LocalDate.now().minusDays(80)
    private fun at(d:Long)=start.plusDays(d).atStartOfDay(zone).toInstant().toEpochMilli()
    private val json=MedicationSnapshot.encode(med,ProfileEntity(1,"E2","sublingual"))
    private fun version(id:Long,from:Long,until:Long?,dose:Double):RegimenVersionEntity {
        val d=RegimenDefinition(json,"EVERY_N_DAYS",1,0,dose,zone.id,start.toString(),null,listOf("08:00:00" to null,"20:00:00" to null))
        return RegimenVersionEntity(id,1,at(from),until?.let(::at),zone.id,d.json(),d.signature(),"APP",at(from))
    }
    private val extra=NotesViewModel.ExtraState(regimens=listOf(version(1,0,30,2.0),version(2,30,null,3.0)))
    private val state=NotesState(medications=listOf(med),loading=false)
    private val twice=TherapyStandard("E2","E2","SUBLINGUAL","MG",null,"EVERY_N_DAYS",1,0,listOf(2.0,2.0))

    @Test fun mergingDifferentStandardsCannotBeSavedUntilOneIsChosen() {
        var saved:TherapyStandard?=null
        val request=PeriodEditRequest(R.string.period_edit_title_merge,listOf(1L),1L,TimelineEdits.Range(start,start.plusDays(60)),twice,null,merge=twice to twice.copy(doses=listOf(3.0,3.0)))
        ui.setContent{MaterialTheme{PeriodEditDialog(request,state,{_,_,_->false},{_,s,_->saved=s}){}}}
        ui.onNodeWithTag("period-edit-save").assertIsNotEnabled()
        ui.onNodeWithTag("merge-choice:next").performClick()
        ui.onNodeWithTag("period-edit-save").assertIsEnabled().performClick()
        ui.runOnIdle{assertEquals(listOf(3.0,3.0),saved!!.doses);assertEquals(1,saved!!.interval)}
    }

    @Test fun overlapWithTheSameMedicineIsShownAndBlocksSaving() {
        val request=PeriodEditRequest(R.string.period_edit_title_create,listOf(1L),1L,TimelineEdits.Range(start,start.plusDays(10)),null,null)
        ui.setContent{MaterialTheme{PeriodEditDialog(request,state,{_,_,_->true},{_,_,_->}){}}}
        ui.onNodeWithTag("period-overlap").assertExists();ui.onNodeWithTag("period-edit-save").assertIsNotEnabled()
    }

    @Test fun cardMenuDeletesThroughTheRepositoryRowsAndSimpleModeCanStillEdit() {
        var edits:List<TimelineEditRow>?=null
        val key=PeriodTimelineProjection.build(extra,emptyList()).projection.periods.first().key
        ui.setContent{MaterialTheme{CompositionLocalProvider(LocalSimpleMode provides true){
            LongitudinalScreen(state,extra,{},{},{},PaddingValues(),timelineActions=TimelineActions({_,rows->edits=rows}))}}}
        ui.onNodeWithTag("timeline-new-period").assertExists()
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag("period-menu:$key"))
        ui.onNodeWithTag("period-menu:$key").performClick()
        ui.onNodeWithTag("period-edit-any:$key").assertExists()
        ui.onNodeWithTag("period-merge:$key").assertExists()
        ui.onNodeWithTag("period-diagnostics:$key").assertDoesNotExist()
        ui.onNodeWithTag("period-delete:$key").performClick()
        ui.onNodeWithTag("period-delete-dialog").assertExists()
        ui.onNode(hasText(ui.activity.getString(R.string.period_menu_delete)) and hasAnyAncestor(isDialog()) and hasClickAction()).performClick()
        ui.runOnIdle{assertEquals(listOf(HistoryPeriods.DELETED),edits!!.map{it.kind});assertEquals(start,edits!!.single().from);assertEquals(start.plusDays(30),edits!!.single().until)}
    }
}
