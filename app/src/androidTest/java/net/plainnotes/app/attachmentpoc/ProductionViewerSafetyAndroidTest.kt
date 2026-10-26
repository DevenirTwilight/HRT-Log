package net.plainnotes.app.attachmentpoc

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.app.attachments.AttachmentViewer
import net.plainnotes.app.data.MedicalAttachmentOperations
import net.plainnotes.app.domain.AttachmentSession
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Production native viewer with real capability revocation. Transport is synthetic memory;
 * encrypted-store/SAF integration remains the existing AttachmentOperationAndroidTest's contract. */
class ProductionViewerSafetyAndroidTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private class Source(private val bytes:ByteArray):MedicalAttachmentOperations.Read {
        val session=AttachmentSession(UUID.randomUUID(),UUID.randomUUID())
        private val cap=session.issue()
        override val size:Long get()=cap.publish{bytes.size.toLong()}
        override fun check()=cap.check()
        override fun <T> publish(block:()->T):T=cap.publish(block)
        override fun own(resource:AutoCloseable):AutoCloseable=cap.own(resource)
        override fun close()=cap.close()
        override fun readAt(position:Long,destination:ByteArray,offset:Int,length:Int):Int=cap.publish {
            require(position>=0 && offset>=0 && length>=0 && offset+length<=destination.size)
            if(position>=bytes.size)-1 else minOf(length,bytes.size-position.toInt()).also {bytes.copyInto(destination,offset,position.toInt(),position.toInt()+it)}
        }
    }
    private fun image(format:Bitmap.CompressFormat,width:Int=32,height:Int=32):ByteArray {
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        try {bitmap.eraseColor(Color.BLUE);return ByteArrayOutputStream().also {assertTrue(bitmap.compress(format,90,it))}.toByteArray()}
        finally {bitmap.recycle()}
    }
    private fun pdf(width:Int=100,height:Int=100):ByteArray {
        val doc=PdfDocument()
        try {val page=doc.startPage(PdfDocument.PageInfo.Builder(width,height,1).create());page.canvas.drawColor(Color.WHITE);doc.finishPage(page)
            return ByteArrayOutputStream().also{doc.writeTo(it)}.toByteArray()
        } finally {doc.close()}
    }
    private fun refused(block:()->Unit) {try {block();fail("Unsafe synthetic input accepted")}catch(e:Exception) {/* fail() throws AssertionError, not swallowed */}}
    private val pdfPreview get()=Build.VERSION.SDK_INT>=Build.VERSION_CODES.P
    private fun unsupported(block:()->Unit) {assertThrows(AttachmentViewer.PdfPreviewUnsupported::class.java) {block()}}
    private fun measured(name:String,block:()->Unit) {
        SyntheticEmulatorGuard.requireEmulator()
        val expected=InstrumentationRegistry.getArguments().getString("viewerExpectedHeapMb")
        if(expected!=null)assertEquals(expected.toLong()*1024*1024,Runtime.getRuntime().maxMemory())
        val observer=SampledMemory()
        try {block()}finally {
            observer.close()
            val line="viewer=$name api=${Build.VERSION.SDK_INT} sampled_not_peak=true ${observer.fields()} max_heap_bytes=${Runtime.getRuntime().maxMemory()}"
            Log.i("SyntheticViewerMemory",line)
            java.io.File(context.filesDir,"synthetic-viewer-memory.txt").appendText(line+"\n")
        }
    }
    @Test fun jpegPngWebpAndPdfPublishReleaseAndRevoke()=measured("formats") {
        for(format in listOf(Bitmap.CompressFormat.JPEG,Bitmap.CompressFormat.PNG,Bitmap.CompressFormat.WEBP)) {
            val source=Source(image(format))
            AttachmentViewer(context,source).use {viewer->
                repeat(3){val bitmap=viewer.image();assertEquals(32,bitmap.width);viewer.release(bitmap);assertTrue(bitmap.isRecycled)}
                val held=viewer.image();source.session.revoke();assertTrue(held.isRecycled)
                refused{viewer.image()}
            }
        }
        val source=Source(pdf())
        AttachmentViewer(context,source).use {viewer->
            if(!pdfPreview) {unsupported{viewer.pdfPage(0)};return@use}
            repeat(3){val page=viewer.pdfPage(0);assertEquals(512,page.width);viewer.release(page)}
            val held=viewer.pdfPage(0);source.session.revoke();assertTrue(held.isRecycled);refused{viewer.pdfPage(0)}
        }
    }
    /** Android 8.x PdfRenderer finalizes a failed open with nativeClose(0), unbalancing pdfium for the
     * whole process. Unopenable documents must not disable later valid previews (P+), and on 8.x the
     * viewer must refuse before PdfRenderer can see untrusted bytes at all. */
    @Test fun validPdfStillRendersAfterUnopenablePdf()=measured("after-unopenable") {
        for(bytes in listOf("%PDF-1.7\ninvalid".toByteArray(),pdf().copyOf(8))) {
            AttachmentViewer(context,Source(bytes)).use{viewer->if(pdfPreview)refused{viewer.pdfPage(0)} else unsupported{viewer.pdfPage(0)}}
        }
        // Give the failed renderers' finalizers the chance to run before the valid document opens.
        repeat(3){Runtime.getRuntime().gc();System.runFinalization()}
        for(size in listOf(100,240)) {
            AttachmentViewer(context,Source(pdf(size,size))).use {viewer->
                if(!pdfPreview) {unsupported{viewer.pdfPage(0)};return@use}
                val page=viewer.pdfPage(0);assertEquals(512,page.width);viewer.release(page);assertTrue(page.isRecycled)
            }
        }
    }
    @Test fun malformedTruncatedAndOversizeRasterRefused()=measured("malformed") {
        for(bytes in listOf(byteArrayOf(1,2,3),image(Bitmap.CompressFormat.PNG).copyOf(8),image(Bitmap.CompressFormat.JPEG).copyOf(3),image(Bitmap.CompressFormat.WEBP).copyOf(12),image(Bitmap.CompressFormat.PNG,2049,1))) {
            AttachmentViewer(context,Source(bytes)).use{viewer->refused{viewer.image()}}
        }
        for(bytes in listOf("%PDF-1.7\ninvalid".toByteArray(),pdf().copyOf(8))) {
            AttachmentViewer(context,Source(bytes)).use{viewer->refused{viewer.pdfPage(0)}}
        }
        AttachmentViewer(context,Source(pdf(1,3000))).use{viewer->refused{viewer.pdfPage(0)}}
    }
    @Test fun closedOrRevokedCapabilityCannotPublishBytesOrPixels()=measured("closed") {
        val source=Source(image(Bitmap.CompressFormat.PNG))
        val viewer=AttachmentViewer(context,source)
        val prefix=viewer.prefix();viewer.close();assertTrue(prefix.all{it==0.toByte()})
        viewer.close();refused{viewer.image()};refused{viewer.prefix()}
        val revoked=Source(pdf());revoked.session.revoke()
        refused{AttachmentViewer(context,revoked).use{it.pdfPage(0)}}
    }
}
