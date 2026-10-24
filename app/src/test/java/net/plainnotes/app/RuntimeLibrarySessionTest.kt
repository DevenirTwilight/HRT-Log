package net.plainnotes.app

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.runtime.RuntimeLibrarySession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class RuntimeLibrarySessionTest {
    @Test fun restoredUnknownDraftRefusesBeforeReadingOrBindingANewLibrary()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();var touched=false
        val repo=NotesRepository(object:DatabaseAccess(context) {override fun get(space:Space):NotesDatabase {touched=true;error("Unknown draft must not read a library")}})
        val session=RuntimeLibrarySession(repo,SavedStateHandle(mapOf(RuntimeLibrarySession.RESTORED to true)))
        var denied=false;try {session.initialize()}catch(_:LibraryScopeMismatch) {denied=true}
        assertTrue(denied);assertFalse(touched)
        assertEquals(RuntimeLibrarySession.State.Unavailable(R.string.library_scope_unknown),session.state.value)
    }
    @Test fun screenAndSavedDraftKeepOldIdentityWhileExplicitFreshOwnerCanUseNewLibrary()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        fun db()=syntheticLibrary(Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build())
        val first=db();val next=db()
        try {
            var selected=first;val source=NotesRepository(object:DatabaseAccess(context) {override fun get(space:Space)=selected})
            val saved=SavedStateHandle();val screen=RuntimeLibrarySession(source,saved);val scoped=screen.initialize()
            val original=(screen.state.value as RuntimeLibrarySession.State.Ready).scope
            val recreated=RuntimeLibrarySession(source,SavedStateHandle(mapOf(RuntimeLibrarySession.SAVED_SCOPE to original.encoded(),RuntimeLibrarySession.RESTORED to true)))
            recreated.initialize();assertEquals(original,(recreated.state.value as RuntimeLibrarySession.State.Ready).scope)
            selected=next
            var denied=false;try {screen.verify()}catch(_:LibraryScopeMismatch){denied=true};assertTrue(denied)
            try {scoped.saveAppointment(AppointmentEntity(type="ENDO",at_utc=1,at_zone="UTC",remind_minutes_before=0));fail("Old screen wrote new data")}catch(_:IllegalStateException){}
            assertTrue(next.dao().appointments().isEmpty());assertEquals(original.encoded(),saved.get<ArrayList<String>>(RuntimeLibrarySession.SAVED_SCOPE))
            val restored=RuntimeLibrarySession(source,SavedStateHandle(mapOf(RuntimeLibrarySession.SAVED_SCOPE to original.encoded(),RuntimeLibrarySession.RESTORED to true)))
            try {restored.initialize();fail("Old saved draft retagged")}catch(_:LibraryScopeMismatch){}
            val fresh=RuntimeLibrarySession(source,SavedStateHandle());val permitted=fresh.initialize()
            assertNotEquals(original,(fresh.state.value as RuntimeLibrarySession.State.Ready).scope)
            permitted.saveAppointment(AppointmentEntity(type="ENDO",at_utc=1,at_zone="UTC",remind_minutes_before=0))
            assertEquals(1,next.dao().appointments().size);assertTrue(first.dao().appointments().isEmpty())
            fresh.pause();try {permitted.deleteAppointment(1);fail("Locked owner wrote")}catch(_:LibraryRequestRevoked){}
            assertEquals(1,next.dao().appointments().size);fresh.resume();permitted.deleteAppointment(1);assertTrue(next.dao().appointments().isEmpty())
        } finally {first.close();next.close()}
    }
}
