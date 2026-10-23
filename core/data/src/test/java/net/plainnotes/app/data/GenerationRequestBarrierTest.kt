package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Frozen counterexample: an old repository's displayed integer must not follow a new DB. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class GenerationRequestBarrierTest {
    @Test fun oldDisplayedIdCannotUpdateSameIntegerInNewDatabase()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        fun database()=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java)
            .allowMainThreadQueries().addCallback(SchemaGuards).build()
        val old=database();val next=database()
        try {
            val original=AppointmentEntity(1,"SYNTHETIC old",1,"UTC",remind_minutes_before=0)
            val replacement=original.copy(type="SYNTHETIC new")
            old.dao().appointment(original);next.dao().appointment(replacement)
            var selected=old
            val access=object:DatabaseAccess(context){override fun get(space:Space)=selected}
            val stale=NotesRepository(access)
            val displayed=stale.appointments().single()
            selected=next
            var refused=false
            try {stale.saveAppointment(displayed.copy(note="stale edit"))}
            catch(_:IllegalStateException) {refused=true}
            assertTrue("Old integer request followed the newly selected database",refused)
            assertEquals(listOf(replacement),next.dao().appointments())
            assertEquals(listOf(original),old.dao().appointments())
            val fresh=NotesRepository(access)
            fresh.saveAppointment(replacement.copy(note="new authorized edit"))
            assertEquals("new authorized edit",next.dao().appointments().single().note)
        } finally {old.close();next.close()}
    }
}
