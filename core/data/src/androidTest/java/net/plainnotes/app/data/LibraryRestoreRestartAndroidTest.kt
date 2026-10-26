package net.plainnotes.app.data

import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real force-stop at three meaningful durability boundaries, fresh PID and device key reopen.
 * The marker contains neutral synthetic IDs/hashes only, never a plaintext record or key. */
class LibraryRestoreRestartAndroidTest {
    private fun hash(b:ByteArray)=MedicalArchiveCrypto.hex(MessageDigest.getInstance("SHA-256").digest(b))
    @Test fun persistentAuthorityChoosesExactlyOldOrVerifiedNewAfterProcessDeath() {
        val args=InstrumentationRegistry.getArguments();val phase=args.getString("generation_phase")
        assumeTrue(phase in listOf("source","prepare","verify"))
        val seam=requireNotNull(args.getString("generation_seam"))
        require(seam in setOf("PREPARED","OBJECTS_COMMITTED","IN_SWITCH","SWITCHED"))
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val marker=File(app.filesDir,"synthetic-generation-restart-ready")
        val sourceFile=File(app.cacheDir,"synthetic-library-bootstrap.cipher")
        if(phase=="source") {
            check(!marker.exists() && !sourceFile.exists())
            LibraryRestoreAndroidTest.SourceBundle(app).use {source->
                source.file.inputStream().use {input->sourceFile.outputStream().use {out->input.copyTo(out);out.fd.sync()}}
            } // All source-device aliases are gone before the restore process begins.
            return
        }
        if(phase=="prepare") {
            check(!marker.exists()) {"Prior synthetic run must be reconciled, not overwritten"}
            check(sourceFile.isFile)
            val target=LibraryRestoreAndroidTest.RootFixture(app)
            val old=target.facts();val operation=UUID.randomUUID()
            MedicalArchiveReader.read(target.session.issue(),"SYNTHETIC generation archive".toCharArray(),FileInputStream(sourceFile).channel).use {archive->
                LibraryRestoreCoordinator(target.access) {boundary->if(boundary.name==seam) {
                    val generation=target.raw.query("SELECT generation_uuid FROM library_candidate WHERE operation_uuid=?",arrayOf(operation.toString())).use {check(it.moveToFirst());it.getString(0)}
                    val json=JSONObject().put("seam",seam).put("pid",Process.myPid()).put("root",target.id.toString())
                        .put("archive",sourceFile.name).put("operation",operation.toString()).put("generation",generation)
                        .put("dataset",target.scope.dataset).put("epoch",target.scope.epoch).put("oldHash",hash(old))
                    marker.outputStream().use {out->out.write(json.toString().toByteArray(Charsets.UTF_8));out.fd.sync()}
                    check(!CountDownLatch(1).await(90,TimeUnit.SECONDS));error("Runner did not kill the live seam")
                }}.restore(target.scope,target.session.issue(),operation,archive)
            }
            error("Requested process-death seam was not reached")
        } else {
            val m=JSONObject(marker.readText());assertEquals(seam,m.getString("seam"));assertNotEquals(Process.myPid(),m.getInt("pid"))
            val name=m.getString("archive");require(name.startsWith("synthetic-library-") && name.endsWith(".cipher") && !name.contains('/'))
            val archiveFile=File(app.cacheDir,name)
            LibraryRestoreAndroidTest.RootFixture(app,UUID.fromString(m.getString("root")),create=false).use {target->
                assertEquals(m.getString("oldHash"),hash(target.facts()))
                val active=LibraryAuthority.current(target.raw)
                if(seam=="SWITCHED") {
                    assertEquals(m.getString("generation"),requireNotNull(active).generation.toString())
                    MedicalArchiveReader.read(target.session.issue(),"SYNTHETIC generation archive".toCharArray(),FileInputStream(archiveFile).channel).use {archive->
                        val manifest=archive.manifest()
                        archive.records {records->LibraryRestoreAndroidTest().verify(target,manifest,records,active.generation)}
                    }
                    val stale=NotesRepository(target.access).forGeneration(LibraryRequestScope("LEGACY",m.getString("dataset"),m.getString("epoch")))
                    val before=runBlocking {NotesRepository(target.access).appointments()}
                    var denied=false
                    try {runBlocking {stale.saveAppointment(AppointmentEntity(2,"SYNTHETIC stale",999,"UTC",remind_minutes_before=0))}}
                    catch(_:IllegalStateException) {denied=true}
                    assertTrue(denied);assertEquals(before,runBlocking {NotesRepository(target.access).appointments()})
                } else {
                    assertNull(active);assertEquals(m.getString("dataset"),target.scope.dataset)
                    val expected=if(seam=="IN_SWITCH")"READY" else "PREPARED"
                    target.raw.query("SELECT state FROM library_candidate WHERE operation_uuid=?",arrayOf(m.getString("operation"))).use {assertTrue(it.moveToFirst());assertEquals(expected,it.getString(0))}
                    if(seam=="PREPARED") {
                        val release=LibraryLifecycle(target.access).releaseUnmaterialized(target.scope,target.session.issue(),UUID.fromString(m.getString("operation")),UUID.fromString(m.getString("generation")))
                        assertFalse(release.alreadyReleased)
                        assertTrue(LibraryLifecycle(target.access).releaseUnmaterialized(target.scope,target.session.issue(),release.operation,release.generation).alreadyReleased)
                        assertFalse(File(target.directory,"library-generations-v1/${release.generation}").exists())
                    }
                    assertTrue(LibraryRestoreCoordinator(target.access).abort(target.scope,target.session.issue(),UUID.fromString(m.getString("operation"))))
                    assertEquals(m.getString("oldHash"),hash(target.facts()))
                    assertEquals(seam!="PREPARED",File(target.directory,"library-generations-v1/${m.getString("generation")}").isDirectory)
                }
            }
            check(archiveFile.delete());check(marker.delete())
        }
    }
}
