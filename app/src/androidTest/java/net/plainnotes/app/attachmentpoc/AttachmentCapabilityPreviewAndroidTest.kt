package net.plainnotes.app.attachmentpoc

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.system.ErrnoException
import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.poc.CapabilityObject
import net.plainnotes.poc.ChunkObject
import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files

/** Test-only preview lifecycle; real public ProxyFD/PdfRenderer, synthetic files only. */
class AttachmentCapabilityPreviewAndroidTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(block:(java.io.File)->Unit) {
        SyntheticEmulatorGuard.requireEmulator()
        val root=Files.createTempDirectory(context.filesDir.toPath(),"synthetic-b1-preview-").toFile()
        try{block(root)}finally{root.deleteRecursively()}
    }
    private fun pdf():ByteArray {
        val document=PdfDocument()
        try {
            val p=document.startPage(PdfDocument.PageInfo.Builder(100,100,1).create());p.canvas.drawColor(Color.WHITE);document.finishPage(p)
            return ByteArrayOutputStream().also{document.writeTo(it)}.toByteArray()
        }finally{document.close()}
    }
    @Test fun proxyRevocationAfterDecryptBeforeDeliveryErasesResponse()=fixture {root->
        val session=CapabilityObject.Session();var revoke=false
        ChunkObject.write(root,ChunkObject.Identity.fresh(),ByteArrayInputStream(pdf())).use{raw->
            EncryptedPdfPoc(context,raw,session,{if(revoke)session.revoke()}).use{viewer->
                assertEquals(1,viewer.pages);revoke=true;val bytes=ByteArray(5){42}
                assertThrows(ErrnoException::class.java){viewer.callback.onRead(0,5,bytes)}
                assertArrayEquals(ByteArray(5),bytes)
                assertThrows(IllegalStateException::class.java){viewer.render(0)}
            }
        }
    }
    @Test fun closingOwnerRevokesExistingProxyAndRendererWithoutPlaintextFile()=fixture {root->
        ChunkObject.write(root,ChunkObject.Identity.fresh(),ByteArrayInputStream(pdf())).use{raw->
            EncryptedPdfPoc(context,raw).use{viewer->
                assertEquals(1,viewer.pages);raw.close();val bytes=ByteArray(5){42}
                assertThrows(ErrnoException::class.java){viewer.callback.onRead(0,5,bytes)}
                assertArrayEquals(ByteArray(5),bytes)
                assertThrows(IllegalStateException::class.java){viewer.render(0)}
            }
        }
        assertEquals(1,root.listFiles()!!.size)
        assertEquals("SYNPOC01",root.listFiles()!!.single().inputStream().use{String(ByteArray(8).also{b->java.io.DataInputStream(it).readFully(b)},Charsets.US_ASCII)})
    }
    @Test fun cancelledDecodedImageCannotPublishBitmapAndOriginalBytesStayUnchanged()=fixture {root->
        val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.BLUE)
        val png=try{ByteArrayOutputStream().also{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}.toByteArray()}finally{bitmap.recycle()}
        val session=CapabilityObject.Session();var seam=false
        ChunkObject.write(root,ChunkObject.Identity.fresh(),ByteArrayInputStream(png)).use{raw->
            assertThrows(IllegalStateException::class.java){ImagePoc.decode(raw,"image/png",session=session,beforePublication={seam=true;session.revoke()})}
            assertTrue(seam)
            raw.open().use{r->val original=ByteArray(png.size);assertEquals(png.size,r.readAt(0,original));assertArrayEquals(png,original)}
        }
    }
}
