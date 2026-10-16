package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import kotlinx.coroutines.*
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RuleKind
import net.plainnotes.app.reference.MedicationReferenceController
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.time.*

/** Real reference dialog and Room, all facts synthetic. Four languages, small screen and large font. */
@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MedicationReferenceUiTest(private val locale:String) {
    companion object {@JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/320dp/2x") fun locales()=listOf("en","fr-rFR","zh-rCN","zh-rTW").map{arrayOf<Any>(it)}}
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private lateinit var db:NotesDatabase;private lateinit var repo:NotesRepository;private lateinit var controller:MedicationReferenceController
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
    @Before fun setup(){RuntimeEnvironment.setQualifiers("$locale-w320dp-h640dp-xxhdpi");RuntimeEnvironment.setFontScale(2f)
        db=Room.inMemoryDatabaseBuilder(ui.activity,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        repo=NotesRepository(object:DatabaseAccess(ui.activity){override fun get(space:Space)=db});controller=MedicationReferenceController(repo,scope)}
    @After fun cleanup(){scope.cancel();db.close()}
    private suspend fun medication()=repo.saveMedication(MedicationEntity(name="SYNTHETIC actual medication",molecule="OTHER",unit="TABLET",dose_per_intake=0.5,container_capacity=28.0,soon_alert_minutes=0,late_after_minutes=60,site_rotation=false,notifications_on=false,active=true,sort_order=0),null,RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.NOON),emptySet(),Instant.parse("2026-03-28T12:00:00Z"))
    private fun count(table:String)=db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM `$table`").use{it.moveToFirst();it.getInt(0)}
    private fun manual(){ui.onNodeWithTag("reference-screen").performScrollToNode(hasTestTag("reference-manual"));ui.onNodeWithTag("reference-manual").performClick();ui.onNodeWithTag("reference-name").performTextInput("SYNTHETIC user product");ui.onNodeWithTag("reference-preview").performClick();ui.onNodeWithTag("reference-select").performClick()}
    @Test fun confirmingActuallyPersistsAndCancellingDoesNotCreateExtraMedication() {
        val id=runBlocking{medication();db.dao().medications().single().id};runBlocking{controller.open(id).join()}
        ui.setContent{NotesTheme(ThemeMode.LIGHT){MedicationReferenceDialog(controller){}}}
        manual();ui.onNodeWithTag("reference-confirm").performClick();ui.waitUntil(10000){controller.state.value.saved}
        assertEquals("SYNTHETIC user product",runBlocking{repo.medicationReferenceState(id)}.reference!!.product!!.name)
        assertEquals(1,count("medication"));assertEquals(1,count("reference_event"));assertEquals(0.5,runBlocking{repo.medications().single()}.dose_per_intake,0.0)
        ui.onNodeWithTag("reference-close").performClick();assertEquals(1,count("reference_event"))
    }
    @Test fun cancellingFinalConfirmationWritesNoDatasetOrAssociation() {
        val id=runBlocking{medication()};runBlocking{controller.open(id).join()}
        ui.setContent{NotesTheme(ThemeMode.DARK){MedicationReferenceDialog(controller){}}}
        manual()
        ui.onNodeWithTag("reference-confirm-cancel").performClick();ui.waitForIdle()
        assertEquals(0,count("reference_dataset"));assertEquals(0,count("reference_event"));assertEquals(1,count("medication"))
    }
    @Test fun invalidIdentifierIsNotTruncatedToAnotherProductAndDraftRemainsEditable() {
        val id=runBlocking{medication()};runBlocking{controller.open(id).join()}
        ui.setContent{NotesTheme(ThemeMode.LIGHT){MedicationReferenceDialog(controller){}}}
        ui.onNodeWithTag("reference-screen").performScrollToNode(hasTestTag("reference-manual"));ui.onNodeWithTag("reference-manual").performClick()
        ui.onNodeWithTag("reference-name").performTextInput("SYNTHETIC draft")
        ui.onNodeWithTag("reference-cis").performScrollTo().performTextInput("000000001")
        ui.onNodeWithTag("reference-preview").performClick()
        ui.onNodeWithTag("reference-invalid").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("reference-cis").performScrollTo().assertTextContains("000000001")
        assertEquals(0,count("reference_event"));assertEquals(0,count("reference_dataset"))
    }
    @Test fun browsingFromNewMedicationReturnsToUnchangedDraft() {
        var open by mutableStateOf(false)
        ui.setContent{NotesTheme(ThemeMode.LIGHT){
            MedicationEditor(EditMedication(null,null,null,emptyList()),{},inDialog=false,onReference={controller.open(null);open=true}){}
            if(open)MedicationReferenceDialog(controller){open=false}
        }}
        ui.onNodeWithTag("medication-name").performTextInput("SYNTHETIC unsaved draft")
        ui.onNodeWithTag("medication-reference-entry").performScrollTo().performClick()
        ui.waitUntil(10000){!controller.state.value.loading}
        ui.onNodeWithTag("reference-close").performClick()
        ui.onNodeWithTag("medication-name").assertTextContains("SYNTHETIC unsaved draft")
        assertEquals(0,count("medication"));assertEquals(0,count("reference_dataset"))
    }
}
