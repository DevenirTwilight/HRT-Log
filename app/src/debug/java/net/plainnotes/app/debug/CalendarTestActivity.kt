package net.plainnotes.app.debug

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RuleKind
import net.plainnotes.app.reminder.ReminderCoordinator
import net.plainnotes.app.ui.NotesApp
import net.plainnotes.app.ui.NotesTheme
import net.plainnotes.app.ui.ThemeMode
import net.plainnotes.app.ui.UiPrefs
import java.time.Instant
import java.time.LocalTime

/** Debug instrumentation only (B3-C): the real AppShell over a synthetic in-memory schema with one daily medication. */
class CalendarTestActivity : ComponentActivity() {
    private lateinit var fixture: CalendarFixture
    val repository get() = fixture.repository
    val model get() = fixture.model
    /** Back presses that reached the activity, standing in for MainActivity's finish / disguise-exit callback. */
    @Volatile var activityBacks = 0
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        UiPrefs(this).disclaimerAccepted = true
        fixture = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = CalendarFixture(applicationContext) as T
        })[CalendarFixture::class.java]
        val appearance = UiPrefs(this).appearance
        setContent {
            var requestOwner by remember {mutableStateOf(fixture.model)}
            NotesTheme(ThemeMode.LIGHT) {key(requestOwner) {NotesApp(requestOwner,appearance,{},onLibraryReopen={fixture.reopenLibrary();requestOwner=fixture.model})}}
        }
        // Same order as MainActivity: registered before composition, so Compose handlers take precedence.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) { override fun handleOnBackPressed() { activityBacks++ } })
    }
}
private class CalendarFixture(private val context: Context) : ViewModel() {
    private val database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build().also { it.openHelper.writableDatabase;syntheticLibraryScope(it) }
    val repository = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = database })
    private val children = ViewModelStore()
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = children }
    private fun createModel() = ViewModelProvider(owner, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NotesViewModel(repository, ReminderCoordinator(context, repository), context) as T
    })[NotesViewModel::class.java]
    @Volatile var model=createModel()
        private set
    init {
        runBlocking {
            val m = MedicationEntity(name = "Synthetic Estradiol", molecule = "E2", route = "ORAL", unit = "MG", dose_per_intake = 2.0, container_capacity = 40.0,
                soon_alert_minutes = 15, late_after_minutes = 60, site_rotation = false, notifications_on = false, active = true, sort_order = 0)
            repository.saveMedication(m, "E2", RuleKind.EVERY_N_DAYS, 1, listOf(LocalTime.of(8, 0)), emptySet(), Instant.now().minusSeconds(3 * 86400))
        }
        model.sync()
    }
    fun reopenLibrary() {children.clear();model=createModel()}
    override fun onCleared() { children.clear(); database.close() }
}
