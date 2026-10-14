package net.plainnotes.app.attachmentpoc

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.poc.ChunkObject
import net.plainnotes.poc.KeyEnvelope
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.nio.file.Files
import java.security.KeyStore
import javax.crypto.KeyGenerator
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

class AttachmentPreviewAndroidTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private inline fun fixture(block:(File)->Unit) {
        // Never inspect or clean an installed user's real cache/files.
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk")) {"Synthetic emulator only"}
        val root=Files.createTempDirectory(context.filesDir.toPath(),"synthetic-med02a-").toFile()
        try{block(root)}finally{root.deleteRecursively()}
    }
    private fun pdf():ByteArray {
        val doc=PdfDocument()
        return try {
        repeat(4){i->val p=doc.startPage(PdfDocument.PageInfo.Builder(320,480,i+1).create());p.canvas.drawColor(Color.WHITE);p.canvas.drawText("SYNTHETIC POC PAGE $i",20f,60f,Paint().apply{color=Color.BLACK;textSize=18f});doc.finishPage(p)}
        ByteArrayOutputStream().also{doc.writeTo(it)}.toByteArray()
        }finally{doc.close()}
    }
    @Test fun pdfPublicProxyMultiPageJumpZoomReopenRevokeAndNoPlaintextFile()=fixture{root->
        val data=pdf();val originalFiles=(context.filesDir.walkTopDown()+context.cacheDir.walkTopDown()).filter{it.isFile}.map{it.absolutePath}.toSet()
        val start=SystemClock.elapsedRealtime();val pssBefore=Debug.getPss()
        ChunkObject.write(root,ChunkObject.Identity.fresh(),ByteArrayInputStream(data),1024).use{o->
            repeat(2){EncryptedPdfPoc(context,o).use{viewer->
                assertEquals(4,viewer.pages)
                for(page in listOf(0,3,1)){val bitmap=viewer.render(page);assertTrue(bitmap.allocationByteCount<=16*1024*1024);assertEquals(Color.WHITE,bitmap.getPixel(0,0));viewer.release(bitmap);assertTrue(bitmap.isRecycled)}
                val zoom=viewer.render(2,1024);assertEquals(1024,zoom.width);viewer.close();assertTrue(zoom.isRecycled)
                assertThrows(IllegalStateException::class.java){viewer.render(0)}
                assertThrows(android.system.ErrnoException::class.java){viewer.callback.onRead(0,1,ByteArray(1))}
                Log.i("MED02APOC","pdf api=${android.os.Build.VERSION.SDK_INT} reads=${viewer.reads} scratch_bound=${viewer.scratchBytes}")
            }}
            assertEquals(1,root.listFiles()!!.size);assertFalse(o.file.readBytes().contentEquals(data))
            for(f in (context.filesDir.walkTopDown()+context.cacheDir.walkTopDown()).filter{it.isFile && it.absolutePath !in originalFiles && it.length()<=20*1024*1024}){
                val b=f.readBytes();assertFalse("Plain PDF file created: ${f.name}",b.size>=5 && b.copyOf(5).toString(Charsets.US_ASCII)=="%PDF-")
                assertFalse("Full plaintext copy",b.contentEquals(data))
            }
        }
        Log.i("MED02APOC","pdf elapsed_ms=${SystemClock.elapsedRealtime()-start} pss_before_kib=$pssBefore pss_after_kib=${Debug.getPss()}")
    }
    @Test fun damagedPdfAndIncompleteCipherNeverReachRenderer()=fixture{root->
        ChunkObject.write(root,ChunkObject.Identity.fresh(),ByteArrayInputStream(pdf()),1024).use{o->
            val b=o.file.readBytes();b[100]=(b[100].toInt() xor 1).toByte();o.file.writeBytes(b)
            assertThrows(javax.crypto.AEADBadTagException::class.java){EncryptedPdfPoc(context,o)}
            o.file.writeBytes(b.copyOf(b.size-68));assertThrows(Exception::class.java){EncryptedPdfPoc(context,o)}
        }
    }
    @Test fun largePdfUsesStreamAndBoundedPageWithActualMemoryObservation()=fixture{root->
        val start=SystemClock.elapsedRealtime();val before=Debug.getPss()
        val observed=SampledMemory()
        try {
        LargeSyntheticPdf.input().use{input->ChunkObject.write(root,ChunkObject.Identity.fresh(),input).use{o->
            assertTrue(o.file.length()>4*1024*1024)
            EncryptedPdfPoc(context,o).use{v->val bitmap=v.render(0);assertEquals(Color.WHITE,bitmap.getPixel(0,0));assertTrue(bitmap.allocationByteCount<=16*1024*1024)
                Log.i("MED02APOC","large_pdf api=${android.os.Build.VERSION.SDK_INT} cipher_bytes=${o.file.length()} bitmap_bytes=${bitmap.allocationByteCount} reads=${v.reads} scratch_bound=${v.scratchBytes} elapsed_ms=${SystemClock.elapsedRealtime()-start} pss_before_kib=$before pss_after_kib=${Debug.getPss()}")
                v.release(bitmap)
            }
        }}
        assertEquals(1,root.listFiles()!!.size)
        }finally{observed.close();Log.i("MED02APOC","large_pdf api=${android.os.Build.VERSION.SDK_INT} ${observed.fields()}")}
    }
    @Test fun jpegPngWebpBoundedThumbnailOriginalBytesAndMimeMismatch()=fixture{root->
        val bitmap=Bitmap.createBitmap(2048,1024,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.BLUE)
        try{for((format,mime) in listOf(Bitmap.CompressFormat.JPEG to "image/jpeg",Bitmap.CompressFormat.PNG to "image/png",Bitmap.CompressFormat.WEBP to "image/webp")){
            val data=ByteArrayOutputStream().also{assertTrue(bitmap.compress(format,90,it))}.toByteArray()
            ChunkObject.write(root,ChunkObject.Identity.fresh(),ByteArrayInputStream(data)).use{o->
                val start=SystemClock.elapsedRealtime();val thumb=ImagePoc.decode(o,mime);assertTrue(thumb.width<=512 && thumb.height<=512);assertTrue(thumb.allocationByteCount<=4*1024*1024)
                Log.i("MED02APOC","image api=${android.os.Build.VERSION.SDK_INT} mime=$mime bytes=${data.size} bitmap_bytes=${thumb.allocationByteCount} elapsed_ms=${SystemClock.elapsedRealtime()-start}");thumb.recycle();assertTrue(thumb.isRecycled)
                o.open().use{r->val original=ByteArray(data.size);r.readAt(0,original);assertArrayEquals(data,original)}
                assertThrows(IllegalArgumentException::class.java){ImagePoc.decode(o,"application/pdf")}
            }
        }}finally{bitmap.recycle()}
    }
    @Test fun dimensionsBombAndInvalidImageRejectedBeforeBitmapAllocation()=fixture{root->
        val bitmap=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)
        val small=ByteArrayOutputStream().also{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        // Mutate IHDR width/height and recompute its CRC so bounds parsing sees the huge dimensions.
        java.nio.ByteBuffer.wrap(small,16,8).putInt(32768).putInt(32768)
        val crc=java.util.zip.CRC32().apply{update(small,12,17)}.value.toInt();java.nio.ByteBuffer.wrap(small,29,4).putInt(crc)
        ChunkObject.write(root,ChunkObject.Identity.fresh(),ByteArrayInputStream(small)).use{o->assertThrows(IllegalArgumentException::class.java){ImagePoc.decode(o,"image/png")}}
        ChunkObject.write(root,ChunkObject.Identity.fresh(),ByteArrayInputStream("not a medical file".toByteArray())).use{o->assertThrows(IllegalStateException::class.java){ImagePoc.decode(o,"image/png")}}
    }
    @Test fun actualKeystoreDeviceWrapPortableRestoreAndMissingAlias()=fixture{_->
        val alias="synthetic-med02a-${java.util.UUID.randomUUID()}";val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        try{
            val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            val key=generator.generateKey();assertNull(key.encoded)
            val content=ByteArray(32){it.toByte()};val ctx="synthetic primary key purpose".toByteArray()
            val envelope=KeyEnvelope.wrap(key,ctx,content);assertArrayEquals(content,KeyEnvelope.unwrap(key,ctx,envelope))
            val portable=KeyEnvelope.portable("synthetic password".toCharArray(),ctx,content);assertArrayEquals(content,KeyEnvelope.restore("synthetic password".toCharArray(),ctx,portable))
            store.deleteEntry(alias);assertNull(store.getKey(alias,null)) // No regeneration or pretend successful restore.
        }finally{store.deleteEntry(alias)}
    }
}
