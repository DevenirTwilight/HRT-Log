package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import kotlinx.coroutines.*
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RuleKind
import net.plainnotes.app.reference.MedicationReferenceController
import net.plainnotes.app.reminder.ReminderCoordinator
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import java.time.*

/** Real bundled official records; only the user's medication is synthetic. No network client. */
abstract class BdpmReferenceFlowContract {
    protected open fun configureHost() {}
    protected open fun idlePlatform() {}
    protected open fun helperFactory():androidx.sqlite.db.SupportSQLiteOpenHelper.Factory?=null
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private lateinit var db:NotesDatabase
    private lateinit var repo:NotesRepository
    @Before fun setup(){configureHost()
        val builder=Room.inMemoryDatabaseBuilder(ui.activity,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards)
        helperFactory()?.let{builder.openHelperFactory(it)}
        db=builder.build()
        repo=NotesRepository(object:DatabaseAccess(ui.activity){override fun get(space:Space)=db})}
    @After fun cleanup(){db.close()}
    private fun click(tag:String) {ui.onNodeWithTag("reference-screen").performScrollToNode(hasTestTag(tag));ui.onNodeWithTag(tag).performClick()}
    private fun search(text:String) {
        ui.onNodeWithTag("reference-screen").performScrollToNode(hasTestTag("bdpm-search"))
        ui.onNodeWithTag("bdpm-search").performTextReplacement(text);click("bdpm-search-go")
        try {
            ui.waitUntil(30000){idlePlatform();ui.onAllNodesWithTag("bdpm-result:65421374").fetchSemanticsNodes().isNotEmpty() ||
                ui.onAllNodes(hasTestTag("bdpm-status") and hasText(ui.activity.getString(R.string.bdpm_unavailable))).fetchSemanticsNodes().isNotEmpty()}
        }catch(error:Throwable) {
            throw AssertionError("Catalogue status: "+ui.onAllNodesWithTag("bdpm-status").printToString()+" query: "+ui.onAllNodesWithTag("bdpm-search").printToString(),error)
        }
        ui.onNodeWithTag("bdpm-result:65421374").assertExists()
    }
    @Test fun realAppEntrySelectsOfficialPackageConfirmsAndReopensWithoutDoseStockOrProfileChanges() {
        val model=NotesViewModel(repo,ReminderCoordinator(ui.activity,repo),ui.activity)
        UiPrefs(ui.activity).disclaimerAccepted=true
        try {
            val id=runBlocking{
                val id=repo.saveMedication(MedicationEntity(name="SYNTHETIC saved medication",molecule="E2",route="ORAL",unit="MG",dose_per_intake=0.5,container_capacity=28.0,soon_alert_minutes=0,late_after_minutes=60,site_rotation=false,notifications_on=false,active=true,sort_order=0),"E2",RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.NOON),emptySet(),Instant.now().minusSeconds(86400))
                repo.addContainers(id,28.0,1,true);repo.unscheduled(id,Instant.now().minusSeconds(3600),0.5);id
            }
            val med=runBlocking{repo.medications()};val stock=runBlocking{repo.containers()};val records=runBlocking{repo.records()};val profile=runBlocking{repo.profile(id)}
            model.sync()
            ui.setContent{NotesTheme(ThemeMode.LIGHT){NotesApp(model,UiPrefs(ui.activity).appearance,{})}}
            ui.onNodeWithContentDescription(ui.activity.getString(R.string.menu)).performClick()
            ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(ui.activity.getString(R.string.medications)))
            ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(ui.activity.getString(R.string.medications))).performClick()
            ui.onNodeWithTag("drug-center-open").performClick()
            ui.onNodeWithTag("drug-center-list").performScrollToNode(hasTestTag("drug-center-medication:$id"));ui.onNodeWithTag("drug-center-medication:$id").performClick()
            search("PROVAMES");click("bdpm-result:65421374");click("bdpm-package:3400935479341")
            ui.waitUntil(30000){idlePlatform();ui.onAllNodesWithTag("reference-select").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("reference-select").performClick()
            ui.onNodeWithTag("reference-confirm-cancel").performClick()
            assertNull(runBlocking{repo.medicationReferenceState(id)}.reference)
            ui.onNodeWithTag("reference-select").performClick();ui.onNodeWithTag("reference-confirm").performClick()
            ui.waitUntil(15000){
                idlePlatform()
                ui.onAllNodesWithTag("reference-saving").fetchSemanticsNodes()
                model.medicationReferences.state.value.let{it.saved || it.failed}
            }
            assertTrue("Reference failed=${model.medicationReferences.state.value.failed}, stale=${model.medicationReferences.state.value.stale}, busy=${model.medicationReferences.state.value.busy}",model.medicationReferences.state.value.saved)
            val linked=runBlocking{repo.medicationReferenceState(id)}.reference!!.product!!
            assertEquals("65421374",linked.cis);assertEquals("3400935479341",linked.cip);assertNotNull(linked.bdpm)
            ui.onNodeWithTag("reference-close").performClick()
            ui.onNodeWithTag("drug-center-list").performScrollToNode(hasTestTag("drug-center-medication:$id"));ui.onNodeWithTag("drug-center-medication:$id").performClick()
            ui.onNodeWithTag("reference-screen").performScrollToNode(hasTestTag("reference-details"))
            ui.onAllNodesWithText("PROVAMES 2 mg, comprimé pelliculé").onFirst().assertExists()
            assertEquals(med,runBlocking{repo.medications()});assertEquals(stock,runBlocking{repo.containers()});assertEquals(records,runBlocking{repo.records()});assertEquals(profile,runBlocking{repo.profile(id)})
            assertTrue(runBlocking{repo.savedReferenceProducts()}.entries.isEmpty())
            click("reference-unlink");ui.onNodeWithTag("reference-confirm").performClick()
            ui.waitUntil(15000){idlePlatform();ui.onAllNodesWithTag("reference-saving").fetchSemanticsNodes();model.medicationReferences.state.value.let{it.saved || it.failed}}
            assertNull(runBlocking{repo.medicationReferenceState(id)}.reference!!.product)
            assertEquals(med,runBlocking{repo.medications()});assertEquals(stock,runBlocking{repo.containers()});assertEquals(records,runBlocking{repo.records()});assertEquals(profile,runBlocking{repo.profile(id)})
        }finally{androidx.lifecycle.ViewModelStore().apply{put("model",model);clear()}}
    }
    @Test fun unsavedDraftCanBrowseGenuineProductButCannotLinkAndReturnsUnchanged() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined);val controller=MedicationReferenceController(repo,scope)
        var open by mutableStateOf(false)
        try {
            ui.setContent{NotesTheme(ThemeMode.DARK){
                MedicationEditor(EditMedication(null,null,null,emptyList()),{},inDialog=false,onReference={controller.open(null);open=true}){}
                if(open)MedicationReferenceDialog(controller){open=false}
            }}
            ui.onNodeWithTag("medication-name").performTextInput("SYNTHETIC unsaved draft")
            ui.onNodeWithTag("medication-reference-entry").performScrollTo().performClick()
            search("PROVAMES");click("bdpm-result:65421374");click("bdpm-package:3400935479341")
            ui.waitUntil(30000){idlePlatform();ui.onAllNodesWithTag("reference-select").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("reference-select").assertIsNotEnabled()
            ui.onNodeWithText(ui.activity.getString(R.string.cancel)).performClick()
            ui.onNodeWithTag("reference-close").performClick()
            ui.onNodeWithTag("medication-name").assertTextContains("SYNTHETIC unsaved draft")
            assertTrue(runBlocking{repo.medications()}.isEmpty())
        }finally{scope.cancel()}
    }
    @Test fun realFilesystemFailureRetryAndNoMatchAreDistinctAndPreserveUserData() {
        val context=ui.activity
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
        val sentinel=java.io.File(context.cacheDir,"synthetic-bdpm-parent-${System.nanoTime()}").apply{writeText("SYNTHETIC sentinel")}
        var ready=false
        val guarded=object:android.content.ContextWrapper(context) {
            override fun getApplicationContext():android.content.Context=this
            override fun getNoBackupFilesDir():java.io.File=if(ready)context.noBackupFilesDir else sentinel
        }
        val catalogue=net.plainnotes.app.ui.BdpmCatalogueUiState(BdpmCatalogue(guarded),scope)
        val before=runBlocking {
            db.dao().insertMedication(MedicationEntity(name="SYNTHETIC preserved during catalogue failure",molecule="OTHER",unit="MG",dose_per_intake=0.5,container_capacity=28.0,site_rotation=false,notifications_on=false,active=false,sort_order=0))
            repo.medications()
        }
        try {
            ui.setContent{NotesTheme(ThemeMode.LIGHT){
                androidx.compose.foundation.lazy.LazyColumn(androidx.compose.ui.Modifier.fillMaxSize().testTag("reference-screen")) {
                    item("catalogue"){net.plainnotes.app.ui.BdpmCataloguePanel(catalogue){error("No association requested")}}
                }
            }}
            ui.onNodeWithTag("reference-screen").performScrollToNode(hasTestTag("bdpm-status"))
            ui.onNodeWithTag("bdpm-status").assertTextEquals(context.getString(R.string.bdpm_initial))
            ui.onNodeWithTag("reference-screen").performScrollToNode(hasTestTag("bdpm-search"))
            ui.onNodeWithTag("bdpm-search").performTextInput("PROVAMES");click("bdpm-search-go")
            ui.waitUntil(15000){idlePlatform();catalogue.phase=="UNAVAILABLE"}
            ui.onNodeWithTag("reference-screen").performScrollToNode(hasTestTag("bdpm-status"))
            ui.onNodeWithTag("bdpm-status").assertTextEquals(context.getString(R.string.bdpm_unavailable))
            assertEquals("SYNTHETIC sentinel",sentinel.readText())
            ready=true
            ui.onNodeWithTag("reference-screen").performScrollToNode(hasText(context.getString(R.string.ref2_reload)))
            ui.onNodeWithText(context.getString(R.string.ref2_reload)).performClick()
            ui.waitUntil(30000){idlePlatform();catalogue.phase=="RESULTS"}
            ui.onNodeWithTag("bdpm-result:65421374").assertExists()
            ui.onNodeWithTag("reference-screen").performScrollToNode(hasTestTag("bdpm-search"))
            ui.onNodeWithTag("bdpm-search").performTextReplacement("NO_MATCH_SYNTHETIC_473029");click("bdpm-search-go")
            ui.waitUntil(15000){idlePlatform();catalogue.phase=="EMPTY"}
            ui.onNodeWithTag("reference-screen").performScrollToNode(hasTestTag("bdpm-status"))
            ui.onNodeWithTag("bdpm-status").assertTextEquals(context.getString(R.string.bdpm_empty))
            ui.onNodeWithTag("bdpm-result:65421374").assertDoesNotExist()
            assertEquals(before,runBlocking{repo.medications()});assertTrue(runBlocking{repo.records()}.isEmpty())
        }finally{scope.cancel();sentinel.delete()}
    }

}
