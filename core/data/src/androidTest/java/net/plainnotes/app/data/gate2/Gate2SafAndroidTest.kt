package net.plainnotes.app.data

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Real local test ContentProvider/reliable pipe; not a cloud provider or production SAF UI. */
class Gate2SafAndroidTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun uri(kind:String)=Uri.parse("content://net.plainnotes.app.data.test.synthetic.gate2/$kind")
    @Test fun actualUnknownLengthAndFalseDeclaredLengthAreNotAuthority() {
        val s=Gate2Store(context)
        try{for(kind in listOf("unknown","false")) {
            val source=uri(kind)
            context.contentResolver.query(source,null,null,null,null)!!.use{c->assertTrue(c.moveToFirst());val i=c.getColumnIndexOrThrow(OpenableColumns.SIZE);if(kind=="unknown")assertTrue(c.isNull(i)) else assertEquals(1L,c.getLong(i))}
            context.contentResolver.openFileDescriptor(source,"r")!!.let{fd->assertEquals(-1L,fd.statSize)
                ParcelFileDescriptor.AutoCloseInputStream(fd).use{input->s.import(s.prepare(),input)}}
            s.open().use{assertEquals(8192L,it.size())}
        };s.assertProductionOff()}finally{s.destroy()}
    }
    @Test fun reliablePipeCloseWithErrorCannotBecomeSuccessfulPartialObject() {
        val s=Gate2Store(context)
        try{val op=s.prepare();var rejected=false
            try{context.contentResolver.openFileDescriptor(uri("failure"),"r")!!.let{fd->ParcelFileDescriptor.AutoCloseInputStream(fd).use{s.import(op,it)}}}
            catch(e:java.io.IOException){rejected=true}
            assertTrue("Reliable pipe error must reach importer",rejected);assertNull(s.active());assertEquals("ABORTED",s.phase(op.id));assertFalse(s.file(op).exists());s.assertProductionOff()
        }finally{s.destroy()}
    }
    private fun failsWith(expected:String,block:()->Unit) {
        val t=try{block();null}catch(t:Throwable){t}?:throw AssertionError("Expected rejection '$expected'")
        assertTrue("Expected rejection '$expected' but got $t",generateSequence(t){it.cause}.any{it.message?.contains(expected)==true})
    }
    private fun admission(kind:String):Gate2Store.Admission {
        val name=context.contentResolver.query(uri(kind),null,null,null,null)!!.use{c->assertTrue(c.moveToFirst());c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))}
        return Gate2Store.Admission(context.contentResolver.getType(uri(kind))!!,name)
    }
    @Test fun candidateAdmissionRejectsMimeMagicConflictsAndUnsafeNamesBeforeCommit() {
        val s=Gate2Store(context)
        try {
            for((kind,reason) in listOf("mime" to "MIME conflict","pdfclaim" to "MIME conflict","badname" to "Unsafe display name")) {
                val op=s.prepare();val gate=admission(kind)
                failsWith(reason){context.contentResolver.openInputStream(uri(kind))!!.use{s.import(op,it,admission=gate)}}
                assertEquals(kind,"ABORTED",s.phase(op.id));assertFalse(kind,s.file(op).exists());assertNull(s.active())
            }
            // Matching claim is admitted: a signature check only, never parser validation or a trusted origin.
            val ok=s.prepare();context.contentResolver.openInputStream(uri("pdfok"))!!.use{s.import(ok,it,admission=admission("pdfok"))}
            assertEquals(ok.id,s.active());s.open().use{assertEquals(8192L,it.size())};s.assertProductionOff()
        }finally{s.destroy()}
    }
    @Test fun mimeClaimIsNotFormatValidation() {
        assertEquals("image/png",context.contentResolver.getType(uri("mime")))
        context.contentResolver.openInputStream(uri("mime"))!!.use{input->val signature=ByteArray(8);assertEquals(8,input.read(signature))
            assertFalse(signature.contentEquals(byteArrayOf(0x89.toByte(),80,78,71,13,10,26,10)))}
        // Harness only authenticates opaque bytes; VALIDATED is still forbidden by production guards.
    }
}
