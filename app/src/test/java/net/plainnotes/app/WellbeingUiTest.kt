package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class WellbeingUiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test fun untouchedSliderIsUnrecordedAndCanBeRecordedThenCleared() {
        var value by mutableStateOf<Int?>(null);val writes=mutableListOf<Int?>()
        ui.setContent{MaterialTheme{FiveLevelInput(value,"Low","High","Synthetic daily item"){value=it;writes+=it}}}
        ui.onNodeWithText(ui.activity.getString(R.string.wb_unrecorded)).assertIsDisplayed();assertTrue(writes.isEmpty())
        ui.onNodeWithContentDescription("Synthetic daily item").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){it(4f)}
        ui.runOnIdle{assertEquals(4,value)}
        ui.onNodeWithText(ui.activity.getString(R.string.wb_clear)).performClick()
        ui.runOnIdle{assertNull(value);assertEquals(listOf(4,null),writes)}
    }
    @Test fun newDailyKeysHaveLabelsAndHistoricalItemsHaveNoAverage() {
        val day=LocalDate.now().toString();val item=CheckinItemEntity(1,"DAY_MOOD",null,true,0)
        ui.setContent{MaterialTheme{WellbeingScreen(listOf(item),listOf(CheckinScoreEntity(day,1,4)),emptyList(),{_,_,_->},{_,_->},{},PaddingValues())}}
        ui.onAllNodesWithText(ui.activity.getString(R.string.wb_day_mood)).assertCountEquals(1)
        ui.onAllNodesWithText("Average",substring=true).assertCountEquals(0)
    }
    @Test fun spironolactoneHasNoInventedSymptomsOrUrgentWarning() {
        val med=MedicationEntity(name="Synthetic SPI",molecule="SPI",unit="MG",dose_per_intake=1.0,container_capacity=10.0,soon_alert_minutes=0,late_after_minutes=60,site_rotation=false,notifications_on=false,active=true,sort_order=0)
        var changed=false
        ui.setContent{MaterialTheme{SymptomsScreen(LocalDate.now(),listOf(med),emptyMap(),emptyList(),null,{_,_,_,_->changed=true})}}
        ui.onNodeWithText(ui.activity.getString(R.string.wb_symptom_none),substring=true).assertIsDisplayed()
        ui.onAllNodes(isToggleable()).assertCountEquals(0)
        ui.runOnIdle{assertFalse(changed)}
    }
    @Test fun optionalReviewStaysEmptyAndHidingAnEffectKeepsItsExistingRecord() {
        var saved:StageReviewEntity?=null
        val existing=StageReviewEntity(id=1,date=LocalDate.now().toString(),effects_json="{\"FAT\":\"NOTICED\"}")
        ui.setContent{MaterialTheme{StageReviewDialog(existing,listOf(existing),emptyList(),listOf(ReviewEffectEntity("FAT",false)),{}, {saved=it})}}
        ui.onAllNodesWithText(ui.activity.getString(R.string.wb_effect_fat)).assertCountEquals(0)
        ui.onNodeWithText(ui.activity.getString(R.string.save)).performClick()
        ui.runOnIdle{
            assertNotNull(saved);assertNull(saved!!.systolic);assertNull(saved!!.diastolic);assertNull(saved!!.weight_kg);assertNull(saved!!.smoking);assertNull(saved!!.satisfaction)
            assertEquals("NOTICED",org.json.JSONObject(saved!!.effects_json).getString("FAT"))
        }
    }

}
