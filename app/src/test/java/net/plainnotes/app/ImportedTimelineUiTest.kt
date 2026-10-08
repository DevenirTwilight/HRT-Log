package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class) @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],application=android.app.Application::class,qualifiers="zh-rCN-w411dp-h891dp-xxhdpi")
class ImportedTimelineUiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val med=MedicationEntity(1,"Synthetic frozen import","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
    private fun row()=RecordEntity(71,1,taken_utc=Instant.now().minusSeconds(400*86400L).toEpochMilli(),taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="IMPORT_HT",source_record_key="ht:synthetic",revision=1,
        config_snapshot=MedicationSnapshot.encode(med,ProfileEntity(1,"E2","sublingual")))
    @Test fun importedSummaryOpensFrozenDetailsAndExactHistoryIds() {
        var selected:List<Long>?=null
        ui.setContent{MaterialTheme{LongitudinalScreen(NotesState(loading=false),NotesViewModel.ExtraState(records=listOf(row())),{},{},{},PaddingValues(),onImportedHistory={selected=it})}}
        ui.onNodeWithTag("timeline:history:unknown:false").assertIsDisplayed()
        val view=ui.activity.window.decorView
        val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        java.io.File("build/screenshots").mkdirs()
        java.io.File("build/screenshots/imported_timeline_overview.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        ui.onNodeWithTag("timeline:history:unknown:false").performClick()
        ui.onNodeWithText("Synthetic frozen import").assertIsDisplayed()
        ui.onAllNodesWithText(ui.activity.getString(R.string.status_on_time),substring=true).assertCountEquals(0)
        ui.onNodeWithText(ui.activity.getString(R.string.timeline_imported_open_history)).performClick()
        ui.runOnIdle{assertEquals(listOf(71L),selected)}
    }
    @Test fun importingOrDeletingRecordsRefreshesTheProjectionWithoutChangingRegimens() {
        val extra=mutableStateOf(NotesViewModel.ExtraState())
        ui.setContent{MaterialTheme{LongitudinalScreen(NotesState(loading=false),extra.value,{},{},{},PaddingValues())}}
        ui.onNodeWithTag("timeline:history:unknown:false").assertDoesNotExist()
        ui.runOnIdle{extra.value=extra.value.copy(records=listOf(row()))}
        ui.onNodeWithTag("timeline:history:unknown:false").assertIsDisplayed()
        ui.runOnIdle{extra.value=extra.value.copy(records=listOf(row().copy(deleted_at_utc=Instant.now().toEpochMilli())))}
        ui.onNodeWithTag("timeline:history:unknown:false").assertDoesNotExist()
    }
    @Test fun selectedOriginalHistoryBypassesTheDefaultThirtyDayWindowAndExcludesOtherSources() {
        val selected=row();val other=med.copy(id=2,name="Synthetic excluded source")
        val excluded=selected.copy(id=72,medication_id=2,config_snapshot=MedicationSnapshot.encode(other,null))
        ui.setContent{MaterialTheme{HistoryScreen(NotesState(medications=listOf(med,other),loading=false),listOf(selected,excluded),{},{},PaddingValues(),selectedRecordIds=setOf(71))}}
        ui.onNodeWithTag("history-records").performScrollToNode(hasTestTag("history-record:71"))
        ui.onNodeWithTag("history-record:71").assertIsDisplayed()
        ui.onNodeWithTag("history-record:72").assertDoesNotExist()
    }
    @Test fun recognizedHistoryIsInsideTreatmentPeriodAndOpensItsOriginalRecords() {
        val base=Instant.parse("2025-01-01T08:00:00Z")
        val rows=(0..4).map{day->row().copy(id=day+1L,taken_utc=base.plusSeconds(day*86400L).toEpochMilli(),source_record_key="ht:synthetic:$day")}
        var selected:List<Long>?=null
        val extra=NotesViewModel.ExtraState(records=rows)
        val period=net.plainnotes.app.timeline.PeriodTimelineProjection.build(extra,emptyList()).projection.periods.first()
        ui.setContent{MaterialTheme{LongitudinalScreen(NotesState(loading=false),extra,{},{},{},PaddingValues(),onImportedHistory={selected=it})}}
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasText("Synthetic frozen import",substring=true))
        ui.onNodeWithText("Synthetic frozen import",substring=true).assertIsDisplayed()
        ui.onNodeWithText(ui.activity.getString(R.string.period_observed)).assertDoesNotExist()
        ui.onNodeWithText(ui.activity.getString(R.string.timeline_imported_history)).assertDoesNotExist()
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag("period-history:${period.key}"))
        ui.onNodeWithTag("period-history:${period.key}").performClick()
        ui.runOnIdle{assertEquals(rows.map{it.id},selected)}
        val view=ui.activity.window.decorView
        val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        java.io.File("build/screenshots").mkdirs()
        java.io.File("build/screenshots/recognized_treatment_period.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }

    @Test fun timelineEntryWithSameDaySavedChangesAndExistingImportedHistoryDoesNotCrash() {
        val base=Instant.parse("2025-01-01T08:00:00Z");val cut=base.plusSeconds(4*86400L)
        val json=MedicationSnapshot.encode(med,ProfileEntity(1,"E2","sublingual"))
        fun version(id:Long,from:Instant,until:Instant?,dose:Double):RegimenVersionEntity {
            val d=RegimenDefinition(json,"EVERY_N_DAYS",1,0,dose,"UTC","2025-01-01",null,listOf("08:00:00" to null))
            return RegimenVersionEntity(id,1,from.toEpochMilli(),until?.toEpochMilli(),"UTC",d.json(),d.signature(),"APP",from.toEpochMilli())
        }
        val extra=NotesViewModel.ExtraState(records=(0..7).map{day->row().copy(id=day+1L,taken_utc=base.plusSeconds(day*86400L).toEpochMilli(),source_record_key="ht:synthetic:$day")},
            regimens=listOf(version(1,cut,cut.plusSeconds(4*3600),3.0),version(2,cut.plusSeconds(4*3600),null,2.0)))
        ui.setContent{MaterialTheme{LongitudinalScreen(NotesState(loading=false),extra,{},{},{},PaddingValues())}}
        ui.onNodeWithTag("period-timeline").assertIsDisplayed()
        ui.onNodeWithText(ui.activity.getString(R.string.period_current)).assertIsDisplayed()
        ui.onNodeWithText(ui.activity.getString(R.string.period_recognition_unavailable)).assertDoesNotExist()
        val audit=ui.activity.getString(R.string.period_saved_changes)
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasText(audit))
        ui.onAllNodesWithText(audit).onFirst().performClick()
        ui.onNodeWithText("3 mg",substring=true).assertIsDisplayed()
    }

    @Test fun twiceDailyWithPartialDaysAppearsInsideOnePeriodAndOpensAllOriginalRows() {
        val base=java.time.Instant.parse("2025-01-01T08:00:00Z")
        val medication=MedicationEntity(1,"Synthetic partial logging","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
        val json=MedicationSnapshot.encode(medication,ProfileEntity(1,"E2","sublingual"))
        val rows=(0..20).flatMap{day->(if(day%3==1)listOf(0) else listOf(0,10)).mapIndexed{slot,hour->
            RecordEntity(day*2+slot+1L,1,taken_utc=base.plusSeconds((day*24+hour)*3600L).toEpochMilli(),taken_zone="UTC",actual_dose=2.0,
                status="ON_TIME",origin="IMPORT_HT",source_record_key="ht:synthetic:partial:$day:$slot",revision=1,config_snapshot=json)
        }}
        val extra=NotesViewModel.ExtraState(records=rows)
        val projection=net.plainnotes.app.timeline.PeriodTimelineProjection.build(extra,emptyList())
        org.junit.Assert.assertEquals(1,projection.projection.standards.size)
        var opened:List<Long>?=null
        ui.setContent{MaterialTheme{LongitudinalScreen(NotesState(loading=false),extra,{},{},{},PaddingValues(),onImportedHistory={opened=it})}}
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasText("Synthetic partial logging",substring=true))
        ui.onNodeWithText("Synthetic partial logging",substring=true).assertIsDisplayed()
        ui.onNodeWithText(ui.activity.getString(R.string.timeline_imported_history)).assertDoesNotExist()
        ui.onNodeWithText(ui.activity.getString(R.string.period_frequency_days,2,1)).assertIsDisplayed()
        val tag="period-history:${projection.projection.periods.first{it.finalStandardSpanKeys.isNotEmpty()}.key}"
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag(tag))
        ui.onNodeWithTag(tag).performClick()
        ui.runOnIdle{org.junit.Assert.assertEquals(rows.map{it.id},opened)}
    }

    @Test fun sameRegimenAcrossOctoberSixHasOneOrdinaryPeriodAndAllSources() {
        val base=java.time.Instant.parse("2026-09-25T01:00:00Z")
        val cut=java.time.Instant.parse("2026-10-06T08:00:00Z")
        val med=MedicationEntity(1,"Synthetic joined regimen","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
        val json=MedicationSnapshot.encode(med,ProfileEntity(1,"E2","sublingual"))
        val before=(0..10).flatMap{day->listOf(0,12).mapIndexed{slot,h->RecordEntity(day*2+slot+1L,1,
            taken_utc=base.plusSeconds((day*24+h)*3600L).toEpochMilli(),taken_zone="Asia/Shanghai",actual_dose=2.0,status="ON_TIME",origin="IMPORT_HT",
            source_record_key="ht:synthetic:joined:$day:$h",revision=1,config_snapshot=json)}}
        val after=before.first().copy(id=101,taken_utc=cut.toEpochMilli(),taken_zone="UTC",origin="APP",source_record_key=null)
        val d=RegimenDefinition(json,"EVERY_N_HOURS",12,0,2.0,"UTC",null,cut.toEpochMilli(),emptyList())
        val version=RegimenVersionEntity(1,1,cut.toEpochMilli(),null,"UTC",d.json(),d.signature(),"APP",cut.toEpochMilli())
        val extra=NotesViewModel.ExtraState(records=before+after,regimens=listOf(version))
        val projection=net.plainnotes.app.timeline.PeriodTimelineProjection.build(extra,emptyList())
        org.junit.Assert.assertEquals(1,projection.projection.periods.size)
        var opened:List<Long>?=null
        ui.setContent{MaterialTheme{LongitudinalScreen(NotesState(loading=false),extra,{},{},{},PaddingValues(),onImportedHistory={opened=it})}}
        ui.onNodeWithText("Synthetic joined regimen",substring=true).assertIsDisplayed()
        ui.onNodeWithText(ui.activity.getString(R.string.period_past)).assertDoesNotExist()
        ui.onNodeWithText(ui.activity.getString(R.string.period_observed)).assertDoesNotExist()
        ui.onNodeWithText(ui.activity.getString(R.string.epoch_reconstructed)).assertDoesNotExist()
        val tag="period-history:${projection.projection.periods.single().key}"
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag(tag))
        ui.onNodeWithTag(tag).performClick()
        ui.runOnIdle{org.junit.Assert.assertEquals((before+after).map{it.id},opened)}
    }

    @Test fun importedAndAppHistoryWithoutAPeriodShowRegimenUnknownNotUnscheduled() {
        // REQUIREMENTS §35a: a missing scheduled time no longer means "unscheduled"; both sources are judged by their period.
        val first=row();val other=first.copy(id=72,origin="APP",source_record_key=null)
        val extra=NotesViewModel.ExtraState(records=listOf(first,other))
        val labels=net.plainnotes.app.timeline.HistoryLabels.build(extra.records,net.plainnotes.app.timeline.PeriodTimelineProjection.build(extra,emptyList()),emptyList(),java.time.ZoneId.of("UTC"))
        org.junit.Assert.assertEquals(mapOf(71L to setOf(net.plainnotes.app.domain.RecordLabel.REGIMEN_UNKNOWN),72L to setOf(net.plainnotes.app.domain.RecordLabel.REGIMEN_UNKNOWN)),labels)
        ui.setContent{MaterialTheme{HistoryScreen(NotesState(medications=listOf(med),loading=false),listOf(first,other),{},{},PaddingValues(),selectedRecordIds=setOf(71,72),labels=labels)}}
        ui.onNodeWithTag("history-records").performScrollToNode(hasTestTag("history-record:71"))
        ui.onNodeWithText(ui.activity.getString(R.string.history_imported_short)).assertDoesNotExist()
        ui.onNodeWithText(ui.activity.getString(R.string.history_unscheduled_short)).assertDoesNotExist()
        ui.onAllNodesWithText(ui.activity.getString(R.string.history_regimen_unknown)).assertCountEquals(2)
    }

    @Test fun recognisedPeriodIsPendingUntilTheUserConfirmsIt() {
        val base=Instant.now().minusSeconds(60*86400L).truncatedTo(java.time.temporal.ChronoUnit.DAYS)
        val json=MedicationSnapshot.encode(med,ProfileEntity(1,"E2","sublingual"))
        val rows=(0..29).flatMap{day->listOf(8,20).mapIndexed{slot,h->RecordEntity(day*2+slot+1L,1,taken_utc=base.plusSeconds((day*24+h)*3600L).toEpochMilli(),taken_zone="UTC",
            actual_dose=2.0,status="ON_TIME",origin="IMPORT_HT",source_record_key="ht:synthetic:pending:$day:$h",revision=1,config_snapshot=json)}}
        val extra=NotesViewModel.ExtraState(records=rows)
        val span=net.plainnotes.app.timeline.PeriodTimelineProjection.build(extra,emptyList()).observed.single().interval.span.id
        var confirmed:net.plainnotes.app.domain.TherapyStandard?=null;var from:java.time.LocalDate?=null
        ui.setContent{MaterialTheme{LongitudinalScreen(NotesState(loading=false),extra,{},{},{},PaddingValues(),
            periodActions=PeriodActions(confirm={key,_,standard,f,_,_,_,evidence->assertNull(key);assertTrue(evidence.contains("sustained-patterns-v1"));confirmed=standard;from=f}))}}
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag("period-confirm:$span"))
        ui.onNodeWithText(ui.activity.getString(R.string.period_pending)).assertExists()
        ui.onNodeWithTag("period-confirm:$span").performClick()
        // The dialog's own behaviour is in HistoryPeriodDialogTest; this screen relayouts forever under Robolectric once a dialog with text fields is open.
        ui.mainClock.autoAdvance=false
        val looper=org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        repeat(4){ui.mainClock.advanceTimeBy(500);var n=0;while(!looper.isIdle && n++<500)looper.runOneTask()}
        val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();assertTrue(dialog!=null && dialog.isShowing);dialog.dismiss()
        assertNull(confirmed);assertNull(from)
    }

}
