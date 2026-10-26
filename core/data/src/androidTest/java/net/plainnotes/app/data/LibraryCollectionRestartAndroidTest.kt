package net.plainnotes.app.data

import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.app.domain.AttachmentSession
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

/** Separate real processes at group boundaries; markers contain only synthetic neutral IDs/hash. */
class LibraryCollectionRestartAndroidTest {
    private fun password()="SYNTHETIC collection restart".toCharArray()
    private fun hash(bytes:ByteArray)=MedicalArchiveCrypto.hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    @Test fun persistentAuthorityChoosesExactlyOldOrVerifiedNewAfterProcessDeath() {
        val args=InstrumentationRegistry.getArguments();val phase=args.getString("generation_phase")
        assumeTrue(phase in listOf("prepare","verify"))
        val seam=requireNotNull(args.getString("generation_seam"));require(seam in setOf("PREPARED","OBJECTS_COMMITTED","IN_SWITCH","SWITCHED"))
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val marker=File(app.filesDir,"synthetic-collection-restart-ready")
        if(phase=="prepare") {
            check(!marker.exists())
            // The four fault cases restore the SAME encrypted fixture. Generate
            // it once, not again inside every60s live-seam startup window. Each
            // case still authenticates it and reconstructs a fresh target in a
            // separate process; neither KDF nor any restore work is bypassed.
            val file=File(app.cacheDir,"synthetic-collection-restart-fixture.cipher")
            if(seam=="PREPARED") {
                check(file.createNewFile())
                LibraryRestoreAndroidTest.SourceBundle(app).use {source->LibraryRestoreAndroidTest.RootFixture(app).use {a->
                MedicalArchiveReader.read(a.session.issue(),source.password(),FileInputStream(source.file).channel).use {
                    LibraryRestoreCoordinator(a.access).restore(a.scope,a.session.issue(),UUID.randomUUID(),it)
                }
                val db=a.access.get(Space.PRIMARY);val scope=requireNotNull(AttachmentIdentity.current(db.openHelper.writableDatabase))
                val session=AttachmentSession(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch))
                try {LibraryCollectionCapture(a.access).write(scope,session.issue(),UUID.randomUUID(),password(),file.outputStream())}finally {session.revoke()}
                }}
            } else check(file.isFile && file.length()>80)
            val target=LibraryRestoreAndroidTest.RootFixture(app);val original=hash(target.facts());val operation=UUID.randomUUID()
            MedicalCollectionReader.read(target.session.issue(),password(),FileInputStream(file).channel).use {archive->
                LibraryCollectionRestore(target.access,checkpoint={boundary->if(boundary.name==seam) {
                    // An empty retained member also has this boundary. Stop only after an
                    // actual original-bearing child committed its objects, not the empty child.
                    val hasObjects=target.raw.query("SELECT 1 FROM library_candidate c JOIN library_resource_owner o USING(operation_uuid) WHERE c.reserved_aliases>1 AND o.started=1 AND c.directory_inode IS NOT NULL LIMIT 1").use {it.moveToFirst()}
                    if(seam=="OBJECTS_COMMITTED" && !hasObjects)return@LibraryCollectionRestore
                    val json=JSONObject().put("seam",seam).put("pid",Process.myPid()).put("root",target.id.toString())
                        .put("archive",file.name).put("operation",operation.toString()).put("oldHash",original)
                    marker.outputStream().use {out->out.write(json.toString().toByteArray(Charsets.UTF_8));out.fd.sync()}
                    check(!CountDownLatch(1).await(90,TimeUnit.SECONDS));error("Runner failed to force-stop")
                }}).restore(target.scope,target.session.issue(),operation,archive)
            }
            error("Process-death boundary not reached")
        }else {
            val m=JSONObject(marker.readText());assertEquals(seam,m.getString("seam"));assertNotEquals(Process.myPid(),m.getInt("pid"))
            val name=m.getString("archive");require(name.startsWith("synthetic-collection-restart-") && name.endsWith(".cipher") && !name.contains('/'))
            val file=File(app.cacheDir,name)
            LibraryRestoreAndroidTest.RootFixture(app,UUID.fromString(m.getString("root")),create=false).use {target->
                assertEquals(m.getString("oldHash"),hash(target.facts()))
                val operation=UUID.fromString(m.getString("operation"))
                val state=requireNotNull(LibraryCollectionRestore(target.access).state(target.scope,target.session.issue(),operation))
                if(seam=="SWITCHED") {
                    assertEquals("COMMITTED",state.phase);assertTrue(state.currentlyActive)
                    MedicalCollectionReader.read(target.session.issue(),password(),FileInputStream(file).channel).use {source->
                        source.records(source.index().active) {records->
                            // The indexed production view intentionally removes export-only created.
                            // This tiny synthetic test reads the strict source header explicitly;
                            // existing v1 fixtures keep their unchanged EPOCH default/assertions.
                            val created=java.time.Instant.parse(BackupJsonReader.read(records).getString("created"))
                            LibraryRestoreAndroidTest().verify(target,source.manifest(source.index().active),records,state.generation,created)
                        }
                        target.raw.query("SELECT role,c.state FROM library_collection_member m JOIN library_candidate c USING(operation_uuid) WHERE collection_uuid=?",arrayOf(operation.toString())).use {c->
                            assertEquals(2,c.count);while(c.moveToNext())assertEquals(if(c.getString(0)=="ACTIVE")"ACTIVE" else "RETIRED",c.getString(1))
                        }
                    }
                }else {
                    assertNull(LibraryAuthority.current(target.raw));assertFalse(state.currentlyActive)
                    assertEquals(if(seam=="IN_SWITCH")"READY" else "PREPARED",state.phase)
                    target.raw.query("SELECT state FROM library_candidate").use {c->assertEquals(2,c.count);while(c.moveToNext())assertTrue(c.getString(0) in setOf("PREPARED","READY"))}
                }
                assertEquals(m.getString("oldHash"),hash(target.facts()))
            }
            if(seam=="SWITCHED")check(file.delete())
            check(marker.delete())
        }
    }
}
