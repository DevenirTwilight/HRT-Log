package net.plainnotes.app.data

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.FileInputStream
import java.util.UUID

/** Real archive/new device keys/persistent SQLCipher/root COMMIT/reopen, then runtime requests.
 * These are synthetic fixture libraries; no public import/restore UI is enabled.
 */
class RuntimeLibraryRequestAndroidTest {
    @Test fun savedOldRequestCannotReadOrWriteAfterRealSwitchWhileFreshRequestsWork() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        LibraryRestoreAndroidTest.SourceBundle(app).use {source->LibraryRestoreAndroidTest.RootFixture(app).use {target->
            val oldFacts=target.facts();val factory=NotesRepository(target.access).pinnedTo(Space.PRIMARY)
            val saved=runBlocking {factory.generationScope()}.encoded()
            val committed=MedicalArchiveReader.read(target.session.issue(),source.password(),FileInputStream(source.file).channel).use {archive->
                LibraryRestoreCoordinator(target.access).restore(target.scope,target.session.issue(),UUID.randomUUID(),archive)
            }
            LibraryRestoreAndroidTest().verify(target,source,committed.generation)
            val restored=NotesRepository(target.access).forGeneration(requireNotNull(LibraryRequestScope.decode(saved)))
            var readDenied=false;try {runBlocking {restored.appointments()}}catch(_:LibraryScopeMismatch) {readDenied=true}
            assertTrue(readDenied)
            val fresh=NotesRepository(target.access);val current=runBlocking {fresh.currentGenerationScope()}
            assertEquals(committed.generation.toString(),current.generation)
            val valid=fresh.forGeneration(current)
            val id=runBlocking {valid.saveAppointment(AppointmentEntity(type="ENDO",at_utc=9999,at_zone="UTC",note="SYNTHETIC fresh runtime request",remind_minutes_before=0))}
            val before=runBlocking {valid.appointments()}
            var writeDenied=false;try {runBlocking {restored.deleteAppointment(id)}}catch(_:LibraryScopeMismatch) {writeDenied=true}
            assertTrue(writeDenied);assertEquals(before,runBlocking {valid.appointments()})
            runBlocking {valid.deleteAppointment(id)};assertFalse(runBlocking {valid.appointments()}.any {it.id==id})
            assertArrayEquals(oldFacts,target.facts())
            target.access.get(Space.PRIMARY).openHelper.writableDatabase.query("SELECT feature_enabled FROM attachment_store WHERE store_state='CURRENT'").use {assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
        }}
    }
}
