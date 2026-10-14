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
    @Test fun mimeClaimIsNotFormatValidation() {
        assertEquals("image/png",context.contentResolver.getType(uri("mime")))
        context.contentResolver.openInputStream(uri("mime"))!!.use{input->val signature=ByteArray(8);assertEquals(8,input.read(signature))
            assertFalse(signature.contentEquals(byteArrayOf(0x89.toByte(),80,78,71,13,10,26,10)))}
        // Harness only authenticates opaque bytes; VALIDATED is still forbidden by production guards.
    }
}
