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
    @Test fun onlyOneConcentrationDestinationOpensM2AndRetainsLegacyAndLabs() {
        assertEquals(1,Destination.entries.count{it==Destination.CONCENTRATION})
        assertFalse(Destination.entries.any{it.name.contains("EXPERIMENTAL")})
        val context=ui.activity
        val db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        db.openHelper.writableDatabase
        val repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
        val model=NotesViewModel(repo,ReminderCoordinator(context,repo),context)
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
            ui.waitUntil(30_000){ui.onAllNodesWithTag("model-M2").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("model-M2").assertIsSelected()
            ui.onNodeWithTag("research-not-personal").assertExists()
            ui.onNodeWithTag("model-LEGACY").performClick()
            ui.onNodeWithTag("concentration-legacy").assertExists()
            open(Destination.LABS)
            ui.onNodeWithText(context.getString(R.string.lab_add)).assertExists()
            open(Destination.CONCENTRATION)
            ui.onNodeWithTag("model-M2").assertIsSelected()
            assertEquals(emptyList<RecordEntity>(),runBlocking{db.dao().records()})
        } finally {
            androidx.lifecycle.ViewModelStore().apply{put("model",model);clear()}
            db.close()
        }
    }
}
