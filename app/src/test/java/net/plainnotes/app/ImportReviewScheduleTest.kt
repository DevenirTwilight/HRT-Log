package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RuleKind
import net.plainnotes.app.timeline.RecognizedSchedule
import net.plainnotes.app.timeline.ScheduleRecognition
import net.plainnotes.app.ui.MedicationDraft
import net.plainnotes.app.ui.MedicationEditor
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

/** REQUIREMENTS §37a: the import review sheet is prefilled from the imported records and asks before saving a different schedule. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class ImportReviewScheduleTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val zone=ZoneId.of("Europe/Paris")
    private val review="""{"source":"hrttracker","raw":{"intakeInterval":"","soonAlertDelay":"","lateAlertDelay":"","notifications":""}}"""
    private val med=MedicationEntity(7,"Synthetic import","E2","SUBLINGUAL","MG",2.0,40.0,soon_alert_minutes=15,late_after_minutes=60,site_rotation=false,notifications_on=false,active=true,sort_order=0,needs_review=review)
    private fun records()=(0L..40L).flatMap{d->listOf(LocalTime.of(8,5),LocalTime.of(20,10)).mapIndexed{i,t->
        RecordEntity(d*2+i+1,7,taken_utc=LocalDate.of(2026,8,1).plusDays(d).atTime(t).atZone(zone).toInstant().toEpochMilli(),taken_zone=zone.id,actual_dose=2.0,
            status="ON_TIME",origin="IMPORT_HT",source_record_key="ht:synthetic:review:$d:$i",revision=1,config_snapshot="{}")}}

    @Test fun twiceDailyHistoryIsRecognisedWithItsUsualTimes() {
        assertEquals(RecognizedSchedule(1,listOf(LocalTime.of(8,0),LocalTime.of(20,15)),listOf(2.0,2.0)),ScheduleRecognition.of(records(),zone))
        assertNull(ScheduleRecognition.of(emptyList(),zone))
    }

    @Test fun reviewSheetIsPrefilledAndAMismatchNeedsConfirmation() {
        var saved:MedicationDraft?=null
        val recognized=ScheduleRecognition.of(records(),zone)
        ui.setContent{MaterialTheme{MedicationEditor(EditMedication(med,ProfileEntity(7,"E2","sublingual",sl_tier=2),null,emptyList(),recognized),{},inDialog=false){saved=it}}}
        ui.onNodeWithTag("schedule-recognized").performScrollTo().assertExists()
        // Typing 11 instead of 1, as happened after the real import.
        ui.onNode(hasSetTextAction() and hasText("1")).performTextReplacement("11")
        ui.onNodeWithText(ui.activity.getString(R.string.save)).performClick()
        ui.onNodeWithTag("schedule-mismatch").assertExists()
        ui.runOnIdle{assertNull(saved)}
        ui.onNodeWithText(ui.activity.getString(R.string.review_schedule_back)).performClick()
        ui.onNode(hasSetTextAction() and hasText("11")).performTextReplacement("1")
        ui.onNodeWithText(ui.activity.getString(R.string.save)).performClick()
        ui.onNodeWithTag("schedule-mismatch").assertDoesNotExist()
        ui.runOnIdle{assertEquals(RuleKind.EVERY_N_DAYS,saved!!.kind);assertEquals(1,saved!!.interval);assertEquals(listOf(LocalTime.of(8,0),LocalTime.of(20,15)),saved!!.times)}
    }
}
