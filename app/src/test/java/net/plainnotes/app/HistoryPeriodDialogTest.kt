package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.domain.TherapyStandard
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

/** REQUIREMENTS §35a: the confirmation page saves only what the user confirms. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class HistoryPeriodDialogTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val d0=LocalDate.of(2026,6,10)
    private val standard=TherapyStandard("E2",null,"SUBLINGUAL","MG",null,"EVERY_N_DAYS",1,0,listOf(2.0,2.0))

    private val med=net.plainnotes.app.data.MedicationEntity(1,"Synthetic E2","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
    @Test fun ordinaryEditorSavesTheStandardAndExclusiveDateRange() {
        var saved:List<Any?>?=null
        val request=PeriodEditRequest(R.string.period_edit_title_edit,listOf(1L),1L,net.plainnotes.app.timeline.TimelineEdits.Range(d0,d0.plusDays(30)),standard,null)
        ui.setContent{MaterialTheme{PeriodEditDialog(request,NotesState(medications=listOf(med)),{_,_,_->false},{m,s,r->saved=listOf(m,s,r)},{})}}
        ui.onNodeWithTag("period-edit-save").performClick()
        ui.runOnIdle{assertEquals(listOf(1L,standard,request.range),saved)}
    }
    @Test fun simpleModeDoesNotExposeTheDrugOrDose() {
        val request=PeriodEditRequest(R.string.period_edit_title_edit,listOf(1L),1L,net.plainnotes.app.timeline.TimelineEdits.Range(d0,null),standard,null)
        ui.setContent{MaterialTheme{androidx.compose.runtime.CompositionLocalProvider(LocalSimpleMode provides true){
            PeriodEditDialog(request,NotesState(medications=listOf(med)),{_,_,_->false},{_,_,_->},{})}}}
        ui.onNodeWithText(med.name).assertDoesNotExist()
        ui.onNodeWithText("#1").assertExists()
        ui.onNodeWithText(ui.activity.getString(R.string.period_dose_n,1)).assertDoesNotExist()
        ui.onNodeWithTag("period-edit-save").assertIsEnabled()
    }
}
