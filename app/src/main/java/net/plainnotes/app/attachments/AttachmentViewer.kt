package net.plainnotes.app.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.OsConstants
import net.plainnotes.app.data.MedicalAttachmentOperations
import java.io.InputStream
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/** Internal viewer, not an exported descriptor or ACTION_VIEW. All originals stay encrypted.
 * Caller uses an IO dispatcher, closes on page exit, and blanks the view when its scope is revoked.
 * Existing fully authenticated/scoped reader checks every callback and every bitmap publication.
 */
internal class AttachmentViewer(private val context:Context,private val read:MedicalAttachmentOperations.Read):AutoCloseable {
    private val monitor=Any()
    @Volatile private var closed=false
    private val disposed=AtomicBoolean(false)
    private var thread:HandlerThread?=null
    private var descriptor:ParcelFileDescriptor?=null
    private var pdf:PdfRenderer?=null
    private val images=mutableListOf<Bitmap>()
    private val prefixes=mutableListOf<ByteArray>()
    private val revocation=read.own(AutoCloseable {
        closed=true
        synchronized(images) {images.forEach {it.recycle()};images.clear();prefixes.forEach {it.fill(0)};prefixes.clear()}
        // Never block AppLock/publication or the ProxyFD callback thread on native renderer teardown.
        cleanup.launch {dispose()}
    })
    private fun valid() {check(!closed);read.check()}
    private fun retain(bitmap:Bitmap):Bitmap {
        try {return read.publish {check(!closed);synchronized(images) {images+=bitmap};bitmap}}catch(e:Throwable) {bitmap.recycle();throw e}
    }
    fun pdfPage(index:Int,width:Int=512):Bitmap=synchronized(monitor) {
        valid();require(width in 1..2048);check(synchronized(images) {images.isEmpty()}) {"Release the previous page"}
        val renderer=openPdf()
        renderer.openPage(index).use {page->
            val height=(width.toLong()*page.height/page.width).coerceAtLeast(1);require(height<=2048 && width.toLong()*height*4<=16*1024*1024)
            val bitmap=Bitmap.createBitmap(width,height.toInt(),Bitmap.Config.ARGB_8888)
            try {page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);retain(bitmap)}catch(e:Throwable) {bitmap.recycle();throw e}
        }
    }
    /** MED-05: number of pages, bounded so a hostile document cannot make the viewer walk an unbounded list. */
    fun pageCount():Int=synchronized(monitor) {valid();openPdf().pageCount.also {check(it in 1..MAXIMUM_PAGES) {"Unsupported page count"}}}
    /** MED-05: one page scaled so neither side exceeds [maxEdge] pixels (a tall page gets a narrower bitmap instead of failing). */
    fun pdfPageFit(index:Int,maxEdge:Int):Bitmap=synchronized(monitor) {
        // Two pages may be held for a moment while the next replaces the shown one; a third is refused.
        valid();require(maxEdge in 16..2048);check(synchronized(images) {images.size<2}) {"Release the previous page"}
        val renderer=openPdf();require(index in 0 until renderer.pageCount)
        renderer.openPage(index).use {page->
            val scale=maxEdge.toDouble()/maxOf(page.width,page.height).coerceAtLeast(1)
            val width=(page.width*scale).toInt().coerceAtLeast(1);val height=(page.height*scale).toInt().coerceAtLeast(1)
            require(width.toLong()*height*4<=16*1024*1024)
            val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            try {page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);retain(bitmap)}catch(e:Throwable) {bitmap.recycle();throw e}
        }
    }
    /** Callers hold [monitor]. Opens the renderer once; the same descriptor/handler lifecycle as before. */
    private fun openPdf():PdfRenderer {
        // Android 8.0/8.1 PdfRenderer sets mInput before nativeCreate; when a document cannot be
        // opened, its finalizer later calls nativeClose(0) and unbalances pdfium's process-wide init
        // count, so every later PDF in the process fails (AOSP O doClose; fixed in P). Untrusted
        // bytes therefore never reach PdfRenderer there: refuse before any proxy or renderer exists.
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.P)throw PdfPreviewUnsupported()
        if(pdf==null) {
            val handler=HandlerThread("attachment-view-reader").apply {start()}.also {thread=it}
            val callbacks=object:ProxyFileDescriptorCallback() {
                override fun onGetSize():Long=try {read.size}catch(e:Exception) {throw ErrnoException("Revoked attachment size",OsConstants.EIO,e)}
                override fun onRead(offset:Long,size:Int,data:ByteArray):Int=try {read.readAt(offset,data,0,minOf(size,data.size))}
                    catch(e:Exception) {data.fill(0);throw ErrnoException("Authenticated attachment read",OsConstants.EIO,e)}
                override fun onRelease() {read.close()}
            }
            try {
                descriptor=(context.getSystemService(Context.STORAGE_SERVICE) as StorageManager).openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY,callbacks,Handler(handler.looper))
                // PdfRenderer stores its input before parsing and its finalizer closes it even after a
                // failed open. A duplicate gives each descriptor exactly one closer, so that finalizer can
                // never race dispose() on the same descriptor number.
                pdf=PdfRenderer(requireNotNull(descriptor).dup())
            }catch(e:Throwable) {close();throw e}
        }
        return requireNotNull(pdf)
    }
    /** Decode dimensions before allocating pixels. No provider MIME or filename participates. */
    fun image(maxEdge:Int=2048):Bitmap=synchronized(monitor) {
        require(maxEdge in 16..2048);valid();check(synchronized(images) {images.isEmpty()})
        fun stream():InputStream=object:InputStream() {
            var position=0L
            override fun read():Int {val b=ByteArray(1);return if(read(b,0,1)<0)-1 else b[0].toInt() and 255}
            override fun read(b:ByteArray,o:Int,n:Int):Int {if(n==0)return 0;val count=this@AttachmentViewer.read.readAt(position,b,o,n);if(count>0)position+=count;return count}
        }
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        stream().use {BitmapFactory.decodeStream(it,null,bounds)}
        // A phone photo is larger than the display limit: it is decoded at a power-of-two reduction instead of being refused.
        require(bounds.outWidth in 1..MAXIMUM_SOURCE_EDGE && bounds.outHeight in 1..MAXIMUM_SOURCE_EDGE && bounds.outWidth.toLong()*bounds.outHeight<=MAXIMUM_SOURCE_PIXELS)
        var sample=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>maxEdge)sample*=2
        val options=BitmapFactory.Options().apply {inSampleSize=sample}
        val decoded=requireNotNull(stream().use {BitmapFactory.decodeStream(it,null,options)})
        require(decoded.width.toLong()*decoded.height*4<=16*1024*1024) {decoded.recycle();"Decoded image too large"}
        retain(decoded)
    }
    fun prefix(maximum:Int=256):ByteArray=synchronized(monitor) {
        valid();require(maximum in 1..4096);val b=ByteArray(minOf(read.size,maximum.toLong()).toInt())
        try {var p=0;while(p<b.size) {val n=read.readAt(p.toLong(),b,p,b.size-p);check(n>0);p+=n};read.publish {check(!closed);synchronized(images) {prefixes.forEach {it.fill(0)};prefixes.clear();prefixes+=b};b}}
        catch(e:Throwable) {b.fill(0);throw e}
    }
    /** The final UI publication must share the existing native scope/capability fence, not
     * merely rely on an asynchronously delivered dataset-change notification. Caller is on IO. */
    fun <T> publishFrame(block:()->T):T=read.publish {check(!closed);block()}
    fun release(bitmap:Bitmap)=synchronized(images) {if(images.remove(bitmap))bitmap.recycle()}
    override fun close() {
        try {revocation.close()}finally {read.close()}
    }
    private fun dispose()=synchronized(monitor) {
        if(disposed.compareAndSet(false,true)) {
            try {pdf?.close()}finally {pdf=null;try {descriptor?.close()}finally {descriptor=null;thread?.quitSafely();thread=null}}
        }
    }
    /** The stored PDF stays encrypted and readable; only the on-device page preview is refused. */
    class PdfPreviewUnsupported:UnsupportedOperationException("PDF preview requires Android 9 or later")
    companion object {
        private val cleanup=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        const val MAXIMUM_PAGES=2000
        private const val MAXIMUM_SOURCE_EDGE=16384
        private const val MAXIMUM_SOURCE_PIXELS=100_000_000L
    }
}
