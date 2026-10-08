package net.plainnotes.app.timeline

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import net.plainnotes.app.*
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import org.junit.Test
import org.junit.Rule
import java.time.LocalDate

/** Native long-scroll/accessibility smoke test with synthetic milestones; no health data. */
class TimelineAndroidTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test fun largeFontHistoricalTimelineScrollsToTheExactOldSource() {
        val today=LocalDate.now()
        val extra=NotesViewModel.ExtraState(milestones=(1..160).map{MilestoneEntity(it.toLong(),today.minusDays(it.toLong()*4).toString(),"CUSTOM","Synthetic $it","Synthetic exact-source $it")})
        ui.setContent{val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)){MaterialTheme{LongitudinalScreen(NotesState(loading=false),extra,{},{},{},PaddingValues())}}}
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag("timeline:milestone:160"))
        ui.onNodeWithTag("timeline:milestone:160").assertHasClickAction().performClick()
        ui.onNodeWithText("Synthetic exact-source 160").assertIsDisplayed()
    }
    @Test fun oldImportedHistoryWithoutARegimenOpensExactSources() {
        val time=java.time.Instant.now().minusSeconds(400*86400L).toEpochMilli()
        val medication=MedicationEntity(1,"Synthetic frozen import","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
        val row=RecordEntity(71,1,taken_utc=time,taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="IMPORT_HT",source_record_key="ht:synthetic",revision=1,
            config_snapshot=MedicationSnapshot.encode(medication,ProfileEntity(1,"E2","sublingual")))
        var opened:List<Long>?=null
        ui.setContent{MaterialTheme{LongitudinalScreen(NotesState(loading=false),NotesViewModel.ExtraState(records=listOf(row)),{},{},{},PaddingValues(),onImportedHistory={opened=it})}}
        ui.onNodeWithTag("timeline:history:unknown:false").assertIsDisplayed().performClick()
        ui.onNodeWithText("Synthetic frozen import").assertIsDisplayed()
        ui.onNodeWithText(ui.activity.getString(R.string.timeline_imported_open_history)).performClick()
        ui.runOnIdle{org.junit.Assert.assertEquals(listOf(71L),opened)}
    }
    @Test fun simpleModeImportedDetailHidesFrozenMedicationNames() {
        val time=java.time.Instant.now().minusSeconds(400*86400L).toEpochMilli()
        val row=RecordEntity(71,1,taken_utc=time,taken_zone="UTC",actual_dose=null,status="ON_TIME",origin="IMPORT_TM",source_record_key="tm:synthetic",revision=1,
            config_snapshot="""{"name":"Synthetic private import","molecule":"E2","unit":"MG"}""")
        ui.setContent{CompositionLocalProvider(LocalSimpleMode provides true){MaterialTheme{
            LongitudinalScreen(NotesState(loading=false),NotesViewModel.ExtraState(records=listOf(row)),{},{},{},PaddingValues())}}}
        ui.onNodeWithTag("timeline:history:unknown:false").performClick()
        ui.onNodeWithText("Synthetic private import").assertDoesNotExist()
        ui.onNodeWithText(ui.activity.getString(R.string.timeline_imported_open_history)).assertIsDisplayed()
    }
    @Test fun sameDaySavedChangesWithExistingImportedHistoryCanOpenTimeline() {
        val base=java.time.Instant.parse("2025-01-01T08:00:00Z");val cut=base.plusSeconds(4*86400L)
        val med=MedicationEntity(1,"Synthetic native history","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
        val json=MedicationSnapshot.encode(med,ProfileEntity(1,"E2","sublingual"))
        fun version(id:Long,from:java.time.Instant,until:java.time.Instant?,dose:Double):RegimenVersionEntity {
            val d=RegimenDefinition(json,"EVERY_N_DAYS",1,0,dose,"UTC","2025-01-01",null,listOf("08:00:00" to null))
            return RegimenVersionEntity(id,1,from.toEpochMilli(),until?.toEpochMilli(),"UTC",d.json(),d.signature(),"APP",from.toEpochMilli())
        }
        val rows=(0..7).map{day->RecordEntity(day+1L,1,taken_utc=base.plusSeconds(day*86400L).toEpochMilli(),taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="IMPORT_HT",source_record_key="ht:synthetic:$day",revision=1,config_snapshot=json)}
        val extra=NotesViewModel.ExtraState(records=rows,regimens=listOf(version(1,cut,cut.plusSeconds(4*3600),3.0),version(2,cut.plusSeconds(4*3600),null,2.0)))
        ui.setContent{MaterialTheme{LongitudinalScreen(NotesState(loading=false),extra,{},{},{},PaddingValues())}}
        ui.onNodeWithTag("period-timeline").assertIsDisplayed()
        ui.onNodeWithText(ui.activity.getString(R.string.period_current)).assertIsDisplayed()
        ui.onNodeWithText(ui.activity.getString(R.string.period_recognition_unavailable)).assertDoesNotExist()
        val audit=ui.activity.getString(R.string.period_saved_changes)
        val key=net.plainnotes.app.timeline.PeriodTimelineProjection.build(extra,emptyList()).projection.periods.last{it.finalStandardSpanKeys.isNotEmpty()}.key
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag("period-menu:$key"))
        ui.onNodeWithText(audit).assertDoesNotExist()
        ui.onNodeWithTag("period-menu:$key").performClick()
        ui.onNodeWithTag("period-details:$key").performClick()
        // The original 3 mg version stays auditable (§37a also notes it on the card while it is shown inside the period).
        ui.onNode(hasText("3 mg",substring=true) and hasAnyAncestor(isDialog())).assertIsDisplayed()
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
        // The name also appears in the "compared with the previous period" line of the later empty card, so scroll to the frequency.
        val frequency=ui.activity.getString(R.string.period_frequency_days,2,1)
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasText(frequency))
        ui.onNodeWithText(frequency).assertIsDisplayed()
        ui.onAllNodesWithText("Synthetic partial logging",substring=true).onFirst().assertExists()
        ui.onNodeWithText(ui.activity.getString(R.string.timeline_imported_history)).assertDoesNotExist()
        val tag="period-history:${projection.projection.periods.first{it.finalStandardSpanKeys.isNotEmpty()}.key}"
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag(tag.replace("period-history:","period-menu:")))
        ui.onNodeWithTag(tag.replace("period-history:","period-menu:")).performClick()
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
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag(tag.replace("period-history:","period-menu:")))
        ui.onNodeWithTag(tag.replace("period-history:","period-menu:")).performClick()
        ui.onNodeWithTag(tag).performClick()
        ui.runOnIdle{org.junit.Assert.assertEquals((before+after).map{it.id},opened)}
    }

}
