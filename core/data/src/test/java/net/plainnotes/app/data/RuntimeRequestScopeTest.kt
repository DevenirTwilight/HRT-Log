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

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class RuntimeRequestScopeTest {
    @Test fun revokedRequestRefusesBeforeOpeningStorageAndLateRevocationRollsBack()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val scope=LibraryRequestScope("LEGACY",java.util.UUID.randomUUID().toString(),java.util.UUID.randomUUID().toString())
        var touched=false
        val unopened=NotesRepository(object:DatabaseAccess(context) {override fun get(space:Space):NotesDatabase {touched=true;error("Revoked request opened storage")}})
            .forGeneration(scope) {throw LibraryRequestRevoked()}
        try {unopened.appointments();fail("Revoked read accepted")}catch(_:LibraryRequestRevoked){}
        try {unopened.deleteAppointment(1);fail("Revoked write accepted")}catch(_:LibraryRequestRevoked){}
        try {unopened.destroyAll();fail("Revoked wipe accepted")}catch(_:LibraryRequestRevoked){}
        assertFalse(touched)
        val db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        try {
            db.runInTransaction {AttachmentIdentity.initialize(db.openHelper.writableDatabase,true)}
            val source=NotesRepository(object:DatabaseAccess(context) {override fun get(space:Space)=db})
            val expected=source.currentGenerationScope();var allowed=true
            val request=source.forGeneration(expected) {if(!allowed)throw LibraryRequestRevoked()}
            try {
                request.transaction {dao->dao.appointment(AppointmentEntity(type="ENDO",at_utc=1,at_zone="UTC",remind_minutes_before=0));allowed=false}
                fail("Late revocation committed")
            }catch(_:LibraryRequestRevoked){}
            assertTrue(db.dao().appointments().isEmpty())
        }finally {db.close()}
    }
    @Test fun restoredOldScopeCannotReadNewLibraryBeforeAnyPriorHandleBinding()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        fun database(label:String)=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries()
            .addCallback(SchemaGuards).build().also {db->db.runInTransaction {
                AttachmentIdentity.initialize(db.openHelper.writableDatabase,true)
                db.openHelper.writableDatabase.execSQL("INSERT INTO appointment(id,type,at_utc,at_zone,note,remind_minutes_before) VALUES(1,'ENDO',1,'UTC',?,0)",arrayOf(label))
            }}
        val old=database("SYNTHETIC old");val next=database("SYNTHETIC new")
        try {
            val identity=requireNotNull(AttachmentIdentity.current(old.openHelper.writableDatabase))
            val captured=LibraryRequestScope(DatabaseAccess.handleIdentity(old),identity.dataset,identity.epoch)
            val access=object:DatabaseAccess(context) {override fun get(space:Space)=next}
            val restored=NotesRepository(access).forGeneration(captured)
            var refused=false
            try {restored.appointments()}catch(_:IllegalStateException) {refused=true}
            assertTrue("A restored old page read same-integer facts from a different library",refused)
            assertEquals("SYNTHETIC old",old.dao().appointments().single().note)
            assertEquals("SYNTHETIC new",next.dao().appointments().single().note)
        } finally {old.close();next.close()}
    }
    @Test fun restoredOldEpochCannotReadReplacedFactsInsideTheSamePhysicalDatabase()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        try {
            db.runInTransaction {AttachmentIdentity.initialize(db.openHelper.writableDatabase,true)}
            val identity=requireNotNull(AttachmentIdentity.current(db.openHelper.writableDatabase))
            val captured=LibraryRequestScope(DatabaseAccess.handleIdentity(db),identity.dataset,identity.epoch)
            val access=object:DatabaseAccess(context) {override fun get(space:Space)=db}
            val restored=NotesRepository(access).forGeneration(captured)
            db.runInTransaction {AttachmentIdentity.replaceStructuredDataset(db.openHelper.writableDatabase)}
            var refused=false;try {restored.appointments()}catch(_:IllegalStateException) {refused=true}
            assertTrue("Saved old scope read through a new dataset/epoch",refused)
        } finally {db.close()}
    }
}
