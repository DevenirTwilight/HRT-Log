package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class HistoricalContextUiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test fun explicitConfirmationUsesChosenHistoricalDateAndDoesNotWriteBeforeConfirm() {
        val m=MedicationEntity(1,"Synthetic history","E2","ORAL","MG",2.0,40.0,site_rotation=false,notifications_on=true,active=true,sort_order=0)
        val at=Instant.parse("2026-03-10T12:00:00Z");val day=at.atZone(ZoneId.systemDefault()).toLocalDate()
        val record=RecordEntity(1,1,taken_utc=at.toEpochMilli(),taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="IMPORT_HT",revision=1,config_snapshot="{\"source\":\"hrttracker\"}")
        var saved:Triple<MedicationEntity,LocalDate,LocalDate>?=null
        ui.setContent{MaterialTheme{HistoricalContextDialog(m,ProfileEntity(1,"EV","oral"),listOf(record),{}){med,_,from,to->saved=Triple(med,from,to)}}}
        ui.runOnIdle{assertNull(saved)}
        ui.onNodeWithText(ui.activity.getString(R.string.history_context_confirm)).assertIsEnabled().performClick()
        ui.runOnIdle{assertEquals(day,saved!!.second);assertEquals(day,saved!!.third);assertEquals("ORAL",saved!!.first.route)}
    }
}
