package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** REQUIREMENTS §39: restore, delete for good and empty, each permanent action confirmed with "cannot be undone". */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class RecycleBinUiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val med=MedicationEntity(1,"Synthetic secret name","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
    private val record=RecordEntity(9,1,taken_utc=1_780_000_000_000,taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="APP",revision=2,config_snapshot="{}",deleted_at_utc=1_780_000_100_000)
    private val items=listOf(TrashItemEntity(1,Trash.RECORD,"9","2026-05-28",2L,"{}",Trash.TRASHED),
        TrashItemEntity(2,Trash.MILESTONE,"5","2026-05-01",1L,Trash.payload("milestone" to org.json.JSONArray().put(org.json.JSONObject().put("id",5).put("date","2026-05-01").put("kind","CUSTOM").put("title","Synthetic milestone").put("note",org.json.JSONObject.NULL))).toString(),Trash.TRASHED))
    private val extra=NotesViewModel.ExtraState(trash=items,trashedRecords=listOf(record))
    private val state=NotesState(medications=listOf(med),loading=false)

    @Test fun restorePurgeAndEmptyAreConfirmed() {
        val restored=mutableListOf<Long>();val purged=mutableListOf<Long>();var emptied=false
        ui.setContent{MaterialTheme{RecycleBinDialog(state,extra,{restored+=it},{purged+=it.id},{emptied=true}){}}}
        ui.onNodeWithText("Synthetic milestone",substring=true).assertExists()
        ui.onNodeWithText("Synthetic secret name",substring=true).assertExists()
        ui.onNodeWithTag("trash-restore:2").performClick();ui.runOnIdle{assertEquals(listOf(2L),restored)}
        ui.onNodeWithTag("trash-purge:1").performClick()
        ui.onNodeWithTag("trash-purge-dialog").assertExists()
        ui.onNodeWithText(ui.activity.getString(R.string.trash_purge_confirm),substring=true).assertExists()
        ui.runOnIdle{assertTrue(purged.isEmpty())}
        ui.onNodeWithTag("trash-purge-confirm").performClick();ui.runOnIdle{assertEquals(listOf(1L),purged)}
        ui.onNodeWithTag("trash-empty").performClick();ui.onNodeWithTag("trash-empty-dialog").assertExists()
        ui.runOnIdle{assertFalse(emptied)}
        ui.onNodeWithTag("trash-empty-confirm").performClick();ui.runOnIdle{assertTrue(emptied)}
    }

    @Test fun simpleModeHidesMedicineNamesAndDoses() {
        ui.setContent{MaterialTheme{CompositionLocalProvider(LocalSimpleMode provides true){RecycleBinDialog(state,extra,{},{},{}){}}}}
        ui.onNodeWithTag("trash-item:1").assertExists()
        ui.onNodeWithText("Synthetic secret name",substring=true).assertDoesNotExist()
    }
}
