package net.plainnotes.app.debug

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.room.Room
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.data.*
import net.plainnotes.app.reminder.ReminderCoordinator
import net.plainnotes.app.ui.DataSection

/** Debug instrumentation only: synthetic in-memory schema, retained model, real production data UI. */
class DataPrivacyTestActivity:ComponentActivity() {
    private lateinit var fixture:PrivacyFixture
    val model get()=fixture.model
    val repository get()=fixture.repository
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        fixture=ViewModelProvider(this,object:ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun<T:ViewModel> create(modelClass:Class<T>):T=PrivacyFixture(applicationContext) as T
        })[PrivacyFixture::class.java]
        setContent {MaterialTheme {Column(Modifier.verticalScroll(rememberScrollState())){DataSection(model,emptyMap())}}}
    }
}
private class PrivacyFixture(context:Context):ViewModel() {
    private val database=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build().also{it.openHelper.writableDatabase}
    val repository=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=database})
    private val children=ViewModelStore()
    private val owner=object:ViewModelStoreOwner{override val viewModelStore=children}
    val model=ViewModelProvider(owner,object:ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun<T:ViewModel> create(modelClass:Class<T>):T=
            NotesViewModel(repository,ReminderCoordinator(context,repository),context) as T
    })[NotesViewModel::class.java]
    override fun onCleared(){children.clear();database.close()}
}
