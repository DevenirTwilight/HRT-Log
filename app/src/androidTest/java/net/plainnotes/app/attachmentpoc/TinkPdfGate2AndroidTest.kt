package net.plainnotes.app.attachmentpoc

import android.graphics.Color
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
    private fun awaitBlocked(thread:Thread) {
        val deadline=android.os.SystemClock.elapsedRealtime()+20_000
        while(thread.state!=Thread.State.BLOCKED){check(thread.isAlive&&android.os.SystemClock.elapsedRealtime()<deadline){"Thread never blocked: ${thread.state}"};Thread.sleep(5)}
    }
    private fun session()=AttachmentSession(UUID.randomUUID(),UUID.randomUUID())
    private fun pdf():ByteArray {
        // Four valid pages with bounded comment padding: offsets span multiple real Tink segments.
        val text=StringBuilder("%PDF-1.4\n");val offsets=mutableListOf<Int>()
        fun obj(id:Int,body:String){offsets+=text.length;text.append("$id 0 obj\n$body\nendobj\n")}
        obj(1,"<< /Type /Catalog /Pages 2 0 R >>")
        obj(2,"<< /Type /Pages /Kids [3 0 R 5 0 R 7 0 R 9 0 R] /Count 4 >>")
        for(i in 0..3){val page=3+i*2;val content=page+1
            obj(page,"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 300] /Resources << >> /Contents $content 0 R >>")
            val rgb=if(i%2==0)"1 1 1" else "0 0 1"
            val paint="q $rgb rg 0 0 200 300 re f Q\n%"+"X".repeat(131072)+"\n"
            obj(content,"<< /Length ${paint.length} >>\nstream\n${paint}endstream")
        }
        val xref=text.length;text.append("xref\n0 11\n0000000000 65535 f \n")
        offsets.forEach{text.append(String.format(java.util.Locale.ROOT,"%010d 00000 n \n",it))}
        text.append("trailer\n<< /Size 11 /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return text.toString().toByteArray(Charsets.US_ASCII)
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
            assertEquals(4,v.pages);assertTrue(evidence.length>B2StreamingCandidate.SCRATCH*4)
            for(page in listOf(0,3,1,0)){val b=v.render(page,if(page==3)512 else 128);assertTrue(b.width<=512);assertEquals(if(page%2==0)Color.WHITE else Color.BLUE,b.getPixel(b.width/2,b.height/2));v.release(b);assertTrue(b.isRecycled)}
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
    @Test fun ownerRevocationDuringNativePageLifetimeRejectsBitmapAndSerializesRendererClose()=fixture {file,evidence->
        val owner=session().issue();val rendered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        val closed=java.util.concurrent.CountDownLatch(1)
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        val v=EncryptedTinkPdfGate2(context,file,key,aad,owner,evidence,beforeBitmapDelivery={rendered.countDown();check(release.await(20,java.util.concurrent.TimeUnit.SECONDS))})
        try {
            val result=pool.submit(java.util.concurrent.Callable{v.render(3)})
            assertTrue(rendered.await(20,java.util.concurrent.TimeUnit.SECONDS));owner.close()
            val closer=Thread{v.close();closed.countDown()}.apply{start()}
            // Deterministic, not a timing window: close is parked on the renderer monitor the open page holds.
            awaitBlocked(closer);assertEquals(1L,closed.count)
            release.countDown()
            val failure=assertThrows(java.util.concurrent.ExecutionException::class.java){result.get(20,java.util.concurrent.TimeUnit.SECONDS)}
            assertTrue(failure.cause is IllegalStateException);closer.join(20_000);assertEquals(0L,closed.count)
            assertEquals(1,v.rejectedBitmaps)
        }finally{release.countDown();pool.shutdownNow();v.close()}
    }

}
