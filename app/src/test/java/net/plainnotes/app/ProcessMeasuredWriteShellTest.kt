package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.measurement.*
import net.plainnotes.app.domain.write.*
import net.plainnotes.app.reminder.ReminderCoordinator
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

/** Real owner + Repository + authenticated NotesApp. Dispatcher control is TEST_ONLY;
 * no alternate writer, mocked receipt, injected display status or clinical recommendation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=android.app.Application::class,qualifiers="en-rUS-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProcessMeasuredWriteShellTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private lateinit var db:NotesDatabase
    private lateinit var repo:NotesRepository
    private lateinit var model:NotesViewModel
    private lateinit var owner:MeasuredSaveCoordinator
    private val process=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val dispatcher=PausedDispatcher()
    private lateinit var expected:PreparedActualSave
    private lateinit var scope:ReferenceScope
    private var medication=0L
    private val at=Instant.parse("2026-10-21T12:00:00Z")
    private val draft=MeasuredActualUseDraft(at.toEpochMilli(),"UTC",0.125,emptyList(),MeasuredStockEffect.UNKNOWN,
        Quantity(BigDecimal("0.5"),MeasureUnit.TABLET,Meaning(QuantityKind.USE_COUNT,"SYNTHETIC tablets")),true)
    private class PausedDispatcher:CoroutineDispatcher() {
        private val queue=ConcurrentLinkedQueue<Runnable>()
        override fun dispatch(context:CoroutineContext,block:Runnable){queue.add(block)}
        fun runAll(){while(true)(queue.poll() ?: return).run()}
    }
    @Before fun open() {
        db=Room.inMemoryDatabaseBuilder(ui.activity,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        repo=NotesRepository(object:DatabaseAccess(ui.activity){override fun get(space:Space)=db})
        runBlocking {
            medication=db.dao().insertMedication(MedicationEntity(name="SYNTHETIC process save",molecule="OTHER",unit="MG",dose_per_intake=0.1,
                container_capacity=28.0,site_rotation=false,notifications_on=false,active=false,sort_order=0))
            val associated=repo.associateInitialMedication(medication,UUID.randomUUID().toString(),
                UserReferenceProduct("SYNTHETIC reference",sourceVersion="synthetic-v1",recordedOn="2026-10-21"),repo.referencePreviewEpoch())
            scope=requireNotNull(associated.scope)
        }
        // Only the scheduling seam is controlled; all persistence and observation use production code.
        val constructor=MeasuredSaveCoordinator::class.java.declaredConstructors.single{it.parameterCount==5}.apply{isAccessible=true}
        owner=constructor.newInstance(repo,process,dispatcher,null,null) as MeasuredSaveCoordinator
        expected=runBlocking{owner.prepare(scope,medication,UUID.randomUUID().toString(),draft,at)}
        model=newModel();UiPrefs(ui.activity).disclaimerAccepted=true
        ui.waitUntil(30000){shadowOf(android.os.Looper.getMainLooper()).idle();!model.state.value.loading && !model.conc.value.loading}
    }
    private fun newModel()=NotesViewModel(repo,ReminderCoordinator(ui.activity,repo),ui.activity,owner)
    private fun clearModel(){ViewModelStore().apply{put("model",model);clear()}}
    @After fun close(){clearModel();process.cancel();dispatcher.runAll();runBlocking{process.coroutineContext[Job]!!.join()};db.close()}
    private fun await(check:()->Boolean) {
        ui.waitUntil(30000){shadowOf(android.os.Looper.getMainLooper()).idle();dispatcher.runAll();check()}
    }
    private fun navigate(target:Destination) {
        ui.onNodeWithContentDescription(ui.activity.getString(R.string.menu)).performClick()
        val text=ui.activity.getString(target.title)
        ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(text))
        ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(text)).performClick()
    }
    @Test fun actualQueuedSaveSurvivesModelReplacementAndRealNavigationThenCommitsOnce() {
        val task=owner.start(expected)
        val modelState=mutableStateOf(model)
        ui.setContent{MaterialTheme{NotesApp(modelState.value,UiPrefs(ui.activity).appearance,{})}}
        ui.onNodeWithTag("write-status-title").assertIsDisplayed()
        ui.onNodeWithTag("write-status-retry").assertDoesNotExist()
        navigate(Destination.MEDICATIONS)
        ui.onNodeWithTag("write-status-title").assertIsDisplayed()
        ui.runOnIdle{clearModel();model=newModel();modelState.value=model;model.refresh()}
        ui.waitUntil(30000){shadowOf(android.os.Looper.getMainLooper()).idle();!model.state.value.loading && model.state.value.medications.size==1}
        assertNull(model.state.value.error);assertSame(task,owner.latestTask.value)
        assertEquals(WriteStatus.PENDING,task.progress.value.status)
        assertSame(task,owner.start(expected))
        await{task.progress.value.status==WriteStatus.SUCCESS && model.extra.value.records.size==1}
        ui.onNodeWithText(ui.activity.getString(R.string.write_status_success)).assertIsDisplayed()
        runBlocking{assertEquals(1,db.dao().records().size)}
        assertEquals(expected.operationUuid,task.confirmed.value!!.operationUuid)
    }
    @Test fun mismatchedHistoricalRequestShowsManualReviewAndCheckNeverReplaysOrReportsFailed() {
        val differentOwner=MeasuredSaveCoordinator(repo)
        val different=runBlocking{differentOwner.prepare(scope,medication,expected.operationUuid,draft,at.plusSeconds(1))}
        val committed=differentOwner.start(different)
        runBlocking{withTimeout(30000){committed.progress.first{it.status==WriteStatus.SUCCESS}}}
        val task=owner.start(expected)
        ui.setContent{MaterialTheme{NotesApp(model,UiPrefs(ui.activity).appearance,{})}}
        await{task.progress.value.phase==WritePhase.MANUAL_CHECK}
        ui.onNodeWithTag("write-status-check").assertIsDisplayed().performClick()
        await{task.progress.value.phase==WritePhase.MANUAL_CHECK}
        navigate(Destination.MEDICATIONS)
        ui.onNodeWithTag("write-status-title").assertIsDisplayed()
        ui.onNodeWithTag("write-status-retry").assertDoesNotExist()
        assertEquals(WriteStatus.PENDING,task.progress.value.status);assertFalse(task.progress.value.retryAllowed)
        assertNull(task.confirmed.value);runBlocking{assertEquals(1,db.dao().records().size)}
    }
}
