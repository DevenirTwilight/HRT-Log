package net.plainnotes.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.write.*
import net.plainnotes.app.reminder.ReminderCoordinator
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Actual NotesApp routes with a synthetic observation; no Repository save is submitted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=android.app.Application::class,qualifiers="en-rUS-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NonBlockingWriteShellTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private lateinit var db:NotesDatabase
    private lateinit var model:NotesViewModel
    @Before fun open() {
        db=Room.inMemoryDatabaseBuilder(ui.activity,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        val repo=NotesRepository(object:DatabaseAccess(ui.activity){override fun get(space:Space)=db})
        model=NotesViewModel(repo,ReminderCoordinator(ui.activity,repo),ui.activity)
        UiPrefs(ui.activity).disclaimerAccepted=true
    }
    @After fun close(){ViewModelStore().apply{put("model",model);clear()};db.close()}
    private fun navigate(target:Destination) {
        ui.onNodeWithContentDescription(ui.activity.getString(R.string.menu)).performClick()
        val text=ui.activity.getString(target.title)
        ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(text))
        ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(text)).performClick()
    }
    @Test fun pendingRemainsReachableWhileRealRoutesAndBackStillWorkWithoutWriting() {
        val progress=WriteProgressSummary("SYNTHETIC operation",WriteStatus.PENDING,WritePhase.WRITING)
        ui.setContent {MaterialTheme{NotesApp(model,UiPrefs(ui.activity).appearance,{},writeProgress=progress)}}
        ui.onNodeWithTag("write-status-title").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion,LiveRegionMode.Polite))
        ui.onNodeWithTag("write-status-hide").performClick()
        ui.onNodeWithTag("write-status-show").assertIsDisplayed().assertHasClickAction()
        navigate(Destination.MEDICATIONS)
        ui.onNodeWithTag("write-status-show").assertIsDisplayed().performClick()
        ui.onNodeWithTag("write-status-title").assertIsDisplayed()
        ui.onNodeWithTag("write-status-retry").assertDoesNotExist()
        ui.waitForIdle()
        val view=ui.activity.window.decorView
        val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        try {
            view.draw(android.graphics.Canvas(image))
            val file=File("build/p4-write-status-shots/actual-shell-pending-medications.png")
            file.parentFile!!.mkdirs();file.outputStream().use{assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))}
        }finally{image.recycle()}
        ui.runOnUiThread {ui.activity.onBackPressedDispatcher.onBackPressed()}
        ui.onNodeWithContentDescription(ui.activity.getString(R.string.calendar_pick_start)).assertExists()
        assertEquals(WriteStatus.PENDING,progress.status)
        runBlocking {
            assertTrue(db.dao().records().isEmpty());assertTrue(db.dao().containers().isEmpty())
            db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM supply_transaction").use{assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
        }
    }
    @Test fun completedObservationChangesTextWithoutReplayingAnyAction() {
        val state=mutableStateOf(WriteProgressSummary("SYNTHETIC",WriteStatus.PENDING,WritePhase.RECONCILING))
        var retryCalls=0
        ui.setContent {MaterialTheme{NotesApp(model,UiPrefs(ui.activity).appearance,{},writeProgress=state.value,onWriteRetry={retryCalls++})}}
        ui.onNodeWithText("Checking save result").assertExists()
        ui.runOnIdle{state.value=WriteProgressSummary("SYNTHETIC",WriteStatus.SUCCESS,WritePhase.TERMINAL)}
        ui.onNodeWithText("Saved").assertExists();ui.onNodeWithTag("write-status-retry").assertDoesNotExist()
        assertEquals(0,retryCalls)
        runBlocking{assertTrue(db.dao().records().isEmpty())}
    }
}
