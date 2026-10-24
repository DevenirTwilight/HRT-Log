package net.plainnotes.app.data

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.AttachmentSession
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.security.KeyStore
import java.time.Instant
import java.util.UUID

/** Production components, synthetic data; no public attachment UI or permanent cleanup API. */
class LibraryLifecycleAndroidTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun wholeUidFixtureStartsWithoutUnownedAliases() {
        val keys=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        val names=keys.aliases().toList().filter {it.startsWith("notes.obj.v1.") || it.startsWith("notes.gen.db.")}
        assertTrue("Run the whole-UID inventory fixture in its own synthetic package, not among independent roots: $names",names.isEmpty())
    }
    private fun reject(action:()->Unit) {try {action();fail("Unsafe lifecycle action accepted")}catch(_:Exception){}}
    private fun save(f:LibraryRestoreAndroidTest.RootFixture):UUID {
        val bytes=byteArrayOf(9,8,7,6)
        val record=f.root.runInTransaction(java.util.concurrent.Callable {UUID.fromString(AttachmentIdentity.bind(f.raw,f.scope,"APPOINTMENT",2))})
        val doc=UUID.randomUUID()
        val r=AttachmentPersistentStore.Request(UUID.randomUUID(),doc,record,0,"SYNTHETIC lifecycle","REPORT","application/octet-stream",bytes.size.toLong(),MedicalArchiveCrypto.hex(MessageDigest.getInstance("SHA-256").digest(bytes)))
        val store=AttachmentPersistentStore(f.context,f.root)
        store.save(f.scope,f.session.issue(),r) {ByteArrayInputStream(bytes)}
        store.save(f.scope,f.session.issue(),r.copy(operation=UUID.randomUUID(),expectedRevision=1)) {ByteArrayInputStream(bytes)}
        return doc
    }
    @Test fun committedHistoryTrashAndCompleteCurrentArchiveUseExistingWriterAndReader() {
        LibraryRestoreAndroidTest.RootFixture(app).use {f->
            val doc=save(f);val lifecycle=LibraryLifecycle(f.access)
            val report=lifecycle.inventory(f.scope,f.session.issue(),UUID.randomUUID())
            assertTrue(report.items.any {it.category=="COMMITTED"});assertTrue(report.items.any {it.category=="HISTORY"})
            reject {f.access.destroy(Space.PRIMARY)} // No ordinary deletion path may discard the medical journal.

            assertEquals(LibraryLifecycle.Eligibility.COMPLETE,lifecycle.exportEligibility(f.scope,f.session.issue(),UUID.randomUUID()).eligibility)
            val out=ByteArrayOutputStream();val password="SYNTHETIC lifecycle export".toCharArray()
            lifecycle.writeArchive(f.scope,f.session.issue(),UUID.randomUUID(),password.copyOf(),out)
            val cipher=File.createTempFile("synthetic-lifecycle-",".cipher",app.cacheDir)
            try {cipher.writeBytes(out.toByteArray());MedicalArchiveReader.read(f.session.issue(),password.copyOf(),FileInputStream(cipher).channel).use {a->assertTrue(a.wholeArchiveAuthenticated);assertEquals(2,a.manifest().versions.size)}}finally {password.fill('\u0000');check(cipher.delete())}
            AttachmentPersistentStore(f.context,f.root).trash(f.scope,f.session.issue(),doc,2)
            val after=lifecycle.inventory(f.scope,f.session.issue(),UUID.randomUUID())
            assertEquals(2,after.items.count {it.category=="TRASHED"})
            assertEquals(LibraryLifecycle.Eligibility.COMPLETE,lifecycle.exportEligibility(f.scope,f.session.issue(),UUID.randomUUID()).eligibility)
            val old=f.facts();f.reopen();f.session=AttachmentSession(UUID.fromString(f.scope.dataset),UUID.fromString(f.scope.epoch))
            assertTrue(LibraryLifecycle(f.access).inventory(f.scope,f.session.issue(),UUID.randomUUID()).revision>after.revision)
            assertArrayEquals(old,f.facts())
            // One explicitly test-owned orphan proves isolation does not relax production refusal.
            val orphan=AttachmentDeviceKeys.Alias(UUID.randomUUID(),AttachmentDeviceKeys.Role.ACTIVE)
            try {
                AttachmentDeviceKeys().provisionNew(orphan)
                val unknown=LibraryLifecycle(f.access).inventory(f.scope,f.session.issue(),UUID.randomUUID())
                assertTrue(unknown.items.any {it.alias==orphan.value && it.category=="UNKNOWN" && it.reason=="UNKNOWN_ALIAS"})
                assertEquals(LibraryLifecycle.Eligibility.UNKNOWN,LibraryLifecycle(f.access).exportEligibility(f.scope,f.session.issue(),UUID.randomUUID()).eligibility)
            }finally {KeyStore.getInstance("AndroidKeyStore").apply {load(null)}.deleteEntry(orphan.value)} // Only this test's alias.
        }
    }
    @Test fun unknownFileNeverBecomesAbsentAfterRestartAndPreventsCompleteExport() {
        LibraryRestoreAndroidTest.RootFixture(app).use {f->
            save(f);val before=f.facts();val unknown=File(f.directory,"attachment-objects-v1/${UUID.randomUUID()}")
            unknown.writeBytes(byteArrayOf(1,2,3))
            val first=LibraryLifecycle(f.access).inventory(f.scope,f.session.issue(),UUID.randomUUID())
            val key=first.items.single {it.reason=="UNPROVEN_OWNER"}.key
            assertEquals(LibraryLifecycle.Eligibility.UNKNOWN,first.eligibility)
            // Test-owned external-loss injection, not a production cleanup operation.
            check(unknown.delete());f.reopen();f.session=AttachmentSession(UUID.fromString(f.scope.dataset),UUID.fromString(f.scope.epoch))
            val lifecycle=LibraryLifecycle(f.access);val after=lifecycle.inventory(f.scope,f.session.issue(),UUID.randomUUID())
            assertTrue(after.items.any {it.key==key && it.category=="UNKNOWN" && it.reason=="PREVIOUS_UNKNOWN_NOT_PRESENT"})
            assertEquals(LibraryLifecycle.Eligibility.UNKNOWN,lifecycle.exportEligibility(f.scope,f.session.issue(),UUID.randomUUID()).eligibility)
            val output=ByteArrayOutputStream();reject {lifecycle.writeArchive(f.scope,f.session.issue(),UUID.randomUUID(),"synthetic".toCharArray(),output)};assertEquals(0,output.size())
            assertArrayEquals(before,f.facts())
            // A partial scan must also retain opaque evidence, not become an empty successful set.
            val unsafe=File(f.directory,"library-generations-v1");check(unsafe.mkdir())
            android.system.Os.chmod(unsafe.path,493) // Test-owned unsafe 0755 root forces a real scan refusal.
            assertTrue(lifecycle.inventory(f.scope,f.session.issue(),UUID.randomUUID()).items.any {it.key=="scan-unavailable" && it.category=="UNKNOWN"})
            check(unsafe.delete());f.reopen();f.session=AttachmentSession(UUID.fromString(f.scope.dataset),UUID.fromString(f.scope.epoch))
            assertTrue(LibraryLifecycle(f.access).inventory(f.scope,f.session.issue(),UUID.randomUUID()).items.any {it.key=="scan-unavailable" && it.category=="UNKNOWN"})
            assertArrayEquals(before,f.facts())
        }
    }
    @Test fun liveNeverStartedOwnerCanBeAtomicallyAbortedAndReleasedAndCannotResume() {
        LibraryRestoreAndroidTest.SourceBundle(app).use {source->LibraryRestoreAndroidTest.RootFixture(app).use {f->
            val before=f.facts();val operation=UUID.randomUUID();var proof:LibraryLifecycle.Release?=null;var reached=false
            MedicalArchiveReader.read(f.session.issue(),source.password(),FileInputStream(source.file).channel).use {archive->
                reject {LibraryRestoreCoordinator(f.access) {at->if(at==LibraryRestoreCoordinator.Boundary.PREPARED) {
                    reached=true
                    val gen=f.raw.query("SELECT generation_uuid FROM library_candidate WHERE operation_uuid=?",arrayOf(operation.toString())).use {assertTrue(it.moveToFirst());UUID.fromString(it.getString(0))}
                    val lifecycle=LibraryLifecycle(f.access)
                    reject {lifecycle.releaseUnmaterialized(f.scope,f.session.issue(),operation,UUID.randomUUID())}
                    proof=lifecycle.releaseUnmaterialized(f.scope,f.session.issue(),operation,gen)
                    assertTrue(lifecycle.releaseUnmaterialized(f.scope,f.session.issue(),operation,gen).alreadyReleased)
                }}.restore(f.scope,f.session.issue(),operation,archive)}
            }
            assertTrue(reached);val saved=requireNotNull(proof);assertFalse(saved.alreadyReleased)
            assertFalse(File(f.directory,"library-generations-v1/${saved.generation}").exists())
            f.raw.query("SELECT started,released FROM library_resource_owner").use {assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0));assertEquals(1,it.getInt(1))}
            assertArrayEquals(before,f.facts());f.reopen();f.session=AttachmentSession(UUID.fromString(f.scope.dataset),UUID.fromString(f.scope.epoch))
            assertTrue(LibraryLifecycle(f.access).releaseUnmaterialized(f.scope,f.session.issue(),operation,saved.generation).alreadyReleased)
            f.root.runInTransaction {val budget=LibraryRestoreAdmission.inspect(f.context,f.raw) {};assertEquals(512,budget.aliases)}
        }}
    }
    @Test fun materializedAndRetainedLibrariesCannotBeReleasedOrIgnoredByExport() {
        LibraryRestoreAndroidTest.SourceBundle(app).use {source->LibraryRestoreAndroidTest.RootFixture(app).use {f->
            val op=UUID.randomUUID()
            val committed=MedicalArchiveReader.read(f.session.issue(),source.password(),FileInputStream(source.file).channel).use {a->LibraryRestoreCoordinator(f.access).restore(f.scope,f.session.issue(),op,a)}
            val current=f.access.get(Space.PRIMARY);val scope=requireNotNull(AttachmentIdentity.current(current.openHelper.writableDatabase))
            val session=AttachmentSession(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch))
            try {
                val lifecycle=LibraryLifecycle(f.access)
                reject {lifecycle.releaseUnmaterialized(scope,session.issue(),op,committed.generation)}
                val report=lifecycle.inventory(scope,session.issue(),UUID.randomUUID())
                assertTrue(report.items.any {it.library=="LEGACY" && it.category=="RETAINED_BUSINESS"})
                assertTrue(report.items.any {it.library==committed.generation.toString() && it.category=="COMMITTED"})
                assertEquals(LibraryLifecycle.Eligibility.REFUSED,lifecycle.exportEligibility(scope,session.issue(),UUID.randomUUID()).eligibility)
                val out=ByteArrayOutputStream();reject {lifecycle.writeArchive(scope,session.issue(),UUID.randomUUID(),source.password(),out)};assertEquals(0,out.size())
                session.revoke();reject {lifecycle.inventory(scope,session.issue(),UUID.randomUUID())}
            }finally {session.revoke()}
        }}
    }
}
