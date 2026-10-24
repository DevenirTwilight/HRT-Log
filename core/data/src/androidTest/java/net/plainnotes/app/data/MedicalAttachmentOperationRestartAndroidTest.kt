package net.plainnotes.app.data

import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MedicalAttachmentOperationRestartAndroidTest {
    @Test fun safFacadeReconcilesRealCommitAfterFreshProcess()=runBlocking {
        val args=InstrumentationRegistry.getArguments();val phase=args.getString("operation_phase");assumeTrue(phase in listOf("prepare","verify"))
        val seam=requireNotNull(args.getString("operation_seam"));require(seam in setOf("PREPARED","STAGED","IN_COMMIT","COMMITTED"))
        val app=InstrumentationRegistry.getInstrumentation().targetContext;val marker=File(app.filesDir,"synthetic-operation-ready")
        val helper=MedicalAttachmentOperationAndroidTest()
        if(phase=="prepare") {
            check(!marker.exists());val f=LibraryRestoreAndroidTest.RootFixture(app)
            val target=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.APPOINTMENT,2)
            val request=MedicalAttachmentOperations.Import(UUID.randomUUID(),UUID.randomUUID(),target,"SYNTHETIC operation")
            val service=MedicalAttachmentOperations(f.access,helper.scope(f),{},checkpoint={at->if(at.name==seam) {
                val m=JSONObject().put("seam",seam).put("pid",Process.myPid()).put("root",f.id.toString()).put("operation",request.operation.toString()).put("document",request.document.toString())
                marker.outputStream().use {out->out.write(m.toString().toByteArray());out.fd.sync()}
                check(!CountDownLatch(1).await(90,TimeUnit.SECONDS));error("Expected real process termination")
            }})
            service.import(f.session.issue(),request,helper.uri("unknown"));error("Seam not reached")
        }else {
            val m=JSONObject(marker.readText());assertEquals(seam,m.getString("seam"));assertNotEquals(Process.myPid(),m.getInt("pid"))
            LibraryRestoreAndroidTest.RootFixture(app,UUID.fromString(m.getString("root")),create=false).use {f->
                val service=NotesRepository(f.access).forGeneration(helper.scope(f)).pinnedTo(Space.PRIMARY).medicalAttachments()
                val target=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.APPOINTMENT,2)
                val op=UUID.fromString(m.getString("operation"));val doc=UUID.fromString(m.getString("document"));val cap=f.session.issue()
                try {
                    val status=service.reconcile(cap,op,target,doc)
                    if(seam=="COMMITTED") {
                        assertEquals(MedicalAttachmentOperations.Phase.COMMITTED,status.phase);assertEquals(1,service.documents(cap,target).size)
                        service.open(cap,target,doc).use {r->assertEquals(8192,r.size);val b=ByteArray(1);assertEquals(1,r.readAt(0,b,0,1));assertEquals(17,b[0].toInt())}
                        assertEquals(0,service.recover(cap));assertEquals(status,service.reconcile(cap,op,target,doc))
                    }else {
                        assertNull(status.receipt);assertTrue(service.documents(cap,target).isEmpty())
                        assertEquals(1,service.recover(cap));assertEquals(0,service.recover(cap))
                        assertEquals(MedicalAttachmentOperations.Phase.ABORTED,service.reconcile(cap,op,target,doc).phase)
                    }
                    f.raw.query("PRAGMA foreign_key_check").use {assertEquals(0,it.count)}
                }finally {cap.close()}
            }
            check(marker.delete())
        }
    }
}
