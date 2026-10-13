package net.plainnotes.app.debug

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        UiPrefs(this).disclaimerAccepted = true
        fixture = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = CalendarFixture(applicationContext) as T
        })[CalendarFixture::class.java]
        val appearance = UiPrefs(this).appearance
        setContent { NotesTheme(ThemeMode.LIGHT) { NotesApp(fixture.model, appearance, {}) } }
    }
}
private class CalendarFixture(context: Context) : ViewModel() {
    private val database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build().also { it.openHelper.writableDatabase }
    val repository = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = database })
    private val children = ViewModelStore()
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = children }
    val model = ViewModelProvider(owner, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NotesViewModel(repository, ReminderCoordinator(context, repository), context) as T
    })[NotesViewModel::class.java]
    init {
        runBlocking {
            val m = MedicationEntity(name = "Synthetic Estradiol", molecule = "E2", route = "ORAL", unit = "MG", dose_per_intake = 2.0, container_capacity = 40.0,
                soon_alert_minutes = 15, late_after_minutes = 60, site_rotation = false, notifications_on = false, active = true, sort_order = 0)
            repository.saveMedication(m, "E2", RuleKind.EVERY_N_DAYS, 1, listOf(LocalTime.of(8, 0)), emptySet(), Instant.now().minusSeconds(3 * 86400))
        }
        model.sync()
    }
    override fun onCleared() { children.clear(); database.close() }
}
