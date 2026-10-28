package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RuleKind
import net.plainnotes.app.reminder.ReminderCoordinator
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.util.UUID

/** Real NotesApp -> center -> existing reference controller; synthetic data, cancel never creates a second medication. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class DrugCenterNavigationTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test fun browseAndExistingAssociationReturnWithoutChangingDoseStockOrHistory() {
        val context=ui.activity
        val db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        val repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=net.plainnotes.app.syntheticLibrary(db)})
        val model=NotesViewModel(repo,ReminderCoordinator(context,repo),context).also {net.plainnotes.app.awaitRuntimeLibrary(it)}
        UiPrefs(context).disclaimerAccepted=true
        try {
            val id=runBlocking {
                val id=repo.saveMedication(MedicationEntity(name="SYNTHETIC existing medication",molecule="E2",route="ORAL",unit="MG",dose_per_intake=2.0,container_capacity=28.0,soon_alert_minutes=0,late_after_minutes=60,site_rotation=false,notifications_on=false,active=true,sort_order=0),"E2",RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.NOON),emptySet(),Instant.now().minusSeconds(86400))
                val product=UserReferenceProduct("SYNTHETIC saved source",listOf("SYNTHETIC ingredient"),"2 mg",sourceVersion="synthetic-v1",recordedOn="2026-10-18")
                repo.associateInitialMedication(id,UUID.randomUUID().toString(),product,repo.medicationReferenceState(id).previewEpoch)
                repo.addContainers(id,28.0,1,true)
                repo.unscheduled(id,Instant.now().minusSeconds(3600),0.5)
                id
            }
            val before=runBlocking{db.dao().medications()}
            val beforeContainers=runBlocking{repo.containers()}
            val beforeRecords=runBlocking{repo.records()}
            val beforeProfile=runBlocking{repo.profile(id)}
            val beforeLedger=runBlocking{db.dao().supplyForContainer(beforeContainers.single().id)}
            assertTrue(beforeRecords.isNotEmpty());assertTrue(beforeLedger.isNotEmpty());assertNotNull(beforeProfile)
            model.sync()
            ui.setContent{NotesTheme(ThemeMode.LIGHT){NotesApp(model,UiPrefs(context).appearance,{})}}
            ui.onNodeWithContentDescription(context.getString(R.string.menu)).performClick()
            ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(context.getString(R.string.medications)))
            ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(context.getString(R.string.medications))).performClick()
            ui.onNodeWithTag("drug-center-open").performClick()
            ui.onNodeWithTag("drug-center-list").performScrollToNode(hasTestTag("drug-center-catalogue"))
            ui.onNodeWithTag("drug-center-catalogue").performClick()
            ui.onNodeWithTag("reference-screen").performScrollToNode(hasTestTag("reference-saved-tab"))
            ui.onNodeWithTag("reference-saved-tab").performClick()
            ui.onNodeWithTag("reference-screen").performScrollToNode(hasTestTag("reference-search"))
            ui.onNodeWithTag("reference-search").performTextInput("SYNTHETIC")
            ui.onNodeWithTag("reference-screen").performScrollToNode(hasTestTag("reference-search-go"))
            ui.onNodeWithTag("reference-search-go").performClick()
            ui.waitUntil(10_000){ui.onAllNodesWithTag("reference-loading").fetchSemanticsNodes().isEmpty()}
            ui.onNodeWithTag("reference-screen").performScrollToNode(hasText("SYNTHETIC saved source"))
            ui.onNodeWithText("SYNTHETIC saved source").assertExists()
            ui.onNodeWithTag("reference-close").performClick()
            ui.onNodeWithTag("drug-center-window").assertExists()
            ui.onNodeWithTag("drug-center-list").performScrollToNode(hasTestTag("drug-center-medication:$id"))
            ui.onNodeWithTag("drug-center-medication:$id").performClick()
            ui.onNodeWithTag("reference-screen").performScrollToNode(hasTestTag("reference-details"))
            ui.onAllNodesWithText("SYNTHETIC saved source").onFirst().assertExists()
            ui.onNodeWithTag("reference-close").performClick()
            ui.onNodeWithTag("drug-center-list").performScrollToNode(hasTestTag("drug-center-catalogue"))
            ui.onNodeWithTag("drug-center-catalogue").performClick()
            ui.onNodeWithTag("reference-screen").performScrollToNode(hasTestTag("bdpm-panel"))
            ui.onNodeWithTag("bdpm-panel").assertExists()
            ui.onNodeWithTag("reference-close").performClick()
            for(tag in listOf("drug-center-rcp","drug-center-notice","drug-center-safety","drug-center-supply")) {
                ui.onNodeWithTag("drug-center-list").performScrollToNode(hasTestTag(tag))
                ui.onNodeWithTag(tag).assertHasClickAction().performClick()
                ui.onNodeWithTag("official-browser").assertExists()
                ui.onNodeWithTag("official-product-search").assertExists()
                ui.onNodeWithTag("reference-window").assertDoesNotExist()
                ui.onNodeWithTag("drug-center-close").performClick()
                ui.onNodeWithTag("drug-center-window").assertExists()
            }
            // No verified full recall dataset: the non-functional module is absent.
            ui.onAllNodesWithTag("drug-center-recalls").assertCountEquals(0)
            ui.onNodeWithTag("drug-center-close").performClick()
            ui.onNodeWithTag("drug-center-window").assertDoesNotExist()
            ui.onNodeWithTag("drug-center-open").assertExists()
            // UI-11: the centre no longer has Medications / Stock shortcut buttons; both stay reachable from the drawer.
            ui.onNodeWithTag("drug-center-open").performClick()
            for(tag in listOf("drug-center-plans","drug-center-stock")) {
                ui.onNodeWithTag("drug-center-list").performScrollToNode(hasTestTag("drug-center-rcp"))
                ui.onAllNodesWithTag(tag).assertCountEquals(0)
            }
            ui.onNodeWithTag("drug-center-close").performClick()
            ui.onNodeWithTag("drug-center-window").assertDoesNotExist()
            ui.onNodeWithTag("top-title").assertTextEquals(context.getString(R.string.medications))
            ui.onNodeWithContentDescription(context.getString(R.string.menu)).performClick()
            ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(context.getString(R.string.stock)))
            ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(context.getString(R.string.stock))).performClick()
            ui.onNodeWithTag("top-title").assertTextEquals(context.getString(R.string.stock))
            assertEquals(before,runBlocking{db.dao().medications()})
            assertEquals(beforeContainers,runBlocking{repo.containers()})
            assertEquals(beforeRecords,runBlocking{repo.records()})
            assertEquals(beforeProfile,runBlocking{repo.profile(id)})
            assertEquals(beforeLedger,runBlocking{db.dao().supplyForContainer(beforeContainers.single().id)})
            assertEquals(1,runBlocking{repo.medicationReferenceState(id).reference?.revision})
        }finally{androidx.lifecycle.ViewModelStore().apply{put("model",model);clear()};db.close()}
    }
}
