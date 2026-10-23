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
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class LibraryRestoreSequencesTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private fun database()=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
    private fun record(id:Long)=AttachmentBackupManifest.Record(UUID.randomUUID(),AttachmentBackupManifest.Kind.APPOINTMENT,id,"PURGED",null,"0".repeat(64))
    private fun appointment(id:Long=0)=AppointmentEntity(id,"SYNTHETIC allocator",1000,"UTC",remind_minutes_before=0)
    @Test fun realRebuildLosesSequenceButRetainedTrashAndTombstonesReserveItBeforeNewWrites()=runBlocking {
        val source=database();val target=database()
        try {
            source.dao().appointment(appointment(2));source.dao().appointment(appointment(9))
            source.runInTransaction {
                val raw=source.openHelper.writableDatabase
                Trash.insert(raw,Trash.APPOINTMENT,"9","1970-01-01",Trash.payload("appointment" to Trash.rows(raw,"appointment","id=?",arrayOf(9))),2000)
                raw.execSQL("DELETE FROM appointment WHERE id=9")
            }
            val tables=RawData.dump(source.openHelper.writableDatabase)
            val raw=target.openHelper.writableDatabase;raw.execSQL("PRAGMA temp_store=MEMORY")
            target.runInTransaction {RawData.reconstructIsolated(raw,BackupTables.of(tables)) {}}
            // Frozen actual old behavior: reconstruction replays only live row2, so next ID is3.
            // Roll back this observation; do not lower a counter or modify production to create RED.
            raw.beginTransaction()
            try {
                val next=raw.compileStatement("INSERT INTO appointment(type,at_utc,at_zone,remind_minutes_before) VALUES ('SYNTHETIC probe',1000,'UTC',0)").use {it.executeInsert()}
                assertEquals(3L,next)
            }finally {raw.endTransaction()}
            target.runInTransaction {LibraryRestoreSequences.reserve(raw,listOf(record(3))) {}}
            val next=target.dao().appointment(appointment())
            assertEquals(10L,next)
            val repo=NotesRepository(object:DatabaseAccess(context) {override fun get(space:Space)=target})
            repo.restoreTrash(repo.trash().single().id)
            assertEquals(setOf(2L,9L,10L),repo.appointments().map {it.id}.toSet())
            assertEquals(appointment(9),repo.appointments().single {it.id==9L})
        }finally {source.close();target.close()}
    }
    @Test fun cannotLowerExistingSequenceAndMaximumIdFailsRatherThanWraps()=runBlocking {
        val db=database()
        try {
            db.dao().appointment(appointment(10));val raw=db.openHelper.writableDatabase
            db.runInTransaction {LibraryRestoreSequences.reserve(raw,listOf(record(3))) {}}
            assertEquals(11L,db.dao().appointment(appointment()))
            db.runInTransaction {LibraryRestoreSequences.reserve(raw,listOf(record(Long.MAX_VALUE))) {}}
            var refused=false
            try {db.dao().appointment(appointment())}catch(_:android.database.SQLException) {refused=true}
            assertTrue(refused);assertEquals(listOf(10L,11L),db.dao().appointments().map {it.id}.sorted())
            raw.query("SELECT seq FROM sqlite_sequence WHERE name='appointment'").use {assertTrue(it.moveToFirst());assertEquals(Long.MAX_VALUE,it.getLong(0))}
        }finally {db.close()}
    }
}
