package net.plainnotes.app.attachmentpoc

import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.system.ErrnoException
import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.app.domain.AttachmentSession
import net.plainnotes.poc.B2StreamingCandidate
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.nio.file.Files
import java.util.UUID

class TinkPdfGate2AndroidTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val key=ByteArray(32){(it+31).toByte()}
    private val aad="B2-SECURITY-GATE2-TEST-ONLY/synthetic-pdf".toByteArray()
    private fun session()=AttachmentSession(UUID.randomUUID(),UUID.randomUUID())
    private fun pdf():ByteArray {
        val doc=PdfDocument()
        try {
            for(i in 1..4){val p=doc.startPage(PdfDocument.PageInfo.Builder(200,300,i).create());p.canvas.drawColor(if(i%2==0)Color.BLUE else Color.WHITE);doc.finishPage(p)}
            return ByteArrayOutputStream().also{doc.writeTo(it)}.toByteArray()
        }finally{doc.close()}
    }
    private fun fixture(test:(File,B2StreamingCandidate.Evidence)->Unit) {
        SyntheticEmulatorGuard.requireEmulator();val root=Files.createTempDirectory(context.noBackupFilesDir.toPath(),"synthetic-gate2-pdf-").toFile()
        try {
            val file=File(root,"synthetic.cipher");val original=pdf()
            file.outputStream().use {out->B2StreamingCandidate.primitive(key).newEncryptingStream(out,aad).use{it.write(original)}}
            val evidence=B2StreamingCandidate.openAuthenticated(file,key,aad).use{it.evidence}
            test(file,evidence)
            // This owned fixture writes only ciphertext; no whole plaintext fallback file is accepted.
            for(f in root.listFiles()!!.filter{it.isFile}){assertFalse(f.inputStream().use{String(ByteArray(5).also{b->DataInputStream(it).readFully(b)})}.startsWith("%PDF-"))}
        }finally{root.deleteRecursively()}
    }
    @Test fun actualTinkMultipageProxyJumpScaleReopenNoPlaintextFallback()=fixture {file,evidence->
        repeat(2){EncryptedTinkPdfGate2(context,file,key,aad,session().issue(),evidence).use{v->
            assertEquals(4,v.pages)
            for(page in listOf(0,3,1,0)){val b=v.render(page,if(page==3)512 else 128);assertTrue(b.width<=512);v.release(b);assertTrue(b.isRecycled)}
            assertTrue(v.reads>0);assertTrue(v.maxRequest>0)
        }}
    }
    @Test fun revokedAfterDecryptBeforeDeliveryZerosProxyResponse()=fixture {file,evidence->
        val session=session();var revoke=false
        EncryptedTinkPdfGate2(context,file,key,aad,session.issue(),evidence,{if(revoke)session.revoke()}).use{v->
            revoke=true;val output=ByteArray(20){99};assertThrows(ErrnoException::class.java){v.callback.onRead(0,20,output)}
            assertArrayEquals(ByteArray(20),output);assertThrows(IllegalStateException::class.java){v.render(0)}
        }
    }
    @Test fun ownerCloseRecyclesNativeBitmapAndRevokesExistingProxy()=fixture {file,evidence->
        val owner=session().issue();val v=EncryptedTinkPdfGate2(context,file,key,aad,owner,evidence);val bitmap=v.render(0)
        v.close();assertTrue(bitmap.isRecycled());val output=ByteArray(20){99}
        assertThrows(ErrnoException::class.java){v.callback.onRead(0,20,output)};assertArrayEquals(ByteArray(20),output)
    }
    @Test fun corruptedTailIsRejectedBeforePdfRendererObtainsDescriptor()=fixture {file,evidence->
        RandomAccessFile(file,"rw").use{it.seek(it.length()-1);val b=it.read();it.seek(it.length()-1);it.write(b xor 1)}
        assertThrows(Exception::class.java){EncryptedTinkPdfGate2(context,file,key,aad,session().issue(),evidence)}
    }
    @Test fun pathnameReplacementKeepsPinnedOriginalButNewOpenFailsCommitment()=fixture {file,evidence->
        EncryptedTinkPdfGate2(context,file,key,aad,session().issue(),evidence).use { v ->
            val old=File(file.parentFile,"old.cipher");assertTrue(file.renameTo(old))
            file.outputStream().use{out->B2StreamingCandidate.primitive(key).newEncryptingStream(out,aad).use{it.write(pdf())}}
            assertThrows(Exception::class.java){EncryptedTinkPdfGate2(context,file,key,aad,session().issue(),evidence)}
            assertEquals(4,v.pages);val page=v.render(3);v.release(page)
            // Kernel pinned inode remains readable after unlink; owner revocation, not path deletion, revokes access.
            assertTrue(old.delete());val again=v.render(0);v.release(again)
        }
    }
}
