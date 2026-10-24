package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RuleKind
import net.plainnotes.app.reminder.ReminderCoordinator
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.time.*
import java.util.UUID

@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],application=android.app.Application::class)
class RecordedReferenceUiTest(private val locale:String) {
    companion object {@JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/320dp/2x") fun cases()=listOf("en","fr-rFR","zh-rCN","zh-rTW").map{arrayOf<Any>(it)}}
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private lateinit var db:NotesDatabase;private lateinit var repo:NotesRepository
    private var model:NotesViewModel?=null
    private val at=Instant.parse("2026-03-28T12:00:00Z")
    private val original=UserReferenceProduct("SYNTHETIC original reference",listOf("SYNTHETIC ingredient A"),"2 mg / tablet","tablet",listOf("oral"),sourceVersion="historical-v1",recordedOn="2026-10-16")
    @Before fun setup(){RuntimeEnvironment.setQualifiers("$locale-w320dp-h640dp-xxhdpi");RuntimeEnvironment.setFontScale(2f)
        db=Room.inMemoryDatabaseBuilder(ui.activity,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build();repo=NotesRepository(object:DatabaseAccess(ui.activity){override fun get(space:Space)=net.plainnotes.app.syntheticLibrary(db)})}
    @After fun cleanup(){model?.let{ViewModelStore().apply{put("source-model",it);clear()}};db.close()}
    private suspend fun medication(active:Boolean=true)=repo.saveMedication(MedicationEntity(name="SYNTHETIC logged medication",molecule="OTHER",unit="TABLET",dose_per_intake=1.0,container_capacity=28.0,soon_alert_minutes=0,late_after_minutes=60,site_rotation=false,notifications_on=false,active=active,sort_order=0),null,RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.NOON),emptySet(),at)
    private suspend fun link(id:Long)=repo.associateInitialMedication(id,UUID.randomUUID().toString(),original,repo.referencePreviewEpoch())
    private fun awaitRead(){ui.waitUntil(10000){ui.onAllNodesWithTag("record-reference-loading").fetchSemanticsNodes().isEmpty()}}
    @Test fun originalContextRemainsVisibleAfterCurrentReferenceChanges() {
        val pair=runBlocking {
            val id=medication();val first=link(id);repo.unscheduled(id,at,1.0)
            val record=repo.records().single();repo.associateMedication(first.scope!!,id,1,UUID.randomUUID().toString(),original.copy(name="SYNTHETIC newer reference",sourceVersion="current-v2"))
            record to repo.referencePreviewEpoch()
        }
        val before=runBlocking{repo.records()}
        ui.setContent{NotesTheme(ThemeMode.DARK){RecordedReferenceDialog(pair.first,pair.second,repo::recordedReferenceHistory){}}}
        awaitRead();ui.onNodeWithTag("record-reference-list").performScrollToNode(hasText(original.name))
        ui.onNodeWithText(original.name).assertExists();ui.onAllNodesWithText("SYNTHETIC newer reference").assertCountEquals(0)
        assertEquals(before,runBlocking{repo.records()})
    }
    @Test fun oldUncapturedRecordShowsUnknownAndStaleRestoreDoesNotJoinReusedId() {
        val pair=runBlocking {
            val id=medication();repo.unscheduled(id,at,1.0);val record=repo.records().single();link(id)
            record to repo.referencePreviewEpoch()
        }
        val archive=runBlocking{repo.exportBackup("SYNTHETIC-history-password".toCharArray())}
        var visible by mutableStateOf(true)
        ui.setContent{NotesTheme(ThemeMode.LIGHT){if(visible)RecordedReferenceDialog(pair.first,pair.second,repo::recordedReferenceHistory){}}}
        awaitRead();ui.onNodeWithTag("record-reference-list").performScrollToNode(hasTestTag("record-reference-empty"))
        ui.onNodeWithTag("record-reference-empty").assertIsDisplayed();ui.onAllNodesWithText(original.name).assertCountEquals(0)
        ui.runOnIdle{visible=false};ui.waitForIdle()
        runBlocking{repo.restoreBackup(archive,"SYNTHETIC-history-password".toCharArray())}
        ui.runOnIdle{visible=true}
        awaitRead();ui.onNodeWithTag("record-reference-list").performScrollToNode(hasTestTag("record-reference-error"))
        ui.onNodeWithTag("record-reference-error").assertIsDisplayed();ui.onAllNodesWithText(original.name).assertCountEquals(0)
        val fresh=runBlocking{repo.recordedReferenceHistory(repo.referencePreviewEpoch(),pair.first.id)}
        assertTrue(fresh.contexts.isEmpty());assertEquals(pair.first,runBlocking{repo.records().single()})
    }
    @Test fun actualHistoryEditEntryOpensReadOnlySourceWithoutSavingTheRecord() {
        // A history-only fixture has no scheduled rule: ViewModel refresh must not reconcile unrelated missed slots.
        val pair=runBlocking{val id=medication(active=false);repo.unscheduled(id,at,1.0);repo.records().single() to repo.referencePreviewEpoch()}
        val vm=NotesViewModel(repo,ReminderCoordinator(ui.activity,repo),ui.activity).also {net.plainnotes.app.awaitRuntimeLibrary(it)}.also{model=it}
        val dialogs=HistoryDialogs().apply{editRecord=pair.first;referenceEpoch=pair.second}
        val meds=runBlocking{repo.medications().associateBy{it.id}}
        ui.setContent{NotesTheme(ThemeMode.LIGHT){HistoryRecordDialogs(dialogs,vm,meds)}}
        ui.waitUntil(10000){!vm.state.value.loading}
        ui.onNodeWithTag("record-reference-entry").performScrollTo().performClick();awaitRead()
        ui.onNodeWithTag("record-reference-list").performScrollToNode(hasTestTag("record-reference-empty"))
        ui.onNodeWithTag("record-reference-empty").assertIsDisplayed()
        ui.onNodeWithTag("record-reference-close").performClick()
        assertEquals(pair.first,dialogs.editRecord);assertNull(dialogs.referenceRecord);assertEquals(pair.first,runBlocking{repo.records().single()})
    }
}
