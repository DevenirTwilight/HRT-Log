package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import net.plainnotes.app.reminder.ReminderCoordinator
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Actual AppShell destination with an empty synthetic Room database; no real data or navigation stubs. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class UnifiedConcentrationNavigationTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test fun onlyOneConcentrationDestinationHasNoModelSelectorAndRetainsLabs() {
        assertEquals(1,Destination.entries.count{it==Destination.CONCENTRATION})
        assertFalse(Destination.entries.any{it.name.contains("EXPERIMENTAL")})
        val context=ui.activity
        val db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        db.openHelper.writableDatabase
        val repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=net.plainnotes.app.syntheticLibrary(db)})
        val model=NotesViewModel(repo,ReminderCoordinator(context,repo),context).also {net.plainnotes.app.awaitRuntimeLibrary(it)}
        try {
            UiPrefs(context).disclaimerAccepted=true
            val prefs=UiPrefs(context)
            ui.setContent { MaterialTheme { NotesApp(model,prefs.appearance,{}) } }
            fun open(destination:Destination) {
                ui.onNodeWithContentDescription(context.getString(R.string.menu)).performClick()
                ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(context.getString(destination.title)))
                ui.onNodeWithText(context.getString(destination.title)).performClick()
            }
            open(Destination.CONCENTRATION)
            ui.waitUntil(30_000){ui.onAllNodesWithTag("m2-no-qualified-history").fetchSemanticsNodes().isNotEmpty()}
            for(choice in listOf("M1","M2","LEGACY")) ui.onAllNodesWithTag("model-$choice").assertCountEquals(0)
            open(Destination.LABS)
            ui.onNodeWithText(context.getString(R.string.labs_empty_title)).assertExists()
            open(Destination.CONCENTRATION)
            ui.waitUntil(30_000){ui.onAllNodesWithTag("m2-no-qualified-history").fetchSemanticsNodes().isNotEmpty()}
            assertEquals(emptyList<RecordEntity>(),runBlocking{db.dao().records()})
        } finally {
            androidx.lifecycle.ViewModelStore().apply{put("model",model);clear()}
            db.close()
        }
    }
}
