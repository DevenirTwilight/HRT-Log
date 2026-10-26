package net.plainnotes.app.data

import android.content.Context
import android.content.ContextWrapper
import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.AttachmentSession
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.time.Instant
import java.util.UUID

/** Genuine production archive -> independent reader -> durable SQLCipher -> authority -> reopen.
 * Only synthetic originals and isolated local-device key identities. Physical phone is NOT_RUN. */
class LibraryRestoreAndroidTest {
    internal class RootFixture(val app:Context,val id:UUID=UUID.randomUUID(),create:Boolean=true,seed:Boolean=true):AutoCloseable {
        val directory=File(app.noBackupFilesDir,"synthetic-library-$id")
        val context=object:ContextWrapper(app) {
            override fun getApplicationContext():Context=this
            override fun getNoBackupFilesDir()=directory
            override fun getDatabasePath(name:String)=File(directory,name)
        }
        var access=DatabaseAccess(context,id)
        val root get()=access.legacyRoot()
        val raw get()=root.openHelper.writableDatabase
        val scope:AttachmentIdentity.Scope
        var session:AttachmentSession
        init {
            if(create)Os.mkdir(directory.path,448)
            scope=requireNotNull(AttachmentIdentity.current(access.get(Space.PRIMARY).openHelper.writableDatabase))
            session=AttachmentSession(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch))
            if(create && seed)runBlocking {
                root.dao().appointment(AppointmentEntity(2,"SYNTHETIC old recipient",999,"UTC",note="Retain original dataset",remind_minutes_before=0))
            }
        }
        fun facts():ByteArray=root.runInTransaction(java.util.concurrent.Callable {MeasuredBackupJson.encode(raw,Instant.EPOCH)})
        fun reopen() {session.revoke();access.close(Space.PRIMARY);access=DatabaseAccess(context,id)}
        override fun close() {
            // TEST-OWNED cleanup only, not a production deletion policy/API.
            val aliases=mutableSetOf("notes.local.$id.notes.wrap")
            try {
                raw.query("SELECT database_alias,alias_plan FROM library_candidate").use {c->while(c.moveToNext()) {
                    aliases+=c.getString(0);LibraryRestoreObjects.decode(c.getBlob(1)).forEach {s->
                        aliases+=AttachmentDeviceKeys.Alias(s.staging,AttachmentDeviceKeys.Role.STAGING).value
                        aliases+=AttachmentDeviceKeys.Alias(s.active,AttachmentDeviceKeys.Role.ACTIVE).value
                    }
                }}
                raw.query("SELECT stage_alias_uuid,active_alias_uuid FROM attachment_journal").use {c->while(c.moveToNext()) {
                    aliases+=AttachmentDeviceKeys.Alias(UUID.fromString(c.getString(0)),AttachmentDeviceKeys.Role.STAGING).value
                    aliases+=AttachmentDeviceKeys.Alias(UUID.fromString(c.getString(1)),AttachmentDeviceKeys.Role.ACTIVE).value
                }}
                access.get(Space.PRIMARY).openHelper.writableDatabase.query("SELECT stage_alias_uuid,active_alias_uuid FROM attachment_journal").use {c->while(c.moveToNext()) {
                    aliases+=AttachmentDeviceKeys.Alias(UUID.fromString(c.getString(0)),AttachmentDeviceKeys.Role.STAGING).value
                    aliases+=AttachmentDeviceKeys.Alias(UUID.fromString(c.getString(1)),AttachmentDeviceKeys.Role.ACTIVE).value
                }}
            } finally {
                session.revoke();access.close(Space.PRIMARY)
                val ks=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
                aliases.forEach {ks.deleteEntry(it)};directory.deleteRecursively()
            }
        }
    }
    internal class SourceBundle(app:Context):AutoCloseable {
        val file=File.createTempFile("synthetic-library-",".cipher",app.cacheDir)
        val manifest:AttachmentBackupManifest
        val records:ByteArray
        init {
            var m:AttachmentBackupManifest?=null;var b:ByteArray?=null
            AttachmentBackupPreparationAndroidTest.Fixture(app).use {f->
                AttachmentBusinessRestoreAndroidTest().build(f)
                AttachmentBackupPreparation(f.context,f.db).capture(f.scope,f.session.issue(),created=Instant.EPOCH).use {snapshot->
                    m=snapshot.manifest();snapshot.records {b=it.copyOf()}
                    MedicalArchiveWriter.write(snapshot,f.session.issue(),password(),file.outputStream())
                }
            }
            manifest=requireNotNull(m);records=requireNotNull(b)
        }
        fun password()="SYNTHETIC generation archive".toCharArray()
        override fun close() {records.fill(0);check(file.delete())}
    }
    internal fun verify(f:RootFixture,b:SourceBundle,expectedGeneration:UUID)=verify(f,b.manifest,b.records,expectedGeneration)
    internal fun verify(f:RootFixture,manifest:AttachmentBackupManifest,records:ByteArray,expectedGeneration:UUID,created:Instant=Instant.EPOCH) {
        val db=f.access.get(Space.PRIMARY);assertEquals(expectedGeneration.toString(),DatabaseAccess.handleIdentity(db))
        val scope=requireNotNull(AttachmentIdentity.current(db.openHelper.writableDatabase))
        assertNotEquals(manifest.dataset.toString(),scope.dataset)
        assertNotEquals(manifest.epoch.toString(),scope.epoch)
        val session=AttachmentSession(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch))
        try {
            AttachmentBackupPreparation(f.context,db).capture(scope,session.issue(),created=created).use {snapshot->
                assertEquals(AttachmentCandidateMetadata.normalized(manifest.copy(dataset=UUID.fromString(scope.dataset),epoch=UUID.fromString(scope.epoch))),AttachmentCandidateMetadata.normalized(snapshot.manifest()))
                snapshot.records {assertArrayEquals(records,it)}
            }
            db.openHelper.writableDatabase.query("SELECT SUM(stage_uses+active_uses),COUNT(*) FROM attachment_journal").use {assertTrue(it.moveToFirst());assertEquals(8L,it.getLong(0));assertEquals(4L,it.getLong(1))}
            db.openHelper.writableDatabase.query("SELECT feature_enabled FROM attachment_store WHERE store_state='CURRENT'").use {assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
        } finally {session.revoke()}
    }
    @Test fun realArchiveActivatesFreshPersistentLibraryAndRejectsOldRequestsAndReaders() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val started=android.os.SystemClock.elapsedRealtime();val samples=org.json.JSONArray()
        fun sample(stage:String) {
            val vm=Runtime.getRuntime();val memory=android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(memory)
            val rss=File("/proc/self/status").readLines().firstOrNull {it.startsWith("VmRSS:")}?.trim()?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull()
            samples.put(org.json.JSONObject().put("stage",stage).put("elapsedMs",android.os.SystemClock.elapsedRealtime()-started)
                .put("javaUsedBytes",vm.totalMemory()-vm.freeMemory()).put("javaMaxBytes",vm.maxMemory())
                .put("nativeHeapAllocatedBytes",android.os.Debug.getNativeHeapAllocatedSize()).put("totalPssKiB",memory.totalPss)
                .put("rssKiB",rss ?: org.json.JSONObject.NULL))
            File(app.filesDir,"synthetic-library-memory.json").writeText(org.json.JSONObject().put("api",android.os.Build.VERSION.SDK_INT)
                .put("pid",android.os.Process.myPid()).put("sampledNotPeak",true).put("samples",samples).toString())
        }
        sample("before_export")
        SourceBundle(app).use {source->sample("after_export");RootFixture(app).use {target->
            val original=target.facts();val oldRepo=NotesRepository(target.access)
            val displayed=runBlocking {oldRepo.appointments().single()}
            val record=target.root.runInTransaction(java.util.concurrent.Callable {UUID.fromString(AttachmentIdentity.bind(target.raw,target.scope,"APPOINTMENT",2))})
            val oldBytes=byteArrayOf(9,8,7)
            val request=AttachmentPersistentStore.Request(UUID.randomUUID(),UUID.randomUUID(),record,0,"Synthetic old original","REPORT","application/octet-stream",oldBytes.size.toLong(),MedicalArchiveCrypto.hex(java.security.MessageDigest.getInstance("SHA-256").digest(oldBytes)))
            val oldStore=AttachmentPersistentStore(target.context,target.root)
            oldStore.save(target.scope,target.session.issue(),request) {ByteArrayInputStream(oldBytes)}
            val oldReader=oldStore.open(target.scope,target.session.issue(),request.document)
            val operation=UUID.randomUUID();var closeRefused=false
            val result=MedicalArchiveReader.read(target.session.issue(),source.password(),FileInputStream(source.file).channel).use {archive->
                sample("after_reader_auth")
                LibraryRestoreCoordinator(target.access) {
                    sample(it.name)
                    if(it==LibraryRestoreCoordinator.Boundary.OBJECTS_COMMITTED) {
                        try {target.access.close(Space.PRIMARY);fail("Close entered a live restore")}
                        catch(e:IllegalStateException) {assertEquals("Database generation work is still running",e.message);closeRefused=true}
                    }
                }.restore(target.scope,target.session.issue(),operation,archive)
            }
            assertTrue(closeRefused);assertTrue(result.requiresNewSession);assertArrayEquals(original,target.facts())
            reject {runBlocking {oldRepo.saveAppointment(displayed.copy(note="stale edit"))}}
            val refused=ByteArray(3) {4};reject {oldReader.readAt(0,refused,0,3)};assertArrayEquals(ByteArray(3),refused);oldReader.close()
            verify(target,source,result.generation)
            val currentDb=target.access.get(Space.PRIMARY);val currentScope=requireNotNull(AttachmentIdentity.current(currentDb.openHelper.writableDatabase))
            val live=AttachmentSession(UUID.fromString(currentScope.dataset),UUID.fromString(currentScope.epoch))
            try {
                val aliases=AttachmentDeviceKeys().retainedAliases(4096)
                MedicalArchiveReader.read(live.issue(),source.password(),FileInputStream(source.file).channel).use {archive->
                    assertEquals(result,LibraryRestoreCoordinator(target.access).restore(currentScope,live.issue(),operation,archive))
                }
                assertEquals(aliases,AttachmentDeviceKeys().retainedAliases(4096))
            }finally {live.revoke()}
            target.reopen();verify(target,source,result.generation)
            // Even a fresh DI repository cannot retag a saved old integer after restart.
            val fresh=NotesRepository(target.access)
            reject {runBlocking {fresh.saveAppointment(displayed)}}
            val scoped=runBlocking {fresh.forGeneration(fresh.generationScope())}
            val current=runBlocking {scoped.appointments().single()}
            runBlocking {scoped.saveAppointment(current.copy(note="SYNTHETIC scoped new edit"))}
            assertEquals("SYNTHETIC scoped new edit",runBlocking {scoped.appointments().single().note})
            // DAO + raw SQL from the nested child Room thread must route without trying to
            // reacquire its parent root writer (delete/trash restore are genuine mixed paths).
            runBlocking {
                scoped.deleteAppointment(current.id,Instant.ofEpochMilli(9000))
                assertTrue(scoped.appointments().isEmpty())
                val item=scoped.trash().single {it.kind==Trash.APPOINTMENT && it.ref==current.id.toString()}
                scoped.restoreTrash(item.id,Instant.ofEpochMilli(10000))
                assertEquals("SYNTHETIC scoped new edit",scoped.appointments().single().note)
                val next=scoped.saveAppointment(AppointmentEntity(type="SYNTHETIC new",at_utc=11000,at_zone="UTC",remind_minutes_before=0))
                assertTrue(next>source.manifest.records.filter {it.kind==AttachmentBackupManifest.Kind.APPOINTMENT}.maxOf {it.sourceId})
            }
            assertArrayEquals(original,target.facts());original.fill(0)
        }}
    }
    @Test fun failuresBeforeAndInsideSwitchRetainOldFactsAndReservationsWithoutAdoption() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        SourceBundle(app).use {source->
            for(stop in listOf(LibraryRestoreCoordinator.Boundary.PREPARED,LibraryRestoreCoordinator.Boundary.OBJECTS_COMMITTED,LibraryRestoreCoordinator.Boundary.IN_SWITCH))RootFixture(app).use {target->
                val before=target.facts();val op=UUID.randomUUID();var reached=false
                MedicalArchiveReader.read(target.session.issue(),source.password(),FileInputStream(source.file).channel).use {archive->
                    reject {LibraryRestoreCoordinator(target.access) {if(it==stop) {reached=true;error("Synthetic $stop")}}.restore(target.scope,target.session.issue(),op,archive)}
                }
                assertTrue(reached);assertNull(LibraryAuthority.current(target.raw));assertArrayEquals(before,target.facts())
                target.raw.query("SELECT state,reserved_aliases,reserved_ciphertext FROM library_candidate WHERE operation_uuid=?",arrayOf(op.toString())).use {assertTrue(it.moveToFirst());assertEquals("ABORTED",it.getString(0));assertEquals(9L,it.getLong(1));assertTrue(it.getLong(2)>0)}
                val aliases=AttachmentDeviceKeys().retainedAliases(4096)
                assertTrue(LibraryRestoreCoordinator(target.access).abort(target.scope,target.session.issue(),op))
                assertEquals(aliases,AttachmentDeviceKeys().retainedAliases(4096));assertArrayEquals(before,target.facts());before.fill(0)
            }
        }
    }
    @Test fun revocationRetainsOldLibraryAndNeedsExplicitFreshAuthorityToAbort() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        SourceBundle(app).use {source->RootFixture(app).use {target->
            val before=target.facts();val op=UUID.randomUUID();var reached=false
            MedicalArchiveReader.read(target.session.issue(),source.password(),FileInputStream(source.file).channel).use {archive->
                reject {LibraryRestoreCoordinator(target.access) {if(it==LibraryRestoreCoordinator.Boundary.PREPARED) {reached=true;target.session.revoke()}}.restore(target.scope,target.session.issue(),op,archive)}
            }
            assertTrue(reached);assertNull(LibraryAuthority.current(target.raw));assertArrayEquals(before,target.facts())
            target.session=AttachmentSession(UUID.fromString(target.scope.dataset),UUID.fromString(target.scope.epoch))
            val coordinator=LibraryRestoreCoordinator(target.access)
            assertEquals(LibraryRestoreCoordinator.OperationState("PREPARED",target.raw.query("SELECT generation_uuid FROM library_candidate").use {assertTrue(it.moveToFirst());UUID.fromString(it.getString(0))},false),coordinator.state(target.scope,target.session.issue(),op))
            assertTrue(coordinator.abort(target.scope,target.session.issue(),op));assertTrue(coordinator.abort(target.scope,target.session.issue(),op))
            assertArrayEquals(before,target.facts());before.fill(0)
        }}
    }
    @Test fun jointAliasAndStrictHeadroomRefuseBeforeAnyCandidateOrTargetKey() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        SourceBundle(app).use {source->RootFixture(app).use {target->
            val before=target.facts();val aliases=AttachmentDeviceKeys().retainedAliases(4096)
            for(policy in listOf(LibraryRestorePolicy(maximumAliases=0),LibraryRestorePolicy(minimumFreeBytes=64L*1024*1024*1024))) {
                MedicalArchiveReader.read(target.session.issue(),source.password(),FileInputStream(source.file).channel).use {archive->
                    reject {LibraryRestoreCoordinator(target.access,policy).restore(target.scope,target.session.issue(),UUID.randomUUID(),archive)}
                }
                target.raw.query("SELECT COUNT(*) FROM library_candidate").use {assertTrue(it.moveToFirst());assertEquals(0L,it.getLong(0))}
                assertNull(LibraryAuthority.current(target.raw));assertArrayEquals(before,target.facts())
                assertEquals(aliases,AttachmentDeviceKeys().retainedAliases(4096))
                assertFalse(File(target.directory,"library-generations-v1").exists())
            }
            before.fill(0)
        }}
    }
    private fun reject(action:()->Unit) {try {action();fail("Unsafe action accepted")}catch(_:Exception) {}}
    @Test fun applicationTransferFacadeAuthenticatesBeforeCommitAndKeepsOldScopeInvalid()=runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        for(format in listOf(1,2)) {
            val file=File.createTempFile("synthetic-public-transfer-",".cipher",app.cacheDir)
            val document=UUID.randomUUID()
            fun transferPassword()=if(format==1)charArrayOf('p')else "SYNTHETIC transfer password".toCharArray()
            try {
                RootFixture(app).use {source->
                    val repo=NotesRepository(source.access).forGeneration(LibraryRequestScope("LEGACY",source.scope.dataset,source.scope.epoch)).pinnedTo(Space.PRIMARY)
                    repo.saveLab(LabValueEntity(analyte_code="E2",value=123.0,unit="pg/mL",sampled_utc=1000,sampled_zone="UTC"))
                    val cap=source.session.issue();val ops=repo.medicalAttachments()
                    ops.import(cap,MedicalAttachmentOperations.Import(UUID.randomUUID(),document,MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.APPOINTMENT,2),"SYNTHETIC facade"),MedicalAttachmentOperationAndroidTest().uri("unknown"))
                    file.outputStream().use {out->if(format==1)ops.writeArchive(cap,transferPassword(),out)else repo.medicalLibraryTransfer().export(cap,transferPassword(),out)}
                }
                RootFixture(app).use {target->
                    val old=LibraryRequestScope("LEGACY",target.scope.dataset,target.scope.epoch)
                    val repo=NotesRepository(target.access).forGeneration(old).pinnedTo(Space.PRIMARY)
                    val facade=repo.medicalLibraryTransfer();val cap=target.session.issue();val before=target.facts()
                    val password=transferPassword()
                    facade.prepare(cap,password,FileInputStream(file).channel).use {prepared->
                        assertTrue(password.all{it=='\u0000'});assertEquals(format,prepared.summary.format);assertEquals(1,prepared.summary.versions)
                        assertArrayEquals(before,target.facts())
                        val receipt=prepared.restore();assertEquals(prepared.operation,receipt.operation)
                        try {prepared.restore();fail("Restore write replayed")}catch(_:IllegalStateException){}
                        val current=NotesRepository(target.access);val scope=current.currentGenerationScope()
                        assertNotEquals(old,scope)
                        val fresh=current.forGeneration(scope).pinnedTo(Space.PRIMARY)
                        val session=AttachmentSession(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch))
                        try {
                            assertEquals(123.0,fresh.labs().single().value,0.0)
                            val state=requireNotNull(fresh.medicalLibraryTransfer().status(session.issue(),receipt.operation))
                            assertTrue(state.committed);assertTrue(state.currentlyActive)
                            val restoredTarget=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.APPOINTMENT,fresh.appointments().single().id)
                            val docs=fresh.medicalAttachments().documents(session.issue(),restoredTarget)
                            assertEquals(document,docs.single().document)
                            fresh.medicalAttachments().open(session.issue(),restoredTarget,document).use {read->
                                val bytes=ByteArray(1);assertEquals(1,read.readAt(0,bytes,0,1));bytes.fill(0)
                            }
                            try {facade.status(cap,receipt.operation);fail("Old scope rebound")}catch(_:Exception){}
                        }finally {session.revoke()}
                    }
                }
            }finally {file.delete()}
        }
    }

}
