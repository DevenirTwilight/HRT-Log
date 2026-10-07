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

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class,qualifiers="zh")
class ImportedTimelineUiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val med=MedicationEntity(1,"Synthetic frozen import","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
    private fun row()=RecordEntity(71,1,taken_utc=Instant.now().minusSeconds(400*86400L).toEpochMilli(),taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="IMPORT_HT",source_record_key="ht:synthetic",revision=1,
        config_snapshot=MedicationSnapshot.encode(med,ProfileEntity(1,"E2","sublingual")))
    @Test fun importedSummaryOpensFrozenDetailsAndExactHistoryIds() {
        var selected:List<Long>?=null
        ui.setContent{MaterialTheme{LongitudinalScreen(NotesState(loading=false),NotesViewModel.ExtraState(records=listOf(row())),{},{},{},PaddingValues(),onImportedHistory={selected=it})}}
        ui.onNodeWithTag("timeline:import-history:IMPORT_HT:unknown:false").assertIsDisplayed().performClick()
        ui.onNodeWithText("Synthetic frozen import").assertIsDisplayed()
        val view=ui.activity.window.decorView
        val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        java.io.File("build/screenshots").mkdirs()
        java.io.File("build/screenshots/imported_timeline_detail.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        ui.onNodeWithText(ui.activity.getString(R.string.timeline_imported_open_history)).performClick()
        ui.runOnIdle{assertEquals(listOf(71L),selected)}
    }
    @Test fun importingOrDeletingRecordsRefreshesTheProjectionWithoutChangingRegimens() {
        val extra=mutableStateOf(NotesViewModel.ExtraState())
        ui.setContent{MaterialTheme{LongitudinalScreen(NotesState(loading=false),extra.value,{},{},{},PaddingValues())}}
        ui.onNodeWithTag("timeline:import-history:IMPORT_HT:unknown:false").assertDoesNotExist()
        ui.runOnIdle{extra.value=extra.value.copy(records=listOf(row()))}
        ui.onNodeWithTag("timeline:import-history:IMPORT_HT:unknown:false").assertIsDisplayed()
        ui.runOnIdle{extra.value=extra.value.copy(records=listOf(row().copy(deleted_at_utc=Instant.now().toEpochMilli())))}
        ui.onNodeWithTag("timeline:import-history:IMPORT_HT:unknown:false").assertDoesNotExist()
    }
    @Test fun selectedOriginalHistoryBypassesTheDefaultThirtyDayWindowAndExcludesOtherSources() {
        val selected=row();val other=med.copy(id=2,name="Synthetic excluded source")
        val excluded=selected.copy(id=72,medication_id=2,config_snapshot=MedicationSnapshot.encode(other,null))
        ui.setContent{MaterialTheme{HistoryScreen(NotesState(medications=listOf(med,other),loading=false),listOf(selected,excluded),{},{},PaddingValues(),selectedRecordIds=setOf(71))}}
        ui.onAllNodesWithText("Synthetic frozen import").assertCountEquals(2)
        // Medication filter chips also contain names; only the selected drug has an execution row.
        ui.onAllNodesWithText("Synthetic excluded source").assertCountEquals(1)
    }
}
